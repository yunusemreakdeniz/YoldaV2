package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Son depremler kartı: EMSC yanıtı ayrıştırma, bölge adları, metin ve sesli uyarı kuralı. */
public class YoldaQuakeTest {
	// İstanbul'dan: Sındırgı ~200 km, Marmara ~40 km, Erzurum ~900 km (dışarıda kalır)
	private static final String JS = "{\"type\":\"FeatureCollection\",\"features\":["
			+ "{\"id\":\"a\",\"properties\":{\"unid\":\"q1\",\"time\":\"2026-11-20T09:00:00.4Z\",\"lat\":39.24,\"lon\":28.17,"
			+ "\"depth\":7.0,\"mag\":4.3,\"flynn_region\":\"WESTERN TURKEY\"}},"
			+ "{\"id\":\"b\",\"properties\":{\"unid\":\"q2\",\"time\":\"2026-11-20T10:00:00Z\",\"lat\":40.80,\"lon\":28.90,"
			+ "\"depth\":9.0,\"mag\":2.8,\"flynn_region\":\"SEA OF MARMARA\"}},"
			+ "{\"id\":\"c\",\"properties\":{\"unid\":\"q3\",\"time\":\"2026-11-20T10:30:00Z\",\"lat\":39.90,\"lon\":41.27,"
			+ "\"depth\":5.0,\"mag\":3.1,\"flynn_region\":\"EASTERN TURKEY\"}}]}";

	@Test
	public void ayristirma() throws Exception {
		List<YoldaQuake.Quake> l = YoldaQuake.parse(JS, 41.01, 28.98);
		assertEquals("300 km dışı atılır", 2, l.size());
		assertEquals("q2", l.get(0).id()); // en yeni önce
		assertEquals("Marmara Denizi", l.get(0).region());
		assertEquals("Batı Türkiye", l.get(1).region());
		assertTrue(l.get(1).distKm() > 150 && l.get(1).distKm() < 250);
		assertEquals(7.0, l.get(1).depthKm(), 0.01);
	}

	@Test
	public void zamanVeMetin() throws Exception {
		long t = YoldaQuake.time("2026-11-20T10:00:00Z");
		assertEquals(t, YoldaQuake.time("2026-11-20T10:00:00.123Z"));
		assertEquals(0, YoldaQuake.time("bozuk"));
		assertEquals("12 dk önce", YoldaQuake.ago(t, t + 12 * 60_000L));
		assertEquals("3 sa önce", YoldaQuake.ago(t, t + 190 * 60_000L));
		assertEquals("az önce", YoldaQuake.ago(t, t + 20_000L));
		YoldaQuake.Quake q = YoldaQuake.parse(JS, 41.01, 28.98).get(1);
		assertEquals("4,3", YoldaQuake.mag(q));
		assertTrue(YoldaQuake.line(q, q.timeMs() + 12 * 60_000L).matches("4,3 · Batı Türkiye · \\d+ km · 12 dk önce"));
		assertEquals(0xFFFACC15, YoldaQuake.color(q));
	}

	@Test
	public void bolgeAdlari() {
		assertEquals("Onikiadalar, Yunanistan", YoldaQuake.region("DODECANESE ISLANDS, GREECE"));
		assertEquals("Türkiye-Suriye sınırı", YoldaQuake.region("TURKEY-SYRIA BORDER REGION"));
		assertEquals("Ege Denizi", YoldaQuake.region("AEGEAN SEA"));
		assertEquals("Batı Türkiye", YoldaQuake.region("NEAR COAST OF WESTERN TURKEY"));
		assertEquals("Bilinmeyen bölge", YoldaQuake.region(""));
	}

	@Test
	public void sesliUyari() {
		long now = 1_000_000_000L;
		YoldaQuake.Quake big = new YoldaQuake.Quake("x", 5.2, 0, 0, 10, now - 5 * 60_000L, "Batı Türkiye", 120);
		YoldaQuake.Quake old = new YoldaQuake.Quake("y", 5.6, 0, 0, 10, now - 40 * 60_000L, "Batı Türkiye", 120);
		YoldaQuake.Quake small = new YoldaQuake.Quake("z", 4.4, 0, 0, 10, now - 60_000L, "Batı Türkiye", 120);
		Set<String> done = new HashSet<>();
		assertTrue(YoldaQuake.shouldAlert(big, now, done));
		assertFalse("eski", YoldaQuake.shouldAlert(old, now, done));
		assertFalse("küçük", YoldaQuake.shouldAlert(small, now, done));
		done.add("x");
		assertFalse("bir kez söylenir", YoldaQuake.shouldAlert(big, now, done));
	}

	@Test
	public void adres() {
		String u = YoldaQuake.url(41.01, 28.98, YoldaQuake.time("2026-11-20T10:00:00Z"));
		assertTrue(u, u.startsWith("https://www.seismicportal.eu/fdsnws/event/1/query?format=json&lat=41.010&lon=28.980"));
		assertTrue(u, u.contains("starttime=2026-11-19T10:00:00"));
		assertTrue(u, u.contains("maxradius=2.70"));
	}

	@Test
	public void sesliKomut() {
		assertEquals(YoldaDeck.QUAKE, YoldaDeck.typeOfSpoken("son depremler"));
	}

	@Test
	public void oneCikarma() {
		long now = 1_000_000_000L;
		YoldaQuake.Quake yeni = new YoldaQuake.Quake("a", 4.1, 0, 0, 10, now - 10 * 60_000L, "Batı Türkiye", 90);
		YoldaQuake.Quake kucuk = new YoldaQuake.Quake("b", 3.2, 0, 0, 10, now - 5 * 60_000L, "Batı Türkiye", 40);
		YoldaQuake.Quake eski = new YoldaQuake.Quake("c", 4.8, 0, 0, 10, now - 90 * 60_000L, "Batı Türkiye", 40);
		Set<String> done = new HashSet<>();
		assertEquals("a", YoldaQuake.surfaceable(List.of(kucuk, yeni, eski), now, done).id());
		done.add("a");
		assertEquals("bir kez öne gelir", null, YoldaQuake.surfaceable(List.of(kucuk, yeni, eski), now, done));
	}

	@Test
	public void kaliciKimlikler() {
		Set<String> s = YoldaQuake.decodeIds("a,b");
		assertEquals("a,b,c", YoldaQuake.encodeIds(s, "c"));
		assertEquals("b,a", YoldaQuake.encodeIds(s, "a")); // yeniden eklenen sona
		assertTrue(YoldaQuake.decodeIds(null).isEmpty());
		Set<String> big = new java.util.LinkedHashSet<>();
		for (int i = 0; i < 45; i++) big.add("x" + i);
		assertEquals(40, YoldaQuake.decodeIds(YoldaQuake.encodeIds(big, null)).size());
	}
}
