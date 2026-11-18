package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Navigasyon yer önerileri: Photon yanıtı, yerel eşleşme, uzaklık yazısı. */
public class YoldaPlaceSuggestTest {
	@Test
	public void photonOkunur() throws Exception {
		String json = "{\"features\":["
				+ "{\"geometry\":{\"coordinates\":[32.8541,39.9208]},\"properties\":{\"name\":\"Anıtkabir\",\"street\":\"Anıt Caddesi\",\"district\":\"Çankaya\",\"city\":\"Ankara\"}},"
				+ "{\"geometry\":{\"coordinates\":[32.8541,39.9208]},\"properties\":{\"name\":\"Anıtkabir\",\"street\":\"Anıt Caddesi\",\"district\":\"Çankaya\",\"city\":\"Ankara\"}},"
				+ "{\"geometry\":{\"coordinates\":[29.0,41.0]},\"properties\":{\"street\":\"Bağdat Caddesi\",\"housenumber\":\"10\",\"city\":\"İstanbul\"}},"
				+ "{\"properties\":{}}]}";
		List<YoldaPlaceSuggest.Suggestion> l = YoldaPlaceSuggest.parse(json, 39.92, 32.85);
		assertEquals(2, l.size()); // aynısı bir kez, boşu atlanır
		assertEquals("Anıtkabir", l.get(0).title());
		assertEquals("Anıt Caddesi, Çankaya, Ankara", l.get(0).sub());
		assertEquals("Anıtkabir, Anıt Caddesi, Çankaya, Ankara", l.get(0).dest());
		assertTrue(l.get(0).meters() < 1000);
		assertEquals("Bağdat Caddesi 10", l.get(1).title());
	}

	@Test
	public void yerelEslesme() {
		List<YoldaPlaceSuggest.Suggestion> l = YoldaPlaceSuggest.local("kizilay", List.of("Kızılay Meydanı", "Ulus"),
				"Yenimahalle", null);
		assertEquals(1, l.size());
		assertEquals("Kızılay Meydanı", l.get(0).title());
		assertEquals("Ev", YoldaPlaceSuggest.local("ev", List.of(), "Yenimahalle", null).get(0).title());
	}

	@Test
	public void uzaklik() {
		assertEquals("", YoldaPlaceSuggest.distance(-1));
		assertEquals("850 m", YoldaPlaceSuggest.distance(848));
		assertEquals("12 km", YoldaPlaceSuggest.distance(12_400));
		assertEquals("2,5 km", YoldaPlaceSuggest.distance(2_500));
	}
}
