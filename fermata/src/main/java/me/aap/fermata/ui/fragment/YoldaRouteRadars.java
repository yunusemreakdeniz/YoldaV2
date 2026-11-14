package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.aap.fermata.ui.fragment.YoldaRadarCore.Cam;
import me.aap.utils.app.App;

/**
 * Rotadaki resmi radarlar (İçişleri Bakanlığı "İller Arası Radar ve Kontrol Noktası" sayfasının kullandığı
 * servis): hedef girilince bulunduğun ilçe → hedef ilçe için bir kez, telefonun kendisinden, sayfayı açmışsın
 * gibi istenir; 30 dakikada bir yenilenir. Dönen radarlar ve hız koridorlarının başlangıç / bitiş noktaları
 * uyarılara eklenir. Hedef yoksa ya da servis yanıt vermezse OpenStreetMap listesi kullanılır (uyarı kesilmez).
 */
public final class YoldaRouteRadars {
	private static final String BASE = "https://www.icisleri.gov.tr";
	private static final String PAGE = BASE + "/iller-arasi-radar-ve-kontrol-noktasi-uygulama-sayilari";
	private static final String API = BASE + "/ISAYWebPart/PolGenControlPointV2/";
	private static final long REFRESH = 30 * 60_000L, ROUTE_TTL = 8 * 3600_000L;
	private static final ExecutorService NET = Executors.newSingleThreadExecutor();

	/** Rotanın resmi radarları (OpenStreetMap listesine eklenir). */
	public static volatile List<Cam> cams = new ArrayList<>();
	/** Durum: "İstanbul Kadıköy → Ankara Çankaya · 34 radar, 5 hız koridoru (14:20)" ya da hata; yoksa null. */
	@Nullable
	public static volatile String status;
	public static volatile long fetchedAt;
	private static volatile double destLat = Double.NaN, destLon = Double.NaN;
	private static volatile long routeAt;
	private static volatile boolean busy;

	private YoldaRouteRadars() {
	}

	/** Rota sonucu (ayrıştırılmış). */
	public record Route(String from, String to, int radarCount, int controlCount, int corridorCount, List<Cam> cams) {
	}

	// ---- giriş: hedef belli oldu ----

	/** Navigasyon hedefi girildi (adres ya da "lat,lon"): rotanın resmi radarlarını al. */
	public static void onDestination(Context ctx, @Nullable String dest) {
		if ((dest == null) || dest.trim().isEmpty()) return;
		Context app = ctx.getApplicationContext();
		if (!YoldaRadar.isEnabled(app) || !YoldaRadar.feature(app, "official")) return;
		NET.submit(() -> {
			double[] to = parseLatLon(dest);
			if (to == null) to = geocode(app, dest);
			if (to == null) {
				status = "Hedefin konumu bulunamadı; OpenStreetMap radarları kullanılıyor";
				YoldaLog.add("Resmi radarlar: hedef bulunamadı (" + dest + ")");
				return;
			}
			destLat = to[0];
			destLon = to[1];
			routeAt = System.currentTimeMillis();
			refresh(app);
		});
	}

	/** Konum geldikçe: 30 dk'da bir yenile; hedefe varınca (300 m) ya da 8 saat sonra rotayı bırak. */
	static void onLocation(Context ctx, double la, double lo) {
		if (Double.isNaN(destLat) || busy) return;
		long now = System.currentTimeMillis();
		if ((now - routeAt > ROUTE_TTL) || (YoldaRadarCore.dist(la, lo, destLat, destLon) < 300)) {
			clear("Hedefe varıldı; OpenStreetMap radarları kullanılıyor");
			return;
		}
		if (now - fetchedAt > REFRESH) NET.submit(() -> refresh(ctx.getApplicationContext()));
	}

	public static void clear(@Nullable String why) {
		destLat = destLon = Double.NaN;
		cams = new ArrayList<>();
		status = why;
		YoldaRadar.routeChanged();
	}

	public static boolean active() {
		return !Double.isNaN(destLat) && !cams.isEmpty();
	}

	// ---- servis ----

