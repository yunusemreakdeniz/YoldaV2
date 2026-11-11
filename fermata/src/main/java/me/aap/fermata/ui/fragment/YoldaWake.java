package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

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
	private static final String GRAMMAR = "[\"hey yolda\", \"hey yol da\", \"yolda\", \"[unk]\"]";

	private static Model model;
	@Nullable
	private static SpeechService service;
	@Nullable
	private static MainActivityDelegate owner;
	private static boolean paused;
	private static boolean downloading;
	private static long lastWake;

	private YoldaWake() {
	}

	public static boolean isEnabled(Context ctx) {
		return prefs(ctx).getBoolean(PREF, false);
	}

	public static boolean isModelReady(Context ctx) {
		return new File(modelDir(ctx), "conf").isDirectory() || new File(modelDir(ctx), "am").isDirectory();
	}

	/** Menüden aç/kapat; açılırken model yoksa indirilir. */
	public static void setEnabled(MainActivityDelegate a, boolean on) {
		Context ctx = a.getContext();
		prefs(ctx).edit().putBoolean(PREF, on).apply();
		if (on) start(a);
		else stop();
	}

	/** Uygulama ekrana gelince (ve sesli asistan bitince) çağrılır. */
	public static void start(MainActivityDelegate a) {
		Context ctx = a.getContext();
		owner = a;
		paused = false;
		if (!isEnabled(ctx) || (service != null)) return;
		if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			YoldaLog.add("Hey Yolda: mikrofon izni yok");
			return;
		}
		if (!isModelReady(ctx)) {
			download(a);
			return;
		}
		App.get().getExecutor().submit(() -> {
			try {
				LibVosk.setLogLevel(LogLevel.WARNINGS);
				if (model == null) model = new Model(modelDir(ctx).getAbsolutePath());
				Recognizer rec = new Recognizer(model, 16000.0f, GRAMMAR);
				SpeechService s = new SpeechService(rec, 16000.0f);
				App.get().getHandler().post(() -> {
					if (paused || (owner == null) || !isEnabled(ctx)) {
						s.shutdown();
						return;
					}
					service = s;
					s.startListening(listener);
				});
			} catch (Throwable ex) {
				Log.e(ex, "Hey Yolda başlatılamadı");
				YoldaLog.add("Hey Yolda başlatılamadı: " + ex);
			}
		});
	}

	/** Uygulama arka plana geçince ya da sesli asistan mikrofonu kullanırken. */
	public static void pause() {
		paused = true;
		stopService();
	}

	public static void stop() {
		paused = true;
		stopService();
	}

	/** Sesli asistan bitince biraz bekleyip yeniden dinlemeye başla. */
	public static void resumeSoon() {
		MainActivityDelegate a = owner;
		if (a == null) return;
		App.get().getHandler().postDelayed(() -> {
			if ((owner == a) && (service == null)) start(a);
		}, 1200);
	}

	private static void stopService() {
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
		return s.contains("hey yolda") || s.contains("hey yol da");
	}

	private static void check(String json) {
		if (!isWake(json)) return;
		long now = System.currentTimeMillis();
		if (now - lastWake < 3000) return;
		lastWake = now;
		MainActivityDelegate a = owner;
		if (a == null) return;
		YoldaLog.add("Hey Yolda duyuldu");
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

	private static void download(MainActivityDelegate a) {
		if (downloading) return;
		downloading = true;
		Context ctx = a.getContext().getApplicationContext();
		UiUtils.showInfo(a.getContext(), "“Hey Yolda” için Türkçe ses modeli indiriliyor (37 MB, bir kez)…");
		App.get().getExecutor().submit(() -> {
			File tmp = new File(ctx.getCacheDir(), "vosk-tr.zip");
			try {
				HttpURLConnection c = (HttpURLConnection) new URL(MODEL_URL).openConnection();
				c.setConnectTimeout(20000);
				c.setReadTimeout(30000);
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
					UiUtils.showInfo(a.getContext(), "Ses modeli indirilemedi; internet bağlantısını kontrol et");
				});
			} finally {
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
			}
		});
	}

	/** Zip'teki tek üst klasörü atlayarak hedefe açar (vosk-model-small-tr-0.3/… → vosk-tr/…). */
	private static void unzip(File zip, File dest) throws Exception {
		File part = new File(dest.getParentFile(), dest.getName() + ".part");
		deleteTree(part);
		//noinspection ResultOfMethodCallIgnored
		part.mkdirs();
		String destPath = part.getCanonicalPath() + File.separator;
		try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zip)))) {
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
