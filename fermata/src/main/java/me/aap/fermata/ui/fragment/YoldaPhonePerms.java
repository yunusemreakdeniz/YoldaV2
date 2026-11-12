package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

import me.aap.utils.log.Log;

/**
 * "Yolda Telefon" (telefonun ekranını araçta kullanma / ekran yansıtma) için telefonda verilmesi gereken izinler.
 * Araç bağlıyken uygulama telefonda arka planda olduğu için bu izin pencereleri araçtan açılamıyor; arabaya
 * binmeden önce telefonda Kurulum ekranından bir kez verilir.
 */
public final class YoldaPhonePerms {
	private YoldaPhonePerms() {
	}

	public static boolean overlay(Context ctx) {
		return Settings.canDrawOverlays(ctx);
	}

	public static boolean writeSettings(Context ctx) {
		return Settings.System.canWrite(ctx);
	}

	/** Araç ekranından dokunma (Erişilebilirlik → Yolda V2). */
	public static boolean accessibility(Context ctx) {
		String s = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
		return (s != null) && s.contains(ctx.getPackageName() + "/") && s.contains("AccessibilityEventDispatcherService");
	}

	/** Eksik izinlerin adları (boşsa hepsi tamam). */
	public static List<String> missing(Context ctx) {
		List<String> l = new ArrayList<>();
		if (!overlay(ctx)) l.add("Diğer uygulamaların üzerinde gösterme");
		if (!writeSettings(ctx)) l.add("Sistem ayarlarını değiştirme");
		if (!accessibility(ctx)) l.add("Erişilebilirlik → Yolda V2");
		return l;
	}

	public static void openOverlay(Context ctx) {
		open(ctx, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.getPackageName())));
	}

	public static void openWriteSettings(Context ctx) {
		open(ctx, new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.getPackageName())));
	}

	public static void openAccessibility(Context ctx) {
		open(ctx, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
	}

	/** Uygulama bilgisi: Android 13+ "Kısıtlanmış ayarlara izin ver" buradaki ⋮ menüsünde. */
	public static void openAppInfo(Context ctx) {
		open(ctx, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName())));
	}

	private static void open(Context ctx, Intent i) {
		try {
			ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} catch (Exception ex) {
			Log.e(ex, "Ayar açılamadı: ", i);
			YoldaLog.add("Ayar açılamadı: " + i.getAction() + " · " + ex);
		}
	}
}
