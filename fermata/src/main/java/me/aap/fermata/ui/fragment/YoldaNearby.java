package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Yakındaki yerler (OpenStreetMap / Overpass): otoparklar ve elektrikli araç şarj istasyonları. Gidilecek yer
 * adı Photon ile koordinata çevrilir ("Gideceğin yerdeki otoparklar"). Ayrıştırma saf (testlerle denetlenir).
 */
public final class YoldaNearby {
	private static final String[] OVERPASS = {"https://overpass-api.de/api/interpreter",
			"https://overpass.kumi.systems/api/interpreter"};

	/**
	 * Bir yer: ad, konum, uzaklık (m), bilgi satırı (otopark: ücretli / ücretsiz, kapasite, katlı; şarj: priz
	 * türleri, güç, işletmeci).
	 */
	public record Spot(String name, double lat, double lon, int meters, String info) {
	}

	private YoldaNearby() {
	}

	static String parkingQuery(double la, double lo, int r) {
		return String.format(Locale.ROOT, "[out:json][timeout:20];nwr(around:%d,%.5f,%.5f)[\"amenity\"=\"parking\"]"
				+ "[\"access\"!~\"private|no\"];out center tags 40;", r, la, lo);
	}

	static String chargerQuery(double la, double lo, int r) {
		return String.format(Locale.ROOT, "[out:json][timeout:20];nwr(around:%d,%.5f,%.5f)[\"amenity\"=\"charging_station\"];"
				+ "out center tags 40;", r, la, lo);
	}

	/** Yol çalışmaları (Mola'dan): yapımı süren yollar ve yol çalışması işaretleri. */
	static String roadworksQuery(double la, double lo, int r) {
		return String.format(Locale.ROOT, "[out:json][timeout:25];(way(around:%d,%.5f,%.5f)[\"highway\"=\"construction\"];"
				+ "node(around:%d,%.5f,%.5f)[\"highway\"=\"construction\"];"
				+ "node(around:%d,%.5f,%.5f)[\"hazard\"=\"roadworks\"];"
				+ "way(around:%d,%.5f,%.5f)[\"construction\"~\"motorway|trunk|primary|secondary|tertiary\"];);out center tags 60;",
				r, la, lo, r, la, lo, r, la, lo, r, la, lo);
	}

	/** Yol çalışması satırı: ad (yoksa yol türü), bilgi: yol türü · bitiş tarihi (varsa). */
	static Spot roadwork(JSONObject t, double a, double b, int m) {
		String kind = t.optString("construction", "");
		String kt = switch (kind) {
			case "motorway" -> "Otoyol";
			case "trunk" -> "Devlet yolu";
			case "primary" -> "Ana yol";
			case "secondary" -> "İl yolu";
			case "tertiary" -> "Bağlantı yolu";
			default -> "Yol";
		};
		String n = t.optString("name", t.optString("ref", "")).trim();
		List<String> p = new ArrayList<>();
		p.add(kt + " çalışması");
		String end = t.optString("opening_date", t.optString("end_date", ""));
		if (!end.isEmpty()) p.add("bitiş " + end);
		String ref = t.optString("ref", "");
		if (!ref.isEmpty() && !ref.equals(n)) p.add(ref);
		return new Spot(n.isEmpty() ? (kt + " çalışması") : n, a, b, m, String.join(" · ", p));
	}

	public static List<Spot> roadworks(double la, double lo, int radius, int n) throws Exception {
		String q = roadworksQuery(la, lo, radius);
		Exception last = null;
		for (String s : OVERPASS) {
			try {
				return parseRoadworks(YoldaInfo.get(s + "?data=" + URLEncoder.encode(q, "UTF-8")), la, lo, n);
			} catch (Exception ex) {
				last = ex;
			}
		}
		throw (last != null) ? last : new IllegalStateException("Overpass yok");
	}

	static List<Spot> parseRoadworks(String json, double la, double lo, int n) throws Exception {
		JSONArray el = new JSONObject(json).optJSONArray("elements");
		List<Spot> out = new ArrayList<>();
		if (el == null) return out;
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (int i = 0; i < el.length(); i++) {
			JSONObject o = el.getJSONObject(i);
			JSONObject t = o.optJSONObject("tags");
			if (t == null) t = new JSONObject();
			JSONObject c = o.optJSONObject("center");
			double a = (c != null) ? c.optDouble("lat", Double.NaN) : o.optDouble("lat", Double.NaN);
			double b = (c != null) ? c.optDouble("lon", Double.NaN) : o.optDouble("lon", Double.NaN);
			if (Double.isNaN(a) || Double.isNaN(b)) continue;
			Spot sp = roadwork(t, a, b, (int) YoldaRadarCore.dist(la, lo, a, b));
			if (!seen.add(sp.name() + (sp.meters() / 500))) continue; // aynı yolun parçaları tek satır
			out.add(sp);
		}
		out.sort((x, y) -> Integer.compare(x.meters(), y.meters()));
		return (out.size() > n) ? new ArrayList<>(out.subList(0, n)) : out;
	}

