package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

/** Podcast: dizin araması, RSS bölümleri, süre; sesli komut. */
public class YoldaPodcastTest {
	@Test
	public void aramaVeBolumler() throws Exception {
		List<YoldaPodcast.Show> l = YoldaPodcast.parseSearch("{\"results\":[{\"collectionName\":\"Fularsız Entellik\","
				+ "\"artistName\":\"Ahmet\",\"feedUrl\":\"https://f/rss\",\"artworkUrl100\":\"https://f/a.jpg\"},{\"collectionName\":\"Yok\"}]}");
		assertEquals(1, l.size()); // RSS'i olmayan atlanır
		assertEquals("https://f/rss", l.get(0).feed());
		String rss = "<rss><channel><title>P</title><item><title><![CDATA[Bölüm 2 &amp; final]]></title>"
				+ "<enclosure url=\"https://f/2.mp3\" type=\"audio/mpeg\"/><pubDate>Tue, 18 Nov 2026 06:00:00 +0000</pubDate>"
				+ "<itunes:duration>1:02:03</itunes:duration></item><item><title>Ses yok</title></item>"
				+ "<item><title>Bölüm 1</title><enclosure url=\"https://f/1.mp3\"/><itunes:duration>2710</itunes:duration></item></channel></rss>";
		List<YoldaPodcast.Episode> e = YoldaPodcast.parseFeed(rss);
		assertEquals(2, e.size());
		assertEquals("Bölüm 2 & final", e.get(0).title());
		assertEquals("https://f/2.mp3", e.get(0).audio());
		assertEquals("Tue, 18 Nov 2026", e.get(0).date());
		assertEquals(3723, e.get(0).durSec());
		assertEquals("1 sa 2 dk", YoldaPodcast.durText(3723));
		assertEquals("45 dk", YoldaPodcast.durText(2710));
	}

	@Test
	public void sesliKomut() {
		assertEquals(YoldaIntents.Kind.PODCAST, YoldaIntents.parse("fularsız entellik podcastini aç").kind());
		assertEquals("fularsız entellik", YoldaIntents.parse("fularsız entellik podcastini aç").arg());
		assertNull(YoldaIntents.parse("podcast dinle").arg());
	}
}
