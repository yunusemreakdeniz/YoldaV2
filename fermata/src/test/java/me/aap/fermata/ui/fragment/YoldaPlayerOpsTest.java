package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Oynatıcı işlem satırı: hız sırası, etiketler, sarma hedefi, uyku etiketi. */
public class YoldaPlayerOpsTest {
	@Test
	public void hizSirasi() {
		assertEquals(1.25f, YoldaPlayerOps.nextSpeed(1f), 0.001f);
		assertEquals(1.5f, YoldaPlayerOps.nextSpeed(1.25f), 0.001f);
		assertEquals(2f, YoldaPlayerOps.nextSpeed(1.5f), 0.001f);
		assertEquals(0.75f, YoldaPlayerOps.nextSpeed(2f), 0.001f);
		assertEquals(1f, YoldaPlayerOps.nextSpeed(0.75f), 0.001f);
		assertEquals("bilinmeyen hız 1'e döner", 1f, YoldaPlayerOps.nextSpeed(3.3f), 0.001f);
	}

	@Test
	public void etiketler() {
		assertEquals("1×", YoldaPlayerOps.speedLabel(1f));
		assertEquals("1,25×", YoldaPlayerOps.speedLabel(1.25f));
		assertEquals("2×", YoldaPlayerOps.speedLabel(2f));
		assertEquals("0,75×", YoldaPlayerOps.speedLabel(0.75f));
		assertEquals("Uyku", YoldaPlayerOps.sleepLabel(0));
		assertEquals("30 dk", YoldaPlayerOps.sleepLabel(30));
	}

	@Test
	public void sarma() {
		assertEquals(40_000L, YoldaPlayerOps.skipTarget(10_000L, true));
		assertEquals(0L, YoldaPlayerOps.skipTarget(10_000L, false));
		assertEquals(85_000L, YoldaPlayerOps.skipTarget(100_000L, false));
	}

	@Test
	public void baskaIcerikteHizBir() {
		assertEquals(1f, YoldaPlayerOps.speed("baska"), 0.001f);
	}
}
