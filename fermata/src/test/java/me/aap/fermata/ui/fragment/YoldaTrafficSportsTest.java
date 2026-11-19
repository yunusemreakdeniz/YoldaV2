package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Trafik (OSRM süresi) ve spor (TheSportsDB) ayrıştırma; sesli komutlar. */
public class YoldaTrafficSportsTest {
	@Test
	public void osrm() throws Exception {
		YoldaTraffic.Eta e = YoldaTraffic.parseOsrm("{\"code\":\"Ok\",\"routes\":[{\"duration\":1520.4,\"distance\":18250.0}]}");
		assertEquals(25, e.minutes());
		assertEquals("25 dk · 18 km", YoldaTraffic.text(e));
		assertEquals("1 sa 10 dk · 9,5 km", YoldaTraffic.text(new YoldaTraffic.Eta(70, 9.5)));
		assertNull(YoldaTraffic.parseOsrm("{\"code\":\"NoRoute\"}"));
	}

	@Test
	public void spor() throws Exception {
		String js = "{\"events\":[{\"strHomeTeam\":\"Galatasaray\",\"strAwayTeam\":\"Fenerbahçe\",\"intHomeScore\":\"2\",\"intAwayScore\":\"1\","
				+ "\"dateEvent\":\"2026-11-16\",\"strTime\":\"19:00:00\"},{\"strHomeTeam\":\"Beşiktaş\",\"strAwayTeam\":\"Trabzonspor\","
				+ "\"intHomeScore\":null,\"intAwayScore\":null,\"dateEvent\":\"2026-11-22\",\"strTime\":\"20:00:00\"}]}";
		List<YoldaSports.Match> l = YoldaSports.parse(js);
		assertEquals("Galatasaray 2-1 Fenerbahçe", l.get(0).line());
		assertEquals("Beşiktaş - Trabzonspor · 11.22 20:00", l.get(1).line());
		assertTrue(l.get(0).has("fenerbahce"));
		assertTrue(!l.get(1).has("galatasaray"));
	}

	@Test
	public void sesliKomut() {
		assertEquals("home", YoldaIntents.parse("eve kaç dakika").arg());
		assertEquals("work", YoldaIntents.parse("işe ne kadar sürer").arg());
		assertEquals(YoldaIntents.Kind.SPORTS, YoldaIntents.parse("maç sonuçları").kind());
		assertEquals("galatasaray", YoldaIntents.parse("galatasaray maçı ne zaman").arg());
	}
}
