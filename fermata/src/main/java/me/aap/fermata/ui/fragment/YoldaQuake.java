package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Son depremler kartı: bulunduğun yerin 300 km çevresinde son 24 saatteki 2,0 ve üzeri depremler. Kaynak EMSC
 * (Avrupa-Akdeniz Sismoloji Merkezi) FDSN olay servisi: anahtarsız, belgeli, CC BY 4.0 (kaynak gösterilir). Liste
 * 5 dakika saklanır; yalnız kart görünürken sorulur. 5,0 ve üzeri, 15 dakikadan yeni bir deprem bir kez sesli
 * söylenir (kartın listesinden kapatılabilir).
 */
public final class YoldaQuake {
	/** Büyüklük, konum, derinlik (km), zaman, bölge adı, sana uzaklık (km). */
	public record Quake(String id, double mag, double lat, double lon, double depthKm, long timeMs, String region,
											double distKm) {
	}

	static final double RADIUS_KM = 300, MIN_MAG = 2.0, WARN_MAG = 4.0, ALERT_MAG = 5.0;
	static final long CACHE_MS = 5 * 60_000L, ALERT_FRESH_MS = 15 * 60_000L;
	static final String VOICE_KEY = "quake_voice";

	private static volatile List<Quake> last = Collections.emptyList();
	private static volatile long lastAt;
	private static volatile boolean asking;
	private static final Set<String> spoken = new HashSet<>();

	private YoldaQuake() {
	}

	/** Saklanan son liste (ağ isteği yapmaz). */
	public static List<Quake> cached() {
		return last;
	}

	public static boolean loaded() {
		return lastAt != 0;
	}

	static String url(double la, double lo, long nowMs) {
		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
		f.setTimeZone(TimeZone.getTimeZone("UTC"));
		return String.format(Locale.ROOT, "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&lat=%.3f&lon=%.3f"
						+ "&maxradius=%.2f&minmag=%.1f&limit=30&orderby=time&starttime=%s", la, lo, RADIUS_KM / 111.2, MIN_MAG,
				f.format(new java.util.Date(nowMs - 24 * 3600_000L)));
	}

	/** "2026-11-20T09:12:33.4Z" → ms (UTC); okunamazsa 0. */
	static long time(String iso) {
		if ((iso == null) || (iso.length() < 19)) return 0;
		try {
			SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
			f.setTimeZone(TimeZone.getTimeZone("UTC"));
			return f.parse(iso.substring(0, 19)).getTime();
		} catch (Exception ex) {
			return 0;
		}
	}

	/** EMSC GeoJSON → depremler (en yeni önce), uzaklık bulunduğun yere göre; 300 km dışı atılır. */
	static List<Quake> parse(String json, double la, double lo) throws Exception {
		JSONArray fs = new JSONObject(json).optJSONArray("features");
		List<Quake> out = new ArrayList<>();
		if (fs == null) return out;
		for (int i = 0; i < fs.length(); i++) {
			JSONObject p = fs.getJSONObject(i).optJSONObject("properties");
			if ((p == null) || !p.has("mag") || !p.has("lat") || !p.has("lon")) continue;
			double qa = p.optDouble("lat"), qo = p.optDouble("lon");
			double d = YoldaRadarCore.dist(la, lo, qa, qo) / 1000.0;
			if (d > RADIUS_KM) continue;
			out.add(new Quake(p.optString("unid", fs.getJSONObject(i).optString("id", String.valueOf(i))), p.optDouble("mag"),
					qa, qo, p.optDouble("depth", 0), time(p.optString("time")), region(p.optString("flynn_region", "")), d));
		}
		out.sort((x, y) -> Long.compare(y.timeMs(), x.timeMs()));
		return out;
	}

