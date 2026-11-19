package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Yakın yerler: Overpass otopark / şarj yanıtı, bilgi satırı, Photon koordinatı, sesli komut. */
public class YoldaNearbyTest {
	private static final double LA = 39.92, LO = 32.85;

	@Test
	public void otoparklarYakindanUzaga() throws Exception {
		String js = "{\"elements\":["
				+ "{\"type\":\"way\",\"center\":{\"lat\":39.925,\"lon\":32.85},\"tags\":{\"amenity\":\"parking\",\"parking\":\"multi-storey\",\"fee\":\"yes\",\"capacity\":\"300\"}},"
				+ "{\"type\":\"node\",\"lat\":39.9205,\"lon\":32.85,\"tags\":{\"amenity\":\"parking\",\"name\":\"Kızılay Otoparkı\",\"fee\":\"no\"}},"
				+ "{\"type\":\"node\",\"tags\":{}}]}";
		List<YoldaNearby.Spot> l = YoldaNearby.parse(js, LA, LO, false, 10);
		assertEquals(2, l.size()); // konumsuz atlanır
		assertEquals("Kızılay Otoparkı", l.get(0).name()); // en yakın önce
		assertEquals("ücretsiz", l.get(0).info());
		assertEquals("Katlı otopark", l.get(1).name());
		assertEquals("ücretli · 300 araç · katlı", l.get(1).info());
		assertTrue(l.get(0).meters() < l.get(1).meters());
	}

	@Test
	public void sarjIstasyonu() throws Exception {
		String js = "{\"elements\":[{\"type\":\"node\",\"lat\":39.93,\"lon\":32.86,\"tags\":{\"amenity\":\"charging_station\","
				+ "\"operator\":\"ZES\",\"socket:type2_combo\":\"2\",\"socket:type2\":\"2\",\"socket:type2_combo:output\":\"120 kW\",\"capacity\":\"4\"}}]}";
		YoldaNearby.Spot s = YoldaNearby.parse(js, LA, LO, true, 5).get(0);
		assertEquals("ZES", s.name());
		assertEquals("CCS · Type 2 · 120kW · 4 soket", s.info());
	}

	@Test
	public void photonVeKomut() throws Exception {
		double[] c = YoldaNearby.firstCoord("{\"features\":[{\"geometry\":{\"coordinates\":[32.8541,39.9208]}}]}");
		assertEquals(39.9208, c[0], 1e-9);
		assertEquals(32.8541, c[1], 1e-9);
		assertNull(YoldaNearby.firstCoord("{\"features\":[]}"));
		assertEquals(YoldaIntents.Kind.PARK_DEST, YoldaIntents.parse("gideceğim yerde otopark var mı").kind());
		assertNull(YoldaIntents.parse("gideceğim yerde otopark var mı").arg());
		assertEquals("kızılay", YoldaIntents.parse("kızılayda otopark").arg());
		assertTrue(YoldaIntents.parse("yakındaki otopark").kind() != YoldaIntents.Kind.PARK_DEST);
	}
}
