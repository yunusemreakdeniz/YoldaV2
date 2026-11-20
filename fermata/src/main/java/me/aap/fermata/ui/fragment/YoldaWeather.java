package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * Hava durumu kartı: Open-Meteo (ücretsiz, anahtarsız). Konum telefonun bilinen son konumundan (izin yoksa
 * İstanbul); sonuç 15 dakika saklanır, internet yokken son bilinen gösterilir. Çok az veri harcar (~2 KB).
 */
public final class YoldaWeather {
	private static final long CACHE_MS = 15 * 60_000L;
	private static Now cached;
	private static long cachedAt;

	private YoldaWeather() {
	}

	public record Hour(String time, int temp, int code) {
	}

	public record Now(int temp, int code, int wind, String place, List<Hour> hours) {
	}

	/** Saklanan son hava (yoksa null; ağ isteği yapmaz). */
	@Nullable
	public static Now cachedNow() {
		return cached;
	}

	/** Güncel hava (saklanan taze ise hemen). */
	public static FutureSupplier<Now> get(Context ctx) {
		Context app = ctx.getApplicationContext();
		if ((cached != null) && (System.currentTimeMillis() - cachedAt < CACHE_MS) && !moved(app, cachedLl))
			return Completed.completed(cached);
		Promise<Now> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				double[] ll = location(app);
				String place = placeName(app, ll);
				String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
						+ "&current=temperature_2m,weather_code,wind_speed_10m&hourly=temperature_2m,weather_code"
						+ "&forecast_hours=8&timezone=auto", ll[0], ll[1]);
				Now n = parse(get(url), place);
				cached = n;
				cachedAt = System.currentTimeMillis();
				cachedLl = ll;
				prefs(app).edit().putString("weather_last", serialize(n))
						.putFloat("loc_lat", (float) ll[0]).putFloat("loc_lon", (float) ll[1]).apply(); // otomatik tema (gün doğumu)
				p.complete(n);
			} catch (Throwable ex) {
				Log.e(ex, "Hava durumu alınamadı");
				Now last = deserialize(prefs(app).getString("weather_last", null));
				if (last != null) p.complete(last);
				else p.completeExceptionally(ex);
			}
		});
		return p;
	}

	private static double[] cachedLl;

	/** Saklanan hava başka bir yerin mi (3 km'den fazla uzaklaşıldı). */
	private static boolean moved(Context ctx, @Nullable double[] at) {
		if (at == null) return true;
		double[] now = location(ctx);
		return YoldaRadarCore.dist(at[0], at[1], now[0], now[1]) > 3000;
	}

	/**
	 * Konum: sürüşteki canlı konum, yoksa telefonun en yeni bilinen konumu, yoksa son kullanılan konum; hiçbiri
	 * yoksa İstanbul. (Önceden bilinen konum o an alınamayınca doğrudan İstanbul'a düşüyor, sesli asistan
	 * "Fatih" diyordu.)
	 */
	@SuppressLint("MissingPermission")
	private static double[] location(Context ctx) {
		try {
			if (YoldaRadar.hasFix() && !Double.isNaN(YoldaRadar.lat)) return new double[]{YoldaRadar.lat, YoldaRadar.lon};
		} catch (Throwable ignore) {
		}
		double[] l = lastKnown(ctx);
		if (l != null) return l;
		SharedPreferences p = prefs(ctx);
		if (p.contains("loc_lat")) return new double[]{p.getFloat("loc_lat", 41.01f), p.getFloat("loc_lon", 28.98f)};
		return new double[]{41.01, 28.98};
	}

	@SuppressLint("MissingPermission")
	@Nullable
	private static double[] lastKnown(Context ctx) {
		try {
			if ((ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
					== PackageManager.PERMISSION_GRANTED) || (ctx.checkSelfPermission(
					android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
				LocationManager lm = ctx.getSystemService(LocationManager.class);
				Location best = null;
				for (String pr : lm.getProviders(true)) {
					Location l = lm.getLastKnownLocation(pr);
					if ((l != null) && ((best == null) || (l.getTime() > best.getTime()))) best = l;
				}
				if (best != null) return new double[]{best.getLatitude(), best.getLongitude()};
			}
		} catch (Throwable ex) {
			Log.e(ex, "Konum alınamadı");
		}
		return null;
	}

	private static String placeName(Context ctx, double[] ll) {
		try {
			android.location.Geocoder g = new android.location.Geocoder(ctx, new Locale("tr", "TR"));
			@SuppressWarnings("deprecation") List<android.location.Address> a = g.getFromLocation(ll[0], ll[1], 1);
			if ((a != null) && !a.isEmpty()) {
				android.location.Address ad = a.get(0);
				String s = (ad.getSubAdminArea() != null) ? ad.getSubAdminArea() : ad.getLocality();
				if (s == null) s = ad.getAdminArea();
				if (s != null) return s;
			}
		} catch (Throwable ignore) {
		}
		return "Konumun";
	}

	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(10000);
		c.setRequestProperty("User-Agent", "Yolda/2");
		try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
			return out.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
	}

	static Now parse(String json, String place) throws Exception {
		JSONObject o = new JSONObject(json);
		JSONObject cur = o.getJSONObject("current");
		List<Hour> hours = new ArrayList<>();
		JSONObject h = o.optJSONObject("hourly");
		if (h != null) {
			JSONArray t = h.getJSONArray("time"), te = h.getJSONArray("temperature_2m"), wc = h.getJSONArray("weather_code");
			for (int i = 0; i < t.length(); i++) {
				String time = t.getString(i);
				int k = time.indexOf('T');
				hours.add(new Hour((k >= 0) ? time.substring(k + 1) : time, (int) Math.round(te.getDouble(i)), wc.getInt(i)));
			}
		}
		return new Now((int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"),
				(int) Math.round(cur.optDouble("wind_speed_10m", 0)), place, hours);
	}

	// ---- hava durumu ekranı: ayrıntılı tahmin ----

	/** Saatlik: saat ("14:00"), sıcaklık, hava kodu, yağış olasılığı (%). */
	public record HourD(String time, int temp, int code, int pop) {
	}

	/** Günlük: tarih ("2026-11-14"), hava kodu, en yüksek / en düşük, yağış olasılığı (%). */
	public record Day(String date, int code, int max, int min, int pop) {
	}

	/** aqi: Avrupa hava kalitesi indeksi (Open-Meteo / CAMS), bilinmiyorsa -1. */
	public record Detail(String place, int temp, int feels, int humidity, int wind, int code, String sunrise, String sunset,
											 List<HourD> hours, List<Day> days, int aqi) {
		Detail withAqi(int a) {
			return new Detail(place, temp, feels, humidity, wind, code, sunrise, sunset, hours, days, a);
		}
	}

	/** Open-Meteo hava kalitesi yanıtı → Avrupa AQI (yoksa -1). */
	static int parseAqi(String json) throws Exception {
		JSONObject c = new JSONObject(json).optJSONObject("current");
		if ((c == null) || c.isNull("european_aqi")) return -1;
		double v = c.optDouble("european_aqi", -1);
		return (Double.isNaN(v) || (v < 0)) ? -1 : (int) Math.round(v);
	}

	/** Avrupa AQI bandı: 0-20 İyi, -40 Makul, -60 Orta, -80 Kötü, -100 Çok kötü, üstü Aşırı kötü. */
	public static String aqiText(int aqi) {
		if (aqi < 0) return "";
		if (aqi <= 20) return "İyi";
		if (aqi <= 40) return "Makul";
		if (aqi <= 60) return "Orta";
		if (aqi <= 80) return "Kötü";
		if (aqi <= 100) return "Çok kötü";
		return "Aşırı kötü";
	}

	/** Bant rengi (iyi yeşil … aşırı kötü mor). */
	public static int aqiColor(int aqi) {
		if (aqi <= 20) return 0xFF34D399;
		if (aqi <= 40) return 0xFFA3E635;
		if (aqi <= 60) return 0xFFFACC15;
		if (aqi <= 80) return 0xFFFB923C;
		if (aqi <= 100) return 0xFFF87171;
		return 0xFFC084FC;
	}

	private static Detail detailCached;
	private static long detailAt;

	/** Ayrıntılı tahmin (15 dk saklanır): anlık, 24 saat, 7 gün. */
	public static FutureSupplier<Detail> detail(Context ctx, boolean force) {
		Context app = ctx.getApplicationContext();
		if (!force && (detailCached != null) && (System.currentTimeMillis() - detailAt < CACHE_MS))
			return Completed.completed(detailCached);
		Promise<Detail> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				double[] ll = location(app);
				String place = placeName(app, ll);
				String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
						+ "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
						+ "&hourly=temperature_2m,weather_code,precipitation_probability&forecast_hours=24"
						+ "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
						+ "&forecast_days=7&timezone=auto", ll[0], ll[1]);
				Detail d = parseDetail(get(url), place);
				try { // hava kalitesi ayrı servis: alınamazsa tahmin yine gösterilir
					d = d.withAqi(parseAqi(get(String.format(Locale.ROOT,
							"https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f&current=european_aqi&timezone=auto",
							ll[0], ll[1]))));
				} catch (Throwable ex) {
					Log.d("Hava kalitesi alınamadı: ", ex.getClass().getSimpleName());
				}
				detailCached = d;
				detailAt = System.currentTimeMillis();
				p.complete(d);
			} catch (Throwable ex) {
				Log.e(ex, "Ayrıntılı hava durumu alınamadı");
				if (detailCached != null) p.complete(detailCached);
				else p.completeExceptionally(ex);
			}
		});
		return p;
	}

	static Detail parseDetail(String json, String place) throws Exception {
		JSONObject o = new JSONObject(json);
		JSONObject cur = o.getJSONObject("current");
		List<HourD> hours = new ArrayList<>();
		JSONObject h = o.optJSONObject("hourly");
		if (h != null) {
			JSONArray t = h.getJSONArray("time"), te = h.getJSONArray("temperature_2m"), wc = h.getJSONArray("weather_code");
			JSONArray pp = h.optJSONArray("precipitation_probability");
			for (int i = 0; i < t.length(); i++) {
				String time = t.getString(i);
				int k = time.indexOf('T');
				hours.add(new HourD((k >= 0) ? time.substring(k + 1) : time, (int) Math.round(te.getDouble(i)), wc.getInt(i),
						(pp != null) ? pp.optInt(i, 0) : 0));
			}
		}
		List<Day> days = new ArrayList<>();
		String rise = "", set = "";
		JSONObject dl = o.optJSONObject("daily");
		if (dl != null) {
			JSONArray t = dl.getJSONArray("time"), wc = dl.getJSONArray("weather_code"), mx = dl.getJSONArray("temperature_2m_max"),
					mn = dl.getJSONArray("temperature_2m_min");
			JSONArray pp = dl.optJSONArray("precipitation_probability_max");
			for (int i = 0; i < t.length(); i++) {
				days.add(new Day(t.getString(i), wc.getInt(i), (int) Math.round(mx.getDouble(i)), (int) Math.round(mn.getDouble(i)),
						(pp != null) ? pp.optInt(i, 0) : 0));
			}
			JSONArray sr = dl.optJSONArray("sunrise"), ss = dl.optJSONArray("sunset");
			if ((sr != null) && (sr.length() > 0)) rise = hm(sr.getString(0));
			if ((ss != null) && (ss.length() > 0)) set = hm(ss.getString(0));
		}
		return new Detail(place, (int) Math.round(cur.getDouble("temperature_2m")),
				(int) Math.round(cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m"))),
				cur.optInt("relative_humidity_2m", 0), (int) Math.round(cur.optDouble("wind_speed_10m", 0)), cur.getInt("weather_code"),
				rise, set, hours, days, -1);
	}

	private static String hm(String iso) {
		int k = iso.indexOf('T');
		return (k >= 0) ? iso.substring(k + 1) : iso;
	}

	/** WMO hava kodu → Türkçe kısa açıklama. */
	public static String text(int code) {
		if (code == 0) return "Açık";
		if (code <= 2) return "Parçalı bulutlu";
		if (code == 3) return "Kapalı";
		if (code <= 48) return "Sisli";
		if (code <= 57) return "Çisenti";
		if (code <= 67) return "Yağmurlu";
		if (code <= 77) return "Karlı";
		if (code <= 82) return "Sağanak";
		if (code <= 86) return "Kar sağanağı";
		return "Gök gürültülü";
	}

	public static String emoji(int code) {
		if (code == 0) return "☀️";
		if (code <= 2) return "⛅";
		if (code == 3) return "☁️";
		if (code <= 48) return "🌫️";
		if (code <= 67) return "🌧️";
		if (code <= 77) return "❄️";
		if (code <= 82) return "🌦️";
		if (code <= 86) return "🌨️";
		return "⛈️";
	}

	private static String serialize(Now n) {
		StringBuilder b = new StringBuilder();
		b.append(n.temp()).append(';').append(n.code()).append(';').append(n.wind()).append(';').append(n.place().replace(";", " "));
		for (Hour h : n.hours()) b.append(';').append(h.time()).append(',').append(h.temp()).append(',').append(h.code());
		return b.toString();
	}

	@Nullable
	private static Now deserialize(@Nullable String s) {
		if (s == null) return null;
		try {
			String[] f = s.split(";");
			List<Hour> hs = new ArrayList<>();
			for (int i = 4; i < f.length; i++) {
				String[] x = f[i].split(",");
				hs.add(new Hour(x[0], Integer.parseInt(x[1]), Integer.parseInt(x[2])));
			}
			return new Now(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3], hs);
		} catch (Exception ex) {
			return null;
		}
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}
}
