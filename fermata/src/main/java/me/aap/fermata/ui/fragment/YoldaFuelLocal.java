package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Bulunduğun ile (ve ilçeye) göre akaryakıt fiyatı: konum Photon ile il / ilçe adına çevrilir, il plaka koduyla
 * Opet'in sitesinin kullandığı fiyat servisinden ilçe fiyatları alınır (belgelenmiş açık bir servis değil; yanıt
 * alınamazsa Türkiye ortalamasına düşülür). LPG bu yanıtta yok: ülke ortalamasından. Kullanıcı pompadaki fiyatı
 * elle girebilir (maliyet hesabı onu kullanır).
 */
public final class YoldaFuelLocal {
	private static final String PREFS = "yolda_fuel";
	/** Plaka sırasıyla 81 il (1 Adana … 81 Düzce). */
	static final String[] PROVINCES = {"Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin",
			"Aydın", "Balıkesir", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum", "Denizli",
			"Diyarbakır", "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane", "Hakkari",
			"Hatay", "Isparta", "Mersin", "İstanbul", "İzmir", "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir", "Kocaeli",
			"Konya", "Kütahya", "Malatya", "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş", "Nevşehir", "Niğde", "Ordu", "Rize",
			"Sakarya", "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat", "Trabzon", "Tunceli", "Şanlıurfa", "Uşak", "Van",
			"Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman", "Kırıkkale", "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır",
			"Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce"};

	/** İlçe fiyatı: benzin, motorin (TL / litre, bilinmiyorsa NaN), il ve ilçe adı. */
	public record Local(double gasoline, double diesel, String province, String district) {
	}

	private YoldaFuelLocal() {
	}

