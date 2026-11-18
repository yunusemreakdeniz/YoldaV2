package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Hava takibi: hangi değişiklikte ne söylenir. */
public class YoldaWeatherWatchTest {
	@Test
	public void degisiklikler() {
		// açıktan yağmura
		assertEquals("Ankara: Yağmur var. Takip mesafesini artır", YoldaWeatherWatch.change(0, 12, 61, 11, -1, -1, "Ankara"));
		// ilk ölçüm kötü havaysa söylenir, iyi havaysa söylenmez
		assertEquals("Sis var. Farlarını aç ve yavaşla", YoldaWeatherWatch.change(-1, Integer.MIN_VALUE, 45, 5, -1, -1, null));
		assertNull(YoldaWeatherWatch.change(-1, Integer.MIN_VALUE, 2, 15, -1, -1, null));
		// aynı türde kalınca tekrar söylenmez
		assertNull(YoldaWeatherWatch.change(61, 11, 63, 10, -1, -1, null));
		// düzelme
		assertTrue(YoldaWeatherWatch.change(61, 11, 1, 12, -1, -1, null).startsWith("Hava düzeldi"));
		// buzlanma sınırı
		assertEquals("Hava 2 dereceye düştü. Yollarda buzlanma olabilir", YoldaWeatherWatch.change(2, 5, 2, 2, -1, -1, "Konumun"));
		// bir saat içinde yağış (bir kez)
		assertEquals("Bir saat içinde kar bekleniyor", YoldaWeatherWatch.change(3, 1, 3, 1, 73, -1, null));
		assertNull(YoldaWeatherWatch.change(3, 1, 3, 1, 73, YoldaWeatherWatch.SNOW, null));
	}

	@Test
	public void turler() {
		assertEquals(YoldaWeatherWatch.RAIN, YoldaWeatherWatch.cat(81));
		assertEquals(YoldaWeatherWatch.STORM, YoldaWeatherWatch.cat(95));
		assertEquals(YoldaWeatherWatch.CLOUDY, YoldaWeatherWatch.cat(3));
	}
}
