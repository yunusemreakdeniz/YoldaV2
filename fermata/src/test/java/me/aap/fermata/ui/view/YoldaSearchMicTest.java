package me.aap.fermata.ui.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Yazı kutusundaki mikrofon dokunma alanı: sağ uçta en az 48dp. */
public class YoldaSearchMicTest {
	@Test
	public void dokunmaAlani() {
		float d = 2f; // 2x yoğunluk: 48dp = 96 px
		assertTrue(YoldaSearchMic.hitsMic(950, 1000, 36, 44, d)); // sağ uç
		assertTrue(YoldaSearchMic.hitsMic(905, 1000, 36, 44, d)); // 96 px içinde
		assertFalse(YoldaSearchMic.hitsMic(500, 1000, 36, 44, d)); // kutunun ortası: klavye
		assertTrue(YoldaSearchMic.hitsMic(880, 1000, 60, 44, d)); // geniş iç boşlukta alan büyür (60+44+24=128)
		assertFalse(YoldaSearchMic.hitsMic(860, 1000, 60, 44, d));
	}
}
