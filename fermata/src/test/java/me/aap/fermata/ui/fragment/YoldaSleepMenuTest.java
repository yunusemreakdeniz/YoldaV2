package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Menüdeki uyku zamanlayıcısı: her dokunuşta sıradaki süre. */
public class YoldaSleepMenuTest {
	@Test
	public void sira() {
		assertEquals(15, YoldaCardStats.nextSleep(0));
		assertEquals(30, YoldaCardStats.nextSleep(12));
		assertEquals(60, YoldaCardStats.nextSleep(29));
		assertEquals(0, YoldaCardStats.nextSleep(45));
	}
}
