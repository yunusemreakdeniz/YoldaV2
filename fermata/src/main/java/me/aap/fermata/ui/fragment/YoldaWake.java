package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * "Hey Yolda" uyandırma sözcüğü: cihazda, internetsiz çalışan açık kaynak Vosk ses tanıma (Apache-2.0)
 * yalnız birkaç sözcüklük dilbilgisiyle dinler; "Hey Yolda" duyunca Yolda'nın sesli asistanını açar.
 * Türkçe küçük model (~37 MB) ilk açılışta bir kez indirilir. Uygulama ekrandayken dinler, sesli asistan
 * konuşurken susar, sonra yeniden dinler.
 */
public final class YoldaWake {
	private static final String PREF = "wake_enabled";
	private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip";
	private static final String MODEL_DIR = "vosk-tr";
	static final String GRAMMAR =
			"[\"hey yolda\", \"ey yolda\", \"yolda\", \"hey yola\", \"ey yola\", \"yola\", \"[unk]\"]";

	/** Model yükleme / mikrofon açma tek bir arka plan iş parçacığında, sırayla (iki dinleyici açılmasın). */
	private static final java.util.concurrent.ExecutorService EXEC = java.util.concurrent.Executors.newSingleThreadExecutor();
	private static Model model;
	/** Tek tanıyıcı, her dinlemede sıfırlanıp yeniden kullanılır (önceden her seferinde yenisi açılıp bellek sızıyordu). */
	private static Recognizer recognizer;
	/** Her başlat/durdur isteğinde artar: araya durdurma girdiyse geç biten başlatma çöpe atılır. */
	private static int gen;
	private static boolean starting;
	@Nullable
	private static SpeechService service;
	@Nullable
	private static MainActivityDelegate owner;
	/**
	 * Araç ekranı (açıksa): "Hey Yolda" komutu her zaman araçta işlenir. Önceden telefon ekranı bir kez öne gelince
	 * komut telefonda işleniyor, araçta "dinliyor ama bir şey yapmıyor" oluyordu.
	 */
	private static MainActivityDelegate carOwner;

	/** Ekran kapandı: ona bağlı dinleyici sahipliği bırakılır. */
	public static void forget(MainActivityDelegate a) {
		if (carOwner == a) carOwner = null;
		if (owner == a) owner = (carOwner != null) ? carOwner : null;
	}
	private static boolean paused;
	private static boolean downloading;
	private static long lastWake;

	private YoldaWake() {
	}

	public static boolean isEnabled(Context ctx) {
		// Varsayılan açık: mikrofon izni verilmişse "Hey Yolda" kurulumsuz çalışsın
		return prefs(ctx).getBoolean(PREF, true);
	}

	public static boolean isModelReady(Context ctx) {
		// Türkçe küçük model eski düzende (final.mdl kökte; conf/ am/ yok): önceden hazır sayılmıyor, her
		// açılışta yeniden açılıyor ve dinleme hiç başlamıyordu
		File d = modelDir(ctx);
		return new File(d, "final.mdl").isFile() || new File(d, "am/final.mdl").isFile()
				|| new File(d, "conf").isDirectory() || new File(d, "am").isDirectory();
	}

	/** Menüden aç/kapat; açılırken model yoksa indirilir. */
	public static void setEnabled(MainActivityDelegate a, boolean on) {
		Context ctx = a.getContext();
		prefs(ctx).edit().putBoolean(PREF, on).apply();
		if (on) {
			YoldaWakeService.start(ctx);
			start(a);
		} else {
			stop();
			YoldaWakeService.stop(ctx);
		}
	}

	/** Şu an dinliyor mu (sesli düğmedeki yeşil nokta). */
	public static boolean isListening() {
		return service != null;
	}

