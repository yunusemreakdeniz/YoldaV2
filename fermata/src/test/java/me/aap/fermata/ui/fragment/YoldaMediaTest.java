package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Medya kartı: süre yazısı, dosya adı. */
public class YoldaMediaTest {
	@Test
	public void sureVeAd() {
		assertEquals("4:05", YoldaMedia.dur(245_000));
		assertEquals("1:02:03", YoldaMedia.dur(3_723_000));
		assertEquals("0:00", YoldaMedia.dur(0));
		assertEquals("VID_20261119", YoldaMedia.stripExt("VID_20261119.mp4"));
		assertEquals("adsız", YoldaMedia.stripExt("adsız"));
	}
}
