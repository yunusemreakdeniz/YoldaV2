package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Yol çalışmaları (Mola'dan): sesli komut, Overpass yanıtı ayrıştırma, yakından uzağa, tekrarsız. */
public class YoldaRoadworksTest {
	@Test
	public void komut() {
		for (String c : new String[]{"yol çalışması var mı", "yakında yol çalışması", "yol çalışmalarını göster"})
			assertEquals(c, YoldaIntents.Kind.ROADWORKS, YoldaIntents.parse(c).kind());
	}

	@Test
	public void ayristir() throws Exception {
		String json = "{\"elements\":["
				+ "{\"type\":\"way\",\"center\":{\"lat\":39.95,\"lon\":32.85},\"tags\":{\"highway\":\"construction\",\"construction\":\"primary\",\"name\":\"Eskişehir Yolu\",\"opening_date\":\"2027-03\"}},"
				+ "{\"type\":\"way\",\"center\":{\"lat\":39.951,\"lon\":32.851},\"tags\":{\"highway\":\"construction\",\"construction\":\"primary\",\"name\":\"Eskişehir Yolu\"}},"
				+ "{\"type\":\"node\",\"lat\":39.92,\"lon\":32.85,\"tags\":{\"hazard\":\"roadworks\"}}]}";
		List<YoldaNearby.Spot> l = YoldaNearby.parseRoadworks(json, 39.92, 32.85, 10);
		assertEquals("aynı yolun yakın parçaları tek satır", 2, l.size());
		assertEquals("Yol çalışması", l.get(0).name());
		assertEquals("Eskişehir Yolu", l.get(1).name());
		assertTrue(l.get(1).info().contains("Ana yol çalışması"));
		assertTrue(l.get(1).info().contains("bitiş 2027-03"));
	}
}
