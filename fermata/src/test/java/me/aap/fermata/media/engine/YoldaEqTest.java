package me.aap.fermata.media.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Ekolayzer hazır ayarları: aradeğerleme, cihaz bantlarına uyarlama, söylenen ad. */
public class YoldaEqTest {
	@Test
	public void bantlaraUyarlanir() {
		Object[] rock = YoldaEq.find("Rock");
		assertEquals(4.0, YoldaEq.gainAt(rock, 60), 1e-9);
		assertEquals(4.0, YoldaEq.gainAt(rock, 20), 1e-9); // en alttan aşağısı ilk değer
		assertEquals(4.0, YoldaEq.gainAt(rock, 20000), 1e-9); // en üstten yukarısı son değer
		double mid = YoldaEq.gainAt(rock, Math.sqrt(60.0 * 230)); // logaritmik orta: 4 ile 2'nin ortası
		assertEquals(3.0, mid, 1e-9);
		// 5 bantlı tipik cihaz: 60, 230, 910, 3600, 14000 Hz; aralık ±15 dB
		short[] lv = YoldaEq.levels(rock, new int[]{60, 230, 910, 3600, 14000}, (short) -1500, (short) 1500);
		assertArrayEquals(new short[]{400, 200, -200, 200, 400}, lv);
		// dar aralık: sınırlanır
		short[] cl = YoldaEq.levels(YoldaEq.find("Bas"), new int[]{60}, (short) -300, (short) 300);
		assertEquals(300, cl[0]);
	}

	@Test
	public void soylenenAd() {
		assertEquals("Rock", YoldaEq.spoken("rock ekolayzer"));
		assertEquals("Konuşma", YoldaEq.spoken("ses modunu haber yap"));
		assertEquals("Araç", YoldaEq.spoken("araç ses ayarı"));
		assertEquals("Kapalı", YoldaEq.spoken("ekolayzeri kapat"));
		assertNull(YoldaEq.spoken("ekolayzer"));
		assertEquals(9, YoldaEq.names().length); // 8 hazır ayar + Özel
	}

	@Test
	public void ozelBantlar() {
		// dikey sürgü: üst +10, orta 0, alt -10, dışarısı sınırda
		org.junit.Assert.assertEquals(10, YoldaEq.dbAt(0, 0, 200));
		org.junit.Assert.assertEquals(0, YoldaEq.dbAt(100, 0, 200));
		org.junit.Assert.assertEquals(-10, YoldaEq.dbAt(200, 0, 200));
		org.junit.Assert.assertEquals(5, YoldaEq.dbAt(50, 0, 200));
		org.junit.Assert.assertEquals(-10, YoldaEq.dbAt(999, 0, 200));
		org.junit.Assert.assertEquals(10, YoldaEq.dbAt(-50, 0, 200));
		// kaydet / oku: sınırlanır, eksik 0
		org.junit.Assert.assertEquals("4,-2,0,10,-10", YoldaEq.encode(new int[]{4, -2, 0, 15, -99}));
		org.junit.Assert.assertArrayEquals(new int[]{4, -2, 0, 10, -10}, YoldaEq.decode("4,-2,0,10,-10"));
		org.junit.Assert.assertArrayEquals(new int[]{3, 0, 0, 0, 0}, YoldaEq.decode("3,x"));
		org.junit.Assert.assertArrayEquals(new int[5], YoldaEq.decode(null));
		// adlar: Özel listede, bant etiketleri
		String[] n = YoldaEq.names();
		org.junit.Assert.assertEquals(YoldaEq.CUSTOM, n[n.length - 1]);
		org.junit.Assert.assertEquals("60 Hz", YoldaEq.bandLabel(0));
		org.junit.Assert.assertEquals("3,6 kHz", YoldaEq.bandLabel(3));
		org.junit.Assert.assertEquals("14 kHz", YoldaEq.bandLabel(4));
	}
}
