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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bilgi kartlarının verisi (anahtar gerektirmeyen açık kaynaklar, telefonda önbellekli):
 * <ul>
 * <li>Döviz: TCMB günlük kurları (today.xml); altın: Truncgil açık verisi (gram, çeyrek).</li>
 * <li>Namaz vakitleri: Aladhan, Diyanet hesap yöntemi (method 13), bulunduğun yere göre.</li>
 * <li>Haberler: TRT Haber son dakika RSS, alınamazsa Anadolu Ajansı güncel RSS.</li>
 * </ul>
 * Ayrıştırma saf Java (testlerle denetlenir); ağ işi arka planda çağrılır.
 */
public final class YoldaInfo {
	private static final String PREFS = "yolda_info";

	public record Rate(String code, String name, double buy, double sell) {
	}

	public record Prayer(String name, String time) {
		/** Günün dakikası (00:00'dan). */
		public int minute() {
			String[] p = time.split(":");
			try {
				return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim().substring(0, 2));
			} catch (Exception ex) {
				return -1;
			}
		}
	}

	public record News(String title, String link) {
	}

	private YoldaInfo() {
	}

	// ---- ayrıştırma (saf) ----

	private static final Pattern TCMB = Pattern.compile("<Currency[^>]*Kod=\"([A-Z]{3})\"[^>]*>.*?<Unit>(\\d+)</Unit>.*?<Isim>([^<]*)</Isim>"
			+ ".*?<ForexBuying>([0-9.]*)</ForexBuying>.*?<ForexSelling>([0-9.]*)</ForexSelling>", Pattern.DOTALL);

	/** TCMB today.xml → USD, EUR, GBP (birim başına). */
	static List<Rate> parseTcmb(String xml) {
		List<Rate> out = new ArrayList<>();
		Matcher m = TCMB.matcher(xml);
		while (m.find()) {
			String code = m.group(1);
			if (!code.equals("USD") && !code.equals("EUR") && !code.equals("GBP")) continue;
			try {
				int unit = Integer.parseInt(m.group(2));
				double b = Double.parseDouble(m.group(4)) / unit, s = Double.parseDouble(m.group(5)) / unit;
				String n = switch (code) {
					case "USD" -> "Dolar";
					case "EUR" -> "Euro";
					default -> "Sterlin";
				};
				out.add(new Rate(code, n, b, s));
			} catch (NumberFormatException ignore) {
			}
		}
		return out;
	}

	/** "2.950,12" → 2950.12 (Türkçe sayı biçimi). */
	static double trNum(String s) {
		if (s == null) return Double.NaN;
		String t = s.replaceAll("[^0-9,.\\-]", "");
		if (t.contains(",")) t = t.replace(".", "").replace(',', '.');
		else if (t.matches("\\d{1,3}(\\.\\d{3})+")) t = t.replace(".", ""); // "4.800" = dört bin sekiz yüz
		try {
			return Double.parseDouble(t);
		} catch (NumberFormatException ex) {
			return Double.NaN;
		}
	}

	/** Truncgil today.json → gram ve çeyrek altın. */
	static List<Rate> parseGold(String json) throws Exception {
		JSONObject o = new JSONObject(json);
		List<Rate> out = new ArrayList<>();
		String[][] want = {{"gram-altin", "Gram altın"}, {"ceyrek-altin", "Çeyrek altın"}};
		for (String[] w : want) {
			JSONObject g = o.optJSONObject(w[0]);
			if (g == null) continue;
			double b = trNum(g.optString("Alış", g.optString("Alis", ""))), s = trNum(g.optString("Satış", g.optString("Satis", "")));
			if (!Double.isNaN(s)) out.add(new Rate(w[0], w[1], b, s));
		}
		return out;
	}

	/** Aladhan yanıtı → İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı. */
	static List<Prayer> parsePrayer(String json) throws Exception {
		JSONObject t = new JSONObject(json).getJSONObject("data").getJSONObject("timings");
		String[][] map = {{"Fajr", "İmsak"}, {"Sunrise", "Güneş"}, {"Dhuhr", "Öğle"}, {"Asr", "İkindi"}, {"Maghrib", "Akşam"},
				{"Isha", "Yatsı"}};
		List<Prayer> out = new ArrayList<>();
		for (String[] m : map) {
			String v = t.optString(m[0], "");
			if (v.length() >= 5) out.add(new Prayer(m[1], v.substring(0, 5)));
		}
		return out;
	}

	private static final Pattern ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
	private static final Pattern TITLE = Pattern.compile("<title>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</title>", Pattern.DOTALL);
	private static final Pattern LINK = Pattern.compile("<link>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</link>", Pattern.DOTALL);

	/** RSS → başlıklar (en çok 15, HTML işaretleri çözülür). */
	static List<News> parseRss(String xml) {
		List<News> out = new ArrayList<>();
		Matcher m = ITEM.matcher(xml);
		while (m.find() && (out.size() < 15)) {
			Matcher t = TITLE.matcher(m.group(1));
			if (!t.find()) continue;
			String title = unescape(t.group(1)).replaceAll("\\s+", " ").trim();
			if (title.isEmpty()) continue;
			Matcher l = LINK.matcher(m.group(1));
			out.add(new News(title, l.find() ? unescape(l.group(1)).trim() : ""));
		}
		return out;
	}

	static String unescape(String s) {
		return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
				.replace("&lt;", "<").replace("&gt;", ">").replace("&#8217;", "’").replaceAll("<[^>]+>", "");
	}

	/** Sıradaki vakit (şu anki dakikadan sonraki ilk; günün sonundaysa ertesi İmsak), yoksa null. */
	@Nullable
	public static Prayer next(List<Prayer> l, int nowMinute) {
		for (Prayer p : l) if (p.minute() > nowMinute) return p;
		return l.isEmpty() ? null : l.get(0);
	}

	/** "1 sa 12 dk" / "25 dk" kalan süre. */
	public static String left(Prayer p, int nowMinute) {
		int d = p.minute() - nowMinute;
		if (d < 0) d += 24 * 60;
		return (d >= 60) ? ((d / 60) + " sa " + (d % 60) + " dk") : (d + " dk");
	}

	public static String money(double v) {
		if (Double.isNaN(v)) return "—";
		return String.format(new Locale("tr"), (v >= 1000) ? "%,.0f ₺" : "%.2f ₺", v);
	}

	// ---- ağ ve önbellek ----

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(12000);
		c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) YoldaV2/2");
		try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] b = new byte[16384];
			for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
			return out.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
	}

	private static boolean fresh(Context ctx, String key, long ttl) {
		return System.currentTimeMillis() - prefs(ctx).getLong(key + "_at", 0) < ttl;
	}

	private static void store(Context ctx, String key, String val) {
		prefs(ctx).edit().putString(key, val).putLong(key + "_at", System.currentTimeMillis()).apply();
	}

	/** Kurlar ve altın (önbellek 30 dk). Arka planda çağır. */
	public static List<Rate> rates(Context ctx, boolean refresh) {
		if (refresh && !fresh(ctx, "fx", 30 * 60_000L)) {
			try {
				store(ctx, "fx_tcmb", get("https://www.tcmb.gov.tr/kurlar/today.xml"));
			} catch (Throwable ex) {
				YoldaLog.add("Döviz kurları alınamadı: " + ex.getClass().getSimpleName());
			}
			try {
				store(ctx, "fx_gold", get("https://finans.truncgil.com/today.json"));
			} catch (Throwable ex) {
				YoldaLog.add("Altın fiyatları alınamadı: " + ex.getClass().getSimpleName());
			}
			prefs(ctx).edit().putLong("fx_at", System.currentTimeMillis()).apply();
		}
		List<Rate> out = new ArrayList<>(parseTcmb(prefs(ctx).getString("fx_tcmb", "")));
		try {
			String g = prefs(ctx).getString("fx_gold", null);
			if (g != null) out.addAll(parseGold(g));
		} catch (Throwable ignore) {
		}
		return out;
	}

	/** Bugünün namaz vakitleri (gün ve yer değişince yenilenir). Arka planda çağır. */
	public static List<Prayer> prayers(Context ctx, boolean refresh) {
		double la = YoldaRadar.lat, lo = YoldaRadar.lon;
		if (Double.isNaN(la)) {
			la = prefs(ctx).getFloat("pr_lat", 39.93f); // konum yoksa son bilinen, o da yoksa Ankara
			lo = prefs(ctx).getFloat("pr_lon", 32.86f);
		}
		Calendar c = Calendar.getInstance();
		String day = c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
		boolean moved = Math.abs(la - prefs(ctx).getFloat("pr_lat", 0)) > 0.3 || Math.abs(lo - prefs(ctx).getFloat("pr_lon", 0)) > 0.3;
		if (refresh && (!day.equals(prefs(ctx).getString("pr_day", "")) || moved)) {
			try {
				String js = get(String.format(Locale.ROOT, "https://api.aladhan.com/v1/timings?latitude=%.4f&longitude=%.4f&method=13", la, lo));
				parsePrayer(js); // geçerli mi
				store(ctx, "pr", js);
				prefs(ctx).edit().putString("pr_day", day).putFloat("pr_lat", (float) la).putFloat("pr_lon", (float) lo).apply();
			} catch (Throwable ex) {
				YoldaLog.add("Namaz vakitleri alınamadı: " + ex.getClass().getSimpleName());
			}
		}
		try {
			String js = prefs(ctx).getString("pr", null);
			return (js != null) ? parsePrayer(js) : new ArrayList<>();
		} catch (Throwable ex) {
			return new ArrayList<>();
		}
	}

	/** Son dakika haberleri (önbellek 15 dk). Arka planda çağır. */
	public static List<News> news(Context ctx, boolean refresh) {
		if (refresh && !fresh(ctx, "news", 15 * 60_000L)) {
			for (String u : new String[]{"https://www.trthaber.com/sondakika_articles.rss",
					"https://www.aa.com.tr/tr/rss/default?cat=guncel"}) {
				try {
					String x = get(u);
					if (!parseRss(x).isEmpty()) {
						store(ctx, "news", x);
						break;
					}
				} catch (Throwable ex) {
					YoldaLog.add("Haberler alınamadı (" + u + "): " + ex.getClass().getSimpleName());
				}
			}
		}
		return parseRss(prefs(ctx).getString("news", ""));
	}

	/** Haber başlıklarını okunacak tek metne çevir. */
	public static String newsSpoken(List<News> l, int n) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < Math.min(n, l.size()); i++) b.append((i == 0) ? "" : ". ").append(l.get(i).title());
		return b.toString();
	}

	/** Kaydedilmiş veriyi JSON'a (testler için yardımcı değil; günlükte kısa özet). */
	static String summary(List<Rate> l) {
		JSONArray a = new JSONArray();
		for (Rate r : l) a.put(r.name() + " " + money(r.sell()));
		return a.toString();
	}
}
