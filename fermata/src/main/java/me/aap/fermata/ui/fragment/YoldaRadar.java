package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.aap.fermata.ui.fragment.YoldaRadarCore.Ahead;
import me.aap.fermata.ui.fragment.YoldaRadarCore.Cam;
import me.aap.fermata.ui.fragment.YoldaRadarCore.Road;
import me.aap.utils.app.App;
import me.aap.utils.log.Log;

/**
 * Radar ve hız sınırı: Yolda açıkken GPS izlenir; gidiş yönündeki radara yaklaşınca bip + sesli uyarı
 * (1000 / 500 / 150 m), bulunduğun yolun hız sınırı ve anlık hız sol kenarda gösterilir; sınır aşılınca uyarır.
 * Radarlar: OpenStreetMap (uygulamayla gelir, haftada bir güncellenir) + kullanıcının eklediği yerler.
 * Hız sınırı: OpenStreetMap yol etiketi; etiket yoksa yol türünden kesin olan genel sınır (otoyol 120…).
 */
public final class YoldaRadar {
	public static final String PREFS = "yolda_radar";
	private static final String ASSET = "yolda_radars.csv";
	private static final String FILE = "yolda_radars.csv";
	private static final long UPDATE_EVERY = 7L * 24 * 3600_000L;
	private static final String[] OVERPASS = {"https://overpass-api.de/api/interpreter",
			"https://overpass.kumi.systems/api/interpreter", "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
			"https://overpass.private.coffee/api/interpreter"};
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
	private static final ExecutorService NET = Executors.newSingleThreadExecutor();

	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static volatile List<Cam> cams = new ArrayList<>();
	private static volatile boolean loaded;
	private static final Map<Long, Road> roads = new LinkedHashMap<>();
	private static final YoldaRadarCore.Alerter alerter = new YoldaRadarCore.Alerter();

	private static Context app;
	private static int users;
	private static LocationManager lm;
	private static boolean listening;

	// ---- anlık durum (ana iş parçacığı) ----
	public static volatile double lat = Double.NaN, lon = Double.NaN;
	public static volatile int speed = -1;
	public static volatile double heading = -1;
	public static volatile long fixAt;
	public static volatile Road road;
	public static volatile int limit;
	public static volatile boolean limitEstimated;
	public static volatile Ahead ahead;
	/** Testler için: son verilen uyarı cümlesi. */
	public static volatile String lastAlert;
	private static double fetchLat = Double.NaN, fetchLon = Double.NaN;
	private static long fetchAt, failAt;
	private static boolean fetching;
	private static int mirror;
	private static long overSince, overWarnAt;
	private static int overWarnLimit;

	// ---- sürüş yardımcıları ----
	public static final YoldaDriveCore.AvgSpeed avg = new YoldaDriveCore.AvgSpeed();
	public static final YoldaDriveCore.Fatigue fatigue = new YoldaDriveCore.Fatigue();
	public static volatile YoldaDriveCore.Place zone;
	public static volatile List<YoldaDriveCore.Stop> stops = new ArrayList<>();
	private static final Map<Long, YoldaDriveCore.Place> places = new LinkedHashMap<>();
	private static long lastZoneId = Long.MIN_VALUE, lastZoneAt, avgWarnAt;
	private static double placesLat = Double.NaN, placesLon = Double.NaN;
	private static long placesAt, placesFailAt;
	private static boolean placesFetching;

	private YoldaRadar() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	// ---- ayarlar ----

	public static boolean isEnabled(Context ctx) {
		return prefs(ctx).getBoolean("enabled", true);
	}

	public static void setEnabled(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean("enabled", on).apply();
		YoldaLog.add("Radar uyarıları: " + (on ? "açık" : "kapalı"));
		if (on) startListening(ctx);
		else {
			stopListening();
			ahead = null;
			alerter.reset();
		}
		changed();
	}

	public static boolean voice(Context ctx) {
		return prefs(ctx).getBoolean("voice", true);
	}

	public static void setVoice(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean("voice", on).apply();
		changed();
	}

