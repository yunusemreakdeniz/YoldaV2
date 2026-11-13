package me.aap.fermata.ui.fragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Sürüş yardımcılarının cihaz gerektirmeyen hesabı (birim testli): ortalama hız koridoru, yorgunluk
 * hatırlatıcısı, okul / hastane bölgesi, yol üstündeki mola yerleri.
 */
public final class YoldaDriveCore {
	private YoldaDriveCore() {
	}

	// ---- ortalama hız koridoru ----

	/**
	 * Ortalama hız denetimi noktasından (kind 'a') geçince koridor başlar: gidilen yol / geçen süre. Bir sonraki
	 * denetim noktasında (en az 1 km sonra) ya da 60 km'de biter. OSM'de koridorlar yalnız giriş / çıkış
	 * noktası olarak var: ikisi eşlenmiyor, sıradaki noktada biter.
	 */
	public static final class AvgSpeed {
		private boolean active;
		private long startCam, startMs, lastMs, endCam = Long.MIN_VALUE;
		private double meters, lastLat = Double.NaN, lastLon;
		private int limit;
		private int lastResult = -1;

		/**
		 * @param passed bu konumda 40 m içinden geçilen ortalama hız noktası (yoksa null)
		 * @return true koridor bu adımda bittiyse ({@link #lastResult()} ortalama)
		 */
		public boolean update(double lat, double lon, long nowMs, YoldaRadarCore.Cam passed) {
			boolean ended = false;
			if (active && !Double.isNaN(lastLat)) {
				double d = YoldaRadarCore.dist(lastLat, lastLon, lat, lon);
				if (d < 500) meters += d; // GPS sıçraması sayılmaz
			}
			lastLat = lat;
			lastLon = lon;
			lastMs = nowMs;
			if (passed != null && passed.kind() == 'a') {
				if (!active) {
					if (passed.id() != endCam) start(passed, nowMs); // çıkış noktası yeni koridor başlatmasın
				} else if ((passed.id() != startCam) && (meters >= 1000)) {
					lastResult = average();
					active = false;
					endCam = passed.id();
					ended = true;
				}
			}
			if (active && (meters > 60_000)) {
				lastResult = average();
				active = false;
				ended = true;
			}
			return ended;
		}

		private void start(YoldaRadarCore.Cam c, long nowMs) {
			active = true;
			startCam = c.id();
			startMs = nowMs;
			meters = 0;
			limit = c.limit();
		}

		public boolean isActive() {
			return active;
		}

		/** Şu ana kadarki ortalama km/s (en az 200 m ve 10 sn sonra; öncesinde -1). */
		public int average() {
			long ms = lastMs - startMs;
			if (!active && (lastResult >= 0)) return lastResult;
			if ((meters < 200) || (ms < 10_000)) return -1;
			return (int) Math.round(meters / (ms / 1000d) * 3.6);
		}

		public int meters() {
			return (int) Math.round(meters);
		}

		public int limit() {
			return limit;
		}

		public int lastResult() {
			return lastResult;
		}

		public void cancel() {
			active = false;
		}
	}

	// ---- yorgunluk ----

	/**
	 * Kesintisiz sürüş süresi: 15 km/s üstü sürüş sayılır; 10 dakika duran (5 km/s altı) mola vermiş sayılır.
	 * İlk hatırlatma 2 saatte, sonra her 30 dakikada.
	 */
	public static final class Fatigue {
		public static final long FIRST = 2 * 3600_000L, EVERY = 30 * 60_000L, BREAK = 10 * 60_000L;
		private long drivingMs, stoppedSince = -1, lastMs = -1, nextAt = FIRST;

		/** @return hatırlatma zamanı geldiyse true. */
		public boolean update(long nowMs, int speedKmh) {
			long dt = (lastMs < 0) ? 0 : Math.min(nowMs - lastMs, 60_000); // uzun boşluk (uygulama kapalı) sayılmaz
			lastMs = nowMs;
			if (speedKmh >= 15) {
				drivingMs += dt;
				stoppedSince = -1;
			} else if (speedKmh >= 0 && speedKmh < 5) {
				if (stoppedSince < 0) stoppedSince = nowMs;
				if (nowMs - stoppedSince >= BREAK) {
					drivingMs = 0;
					nextAt = FIRST;
				}
			}
			if (drivingMs >= nextAt) {
				nextAt = drivingMs + EVERY;
				return true;
			}
			return false;
		}

		public long drivingMs() {
			return drivingMs;
		}
	}

