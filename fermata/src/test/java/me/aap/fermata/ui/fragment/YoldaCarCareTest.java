package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

/** Araç bakımı ve takvim: tarih okuma, kalan gün, okunuş; sesli komutlar. */
public class YoldaCarCareTest {
	private static long day(int y, int m, int d) {
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(y, m - 1, d);
		return c.getTimeInMillis();
	}

	@Test
	public void tarihVeKalanGun() {
		assertEquals(day(2027, 3, 15), YoldaCarCare.parseDate("15.03.2027"));
		assertEquals(day(2027, 3, 15), YoldaCarCare.parseDate("15/3/27"));
		assertEquals(day(2027, 3, 15), YoldaCarCare.parseDate("2027-03-15"));
		assertEquals(-1, YoldaCarCare.parseDate("yarın"));
		assertEquals(-1, YoldaCarCare.parseDate("32.13.2027"));
		long now = day(2026, 11, 19) + 15 * 3600_000L; // öğleden sonra
		assertEquals(0, YoldaCarCare.daysLeft(day(2026, 11, 19), now));
		assertEquals(1, YoldaCarCare.daysLeft(day(2026, 11, 20), now));
		assertEquals(-3, YoldaCarCare.daysLeft(day(2026, 11, 16), now));
		assertEquals("bugün", YoldaCarCare.left(0));
		assertEquals("yarın", YoldaCarCare.left(1));
		assertEquals("42 gün", YoldaCarCare.left(42));
		assertEquals("3 gün geçti", YoldaCarCare.left(-3));
	}

	@Test
	public void takvimOkunusu() {
		long now = day(2026, 11, 19) + 10 * 3600_000L;
		YoldaCalendar.Event e = new YoldaCalendar.Event(now + 3600_000L * 4 + 30 * 60_000L, now + 3600_000L * 5, "Toplantı", "Kızılay", false);
		assertTrue(YoldaCalendar.when(e, now).matches("\\d\\d:\\d\\d"));
		assertEquals("Şimdi", YoldaCalendar.when(new YoldaCalendar.Event(now - 1000, now + 1000, "A", "", false), now));
		assertEquals("Tüm gün", YoldaCalendar.when(new YoldaCalendar.Event(now, now + 1000, "A", "", true), now));
		assertTrue(YoldaCalendar.when(new YoldaCalendar.Event(now + 24 * 3600_000L, now + 25 * 3600_000L, "A", "", false), now).startsWith("Yarın"));
		assertEquals("Yakında bir etkinlik yok", YoldaCalendar.spoken(List.of(), now));
		assertTrue(YoldaCalendar.spoken(List.of(e), now).contains("Toplantı, Kızılay"));
	}

	@Test
	public void sesliKomutlar() {
		assertEquals(YoldaIntents.Kind.CALENDAR, YoldaIntents.parse("bugün ne var").kind());
		assertEquals(YoldaIntents.Kind.CALENDAR, YoldaIntents.parse("toplantım ne zaman").kind());
		assertEquals(YoldaIntents.Kind.CARCARE, YoldaIntents.parse("muayene ne zaman").kind());
		assertEquals(YoldaIntents.Kind.CARCARE, YoldaIntents.parse("sigortam ne zaman bitiyor").kind());
	}
}