	public static boolean overspeedWarn(Context ctx) {
		return prefs(ctx).getBoolean("overspeed", true);
	}

	public static void setOverspeedWarn(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean("overspeed", on).apply();
		changed();
	}

	/** Ayrı açılıp kapatılan özellikler: cams (radar uyarısı), avg, zones, stops, fatigue. */
	public static boolean feature(Context ctx, String key) {
		return prefs(ctx).getBoolean("f_" + key, true);
	}

	public static void setFeature(Context ctx, String key, boolean on) {
		prefs(ctx).edit().putBoolean("f_" + key, on).apply();
		YoldaLog.add("Sürüş yardımcısı " + key + ": " + (on ? "açık" : "kapalı"));
		if (!on && "avg".equals(key)) avg.cancel();
		if (!on && "zones".equals(key)) zone = null;
		if (!on && "cams".equals(key)) ahead = null;
		changed();
	}

	public static boolean hasPermission(Context ctx) {
		return ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	// ---- yaşam döngüsü: Yolda'nın her ekranı (telefon, araç) bağlanır ----

	public static void attach(Context ctx) {
		app = ctx.getApplicationContext();
		users++;
		load(app);
		if (isEnabled(app)) startListening(app);
	}

	public static void detach() {
		if (--users <= 0) {
			users = 0;
			stopListening();
		}
	}

	/** İzin sonradan verildiyse dinlemeyi başlat. */
	public static void permissionChanged(Context ctx) {
		if (isEnabled(ctx)) startListening(ctx);
		changed();
	}

	@SuppressLint("MissingPermission")
	private static void startListening(Context ctx) {
		if (listening || (users <= 0) || !hasPermission(ctx)) return;
		try {
			lm = (LocationManager) ctx.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
			if (lm == null) return;
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, LISTENER, Looper.getMainLooper());
			listening = true;
			Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if ((last != null) && (System.currentTimeMillis() - last.getTime() < 60_000)) onLocation(last);
			YoldaLog.add("Radar: konum dinleniyor");
		} catch (Throwable ex) {
			Log.e(ex, "Radar: konum dinlenemedi");
		}
	}

	private static void stopListening() {
		if (!listening) return;
		listening = false;
		try {
			if (lm != null) lm.removeUpdates(LISTENER);
		} catch (Throwable ignore) {
		}
		fixAt = 0;
		speed = -1;
		changed();
	}

	public static boolean isListening() {
		return listening;
	}

	private static final LocationListener LISTENER = new LocationListener() {
		@Override
		public void onLocationChanged(Location l) {
			onLocation(l);
		}

		@Override
		public void onStatusChanged(String p, int s, Bundle b) {
		}

		@Override
		public void onProviderEnabled(String p) {
		}

		@Override
		public void onProviderDisabled(String p) {
			fixAt = 0;
			changed();
		}
	};

	private static void onLocation(Location l) {
		int sp = l.hasSpeed() ? Math.round(l.getSpeed() * 3.6f) : -1;
		double hd = (l.hasBearing() && (sp >= 8)) ? l.getBearing() : ((sp >= 0) && (sp < 5) ? -1 : heading);
		feed(l.getLatitude(), l.getLongitude(), sp, hd);
	}

