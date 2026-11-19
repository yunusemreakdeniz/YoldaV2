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
		assertEquals(8, YoldaEq.names().length);
	}
}
