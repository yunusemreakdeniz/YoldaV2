package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** Sürüş riski (saatlik tahminden): buzlanma, sis, fırtına, kuvvetli yağış ve rüzgâr. */
public class YoldaDriveWeatherTest {
	private static YoldaWeather.Hour h(String t, int temp, int code, int gust, int vis, double mm) {
		return new YoldaWeather.Hour(t, temp, code, gust, vis, mm);
	}

	@Test
	public void riskler() {
		assertEquals("Buzlanma riski", YoldaWeather.risk(h("06:00", 0, 61, 10, 9000, 0.4)));
		assertEquals("Buzlanma riski", YoldaWeather.risk(h("06:00", -2, 3, 10, 9000, 0.2)));
		assertEquals("", YoldaWeather.risk(h("06:00", -2, 0, 10, 20000, 0))); // kuru soğuk: risk yok
		assertEquals("Yoğun sis", YoldaWeather.risk(h("06:00", 8, 3, 5, 300, 0)));
		assertEquals("Yoğun sis", YoldaWeather.risk(h("06:00", 8, 45, 5, -1, -1)));
		assertEquals("Fırtına", YoldaWeather.risk(h("06:00", 15, 95, 40, 8000, 2)));
		assertEquals("Kuvvetli yağış", YoldaWeather.risk(h("06:00", 15, 63, 20, 8000, 6.5)));
		assertEquals("Kuvvetli rüzgâr", YoldaWeather.risk(h("06:00", 15, 2, 72, 20000, 0)));
		assertEquals("", YoldaWeather.risk(new YoldaWeather.Hour("06:00", 15, 2))); // eski kayıt: bilinmiyor
	}

	@Test
	public void ilkRisk() {
		YoldaWeather.Now n = new YoldaWeather.Now(5, 3, 10, "X", List.of(h("20:00", 5, 3, 10, 20000, 0),
				h("21:00", 3, 3, 10, 20000, 0), h("22:00", 1, 3, 15, 400, 0)));
		assertEquals("Yoğun sis · 22:00'de", YoldaWeather.driveAlert(n));
		YoldaWeather.Now hemen = new YoldaWeather.Now(0, 71, 10, "X", List.of(h("20:00", 0, 71, 10, 3000, 0.5)));
		assertEquals("Buzlanma riski · şimdi", YoldaWeather.driveAlert(hemen));
		assertEquals("", YoldaWeather.driveAlert(new YoldaWeather.Now(14, 2, 10, "X", List.of(h("20:00", 14, 2, 10, 20000, 0)))));
	}

	@Test
	public void sesli() {
		assertEquals("Dikkat, yoğun sis 22:00'de", YoldaWeather.spokenAlert("Yoğun sis · 22:00'de"));
		assertEquals("Dikkat, buzlanma riski şu an", YoldaWeather.spokenAlert("Buzlanma riski · şimdi"));
		assertEquals("", YoldaWeather.spokenAlert(""));
	}

	@Test
	public void sesliKomut() {
		for (String c : new String[]{"yol durumu nasıl", "yolda sis var mı", "buzlanma var mı", "sürüş uyarısı var mı", "yolda hava nasıl"}) {
			YoldaIntents.Intent it = YoldaIntents.parse(c);
			assertEquals(c, YoldaIntents.Kind.WEATHER, it.kind());
			assertEquals(c, "road", it.arg());
		}
		assertEquals(null, YoldaIntents.parse("hava nasıl").arg());
	}
}