	/** Yeni konum (testler doğrudan verir). speedKmh &lt; 0: bilinmiyor; heading &lt; 0: yön yok (duruyor). */
	public static void feed(double la, double lo, int speedKmh, double hd) {
		lat = la;
		lon = lo;
		speed = speedKmh;
		heading = hd;
		fixAt = SystemClock.elapsedRealtime();
		Context ctx = app;
		if (ctx == null) return;
		if (!isEnabled(ctx)) {
			changed();
			return;
		}

		// hız sınırı: bulunduğun yol
		Road r;
		synchronized (roads) {
			r = YoldaRadarCore.matchRoad(la, lo, hd, new ArrayList<>(roads.values()));
		}
		road = r;
		if (r != null) {
			limit = r.limit();
			limitEstimated = r.estimated();
		} else {
			limit = 0;
		}
		if ((r == null) || needFetch(la, lo)) fetchRoads(la, lo);

		// radar
		Ahead a = feature(ctx, "cams") ? YoldaRadarCore.ahead(la, lo, hd, cams, 1500) : null;
		ahead = a;
		if ((limit == 0) && (a != null) && (a.meters() < 300) && (a.cam().limit() > 0)) {
			limit = a.cam().limit(); // yol etiketi yoksa radarın sınırı
			limitEstimated = false;
		}
		int stage = alerter.update(a, Math.max(0, speedKmh));
		if (stage > 0) alert(ctx, YoldaRadarCore.alertText(a, stage, limit), stage <= 150, 'r');
		helpers(ctx, la, lo, speedKmh, hd);

		// hız aşımı: 3 sn sürerse çift bip, sürerse 20 sn'de bir (sınır değişince hemen)
		long now = SystemClock.elapsedRealtime();
		if (overspeedWarn(ctx) && YoldaRadarCore.overSpeed(speedKmh, limit) && !limitEstimated) {
			if (overSince == 0) overSince = now;
			if ((now - overSince >= 3000) && ((now - overWarnAt > 20_000) || (overWarnLimit != limit))) {
				overWarnAt = now;
				overWarnLimit = limit;
				overBeep(ctx); // hız sınırı: yalnız bip (sol kenarda levha yanıp söner)
			}
		} else {
			overSince = 0;
		}
		changed();
	}

