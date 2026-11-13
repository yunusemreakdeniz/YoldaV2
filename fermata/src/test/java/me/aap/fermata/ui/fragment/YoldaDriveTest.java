package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.ui.fragment.YoldaDriveCore.Place;
import me.aap.fermata.ui.fragment.YoldaDriveCore.Stop;
import me.aap.fermata.ui.fragment.YoldaRadarCore.Cam;

/** Sürüş yardımcıları: ortalama hız koridoru, mola hatırlatıcısı, okul / hastane bölgesi, öndeki mola yerleri. */
public class YoldaDriveTest {
	private static final double LAT0 = 40.0, LON0 = 30.0;

	private static double latAt(double m) {
		return LAT0 + m / 111_195d;
	}

	private static double lonAt(double m) {
		return LON0 + m / (Math.cos(Math.toRadians(LAT0)) * 111_195d);
	}

	// ---- ortalama hız ----

	/** 10 km'lik koridor 100 km/s ile: başlar, ortalama ≈ 100, çıkışta biter; çıkış noktası yeni koridor açmaz. */
	@Test
	public void ortalamaHizKoridoru() {
		Cam in = new Cam(1, latAt(500), LON0, 110, -1, 'a');
		Cam out = new Cam(2, latAt(10_500), LON0, 110, -1, 'a');
		YoldaDriveCore.AvgSpeed av = new YoldaDriveCore.AvgSpeed();
		double mps = 100 / 3.6;
		boolean started = false, ended = false;
		int endedAt = -1;
		for (int s = 0; s <= 500; s++) { // saniye
			double m = s * mps;
			double la = latAt(m);
			Cam passed = null;
			for (Cam c : List.of(in, out)) if (YoldaRadarCore.dist(la, LON0, c.lat(), c.lon()) <= 40) passed = c;
			boolean e = av.update(la, LON0, s * 1000L, passed);
			if (av.isActive()) started = true;
			if (e) {
				ended = true;
				endedAt = s;
			}
			if ((s == 200) && av.isActive()) assertEquals(100, av.average(), 2);
		}
		assertTrue(started);
		assertTrue(ended);
		assertTrue("çıkışta bitmeli: " + endedAt, (endedAt > 370) && (endedAt < 382));
		assertFalse("çıkış noktası yeni koridor açmamalı", av.isActive());
		assertEquals(100, av.lastResult(), 2);
		assertEquals(110, av.limit());
	}

	@Test
	public void koridorBaslangictaOrtalamaYok() {
		YoldaDriveCore.AvgSpeed av = new YoldaDriveCore.AvgSpeed();
		Cam in = new Cam(1, LAT0, LON0, 0, -1, 'a');
		av.update(LAT0, LON0, 0, in);
		assertTrue(av.isActive());
		assertEquals(-1, av.average());
		// normal radar koridor başlatmaz
		YoldaDriveCore.AvgSpeed b = new YoldaDriveCore.AvgSpeed();
		b.update(LAT0, LON0, 0, new Cam(3, LAT0, LON0, 50, -1, 'r'));
		assertFalse(b.isActive());
	}

	@Test
	public void koridor60kmdeBiter() {
		YoldaDriveCore.AvgSpeed av = new YoldaDriveCore.AvgSpeed();
		av.update(LAT0, LON0, 0, new Cam(1, LAT0, LON0, 0, -1, 'a'));
		boolean ended = false;
		for (int s = 1; s <= 3000 && !ended; s++) ended = av.update(latAt(s * 30.0), LON0, s * 1000L, null);
		assertTrue(ended);
		assertFalse(av.isActive());
	}

	// ---- mola hatırlatıcısı ----

	@Test
	public void molaHatirlatici() {
		YoldaDriveCore.Fatigue f = new YoldaDriveCore.Fatigue();
		List<Long> at = new ArrayList<>();
		long t = 0;
		for (; t <= 3 * 3600_000L; t += 1000) if (f.update(t, 90)) at.add(t);
		assertEquals(List.of(2 * 3600_000L, 2 * 3600_000L + 30 * 60_000L, 3 * 3600_000L), at);
	}

