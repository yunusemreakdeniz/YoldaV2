package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Akaryakıt: OpenVan.camp yanıtı, değişim oku, yol maliyeti. */
public class YoldaFuelTest {
	@Test
	public void turkiyeOkunur() throws Exception {
		String json = "{\"success\":true,\"data\":{\"DE\":{\"prices\":{\"gasoline\":1.8}},"
				+ "\"TR\":{\"currency\":\"TRY\",\"prices\":{\"gasoline\":71.0886,\"diesel\":95.1446,\"lpg\":50.7367,\"cng\":null},"
				+ "\"price_changes\":{\"gasoline\":-4.5055,\"diesel\":-0.9349,\"lpg\":0},\"fetched_at\":\"2026-11-15T18:28:46+03:00\"}}}";
		YoldaFuel.Prices p = YoldaFuel.parse(json);
		assertNotNull(p);
		assertEquals(71.0886, p.of("gasoline"), 1e-6);
		assertEquals(95.1446, p.of("diesel"), 1e-6);
		assertEquals(50.7367, p.of("lpg"), 1e-6);
		assertEquals("▼ 4,51", YoldaFuel.change(p.changeOf("gasoline")));
		assertEquals("", YoldaFuel.change(p.changeOf("lpg")));
		assertEquals("71,09 ₺", YoldaFuel.money(p.of("gasoline")));
		assertNull(YoldaFuel.parse("{\"data\":{}}"));
	}

	@Test
	public void maliyet() {
		assertEquals(497.62, YoldaFuel.cost(100_000, 7, 71.0886), 0.01);
		assertEquals(248.81, YoldaFuel.cost(50_000, 7, 71.0886), 0.01);
		assertTrue(Double.isNaN(YoldaFuel.cost(0, 7, 71)));
		assertEquals("—", YoldaFuel.money(Double.NaN));
		assertEquals("▲ 1,20", YoldaFuel.change(1.2));
	}
}
