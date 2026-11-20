package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;

/** Eczane kartının en yakın nöbetçi kaydı: saklama biçimi ve nöbet günü anahtarı. */
public class YoldaPharmacyNearTest {
	@Test
	public void kayitGidipGelir() {
		YoldaPharmacy.Near n = new YoldaPharmacy.Near("Şifa Eczanesi", 40.9901, 29.0287, "0216 000 00 00", "20261120");
		YoldaPharmacy.Near b = YoldaPharmacy.decodeNear(YoldaPharmacy.encodeNear(n));
		assertEquals(n, b);
		assertNull(YoldaPharmacy.decodeNear(null));
		assertNull(YoldaPharmacy.decodeNear("bozuk"));
	}

	@Test
	public void gunAnahtari() {
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(2026, Calendar.NOVEMBER, 5, 12, 0, 0);
		assertEquals("20261105", YoldaPharmacy.dutyKey(c));
	}
}
