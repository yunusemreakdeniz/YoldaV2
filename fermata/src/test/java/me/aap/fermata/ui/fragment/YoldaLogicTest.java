package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.media.yt.YtMusic;

/**
 * Yolda'nın cihaz gerektirmeyen mantığı: sesli komut çözümleme, şarkı kimliği, kapak adresi, süre biçimi.
 */
public class YoldaLogicTest {

	// ---- sesli komut: şarkı isteği ----

	@Test
	public void songQuery_cal() {
		assertEquals("tarkan", YoldaVoice.songQuery("Tarkan çal"));
		assertEquals("sezen aksu", YoldaVoice.songQuery("sezen aksu çalar mısın"));
		assertEquals("duman", YoldaVoice.songQuery("duman çalsana"));
	}

	@Test
	public void songQuery_sarkisiniAc() {
		assertEquals("sezen aksu", YoldaVoice.songQuery("Sezen Aksu şarkısını aç"));
		assertEquals("mor ve ötesi", YoldaVoice.songQuery("mor ve ötesi şarkılarını dinle"));
		assertEquals("tarkan", YoldaVoice.songQuery("tarkan şarkısını çal"));
	}

	@Test
	public void songQuery_genelMuzik() {
		assertEquals("Türkçe pop", YoldaVoice.songQuery("müzik çal"));
		assertEquals("Türkçe pop", YoldaVoice.songQuery("bir şarkı çal"));
	}

	@Test
	public void songQuery_sarkiIstegiDegil() {
		assertNull(YoldaVoice.songQuery("trt haber aç"));
		assertNull(YoldaVoice.songQuery("eve git"));
		assertNull(YoldaVoice.songQuery("çal"));
		assertNull(YoldaVoice.songQuery(""));
		assertNull(YoldaVoice.songQuery(null));
	}

	@Test
	public void musicContextQuery() {
		assertEquals("duman", YoldaVoice.musicContextQuery("duman şarkılarını"));
		assertEquals("tarkan", YoldaVoice.musicContextQuery("tarkan"));
		assertEquals("sezen aksu", YoldaVoice.musicContextQuery("sezen aksu albümünü"));
		assertNull(YoldaVoice.musicContextQuery("  "));
		assertNull(YoldaVoice.musicContextQuery(null));
	}

	@Test
	public void norm_turkceVeIsaretler() {
		assertEquals("trthaber", YoldaVoice.norm("TRT Haber"));
		assertEquals("showtv", YoldaVoice.norm("Show TV"));
		assertEquals("kralpop", YoldaVoice.norm("Kral Pop!"));
		assertEquals("cnnturk", YoldaVoice.norm("CNN Türk"));
		assertEquals("ikinci", YoldaVoice.norm("İkinci"));
	}

	// ---- şarkı kimliği ----

	@Test
	public void trackId() {
		assertEquals("JJPlzNajdUs", track("https://music.youtube.com/watch?v=JJPlzNajdUs").id());
		assertEquals("abc", track("https://music.youtube.com/watch?v=abc&list=RD").id());
		assertEquals("https://x/y", track("https://x/y").id());
	}

	// ---- kapak adresi ----

	@Test
	public void bigThumb_googleusercontent() {
		assertEquals("https://lh3.googleusercontent.com/abc=w720-h720-l90-rj",
				MusicFragment.bigThumb("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"));
		assertEquals("https://yt3.ggpht.com/abc=s720",
				MusicFragment.bigThumb("https://yt3.ggpht.com/abc=s88-c-k-c0x00ffffff-no-rj"));
	}

	@Test
	public void bigThumb_ytimg() {
		assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg",
				MusicFragment.bigThumb("https://i.ytimg.com/vi/x/default.jpg"));
		assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg",
				MusicFragment.bigThumb("https://i.ytimg.com/vi/x/mqdefault.jpg"));
		assertNull(MusicFragment.bigThumb(null));
	}

	// ---- süre ve alt yazı ----

	@Test
	public void fmt() {
		assertEquals("0:00", MusicFragment.fmt(0));
		assertEquals("0:09", MusicFragment.fmt(9));
		assertEquals("4:11", MusicFragment.fmt(251));
		assertEquals("61:01", MusicFragment.fmt(3661));
	}

	@Test
	public void subtitle() {
		assertEquals("Şarkı • Mustafa Sandal • 4:11",
				MusicFragment.subtitle(new YtMusic.Track("u", "t", "Mustafa Sandal", null, 251)));
		assertEquals("Şarkı", MusicFragment.subtitle(new YtMusic.Track("u", "t", null, null, 0)));
		assertTrue(MusicFragment.subtitle(new YtMusic.Track("u", "t", "", null, 30)).endsWith("0:30"));
	}

	private static YtMusic.Track track(String url) {
		return new YtMusic.Track(url, "t", "a", null, 0);
	}
}
