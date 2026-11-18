package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/** TV yedek adresleri: ad eşleştirme ve m3u okuma. */
public class YoldaChannelAltTest {
	@Test
	public void normEslesir() {
		assertEquals(YoldaChannelAlt.norm("TRT 1 HD"), YoldaChannelAlt.norm("TRT 1"));
		assertEquals(YoldaChannelAlt.norm("Show TV (1080p)"), YoldaChannelAlt.norm("Show"));
		assertEquals(YoldaChannelAlt.norm("Haber Türk"), YoldaChannelAlt.norm("Haber Turk"));
		assertEquals("trt1", YoldaChannelAlt.norm("TRT 1 [Geo-blocked]"));
	}

	@Test
	public void m3uOkunur() {
		String m3u = "#EXTM3U\n#EXTINF:-1 tvg-id=\"TRT1.tr\",TRT 1 (1080p)\nhttps://a/1.m3u8\n"
				+ "#EXTINF:-1,TRT 1 HD\nhttps://b/1.m3u8\n#EXTVLCOPT:http-user-agent=x\n#EXTINF:-1,Kanal D\nhttps://c/d.m3u8\n";
		Map<String, List<String>> m = YoldaChannelAlt.parse(m3u);
		assertEquals(List.of("https://a/1.m3u8", "https://b/1.m3u8"), m.get("trt1"));
		assertEquals(List.of("https://c/d.m3u8"), m.get("kanald"));
		assertNull(m.get("yok"));
	}
}