	private static void refresh(Context app) {
		if (busy) return;
		busy = true;
		try {
			double la = YoldaRadar.lat, lo = YoldaRadar.lon;
			if (Double.isNaN(la)) {
				double[] last = YoldaRadar.lastKnown(app);
				if (last == null) throw new IllegalStateException("konum yok");
				la = last[0];
				lo = last[1];
			}
			CookieManager jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
			http(jar, "GET", PAGE, null); // sayfayı aç (oturum çerezi)
			JSONArray cities = new JSONArray(http(jar, "GET", API + "GetCities", null));
			int[] from = district(app, jar, cities, la, lo);
			int[] to = district(app, jar, cities, destLat, destLon);
			if ((from == null) || (to == null)) throw new IllegalStateException("ilçe bulunamadı");
			String body = "fromLatitude=" + enc(from[1] / 1e6) + "&fromLongitude=" + enc(from[2] / 1e6)
					+ "&toLatitude=" + enc(to[1] / 1e6) + "&toLongitude=" + enc(to[2] / 1e6)
					+ "&fromDistrictId=" + from[0] + "&toDistrictId=" + to[0];
			Route r = parse(new JSONObject(http(jar, "POST", API + "CreateRoute", body)));
			cams = r.cams();
			fetchedAt = System.currentTimeMillis();
			int corr = 0;
			for (Cam c : r.cams()) if (c.kind() == 'a') corr++;
			status = r.from() + " → " + r.to() + " · " + (r.cams().size() - corr) + " radar, " + (corr / 2) + " hız koridoru ("
					+ new java.text.SimpleDateFormat("HH:mm", new Locale("tr")).format(new java.util.Date()) + ")";
			YoldaLog.add("Resmi radarlar: " + status);
			YoldaRadar.routeChanged();
		} catch (Throwable ex) {
			YoldaLog.add("Resmi radarlar alınamadı: " + ex.getMessage());
			if (cams.isEmpty()) status = "Resmi radarlar alınamadı; OpenStreetMap radarları kullanılıyor";
			fetchedAt = System.currentTimeMillis() - REFRESH + 5 * 60_000L; // 5 dk sonra yeniden dene
		} finally {
			busy = false;
		}
	}

	/** Konuma en yakın ilçe: {id, lat×1e6, lon×1e6}. İl, adına göre (konumun il adı) ya da en yakın ilçeye göre. */
	@Nullable
	private static int[] district(Context app, CookieManager jar, JSONArray cities, double la, double lo) throws Exception {
		String il = adminArea(app, la, lo);
		List<Integer> tryCities = new ArrayList<>();
		for (int i = 0; i < cities.length(); i++) {
			JSONObject c = cities.getJSONObject(i);
			if ((il != null) && fold(c.optString("Name")).equals(fold(il))) tryCities.add(c.getInt("Id"));
		}
		if (tryCities.isEmpty()) return null;
		int[] best = null;
		double bd = Double.MAX_VALUE;
		for (int cityId : tryCities) {
			JSONArray ds = new JSONArray(http(jar, "GET", API + "GetDistricts?cityId=" + cityId, null));
			for (int i = 0; i < ds.length(); i++) {
				JSONObject d = ds.getJSONObject(i);
				double dla = d.optDouble("Latitude", Double.NaN), dlo = d.optDouble("Longitude", Double.NaN);
				if (Double.isNaN(dla) || Double.isNaN(dlo)) continue;
				double dist = YoldaRadarCore.dist(la, lo, dla, dlo);
				if (dist < bd) {
					bd = dist;
					best = new int[]{d.getInt("Id"), (int) Math.round(dla * 1e6), (int) Math.round(dlo * 1e6)};
				}
			}
		}
		return best;
	}

