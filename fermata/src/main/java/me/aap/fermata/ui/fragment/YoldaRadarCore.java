package me.aap.fermata.ui.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Radar uyarısının cihaz gerektirmeyen hesabı (birim testleriyle denetlenir): mesafe, yön, "radar önümde mi",
 * uyarı aşamaları, hız sınırı etiketinin çözümü, bulunduğun yolun eşleşmesi, hız aşımı.
 */
public final class YoldaRadarCore {
	private static final double R = 6_371_000d;

	private YoldaRadarCore() {
	}

	/** Radar: kind r = hız radarı, a = ortalama hız koridoru, k = kırmızı ışık, u = kullanıcının eklediği. */
	public record Cam(long id, double lat, double lon, int limit, int dir, char kind) {
		public String label() {
			return switch (kind) {
				case 'a' -> "Ortalama hız";
				case 'k' -> "Kırmızı ışık";
				case 'u' -> "Radar (eklediğin)";
				default -> "Radar";
			};
		}
	}

	/** Bulunduğun yol: hız sınırı (0 = bilinmiyor), tahmin mi (etiket yok, yol türünden), noktalar lat,lon,lat,lon… */
	public record Road(long id, String highway, int limit, boolean estimated, boolean oneway, double[] pts) {
	}

	/** Öndeki radar ve uzaklığı (metre). */
	public record Ahead(Cam cam, int meters) {
	}

	// ---- veri ----

