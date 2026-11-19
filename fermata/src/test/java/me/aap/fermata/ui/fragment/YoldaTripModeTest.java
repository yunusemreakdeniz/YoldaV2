package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Yolculuk kaydı: Android Auto'da kendiliğinden, telefonda elle başlat / bitir. */
public class YoldaTripModeTest {
	private static final double LA = 39.9, LO = 32.85;

	@Test
	public void telefondaKendiligindenBaslamaz() {
		YoldaDriveCore.Trip t = new YoldaDriveCore.Trip();
		t.update(0, 0, LA, LO, 60, null, false); // araç bağlı değil: hız olsa da başlamaz
		assertFalse(t.isActive());
		t.update(1000, 1000, LA, LO, 60, null, true); // Android Auto bağlı: başlar
		assertTrue(t.isActive());
		assertFalse(t.isManual());
	}

	@Test
	public void elleBaslatBitir() {
		YoldaDriveCore.Trip t = new YoldaDriveCore.Trip();
		assertTrue(t.start(0, LA, LO, true));
		assertTrue(t.isManual());
		assertFalse(t.start(10, LA, LO, true)); // zaten sürüyor
		// 2 km yol
		long ms = 0;
		for (int i = 1; i <= 20; i++) {
			ms += 5000;
			t.update(ms, ms, LA + i * 0.0009, LO, 50, null, false);
		}
		assertTrue(t.meters() > 1500);
		// 15 dk durunca elle başlatılan bitmez (otomatik 10 dk'da biterdi)
		for (int i = 0; i < 18; i++) {
			ms += 60_000;
			assertNull(t.update(ms, ms, LA + 20 * 0.0009, LO, 0, null, false));
		}
		assertTrue(t.isActive());
		YoldaDriveCore.TripRec r = t.end();
		assertNotNull(r);
		assertFalse(t.isActive());
	}

	@Test
	public void aracBaglaninciKonumsuzBaslar() {
		YoldaDriveCore.Trip t = new YoldaDriveCore.Trip();
		assertTrue(t.start(0, Double.NaN, Double.NaN, false)); // konum henüz yok
		long ms = 0;
		for (int i = 0; i <= 20; i++) {
			ms += 5000;
			t.update(ms, ms, LA + i * 0.0009, LO, 50, null, true);
		}
		YoldaDriveCore.TripRec r = t.end();
		assertNotNull(r);
		assertEquals(LA, r.startLat(), 1e-6); // ilk konum başlangıç
	}

	@Test
	public void sesliKomut() {
		assertEquals("start", YoldaIntents.parse("yolculuğu başlat").arg());
		assertEquals("stop", YoldaIntents.parse("yolculuğu bitir").arg());
		assertEquals(YoldaIntents.Kind.TRIP, YoldaIntents.parse("yolculuk kaydını durdur").kind());
	}
}
