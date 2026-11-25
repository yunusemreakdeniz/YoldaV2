package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;

/**
 * Araçtan inince durdur (Mola'dan): Android Auto bağlantısı kesilince çalan müzik / video telefonun hoparlöründen
 * devam etmesin. Kısa kopmalarda (kablo oynadı) durmasın diye birkaç saniye beklenir; o arada yeniden bağlanırsa
 * hiçbir şey yapılmaz. Varsayılan kapalı (menüden açılır).
 */
public final class YoldaCarOff {
	static final String PREF = "car_off_pause";
	static final long WAIT_MS = 4000;
	private static volatile boolean connected;
	private static volatile long gen;

	private YoldaCarOff() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	public static boolean isOn(Context ctx) {
		return prefs(ctx).getBoolean(PREF, false);
	}

	public static void set(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean(PREF, on).apply();
	}

	/** Bekleme bittiğinde durdurulmalı mı: ayar açık, hâlâ bağlı değil, araya yeni bağlantı girmemiş. */
	static boolean shouldPause(boolean on, boolean stillConnected, long startGen, long nowGen) {
		return on && !stillConnected && (startGen == nowGen);
	}

	/** {@link YoldaRadar#carChanged} çağırır. */
	static void carChanged(Context ctx, boolean conn) {
		connected = conn;
		long g = ++gen;
		if (conn || !isOn(ctx)) return;
		App.get().getHandler().postDelayed(() -> {
			if (!shouldPause(isOn(ctx), connected, g, gen)) return;
			MainActivity m = MainActivity.getActiveInstance();
			if (m == null) return;
			m.getActivityDelegate().onSuccess(a -> {
				var cb = a.getMediaSessionCallback();
				if (cb.isPlaying()) {
					cb.onPause();
					YoldaLog.add("Araç bağlantısı kesildi: oynatma durduruldu");
				}
			});
		}, WAIT_MS);
	}
}
