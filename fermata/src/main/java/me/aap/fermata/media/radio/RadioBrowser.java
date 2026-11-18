package me.aap.fermata.media.radio;

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

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * Açık internet radyosu dizini radio-browser.info (topluluk verisi, ücretsiz, anahtar gerektirmez):
 * Türkiye'nin en çok dinlenenleri ve ada göre arama. Ağ işi arka planda yapılır.
 */
public final class RadioBrowser {
	private static final String[] SERVERS = {"https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info",
			"https://at1.api.radio-browser.info"};

	private RadioBrowser() {
	}

	/** Bir radyo istasyonu. */
	public record Station(String name, String url, @Nullable String logo, String tags, String country, String state,
												 int bitrate) {
		/** Alt satır: şehir · türler · kbps. */
		public String sub() {
			StringBuilder b = new StringBuilder();
			if (!state.isEmpty()) b.append(state);
			String t = tags.replace(",", ", ");
			if (t.length() > 40) t = t.substring(0, 40) + "…";
			if (!t.isEmpty()) b.append((b.length() > 0) ? " · " : "").append(t);
			if (bitrate > 0) b.append((b.length() > 0) ? " · " : "").append(bitrate).append(" kbps");
			return (b.length() > 0) ? b.toString() : country;
		}
	}

	/** Tür süzgeçleri: görünen ad → etiketlerde aranan sözcükler. */
	public static final String[][] GENRES = {
			{"Haber", "news", "haber", "talk"},
			{"Pop", "pop", "hits", "top"},
			{"Türk sanat / halk", "turkish classical", "sanat", "halk", "folk", "türkü", "turku", "thm", "tsm"},
			{"Arabesk", "arabesk", "fantazi"},
			{"Rock", "rock", "metal", "alternative"},
			{"Slow / Romantik", "slow", "romantic", "love", "chill", "easy"},
			{"Klasik / Caz", "classical", "klasik", "jazz", "caz", "blues"},
			{"Spor", "sport", "spor"},
			{"Dini", "religious", "islam", "quran", "kuran", "ilahi", "dini"},
			{"Çocuk", "kids", "children", "çocuk", "cocuk"},
	};

	/** İstasyon bu türe uyuyor mu (GENRES satırı). */
	public static boolean matchesGenre(Station s, String[] genre) {
		String t = (s.tags() + " " + s.name()).toLowerCase(java.util.Locale.ROOT);
		for (int i = 1; i < genre.length; i++) if (t.contains(genre[i])) return true;
		return false;
	}

	/** Şehir eşleşmesi: büyük-küçük harf ve Türkçe harf farkı gözetmeden. */
	public static boolean matchesCity(Station s, String city) {
		return fold(s.state()).contains(fold(city)) || fold(s.name()).contains(fold(city));
	}

	static String fold(String s) {
		String n = s.toLowerCase(new java.util.Locale("tr")).replace('ı', 'i');
		return java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "");
	}

	/** En çok istasyonu olan şehirler (sırayla, en fazla n). */
	public static List<String> cities(List<Station> l, int n) {
		java.util.Map<String, Integer> c = new java.util.HashMap<>();
		java.util.Map<String, String> shown = new java.util.HashMap<>();
		for (Station s : l) {
			if (s.state().isEmpty()) continue;
			String k = fold(s.state());
			c.merge(k, 1, Integer::sum);
			shown.putIfAbsent(k, s.state());
		}
		List<String> keys = new ArrayList<>(c.keySet());
		keys.sort((a, b) -> c.get(b) - c.get(a));
		List<String> out = new ArrayList<>();
		for (String k : keys) {
			if (out.size() >= n) break;
			out.add(shown.get(k));
		}
		return out;
	}

	/** Türkiye'deki tüm çalışan istasyonlar (son denetimi başarılı olanlar, en çok dinlenen önce). */
	public static FutureSupplier<List<Station>> allTurkey() {
		return query("/json/stations/bycountrycodeexact/TR?order=clickcount&reverse=true&hidebroken=true&limit=1500");
	}

	/** Türkiye'de en çok dinlenen istasyonlar. */
	public static FutureSupplier<List<Station>> topTurkey() {
		return query("/json/stations/bycountrycodeexact/TR?order=clickcount&reverse=true&hidebroken=true&limit=80");
	}

	/** Ada göre arama (önce Türkiye, az sonuç çıkarsa tüm dünya). */
	public static FutureSupplier<List<Station>> search(String name) {
		Promise<List<Station>> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				String q = URLEncoder.encode(name, "UTF-8");
				List<Station> l = fetch("/json/stations/search?name=" + q
						+ "&countrycode=TR&hidebroken=true&order=clickcount&reverse=true&limit=40");
				if (l.size() < 5) {
					for (Station s : fetch("/json/stations/search?name=" + q
							+ "&hidebroken=true&order=clickcount&reverse=true&limit=40")) {
						boolean dup = false;
						for (Station x : l) dup |= x.url().equals(s.url());
						if (!dup) l.add(s);
					}
				}
				p.complete(l);
			} catch (Throwable ex) {
				Log.e(ex, "Radyo araması başarısız: ", name);
				p.completeExceptionally(ex);
			}
		});
		return p;
	}

	private static FutureSupplier<List<Station>> query(String path) {
		Promise<List<Station>> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				p.complete(fetch(path));
			} catch (Throwable ex) {
				Log.e(ex, "Radyo dizini okunamadı");
				p.completeExceptionally(ex);
			}
		});
		return p;
	}

	private static List<Station> fetch(String path) throws Exception {
		Exception last = null;
		for (String s : SERVERS) {
			try {
				return parse(get(s + path));
			} catch (Exception ex) {
				last = ex;
			}
		}
		throw (last != null) ? last : new IllegalStateException("Radyo dizinine ulaşılamadı");
	}

	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(12000);
		c.setRequestProperty("User-Agent", "Yolda/2");
		try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buf = new byte[16384];
			for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
			return out.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
	}

	static List<Station> parse(String json) throws Exception {
		JSONArray a = new JSONArray(json);
		List<Station> out = new ArrayList<>();
		for (int i = 0; i < a.length(); i++) {
			JSONObject o = a.getJSONObject(i);
			String url = o.optString("url_resolved", "");
			if (url.isEmpty()) url = o.optString("url", "");
			String name = o.optString("name", "").trim();
			if (url.isEmpty() || name.isEmpty()) continue;
			if (o.has("lastcheckok") && (o.optInt("lastcheckok", 1) == 0)) continue; // son denetimde çalışmıyordu
			String logo = o.optString("favicon", "");
			boolean dup = false;
			for (Station s : out) dup |= s.url().equals(url) || s.name().equalsIgnoreCase(name);
			if (dup) continue;
			out.add(new Station(name, url, logo.startsWith("http") ? logo : null, o.optString("tags", ""),
					o.optString("countrycode", ""), o.optString("state", "").trim(), o.optInt("bitrate", 0)));
		}
		return out;
	}
}