	static String fold(String s) {
		String n = s.toLowerCase(new Locale("tr")).replace('ı', 'i');
		return Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "").replaceAll("[^a-z]", "");
	}

	/** İl adından plaka kodu ("Ankara" → 6, "İstanbul" → 34, "Mersin" / "İçel" → 33); bulunamazsa 0. */
	static int plate(@Nullable String name) {
		if (name == null) return 0;
		String f = fold(name.replaceAll("(?i)\\s*(ili|province|il)$", ""));
		if (f.equals("icel")) return 33;
		if (f.equals("afyon")) return 3;
		if (f.equals("maras")) return 46;
		if (f.equals("urfa")) return 63;
		for (int i = 0; i < PROVINCES.length; i++) if (fold(PROVINCES[i]).equals(f)) return i + 1;
		return 0;
	}

	/** Photon ters arama yanıtı → {il, ilçe} (bulunamazsa null). */
	@Nullable
	static String[] parseReverse(String json) throws Exception {
		JSONArray f = new JSONObject(json).optJSONArray("features");
		if ((f == null) || (f.length() == 0)) return null;
		JSONObject p = f.getJSONObject(0).optJSONObject("properties");
		if (p == null) return null;
		String il = p.optString("state", p.optString("city", ""));
		String ilce = p.optString("county", p.optString("district", p.optString("city", "")));
		if (plate(il) == 0) { // bazı yerlerde il "city" alanında
			String c = p.optString("city", "");
			if (plate(c) != 0) il = c;
		}
		return il.isEmpty() ? null : new String[]{il, ilce};
	}

	/** Opet yanıtı → seçilen ilçenin (yoksa ildeki ilk ilçenin) benzin ve motorin fiyatı. */
	@Nullable
	static Local parseOpet(String json, @Nullable String district) throws Exception {
		JSONArray a = new JSONArray(json);
		if (a.length() == 0) return null;
		JSONObject pick = a.getJSONObject(0);
		if ((district != null) && !district.isEmpty()) {
			String want = fold(district);
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				if (fold(o.optString("districtName")).equals(want)) {
					pick = o;
					break;
				}
			}
		}
		double g = Double.NaN, d = Double.NaN;
		JSONArray pr = pick.optJSONArray("prices");
		if (pr != null) for (int i = 0; i < pr.length(); i++) {
			JSONObject p = pr.getJSONObject(i);
			String n = fold(p.optString("productName"));
			double v = p.optDouble("amount", Double.NaN);
			if (Double.isNaN(g) && n.contains("benzin")) g = v;
			else if (n.contains("motorin") && (Double.isNaN(d) || n.contains("eco"))) d = v; // standart motorin önce
		}
		if (Double.isNaN(g) && Double.isNaN(d)) return null;
		return new Local(g, d, title(pick.optString("provinceName")), title(pick.optString("districtName")));
	}

	/** "ANKARA" → "Ankara". */
	static String title(String s) {
		if ((s == null) || s.isEmpty()) return "";
		String l = s.toLowerCase(new Locale("tr"));
		return l.substring(0, 1).toUpperCase(new Locale("tr")) + l.substring(1);
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	/** Son alınan ilçe fiyatı (önbellek), yoksa null. */
	@Nullable
	public static Local cached(Context ctx) {
		SharedPreferences p = prefs(ctx);
		String js = p.getString("local_json", null);
		if (js == null) return null;
		try {
			return parseOpet(js, p.getString("local_district", null));
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Konuma göre il / ilçe fiyatını yenile (6 saatte bir ya da 20 km'den fazla yer değişince). Arka planda çağır.
	 * Alınamazsa önbellek ya da null.
	 */
	@Nullable
	public static Local refresh(Context ctx, boolean force) {
		SharedPreferences p = prefs(ctx);
		double la = YoldaRadar.lat, lo = YoldaRadar.lon;
		if (Double.isNaN(la)) return cached(ctx);
		boolean moved = YoldaRadarCore.dist(la, lo, p.getFloat("local_lat", 0), p.getFloat("local_lon", 0)) > 20_000;
		if (!force && !moved && (System.currentTimeMillis() - p.getLong("local_at", 0) < 6 * 3600_000L)) return cached(ctx);
		try {
			String[] where = parseReverse(YoldaInfo.get(String.format(Locale.ROOT,
					"https://photon.komoot.io/reverse?lat=%.5f&lon=%.5f&lang=default", la, lo)));
			int code = (where != null) ? plate(where[0]) : 0;
			if (code == 0) return cached(ctx);
			String js = YoldaInfo.get("https://api.opet.com.tr/api/fuelprices/prices?ProvinceCode=" + code);
			Local l = parseOpet(js, where[1]);
			if (l == null) return cached(ctx);
			p.edit().putString("local_json", js).putString("local_district", where[1]).putLong("local_at", System.currentTimeMillis())
					.putFloat("local_lat", (float) la).putFloat("local_lon", (float) lo).apply();
			return l;
		} catch (Throwable ex) {
			YoldaLog.add("İl akaryakıt fiyatı alınamadı: " + ex.getClass().getSimpleName());
			return cached(ctx);
		}
	}

	/** Pompada görülen fiyat (elle; NaN = yok) — maliyet hesabı önce bunu kullanır. */
	public static double manual(Context ctx, String type) {
		return prefs(ctx).getFloat("manual_" + type, Float.NaN);
	}

	public static void setManual(Context ctx, String type, double v) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		if (Double.isNaN(v) || (v <= 0)) e.remove("manual_" + type);
		else e.putFloat("manual_" + type, (float) v);
		e.apply();
	}

	/** Bu yakıt için geçerli fiyat ve kaynağı: elle girilen > ilçe (Opet) > Türkiye ortalaması. */
	public static Object[] price(Context ctx, String type, @Nullable YoldaFuel.Prices national) {
		double m = manual(ctx, type);
		if (!Double.isNaN(m)) return new Object[]{m, "pompada girdiğin fiyat"};
		Local l = cached(ctx);
		if (l != null) {
			double v = "diesel".equals(type) ? l.diesel() : "gasoline".equals(type) ? l.gasoline() : Double.NaN;
			if (!Double.isNaN(v)) return new Object[]{v, "Opet · " + l.province() + (l.district().isEmpty() ? "" : (" / " + l.district()))};
		}
		return new Object[]{(national != null) ? national.of(type) : Double.NaN, "Türkiye ortalaması"};
	}

	/** Resmi fiyat sayfaları (uygulama içinde açılır). */
	public static final String OPET_PAGE = "https://www.opet.com.tr/akaryakit-fiyatlari";
	public static final String PO_PAGE = "https://www.petrolofisi.com.tr/akaryakit-fiyatlari";
}
