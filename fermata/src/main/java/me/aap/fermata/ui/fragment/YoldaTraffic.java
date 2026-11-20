package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Trafik kartı: bulunduğun yerden eve / işe yol süresi ve mesafe. Kullanıcı TomTom anahtarı girdiyse (hız sınırıyla
 * aynı anahtar ve günlük kota) süre TomTom Routing'den canlı trafikle gelir, gecikme ayrıca yazılır; anahtar yoksa,
 * kota dolduysa ya da TomTom yanıt vermezse açık OSRM rota servisinden "trafik hariç tahmini" gösterilir.
 * Hedef Photon ile koordinata çevrilir; sonuç 10 dakika önbellekte.
 */
public final class YoldaTraffic {
	/** Süre (dk), mesafe (km), trafik gecikmesi (dk) ve canlı trafikle mi hesaplandı. */
	public record Eta(int minutes, double km, int delayMin, boolean live) {
		public Eta(int minutes, double km) {
			this(minutes, km, 0, false);
		}
	}

	private static final Map<String, Object[]> cache = new HashMap<>(); // hedef → {Eta, zaman}

	private YoldaTraffic() {
	}

	/** OSRM yanıtı → süre ve mesafe (bulunamazsa null). */
	@Nullable
	static Eta parseOsrm(String json) throws Exception {
		JSONObject o = new JSONObject(json);
		if (!"Ok".equals(o.optString("code"))) return null;
		JSONArray r = o.optJSONArray("routes");
		if ((r == null) || (r.length() == 0)) return null;
		JSONObject a = r.getJSONObject(0);
		return new Eta((int) Math.round(a.optDouble("duration") / 60.0), a.optDouble("distance") / 1000.0);
	}

	/** TomTom Routing yanıtı → canlı trafikli süre, mesafe ve gecikme (bulunamazsa null). */
	@Nullable
	static Eta parseTomTom(String json) throws Exception {
		JSONArray r = new JSONObject(json).optJSONArray("routes");
		if ((r == null) || (r.length() == 0)) return null;
		JSONObject s = r.getJSONObject(0).optJSONObject("summary");
		if ((s == null) || !s.has("travelTimeInSeconds")) return null;
		return new Eta((int) Math.round(s.optDouble("travelTimeInSeconds") / 60.0), s.optDouble("lengthInMeters") / 1000.0,
				(int) Math.round(s.optDouble("trafficDelayInSeconds", 0) / 60.0), true);
	}

	/** TomTom rota adresi (enlem,boylam sırası). */
	static String tomTomUrl(String key, double la, double lo, double la2, double lo2) throws Exception {
		return String.format(Locale.ROOT, "https://api.tomtom.com/routing/1/calculateRoute/%.5f,%.5f:%.5f,%.5f/json", la, lo, la2, lo2)
				+ "?traffic=true&travelMode=car&routeType=fastest&key=" + java.net.URLEncoder.encode(key, "UTF-8");
	}

	/** Kartta süre notu: "trafik +6 dk" / "trafik akıcı" (canlı) ya da "trafik hariç tahmini". */
	public static String note(Eta e) {
		if (!e.live()) return "trafik hariç tahmini";
		return (e.delayMin() >= 1) ? ("trafik +" + e.delayMin() + " dk") : "trafik akıcı";
	}

	/** Not rengi: akıcı yeşil, 1-9 dk turuncu, 10 dk ve üstü kırmızı; tahminse soluk. */
	public static int noteColor(Eta e) {
		if (!e.live()) return 0xCCFFFFFF;
		if (e.delayMin() < 1) return 0xFF34D399;
		return (e.delayMin() < 10) ? 0xFFFB923C : 0xFFF87171;
	}

	/** "25 dk · 18 km" / "1 sa 10 dk · 92 km". */
	public static String text(Eta e) {
		String t = (e.minutes() >= 60) ? ((e.minutes() / 60) + " sa " + (e.minutes() % 60) + " dk") : (e.minutes() + " dk");
		return t + " · " + String.format(new Locale("tr"), (e.km() < 10) ? "%.1f km" : "%.0f km", e.km());
	}

	/** Bulunduğun yerden hedefe süre (arka planda çağır), bilinmiyorsa null. */
	@Nullable
	public static synchronized Eta eta(String dest) throws Exception {
		double la = YoldaRadar.lat, lo = YoldaRadar.lon;
		if (Double.isNaN(la) || (dest == null) || dest.isEmpty()) return null;
		Object[] c = cache.get(dest);
		if ((c != null) && (System.currentTimeMillis() - (long) c[1] < 10 * 60_000L)) return (Eta) c[0];
		double[] to = YoldaNearby.geocode(dest, la, lo);
		if (to == null) return null;
		Eta e = null;
		String key = YoldaTomTom.takeKey(me.aap.utils.app.App.get());
		if (key != null) {
			try {
				int[] code = new int[1];
				String body = YoldaTomTom.fetch(tomTomUrl(key, la, lo, to[0], to[1]), code);
				if (code[0] == 200) e = parseTomTom(body);
				else {
					YoldaTomTom.onCode(code[0]);
					YoldaLog.add("TomTom trafik yanıt vermedi (" + code[0] + "), OSRM tahmini kullanılıyor");
				}
			} catch (Throwable ex) {
				YoldaLog.add("TomTom trafiğe ulaşılamadı: " + ex.getClass().getSimpleName());
			}
		}
		if (e == null) e = parseOsrm(YoldaInfo.get(String.format(Locale.ROOT,
				"https://router.project-osrm.org/route/v1/driving/%.5f,%.5f;%.5f,%.5f?overview=false", lo, la, to[1], to[0])));
		if (e != null) cache.put(dest, new Object[]{e, System.currentTimeMillis()});
		return e;
	}

	/** Google Haritalar'da araçla yol (canlı trafikli süre orada). */
	public static String mapsUrl(String dest) {
		return "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" + android.net.Uri.encode(dest);
	}
}
