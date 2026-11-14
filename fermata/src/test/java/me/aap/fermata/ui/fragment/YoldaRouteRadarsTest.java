package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

/**
 * İçişleri Bakanlığı rota yanıtının ayrıştırılması. Örnek, bakanlık sayfasının kodunun okuduğu alanlarla kurulmuştur
 * (Radars: x, y, activity, owner; SpeedTunnels: activity, startLatY / startLonX, endLatY / endLonX).
 */
public class YoldaRouteRadarsTest {
	private static final String SAMPLE = "{\"success\":true,\"data\":{\"FromDistrict\":\"Kadıköy\",\"ToDistrict\":\"Çankaya\","
			+ "\"RadarCount\":3,\"ControlPointCount\":4,\"CorridorCount\":1,"
			+ "\"Coordinates\":[{\"x\":29.03,\"y\":40.99},{\"x\":32.85,\"y\":39.92}],"
			+ "\"Radars\":[{\"x\":29.5,\"y\":40.8,\"activity\":3,\"owner\":2},"
			+ "{\"x\":30.1,\"y\":40.7,\"activity\":3,\"owner\":1},"     // başka birimin: sayfada gösterilmiyor
			+ "{\"x\":31.0,\"y\":40.5,\"activity\":1,\"owner\":2},"     // etkin değil
			+ "{\"x\":32.0,\"y\":40.1,\"activity\":3,\"owner\":2}],"
			+ "\"SpeedTunnels\":[{\"name\":\"Bolu Dağı\",\"activity\":3,\"startLatY\":40.75,\"startLonX\":31.4,"
			+ "\"endLatY\":40.72,\"endLonX\":31.55,\"coordinates\":[]},"
			+ "{\"name\":\"Kapalı\",\"activity\":0,\"startLatY\":40.6,\"startLonX\":31.9,\"endLatY\":40.55,\"endLonX\":32.0}]}}";

	@Test
	public void rotaYaniti() throws Exception {
		YoldaRouteRadars.Route r = YoldaRouteRadars.parse(new JSONObject(SAMPLE));
		assertEquals("Kadıköy", r.from());
		assertEquals("Çankaya", r.to());
		long radars = r.cams().stream().filter(c -> c.kind() == 'r').count();
		long corr = r.cams().stream().filter(c -> c.kind() == 'a').count();
		assertEquals("yalnız etkin ve emniyetin radarları", 2, radars);
		assertEquals("etkin koridorun başı ve sonu", 2, corr);
		YoldaRadarCore.Cam first = r.cams().get(0);
		assertEquals(40.8, first.lat(), 1e-9);
		assertEquals(29.5, first.lon(), 1e-9);
		assertTrue("OSM kimlikleriyle çakışmasın", first.id() < 0);
	}

	@Test
	public void hataYaniti() throws Exception {
		try {
			YoldaRouteRadars.parse(new JSONObject("{\"success\":false,\"message\":\"Veri alınamadı\"}"));
			fail();
		} catch (IllegalStateException ex) {
			assertEquals("Veri alınamadı", ex.getMessage());
		}
	}

	@Test
	public void yardimcilar() {
		assertEquals(41.01, YoldaRouteRadars.parseLatLon("41.010000,28.980000")[0], 1e-9);
		assertNull(YoldaRouteRadars.parseLatLon("Taksim"));
		assertEquals("istanbul", YoldaRouteRadars.fold("İstanbul"));
		assertEquals("canakkale", YoldaRouteRadars.fold("Çanakkale"));
		assertEquals(YoldaRouteRadars.id('r', 40.8, 29.5), YoldaRouteRadars.id('r', 40.8, 29.5));
		assertNotEquals(YoldaRouteRadars.id('r', 40.8, 29.5), YoldaRouteRadars.id('r', 40.8, 29.6));
	}
}
