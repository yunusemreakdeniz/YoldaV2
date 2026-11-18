package me.aap.fermata.ui.fragment;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Yolda hava takibi: araç bağlıyken ya da yolculuk sürerken bulunduğun yerin havasını izler (15 dakikada bir ya
 * da 10 km'de bir). Hava kötüleşince (yağmur, kar, sis, fırtına), düzelince, buzlanma sınırına inince ya da bir
 * saat içinde yağış başlayacaksa bildirim gönderir ve sesli söyler (haritadayken de). Sürüş ekranından kapatılır.
 */
public final class YoldaWeatherWatch {
	static final String FEATURE = "weather";
	private static final String CHANNEL = "yolda_weather";
	private static final int NOTIFY_ID = 4711;
	private static final long EVERY_MS = 15 * 60_000L;
	private static final double EVERY_M = 10_000;

	private static long checkedAt;
	private static double checkedLat = Double.NaN, checkedLon = Double.NaN;
	private static volatile boolean busy;
	/** Son bilinen durum: hava türü, sıcaklık, yaklaşan yağış için söylenen tür (aynısı tekrar söylenmesin). */
	private static int lastCode = -1, lastTemp = Integer.MIN_VALUE, saidSoonCat = -1;

	private YoldaWeatherWatch() {
	}

	// ---- hava türleri (WMO kodları) ----

	static final int CLEAR = 0, CLOUDY = 1, FOG = 2, RAIN = 3, SNOW = 4, STORM = 5;

	static int cat(int code) {
		if (code <= 0) return CLEAR;
		if (code <= 3) return CLOUDY;
		if ((code == 45) || (code == 48)) return FOG;
		if ((code >= 71 && code <= 77) || (code == 85) || (code == 86)) return SNOW;
		if (code >= 95) return STORM;
		if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
		return CLOUDY;
	}

	private static boolean bad(int c) {
		return c >= FOG;
	}

	/**
	 * Değişiklik cümlesi (yoksa null).
	 *
	 * @param prevCode önceki WMO kodu (-1: ilk ölçüm, yalnız kötü hava söylenir)
	 * @param soonCode bir saat sonraki kod (-1: bilinmiyor)
	 */
	@Nullable
	static String change(int prevCode, int prevTemp, int code, int temp, int soonCode, int saidSoon, @Nullable String place) {
		String at = ((place != null) && !place.isEmpty() && !"Konumun".equals(place)) ? (place + ": ") : "";
		int pc = (prevCode < 0) ? -1 : cat(prevCode), c = cat(code);
		if ((c != pc) && bad(c)) {
			return at + switch (c) {
				case RAIN -> "Yağmur var. Takip mesafesini artır";
				case SNOW -> "Kar yağıyor. Yavaş ve dikkatli sür";
				case FOG -> "Sis var. Farlarını aç ve yavaşla";
				default -> "Gök gürültülü sağanak var. Dikkatli sür";
			};
		}
		if ((pc >= 0) && bad(pc) && !bad(c)) return at + "Hava düzeldi: " + YoldaWeather.text(code).toLowerCase(new Locale("tr"))
				+ ", " + temp + " derece";
		if ((temp <= 3) && (prevTemp != Integer.MIN_VALUE) && (prevTemp > 3))
			return at + "Hava " + temp + " dereceye düştü. Yollarda buzlanma olabilir";
		if (!bad(c) && (soonCode >= 0) && bad(cat(soonCode)) && (cat(soonCode) != saidSoon)) {
			return at + switch (cat(soonCode)) {
				case RAIN -> "Bir saat içinde yağmur bekleniyor";
				case SNOW -> "Bir saat içinde kar bekleniyor";
				case FOG -> "Bir saat içinde sis bekleniyor";
				default -> "Bir saat içinde fırtına bekleniyor";
			};
		}
		return null;
	}

	// ---- konum geldikçe ----

	/** Sürüşte konum geldikçe çağrılır (YoldaRadar.feed). */
	static void onLocation(Context ctx, double la, double lo) {
		Context app = ctx.getApplicationContext();
		if (busy || !YoldaRadar.feature(app, FEATURE)) return;
		boolean driving = YoldaRadar.trip.isActive();
		try {
			driving |= YoldaCar.isConnected();
		} catch (Throwable ignore) {
		}
		if (!driving) return;
		long now = SystemClock.elapsedRealtime();
		boolean moved = Double.isNaN(checkedLat) || (YoldaRadarCore.dist(checkedLat, checkedLon, la, lo) > EVERY_M);
		if (!moved && (checkedAt != 0) && (now - checkedAt < EVERY_MS)) return;
		checkedAt = now;
		checkedLat = la;
		checkedLon = lo;
		busy = true;
		YoldaWeather.get(app).main().onCompletion((n, err) -> {
			busy = false;
			if (n == null) return;
			int soon = -1;
			List<YoldaWeather.Hour> hs = n.hours();
			if ((hs != null) && (hs.size() > 1)) soon = hs.get(1).code();
			String msg = change(lastCode, lastTemp, n.code(), n.temp(), soon, saidSoonCat, n.place());
			if ((soon >= 0) && !bad(cat(n.code())) && bad(cat(soon))) saidSoonCat = cat(soon);
			if (bad(cat(n.code()))) saidSoonCat = -1;
			lastCode = n.code();
			lastTemp = n.temp();
			if (msg != null) notify(app, msg);
		});
	}

	private static void notify(Context app, String msg) {
		YoldaLog.add("Hava uyarısı: " + msg);
		try {
			NotificationManager nm = app.getSystemService(NotificationManager.class);
			if (Build.VERSION.SDK_INT >= 26) {
				nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Hava uyarıları", NotificationManager.IMPORTANCE_HIGH));
			}
			Notification.Builder b = (Build.VERSION.SDK_INT >= 26) ? new Notification.Builder(app, CHANNEL)
					: new Notification.Builder(app);
			nm.notify(NOTIFY_ID, b.setSmallIcon(R.drawable.notification).setContentTitle("Yolda · hava")
					.setContentText(msg).setStyle(new Notification.BigTextStyle().bigText(msg)).setAutoCancel(true).build());
		} catch (Throwable ex) {
			YoldaLog.add("Hava bildirimi gönderilemedi: " + ex);
		}
		YoldaStyle.toast(app, "🌦 " + msg);
		if (YoldaRadar.voice(app)) YoldaSpeak.sayAlways(app, msg);
	}
}
