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
}
