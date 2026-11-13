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
	@androidx.annotation.Nullable
	private static java.lang.ref.WeakReference<Context> ui;
	private static int users;
	private static boolean service;
	/** Son hareket (10 km/s üstü) anı: hizmet uzun süre hareketsizse kapanır. */
	public static volatile long lastMoveAt;
	public static final YoldaDriveCore.Trip trip = new YoldaDriveCore.Trip();
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
		if (on) {
			startListening(ctx);
			if (feature(ctx, "bg") && hasUi()) YoldaDriveService.start(ctx);
		} else {
			YoldaDriveService.stop(ctx);
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
		if ("bg".equals(key)) {
			if (on && isEnabled(ctx) && hasUi()) YoldaDriveService.start(ctx);
			else if (!on) YoldaDriveService.stop(ctx);
		}
		if (!on && "trips".equals(key)) trip.end();
		changed();
	}

	public static boolean hasPermission(Context ctx) {
		return ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	// ---- yaşam döngüsü: Yolda'nın her ekranı (telefon, araç) bağlanır ----

	/** Yolda ekranı (telefon ya da araç) açıldı. */
	public static void attach(Context ctx) {
		app = ctx.getApplicationContext();
		ui = new java.lang.ref.WeakReference<>(ctx);
		users++;
		load(app);
		initCar(app);
		if (isEnabled(app)) {
			startListening(app);
			if (feature(app, "bg")) YoldaDriveService.start(app); // ekran açıkken başlatılabilir (Android kuralı)
		}
	}

	public static void detach() {
		if (--users <= 0) {
			users = 0;
			ui = null;
			if (!service) stopListening();
		}
	}

	/** Arka plan hizmeti başladı: ekran kapansa da dinlemeye devam. */
	public static void attachService(Context ctx) {
		app = ctx.getApplicationContext();
		service = true;
		lastMoveAt = SystemClock.elapsedRealtime();
		load(app);
		initCar(app);
		startListening(app);
	}

	public static void detachService() {
		service = false;
		Context c = app;
		if (c != null) endTrip(c);
		if (users <= 0) stopListening();
	}

	/** Yolda ekranı açık mı (telefon ya da araç). */
	public static boolean hasUi() {
		return users > 0;
	}

	private static boolean carInit;

	private static void initCar(Context ctx) {
		if (carInit) return;
		carInit = true;
		YoldaCar.init(ctx);
		YoldaCar.addListener(YoldaRadar::carChanged);
	}

	/** Araç bağlandı / ayrıldı: ayrılınca park yeri kaydedilir, yolculuk biter; ekran yoksa hizmet kapanır. */
	public static void carChanged(boolean connected) {
		Context c = app;
		if (c == null) return;
		if (connected) {
			if (isEnabled(c) && feature(c, "bg") && hasUi()) YoldaDriveService.start(c);
			return;
		}
		if (isEnabled(c) && feature(c, "park")) savePark(c, true);
		endTrip(c);
		if (!hasUi()) YoldaDriveService.stop(c);
	}

	/** İzin sonradan verildiyse dinlemeyi başlat. */
	public static void permissionChanged(Context ctx) {
		if (isEnabled(ctx)) {
			startListening(ctx);
			if (feature(ctx, "bg") && hasUi()) YoldaDriveService.start(ctx);
		}
		changed();
	}

	@SuppressLint("MissingPermission")
	private static void startListening(Context ctx) {
		if (listening || ((users <= 0) && !service) || !hasPermission(ctx)) return;
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

	/** Testler için: gerçek GPS yok sayılır (benzetilen konum bozulmasın). */
	public static volatile boolean ignoreGps;

	private static void onLocation(Location l) {
		if (ignoreGps) return;
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
		if (speedKmh >= 10) lastMoveAt = SystemClock.elapsedRealtime();
		if (feature(ctx, "trips")) {
			Long passed = null;
			for (Ahead n : YoldaRadarCore.nearby(la, lo, cams, 40, 1)) passed = n.cam().id();
			YoldaDriveCore.TripRec rec = trip.update(SystemClock.elapsedRealtime(), System.currentTimeMillis(), la, lo, speedKmh, passed);
			if (rec != null) saveTrip(ctx, rec);
		}

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

	// ---- park yeri ----

	/** Park yeri: {lat, lon, zaman (ms)}; yoksa null. */
	@androidx.annotation.Nullable
	public static double[] park(Context ctx) {
		SharedPreferences p = prefs(ctx);
		if (!p.contains("park_lat")) return null;
		return new double[]{Double.longBitsToDouble(p.getLong("park_lat", 0)), Double.longBitsToDouble(p.getLong("park_lon", 0)),
				p.getLong("park_at", 0)};
	}

	public static void clearPark(Context ctx) {
		prefs(ctx).edit().remove("park_lat").remove("park_lon").remove("park_at").apply();
		changed();
	}

	/**
	 * Bulunduğun yeri park yeri olarak kaydet (son 5 dk içindeki konum; yoksa telefonun bilinen son konumu).
	 * notify: araç ayrılınca bildirim gösterilir (dokununca yürüyerek yol tarifi).
	 */
	@SuppressLint("MissingPermission")
	public static boolean savePark(Context ctx, boolean notify) {
		double la = lat, lo = lon;
		if (Double.isNaN(la) || (SystemClock.elapsedRealtime() - fixAt > 5 * 60_000L)) {
			try {
				LocationManager m = (LocationManager) ctx.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
				Location l = hasPermission(ctx) && (m != null) ? m.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
				if (l == null && hasPermission(ctx) && (m != null)) l = m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if (l != null) {
					la = l.getLatitude();
					lo = l.getLongitude();
				}
			} catch (Throwable ignore) {
			}
		}
		if (Double.isNaN(la)) {
			YoldaLog.add("Park yeri kaydedilemedi: konum yok");
			return false;
		}
		prefs(ctx).edit().putLong("park_lat", Double.doubleToLongBits(la)).putLong("park_lon", Double.doubleToLongBits(lo))
				.putLong("park_at", System.currentTimeMillis()).apply();
		YoldaLog.add(String.format(Locale.ROOT, "Park yeri kaydedildi: %.5f,%.5f", la, lo));
		if (notify) notifyPark(ctx, la, lo);
		changed();
		return true;
	}

	/** Park yerine yürüyerek yol tarifi (Google Haritalar; yoksa harita uygulaması). */
	public static android.content.Intent walkIntent(double la, double lo) {
		return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(Locale.ROOT,
				"google.navigation:q=%.6f,%.6f&mode=w", la, lo))).setPackage("com.google.android.apps.maps")
				.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
	}

	public static void walkToPark(Context ctx) {
		double[] p = park(ctx);
		if (p == null) return;
		try {
			ctx.startActivity(walkIntent(p[0], p[1]));
		} catch (Throwable ex) {
			try {
				ctx.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(String.format(
						Locale.ROOT, "geo:%.6f,%.6f?q=%.6f,%.6f(Aracım)", p[0], p[1], p[0], p[1])))
						.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
			} catch (Throwable ignore) {
			}
		}
	}

	private static void notifyPark(Context ctx, double la, double lo) {
		try {
			android.app.NotificationManager nm = ctx.getSystemService(android.app.NotificationManager.class);
			String ch = "yolda_park";
			if ((android.os.Build.VERSION.SDK_INT >= 26) && (nm.getNotificationChannel(ch) == null))
				nm.createNotificationChannel(new android.app.NotificationChannel(ch, "Park yeri", android.app.NotificationManager.IMPORTANCE_DEFAULT));
			android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 3, walkIntent(la, lo),
					android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
			android.app.Notification.Builder b = (android.os.Build.VERSION.SDK_INT >= 26)
					? new android.app.Notification.Builder(ctx, ch) : new android.app.Notification.Builder(ctx);
			nm.notify(0x59_50, b.setSmallIcon(me.aap.fermata.R.drawable.notification).setContentTitle("Aracının yeri kaydedildi")
					.setContentText("Dokun, aracına yürüyerek yol tarifi al").setContentIntent(pi).setAutoCancel(true).build());
		} catch (Throwable ex) {
			YoldaLog.add("Park bildirimi gösterilemedi: " + ex);
		}
	}

	// ---- yolculuklar ----

	private static void endTrip(Context ctx) {
		YoldaDriveCore.TripRec rec = trip.end();
		if (rec != null) saveTrip(ctx, rec);
	}

	/** Kısa (20 km altı) yolculuklardan saklanan; 20 km ve üstü olanlar silinene kadar kalır. */
	private static final int MAX_SHORT = 30, MAX_LONG = 2000;

	private static synchronized void saveTrip(Context ctx, YoldaDriveCore.TripRec t) {
		try {
			JSONArray old = new JSONArray(prefs(ctx).getString("trips", "[]"));
			JSONArray all = new JSONArray();
			all.put(new JSONObject().put("s", t.startWall()).put("e", t.endWall()).put("m", t.meters()).put("mv", t.movingMs())
					.put("mx", t.maxKmh()).put("r", t.radars()).put("sla", t.startLat()).put("slo", t.startLon())
					.put("ela", t.endLat()).put("elo", t.endLon()));
			for (int i = 0; i < old.length(); i++) all.put(old.get(i));
			int[] m = new int[all.length()];
			for (int i = 0; i < m.length; i++) m[i] = all.getJSONObject(i).optInt("m");
			boolean[] keep = YoldaDriveCore.retain(m, MAX_SHORT, MAX_LONG);
			JSONArray a = new JSONArray();
			for (int i = 0; i < keep.length; i++) if (keep[i]) a.put(all.get(i));
			prefs(ctx).edit().putString("trips", a.toString()).apply();
			nameTrip(ctx, t);
			lastTrip = t;
			YoldaLog.add("Yolculuk bitti: " + t.summary());
			YoldaStyleToast.show(ctx, "Yolculuk bitti: " + t.summary());
			changed();
		} catch (Throwable ex) {
			Log.e(ex, "Yolculuk kaydedilemedi");
		}
	}

	/** Testler için: son kaydedilen yolculuk. */
	public static volatile YoldaDriveCore.TripRec lastTrip;

	/** Yolculuk ve nereden nereye ("Kadıköy → Ankara"; adı henüz bulunmadıysa ""). */
	public record TripItem(YoldaDriveCore.TripRec rec, String route) {
	}

	/** Yolculuklar (en yeni önce): 20 km ve üstü olanların hepsi, kısalardan son 30. */
	public static List<YoldaDriveCore.TripRec> trips(Context ctx) {
		List<YoldaDriveCore.TripRec> l = new ArrayList<>();
		for (TripItem t : tripItems(ctx)) l.add(t.rec());
		return l;
	}

	public static List<TripItem> tripItems(Context ctx) {
		List<TripItem> l = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(prefs(ctx).getString("trips", "[]"));
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				YoldaDriveCore.TripRec r = new YoldaDriveCore.TripRec(o.getLong("s"), o.getLong("e"), o.getInt("m"), o.getLong("mv"),
						o.getInt("mx"), o.optInt("r"), o.optDouble("sla", Double.NaN), o.optDouble("slo", Double.NaN),
						o.optDouble("ela", Double.NaN), o.optDouble("elo", Double.NaN));
				l.add(new TripItem(r, YoldaDriveCore.route(o.optString("fi"), o.optString("fl"), o.optString("ti"), o.optString("tl"))));
			}
		} catch (Throwable ignore) {
		}
		return l;
	}

	/** Tek yolculuğu sil (başlangıç zamanıyla). */
	public static synchronized void deleteTrip(Context ctx, long startWall) {
		try {
			JSONArray a = new JSONArray(prefs(ctx).getString("trips", "[]"));
			JSONArray b = new JSONArray();
			for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).optLong("s") != startWall) b.put(a.get(i));
			prefs(ctx).edit().putString("trips", b.toString()).apply();
		} catch (Throwable ignore) {
		}
		changed();
	}

	public static void clearTrips(Context ctx) {
		prefs(ctx).edit().remove("trips").apply();
		changed();
	}

	/** Başlangıç ve bitişin ilçe / il adı (arka planda; internet / Geocoder yoksa boş kalır). */
	private static void nameTrip(Context ctx, YoldaDriveCore.TripRec t) {
		if (Double.isNaN(t.startLat())) return;
		Context c = ctx.getApplicationContext();
		NET.submit(() -> {
			String[] f = place(c, t.startLat(), t.startLon()), to = place(c, t.endLat(), t.endLon());
			if ((f == null) && (to == null)) return;
			synchronized (YoldaRadar.class) {
				try {
					JSONArray a = new JSONArray(prefs(c).getString("trips", "[]"));
					for (int i = 0; i < a.length(); i++) {
						JSONObject o = a.getJSONObject(i);
						if (o.optLong("s") != t.startWall()) continue;
						if (f != null) o.put("fi", f[0]).put("fl", f[1]);
						if (to != null) o.put("ti", to[0]).put("tl", to[1]);
					}
					prefs(c).edit().putString("trips", a.toString()).apply();
				} catch (Throwable ignore) {
				}
			}
			changed();
		});
	}

	/** {ilçe, il}; bulunamazsa null. */
	@androidx.annotation.Nullable
	private static String[] place(Context ctx, double la, double lo) {
		try {
			android.location.Geocoder g = new android.location.Geocoder(ctx, new Locale("tr", "TR"));
			@SuppressWarnings("deprecation") List<android.location.Address> l = g.getFromLocation(la, lo, 1);
			if ((l == null) || l.isEmpty()) return null;
			android.location.Address ad = l.get(0);
			String ilce = (ad.getSubAdminArea() != null) ? ad.getSubAdminArea() : (ad.getLocality() != null) ? ad.getLocality() : "";
			String il = (ad.getAdminArea() != null) ? ad.getAdminArea() : "";
			return new String[]{ilce, il};
		} catch (Throwable ex) {
			return null;
		}
	}

	/** Kısa yazı: açık Yolda ekranında (arka planda yalnız ses). */
	private static final class YoldaStyleToast {
		static void show(Context ctx, String text) {
			java.lang.ref.WeakReference<Context> w = ui;
			Context c = (w != null) ? w.get() : null;
			if (c == null) return;
			App.get().getHandler().post(() -> me.aap.fermata.ui.view.YoldaStyle.toast(c, text));
		}
	}
}