	/**
	 * CreateRoute yanıtı → radarlar ve hız koridorları (sayfa gibi yalnız etkin olanlar: radar activity 3 ve owner 2,
	 * koridor activity 3). Koridorun başlangıcı ve bitişi "ortalama hız" noktası olarak eklenir.
	 */
	public static Route parse(JSONObject res) throws Exception {
		if (!res.optBoolean("success", true) || !res.has("data") || res.isNull("data"))
			throw new IllegalStateException(res.optString("message", "veri yok"));
		JSONObject d = res.getJSONObject("data");
		List<Cam> l = new ArrayList<>();
		JSONArray rs = d.optJSONArray("Radars");
		if (rs != null) {
			for (int i = 0; i < rs.length(); i++) {
				JSONObject r = rs.getJSONObject(i);
				if ((r.optInt("activity") != 3) || (r.optInt("owner") != 2)) continue;
				double y = r.optDouble("y", Double.NaN), x = r.optDouble("x", Double.NaN);
				if (Double.isNaN(x) || Double.isNaN(y)) continue;
				l.add(new Cam(id('r', y, x), y, x, 0, -1, 'r'));
			}
		}
		JSONArray ts = d.optJSONArray("SpeedTunnels");
		if (ts != null) {
			for (int i = 0; i < ts.length(); i++) {
				JSONObject t = ts.getJSONObject(i);
				if (t.optInt("activity") != 3) continue;
				double sy = t.optDouble("startLatY", Double.NaN), sx = t.optDouble("startLonX", Double.NaN);
				double ey = t.optDouble("endLatY", Double.NaN), ex = t.optDouble("endLonX", Double.NaN);
				if (Double.isNaN(sy) || Double.isNaN(sx) || Double.isNaN(ey) || Double.isNaN(ex)) continue;
				l.add(new Cam(id('a', sy, sx), sy, sx, 0, -1, 'a'));
				l.add(new Cam(id('a', ey, ex), ey, ex, 0, -1, 'a'));
			}
		}
		return new Route(d.optString("FromDistrict"), d.optString("ToDistrict"), d.optInt("RadarCount"),
				d.optInt("ControlPointCount"), d.optInt("CorridorCount"), l);
	}

	/** Kararlı kimlik (OSM kimlikleriyle çakışmasın: negatif, 2^40 üstü). */
	static long id(char kind, double la, double lo) {
		long a = Math.round(la * 1e5), b = Math.round(lo * 1e5);
		return -((1L << 41) + Math.abs((a * 1_000_003L + b) * 31 + kind) % (1L << 40));
	}

	@Nullable
	static double[] parseLatLon(String s) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(-?\\d{1,2}\\.\\d+)\\s*,\\s*(-?\\d{1,3}\\.\\d+)\\s*$").matcher(s);
		return m.matches() ? new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))} : null;
	}

	static String fold(String s) {
		String t = s.toLowerCase(new Locale("tr")).replace('ı', 'i');
		return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "").replaceAll("[^a-z]", "");
	}

	@Nullable
	@SuppressWarnings("deprecation")
	private static double[] geocode(Context app, String q) {
		try {
			List<Address> l = new Geocoder(app, new Locale("tr", "TR")).getFromLocationName(q + ", Türkiye", 1);
			if ((l != null) && !l.isEmpty()) return new double[]{l.get(0).getLatitude(), l.get(0).getLongitude()};
		} catch (Throwable ignore) {
		}
		return null;
	}

	@Nullable
	@SuppressWarnings("deprecation")
	private static String adminArea(Context app, double la, double lo) {
		try {
			List<Address> l = new Geocoder(app, new Locale("tr", "TR")).getFromLocation(la, lo, 1);
			if ((l != null) && !l.isEmpty()) return l.get(0).getAdminArea();
		} catch (Throwable ignore) {
		}
		return null;
	}

	private static String enc(double v) {
		return String.format(Locale.ROOT, "%.6f", v);
	}

	/** Tarayıcı gibi istek: oturum çerezi, sayfanın gönderdiği başlıklar. */
	private static String http(CookieManager jar, String method, String url, @Nullable String form) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(10_000);
		c.setReadTimeout(20_000);
		c.setRequestMethod(method);
		c.setInstanceFollowRedirects(true);
		c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Yolda/2");
		c.setRequestProperty("Accept", url.equals(PAGE) ? "text/html" : "application/json, text/javascript, */*; q=0.01");
		if (!url.equals(PAGE)) {
			c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
			c.setRequestProperty("Referer", PAGE);
		}
		List<HttpCookie> ck = jar.getCookieStore().get(URI.create(BASE));
		if (!ck.isEmpty()) {
			StringBuilder b = new StringBuilder();
			for (HttpCookie h : ck) b.append((b.length() > 0) ? "; " : "").append(h.getName()).append('=').append(h.getValue());
			c.setRequestProperty("Cookie", b.toString());
		}
		if (form != null) {
			byte[] data = form.getBytes(StandardCharsets.UTF_8);
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
			c.setFixedLengthStreamingMode(data.length);
			try (OutputStream o = c.getOutputStream()) {
				o.write(data);
			}
		}
		int code = c.getResponseCode();
		java.util.Map<String, List<String>> hdr = c.getHeaderFields();
		jar.put(URI.create(BASE), hdr);
		if (code != 200) throw new IllegalStateException(url.substring(url.lastIndexOf('/') + 1) + ": HTTP " + code);
		try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buf = new byte[16384];
			for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
			return out.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
	}
}
