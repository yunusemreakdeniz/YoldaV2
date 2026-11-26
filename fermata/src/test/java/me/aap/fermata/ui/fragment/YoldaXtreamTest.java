package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Xtream Codes adresleri: şema eklenir, sondaki / atılır. */
public class YoldaXtreamTest {
	@Test
	public void sunucu() {
		assertEquals("http://iptv.ornek.com:8080", YoldaXtream.server(" iptv.ornek.com:8080/ "));
		assertEquals("https://a.b", YoldaXtream.server("https://a.b//"));
	}
}
