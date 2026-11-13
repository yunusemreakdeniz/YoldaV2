package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.text.Html;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * Nöbetçi eczaneler: Ankara için Ankara Eczacı Odası (her tarih, koordinatlı), diğer iller için
 * eczaneler.gen.tr (dün, bugün, yarın, ertesi gün; koordinat eczanenin kendi sayfasından, saklanır).
 * Nöbet günü sabah 08:30'da değişir: o saatten önce "bugün" bir önceki günün nöbetidir.
 */
public final class YoldaPharmacy {
	static final String GEN = "https://www.eczaneler.gen.tr";
	static final String AEO = "https://www.aeo.org.tr/getPharmacies/";
	private static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36";
	private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos",
			"Eylül", "Ekim", "Kasım", "Aralık"};

	private YoldaPharmacy() {
	}

	/** İl ya da ilçe: adres parçası (slug) ve görünen ad. */
	public record Place(String slug, String name) {
		String encode() {
			return slug + "|" + name;
		}

		@Nullable
		static Place decode(@Nullable String s) {
			if (s == null) return null;
			int i = s.indexOf('|');
			return (i <= 0) ? null : new Place(s.substring(0, i), s.substring(i + 1));
		}
	}

	/** Bir nöbetçi eczane. Koordinat bilinmiyorsa lat / lon NaN. */
	public record Pharmacy(String name, String district, String address, String note, String phone, double lat,
												 double lon, @Nullable String slug) {
		public boolean hasLocation() {
			return !Double.isNaN(lat) && !Double.isNaN(lon);
		}

		Pharmacy withLocation(double la, double lo) {
			return new Pharmacy(name, district, address, note, phone, la, lo, slug);
		}
	}

	/** Bir günün nöbet listesi ve geçerlilik yazısı ("13 Kasım akşamından 14 Kasım sabahına kadar"). */
	public record Duty(List<Pharmacy> list, String validity) {
	}

	// ---- nöbet günü ----

	/** Nöbet günü (gün başlangıcı, yerel): 08:30'dan önce dün. */
	public static Calendar dutyToday() {
		Calendar c = Calendar.getInstance();
		if ((c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) < 8 * 60 + 30) c.add(Calendar.DAY_OF_MONTH, -1);
		c.set(Calendar.HOUR_OF_DAY, 12);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c;
	}

	public static Calendar day(int offset) {
		Calendar c = dutyToday();
		c.add(Calendar.DAY_OF_MONTH, offset);
		return c;
	}

	/** "Bugün · 13 Kasım Cuma". */
	public static String dayLabel(int offset) {
		Calendar c = day(offset);
		String rel = switch (offset) {
			case -1 -> "Dün";
			case 0 -> "Bugün";
			case 1 -> "Yarın";
			default -> null;
		};
		String d = c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)] + " "
				+ new java.text.SimpleDateFormat("EEEE", new Locale("tr", "TR")).format(c.getTime());
		return (rel != null) ? (rel + " · " + d) : d;
	}

	/** Seçilebilen gün aralığı: Ankara'da geniş, diğer illerde sitenin verdiği dört gün. */
	public static int minOffset(Place il) {
		return isAnkara(il) ? -7 : 0; // sitenin "dün" sekmesi çoğu zaman boş
	}

	public static int maxOffset(Place il) {
		return isAnkara(il) ? 14 : 2;
	}

	static boolean isAnkara(@Nullable Place il) {
		return (il != null) && "ankara".equals(il.slug());
	}

	// ---- il / ilçe listeleri (saklanır) ----

	public static FutureSupplier<List<Place>> provinces(Context ctx) {
		SharedPreferences p = prefs(ctx);
		List<Place> cached = decodeList(p.getString("ecz_iller", null));
		if (cached.size() >= 81) return me.aap.utils.async.Completed.completed(cached);
		return async(() -> {
			String h = get(GEN + "/");
			List<Place> l = new ArrayList<>();
			Matcher m = Pattern.compile("href=\"/nobetci-([a-z]+)\"[^>]*class=\"hpali\">([^<]+)").matcher(h);
			Set<String> seen = new java.util.HashSet<>();
			while (m.find()) if (seen.add(m.group(1))) l.add(new Place(m.group(1), clean(m.group(2))));
			if (l.size() < 70) throw new IllegalStateException("İl listesi alınamadı");
			p.edit().putString("ecz_iller", encodeList(l)).apply();
			return l;
		});
	}

	public static FutureSupplier<List<Place>> districts(Context ctx, Place il) {
		SharedPreferences p = prefs(ctx);
		List<Place> cached = decodeList(p.getString("ecz_ilceler_" + il.slug(), null));
		if (!cached.isEmpty()) return me.aap.utils.async.Completed.completed(cached);
		return async(() -> {
			String h = get(GEN + "/nobetci-" + il.slug());
			List<Place> l = new ArrayList<>();
			Matcher m = Pattern.compile("href=\"/nobetci-" + Pattern.quote(il.slug()) + "-([a-z0-9-]+)\"[^>]*>([^<]+)").matcher(h);
			Set<String> seen = new java.util.HashSet<>();
			while (m.find()) if (seen.add(m.group(1))) l.add(new Place(m.group(1), clean(m.group(2))));
			l.sort((a, b) -> java.text.Collator.getInstance(new Locale("tr", "TR")).compare(a.name(), b.name()));
			if (!l.isEmpty()) p.edit().putString("ecz_ilceler_" + il.slug(), encodeList(l)).apply();
			return l;
		});
	}

	// ---- nöbet listesi ----

	/** İl (ve varsa ilçe) için verilen günün nöbetçileri; konum biliniyorsa uzaklığa göre sıralı. */
	public static FutureSupplier<Duty> duty(Context ctx, Place il, @Nullable Place ilce, int offset) {
		initCache(ctx);
		return async(() -> isAnkara(il) ? aeo(ilce, offset) : genTr(il, ilce, offset));
	}

	private static Duty aeo(@Nullable Place ilce, int offset) throws Exception {
		Calendar c = day(offset);
		String date = String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
				c.get(Calendar.DAY_OF_MONTH));
		JSONObject o = new JSONObject(get(AEO + date));
		String h = o.optString("html", "");
		String validity = "";
		Matcher vm = Pattern.compile("class=\"alert-nobet\">(.*?)</p>", Pattern.DOTALL).matcher(h);
		if (vm.find()) validity = clean(vm.group(1));
		List<Pharmacy> out = new ArrayList<>();
		String want = (ilce != null) ? fold(ilce.name()) : null;
		for (String b : h.split("class=\"inline-box\"")) {
			Matcher dm = Pattern.compile("data-district=\"([^\"]*)\"").matcher(b);
			Matcher nm = Pattern.compile("<h4>(.*?)</h4>", Pattern.DOTALL).matcher(b);
			if (!dm.find() || !nm.find()) continue;
			String district = clean(dm.group(1));
			if ((want != null) && !fold(district).equals(want)) continue;
			String addr = "", note = "", phone = "";
			Matcher pm = Pattern.compile("<div class=\"text\">.*?<p>(.*?)</p>", Pattern.DOTALL).matcher(b);
			if (pm.find()) {
				String p = pm.group(1);
				Matcher sm = Pattern.compile("<span>(.*?)</span>", Pattern.DOTALL).matcher(p);
				if (sm.find()) phone = clean(sm.group(1));
				p = p.replaceAll("(?s)<span>.*?</span>", "");
				String[] parts = p.split("(?i)<br\\s*/?>", 2);
				addr = clean(parts[0]);
				if (parts.length > 1) note = clean(parts[1]);
			}
			double la = Double.NaN, lo = Double.NaN;
			Matcher qm = Pattern.compile("query=(-?[0-9.]+),(-?[0-9.]+)").matcher(b);
			if (qm.find()) {
				la = parse(qm.group(1));
				lo = parse(qm.group(2));
			}
			out.add(new Pharmacy(title(clean(nm.group(1))), district, addr, note, phone, la, lo, null));
		}
		return new Duty(out, validity);
	}

	private static Duty genTr(Place il, @Nullable Place ilce, int offset) throws Exception {
		String h = get(GEN + "/nobetci-" + il.slug() + ((ilce != null) ? ("-" + ilce.slug()) : ""));
		Calendar want = day(offset);
		String[] panes = h.split("<div class=\"tab-pane");
		String pane = null;
		String validity = "";
		for (int i = 1; i < panes.length; i++) {
			Matcher am = Pattern.compile("alert-warning[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(panes[i]);
			if (!am.find()) continue;
			String v = clean(am.group(1));
			Matcher dm = Pattern.compile("(\\d{1,2})\\s+(\\S+)").matcher(v);
			if (!dm.find()) continue;
			int dd = Integer.parseInt(dm.group(1));
			int mm = month(dm.group(2));
			if ((dd == want.get(Calendar.DAY_OF_MONTH)) && (mm == want.get(Calendar.MONTH))) {
				// aynı tarihi taşıyan boş sekme olabiliyor ("dün" sekmesi): eczanesi olanı seç
				boolean rows = panes[i].contains("<span class=\"isim\">");
				if ((pane == null) || (rows && !pane.contains("<span class=\"isim\">"))) {
					pane = panes[i];
					validity = v;
				}
			}
		}
		if (pane == null) throw new IllegalStateException("Bu gün için nöbet listesi yayımlanmamış");
		List<Pharmacy> out = new ArrayList<>();
		for (String r : pane.split("<td colspan=\"3\" class=\"border-bottom\">")) {
			Matcher nm = Pattern.compile("<span class=\"isim\">(.*?)</span>", Pattern.DOTALL).matcher(r);
			if (!nm.find()) continue;
			Matcher sl = Pattern.compile("href=\"/eczane/([^\"]+)\"").matcher(r);
			Matcher ad = Pattern.compile("<div class='col-lg-6'>(.*?)<div", Pattern.DOTALL).matcher(r);
			Matcher no = Pattern.compile("<span class='font-italic'>(.*?)</span>", Pattern.DOTALL).matcher(r);
			Matcher ph = Pattern.compile("<div class='col-lg-3 py-lg-2'>(.*?)</div>", Pattern.DOTALL).matcher(r);
			String addr = ad.find() ? clean(ad.group(1)) : "";
			String district = (ilce != null) ? ilce.name() : districtOf(addr);
			String slug = sl.find() ? sl.group(1) : null;
			double[] ll = cachedLocation(slug);
			out.add(new Pharmacy(clean(nm.group(1)), district, addr, no.find() ? clean(no.group(1)) : "",
					ph.find() ? clean(ph.group(1)) : "", ll[0], ll[1], slug));
		}
		return new Duty(out, validity);
	}

	/** "… Kadıköy / İstanbul" → "Kadıköy". */
	static String districtOf(String addr) {
		int i = addr.lastIndexOf(" / ");
		if (i <= 0) return "";
		String s = addr.substring(0, i).trim();
		int k = s.lastIndexOf(' ');
		return (k >= 0) ? s.substring(k + 1) : s;
	}

	// ---- koordinat (eczanenin sayfasından, kalıcı saklanır) ----

	private static SharedPreferences llPrefs;

	private static double[] cachedLocation(@Nullable String slug) {
		if ((slug == null) || (llPrefs == null)) return new double[]{Double.NaN, Double.NaN};
		String s = llPrefs.getString(slug, null);
		if (s == null) return new double[]{Double.NaN, Double.NaN};
		int i = s.indexOf(',');
		return new double[]{parse(s.substring(0, i)), parse(s.substring(i + 1))};
	}

	/** Koordinatı bilinmeyen eczanelerin konumunu sırayla bulur (en çok 40); her bulunan için çağrılır. */
	public static void resolveLocations(Context ctx, List<Pharmacy> l, java.util.function.BiConsumer<Integer, Pharmacy> found) {
		SharedPreferences p = ctx.getApplicationContext().getSharedPreferences("yolda_eczane_konum", Context.MODE_PRIVATE);
		llPrefs = p;
		List<Pharmacy> copy = new ArrayList<>(l);
		EXEC.submit(() -> {
			int n = 0;
			for (int i = 0; (i < copy.size()) && (n < 40); i++) {
				Pharmacy ph = copy.get(i);
				if (ph.hasLocation() || (ph.slug() == null)) continue;
				n++;
				try {
					String h = get(GEN + "/eczane/" + ph.slug());
					Matcher la = Pattern.compile("itemprop=\"latitude\" content=\"(-?[0-9.]+)\"").matcher(h);
					Matcher lo = Pattern.compile("itemprop=\"longitude\" content=\"(-?[0-9.]+)\"").matcher(h);
					double a, b;
					if (la.find() && lo.find()) {
						a = parse(la.group(1));
						b = parse(lo.group(1));
					} else {
						Matcher q = Pattern.compile("q=(-?[0-9.]+),(-?[0-9.]+)").matcher(h);
						if (!q.find()) continue;
						a = parse(q.group(1));
						b = parse(q.group(2));
					}
					if (Double.isNaN(a) || Double.isNaN(b)) continue;
					p.edit().putString(ph.slug(), a + "," + b).apply();
					Pharmacy np = ph.withLocation(a, b);
					int idx = i;
					App.get().getHandler().post(() -> found.accept(idx, np));
				} catch (Throwable ex) {
					Log.d(ex, "Eczane konumu alınamadı: ", ph.slug());
				}
			}
		});
	}

	static void initCache(Context ctx) {
		if (llPrefs == null)
			llPrefs = ctx.getApplicationContext().getSharedPreferences("yolda_eczane_konum", Context.MODE_PRIVATE);
	}

	// ---- konum ve uzaklık ----

	/** Bilinen son konum (izin yoksa ya da bilinmiyorsa null). */
	@Nullable
	public static double[] myLocation(Context ctx) {
		try {
			if ((ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
					&& (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED))
				return null;
			LocationManager lm = ctx.getSystemService(LocationManager.class);
			Location best = null;
			for (String pr : lm.getProviders(true)) {
				Location l = lm.getLastKnownLocation(pr);
				if ((l != null) && ((best == null) || (l.getTime() > best.getTime()))) best = l;
			}
			return (best != null) ? new double[]{best.getLatitude(), best.getLongitude()} : null;
		} catch (Throwable ex) {
			return null;
		}
	}

	/** Kuş uçuşu uzaklık (km). */
	public static double km(double la1, double lo1, double la2, double lo2) {
		double r = 6371.0;
		double dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
		double a = Math.sin(dLa / 2) * Math.sin(dLa / 2) + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2))
				* Math.sin(dLo / 2) * Math.sin(dLo / 2);
		return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	/** "850 m", "1,2 km", "14 km". */
	public static String fmtKm(double km) {
		if (km < 1) return Math.max(10, Math.round(km * 100) * 10) + " m";
		if (km < 10) return String.format(new Locale("tr", "TR"), "%.1f km", km);
		return Math.round(km) + " km";
	}

	/** Konuma göre en yakın önce (konumu bilinmeyenler sonda, kendi sıralarında). */
	public static List<Pharmacy> sortByDistance(List<Pharmacy> l, @Nullable double[] loc) {
		if (loc == null) return l;
		List<Pharmacy> s = new ArrayList<>(l);
		s.sort((a, b) -> Double.compare(a.hasLocation() ? km(loc[0], loc[1], a.lat(), a.lon()) : Double.MAX_VALUE,
				b.hasLocation() ? km(loc[0], loc[1], b.lat(), b.lon()) : Double.MAX_VALUE));
		return s;
	}

	/** Konumdan il adını bulur (ilk açılışta il otomatik seçilsin). */
	@Nullable
	public static String provinceAt(Context ctx, double[] ll) {
		try {
			android.location.Geocoder g = new android.location.Geocoder(ctx, new Locale("tr", "TR"));
			@SuppressWarnings("deprecation") List<android.location.Address> a = g.getFromLocation(ll[0], ll[1], 1);
			if ((a != null) && !a.isEmpty()) return a.get(0).getAdminArea();
		} catch (Throwable ignore) {
		}
		return null;
	}

	// ---- seçimler ve yıldızlar ----

	public static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	@Nullable
	public static Place selectedIl(Context ctx) {
		return Place.decode(prefs(ctx).getString("ecz_il", null));
	}

	@Nullable
	public static Place selectedIlce(Context ctx) {
		return Place.decode(prefs(ctx).getString("ecz_ilce", null));
	}

	public static void select(Context ctx, @Nullable Place il, @Nullable Place ilce) {
		prefs(ctx).edit().putString("ecz_il", (il != null) ? il.encode() : null)
				.putString("ecz_ilce", (ilce != null) ? ilce.encode() : null).apply();
	}

	/** Yıldızlı iller ("ankara|Ankara") ve ilçeler ("ankara/cankaya|Çankaya"), eklenme sırasıyla. */
	public static List<String> stars(Context ctx, String kind) {
		String s = prefs(ctx).getString("ecz_star_" + kind, "");
		List<String> l = new ArrayList<>();
		for (String x : s.split("\n")) if (!x.isEmpty()) l.add(x);
		return l;
	}

	public static boolean isStar(Context ctx, String kind, String key) {
		for (String s : stars(ctx, kind)) if (s.startsWith(key + "|")) return true;
		return false;
	}

	public static boolean toggleStar(Context ctx, String kind, String key, String name) {
		Set<String> l = new LinkedHashSet<>(stars(ctx, kind));
		boolean on = !isStar(ctx, kind, key);
		l.removeIf(s -> s.startsWith(key + "|"));
		if (on) l.add(key + "|" + name);
		prefs(ctx).edit().putString("ecz_star_" + kind, String.join("\n", l)).apply();
		return on;
	}

	// ---- yardımcılar ----

	private interface Job<T> {
		T run() throws Exception;
	}

	private static final java.util.concurrent.ExecutorService EXEC = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "YoldaEczane");
		t.setDaemon(true);
		return t;
	});

	private static <T> FutureSupplier<T> async(Job<T> j) {
		Promise<T> p = new Promise<>();
		EXEC.submit(() -> {
			try {
				p.complete(j.run());
			} catch (Throwable ex) {
				Log.e(ex, "Nöbetçi eczane");
				YoldaLog.add("Eczane: alınamadı (" + ex + ")");
				p.completeExceptionally(ex);
			}
		});
		return p;
	}

	static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(10000);
		c.setReadTimeout(15000);
		c.setRequestProperty("User-Agent", UA);
		c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9");
		try {
			int code = c.getResponseCode();
			if (code >= 400) throw new java.io.IOException("Sunucu yanıtı " + code);
			try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] buf = new byte[16384];
				for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
				return out.toString(StandardCharsets.UTF_8.name());
			}
		} finally {
			c.disconnect();
		}
	}

	/** HTML etiketlerini ve kodlarını temizler, boşlukları sadeleştirir. */
	static String clean(String s) {
		String t = Html.fromHtml(s.replaceAll("(?s)<[^>]+>", " "), Html.FROM_HTML_MODE_LEGACY).toString();
		return t.replace(' ', ' ').replaceAll("\\s+", " ").replaceAll(" ([,.])", "$1").trim();
	}

	/** "PARLAK ECZANESİ" → "Parlak Eczanesi". */
	static String title(String s) {
		Locale tr = new Locale("tr", "TR");
		StringBuilder b = new StringBuilder();
		for (String w : s.toLowerCase(tr).split(" ")) {
			if (w.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			b.append(w.substring(0, 1).toUpperCase(tr)).append(w.substring(1));
		}
		return b.toString();
	}

	/** Karşılaştırma için: küçük harf, Türkçe karakterler sadeleşmiş ("Çankaya" = "cankaya"). */
	static String fold(String s) {
		String t = s.toLowerCase(new Locale("tr", "TR")).replace('ı', 'i');
		t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return t.replaceAll("[^a-z0-9]", "");
	}

	private static int month(String s) {
		String f = fold(s);
		for (int i = 0; i < MONTHS.length; i++) if (fold(MONTHS[i]).equals(f)) return i;
		return -1;
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s);
		} catch (Exception ex) {
			return Double.NaN;
		}
	}

	private static String encodeList(List<Place> l) {
		JSONArray a = new JSONArray();
		for (Place p : l) a.put(p.encode());
		return a.toString();
	}

	private static List<Place> decodeList(@Nullable String s) {
		if (s == null) return Collections.emptyList();
		List<Place> l = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(s);
			for (int i = 0; i < a.length(); i++) {
				Place p = Place.decode(a.getString(i));
				if (p != null) l.add(p);
			}
		} catch (Exception ignore) {
		}
		return l;
	}
}
