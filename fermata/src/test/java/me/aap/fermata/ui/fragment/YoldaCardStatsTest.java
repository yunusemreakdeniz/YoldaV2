package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

/** Kart yüzü hesapları: park süresi, haftalık sürüş, döviz değişimi, namaz uyarısı. */
public class YoldaCardStatsTest {
	@Test
	public void sure() {
		assertEquals("25 dk", YoldaCardStats.elapsed(25 * 60_000L));
		assertEquals("1 sa 25 dk", YoldaCardStats.elapsed(85 * 60_000L));
		assertEquals("2 gün 3 sa", YoldaCardStats.elapsed((51 * 60 + 10) * 60_000L));
		assertEquals(5, YoldaCardStats.walkMin(350));
		assertEquals(1, YoldaCardStats.walkMin(20));
	}

	private static long at(int y, int m, int d, int h) {
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(y, m - 1, d, h, 0, 0);
		return c.getTimeInMillis();
	}

	private static YoldaDriveCore.TripRec trip(long start, int meters, long ms) {
		return new YoldaDriveCore.TripRec(start, start + ms, meters, ms, 90, 0, 0, 0, 0, 0);
	}

	@Test
	public void hafta() {
		long now = at(2026, 11, 20, 12); // 20 Kasım 2026 cuma
		List<YoldaDriveCore.TripRec> l = List.of(trip(at(2026, 11, 16, 9), 40_000, 3_600_000L), // pazartesi
				trip(at(2026, 11, 18, 18), 20_000, 1_200_000L), trip(at(2026, 11, 20, 8), 10_000, 600_000L),
				trip(at(2026, 11, 15, 9), 99_000, 3_600_000L)); // geçen pazar: sayılmaz
		YoldaCardStats.Week w = YoldaCardStats.week(l, now);
		assertEquals(4, w.today());
		assertEquals(3, w.count());
		assertEquals(70_000, w.meters());
		assertEquals(40_000, w.dayMeters()[0]);
		assertEquals(20_000, w.dayMeters()[2]);
		assertEquals(10_000, w.dayMeters()[4]);
		assertEquals(0, w.dayMeters()[6]);
		assertEquals(47, YoldaCardStats.avgKmh(w)); // 70 km / 1,5 sa
	}

	@Test
	public void doviz() {
		String s = YoldaCardStats.fxRoll(null, "20261119", 41.80);
		assertTrue(Double.isNaN(YoldaCardStats.fxChange(s, 41.80))); // ilk gün dünkü yok
		s = YoldaCardStats.fxRoll(s, "20261119", 41.79); // aynı gün: son değer güncellenir
		s = YoldaCardStats.fxRoll(s, "20261120", 41.92); // ertesi gün: dünkü = 41,79
		assertEquals(0.31, YoldaCardStats.fxChange(s, 41.92), 0.01);
		assertEquals("▲ %0,3", YoldaCardStats.fxText(0.31));
		assertEquals("▼ %0,2", YoldaCardStats.fxText(-0.2));
		assertEquals("= %0,0", YoldaCardStats.fxText(0.01));
		assertEquals("", YoldaCardStats.fxText(Double.NaN));
		assertEquals(0xFFF87171, YoldaCardStats.fxColor(0.5));
		assertEquals(0xFF34D399, YoldaCardStats.fxColor(-0.5));
	}

	@Test
	public void namaz() {
		assertTrue(YoldaCardStats.prayerWarnNow(10));
		assertTrue(YoldaCardStats.prayerWarnNow(9));
		assertFalse(YoldaCardStats.prayerWarnNow(11));
		assertFalse(YoldaCardStats.prayerWarnNow(3));
		assertEquals(72, YoldaCardStats.minutesLeft(17 * 60 + 42, 16 * 60 + 30));
		assertEquals(30, YoldaCardStats.minutesLeft(10, 23 * 60 + 40));
	}
}