	/** Ortalama hız koridoru, okul / hastane bölgesi, yorgunluk, öndeki mola yerleri. */
	private static void helpers(Context ctx, double la, double lo, int sp, double hd) {
		long now = SystemClock.elapsedRealtime();
		// ortalama hız
		if (feature(ctx, "avg")) {
			Cam passed = null;
			for (Ahead n : YoldaRadarCore.nearby(la, lo, cams, 40, 3)) {
				if (n.cam().kind() == 'a') {
					passed = n.cam();
					break;
				}
			}
			boolean was = avg.isActive();
			boolean ended = avg.update(la, lo, now, passed);
			int lim = (avg.limit() > 0) ? avg.limit() : limit;
			if (!was && avg.isActive()) {
				alert(ctx, "Ortalama hız denetimi başladı" + ((lim > 0) ? (", sınır " + lim) : ""), false, 'a');
			} else if (ended) {
				int res = avg.lastResult();
				alert(ctx, "Ortalama hız denetimi bitti" + ((res > 0) ? (", ortalaman " + res) : "")
						+ (((res > 0) && (lim > 0) && YoldaRadarCore.overSpeed(res, lim)) ? ", sınırın üstünde" : ""), false, 'a');
			} else if (avg.isActive() && (lim > 0) && YoldaRadarCore.overSpeed(avg.average(), lim) && (now - avgWarnAt > 60_000)) {
				avgWarnAt = now;
				alert(ctx, "Ortalaman " + avg.average() + ", sınır " + lim + ". Yavaşla", false, 'a');
			}
		}
		// okul / hastane
		List<YoldaDriveCore.Place> pl;
		synchronized (places) {
			pl = new ArrayList<>(places.values());
		}
		if (feature(ctx, "zones")) {
			java.util.Calendar cal = java.util.Calendar.getInstance();
			YoldaDriveCore.Place z = YoldaDriveCore.zone(la, lo, hd, pl, YoldaDriveCore.schoolHours(
					cal.get(java.util.Calendar.DAY_OF_WEEK), cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)));
			zone = z;
			if ((z != null) && ((z.id() != lastZoneId) || (now - lastZoneAt > 10 * 60_000L))) {
				lastZoneId = z.id();
				lastZoneAt = now;
				String what = (z.kind() == 's') ? "Okul bölgesi" : "Hastane bölgesi";
				if (sp > YoldaDriveCore.ZONE_SPEED + 10) alert(ctx, what + ", yavaşla", false, 'z');
				else {
					YoldaStyleToast.show(ctx, "⚠ " + what);
					mark('z');
				}
			}
		} else {
			zone = null;
		}
		// mola yerleri
		stops = feature(ctx, "stops") ? YoldaDriveCore.stopsAhead(la, lo, hd, pl, 30_000, 8) : new ArrayList<>();
		if (feature(ctx, "zones") || feature(ctx, "stops")) fetchPlaces(la, lo);
		// yorgunluk
		if (feature(ctx, "fatigue") && fatigue.update(now, sp)) {
			String t = YoldaDriveCore.duration(fatigue.drivingMs()) + "dir ara vermeden sürüyorsun. Bir mola iyi gelir";
			List<YoldaDriveCore.Stop> st = stops;
			if (!st.isEmpty()) t += ". En yakın " + st.get(0).place().label().toLowerCase(new Locale("tr")) + " "
					+ RadarFragment.meters(st.get(0).meters()).replace(" km", " kilometre").replace(" m", " metre") + " ileride";
			alert(ctx, t, false, 'f');
		}
	}

	private static void fetchPlaces(double la, double lo) {
		long now = SystemClock.elapsedRealtime();
		if (placesFetching || ((placesFailAt != 0) && (now - placesFailAt < 60_000))) return;
		boolean moved = Double.isNaN(placesLat) || (YoldaRadarCore.dist(la, lo, placesLat, placesLon) > 1000);
		if (!moved && (now - placesAt < 10 * 60_000L)) return;
		if ((placesAt != 0) && (now - placesAt < 20_000)) return;
		placesFetching = true;
		placesAt = now;
		NET.submit(() -> {
			String q = String.format(Locale.ROOT, "[out:json][timeout:25];(nwr(around:1500,%1$.5f,%2$.5f)[\"amenity\"~\"^(school|hospital)$\"];"
					+ "nwr(around:20000,%1$.5f,%2$.5f)[\"amenity\"=\"fuel\"];nwr(around:30000,%1$.5f,%2$.5f)[\"highway\"~\"^(rest_area|services)$\"];"
					+ ");out center tags;", la, lo);
			try {
				JSONArray el = new JSONObject(overpass(q, 30_000)).getJSONArray("elements");
				List<YoldaDriveCore.Place> got = new ArrayList<>();
				for (int i = 0; i < el.length(); i++) {
					JSONObject o = el.getJSONObject(i);
					JSONObject t = o.optJSONObject("tags");
					if (t == null) continue;
					JSONObject c = o.optJSONObject("center");
					double pla = (c != null) ? c.getDouble("lat") : o.optDouble("lat", Double.NaN);
					double plo = (c != null) ? c.getDouble("lon") : o.optDouble("lon", Double.NaN);
					if (Double.isNaN(pla) || Double.isNaN(plo)) continue;
					String am = t.optString("amenity"), hw = t.optString("highway");
					char k = "school".equals(am) ? 's' : "hospital".equals(am) ? 'h' : "fuel".equals(am) ? 'f' : (!hw.isEmpty() ? 'r' : 0);
					if (k == 0) continue;
					String name = t.optString("name", t.optString("brand", ""));
					String type = o.optString("type");
					long id = o.getLong("id") * 4 + ("node".equals(type) ? 0 : "way".equals(type) ? 1 : 2);
					got.add(new YoldaDriveCore.Place(id, pla, plo, k, name));
				}
				synchronized (places) {
					for (YoldaDriveCore.Place p : got) places.put(p.id(), p);
					while (places.size() > 4000) places.remove(places.keySet().iterator().next());
				}
				placesLat = la;
				placesLon = lo;
				placesFailAt = 0;
			} catch (Throwable ex) {
				placesFailAt = SystemClock.elapsedRealtime();
				mirror++;
				Log.d(ex, "Sürüş yardımcıları: yerler alınamadı");
			} finally {
				placesFetching = false;
			}
		});
	}

	/** Testler için: yakındaki yerleri değiştir (ağdan alma bir süre beklesin). */
	public static void setPlaces(List<YoldaDriveCore.Place> l) {
		synchronized (places) {
			places.clear();
			for (YoldaDriveCore.Place p : l) places.put(p.id(), p);
		}
		placesLat = lat;
		placesLon = lon;
		placesAt = SystemClock.elapsedRealtime();
		lastZoneId = Long.MIN_VALUE;
	}

	/** Son uyarının zamanı ve türü (sol kenar o göstergeyi yanıp söndürür): r radar, o hız aşımı, a ortalama, z bölge, f mola. */
	public static volatile long alertAt;
	public static volatile char alertKind;
	/** Testler için: son hız aşımı bipinin zamanı. */
	public static volatile long overBeepAt;

	private static void mark(char kind) {
		alertKind = kind;
		alertAt = SystemClock.elapsedRealtime();
	}

	/** Hız sınırı aşımı: konuşmadan, yazısız, yalnız bip. */
	private static void overBeep(Context ctx) {
		overBeepAt = SystemClock.elapsedRealtime();
		YoldaLog.add("Hız sınırı aşıldı: " + speed + " / " + limit + " (bip)");
		mark('o');
		beep(false);
		App.get().getHandler().postDelayed(() -> beep(false), 260); // çift bip: radardan ayırt edilsin
	}

	private static void alert(Context ctx, String text, boolean urgent, char kind) {
		lastAlert = text;
		mark(kind);
		YoldaLog.add("Radar uyarısı: " + text);
		beep(urgent);
		YoldaStyleToast.show(ctx, "⚠ " + text);
		if (voice(ctx)) App.get().getHandler().postDelayed(() -> YoldaSpeak.sayAlways(ctx, text), 350);
	}

	private static void beep(boolean urgent) {
		try {
			ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
			tg.startTone(urgent ? ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD : ToneGenerator.TONE_PROP_BEEP2, urgent ? 300 : 200);
			App.get().getHandler().postDelayed(tg::release, 600);
		} catch (Throwable ignore) {
		}
	}

	// ---- dinleyiciler ----

	public static void addListener(Runnable r) {
		listeners.add(r);
	}

	public static void removeListener(Runnable r) {
		listeners.remove(r);
	}

	private static void changed() {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			App.get().getHandler().post(YoldaRadar::changed);
			return;
		}
		for (Runnable r : listeners) {
			try {
				r.run();
			} catch (Throwable ex) {
				Log.e(ex, "Radar dinleyicisi");
			}
		}
	}

	/** Konum taze mi (son 10 sn). */
	public static boolean hasFix() {
		return (fixAt != 0) && (SystemClock.elapsedRealtime() - fixAt < 10_000);
	}

	// ---- radar listesi ----

	public static List<Cam> cams() {
		return cams;
	}

	/** Testler için: radar listesini değiştir. */
	public static void setCams(List<Cam> l) {
		cams = new ArrayList<>(l);
		loaded = true;
		alerter.reset();
	}

	private static void load(Context ctx) {
		if (loaded) return;
		loaded = true;
		EXEC.submit(() -> {
			List<Cam> l = new ArrayList<>();
			File f = new File(ctx.getFilesDir(), FILE);
			String csv = null;
			try {
				if (f.isFile() && (f.length() > 1000)) csv = new String(java.nio.file.Files.readAllBytes(f.toPath()),
						StandardCharsets.UTF_8);
			} catch (Throwable ignore) {
			}
			if (csv == null) {
				try (InputStream in = ctx.getAssets().open(ASSET)) {
					csv = new String(readAll(in), StandardCharsets.UTF_8);
				} catch (Throwable ex) {
					Log.e(ex, "Radar listesi okunamadı");
				}
			}
			l.addAll(YoldaRadarCore.parseCsv(csv));
			l.addAll(YoldaRadarCore.parseCsv(prefs(ctx).getString("user_cams", "")));
			cams = l;
			YoldaLog.add("Radar: " + l.size() + " radar yüklendi");
			changed();
			if (System.currentTimeMillis() - prefs(ctx).getLong("updated_at", 0) > UPDATE_EVERY) update(ctx, null);
		});
	}

	public static long updatedAt(Context ctx) {
		return prefs(ctx).getLong("updated_at", 0);
	}

	public static int userCount(Context ctx) {
		return YoldaRadarCore.parseCsv(prefs(ctx).getString("user_cams", "")).size();
	}

	/** Türkiye'nin radarlarını OpenStreetMap'ten yeniden al (haftada bir kendiliğinden). */
	public static void update(Context ctx, @androidx.annotation.Nullable java.util.function.Consumer<String> done) {
		Context c = ctx.getApplicationContext();
		NET.submit(() -> {
			String q = "[out:json][timeout:90];area[\"ISO3166-1\"=\"TR\"][admin_level=2]->.tr;(node[\"highway\"=\"speed_camera\"](area.tr);"
					+ "node[\"enforcement\"~\"^(maxspeed|average_speed|traffic_signals)$\"](area.tr););out body;";
			String res = null;
			try {
				JSONArray el = new JSONObject(overpass(q, 120_000)).getJSONArray("elements");
				List<Cam> l = new ArrayList<>();
				for (int i = 0; i < el.length(); i++) {
					JSONObject o = el.getJSONObject(i);
					JSONObject t = o.optJSONObject("tags");
					if ((t == null) || !o.has("lat")) continue;
					String enf = t.optString("enforcement");
					char kind = "average_speed".equals(enf) ? 'a' : "traffic_signals".equals(enf) ? 'k' : 'r';
					l.add(new Cam(o.getLong("id"), o.getDouble("lat"), o.getDouble("lon"),
							YoldaRadarCore.parseMaxspeed(t.optString("maxspeed")), YoldaRadarCore.parseDir(t.optString("direction")), kind));
				}
				if (l.size() < 100) throw new IllegalStateException("az radar: " + l.size());
				String csv = YoldaRadarCore.toCsv(l);
				try (OutputStream out = new java.io.FileOutputStream(new File(c.getFilesDir(), FILE))) {
					out.write(csv.getBytes(StandardCharsets.UTF_8));
				}
				prefs(c).edit().putLong("updated_at", System.currentTimeMillis()).apply();
				l.addAll(YoldaRadarCore.parseCsv(prefs(c).getString("user_cams", "")));
				cams = l;
				res = l.size() + " radar güncellendi";
				changed();
			} catch (Throwable ex) {
				Log.e(ex, "Radar listesi güncellenemedi");
				res = "Radar listesi güncellenemedi (internet / sunucu yoğun)";
			}
			YoldaLog.add("Radar: " + res);
			String r = res;
			if (done != null) App.get().getHandler().post(() -> done.accept(r));
		});
	}

	/** Bulunduğun yere radar ekle (OSM'de olmayan). */
	public static boolean addHere(Context ctx) {
		if (Double.isNaN(lat)) return false;
		List<Cam> u = YoldaRadarCore.parseCsv(prefs(ctx).getString("user_cams", ""));
		Cam c = new Cam(-System.currentTimeMillis(), lat, lon, limit, (heading >= 0) ? (int) Math.round(heading) : -1, 'u');
		u.add(c);
		prefs(ctx).edit().putString("user_cams", YoldaRadarCore.toCsv(u)).apply();
		List<Cam> all = new ArrayList<>(cams);
		all.add(c);
		cams = all;
		changed();
		return true;
	}

	public static void removeUser(Context ctx, long id) {
		List<Cam> u = YoldaRadarCore.parseCsv(prefs(ctx).getString("user_cams", ""));
		u.removeIf(c -> c.id() == id);
		prefs(ctx).edit().putString("user_cams", YoldaRadarCore.toCsv(u)).apply();
		List<Cam> all = new ArrayList<>(cams);
		all.removeIf(c -> c.id() == id);
		cams = all;
		changed();
	}

	// ---- hız sınırı: yakındaki yollar ----

	private static boolean needFetch(double la, double lo) {
		return Double.isNaN(fetchLat) || (YoldaRadarCore.dist(la, lo, fetchLat, fetchLon) > 220);
	}

	private static void fetchRoads(double la, double lo) {
		long now = SystemClock.elapsedRealtime();
		if (fetching || (now - fetchAt < 8000) || ((failAt != 0) && (now - failAt < 30_000))) return;
		if (!needFetch(la, lo) && (now - fetchAt < 120_000)) return;
		fetching = true;
		fetchAt = now;
		NET.submit(() -> {
			String q = String.format(Locale.ROOT, "[out:json][timeout:15];way(around:350,%.5f,%.5f)[\"highway\"~\"^(motorway|trunk|"
					+ "primary|secondary|tertiary|unclassified|residential|living_street|motorway_link|trunk_link|primary_link|"
					+ "secondary_link|tertiary_link)$\"];out tags geom;", la, lo);
			try {
				JSONArray el = new JSONObject(overpass(q, 20_000)).getJSONArray("elements");
				List<Road> got = new ArrayList<>();
				for (int i = 0; i < el.length(); i++) {
					JSONObject o = el.getJSONObject(i);
					JSONObject t = o.optJSONObject("tags");
					JSONArray g = o.optJSONArray("geometry");
					if ((t == null) || (g == null) || (g.length() < 2)) continue;
					double[] pts = new double[g.length() * 2];
					for (int j = 0; j < g.length(); j++) {
						pts[2 * j] = g.getJSONObject(j).getDouble("lat");
						pts[2 * j + 1] = g.getJSONObject(j).getDouble("lon");
					}
					String hw = t.optString("highway");
					boolean oneway = "yes".equals(t.optString("oneway")) || hw.equals("motorway");
					int lim = YoldaRadarCore.parseMaxspeed(t.optString("maxspeed"));
					boolean est = false;
					if (lim == 0) {
						lim = YoldaRadarCore.parseMaxspeed(t.optString("maxspeed:type", t.optString("source:maxspeed")));
						if (lim == 0) {
							lim = YoldaRadarCore.defaultLimit(hw, oneway);
							est = lim > 0;
						}
					}
					got.add(new Road(o.getLong("id"), hw, lim, est, oneway, pts));
				}
				synchronized (roads) {
					for (Road r : got) roads.put(r.id(), r);
					// En fazla 1500 yol: en eskiler çıkar
					while (roads.size() > 1500) roads.remove(roads.keySet().iterator().next());
				}
				fetchLat = la;
				fetchLon = lo;
				failAt = 0;
			} catch (Throwable ex) {
				failAt = SystemClock.elapsedRealtime();
				mirror++;
				Log.d(ex, "Radar: yol bilgisi alınamadı");
			} finally {
				fetching = false;
			}
		});
	}

	private static String overpass(String query, int timeout) throws Exception {
		Exception last = null;
		for (int k = 0; k < OVERPASS.length; k++) {
			String u = OVERPASS[Math.floorMod(mirror + k, OVERPASS.length)];
			HttpURLConnection c = null;
			try {
				c = (HttpURLConnection) new URL(u).openConnection();
				c.setConnectTimeout(8000);
				c.setReadTimeout(timeout);
				c.setRequestMethod("POST");
				c.setDoOutput(true);
				c.setRequestProperty("User-Agent", "Yolda/2 (Android)");
				c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				try (OutputStream out = c.getOutputStream()) {
					out.write(("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8));
				}
				if (c.getResponseCode() != 200) throw new IllegalStateException(u + ": HTTP " + c.getResponseCode());
				try (InputStream in = c.getInputStream()) {
					String s = new String(readAll(in), StandardCharsets.UTF_8);
					if (!s.contains("\"elements\"")) throw new IllegalStateException(u + ": yanıt yok");
					mirror = Math.floorMod(mirror + k, OVERPASS.length);
					return s;
				}
			} catch (Exception ex) {
				last = ex;
			} finally {
				if (c != null) c.disconnect();
			}
		}
		throw (last != null) ? last : new IllegalStateException("Overpass");
	}

	private static byte[] readAll(InputStream in) throws java.io.IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
		return b.toByteArray();
	}

	/** Kısa yazı için ara sınıf (YoldaStyle'a bağımlılık tek yerde). */
	private static final class YoldaStyleToast {
		static void show(Context ctx, String text) {
			App.get().getHandler().post(() -> me.aap.fermata.ui.view.YoldaStyle.toast(ctx, text));
		}
	}
}
