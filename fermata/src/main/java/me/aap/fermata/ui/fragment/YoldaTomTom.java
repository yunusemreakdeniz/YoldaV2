package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;

import me.aap.fermata.ui.view.YoldaStyle;

/**
 * TomTom Snap to Roads ile hız sınırı (isteğe bağlı): kullanıcı Menü'den kendi ücretsiz TomTom anahtarını girerse,
 * haritada (OSM) sınırı yazılı olmayan yollarda son GPS noktaları TomTom'a gönderilir, bulunulan yol parçasının
 * sınırı alınır. Sıra: OSM'de yazılı sınır, sonra TomTom, en son tahmin. Ücretsiz kota (günde 2.500 istek) aşılmasın:
 * en çok 20 sn ve 400 m'de bir, günde en çok 2.000 istek; sonuç 5 dk / 600 m geçerli, kalıcı saklanmaz.
 * Anahtar yalnız bu telefonda saklanır.
 */
public final class YoldaTomTom {
	static final String KEY = "tomtom_key";
	static final int DAILY_MAX = 2000;
	static final long MIN_GAP_MS = 20_000, VALID_MS = 5 * 60_000L, BACKOFF_429_MS = 60 * 60_000L;
	static final double MIN_MOVE_M = 400, VALID_M = 600, POINT_GAP_M = 25;
	static final String FIELDS = "{projectedPoints{properties{routeIndex,snapResult}},route{properties{id,speedLimits{value,unit,type}}}}";

	private static final java.util.concurrent.ExecutorService NET = java.util.concurrent.Executors.newSingleThreadExecutor();
	/** Son noktalar: {lat, lon, yön}. */
	private static final ArrayDeque<double[]> pts = new ArrayDeque<>();
	private static volatile int limit;
	private static volatile long limitAt, lastAskAt, backoffUntil;
	private static volatile double limitLat, limitLon, askLat = Double.NaN, askLon;
	private static volatile boolean asking;
	/** Anahtar geçersiz çıktı (403): yeniden girilene kadar sorulmaz. */
	static volatile boolean keyBad;

	private YoldaTomTom() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	public static boolean isEnabled(Context ctx) {
		String k = prefs(ctx).getString(KEY, null);
		return (k != null) && !k.trim().isEmpty();
	}

