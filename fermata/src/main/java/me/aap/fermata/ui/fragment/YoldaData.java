package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Process;

import java.util.Calendar;
import java.util.Locale;

import me.aap.utils.app.App;

/**
 * Mobil veri sayacı ve sınırı: Yolda'nın kendi kullandığı bayt (TrafficStats, uygulama kimliği) 30 saniyede bir
 * okunur; o anda bağlantı mobilse aradaki fark bu ayın mobil toplamına eklenir (ek izin gerekmez). Sınır açıksa
 * %80'de ve sınırda bir kez sesli ve yazılı uyarır; sınırda video kalitesi 360p'ye iner. Kullanıcı "devam et"
 * derse ay sonuna kadar yeniden uyarmaz ve kaliteye dokunmaz. Ay başında sayaç sıfırlanır.
 */
public final class YoldaData {
	private static final String PREFS = "yolda_data";
	private static final long TICK_MS = 30_000;
	private static long lastBytes = -1;
	private static boolean started;

	private YoldaData() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	/** Ay anahtarı: 2026-11. */
	static String month(Calendar c) {
		return String.format(Locale.ROOT, "%d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
	}

	/** Bu ay mobil veride kullanılan bayt. */
	public static long usedThisMonth(Context ctx) {
		SharedPreferences p = prefs(ctx);
		return month(Calendar.getInstance()).equals(p.getString("month", "")) ? p.getLong("mobile", 0) : 0;
	}

	/** Sınır (MB), 0 = kapalı. */
	public static int limitMb(Context ctx) {
		return prefs(ctx).getInt("limit_mb", 0);
	}

	public static void setLimitMb(Context ctx, int mb) {
		prefs(ctx).edit().putInt("limit_mb", Math.max(0, mb)).putInt("warned", 0).putBoolean("cont", false).apply();
	}

	/** Sınır aşıldı ama kullanıcı "devam et" dedi (ay sonuna kadar). */
	public static void continueAnyway(Context ctx) {
		prefs(ctx).edit().putBoolean("cont", true).apply();
	}

	public static boolean continued(Context ctx) {
		return prefs(ctx).getBoolean("cont", false);
	}

	/** "1,4 GB" / "350 MB". */
	public static String size(long bytes) {
		double mb = bytes / (1024.0 * 1024.0);
		return (mb >= 1024) ? String.format(new Locale("tr"), "%.1f GB", mb / 1024) : String.format(new Locale("tr"), "%.0f MB", mb);
	}

	/** Yüzde (sınır kapalıysa -1). */
	public static int percent(long used, int limitMb) {
		if (limitMb <= 0) return -1;
		return (int) Math.min(999, used * 100 / (limitMb * 1024L * 1024L));
	}

	/** Verilecek uyarı aşaması: 0 yok, 1 = %80, 2 = sınır; {@code warned} daha önce verilen en yüksek aşama. */
	static int stage(int percent, int warned) {
		int s = (percent >= 100) ? 2 : (percent >= 80) ? 1 : 0;
		return (s > warned) ? s : 0;
	}

	/** Şu anki bağlantı mobil mi (Wi-Fi değil). */
	public static boolean onMobile(Context ctx) {
		try {
			ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
			Network n = cm.getActiveNetwork();
			NetworkCapabilities c = (n != null) ? cm.getNetworkCapabilities(n) : null;
			return (c != null) && c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
					&& !c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
		} catch (Throwable ex) {
			return false;
		}
	}

	/** Sayacı başlat (uygulama açılışında bir kez). */
	public static synchronized void start(Context ctx) {
		if (started) return;
		started = true;
		Context app = ctx.getApplicationContext();
		App.get().getHandler().post(new Runnable() {
			@Override
			public void run() {
				try {
					tick(app);
				} catch (Throwable ignore) {
				}
				App.get().getHandler().postDelayed(this, TICK_MS);
			}
		});
	}

	private static void tick(Context app) {
		int uid = Process.myUid();
		long now = TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid);
		if (now < 0) return; // cihaz desteklemiyor
		long prev = lastBytes;
		lastBytes = now;
		if ((prev < 0) || (now < prev) || !onMobile(app)) return;
		SharedPreferences p = prefs(app);
		String m = month(Calendar.getInstance());
		SharedPreferences.Editor e = p.edit();
		if (!m.equals(p.getString("month", ""))) { // yeni ay: sıfırla
			e.putString("month", m).putLong("mobile", 0).putInt("warned", 0).putBoolean("cont", false);
		}
		long used = (m.equals(p.getString("month", "")) ? p.getLong("mobile", 0) : 0) + (now - prev);
		e.putLong("mobile", used).apply();
		int lim = limitMb(app);
		int st = stage(percent(used, lim), p.getInt("warned", 0));
		if ((st == 0) || continued(app)) return;
		p.edit().putInt("warned", st).apply();
		String msg = (st == 1) ? ("Mobil verinin yüzde sekseni kullanıldı: " + size(used))
				: ("Mobil veri sınırına ulaşıldı: " + size(used) + ". Video kalitesi 360p'ye indi. Sürdürmek için Telefon durumu kartında Devam et");
		if (st == 2) me.aap.fermata.ui.view.YoldaStyle.setVideoQuality(app, "360");
		YoldaLog.add("Veri: " + msg);
		YoldaAlertLog.add(app, 'd', msg, YoldaRadar.lat, YoldaRadar.lon);
		me.aap.fermata.ui.view.YoldaStyle.toast(app, "📶 " + msg);
		YoldaSpeak.sayAlways(app, msg);
	}
}
