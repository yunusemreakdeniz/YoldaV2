package me.aap.fermata.addon.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Reklam filtresi: reklam sunucuları kesilir, video akışı ve sayfalar kesilmez. */
public class AdFilterTest {

	@Test
	public void reklamSunuculariEngellenir() {
		assertTrue(FermataWebClient.isAd("googleads.g.doubleclick.net", "/pagead/id", null));
		assertTrue(FermataWebClient.isAd("pagead2.googlesyndication.com", "/x.js", null));
		assertTrue(FermataWebClient.isAd("www.googleadservices.com", "/", null));
		assertTrue(FermataWebClient.isAd("imasdk.googleapis.com", "/js/sdkloader/ima3.js", null));
		assertTrue(FermataWebClient.isAd("AD.DOUBLECLICK.NET", "/", null));
	}

	@Test
	public void youtubeReklamUclariEngellenir() {
		assertTrue(FermataWebClient.isAd("www.youtube.com", "/pagead/viewthroughconversion/1", null));
		assertTrue(FermataWebClient.isAd("m.youtube.com", "/api/stats/ads", "ver=2"));
		assertTrue(FermataWebClient.isAd("www.youtube.com", "/get_midroll_info", "ei=1"));
	}

	@Test
	public void reklamDosyalariHerSitedeEngellenir() {
		assertTrue(FermataWebClient.isAd("www.haber-sitesi.com.tr", "/static/js/prebid8.12.0.js", null));
		assertTrue(FermataWebClient.isAd("cdn.example.com", "/pagead/js/adsbygoogle.js", "client=ca-pub-1"));
		assertTrue(FermataWebClient.isAd("cdn.example.com", "/gampad/pubads_impl_2026.js", null));
		assertTrue(FermataWebClient.isAd("adservice.google.com.tr", "/adsid/integrator.js", null));
		assertTrue(FermataWebClient.isAd("s0.2mdn.net", "/videoplayback/x.mp4", null));
		// "ads" geçen sıradan dosyalar ve sayfalar kesilmez
		assertFalse(FermataWebClient.isAd("www.example.com", "/uploads/headset.js", null));
		assertFalse(FermataWebClient.isAd("www.example.com", "/roads/map.js", null));
		assertFalse(FermataWebClient.isAd("www.example.com", "/downloads/", null));
	}

	@Test
	public void gorselTemizlikGecerliCss() {
		// tırnak karışmasın (betik içine gömülüyor) ve her kural gizlemeyle bitsin
		assertFalse(FermataWebClient.COSMETIC_CSS.contains("\""));
		assertTrue(FermataWebClient.COSMETIC_CSS.endsWith("{display:none!important;height:0!important;min-height:0!important}"));
		assertTrue(FermataWebClient.COSMETIC_JS.contains("__yoldaCss"));
	}

	@Test
	public void icerikEngellenmez() {
		assertFalse(FermataWebClient.isAd("m.youtube.com", "/watch", "v=abc"));
		assertFalse(FermataWebClient.isAd("music.youtube.com", "/", null));
		assertFalse(FermataWebClient.isAd("rr3---sn-abc.googlevideo.com", "/videoplayback", "itag=140"));
		assertFalse(FermataWebClient.isAd("i.ytimg.com", "/vi/x/hqdefault.jpg", null));
		assertFalse(FermataWebClient.isAd("www.youtube.com", "/youtubei/v1/player", null));
		assertFalse(FermataWebClient.isAd("tv-trt1.medya.trt.com.tr", "/master.m3u8", null));
		assertFalse(FermataWebClient.isAd(null, "/", null));
	}

	@Test
	public void benzerAdliSunucuKarismaz() {
		// "notdoubleclick.net" doubleclick.net'in alt alanı değil
		assertFalse(FermataWebClient.isAd("notdoubleclick.net", "/", null));
	}
}
