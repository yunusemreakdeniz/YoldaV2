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

	// ---- "Hey Yolda" uyandırma ----

	@Test
	public void heyYolda() {
		// gerçek (sentetik) "hey yolda" kayıtlarının Vosk sonucu: model "hey"i duymaz, tek "yolda" verir
		String real1 = "{\"result\" : [{\"conf\" : 0.938070, \"end\" : 0.720000, \"start\" : 0.300000, \"word\" : \"yolda\"}], \"text\" : \"yolda\"}";
		String real2 = "{\"result\" : [{\"conf\" : 0.991270, \"end\" : 1.290000, \"start\" : 0.720000, \"word\" : \"yolda\"}], \"text\" : \"yolda\"}";
		assertTrue(YoldaWake.isWake(real1));
		assertTrue(YoldaWake.isWake(real2));
		assertTrue(YoldaWake.isWake("{\"text\" : \"hey yolda\"}"));
		assertTrue(YoldaWake.isWake("{\"text\" : \"hey yol da\"}"));
		assertTrue(YoldaWake.isWake("{\"text\" : \"HEY YOLDA\"}"));
		assertTrue(YoldaWake.isWake("{\"text\" : \"ey yolda\"}"));
		assertTrue(YoldaWake.isWake("{\"result\" : [{\"conf\" : 1.0, \"word\" : \"hey\"}, "
				+ "{\"conf\" : 0.97, \"word\" : \"yolda\"}], \"text\" : \"hey yolda\"}"));
		// sıkı kip: ara sonuç sayılmaz
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"partial\" : \"hey yolda\"}"));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"partial\" : \"yolda\"}"));
		// sıkı kip: güven düşük (sohbette "yoldayım", "yolda kaldık" benzeri)
		org.junit.Assert.assertFalse(YoldaWake.isWake(real1.replace("0.938070", "0.71")));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"result\" : [{\"conf\" : 0.31, \"word\" : \"hey\"}, "
				+ "{\"conf\" : 0.42, \"word\" : \"yolda\"}], \"text\" : \"hey yolda\"}"));
		// sıkı kip: süre doğal değil (0.1 sn çıtırtı, 2 sn uzatılmış)
		org.junit.Assert.assertFalse(YoldaWake.isWake(real1.replace("\"end\" : 0.720000", "\"end\" : 0.400000")));
		org.junit.Assert.assertFalse(YoldaWake.isWake(real1.replace("\"end\" : 0.720000", "\"end\" : 2.300000")));
		// tek "yola" artık uyandırmaz; "hey yola" yalnız normal kipte
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"text\" : \"yola\"}"));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"text\" : \"hey yola\"}"));
		assertTrue(YoldaWake.isWake("{\"text\" : \"hey yola\"}", false, false));
		// devamı olan ya da uzun cümle içi uyandırmaz
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"text\" : \"yola [unk]\"}"));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"text\" : \"hey yolda [unk]\"}"));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"text\" : \"[unk] [unk] yolda [unk]\"}"));
		// bir şey çalarken: tek "yolda" uyandırmaz, açık "hey yolda" yüksek güvenle uyandırır
		org.junit.Assert.assertFalse(YoldaWake.isWake(real2, true));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"text\" : \"[unk] yolda\"}", true));
		assertTrue(YoldaWake.isWake("{\"result\" : [{\"conf\" : 0.99, \"word\" : \"hey\"}, {\"conf\" : 0.98, \"word\" : \"yolda\"}], "
				+ "\"text\" : \"hey yolda\"}", true));
		// normal kip (daha duyarlı): eski davranış
		assertTrue(YoldaWake.isWake("{\"partial\" : \"hey yolda\"}", false, false));
		assertTrue(YoldaWake.isWake(real1.replace("0.938070", "0.71"), false, false));
		org.junit.Assert.assertFalse(YoldaWake.isWake("{\"partial\" : \"\"}"));
		org.junit.Assert.assertFalse(YoldaWake.isWake(null));
	}

	@Test
	public void heyYoldaTekBasina() {
		// sohbetin ortasında: önceki konuşmadan 2 sn geçmeden "yolda" uyandırmaz (sıkı kip)
		org.junit.Assert.assertFalse(YoldaWake.isolated(10_000, 9_000, true));
		assertTrue(YoldaWake.isolated(10_000, 7_500, true));
		assertTrue(YoldaWake.isolated(10_000, 9_900, false));
		assertTrue(YoldaWake.hasSpeech("{\"text\" : \"[unk] [unk]\"}"));
		org.junit.Assert.assertFalse(YoldaWake.hasSpeech("{\"text\" : \"\"}"));
		org.junit.Assert.assertFalse(YoldaWake.hasSpeech("{\"partial\" : \"yolda\"}"));
	}

	// ---- radyo sesli komutu ----

	@Test
	public void radyoKomutu() {
		assertEquals("kral fm", YoldaVoice.radioQuery("kral fm aç"));
		assertEquals("joy türk", YoldaVoice.radioQuery("joy türk radyosunu aç"));
		assertEquals("metro fm", YoldaVoice.radioQuery("radyo metro fm"));
		assertNull(YoldaVoice.radioQuery("trt 1 aç"));
		assertNull(YoldaVoice.radioQuery("radyo aç"));
	}

	// ---- tanı günlüğü: önceki kapanış nedenleri ----

	@Test
	public void kapanisNedenleri() {
		assertEquals("BELLEK YETMEDİ (sistem kapattı)", YoldaLog.reasonName(3));
		assertEquals("ÇÖKME (Java)", YoldaLog.reasonName(4));
		assertEquals("YEREL ÇÖKME (native)", YoldaLog.reasonName(5));
		assertEquals("YANIT VERMEDİ (ANR)", YoldaLog.reasonName(6));
		assertEquals("bilinmiyor (99)", YoldaLog.reasonName(99));
	}

	private static YtMusic.Track track(String url) {
		return new YtMusic.Track(url, "t", "a", null, 0);
	}
}
