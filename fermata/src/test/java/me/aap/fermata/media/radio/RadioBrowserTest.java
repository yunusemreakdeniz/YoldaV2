package me.aap.fermata.media.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Radyo dizini: çalışmayanları eleme, tür ve şehir süzgeci. */
public class RadioBrowserTest {
	private static final String JSON = "["
			+ "{\"name\":\"Radyo A\",\"url_resolved\":\"https://a\",\"tags\":\"pop,hits\",\"state\":\"İstanbul\",\"bitrate\":128,\"lastcheckok\":1},"
			+ "{\"name\":\"Ölü Radyo\",\"url_resolved\":\"https://b\",\"tags\":\"pop\",\"lastcheckok\":0},"
			+ "{\"name\":\"Haber FM\",\"url_resolved\":\"https://c\",\"tags\":\"news\",\"state\":\"Ankara\",\"lastcheckok\":1},"
			+ "{\"name\":\"Türkü Radyo\",\"url_resolved\":\"https://d\",\"tags\":\"folk\",\"state\":\"Istanbul\"}"
			+ "]";

	@Test
	public void olulerElenir() throws Exception {
		List<RadioBrowser.Station> l = RadioBrowser.parse(JSON);
		assertEquals(3, l.size());
		for (RadioBrowser.Station s : l) assertFalse(s.name().startsWith("Ölü"));
	}

	@Test
	public void turVeSehir() throws Exception {
		List<RadioBrowser.Station> l = RadioBrowser.parse(JSON);
		String[] pop = RadioBrowser.GENRES[1];
		assertTrue(RadioBrowser.matchesGenre(l.get(0), pop));
		assertFalse(RadioBrowser.matchesGenre(l.get(1), pop));
		assertTrue(RadioBrowser.matchesCity(l.get(2), "istanbul")); // İ / I farkı gözetilmez
		assertEquals("İstanbul", RadioBrowser.cities(l, 5).get(0)); // iki istasyonla ilk sırada
		assertEquals("İstanbul · pop, hits · 128 kbps", l.get(0).sub());
	}
}
