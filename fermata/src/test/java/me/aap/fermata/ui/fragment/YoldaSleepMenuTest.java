package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Menüdeki uyku zamanlayıcısı: her dokunuşta sıradaki süre. */
public class YoldaSleepMenuTest {
	@Test
	public void sira() {
		assertEquals(15, YoldaMenuFragment.nextSleep(0));
		assertEquals(30, YoldaMenuFragment.nextSleep(12));
		assertEquals(60, YoldaMenuFragment.nextSleep(29));
		assertEquals(0, YoldaMenuFragment.nextSleep(45));
	}
}
