package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Panel kartları temaya uyar ama her temada okunur kalır. */
public class YoldaKokpitPaletteTest {
	private static final int TEXT = 0xFFF4F4F5, SUB = 0xFFA9ADB4;
	// YoldaStyle'daki temalardan örnekler: {vurgu, ışık 0, ışık 1, ışık 2, ışık 3, stil}
	private static final int[][] THEMES = {
			{0xFFF5F7FF, 0xFF5B21B6, 0xFF9D174D, 0xFF0369A1, 0xFF0A0D18, 0}, // Gece
			{0xFFFFC857, 0xFFFF8A3D, 0xFFFF4F6D, 0xFFFFC857, 0xFF6B1E3F, 0}, // Gün batımı
			{0xFFE6E8EE, 0xFF2A2740, 0xFF1C2433, 0xFF262230, 0xFF0B0D12, 2}, // Asil
			{0xFFE5E7EB, 0xFF9CA3AF, 0xFF6B7280, 0xFFD1D5DB, 0xFF1F2328, 1}, // Gümüş
			{0xFFE0F2FE, 0xFF7DD3FC, 0xFFBAE6FD, 0xFF38BDF8, 0xFF0C2A40, 1}, // Buz mavisi
			{0xFFBEF264, 0xFF16A34A, 0xFF84CC16, 0xFF0D9488, 0xFF14361F, 0}, // Orman
			{0xFFFDE047, 0xFFF59E0B, 0xFFEA580C, 0xFFFDE047, 0xFF5C3310, 0}, // Kehribar
	};

	private static YoldaKokpitPalette of(int[] t) {
		return YoldaKokpitPalette.of(t[0], new int[]{t[1], t[2], t[3], t[4]}, t[5]);
	}

	@Test
	public void herTemadaOkunur() {
		for (int[] t : THEMES) {
			YoldaKokpitPalette k = of(t);
			for (int bg : new int[]{k.well(), k.wellEnd(), k.deep()}) {
				assertTrue("Başlık karşıtlığı " + Integer.toHexString(bg), YoldaKokpitPalette.contrast(TEXT, bg) >= 7);
				assertTrue("Alt yazı karşıtlığı " + Integer.toHexString(bg), YoldaKokpitPalette.contrast(SUB, bg) >= 4.5);
				assertTrue("Vurgu (simge / etiket) karşıtlığı " + Integer.toHexString(k.accent()),
						YoldaKokpitPalette.contrast(k.accent(), bg) >= 4.5);
			}
		}
	}

	@Test
	public void temayaUyar() {
		YoldaKokpitPalette gece = of(THEMES[0]), orman = of(THEMES[5]);
		assertNotEquals("Renkli temalarda kart rengi farklı", gece.well(), orman.well());
		assertNotEquals("Canlı temada hafif renk geçişi", gece.well(), gece.wellEnd());
		assertEquals("Gün batımının vurgusu kendi sarısı", 0xFFFFC857, of(THEMES[1]).accent());
		assertTrue("Gece: beyaz vurgu yerine morumsu", YoldaKokpitPalette.saturation(gece.accent()) > 0.3f);
		assertEquals("Renksiz Asil temada kendi platin vurgusu", 0xFFE6E8EE, of(THEMES[2]).accent());
		assertEquals("Gümüş temada gümüş", 0xFFE5E7EB, of(THEMES[3]).accent());
		assertEquals("Koyu vurgulu renksiz temada altın", YoldaKokpitPalette.GOLD,
				YoldaKokpitPalette.pickAccent(0xFF333333, new int[]{0xFF222222, 0xFF2A2A2A}));
		YoldaKokpitPalette asil = of(THEMES[2]);
		assertEquals("Asil: düz yüzey", asil.well(), asil.wellEnd());
	}
}
