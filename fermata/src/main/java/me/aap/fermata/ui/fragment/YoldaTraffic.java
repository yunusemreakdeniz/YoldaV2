package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Trafik kartı: bulunduğun yerden eve / işe yol süresi ve mesafe. Ücretsiz canlı trafik verisi yok: süre açık
 * OSRM rota servisinden "trafik hariç tahmini" olarak gösterilir; canlı trafik için yol Google Haritalar'da
 * açılır (orada trafikli süre görünür). Hedef Photon ile koordinata çevrilir; sonuç 10 dakika önbellekte.
 */
public final class YoldaTraffic {
	/** Süre (dk) ve mesafe (km). */
	public record Eta(int minutes, double km) {
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
		Eta e = parseOsrm(YoldaInfo.get(String.format(Locale.ROOT,
				"https://router.project-osrm.org/route/v1/driving/%.5f,%.5f;%.5f,%.5f?overview=false", lo, la, to[1], to[0])));
		if (e != null) cache.put(dest, new Object[]{e, System.currentTimeMillis()});
		return e;
	}

	/** Google Haritalar'da araçla yol (canlı trafikli süre orada). */
	public static String mapsUrl(String dest) {
		return "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" + android.net.Uri.encode(dest);
	}
}