	@Test
	public void molaVerinceSifirlanir() {
		YoldaDriveCore.Fatigue f = new YoldaDriveCore.Fatigue();
		long t = 0;
		for (; t < 90 * 60_000L; t += 1000) assertFalse(f.update(t, 100));
		for (int i = 0; i < 11 * 60; i++, t += 1000) f.update(t, 0); // 11 dk mola
		assertEquals(0, f.drivingMs());
		boolean warned = false;
		for (long end = t + 90 * 60_000L; t < end; t += 1000) warned |= f.update(t, 100);
		assertFalse("moladan sonra 1,5 saatte uyarmamalı", warned);
		// kırmızı ışıkta 2 dk durmak mola sayılmaz
		YoldaDriveCore.Fatigue g = new YoldaDriveCore.Fatigue();
		long u = 0;
		for (; u < 100 * 60_000L; u += 1000) g.update(u, 100);
		for (int i = 0; i < 120; i++, u += 1000) g.update(u, 0);
		assertTrue(g.drivingMs() >= 100 * 60_000L - 1000);
	}

	@Test
	public void uygulamaKapaliykenGecenSureSayilmaz() {
		YoldaDriveCore.Fatigue f = new YoldaDriveCore.Fatigue();
		f.update(0, 100);
		f.update(5 * 3600_000L, 100); // 5 saat sonra ilk konum
		assertTrue(f.drivingMs() <= 60_000);
	}

	@Test
	public void sure() {
		assertEquals("45 dakika", YoldaDriveCore.duration(45 * 60_000L));
		assertEquals("2 saat", YoldaDriveCore.duration(2 * 3600_000L));
		assertEquals("2 saat 15 dakika", YoldaDriveCore.duration(135 * 60_000L));
	}

	// ---- okul / hastane ----

	@Test
	public void okulSaatleri() {
		assertTrue(YoldaDriveCore.schoolHours(2, 8, 0));   // pazartesi
		assertTrue(YoldaDriveCore.schoolHours(6, 18, 30)); // cuma
		assertFalse(YoldaDriveCore.schoolHours(2, 6, 59));
		assertFalse(YoldaDriveCore.schoolHours(2, 19, 0));
		assertFalse(YoldaDriveCore.schoolHours(7, 10, 0)); // cumartesi
		assertFalse(YoldaDriveCore.schoolHours(1, 10, 0)); // pazar
	}

	@Test
	public void bolge() {
		Place school = new Place(1, latAt(150), LON0, 's', "İlkokul");
		Place hosp = new Place(2, latAt(-150), LON0, 'h', "Devlet Hastanesi");
		Place far = new Place(3, latAt(900), LON0, 's', "Uzak");
		List<Place> l = List.of(school, hosp, far);
		assertEquals(1, YoldaDriveCore.zone(LAT0, LON0, 0, l, true).id());
		assertNull("okul saati dışında okul yok", YoldaDriveCore.zone(LAT0, LON0, 0, l, false));
		assertEquals(2, YoldaDriveCore.zone(LAT0, LON0, 180, l, false).id());
		// arkada kalan okul (60 m'den uzak) sayılmaz
		assertNull(YoldaDriveCore.zone(latAt(300), LON0, 0, List.of(school), true));
		// yanından geçerken (60 m içinde) yönden bağımsız
		assertNotNull(YoldaDriveCore.zone(latAt(150), lonAt(40), 0, List.of(school), true));
	}

	// ---- mola yerleri ----

	@Test
	public void ondekiMolaYerleri() {
		Place f1 = new Place(1, latAt(2000), LON0, 'f', "Opet");
		Place f2 = new Place(2, latAt(8000), lonAt(500), 'f', "Shell");
		Place behind = new Place(3, latAt(-3000), LON0, 'f', "Arkada");
		Place side = new Place(4, latAt(1000), lonAt(3000), 'f', "Yanda");
		Place rest = new Place(5, latAt(15_000), LON0, 'r', "Tesis");
		Place school = new Place(6, latAt(500), LON0, 's', "Okul");
		List<Stop> s = YoldaDriveCore.stopsAhead(LAT0, LON0, 0, List.of(f1, f2, behind, side, rest, school), 30_000, 8);
		List<Long> ids = new ArrayList<>();
		for (Stop x : s) ids.add(x.place().id());
		assertEquals(List.of(1L, 2L, 5L), ids);
		assertEquals(2000, s.get(0).meters(), 5);
		assertEquals(1, YoldaDriveCore.stopsAhead(LAT0, LON0, 0, List.of(f1, f2, rest), 30_000, 1).size());
	}
}
