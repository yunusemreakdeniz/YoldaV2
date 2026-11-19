package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** TomTom Snap to Roads hız sınırı: istek adresi, yanıt, kota ve geçerlilik kuralları. */
public class YoldaTomTomTest {
	@Test
	public void istekAdresi() throws Exception {
		String u = YoldaTomTom.url("ABC", List.of(new double[]{39.9, 32.85, 90}, new double[]{39.901, 32.851, -1}));
		assertTrue(u, u.startsWith("https://api.tomtom.com/snapToRoads/1?key=ABC&points="));
		// TomTom sırası boylam,enlem; noktalar ; ile; yönü olmayan boş
		String pts = java.net.URLDecoder.decode(u.replaceAll(".*points=([^&]*).*", "$1"), "UTF-8");
		assertEquals("32.850000,39.900000;32.851000,39.901000", pts);
		assertEquals("90;", java.net.URLDecoder.decode(u.replaceAll(".*headings=([^&]*).*", "$1"), "UTF-8"));
		assertEquals(YoldaTomTom.FIELDS, java.net.URLDecoder.decode(u.replaceAll(".*fields=([^&]*).*", "$1"), "UTF-8"));
		assertTrue(u.endsWith("&vehicleType=PassengerCar&measurementSystem=metric"));
	}

	@Test
	public void yanit() throws Exception {
		String js = "{\"route\":[{\"properties\":{\"id\":\"a\",\"speedLimits\":{\"value\":50,\"unit\":\"kmph\",\"type\":\"Maximum\"}}},"
				+ "{\"properties\":{\"id\":\"b\",\"speedLimits\":{\"value\":82,\"unit\":\"kmph\",\"type\":\"Maximum\"}}}],"
				+ "\"projectedPoints\":[{\"properties\":{\"routeIndex\":0,\"snapResult\":\"Matched\"}},"
				+ "{\"properties\":{\"routeIndex\":1,\"snapResult\":\"Matched\"}}]}";
		assertEquals(82, YoldaTomTom.parse(js)); // son nokta = bulunulan yer
		// son nokta yol dışı: bir öncekinin yolu
		assertEquals(50, YoldaTomTom.parse(js.replace("{\"routeIndex\":1,\"snapResult\":\"Matched\"}",
				"{\"routeIndex\":null,\"snapResult\":\"OffRoad\"}")));
		// mil, önerilen sınır (azami değil), sınır yok
		assertEquals(80, YoldaTomTom.parse(js.replace("\"value\":82,\"unit\":\"kmph\"", "\"value\":50,\"unit\":\"mph\"")));
		assertEquals(0, YoldaTomTom.parse(js.replace("\"value\":82,\"unit\":\"kmph\",\"type\":\"Maximum\"",
				"\"value\":82,\"unit\":\"kmph\",\"type\":\"Recommended\"")));
		assertEquals(0, YoldaTomTom.parse("{\"route\":[{\"properties\":{\"id\":\"a\"}}],"
				+ "\"projectedPoints\":[{\"properties\":{\"routeIndex\":0,\"snapResult\":\"Matched\"}}]}"));
		assertEquals(0, YoldaTomTom.parse("{\"detailedError\":{\"code\":\"INVALID_REQUEST\"}}"));
	}

	@Test
	public void kotaVeSiklik() {
		long now = 1_000_000;
		assertTrue(YoldaTomTom.shouldAsk(now, 0, Double.NaN, 0, 0)); // ilk istek
		assertFalse(YoldaTomTom.shouldAsk(now, now - 5_000, 1000, 0, 0)); // 20 sn dolmadı
		assertFalse(YoldaTomTom.shouldAsk(now, now - 60_000, 150, 0, 0)); // 400 m gidilmedi
		assertTrue(YoldaTomTom.shouldAsk(now, now - 60_000, 450, 0, 0));
		assertFalse(YoldaTomTom.shouldAsk(now, 0, Double.NaN, YoldaTomTom.DAILY_MAX, 0)); // günlük sınır
		assertFalse(YoldaTomTom.shouldAsk(now, 0, Double.NaN, 0, now + 1)); // 429 sonrası bekleme
		// günlük sınır ücretsiz kotanın (2.500) altında
		assertTrue(YoldaTomTom.DAILY_MAX < 2500);
	}

	@Test
	public void gecerlilik() {
		long now = 10_000_000;
		assertTrue(YoldaTomTom.valid(now, now - 60_000, 200));
		assertFalse(YoldaTomTom.valid(now, now - 6 * 60_000L, 200)); // 5 dk geçti
		assertFalse(YoldaTomTom.valid(now, now - 60_000, 700)); // 600 m uzaklaşıldı
		assertFalse(YoldaTomTom.valid(now, 0, 0)); // hiç alınmadı
	}
}
