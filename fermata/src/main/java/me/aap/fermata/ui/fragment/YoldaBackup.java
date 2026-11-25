package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Ayar yedeği güvenliği: Ayarlar → dışa aktar zip'ine kullanıcının API anahtarları (Gemini, TomTom) girmez
 * (anahtarlar yalnız telefonda kalır; yedek dosyası paylaşılabilir). Geri yüklemede telefondaki anahtarlar korunur.
 */
public final class YoldaBackup {
	/** Yedeğe girmeyen anahtarlar (yolda_home içinde). */
	static final String[] SECRETS = {"gemini_key", "tomtom_key"};
	private static final Pattern SECRET_LINE = Pattern.compile(
			"\s*<string name=\"(?:gemini_key|tomtom_key)\">[^<]*</string>\s*?(\r?\n)?");

	private YoldaBackup() {
	}

	/** Bu tercih dosyası yedeğe temizlenerek mi yazılmalı. */
	public static boolean needsSanitize(String prefsName) {
		return YoldaStyle.PREFS.equals(prefsName);
	}

	/** Tercih XML'inden anahtar satırlarını çıkarır. */
	public static String sanitize(String xml) {
		return SECRET_LINE.matcher(xml).replaceAll("\n").replaceAll("\n{2,}", "\n");
	}

	/** Geri yüklemeden önce telefondaki anahtarları al. */
	public static Map<String, String> keepSecrets(Context ctx) {
		SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
		Map<String, String> m = new HashMap<>();
		for (String k : SECRETS) {
			String v = p.getString(k, null);
			if (v != null) m.put(k, v);
		}
		return m;
	}

	/** Geri yüklemeden sonra anahtarları geri yaz (yedekte yoktu). */
	public static void restoreSecrets(Context ctx, Map<String, String> m) {
		if (m.isEmpty()) return;
		SharedPreferences.Editor e = ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).edit();
		for (Map.Entry<String, String> en : m.entrySet()) e.putString(en.getKey(), en.getValue());
		e.commit(); // uygulama hemen yeniden başlıyor
	}
}
