package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.view.YoldaSky;

/** Akıcılık: ışık önbelleği sınırlı, yavaş kare sınıfları ve günlük satırı. */
public class YoldaPerfTest {
	@Test
	public void renkYuvarlanir() {
		// kanal başına 32 basamak: renk geçişindeki ara renklerin çoğu aynı ışığı kullanır
		assertEquals(YoldaSky.quantize(0xFF7C3AED), YoldaSky.quantize(0xFF7D3BEE));
		assertTrue(YoldaSky.quantize(0xFF7C3AED) != YoldaSky.quantize(0xFF843AED));
		java.util.Set<Integer> keys = new java.util.HashSet<>();
		for (int i = 0; i <= 255; i++) keys.add(YoldaSky.quantize(0xFF000000 | (i << 16) | (i << 8) | i)); // gri geçiş
		assertEquals(32, keys.size()); // 256 ara renk → 32 ışık
	}

	@Test
	public void yavasKare() {
		assertEquals(0, me.aap.fermata.ui.view.YoldaJankAccess.classify(16_000_000L));
		assertEquals(1, me.aap.fermata.ui.view.YoldaJankAccess.classify(40_000_000L));
		assertEquals(2, me.aap.fermata.ui.view.YoldaJankAccess.classify(300_000_000L));
		assertNull(me.aap.fermata.ui.view.YoldaJankAccess.line("Ana sayfa", 1800, 0, 0));
		assertEquals("Akıcılık · Ana sayfa: 1800 kare, 42 yavaş (%2,3), 1 donma",
				me.aap.fermata.ui.view.YoldaJankAccess.line("Ana sayfa", 1800, 42, 1));
	}

	@Test
	public void saydamlikBasamakli() {
		// yumuşak parçacıkların saydamlığı 16'lık basamaklarda: ışık önbelleği en çok 17 saydamlık
		java.util.Set<Integer> a = new java.util.HashSet<>();
		for (int i = 0; i <= 300; i++) a.add(me.aap.fermata.ui.view.YoldaSkyAccess.qa(i));
		assertTrue(a.size() <= 17);
		assertEquals(0, me.aap.fermata.ui.view.YoldaSkyAccess.qa(-5));
		assertEquals(255, me.aap.fermata.ui.view.YoldaSkyAccess.qa(999));
		assertEquals(64, me.aap.fermata.ui.view.YoldaSkyAccess.qa(66));
	}

	@Test
	public void kurulumSuresi() {
		assertNull(me.aap.fermata.ui.view.YoldaJankAccess.buildLine("Kanallar ızgarası", 12, 47)); // kısa: yazılmaz
		assertEquals("Kurulum · Kanallar ızgarası: 120 ms (47 öğe)",
				me.aap.fermata.ui.view.YoldaJankAccess.buildLine("Kanallar ızgarası", 120, 47));
		assertEquals("Kurulum · Telefon görünümü: 64 ms", me.aap.fermata.ui.view.YoldaJankAccess.buildLine("Telefon görünümü", 64, 0));
	}
}