	/** "2 saat 15 dakika" */
	public static String duration(long ms) {
		long min = Math.round(ms / 60_000d); // 19 dk 59 sn → 20 dakika
		long h = min / 60, m = min % 60;
		if (h == 0) return m + " dakika";
		return h + " saat" + ((m > 0) ? (" " + m + " dakika") : "");
	}

	// ---- yolculuk özeti ----

	/** Biten yolculuk: başlangıç / bitiş (duvar saati), metre, hareket süresi, en yüksek hız, geçilen radar. */
	public record TripRec(long startWall, long endWall, int meters, long movingMs, int maxKmh, int radars,
												double startLat, double startLon, double endLat, double endLon) {
		/** 20 km ve üstü yolculuklar geçmişte kalıcı tutulur (kullanıcı silene kadar). */
		public static final int LONG_M = 20_000;

		public boolean isLong() {
			return meters >= LONG_M;
		}

		public int avgKmh() {
			return (movingMs > 0) ? (int) Math.round(meters / (movingMs / 1000d) * 3.6) : 0;
		}

		public long durationMs() {
			return endWall - startWall;
		}

		/** "42,3 km · 38 dakika · ort. 67 · en yüksek 112 · 3 radar" */
		public String summary() {
			return String.format(new java.util.Locale("tr"), "%.1f km", meters / 1000f) + " · " + duration(durationMs())
					+ " · ort. " + avgKmh() + " · en yüksek " + maxKmh() + ((radars > 0) ? (" · " + radars + " radar") : "");
		}
	}

	/**
	 * Yolculuk: 15 km/s'yi geçince başlar; 10 dakika durunca (5 km/s altı) ya da araç bağlantısı kesilince biter.
	 * 1 km'den kısa yolculuk kaydedilmez. Bitiş zamanı son hareket anıdır (bekleme sayılmaz).
	 */
	public static final class Trip {
		public static final long STOP = 10 * 60_000L;
		private boolean active;
		private long startWall, lastMs = -1, stopSince = -1, lastMoveWall, movingMs;
		private double meters, lastLat = Double.NaN, lastLon, sLat, sLon, eLat, eLon;
		private int max;
		private final java.util.Set<Long> radars = new java.util.HashSet<>();

		/**
		 * @param passedCam bu konumda 40 m içinden geçilen radar (yoksa null)
		 * @return bu adımda biten yolculuk (kaydedilecekse), yoksa null
		 */
		public TripRec update(long nowMs, long wallMs, double lat, double lon, int speedKmh, Long passedCam) {
			long dt = (lastMs < 0) ? 0 : Math.min(nowMs - lastMs, 60_000);
			lastMs = nowMs;
			TripRec done = null;
			if (!active) {
				if (speedKmh >= 15) {
					active = true;
					startWall = wallMs;
					lastMoveWall = wallMs;
					meters = 0;
					movingMs = 0;
					max = speedKmh;
					stopSince = -1;
					radars.clear();
					sLat = eLat = lat;
					sLon = eLon = lon;
				}
			} else {
				if (!Double.isNaN(lastLat)) {
					double d = YoldaRadarCore.dist(lastLat, lastLon, lat, lon);
					if (d < 500) meters += d; // GPS sıçraması sayılmaz
				}
				if (speedKmh >= 5) {
					movingMs += dt;
					stopSince = -1;
					lastMoveWall = wallMs;
					eLat = lat;
					eLon = lon;
				} else {
					if (stopSince < 0) stopSince = nowMs;
					if (nowMs - stopSince >= STOP) done = finish();
				}
				if (active) {
					max = Math.max(max, speedKmh);
					if (passedCam != null) radars.add(passedCam);
				}
			}
			lastLat = lat;
			lastLon = lon;
			return done;
		}

		/** Araç bağlantısı kesildi / hizmet kapanıyor: yolculuğu bitir. */
		public TripRec end() {
			return active ? finish() : null;
		}

		private TripRec finish() {
			active = false;
			stopSince = -1;
			if (meters < 1000) return null;
			return new TripRec(startWall, lastMoveWall, (int) Math.round(meters), movingMs, max, radars.size(), sLat, sLon, eLat, eLon);
		}

		public boolean isActive() {
			return active;
		}

		public int meters() {
			return (int) Math.round(meters);
		}
	}

	/**
	 * Hangi yolculuklar saklanır (en yeni önce verilir): 20 km ve üstü olanların hepsi (en çok maxLong), kısalardan
	 * en yeni maxShort tanesi.
	 */
	public static boolean[] retain(int[] metersNewestFirst, int maxShort, int maxLong) {
		boolean[] keep = new boolean[metersNewestFirst.length];
		int s = 0, l = 0;
		for (int i = 0; i < metersNewestFirst.length; i++) {
			if (metersNewestFirst[i] >= TripRec.LONG_M) keep[i] = (l++ < maxLong);
			else keep[i] = (s++ < maxShort);
		}
		return keep;
	}

