package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Uyarı geçmişi: saklama biçimi, sınır, zaman aralığı ve özet. */
public class YoldaAlertLogTest {
	@Test
	public void saklaVeOku() {
		List<YoldaAlertLog.Entry> l = new ArrayList<>();
		l.add(new YoldaAlertLog.Entry(2000, 'r', "500 metre sonra radar | 70", 39.91234, 32.85678));
		l.add(new YoldaAlertLog.Entry(1000, 'w', "Yağmur başladı", Double.NaN, Double.NaN));
		List<YoldaAlertLog.Entry> back = YoldaAlertLog.decode(YoldaAlertLog.encode(l));
		assertEquals(2, back.size());
		assertEquals('r', back.get(0).kind());
		assertEquals("500 metre sonra radar / 70", back.get(0).text());
		assertEquals(39.91234, back.get(0).lat(), 1e-6);
		assertTrue(back.get(0).hasPlace());
		assertFalse(back.get(1).hasPlace());
	}

	@Test
	public void sinirAralikOzet() {
		List<YoldaAlertLog.Entry> l = new ArrayList<>();
		for (int i = 0; i < YoldaAlertLog.MAX + 20; i++) l.add(new YoldaAlertLog.Entry(i, (i % 2 == 0) ? 'r' : 'o', "x", 0, 0));
		assertEquals(YoldaAlertLog.MAX, YoldaAlertLog.decode(YoldaAlertLog.encode(l)).size());
		List<YoldaAlertLog.Entry> b = YoldaAlertLog.between(l, 10, 13);
		assertEquals(4, b.size());
		assertEquals("2 radar · 2 hız aşımı", YoldaAlertLog.summary(b));
		assertTrue(YoldaAlertLog.decode("bozuk satır\n").isEmpty());
	}
}