	/** Overpass yanıtı → yerler, yakından uzağa (en çok n). */
	static List<Spot> parse(String json, double la, double lo, boolean charger, int n) throws Exception {
		JSONArray el = new JSONObject(json).optJSONArray("elements");
		List<Spot> out = new ArrayList<>();
		if (el == null) return out;
		for (int i = 0; i < el.length(); i++) {
			JSONObject o = el.getJSONObject(i);
			JSONObject t = o.optJSONObject("tags");
			if (t == null) t = new JSONObject();
			JSONObject c = o.optJSONObject("center");
			double a = (c != null) ? c.optDouble("lat", Double.NaN) : o.optDouble("lat", Double.NaN);
			double b = (c != null) ? c.optDouble("lon", Double.NaN) : o.optDouble("lon", Double.NaN);
			if (Double.isNaN(a) || Double.isNaN(b)) continue;
			int m = (int) YoldaRadarCore.dist(la, lo, a, b);
			out.add(new Spot(name(t, charger), a, b, m, charger ? chargerInfo(t) : parkingInfo(t)));
		}
		out.sort((x, y) -> Integer.compare(x.meters(), y.meters()));
		return (out.size() > n) ? new ArrayList<>(out.subList(0, n)) : out;
	}

	static String name(JSONObject t, boolean charger) {
		String n = t.optString("name", "").trim();
		if (n.isEmpty()) n = t.optString("operator", t.optString("brand", "")).trim();
		if (!n.isEmpty()) return n;
		if (charger) return "Şarj istasyonu";
		return "multi-storey".equals(t.optString("parking")) ? "Katlı otopark"
				: "underground".equals(t.optString("parking")) ? "Kapalı otopark" : "Otopark";
	}

	static String parkingInfo(JSONObject t) {
		List<String> p = new ArrayList<>();
		String fee = t.optString("fee", "");
		if ("yes".equals(fee)) p.add("ücretli");
		else if ("no".equals(fee)) p.add("ücretsiz");
		String cap = t.optString("capacity", "");
		if (cap.matches("\\d+")) p.add(cap + " araç");
		String k = t.optString("parking", "");
		if ("multi-storey".equals(k)) p.add("katlı");
		else if ("underground".equals(k)) p.add("yer altı");
		else if ("street_side".equals(k) || "lane".equals(k)) p.add("yol kenarı");
		return String.join(" · ", p);
	}

	static String chargerInfo(JSONObject t) {
		List<String> p = new ArrayList<>();
		String[][] sockets = {{"socket:type2_combo", "CCS"}, {"socket:chademo", "CHAdeMO"}, {"socket:type2", "Type 2"},
				{"socket:tesla_supercharger", "Tesla"}};
		for (String[] s : sockets) if (t.has(s[0]) && !"no".equals(t.optString(s[0]))) p.add(s[1]);
		String out = t.optString("socket:type2_combo:output", t.optString("charging_station:output", t.optString("maxpower", "")));
		if (!out.isEmpty()) p.add(out.replace(" ", ""));
		String cap = t.optString("capacity", "");
		if (cap.matches("\\d+")) p.add(cap + " soket");
		String op = t.optString("operator", "");
		String nm = t.optString("name", "");
		if (!op.isEmpty() && !nm.isEmpty() && !op.equals(nm)) p.add(op); // adı yoksa işletmeci zaten ad olarak görünür
		return String.join(" · ", p);
	}

	/** Yakındaki otoparklar / şarj istasyonları (arka planda çağır). */
	public static List<Spot> find(double la, double lo, boolean charger, int radius, int n) throws Exception {
		String q = charger ? chargerQuery(la, lo, radius) : parkingQuery(la, lo, radius);
		Exception last = null;
		for (String s : OVERPASS) {
			try {
				return parse(YoldaInfo.get(s + "?data=" + URLEncoder.encode(q, "UTF-8")), la, lo, charger, n);
			} catch (Exception ex) {
				last = ex;
			}
		}
		throw (last != null) ? last : new IllegalStateException("Overpass yok");
	}

	/** Yer adını koordinata çevir (Photon; bulunduğun yere yakın olan önce). Bulunamazsa null. */
	@Nullable
	public static double[] geocode(String place, double la, double lo) throws Exception {
		String u = "https://photon.komoot.io/api/?q=" + URLEncoder.encode(place, "UTF-8") + "&limit=1"
				+ (Double.isNaN(la) ? "" : String.format(Locale.ROOT, "&lat=%.4f&lon=%.4f", la, lo));
		return firstCoord(YoldaInfo.get(u));
	}

	@Nullable
	static double[] firstCoord(String json) throws Exception {
		JSONArray f = new JSONObject(json).optJSONArray("features");
		if ((f == null) || (f.length() == 0)) return null;
		JSONArray xy = f.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
		return new double[]{xy.getDouble(1), xy.getDouble(0)};
	}
}