	/** "id,lat,lon,limit,direction,kind" satırları (limit / direction boş olabilir). */
	public static List<Cam> parseCsv(String csv) {
		List<Cam> l = new ArrayList<>();
		if (csv == null) return l;
		for (String line : csv.split("\n")) {
			String[] f = line.trim().split(",", -1);
			if (f.length < 6) continue;
			try {
				l.add(new Cam(Long.parseLong(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2]),
						parseMaxspeed(f[3]), parseDir(f[4]), f[5].isEmpty() ? 'r' : f[5].charAt(0)));
			} catch (RuntimeException ignore) {
			}
		}
		return l;
	}

	public static String toCsv(List<Cam> cams) {
		StringBuilder b = new StringBuilder();
		for (Cam c : cams) {
			b.append(c.id()).append(',').append(String.format(Locale.ROOT, "%.5f,%.5f", c.lat(), c.lon())).append(',')
					.append((c.limit() > 0) ? String.valueOf(c.limit()) : "").append(',')
					.append((c.dir() >= 0) ? String.valueOf(c.dir()) : "").append(',').append(c.kind()).append('\n');
		}
		return b.toString();
	}

	/** OSM direction: "120", "-60", "NE", "105;285" (ilk değer) → 0-359; yoksa -1. */
	static int parseDir(String s) {
		if ((s == null) || s.trim().isEmpty()) return -1;
		String t = s.trim().split("[;,]")[0].trim().toUpperCase(Locale.ROOT);
		String[] names = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
		for (int i = 0; i < names.length; i++) if (names[i].equals(t)) return Math.round(i * 22.5f) % 360;
		try {
			return Math.floorMod(Math.round(Float.parseFloat(t)), 360);
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	/** OSM maxspeed: "50", "70kmh", "50 mph", "TR:urban", "TR:motorway" → km/s; bilinmiyorsa 0. */
	public static int parseMaxspeed(String s) {
		if ((s == null) || s.trim().isEmpty()) return 0;
		String t = s.trim().toLowerCase(Locale.ROOT).split(";")[0].trim();
		switch (t) {
			case "tr:urban", "tr:living_street" -> {
				return t.endsWith("street") ? 20 : 50;
			}
			case "tr:rural" -> {
				return 90;
			}
			case "tr:trunk", "tr:expressway" -> {
				return 110;
			}
			case "tr:motorway" -> {
				return 120;
			}
			case "walk" -> {
				return 10;
			}
			default -> {
			}
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})\\s*(km/h|kmh|kph|mph)?$").matcher(t);
		if (!m.matches()) return 0;
		int v = Integer.parseInt(m.group(1));
		if ("mph".equals(m.group(2))) v = Math.round(v * 1.609f);
		return ((v >= 5) && (v <= 160)) ? v : 0;
	}

	/**
	 * Etiketsiz yolda Türkiye'deki genel sınır (otomobil): yalnız yol türünden kesin olanlar; şehir içi / dışı
	 * belli olmayan yollarda (primary, secondary) 0 = gösterme.
	 */
	public static int defaultLimit(String highway, boolean oneway) {
		if (highway == null) return 0;
		return switch (highway) {
			case "motorway" -> 120;
			case "trunk" -> oneway ? 110 : 0; // bölünmüş yol
			case "residential" -> 50;
			case "living_street" -> 20;
			default -> 0;
		};
	}

	// ---- geometri ----

	public static double dist(double lat1, double lon1, double lat2, double lon2) {
		double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
		double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
		return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	/** Kuzeyden saat yönünde 0-360. */
	public static double bearing(double lat1, double lon1, double lat2, double lon2) {
		double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
		double y = Math.sin(dl) * Math.cos(p2);
		double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
		return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
	}

	/** İki yön arasındaki fark, 0-180. */
	public static double angleDiff(double a, double b) {
		double d = Math.abs(((a - b) % 360 + 360) % 360);
		return (d > 180) ? (360 - d) : d;
	}

	/** Noktanın AB doğru parçasına uzaklığı (metre; kısa mesafede düzlem yaklaşımı). */
	static double distToSeg(double lat, double lon, double aLat, double aLon, double bLat, double bLon) {
		double k = Math.cos(Math.toRadians(lat)) * 111_320d;
		double ax = (aLon - lon) * k, ay = (aLat - lat) * 110_540d;
		double bx = (bLon - lon) * k, by = (bLat - lat) * 110_540d;
		double dx = bx - ax, dy = by - ay;
		double l2 = dx * dx + dy * dy;
		double t = (l2 == 0) ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2));
		double px = ax + t * dx, py = ay + t * dy;
		return Math.sqrt(px * px + py * py);
	}

	// ---- radar önümde mi ----

	/** Önde sayılmak için radarın gidiş yönünden en fazla bu kadar sapması (derece). */
	static final double AHEAD_DEG = 30;
	/** Radar yönü belliyse gidiş yönüyle en fazla bu kadar farklı olabilir. */
	static final double DIR_DEG = 75;

	/**
	 * Gidiş yönündeki en yakın radar (en fazla maxM metre). heading &lt; 0 (duruyor / yön bilinmiyor) ise yalnız
	 * çok yakındaki (250 m) en yakın radar. Yönü belli radar ters yöndeki trafiği ölçüyorsa sayılmaz.
	 */
	public static Ahead ahead(double lat, double lon, double heading, List<Cam> cams, int maxM) {
		Cam best = null;
		double bestD = Double.MAX_VALUE;
		double dLat = maxM / 110_540d, dLon = maxM / (Math.cos(Math.toRadians(lat)) * 111_320d);
		for (Cam c : cams) {
			if ((Math.abs(c.lat() - lat) > dLat) || (Math.abs(c.lon() - lon) > dLon)) continue;
			double d = dist(lat, lon, c.lat(), c.lon());
			if (d > maxM) continue;
			if (heading < 0) {
				if (d > 250) continue;
			} else {
				// Çok yakındayken (radarın üstü) açı anlamsızlaşır: 40 m içinde yönden bağımsız
				if ((d > 40) && (angleDiff(heading, bearing(lat, lon, c.lat(), c.lon())) > AHEAD_DEG)) continue;
				if ((c.dir() >= 0) && (angleDiff(heading, c.dir()) > DIR_DEG)) continue;
			}
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return (best == null) ? null : new Ahead(best, (int) Math.round(bestD));
	}

	/** Gidiş yönünden bağımsız, yakındaki radarlar (en yakın önce). */
	public static List<Ahead> nearby(double lat, double lon, List<Cam> cams, int maxM, int max) {
		List<Ahead> l = new ArrayList<>();
		for (Cam c : cams) {
			double d = dist(lat, lon, c.lat(), c.lon());
			if (d <= maxM) l.add(new Ahead(c, (int) Math.round(d)));
		}
		l.sort((x, y) -> Integer.compare(x.meters(), y.meters()));
		return (l.size() > max) ? new ArrayList<>(l.subList(0, max)) : l;
	}

	// ---- uyarı aşamaları ----

	/**
	 * Her radar için aşamalar bir kez söylenir: hızlıyken 1000 m, sonra 500 m ve 150 m. Radar geçilince
	 * (uzaklık artmaya başlayıp 60 m'yi aşınca) ya da önde başka radar olunca baştan başlar.
	 */
	public static final class Alerter {
		private long camId = Long.MIN_VALUE;
		private int stage; // 0 = hiç, 1 = 1000, 2 = 500, 3 = 150
		private int minSeen = Integer.MAX_VALUE;

		/** @return söylenecek aşama mesafesi (1000 / 500 / 150), yoksa 0. */
		public int update(Ahead a, int speedKmh) {
			if (a == null) return 0;
			if (a.cam().id() != camId) {
				camId = a.cam().id();
				stage = 0;
				minSeen = Integer.MAX_VALUE;
			}
			if (a.meters() < minSeen) minSeen = a.meters();
			else if ((a.meters() > minSeen + 60) && (minSeen < 100)) { // geçildi
				stage = 3;
				return 0;
			}
			int want;
			if (a.meters() <= 150) want = 3;
			else if (a.meters() <= 500) want = 2;
			else if ((a.meters() <= 1000) && (speedKmh >= 70)) want = 1;
			else want = 0;
			if (want > stage) {
				// Bir aşamayı kaçırdıysa (ör. 1000'i görmeden 400'de) yalnız en son aşama söylenir
				stage = want;
				return switch (want) {
					case 1 -> 1000;
					case 2 -> 500;
					default -> 150;
				};
			}
			return 0;
		}

		public void reset() {
			camId = Long.MIN_VALUE;
			stage = 0;
			minSeen = Integer.MAX_VALUE;
		}
	}

	/** Uyarı cümlesi: "500 metre sonra radar, hız sınırı 70". */
	public static String alertText(Ahead a, int stageM, int roadLimit) {
		int lim = (a.cam().limit() > 0) ? a.cam().limit() : roadLimit;
		String what = switch (a.cam().kind()) {
			case 'a' -> "ortalama hız denetimi";
			case 'k' -> "kırmızı ışık kamerası";
			default -> "radar";
		};
		String where = (stageM >= 1000) ? "1 kilometre sonra " : (stageM >= 500) ? (round50(a.meters()) + " metre sonra ")
				: "Dikkat, ";
		return where + what + ((lim > 0) ? (", hız sınırı " + lim) : "");
	}

	private static int round50(int m) {
		return Math.max(50, Math.round(m / 50f) * 50);
	}

	// ---- bulunduğun yol ----

	/**
	 * En yakın yol (25 m içinde). Gidiş yönü belliyse yol parçasının doğrultusu yöne uyanlar öne geçer
	 * (köprü altındaki yan yol yerine üstteki ana yol).
	 */
	public static Road matchRoad(double lat, double lon, double heading, List<Road> roads) {
		Road best = null;
		double bestScore = Double.MAX_VALUE;
		for (Road r : roads) {
			double[] p = r.pts();
			for (int i = 0; i + 3 < p.length; i += 2) {
				double d = distToSeg(lat, lon, p[i], p[i + 1], p[i + 2], p[i + 3]);
				if (d > 25) continue;
				double score = d;
				if (heading >= 0) {
					double b = bearing(p[i], p[i + 1], p[i + 2], p[i + 3]);
					double ad = r.oneway() ? angleDiff(heading, b) : Math.min(angleDiff(heading, b), angleDiff(heading, b + 180));
					if (ad > 60) score += 30; // doğrultu uymuyor: kesişen ya da yan yol
					else score += ad / 6;
				}
				if (score < bestScore) {
					bestScore = score;
					best = r;
				}
			}
		}
		return best;
	}

	/** Türkiye'de %10 hoşgörü: sınırın %10'undan (en az 5 km/s) fazlası aşım sayılır. */
	public static boolean overSpeed(int speedKmh, int limit) {
		return (limit > 0) && (speedKmh > limit + Math.max(5, Math.round(limit * 0.1f)));
	}
}
