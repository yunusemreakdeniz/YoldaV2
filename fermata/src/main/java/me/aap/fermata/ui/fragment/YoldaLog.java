package me.aap.fermata.ui.fragment;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Yolda tanı günlüğü: çökmeler (yığın izi), uygulamanın açılışları (telefon / araç) ve önemli olaylar
 * cihazda küçük bir dosyaya yazılır. Menü → "Tanı günlüğü" ile görülür ve kopyalanır; araçta
 * "kendini yeniden başlatma" gibi sorunların nedenini bulmak için.
 */
public final class YoldaLog {
	private static final String FILE = "yolda_log.txt";
	private static final int MAX = 48 * 1024;
	private static volatile boolean installed;
	private static Context app;

	private YoldaLog() {
	}

	/** Yakalanmayan her hatayı günlüğe yazar, sonra önceki işleyiciye devreder (bir kez kurulur). */
	public static synchronized void install(Context ctx) {
		app = ctx.getApplicationContext();
		if (installed) return;
		installed = true;
		Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			try {
				add("ÇÖKME [" + t.getName() + "] " + stack(e));
			} catch (Throwable ignore) {
			}
			if (prev != null) prev.uncaughtException(t, e);
		});
		logExitReasons(app);
	}

	/**
	 * Android 11+: uygulamanın önceki kapanışlarının gerçek nedeni (Java çökmesi, yerel çökme, yanıt vermeme,
	 * bellek yetmediği için sistemin kapatması…). "Yolda durduruldu" ve "kendini yeniden başlatıyor"
	 * sorunlarının nedeni böylece günlükte görünür.
	 */
	private static void logExitReasons(Context c) {
		if (android.os.Build.VERSION.SDK_INT < 30) return;
		try {
			android.app.ActivityManager am = c.getSystemService(android.app.ActivityManager.class);
			android.content.SharedPreferences p = c.getSharedPreferences("yolda_log", Context.MODE_PRIVATE);
			long seen = p.getLong("exit_seen", 0);
			long newest = seen;
			java.util.List<android.app.ApplicationExitInfo> l = am.getHistoricalProcessExitReasons(null, 0, 6);
			SimpleDateFormat df = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT);
			for (int i = l.size() - 1; i >= 0; i--) {
				android.app.ApplicationExitInfo e = l.get(i);
				if (e.getTimestamp() <= seen) continue;
				newest = Math.max(newest, e.getTimestamp());
				StringBuilder b = new StringBuilder("Önceki kapanış ").append(df.format(new Date(e.getTimestamp())))
						.append(": ").append(reasonName(e.getReason()));
				if (e.getDescription() != null) b.append(" · ").append(e.getDescription());
				b.append(" · önem ").append(e.getImportance());
				if (e.getPss() > 0) b.append(" · bellek ").append(e.getPss() / 1024).append(" MB");
				if (e.getReason() == 6) b.append('\n').append(anrTrace(e)); // yanıt vermedi: ana iş parçacığı nerede takıldı
				add(b.toString());
			}
			if (newest != seen) p.edit().putLong("exit_seen", newest).apply();
		} catch (Throwable ex) {
			add("Kapanış nedenleri okunamadı: " + ex);
		}
	}

	static String reasonName(int r) {
		return switch (r) {
			case 1 -> "uygulama kendisi çıktı";
			case 2 -> "sinyal ile sonlandı";
			case 3 -> "BELLEK YETMEDİ (sistem kapattı)";
			case 4 -> "ÇÖKME (Java)";
			case 5 -> "YEREL ÇÖKME (native)";
			case 6 -> "YANIT VERMEDİ (ANR)";
			case 7 -> "başlatılamadı";
			case 8 -> "izin değişti";
			case 9 -> "aşırı kaynak kullanımı";
			case 10 -> "kullanıcı kapattı";
			case 11 -> "kullanıcı durdurdu";
			case 12 -> "bağlı işlem kapandı";
			case 13 -> "diğer (sistem)";
			case 14 -> "donduruldu";
			case 15 -> "paket durumu değişti";
			case 16 -> "uygulama güncellendi";
			default -> "bilinmiyor (" + r + ")";
		};
	}

	@androidx.annotation.RequiresApi(30)
	private static String anrTrace(android.app.ApplicationExitInfo e) {
		try (java.io.InputStream in = e.getTraceInputStream()) {
			if (in == null) return "";
			byte[] buf = new byte[200_000];
			int n = 0;
			for (int r; (n < buf.length) && ((r = in.read(buf, n, buf.length - n)) > 0); ) n += r;
			String s = new String(buf, 0, n, StandardCharsets.UTF_8);
			int i = s.indexOf("\"main\"");
			if (i < 0) return "";
			int end = s.indexOf("\n\n", i);
			return s.substring(i, (end < 0) ? Math.min(s.length(), i + 3000) : Math.min(end, i + 3000));
		} catch (Throwable ex) {
			return "";
		}
	}

	public static synchronized void add(String line) {
		android.util.Log.i("Yolda", line); // test ve hata ayıklama kayıtlarında da görünsün
		Context c = app;
		if (c == null) return;
		try {
			File f = new File(c.getFilesDir(), FILE);
			if (f.length() > MAX) trim(f);
			String ts = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT).format(new Date());
			try (FileOutputStream out = new FileOutputStream(f, true)) {
				out.write((ts + "  " + line + "\n").getBytes(StandardCharsets.UTF_8));
			}
		} catch (Throwable ignore) {
		}
	}

	public static String read(Context ctx) {
		try {
			File f = new File(ctx.getApplicationContext().getFilesDir(), FILE);
			if (!f.isFile()) return "";
			byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
			return new String(b, StandardCharsets.UTF_8);
		} catch (Throwable ex) {
			return "Günlük okunamadı: " + ex;
		}
	}

	public static void clear(Context ctx) {
		//noinspection ResultOfMethodCallIgnored
		new File(ctx.getApplicationContext().getFilesDir(), FILE).delete();
	}

	static String stack(Throwable e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String s = sw.toString();
		return (s.length() > 4000) ? s.substring(0, 4000) + "…" : s;
	}

	private static void trim(File f) {
		try {
			byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
			int from = Math.max(0, b.length - MAX / 2);
			try (FileOutputStream out = new FileOutputStream(f, false)) {
				out.write(b, from, b.length - from);
			}
		} catch (Throwable ignore) {
		}
	}
}
