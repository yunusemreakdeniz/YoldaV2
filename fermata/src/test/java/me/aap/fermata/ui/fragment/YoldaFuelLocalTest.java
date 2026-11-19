package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** İle göre akaryakıt: il adı → plaka, Photon ters arama, Opet ilçe fiyatı. */
public class YoldaFuelLocalTest {
	@Test
	public void plakaKodu() {
		assertEquals(81, YoldaFuelLocal.PROVINCES.length);
		assertEquals(6, YoldaFuelLocal.plate("Ankara"));
		assertEquals(34, YoldaFuelLocal.plate("İstanbul"));
		assertEquals(34, YoldaFuelLocal.plate("Istanbul"));
		assertEquals(33, YoldaFuelLocal.plate("Mersin"));
		assertEquals(33, YoldaFuelLocal.plate("İçel"));
		assertEquals(46, YoldaFuelLocal.plate("Kahramanmaraş"));
		assertEquals(81, YoldaFuelLocal.plate("Düzce"));
		assertEquals(0, YoldaFuelLocal.plate("Berlin"));
	}

	@Test
	public void tersArama() throws Exception {
		String[] w = YoldaFuelLocal.parseReverse("{\"features\":[{\"properties\":{\"state\":\"Ankara\",\"county\":\"Çankaya\"}}]}");
		assertEquals("Ankara", w[0]);
		assertEquals("Çankaya", w[1]);
		assertNull(YoldaFuelLocal.parseReverse("{\"features\":[]}"));
	}

	@Test
	public void opetIlceFiyati() throws Exception {
		String js = "[{\"provinceName\":\"ANKARA\",\"districtName\":\"AKYURT\",\"prices\":["
				+ "{\"productName\":\"Kurşunsuz Benzin 95\",\"amount\":72.24},{\"productName\":\"Motorin UltraForce\",\"amount\":96.38},"
				+ "{\"productName\":\"Motorin EcoForce\",\"amount\":96.30}]},"
				+ "{\"provinceName\":\"ANKARA\",\"districtName\":\"ÇANKAYA\",\"prices\":["
				+ "{\"productName\":\"Kurşunsuz Benzin 95\",\"amount\":72.10},{\"productName\":\"Motorin EcoForce\",\"amount\":96.20}]}]";
		YoldaFuelLocal.Local l = YoldaFuelLocal.parseOpet(js, "Çankaya");
		assertEquals("Çankaya", l.district());
		assertEquals("Ankara", l.province());
		assertEquals(72.10, l.gasoline(), 1e-9);
		assertEquals(96.20, l.diesel(), 1e-9);
		YoldaFuelLocal.Local f = YoldaFuelLocal.parseOpet(js, "Yok"); // ilçe bulunamazsa ilk ilçe
		assertEquals("Akyurt", f.district());
		assertEquals(96.30, f.diesel(), 1e-9); // standart (EcoForce) motorin
		assertNull(YoldaFuelLocal.parseOpet("[]", null));
	}
}