	/** EMSC bölge adı (İngilizce, büyük harf) → okunur Türkçe: "WESTERN TURKEY" → "Batı Türkiye". */
	static String region(String r) {
		if ((r == null) || r.isEmpty()) return "Bilinmeyen bölge";
		String s = r.toUpperCase(Locale.ROOT);
		String[][] w = {{"SEA OF MARMARA", "Marmara Denizi"}, {"MARMARA SEA", "Marmara Denizi"}, {"AEGEAN SEA", "Ege Denizi"},
				{"BLACK SEA", "Karadeniz"}, {"EASTERN MEDITERRANEAN SEA", "Doğu Akdeniz"}, {"CENTRAL MEDITERRANEAN SEA", "Orta Akdeniz"},
				{"DODECANESE ISLANDS", "Onikiadalar"}, {"CRETE", "Girit"}, {"CYPRUS REGION", "Kıbrıs açıkları"}, {"CYPRUS", "Kıbrıs"},
				{"TURKEY-SYRIA BORDER REGION", "Türkiye-Suriye sınırı"}, {"TURKEY-IRAN BORDER REGION", "Türkiye-İran sınırı"},
				{"GEORGIA", "Gürcistan"}, {"ARMENIA", "Ermenistan"}, {"AZERBAIJAN", "Azerbaycan"}, {"BULGARIA", "Bulgaristan"},
				{"GREECE", "Yunanistan"}, {"SYRIA", "Suriye"}, {"IRAN", "İran"}, {"IRAQ", "Irak"},
				{"WESTERN TURKEY", "Batı Türkiye"}, {"EASTERN TURKEY", "Doğu Türkiye"}, {"CENTRAL TURKEY", "İç Anadolu"},
				{"TURKEY", "Türkiye"}, {"NORTHERN", "Kuzey"}, {"SOUTHERN", "Güney"}, {"WESTERN", "Batı"}, {"EASTERN", "Doğu"},
				{"CENTRAL", "Orta"}, {"REGION", "bölgesi"}, {"NEAR COAST OF ", ""}, {"OFFSHORE ", ""}};
		for (String[] x : w) s = s.replace(x[0], x[1]);
		// çevrilmeyen kalan İngilizce sözcükleri baş harfi büyük yaz
		StringBuilder b = new StringBuilder();
		for (String part : s.split(" ")) {
			if (part.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			boolean upper = part.equals(part.toUpperCase(Locale.ROOT)) && (part.length() > 1);
			b.append(upper ? (part.charAt(0) + part.substring(1).toLowerCase(Locale.ROOT)) : part);
		}
		return b.toString().replace(" ,", ",");
	}

	/** "4,3" (Türkçe ondalık). */
	public static String mag(Quake q) {
		return String.format(new Locale("tr"), "%.1f", q.mag());
	}

	/** "12 dk önce" / "3 sa önce" / "az önce". */
	public static String ago(long timeMs, long nowMs) {
		long m = Math.max(0, (nowMs - timeMs) / 60_000L);
		if (m < 1) return "az önce";
		return (m < 60) ? (m + " dk önce") : ((m / 60) + " sa önce");
	}

	/** Kartta: "4,3 · Batı Türkiye · 142 km · 12 dk önce". */
	public static String line(Quake q, long nowMs) {
		return mag(q) + " · " + q.region() + " · " + Math.round(q.distKm()) + " km · " + ago(q.timeMs(), nowMs);
	}

	/** Büyüklük rengi: 5+ kırmızı, 4+ sarı, altı soluk. */
	public static int color(Quake q) {
		if (q.mag() >= ALERT_MAG) return 0xFFF87171;
		return (q.mag() >= WARN_MAG) ? 0xFFFACC15 : 0xDDFFFFFF;
	}

	/** Sesli söylenmeli mi: 5,0 ve üzeri, 15 dk'dan yeni, daha önce söylenmemiş. */
	static boolean shouldAlert(Quake q, long nowMs, Set<String> done) {
		return (q.mag() >= ALERT_MAG) && (nowMs - q.timeMs() < ALERT_FRESH_MS) && !done.contains(q.id());
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	public static boolean voiceOn(Context ctx) {
		return prefs(ctx).getBoolean(VOICE_KEY, true);
	}

	public static void setVoice(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean(VOICE_KEY, on).apply();
	}

	/** Konum: sürüşteki canlı konum, yoksa son bilinen (hava ile ortak); yoksa null. */
	@Nullable
	private static double[] where(Context ctx) {
		if (!Double.isNaN(YoldaRadar.lat)) return new double[]{YoldaRadar.lat, YoldaRadar.lon};
		SharedPreferences p = prefs(ctx);
		if (p.contains("loc_lat")) return new double[]{p.getFloat("loc_lat", 0), p.getFloat("loc_lon", 0)};
		return null;
	}

	/**
	 * Liste eskiyse arka planda yenile; bitince {@code done} ana iş parçacığında çağrılır. Yeni ve büyük deprem varsa
	 * (ayar açıksa) sesli söylenir.
	 */
	public static void refresh(Context ctx, Runnable done) {
		long now = System.currentTimeMillis();
		if (asking || ((lastAt != 0) && (now - lastAt < CACHE_MS))) return;
		double[] ll = where(ctx);
		if (ll == null) return;
		asking = true;
		Context app = ctx.getApplicationContext();
		me.aap.utils.app.App.get().execute(() -> {
			try {
				List<Quake> l = parse(YoldaInfo.get(url(ll[0], ll[1], System.currentTimeMillis())), ll[0], ll[1]);
				last = l;
				lastAt = System.currentTimeMillis();
				if (voiceOn(app)) {
					for (Quake q : l) {
						synchronized (spoken) {
							if (!shouldAlert(q, lastAt, spoken)) continue;
							spoken.add(q.id());
						}
						YoldaSpeak.sayAlways(app, "Deprem: " + mag(q) + " büyüklüğünde, " + q.region() + ", "
								+ Math.round(q.distKm()) + " kilometre uzakta");
						break; // bir kerede tek uyarı
					}
				}
			} catch (Throwable ex) {
				YoldaLog.add("Deprem listesi alınamadı: " + ex.getClass().getSimpleName());
				lastAt = System.currentTimeMillis() - CACHE_MS + 60_000L; // hata: 1 dk sonra yeniden dene
			} finally {
				asking = false;
			}
		}).onCompletion((v, err) -> me.aap.utils.app.App.get().getHandler().post(done));
	}
}
