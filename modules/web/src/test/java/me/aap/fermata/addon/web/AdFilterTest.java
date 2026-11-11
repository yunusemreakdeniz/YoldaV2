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
