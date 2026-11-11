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
	}

	public static synchronized void add(String line) {
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
