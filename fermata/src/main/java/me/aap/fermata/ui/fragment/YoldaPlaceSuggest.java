package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Navigasyon aramasında yazdıkça yer önerileri: OpenStreetMap tabanlı Photon (komoot) arama servisi, anahtar
 * gerektirmez; bulunduğun yere yakın sonuçlar öne gelir. Son gidilenler ve kayıtlı yerler ağsız, anında eşleşir.
 */
public final class YoldaPlaceSuggest {
	private static final String API = "https://photon.komoot.io/api/";

	/** Bir öneri: başlık (yer adı), alt satır (sokak, ilçe, il), gidilecek metin, uzaklık (m, bilinmiyorsa -1). */
	public record Suggestion(String title, String sub, String dest, int meters) {
	}

	private YoldaPlaceSuggest() {
	}

	/** Ağdan öneriler (arka planda çağır). */
	/** Türkiye kutusu (boylam / enlem): içindeyken ya da konum bilinmiyorsa arama bununla sınırlanır. */
	static final double[] TR_BOX = {25.6, 35.7, 44.9, 42.2};

	static boolean inTurkey(double lat, double lon) {
		return !Double.isNaN(lat) && (lon >= TR_BOX[0]) && (lon <= TR_BOX[2]) && (lat >= TR_BOX[1]) && (lat <= TR_BOX[3]);
	}

	/** İstek adresi: yakına ağırlık (konum + yakınlaştırma), Türkiye'deyken ülke kutusu. */
	static String url(String q, double lat, double lon) throws Exception {
		StringBuilder u = new StringBuilder(API).append("?q=").append(URLEncoder.encode(q, "UTF-8")).append("&limit=10");
		boolean known = !Double.isNaN(lat) && !Double.isNaN(lon);
		if (known) u.append(String.format(Locale.ROOT, "&lat=%.4f&lon=%.4f&zoom=12&location_bias_scale=0.3", lat, lon));
		if (!known || inTurkey(lat, lon))
			u.append(String.format(Locale.ROOT, "&bbox=%.1f,%.1f,%.1f,%.1f", TR_BOX[0], TR_BOX[1], TR_BOX[2], TR_BOX[3]));
		return u.toString();
	}

	/** Yakın olanlar (50 km içi) öne; kendi aralarında servisin sırası korunur. */
	static List<Suggestion> nearFirst(List<Suggestion> l) {
		List<Suggestion> near = new ArrayList<>(), far = new ArrayList<>();
		for (Suggestion s : l) ((s.meters() >= 0) && (s.meters() <= 50_000) ? near : far).add(s);
		near.addAll(far);
		return near;
	}

	public static List<Suggestion> fetch(String q, double lat, double lon) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url(q, lat, lon)).openConnection();
		c.setConnectTimeout(6000);
		c.setReadTimeout(8000);
		c.setRequestProperty("User-Agent", "YoldaV2/2 (Android)");
		try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] b = new byte[8192];
			for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
			return nearFirst(parse(out.toString(StandardCharsets.UTF_8.name()), lat, lon));
		} finally {
			c.disconnect();
		}
	}

	/** Photon GeoJSON yanıtını önerilere çevir (aynı başlık + alt satır bir kez). */
	static List<Suggestion> parse(String json, double lat, double lon) throws Exception {
		JSONArray f = new JSONObject(json).optJSONArray("features");
		List<Suggestion> out = new ArrayList<>();
		if (f == null) return out;
		for (int i = 0; i < f.length(); i++) {
			JSONObject o = f.getJSONObject(i);
			JSONObject p = o.optJSONObject("properties");
			if (p == null) continue;
			String street = p.optString("street", "");
			String hn = p.optString("housenumber", "");
			String name = p.optString("name", "");
			String district = p.optString("district", p.optString("locality", ""));
			String county = p.optString("county", "");
			String city = p.optString("city", p.optString("state", ""));
			String title = !name.isEmpty() ? name : (street + (hn.isEmpty() ? "" : (" " + hn))).trim();
			if (title.isEmpty()) continue;
			List<String> parts = new ArrayList<>();
			if (!name.isEmpty() && !street.isEmpty()) parts.add(street + (hn.isEmpty() ? "" : (" " + hn)));
			for (String s : new String[]{district, county, city}) {
				if (!s.isEmpty() && !s.equals(title) && !parts.contains(s)) parts.add(s);
			}
			String sub = String.join(", ", parts);
			int m = -1;
			JSONObject g = o.optJSONObject("geometry");
			JSONArray xy = (g != null) ? g.optJSONArray("coordinates") : null;
			if ((xy != null) && (xy.length() >= 2) && !Double.isNaN(lat))
				m = (int) YoldaRadarCore.dist(lat, lon, xy.getDouble(1), xy.getDouble(0));
			String dest = sub.isEmpty() ? title : (title + ", " + sub);
			boolean dup = false;
			for (Suggestion s : out) dup |= s.dest().equals(dest);
			if (!dup) out.add(new Suggestion(title, sub, dest, m));
		}
		return out;
	}

	/** Son gidilenler ve kayıtlı yerlerden eşleşenler (Türkçe harf farkı gözetmeden, en fazla 3). */
	public static List<Suggestion> local(String q, List<String> recent, @Nullable String home, @Nullable String work) {
		List<Suggestion> out = new ArrayList<>();
		String k = fold(q);
		if (k.isEmpty()) return out;
		if ((home != null) && (fold(home).contains(k) || "ev".startsWith(k))) out.add(new Suggestion("Ev", home, home, -1));
		if ((work != null) && (fold(work).contains(k) || "is".startsWith(k))) out.add(new Suggestion("İş", work, work, -1));
		for (String r : recent) {
			if (out.size() >= 3) break;
			if (fold(r).contains(k)) out.add(new Suggestion(r, "Son gidilen", r, -1));
		}
		return out;
	}

	static String fold(String s) {
		String n = s.toLowerCase(new Locale("tr")).replace('ı', 'i');
		return java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "").trim();
	}

	/** Uzaklık yazısı: "850 m", "12 km". */
	public static String distance(int m) {
		if (m < 0) return "";
		if (m < 1000) return (Math.max(10, Math.round(m / 10f) * 10)) + " m";
		if (m < 10_000) return String.format(new Locale("tr"), "%.1f km", m / 1000f);
		return (m / 1000) + " km";
	}
}