	public static void setKey(Context ctx, @Nullable String key) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		if ((key == null) || key.trim().isEmpty()) e.remove(KEY);
		else e.putString(KEY, key.trim());
		e.apply();
		keyBad = false;
		backoffUntil = 0;
		limit = 0;
	}

	/** Sorulmalı mı: kota, bekleme, en az 20 sn ve (ilk değilse) 400 m. */
	static boolean shouldAsk(long now, long lastAsk, double movedM, int countToday, long backoff) {
		if (now < backoff) return false;
		if (countToday >= DAILY_MAX) return false;
		if ((lastAsk != 0) && (now - lastAsk < MIN_GAP_MS)) return false;
		return (lastAsk == 0) || Double.isNaN(movedM) || (movedM >= MIN_MOVE_M);
	}

	/** Alınan sınır hâlâ geçerli mi (5 dk ve 600 m). */
	static boolean valid(long now, long at, double distM) {
		return (at != 0) && (now - at < VALID_MS) && (distM < VALID_M);
	}

	/** İstek adresi: noktalar boylam,enlem (TomTom sırası); yön yoksa boş. */
	static String url(String key, java.util.List<double[]> points) throws Exception {
		StringBuilder p = new StringBuilder(), h = new StringBuilder();
		for (int i = 0; i < points.size(); i++) {
			double[] q = points.get(i);
			if (i > 0) {
				p.append(';');
				h.append(';');
			}
			p.append(String.format(Locale.ROOT, "%.6f,%.6f", q[1], q[0]));
			if (q[2] >= 0) h.append(Math.round(q[2]) % 360);
		}
		return "https://api.tomtom.com/snapToRoads/1?key=" + URLEncoder.encode(key, "UTF-8")
				+ "&points=" + URLEncoder.encode(p.toString(), "UTF-8")
				+ "&headings=" + URLEncoder.encode(h.toString(), "UTF-8")
				+ "&fields=" + URLEncoder.encode(FIELDS, "UTF-8")
				+ "&vehicleType=PassengerCar&measurementSystem=metric";
	}

	/** Yanıttan son noktanın (bulunulan yer) yolunun azami sınırı (km/s); yoksa 0. */
	static int parse(String json) throws Exception {
		JSONObject o = new JSONObject(json);
		JSONArray pp = o.optJSONArray("projectedPoints"), route = o.optJSONArray("route");
		if ((pp == null) || (route == null)) return 0;
		for (int k = pp.length() - 1; k >= 0; k--) { // en son eşleşen nokta
			JSONObject pr = pp.getJSONObject(k).optJSONObject("properties");
			if ((pr == null) || !"Matched".equals(pr.optString("snapResult")) || pr.isNull("routeIndex")) continue;
			int ri = pr.optInt("routeIndex", -1);
			if ((ri < 0) || (ri >= route.length())) continue;
			JSONObject rp = route.getJSONObject(ri).optJSONObject("properties");
			JSONObject sl = (rp != null) ? rp.optJSONObject("speedLimits") : null;
			if ((sl == null) || !"Maximum".equalsIgnoreCase(sl.optString("type", "Maximum"))) return 0;
			int v = sl.optInt("value", 0);
			if ("mph".equalsIgnoreCase(sl.optString("unit"))) v = Math.round(v * 1.609f);
			return ((v >= 5) && (v <= 160)) ? v : 0;
		}
		return 0;
	}

	/**
	 * Başka bir TomTom isteği (trafik kartının canlı süresi) için anahtar: anahtar girilmiş, geçersiz değil, kota ve
	 * bekleme uygunsa döner ve isteği günlük sayaca yazar; değilse null (çağıran ücretsiz kaynağa düşer).
	 */
	@Nullable
	static String takeKey(Context ctx) {
		if (keyBad || (System.currentTimeMillis() < backoffUntil)) return null;
		SharedPreferences p = prefs(ctx);
		String k = p.getString(KEY, null);
		if ((k == null) || k.trim().isEmpty()) return null;
		synchronized (YoldaTomTom.class) {
			if (countToday(p, false) >= DAILY_MAX) return null;
			countToday(p, true);
		}
		return k.trim();
	}

	/** Başka bir TomTom isteğinin yanıt kodu: 403 anahtarı, 429 kotayı kapatır (hız sınırıyla ortak). */
	static void onCode(int code) {
		if (code == 403) keyBad = true;
		else if (code == 429) backoffUntil = System.currentTimeMillis() + BACKOFF_429_MS;
	}

	static String fetch(String url, int[] code) throws Exception {
		return get(url, code);
	}

	/** Bugünkü istek sayısı (gün değişince sıfırlanır). */
	private static int countToday(SharedPreferences p, boolean inc) {
		String day = new java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new java.util.Date());
		int n = day.equals(p.getString("tt_day", "")) ? p.getInt("tt_count", 0) : 0;
		if (inc) p.edit().putString("tt_day", day).putInt("tt_count", ++n).apply();
		return n;
	}

	/**
	 * Her konumda çağrılır. need: OSM'de bu yolun sınırı yazılı değil (yok ya da tahmin). Geçerli TomTom sınırı
	 * varsa döner (km/s), yoksa 0; gerekirse arka planda yeni istek başlatır.
	 */
	public static int onPosition(Context ctx, double la, double lo, double hd, boolean need) {
		synchronized (pts) {
			double[] last = pts.peekLast();
			if ((last == null) || (YoldaRadarCore.dist(last[0], last[1], la, lo) >= POINT_GAP_M)) {
				pts.addLast(new double[]{la, lo, hd});
				while (pts.size() > 6) pts.removeFirst();
			}
		}
		long now = System.currentTimeMillis();
		int cur = valid(now, limitAt, YoldaRadarCore.dist(limitLat, limitLon, la, lo)) ? limit : 0;
		if (!need || keyBad || asking) return cur;
		String key = prefs(ctx).getString(KEY, null);
		if ((key == null) || key.trim().isEmpty()) return cur;
		double moved = Double.isNaN(askLat) ? Double.NaN : YoldaRadarCore.dist(askLat, askLon, la, lo);
		SharedPreferences p = prefs(ctx);
		if ((cur > 0) || !shouldAsk(now, lastAskAt, moved, countToday(p, false), backoffUntil)) return cur;
		java.util.List<double[]> send;
		synchronized (pts) {
			if (pts.size() < 2) return cur; // TomTom en az iki nokta ister
			send = new java.util.ArrayList<>(pts);
		}
		asking = true;
		lastAskAt = now;
		askLat = la;
		askLon = lo;
		countToday(p, true);
		NET.submit(() -> {
			try {
				int[] code = new int[1];
				String body = get(url(key.trim(), send), code);
				if (code[0] == 200) {
					int v = parse(body);
					if (v > 0) {
						limit = v;
						limitAt = System.currentTimeMillis();
						limitLat = la;
						limitLon = lo;
					}
					YoldaLog.add("TomTom hız sınırı: " + ((v > 0) ? (v + " km/s") : "bu yolda yok"));
				} else if (code[0] == 403) {
					keyBad = true;
					YoldaLog.add("TomTom anahtarı geçersiz (403)");
					me.aap.utils.app.App.get().getHandler().post(() ->
							YoldaStyle.toast(ctx, "TomTom anahtarı geçersiz: Menü → Ayarlar'dan yeniden gir"));
				} else if (code[0] == 429) {
					backoffUntil = System.currentTimeMillis() + BACKOFF_429_MS;
					YoldaLog.add("TomTom kotası doldu (429): 1 saat sorulmayacak");
				} else {
					YoldaLog.add("TomTom yanıt vermedi (" + code[0] + ")");
				}
			} catch (Throwable ex) {
				YoldaLog.add("TomTom'a ulaşılamadı: " + ex.getClass().getSimpleName());
			} finally {
				asking = false;
			}
		});
		return cur;
	}

	private static String get(String url, int[] code) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(10000);
		c.setRequestProperty("User-Agent", "Yolda/2 (Android)");
		try {
			code[0] = c.getResponseCode();
			try (InputStream in = (code[0] < 400) ? c.getInputStream() : c.getErrorStream()) {
				if (in == null) return "";
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
				return b.toString(StandardCharsets.UTF_8.name());
			}
		} finally {
			c.disconnect();
		}
	}
}
