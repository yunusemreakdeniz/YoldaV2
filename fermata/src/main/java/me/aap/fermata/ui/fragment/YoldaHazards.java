package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Yoldaki tehlikeler (OpenStreetMap): hemzemin geçit, kasis, okul yanı yaya geçidi, tünel, gişe, köprü ve
 * yolun geometrisinden bulunan keskin viraj. Saf mantık (Android'siz): testlerle denetlenir. Her tehlike bir kez
 * uyarılır (30 dk sonra yeniden uyarılabilir); yalnız gidiş yönünde önde olanlar sayılır.
 */
public final class YoldaHazards {
	/** l hemzemin geçit, b kasis, p okul yanı yaya geçidi, c keskin viraj, t tünel, g gişe, i köprü (buzlanma). */
	public static final String KINDS = "lbpctgi";

	public record Hazard(long id, double lat, double lon, char kind) {
	}

	private YoldaHazards() {
	}

	/** Ayar anahtarı (Sürüş ekranındaki aç / kapat). */
	public static String feature(char kind) {
		return "hz_" + kind;
	}

	/** Sürüş ekranında görünen ad. */
	public static String label(char kind) {
		return switch (kind) {
			case 'l' -> "Hemzemin geçit";
			case 'b' -> "Kasis / hız tümseği";
			case 'p' -> "Okul yanı yaya geçidi";
			case 'c' -> "Keskin viraj";
			case 't' -> "Tünel";
			case 'g' -> "Gişe";
			case 'i' -> "Köprü buzlanması";
			default -> "Tehlike";
		};
	}

	/** Sürüş ekranındaki açıklama. */
	public static String hint(char kind) {
		return switch (kind) {
			case 'l' -> "Tren yolu geçidine yaklaşınca";
			case 'b' -> "Kasise 150 m kala";
			case 'p' -> "Okulun 250 m yakınındaki yaya geçitleri";
			case 'c' -> "Önünde sert dönüş varsa ve hızlıysan";
			case 't' -> "Tünel girişine yaklaşınca (farlar)";
			case 'g' -> "Otoyol gişesine yaklaşınca";
			case 'i' -> "Hava 3°C ve altındayken köprülerde";
			default -> "";
		};
	}

	/** Uyarı mesafesi (m): hızlıyken daha erken. */
	static int warnDist(char kind, int speedKmh) {
		int base = switch (kind) {
			case 'b', 'p' -> 150;
			case 'l', 'g' -> 400;
			case 'c', 't', 'i' -> 300;
			default -> 200;
		};
		if (speedKmh >= 90) return base * 2;
		if (speedKmh >= 60) return (base * 3) / 2;
		return base;
	}

	/** Söylenecek cümle. */
	public static String text(char kind, int meters) {
		String d = (meters >= 150) ? (Math.max(50, Math.round(meters / 50f) * 50) + " metre sonra ") : "Dikkat, ";
		return switch (kind) {
			case 'l' -> d + "hemzemin geçit, yavaşla";
			case 'b' -> d + "kasis";
			case 'p' -> d + "okul yanında yaya geçidi";
			case 'c' -> d + "keskin viraj, yavaşla";
			case 't' -> d + "tünel, farlarını aç";
			case 'g' -> d + "gişe";
			case 'i' -> d + "köprü, buzlanma olabilir";
			default -> d + "tehlike";
		};
	}

	/** Uyarılan tehlikeler (id → elapsedRealtime). */
	public static final class Memory {
		private final Map<Long, Long> said = new HashMap<>();

		boolean fresh(long id, long now) {
			Long at = said.get(id);
			return (at == null) || (now - at > 30 * 60_000L);
		}

		void mark(long id, long now) {
			said.put(id, now);
			if (said.size() > 500) said.keySet().removeIf(k -> now - said.get(k) > 30 * 60_000L);
		}
	}

	/**
	 * Uyarılacak ilk tehlike ya da null. {@code enabled} izinli türler; {@code cold} köprü buzlanması uyarılsın mı;
	 * {@code heading} &lt; 0 ise yön bilinmiyor (uyarı yok: arkadakiler için yanlış uyarı olmasın).
	 */
	@Nullable
	public static Hazard next(double lat, double lon, double heading, int speedKmh, List<Hazard> list, String enabled,
														boolean cold, Memory mem, long now) {
		if ((heading < 0) || (speedKmh < 15)) return null;
		Hazard best = null;
		double bestD = Double.MAX_VALUE;
		for (Hazard h : list) {
			if (enabled.indexOf(h.kind()) < 0) continue;
			if ((h.kind() == 'i') && !cold) continue;
			double d = YoldaRadarCore.dist(lat, lon, h.lat(), h.lon());
			if ((d > warnDist(h.kind(), speedKmh)) || (d < 15) || (d >= bestD)) continue;
			if (YoldaRadarCore.angleDiff(heading, YoldaRadarCore.bearing(lat, lon, h.lat(), h.lon())) > 35) continue;
			if (!mem.fresh(h.id(), now)) continue;
			best = h;
			bestD = d;
		}
		if (best != null) mem.mark(best.id(), now);
		return best;
	}

	/** Okul yanındaki (250 m) yaya geçitleri. */
	public static List<Hazard> nearSchools(List<Hazard> crossings, List<YoldaDriveCore.Place> places) {
		List<Hazard> out = new ArrayList<>();
		for (Hazard h : crossings) {
			for (YoldaDriveCore.Place p : places) {
				if ((p.kind() == 's') && (YoldaRadarCore.dist(h.lat(), h.lon(), p.lat(), p.lon()) <= 250)) {
					out.add(h);
					break;
				}
			}
		}
		return out;
	}

	/**
	 * Yolun geometrisinden keskin virajlar: art arda parçalar arasında 100 m içinde 70°'den fazla dönüş.
	 * Virajın tepe noktası tehlike olarak döner (id: yol id'si ve nokta sırası).
	 */
	public static List<Hazard> curves(long roadId, double[] pts) {
		List<Hazard> out = new ArrayList<>();
		int n = pts.length / 2;
		int lastAt = -100;
		for (int i = 1; i + 1 < n; i++) {
			double inB = YoldaRadarCore.bearing(pts[2 * (i - 1)], pts[2 * (i - 1) + 1], pts[2 * i], pts[2 * i + 1]);
			// çıkış doğrultusu: 100 m içindeki son nokta (yumuşak, çok parçalı virajlar da sayılsın)
			int j = i + 1;
			double run = 0;
			while (j + 1 < n) {
				double seg = YoldaRadarCore.dist(pts[2 * (j - 1)], pts[2 * (j - 1) + 1], pts[2 * j], pts[2 * j + 1]);
				if (run + seg > 100) break;
				run += seg;
				j++;
			}
			double outB = YoldaRadarCore.bearing(pts[2 * (j - 1)], pts[2 * (j - 1) + 1], pts[2 * j], pts[2 * j + 1]);
			if ((YoldaRadarCore.angleDiff(inB, outB) > 70) && (i - lastAt > 3)) {
				out.add(new Hazard(-(roadId * 1000 + i), pts[2 * i], pts[2 * i + 1], 'c'));
				lastAt = i;
			}
		}
		return out;
	}
}
