package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.ui.fragment.YoldaRadarCore.Ahead;
import me.aap.fermata.ui.fragment.YoldaRadarCore.Cam;
import me.aap.fermata.ui.fragment.YoldaRadarCore.Road;

/** Radar uyarısı: mesafe, yön, önde mi, aşamalar, hız sınırı, yol eşleşmesi. Gerçek bir yolculuk benzetimiyle. */
public class YoldaRadarTest {
	// Kuzeye giden düz bir yol: her 0.001° enlem ≈ 110.5 m
	private static final double LAT0 = 40.0, LON0 = 30.0;

	private static Cam cam(long id, double dLatM, double dLonM, int limit, int dir) {
		return new Cam(id, LAT0 + dLatM / 111_195d, LON0 + dLonM / (Math.cos(Math.toRadians(LAT0)) * 111_195d), limit, dir, 'r');
	}

	private static double latAt(double m) {
		return LAT0 + m / 111_195d;
	}

	@Test
	public void mesafeVeYon() {
		double d = YoldaRadarCore.dist(41.0082, 28.9784, 39.9334, 32.8597); // İstanbul – Ankara kuş uçuşu ≈ 350 km
		assertTrue(d > 345_000 && d < 355_000);
		assertEquals(0, YoldaRadarCore.bearing(40, 30, 41, 30), 0.5);
		assertEquals(90, YoldaRadarCore.bearing(40, 30, 40, 31), 0.5);
		assertEquals(20, YoldaRadarCore.angleDiff(350, 10), 1e-9);
		assertEquals(180, YoldaRadarCore.angleDiff(0, 180), 1e-9);
	}

	@Test
	public void hizSiniriEtiketi() {
		assertEquals(50, YoldaRadarCore.parseMaxspeed("50"));
		assertEquals(70, YoldaRadarCore.parseMaxspeed("70kmh"));
		assertEquals(80, YoldaRadarCore.parseMaxspeed("50 mph"));
		assertEquals(50, YoldaRadarCore.parseMaxspeed("TR:urban"));
		assertEquals(90, YoldaRadarCore.parseMaxspeed("TR:rural"));
		assertEquals(120, YoldaRadarCore.parseMaxspeed("TR:motorway"));
		assertEquals(0, YoldaRadarCore.parseMaxspeed("signals"));
		assertEquals(0, YoldaRadarCore.parseMaxspeed(""));
		assertEquals(0, YoldaRadarCore.parseMaxspeed(null));
		assertEquals(120, YoldaRadarCore.defaultLimit("motorway", true));
		assertEquals(110, YoldaRadarCore.defaultLimit("trunk", true));
		assertEquals(0, YoldaRadarCore.defaultLimit("trunk", false));
		assertEquals(0, YoldaRadarCore.defaultLimit("primary", false)); // şehir içi mi dışı mı belli değil
		assertEquals(50, YoldaRadarCore.defaultLimit("residential", false));
	}

	@Test
	public void yonEtiketi() {
		assertEquals(90, YoldaRadarCore.parseDir("E"));
		assertEquals(45, YoldaRadarCore.parseDir("NE"));
		assertEquals(300, YoldaRadarCore.parseDir("-60"));
		assertEquals(105, YoldaRadarCore.parseDir("105;285"));
		assertEquals(-1, YoldaRadarCore.parseDir(""));
		assertEquals(-1, YoldaRadarCore.parseDir("forward"));
	}

	@Test
	public void csvGidipGelir() {
		List<Cam> l = List.of(new Cam(1, 41.01769, 28.97252, 50, 95, 'r'), new Cam(-5, 40.5, 30.25, 0, -1, 'u'));
		List<Cam> back = YoldaRadarCore.parseCsv(YoldaRadarCore.toCsv(l));
		assertEquals(l, back);
		assertEquals(1, YoldaRadarCore.parseCsv("bozuk\n1,2,3\n7,40.1,30.1,,,a\n").size());
	}