	/** "Kadıköy → Beşiktaş" (aynı ilde ilçeler), "İstanbul → Ankara" (iller arası); bilinmiyorsa "". */
	public static String route(String fromIlce, String fromIl, String toIlce, String toIl) {
		String f, t;
		if (!fromIl.isEmpty() && !toIl.isEmpty() && !fromIl.equals(toIl)) {
			f = fromIl;
			t = toIl;
		} else {
			f = fromIlce.isEmpty() ? fromIl : fromIlce;
			t = toIlce.isEmpty() ? toIl : toIlce;
		}
		if (f.isEmpty() && t.isEmpty()) return "";
		if (f.equals(t)) return f;
		return (f.isEmpty() ? "?" : f) + " → " + (t.isEmpty() ? "?" : t);
	}

	/** Park yeri: araçtan ne kadar uzaktasın ("350 m", "1,2 km"). */
	public static String distText(double meters) {
		return (meters >= 1000) ? String.format(new java.util.Locale("tr"), "%.1f km", meters / 1000) : (Math.round(meters) + " m");
	}

	// ---- okul / hastane bölgesi ----

	/** Yer: kind s = okul, h = hastane, f = akaryakıt, r = dinlenme tesisi. */
	public record Place(long id, double lat, double lon, char kind, String name) {
		public String label() {
			return switch (kind) {
				case 's' -> "Okul";
				case 'h' -> "Hastane";
				case 'f' -> "Akaryakıt";
				case 'r' -> "Dinlenme tesisi";
				default -> "Yer";
			};
		}
	}

	/** Okul uyarısı yalnız okul saatlerinde: hafta içi 07:00 - 18:30. */
	public static boolean schoolHours(int dayOfWeek, int hour, int minute) {
		// java.util.Calendar: Pazar = 1, Cumartesi = 7
		if ((dayOfWeek == 1) || (dayOfWeek == 7)) return false;
		int t = hour * 60 + minute;
		return (t >= 7 * 60) && (t <= 18 * 60 + 30);
	}

	/**
	 * Yaklaşılan bölge: gidiş yönünde 200 m içindeki (ya da 60 m içinde olduğun) okul / hastane; okul saati
	 * dışında okullar sayılmaz. Bulunamazsa null.
	 */
	public static Place zone(double lat, double lon, double heading, List<Place> places, boolean schoolTime) {
		Place best = null;
		double bd = Double.MAX_VALUE;
		for (Place p : places) {
			if ((p.kind() != 's') && (p.kind() != 'h')) continue;
			if ((p.kind() == 's') && !schoolTime) continue;
			double d = YoldaRadarCore.dist(lat, lon, p.lat(), p.lon());
			if (d > 200) continue;
			if ((d > 60) && (heading >= 0) && (YoldaRadarCore.angleDiff(heading, YoldaRadarCore.bearing(lat, lon, p.lat(), p.lon())) > 50))
				continue;
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** Bölgede önerilen en yüksek hız: okul 30, hastane 30 (Türkiye'de okul / hastane geçitleri). */
	public static final int ZONE_SPEED = 30;

	// ---- yol üstü mola yerleri ----

	/** Önündeki yer ve uzaklığı. */
	public record Stop(Place place, int meters) {
	}

	/**
	 * Gidiş yönündeki akaryakıt ve dinlenme tesisleri (en fazla maxM metre, yönden en fazla 35° sapma; uzaklaştıkça
	 * yol kıvrılacağı için sapma payı 5 km'ye kadar 35°, sonra 25°). Yön bilinmiyorsa yakındakiler.
	 */
	public static List<Stop> stopsAhead(double lat, double lon, double heading, List<Place> places, int maxM, int max) {
		List<Stop> l = new ArrayList<>();
		for (Place p : places) {
			if ((p.kind() != 'f') && (p.kind() != 'r')) continue;
			double d = YoldaRadarCore.dist(lat, lon, p.lat(), p.lon());
			if ((d > maxM) || (d < 30)) continue;
			if (heading >= 0) {
				double tol = (d <= 5000) ? 35 : 25;
				if (YoldaRadarCore.angleDiff(heading, YoldaRadarCore.bearing(lat, lon, p.lat(), p.lon())) > tol) continue;
			}
			l.add(new Stop(p, (int) Math.round(d)));
		}
		l.sort((a, b) -> Integer.compare(a.meters(), b.meters()));
		return (l.size() > max) ? new ArrayList<>(l.subList(0, max)) : l;
	}
}
