package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** TomTom canlı trafik süresi ve Open-Meteo hava kalitesi ayrıştırma. */
public class YoldaLiveApisTest {
	@Test
	public void tomTomRota() throws Exception {
		String js = "{\"routes\":[{\"summary\":{\"lengthInMeters\":18250,\"travelTimeInSeconds\":1860,"
				+ "\"trafficDelayInSeconds\":372,\"noTrafficTravelTimeInSeconds\":1500}}]}";
		YoldaTraffic.Eta e = YoldaTraffic.parseTomTom(js);
		assertEquals(31, e.minutes());
		assertEquals(6, e.delayMin());
		assertTrue(e.live());
		assertEquals("31 dk · 18 km", YoldaTraffic.text(e));
		assertEquals("trafik +6 dk", YoldaTraffic.note(e));
	}

	@Test
	public void tomTomAkiciVeBos() throws Exception {
		YoldaTraffic.Eta e = YoldaTraffic.parseTomTom("{\"routes\":[{\"summary\":{\"lengthInMeters\":5000,\"travelTimeInSeconds\":600,"
				+ "\"trafficDelayInSeconds\":0}}]}");
		assertEquals("trafik akıcı", YoldaTraffic.note(e));
		assertNull(YoldaTraffic.parseTomTom("{\"routes\":[]}"));
		assertNull(YoldaTraffic.parseTomTom("{\"error\":{\"description\":\"x\"}}"));
	}

	@Test
	public void osrmCanliDegil() {
		YoldaTraffic.Eta e = new YoldaTraffic.Eta(25, 18);
		assertFalse(e.live());
		assertEquals("trafik hariç tahmini", YoldaTraffic.note(e));
	}

	@Test
	public void tomTomAdresi() throws Exception {
		String u = YoldaTraffic.tomTomUrl("ab c", 41.0, 29.0, 40.5, 29.5);
		assertTrue(u.startsWith("https://api.tomtom.com/routing/1/calculateRoute/41.00000,29.00000:40.50000,29.50000/json?"));
		assertTrue(u.contains("traffic=true"));
		assertTrue(u.endsWith("key=ab+c"));
	}

	@Test
	public void havaKalitesi() throws Exception {
		assertEquals(37, YoldaWeather.parseAqi("{\"current\":{\"time\":\"2026-11-20T10:00\",\"european_aqi\":36.6}}"));
		assertEquals(-1, YoldaWeather.parseAqi("{\"current\":{\"european_aqi\":null}}"));
		assertEquals(-1, YoldaWeather.parseAqi("{}"));
		assertEquals("İyi", YoldaWeather.aqiText(12));
		assertEquals("Makul", YoldaWeather.aqiText(37));
		assertEquals("Orta", YoldaWeather.aqiText(55));
		assertEquals("Kötü", YoldaWeather.aqiText(70));
		assertEquals("Çok kötü", YoldaWeather.aqiText(95));
		assertEquals("Aşırı kötü", YoldaWeather.aqiText(140));
		assertEquals("", YoldaWeather.aqiText(-1));
	}

	@Test
	public void saatEki() {
		assertEquals("17:00'de", YoldaWeather.atHour("17:00"));
		assertEquals("16:00'da", YoldaWeather.atHour("16:00"));
		assertEquals("13:00'te", YoldaWeather.atHour("13:00"));
		assertEquals("10:00'da", YoldaWeather.atHour("10:00"));
		assertEquals("20:00'de", YoldaWeather.atHour("20:00"));
		assertEquals("00:00'da", YoldaWeather.atHour("00:00"));
		assertEquals("09:00'da", YoldaWeather.atHour("09:00"));
		assertEquals("15:00'te", YoldaWeather.atHour("15:00"));
	}

	@Test
	public void havaDegisimi() {
		java.util.List<YoldaWeather.Hour> hs = java.util.Arrays.asList(new YoldaWeather.Hour("15:00", 14, 2),
				new YoldaWeather.Hour("16:00", 13, 3), new YoldaWeather.Hour("17:00", 12, 61), new YoldaWeather.Hour("18:00", 11, 63));
		assertEquals("17:00'de yağmur", YoldaWeather.changeHint(new YoldaWeather.Now(14, 2, 10, "X", hs)));
		java.util.List<YoldaWeather.Hour> kar = java.util.Arrays.asList(new YoldaWeather.Hour("15:00", 1, 3),
				new YoldaWeather.Hour("16:00", 0, 73));
		assertEquals("16:00'da kar", YoldaWeather.changeHint(new YoldaWeather.Now(1, 3, 10, "X", kar)));
		java.util.List<YoldaWeather.Hour> diner = java.util.Arrays.asList(new YoldaWeather.Hour("15:00", 9, 61),
				new YoldaWeather.Hour("19:00", 8, 2));
		assertEquals("19:00'da diner", YoldaWeather.changeHint(new YoldaWeather.Now(9, 61, 10, "X", diner)));
		assertEquals("", YoldaWeather.changeHint(new YoldaWeather.Now(14, 2, 10, "X", hs.subList(0, 2))));
	}

	@Test
	public void gecikmeRengi() {
		assertEquals(0xFF34D399, YoldaTraffic.noteColor(new YoldaTraffic.Eta(20, 10, 0, true)));
		assertEquals(0xFFFB923C, YoldaTraffic.noteColor(new YoldaTraffic.Eta(20, 10, 6, true)));
		assertEquals(0xFFF87171, YoldaTraffic.noteColor(new YoldaTraffic.Eta(40, 10, 15, true)));
	}
}