	@Test
	public void uygulamaylaGelenListe() throws Exception {
		Path p = Paths.get("src/main/assets/yolda_radars.csv");
		if (!Files.exists(p)) p = Paths.get("fermata/src/main/assets/yolda_radars.csv");
		List<Cam> l = YoldaRadarCore.parseCsv(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
		assertTrue("Türkiye listesi: " + l.size(), l.size() > 500);
		for (Cam c : l) {
			assertTrue("Türkiye sınırları: " + c, (c.lat() > 35.5) && (c.lat() < 42.5) && (c.lon() > 25.5) && (c.lon() < 45));
			assertTrue(c.limit() == 0 || (c.limit() >= 20 && c.limit() <= 140));
		}
	}

	@Test
	public void onumdekiRadar() {
		Cam ahead = cam(1, 800, 0, 70, -1);      // 800 m kuzeyde
		Cam behind = cam(2, -300, 0, 50, -1);    // arkada
		Cam side = cam(3, 200, 600, 50, -1);     // yan yolda (açı büyük)
		Cam wrongDir = cam(4, 400, 0, 50, 180);  // güneye giden trafiği ölçüyor
		List<Cam> cams = List.of(ahead, behind, side, wrongDir);
		Ahead a = YoldaRadarCore.ahead(LAT0, LON0, 0, cams, 1500);
		assertNotNull(a);
		assertEquals(1, a.cam().id());
		assertEquals(800, a.meters(), 3);
		// Güneye giderken: arkadaki (artık önde) ve güneyi ölçen
		Ahead s = YoldaRadarCore.ahead(LAT0, LON0, 180, cams, 1500);
		assertNotNull(s);
		assertEquals(2, s.cam().id());
		// Duruyorken yalnız çok yakın olan
		assertNull(YoldaRadarCore.ahead(LAT0, LON0, -1, cams, 1500));
		assertEquals(2, YoldaRadarCore.ahead(latAt(-200), LON0, -1, cams, 1500).cam().id());
		// Menzil dışı
		assertNull(YoldaRadarCore.ahead(LAT0, LON0, 0, List.of(cam(9, 3000, 0, 0, -1)), 1500));
	}

	/** 120 km/s ile radara yaklaşıp geçen yolculuk: 1000, 500, 150 m bir kez; geçince susar; sonrakinde yeniden. */
	@Test
	public void uyariAsamalari() {
		List<Cam> cams = List.of(cam(1, 2000, 0, 90, -1), cam(2, 4000, 0, 0, -1));
		YoldaRadarCore.Alerter al = new YoldaRadarCore.Alerter();
		List<Integer> said = new ArrayList<>();
		List<Long> which = new ArrayList<>();
		for (int m = 0; m <= 4200; m += 33) { // ~1 sn'de 33 m
			Ahead a = YoldaRadarCore.ahead(latAt(m), LON0, 0, cams, 1500);
			int st = al.update(a, 120);
			if (st > 0) {
				said.add(st);
				which.add(a.cam().id());
			}
		}
		assertEquals(List.of(1000, 500, 150, 1000, 500, 150), said);
		assertEquals(List.of(1L, 1L, 1L, 2L, 2L, 2L), which);
	}

	@Test
	public void yavasGiderken1000Soylenmez() {
		List<Cam> cams = List.of(cam(1, 1200, 0, 50, -1));
		YoldaRadarCore.Alerter al = new YoldaRadarCore.Alerter();
		List<Integer> said = new ArrayList<>();
		for (int m = 0; m <= 1300; m += 10) {
			int st = al.update(YoldaRadarCore.ahead(latAt(m), LON0, 0, cams, 1500), 45);
			if (st > 0) said.add(st);
		}
		assertEquals(List.of(500, 150), said);
	}

	@Test
	public void uyariCumlesi() {
		Ahead a = new Ahead(cam(1, 480, 0, 70, -1), 480);
		assertEquals("500 metre sonra radar, hız sınırı 70", YoldaRadarCore.alertText(a, 500, 0));
		assertEquals("1 kilometre sonra radar, hız sınırı 50", YoldaRadarCore.alertText(new Ahead(cam(1, 990, 0, 0, -1), 990), 1000, 50));
		assertEquals("Dikkat, radar", YoldaRadarCore.alertText(new Ahead(cam(1, 140, 0, 0, -1), 140), 150, 0));
		Ahead avg = new Ahead(new Cam(5, 40, 30, 110, -1, 'a'), 500);
		assertEquals("500 metre sonra ortalama hız denetimi, hız sınırı 110", YoldaRadarCore.alertText(avg, 500, 0));
	}

	@Test
	public void bulundugumYol() {
		// Kuzey-güney ana yol (sınır 82) ve onu kesen doğu-batı sokak (50)
		Road main = new Road(1, "trunk", 82, false, true, new double[]{latAt(-500), LON0, latAt(500), LON0});
		double dLon = 300 / (Math.cos(Math.toRadians(LAT0)) * 111_195d);
		Road street = new Road(2, "residential", 50, true, false, new double[]{LAT0, LON0 - dLon, LAT0, LON0 + dLon});
		List<Road> roads = List.of(main, street);
		assertEquals(1, YoldaRadarCore.matchRoad(latAt(200), LON0, 0, roads).id());
		// Kavşakta: kuzeye gidiyorsa ana yol, doğuya gidiyorsa sokak
		assertEquals(1, YoldaRadarCore.matchRoad(LAT0, LON0, 2, roads).id());
		assertEquals(2, YoldaRadarCore.matchRoad(LAT0, LON0, 90, roads).id());
		// 40 m uzakta: hiçbiri
		assertNull(YoldaRadarCore.matchRoad(latAt(200), LON0 + 40 / (Math.cos(Math.toRadians(LAT0)) * 111_195d), 0,
				List.of(main)));
	}

	@Test
	public void hizAsimi() {
		assertFalse(YoldaRadarCore.overSpeed(55, 50));
		assertTrue(YoldaRadarCore.overSpeed(56, 50));
		assertFalse(YoldaRadarCore.overSpeed(132, 120));
		assertTrue(YoldaRadarCore.overSpeed(133, 120));
		assertFalse("sınır bilinmiyorsa uyarı yok", YoldaRadarCore.overSpeed(200, 0));
	}

	@Test
	public void yakindakiler() {
		List<Cam> cams = List.of(cam(1, 5000, 0, 0, -1), cam(2, 100, 0, 0, -1), cam(3, 40_000, 0, 0, -1));
		List<Ahead> n = YoldaRadarCore.nearby(LAT0, LON0, cams, 30_000, 10);
		assertEquals(2, n.size());
		assertEquals(2, n.get(0).cam().id());
	}
}