	/** Uygulama ekrana gelince (ve sesli asistan bitince) çağrılır. */
	public static void start(MainActivityDelegate a) {
		Context ctx = a.getContext();
		owner = a;
		if (a.isCarActivity()) carOwner = a;
		paused = false;
		if (!isEnabled(ctx) || (service != null) || starting) return;
		if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			YoldaLog.add("Hey Yolda: mikrofon izni yok");
			return;
		}
		// Telefonda uygulama açıkken mikrofon hizmetini başlat: araçtayken (telefonda arka planda) de duyulsun.
		// İzin yokken başlatılmaz (mikrofon türündeki hizmet izinsiz açılınca Android uygulamayı kapatıyor).
		if (!a.isCarActivity()) YoldaWakeService.start(ctx);
		if (!isModelReady(ctx)) {
			if (hasBundledModel(ctx)) extractBundled(a); // APK'nın içindeki model: internetsiz açılır
			else download(a);
			return;
		}
		starting = true;
		int g = ++gen;
		String dir = modelDir(ctx).getAbsolutePath();
		EXEC.submit(() -> {
			SpeechService s = null;
			Throwable fail = null;
			try {
				LibVosk.setLogLevel(LogLevel.WARNINGS);
				if (model == null) model = new Model(dir);
				if (recognizer == null) recognizer = new Recognizer(model, 16000.0f, GRAMMAR);
				else recognizer.reset();
				s = new SpeechService(recognizer, 16000.0f);
			} catch (Throwable ex) {
				fail = ex;
			}
			SpeechService fs = s;
			Throwable ff = fail;
			App.get().getHandler().post(() -> {
				starting = false;
				if (fs == null) { // çoğunlukla mikrofon o an başka bir uygulamada: biraz sonra yeniden dene
					Log.e(ff, "Hey Yolda başlatılamadı");
					YoldaLog.add("Hey Yolda başlatılamadı: " + ff);
					if (!paused) retrySoon(3000);
					return;
				}
				if ((g != gen) || paused || (owner == null) || !isEnabled(ctx) || (service != null)) {
					try {
						fs.shutdown();
					} catch (Throwable ignore) {
					}
					// bu arada durdurulup yeniden istendiyse (asistan bitti) şimdi başlat
					if (!paused && (owner != null) && (service == null) && isEnabled(ctx)) start(owner);
					return;
				}
				service = fs;
				fs.startListening(listener);
				YoldaLog.add("Hey Yolda: dinliyor (" + (a.isCarActivity() ? "araç" : "telefon") + ")");
				App.get().getHandler().postDelayed(() -> checkSilenced(a, fs), 3000);
			});
		});
	}

	/** Son denetimde Android mikrofonu susturuyor muydu (test ve tanı için). */
	static volatile boolean silenced;
	private static boolean silencedNoticeShown;

	/**
	 * Android arka plandaki uygulamaya mikrofonu sessiz verir (hata da vermez): araç bağlıyken telefonda Yolda
	 * açılmadıysa "Hey Yolda" hiçbir şey duymaz. Bunu algılayıp nedenini söyler.
	 */
	private static void checkSilenced(MainActivityDelegate a, SpeechService s) {
		if ((service != s) || (Build.VERSION.SDK_INT < 29)) return;
		try {
			android.media.AudioManager am = a.getContext().getSystemService(android.media.AudioManager.class);
			boolean sil = false;
			for (android.media.AudioRecordingConfiguration c : am.getActiveRecordingConfigurations()) {
				if (c.isClientSilenced()) sil = true;
			}
			silenced = sil;
			if (!sil) return;
			YoldaLog.add("Hey Yolda: Android mikrofonu susturuyor (Yolda telefonda arka planda, hizmet başlamamış)");
			if (!silencedNoticeShown) {
				silencedNoticeShown = true;
				UiUtils.showInfo(a.getContext(), "“Hey Yolda” şu an duyamıyor: Android mikrofonu kapattı. Arabaya "
						+ "bağlanmadan önce telefonda Yolda'yı bir kez aç; bildirimde “Hey Yolda dinliyor” görünür.");
			}
		} catch (Throwable ex) {
			Log.d(ex);
		}
	}

	/** Sesli asistan mikrofonu kullanırken. */
	public static void pause() {
		paused = true;
		stopService();
	}

	/**
	 * Bir ekran arka plana geçti. Araç bağlanınca telefondaki uygulama arka plana geçer; araç ekranı
	 * dinlerken telefonun duraklaması dinlemeyi kapatmamalı (önceki hata buydu).
	 */
	public static void pause(MainActivityDelegate a) {
		if ((owner != null) && (owner != a)) return;
		YoldaLog.add("Hey Yolda: dinleme durdu (ekran arka planda)");
		pause();
	}

	public static void stop() {
		paused = true;
		stopService();
	}

	/** Sesli asistan bitince biraz bekleyip yeniden dinlemeye başla. */
	public static void resumeSoon() {
		paused = false;
		retrySoon(1200);
	}

	private static void retrySoon(long delay) {
		MainActivityDelegate a = owner;
		if (a == null) return;
		App.get().getHandler().postDelayed(() -> {
			if ((owner == a) && (service == null) && !paused) start(a);
		}, delay);
	}

	/**
	 * Dinlemeyi hemen bırakır: mikrofon bu çağrı dönünce boştur (sesli asistan hemen ardından mikrofonu
	 * açabilir). Vosk'un okuma iş parçacığı en fazla bir ses parçası (~0,2 sn) sonra durur.
	 */
	private static void stopService() {
		gen++;
		SpeechService s = service;
		service = null;
		if (s != null) {
			try {
				s.stop();
				s.shutdown();
			} catch (Throwable ignore) {
			}
		}
	}

	private static final RecognitionListener listener = new RecognitionListener() {
		@Override
		public void onPartialResult(String hypothesis) {
			check(hypothesis);
		}

		@Override
		public void onResult(String hypothesis) {
			check(hypothesis);
		}

		@Override
		public void onFinalResult(String hypothesis) {
			check(hypothesis);
		}

		@Override
		public void onError(Exception e) {
			Log.e(e, "Hey Yolda dinleme hatası");
			stopService();
			resumeSoon();
		}

		@Override
		public void onTimeout() {
			stopService();
			resumeSoon();
		}
	};

	/** Tanınan metinde "hey yolda" geçiyorsa (yalnız "yolda" yetmez: konuşmada sık geçer) asistanı aç. */
	static boolean isWake(String json) {
		if (json == null) return false;
		String s = json.toLowerCase(new Locale("tr"));
		if (s.contains("hey yolda") || s.contains("ey yolda") || s.contains("hey yol da") || s.contains("hey yola")
				|| s.contains("ey yola") || s.contains("heyolda") || s.contains("eyolda")) return true;
		// Vosk sonucu: {"text" : "[unk] yolda"} ya da {"partial" : "yolda"} — söylenen yalnız "(hey) yolda" ise
		int q = s.lastIndexOf(':');
		if (q < 0) return false;
		String[] words = s.substring(q + 1).replaceAll("[\"{}]+", " ").trim().split("\\s+");
		int unk = 0;
		StringBuilder rest = new StringBuilder();
		for (String w : words) {
			if (w.equals("[unk]")) unk++;
			else if (!w.isEmpty()) rest.append(rest.length() > 0 ? " " : "").append(w);
		}
		// "yolda" ya da "<bilinmeyen tek sözcük> yolda" (ör. modelde olmayan "hey")
		return (unk <= 1) && (rest.toString().equals("yolda") || rest.toString().equals("yola"));
	}

	private static void check(String json) {
		if ((json != null) && json.contains("yolda")) YoldaLog.add("Hey Yolda: duyulan " + json.replaceAll("\\s+", " "));
		if (!isWake(json)) return;
		long now = System.currentTimeMillis();
		if (now - lastWake < 3000) return;
		lastWake = now;
		MainActivityDelegate a = (carOwner != null) ? carOwner : owner;
		if (a == null) return;
		YoldaLog.add("Hey Yolda duyuldu (" + (a.isCarActivity() ? "araç" : "telefon") + " ekranında işleniyor)");
		pause(); // mikrofonu sesli asistana bırak
		App.get().getHandler().postDelayed(() -> {
			try {
				a.startVoiceAssistant();
			} catch (Throwable ex) {
				Log.e(ex, "Sesli asistan açılamadı");
				resumeSoon();
			}
		}, 250);
	}

	// ---- model indirme ----

	private static boolean downloadNoticeShown;
	private static long lastFail;

	private static void download(MainActivityDelegate a) {
		if (downloading) return;
		// Başarısız olduysa her açılışta yeniden deneyip uyarı göstermesin: 10 dakika bekle
		if ((lastFail > 0) && (System.currentTimeMillis() - lastFail < 10 * 60_000L)) return;
		downloading = true;
		Context ctx = a.getContext().getApplicationContext();
		if (!downloadNoticeShown) {
			downloadNoticeShown = true;
			UiUtils.showInfo(a.getContext(), "“Hey Yolda” için Türkçe ses modeli indiriliyor (37 MB, bir kez)…");
		}
		YoldaLog.add("Hey Yolda modeli indiriliyor");
		App.get().getExecutor().submit(() -> {
			File tmp = new File(ctx.getCacheDir(), "vosk-tr.zip");
			try {
				HttpURLConnection c = (HttpURLConnection) new URL(MODEL_URL).openConnection();
				c.setConnectTimeout(20000);
				c.setReadTimeout(60000);
				c.setInstanceFollowRedirects(true);
				c.setRequestProperty("User-Agent", "Yolda/2");
				int code = c.getResponseCode();
				if (code != 200) throw new java.io.IOException("Sunucu yanıtı " + code);
				try (InputStream in = new BufferedInputStream(c.getInputStream());
						 OutputStream out = new FileOutputStream(tmp)) {
					byte[] buf = new byte[65536];
					for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
				}
				unzip(tmp, modelDir(ctx));
				YoldaLog.add("Hey Yolda modeli indirildi");
				App.get().getHandler().post(() -> {
					downloading = false;
					UiUtils.showInfo(a.getContext(), "Hazır: “Hey Yolda” deyince asistan açılır");
					start(a);
				});
			} catch (Throwable ex) {
				Log.e(ex, "Model indirilemedi");
				YoldaLog.add("Hey Yolda modeli indirilemedi: " + ex);
				App.get().getHandler().post(() -> {
					downloading = false;
					lastFail = System.currentTimeMillis();
					UiUtils.showInfo(a.getContext(), "Ses modeli indirilemedi; internet bağlantısını kontrol et");
				});
			} finally {
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
			}
		});
	}

	/** Zip'teki tek üst klasörü atlayarak hedefe açar (vosk-model-small-tr-0.3/… → vosk-tr/…). */
	private static final String BUNDLED = "vosk-tr.zip";

	/** Test: modeli (gerekirse gömülüden açarak) hazırlar; bu iş parçacığında, eşzamanlı. */
	static File prepareModelSync(Context ctx) throws Exception {
		File d = modelDir(ctx);
		if (!isModelReady(ctx)) {
			try (InputStream in = ctx.getAssets().open(BUNDLED)) {
				unzip(in, d);
			}
		}
		return d;
	}

	/** APK'nın içinde ses modeli var mı (GitHub derlemesi gömer). */
	static boolean hasBundledModel(Context ctx) {
		try (InputStream in = ctx.getAssets().open(BUNDLED)) {
			return in.read() >= 0;
		} catch (Exception ex) {
			return false;
		}
	}

	/** İçteki modeli bir kez açar (internet gerekmez), sonra dinlemeye başlar. */
	private static void extractBundled(MainActivityDelegate a) {
		if (downloading) return;
		downloading = true;
		Context ctx = a.getContext().getApplicationContext();
		YoldaLog.add("Hey Yolda modeli uygulamanın içinden açılıyor");
		App.get().getExecutor().submit(() -> {
			try (InputStream in = ctx.getAssets().open(BUNDLED)) {
				unzip(in, modelDir(ctx));
				YoldaLog.add("Hey Yolda modeli hazır (gömülü)");
				App.get().getHandler().post(() -> {
					downloading = false;
					if (isModelReady(ctx)) start(a);
					else YoldaLog.add("Hey Yolda: model açıldı ama tanınmadı; dosyalar: "
							+ java.util.Arrays.toString(modelDir(ctx).list()));
				});
			} catch (Throwable ex) {
				Log.e(ex, "Gömülü model açılamadı");
				YoldaLog.add("Gömülü model açılamadı: " + ex + "; internetten indirilecek");
				App.get().getHandler().post(() -> {
					downloading = false;
					download(a);
				});
			}
		});
	}

	private static void unzip(File zip, File dest) throws Exception {
		try (InputStream in = new java.io.FileInputStream(zip)) {
			unzip(in, dest);
		}
	}

	/** Zip'teki tek üst klasörü atlayarak hedefe açar (vosk-model-small-tr-0.3/… → vosk-tr/…). */
	private static void unzip(InputStream src, File dest) throws Exception {
		File part = new File(dest.getParentFile(), dest.getName() + ".part");
		deleteTree(part);
		//noinspection ResultOfMethodCallIgnored
		part.mkdirs();
		String destPath = part.getCanonicalPath() + File.separator;
		try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(src))) {
			byte[] buf = new byte[65536];
			for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
				String name = e.getName();
				int slash = name.indexOf('/');
				if (slash >= 0) name = name.substring(slash + 1);
				if (name.isEmpty()) continue;
				File f = new File(part, name);
				if (!f.getCanonicalPath().startsWith(destPath)) continue; // zip içinden dışarı yazma yok
				if (e.isDirectory()) {
					//noinspection ResultOfMethodCallIgnored
					f.mkdirs();
					continue;
				}
				//noinspection ResultOfMethodCallIgnored
				f.getParentFile().mkdirs();
				try (OutputStream out = new FileOutputStream(f)) {
					for (int n; (n = zin.read(buf)) > 0; ) out.write(buf, 0, n);
				}
			}
		}
		deleteTree(dest);
		if (!part.renameTo(dest)) throw new IllegalStateException("Model klasörü taşınamadı");
	}

	private static void deleteTree(File f) {
		File[] l = f.listFiles();
		if (l != null) for (File c : l) deleteTree(c);
		//noinspection ResultOfMethodCallIgnored
		f.delete();
	}

	private static File modelDir(Context ctx) {
		return new File(ctx.getApplicationContext().getFilesDir(), MODEL_DIR);
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}
}
