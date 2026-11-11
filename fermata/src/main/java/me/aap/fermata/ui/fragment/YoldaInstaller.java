package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * Yolda gömülü kurulum: araç ekranında Yolda'nın kendi tasarımının açılması için uygulamanın
 * "Play Store'dan kurulmuş" sayılması gerekir. Shizuku'nun kabuk (shell) yetkisiyle kendimizi
 * {@code pm install -i com.android.vending} ile yeniden kurar. KingInstaller gerekmez.
 * Shizuku sınıfları yoksa ya da erişilemezse her şey sessizce kapanır (uygulama normal çalışır).
 */
public final class YoldaInstaller {
	public static final int SHIZUKU_REQUEST = 7001;
	private static final String PLAY = "com.android.vending";

	private YoldaInstaller() {
	}

	/** Araç modu hazır mı: kurulum kaynağı Play Store (ya da benzeri) görünüyor mu. */
	public static boolean isCarReady(Context ctx) {
		return PLAY.equals(installer(ctx));
	}

	public static String installer(Context ctx) {
		try {
			PackageManager pm = ctx.getPackageManager();
			if (Build.VERSION.SDK_INT >= 30)
				return pm.getInstallSourceInfo(ctx.getPackageName()).getInstallingPackageName();
			return pm.getInstallerPackageName(ctx.getPackageName());
		} catch (Throwable ex) {
			return null;
		}
	}

	// ---- Shizuku durumu ----

	public static boolean shizukuInstalled(Context ctx) {
		try {
			ctx.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
			return true;
		} catch (Throwable ex) {
			return false;
		}
	}

	/** Shizuku çalışıyor (binder bağlı) mu. */
	public static boolean shizukuRunning() {
		try {
			return rikka.shizuku.Shizuku.pingBinder();
		} catch (Throwable ex) {
			return false;
		}
	}

	public static boolean shizukuGranted() {
		try {
			return shizukuRunning() && !rikka.shizuku.Shizuku.isPreV11()
					&& (rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED);
		} catch (Throwable ex) {
			return false;
		}
	}

	public static void requestShizuku() {
		try {
			rikka.shizuku.Shizuku.requestPermission(SHIZUKU_REQUEST);
		} catch (Throwable ex) {
			Log.e(ex, "Shizuku izni istenemedi");
		}
	}

	/**
	 * Kendini Play Store kaynağıyla yeniden kurar (Shizuku kabuğunda {@code pm install -i ...}).
	 * Başarılıysa sistem uygulamayı kısa süre sonra yeniden başlatır.
	 */
	public static FutureSupplier<String> makeCarReady(Context ctx) {
		Promise<String> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				if (!shizukuGranted()) {
					p.complete("Önce Shizuku izni gerekli.");
					return;
				}
				String apk = ctx.getApplicationInfo().sourceDir;
				String cmd = "pm install -r -i " + PLAY + " \"" + apk + "\"";
				Process proc = newProcess(new String[]{"sh", "-c", cmd});
				if (proc == null) {
					p.complete("Shizuku kabuğu açılamadı.");
					return;
				}
				StringBuilder out = new StringBuilder();
				try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
					String line;
					while ((line = r.readLine()) != null) out.append(line).append('\n');
				}
				int code = proc.waitFor();
				String res = out.toString().trim();
				if ((code == 0) || res.contains("Success")) p.complete(null); // uygulama birazdan yeniden başlar
				else p.complete("Kurulum tamamlanamadı: " + (res.isEmpty() ? ("kod " + code) : res));
			} catch (Throwable ex) {
				Log.e(ex, "Araç modu hazırlanamadı");
				p.complete("Hata: " + ex.getClass().getSimpleName());
			}
		});
		return p;
	}

	/** Shizuku.newProcess gizli bir yöntem; yansıma ile çağrılır. */
	private static Process newProcess(String[] cmd) throws Exception {
		Method m = rikka.shizuku.Shizuku.class.getDeclaredMethod(
				"newProcess", String[].class, String[].class, String.class);
		m.setAccessible(true);
		return (Process) m.invoke(null, cmd, null, null);
	}
}
