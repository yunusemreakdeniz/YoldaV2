package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Akaryakıt fiyatları: Türkiye ortalaması (benzin, motorin, LPG; TL / litre) ve haftalık değişim. Kaynak
 * OpenVan.camp açık verisi (CC BY 4.0, anahtar gerekmez; kaynak gösterilmesi şart). Veri 6 saatte bir yenilenir,
 * en fazla 6 saatte bir sorulur. Yakıt türü ve tüketimle 100 km ve yolculuk maliyeti hesaplanır.
 */
public final class YoldaFuel {
	static final String API = "https://openvan.camp/api/fuel/prices?source=yolda-android";
	public static final String ATTRIBUTION = "Veri: OpenVan.camp (CC BY 4.0) · Türkiye ortalaması";
	private static final String PREFS = "yolda_fuel";
	private static final long TTL = 6 * 3600_000L;

	/** Fiyatlar (TL / litre, bilinmiyorsa NaN), değişimler ve verinin tarihi. */
	public record Prices(double gasoline, double diesel, double lpg, double dGasoline, double dDiesel, double dLpg,
											 String fetchedAt) {
		public double of(String type) {
			return switch (type) {
				case "diesel" -> diesel;
				case "lpg" -> lpg;
				default -> gasoline;
			};
		}

		public double changeOf(String type) {
			return switch (type) {
				case "diesel" -> dDiesel;
				case "lpg" -> dLpg;
				default -> dGasoline;
			};
		}
	}

	private YoldaFuel() {
	}

	public static String label(String type) {
		return switch (type) {
			case "diesel" -> "Motorin";
			case "lpg" -> "LPG";
			default -> "Benzin";
		};
	}

	/** Yanıttan Türkiye kaydı; yoksa null. */
	@Nullable
	static Prices parse(String json) throws Exception {
		JSONObject data = new JSONObject(json).optJSONObject("data");
		JSONObject tr = (data != null) ? data.optJSONObject("TR") : null;
		if (tr == null) return null;
		JSONObject p = tr.optJSONObject("prices"), c = tr.optJSONObject("price_changes");
		if (p == null) return null;
		Prices r = new Prices(num(p, "gasoline"), num(p, "diesel"), num(p, "lpg"), num(c, "gasoline"), num(c, "diesel"),
				num(c, "lpg"), tr.optString("fetched_at", ""));
		return (Double.isNaN(r.gasoline()) && Double.isNaN(r.diesel()) && Double.isNaN(r.lpg())) ? null : r;
	}

	private static double num(@Nullable JSONObject o, String k) {
		return ((o == null) || o.isNull(k)) ? Double.NaN : o.optDouble(k, Double.NaN);
	}

	/** "71,09 ₺"; bilinmiyorsa "—". */
	public static String money(double v) {
		return Double.isNaN(v) ? "—" : String.format(new Locale("tr"), "%.2f ₺", v);
	}

	/** Değişim oku: "▲ 1,20" / "▼ 4,51" / "" (değişim yok ya da bilinmiyor). */
	public static String change(double d) {
		if (Double.isNaN(d) || (Math.abs(d) < 0.005)) return "";
		return String.format(new Locale("tr"), "%s %.2f", (d > 0) ? "▲" : "▼", Math.abs(d));
	}

	/** Yol maliyeti (TL): metre, litre / 100 km, TL / litre. */
	public static double cost(int meters, double perHundred, double price) {
		if (Double.isNaN(price) || (meters <= 0) || (perHundred <= 0)) return Double.NaN;
		return meters / 100_000.0 * perHundred * price;
	}

	// ---- kayıtlı tercih ve önbellek ----

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	/** Yakıt türü: gasoline, diesel, lpg. */
	public static String type(Context ctx) {
		return prefs(ctx).getString("type", "gasoline");
	}

	public static void setType(Context ctx, String type) {
		prefs(ctx).edit().putString("type", type).apply();
	}

	/** Tüketim (litre / 100 km), varsayılan 7. */
	public static double consumption(Context ctx) {
		return prefs(ctx).getFloat("per100", 7f);
	}

	public static void setConsumption(Context ctx, double v) {
		prefs(ctx).edit().putFloat("per100", (float) Math.max(2, Math.min(30, v))).apply();
	}

	/** Önbellekteki fiyatlar (yoksa null). */
	@Nullable
	public static Prices cached(Context ctx) {
		String s = prefs(ctx).getString("json", null);
		if (s == null) return null;
		try {
			return parse(s);
		} catch (Exception ex) {
			return null;
		}
	}

	/** Önbellek eskiyse ağdan yenile (arka planda çağır). @return güncel fiyatlar ya da önbellek */
	@Nullable
	public static Prices refresh(Context ctx, boolean force) {
		SharedPreferences p = prefs(ctx);
		if (!force && (System.currentTimeMillis() - p.getLong("at", 0) < TTL) && (cached(ctx) != null)) return cached(ctx);
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
			c.setConnectTimeout(8000);
			c.setReadTimeout(15000);
			c.setRequestProperty("User-Agent", "YoldaV2/2 (Android)");
			String body;
			try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] b = new byte[16384];
				for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
				body = out.toString(StandardCharsets.UTF_8.name());
			} finally {
				c.disconnect();
			}
			Prices r = parse(body);
			if (r != null) {
				// yalnız Türkiye kaydı saklanır (yanıt 175 ülke, büyük)
				JSONObject tr = new JSONObject(body).getJSONObject("data").getJSONObject("TR");
				p.edit().putString("json", new JSONObject().put("data", new JSONObject().put("TR", tr)).toString())
						.putLong("at", System.currentTimeMillis()).apply();
			}
			return (r != null) ? r : cached(ctx);
		} catch (Throwable ex) {
			YoldaLog.add("Akaryakıt fiyatları alınamadı: " + ex.getClass().getSimpleName());
			return cached(ctx);
		}
	}
}
