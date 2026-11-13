package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.fragment.YoldaIntents.Intent;
import me.aap.fermata.ui.fragment.YoldaIntents.Kind;

/** Hey Yolda: söylenen cümle → niyet. Arabada söylenebilecek gerçek cümlelerle. */
public class YoldaIntentsTest {

	private static Intent p(String s) {
		return YoldaIntents.parse(s);
	}

	private static void kind(Kind k, String s) {
		Intent i = p(s);
		assertTrue("“" + s + "” → " + i, (i != null) && (i.kind() == k));
	}

	private static void kind(Kind k, String arg, String s) {
		Intent i = p(s);
		assertTrue("“" + s + "” → " + i, (i != null) && (i.kind() == k));
		assertEquals("“" + s + "”", arg, i.arg());
	}

	private static void num(Kind k, int n, String s) {
		Intent i = p(s);
		assertTrue("“" + s + "” → " + i, (i != null) && (i.kind() == k));
		assertEquals("“" + s + "”", n, i.num());
	}

	// ---- temizlik ----

	@Test
	public void clean_uyandirmaVeNezaket() {
		assertEquals("trt haber aç", YoldaIntents.clean("Hey Yolda, TRT Haber aç lütfen."));
		assertEquals("trt haber aç", YoldaIntents.clean("yolda trt haber açar mısın"));
		assertEquals("tarkan çal", YoldaIntents.clean("Lütfen Tarkan çalar mısın?"));
		assertEquals("bir daha", YoldaIntents.clean("bir daha"));
		assertEquals("", YoldaIntents.clean(null));
	}

	// ---- oynatma ----

	@Test
	public void oynatma() {
		kind(Kind.PAUSE, "duraklat");
		kind(Kind.PAUSE, "Hey Yolda dur");
		kind(Kind.PLAY, "devam et");
		kind(Kind.NEXT, "sonraki şarkı");
		kind(Kind.NEXT, "atla");
		kind(Kind.NEXT, "başka kanal");
		kind(Kind.PREV, "önceki kanal");
		kind(Kind.STOP, "radyoyu kapat");
		kind(Kind.STOP, "kapat");
		kind(Kind.STOP, "oynatıcıyı kapat");
		kind(Kind.RESUME, "kaldığım yerden devam et");
		kind(Kind.REPEAT, "bir daha");
		kind(Kind.HELP, "ne yapabilirsin");
	}

	// ---- ses ----

	@Test
	public void ses() {
		kind(Kind.VOL_UP, "sesi aç");
		kind(Kind.VOL_UP, "sesi biraz aç");
		kind(Kind.VOL_UP, "sesi yükselt");
		kind(Kind.VOL_DOWN, "sesi kıs");
		kind(Kind.VOL_DOWN, "sesi biraz azalt");
		kind(Kind.MUTE, "sustur");
		kind(Kind.MUTE, "sessize al");
		num(Kind.VOL_SET, 50, "sesi yüzde elli yap");
		num(Kind.VOL_SET, 30, "ses yüzde 30");
		num(Kind.VOL_SET, 100, "sesi sonuna kadar aç");
		num(Kind.VOL_SET, 50, "sesi yarıya indir");
		num(Kind.VOL_SET, 75, "ses seviyesini yetmiş beş yap".replace("yetmiş beş", "75"));
		num(Kind.VOL_SET, 35, "sesi yüzde otuz beş yap");
		num(Kind.VOL_SET, 100, "ses on beş"); // telefon ölçeği 0-15
	}

	// ---- uyku zamanlayıcısı ----

	@Test
	public void uyku() {
		num(Kind.SLEEP, 30, "otuz dakika sonra kapat");
		num(Kind.SLEEP, 30, "yarım saat sonra kapat");
		num(Kind.SLEEP, 60, "bir saat sonra kapat");
		num(Kind.SLEEP, 120, "2 saat sonra kapat");
		num(Kind.SLEEP, 45, "kırk beş dakika sonra dursun");
		num(Kind.SLEEP, 20, "uyku zamanlayıcısı 20 dakika");
		num(Kind.SLEEP, 30, "uyku zamanlayıcısını kur");
		kind(Kind.SLEEP_CANCEL, "zamanlayıcıyı iptal et");
		assertEquals(15, YoldaIntents.minutes("çeyrek saat"));
		assertEquals(-1, YoldaIntents.minutes("biraz"));
	}

	// ---- bilgi ----

	@Test
	public void bilgi() {
		kind(Kind.TIME, "saat kaç");
		kind(Kind.WEATHER, "hava nasıl");
		kind(Kind.WEATHER, "bugün hava nasıl olacak");
		kind(Kind.WEATHER, "dışarısı kaç derece");
		kind(Kind.WHAT_PLAYING, "ne çalıyor");
		kind(Kind.WHAT_PLAYING, "bu hangi şarkı");
	}

	// ---- favoriler, tema, yanıt ----

	@Test
	public void favoriTemaYanit() {
		kind(Kind.FAV_ADD, "favorilere ekle");
		kind(Kind.FAV_ADD, "bu kanalı favorilere ekle");
		kind(Kind.FAV_OPEN, "kral pop", "favorilerimden kral pop'u aç");
		kind(Kind.FAV_OPEN, "trt haber", "favorilerden trt haberi aç");
		kind(Kind.THEME_NEXT, "temayı değiştir");
		kind(Kind.THEME_AUTO, "otomatik tema");
		kind(Kind.SPEAK_OFF, "sesli yanıtı kapat");
		kind(Kind.SPEAK_ON, "sesli yanıtı aç");
	}

	// ---- navigasyon ----

	@Test
	public void navigasyon() {
		kind(Kind.NAV_HOME, "eve git");
		kind(Kind.NAV_HOME, "beni evime götür");
		kind(Kind.NAV_WORK, "işe git");
		kind(Kind.NAV_TO, "taksim", "Taksim'e git");
		kind(Kind.NAV_TO, "ankara", "Ankara'ya yol tarifi");
		kind(Kind.NAV_TO, "kadıköy", "kadıköy'e navigasyon başlat");
		kind(Kind.NAV_NEARBY, "benzin istasyonu", "en yakın benzinlik");
		kind(Kind.NAV_NEARBY, "benzin istasyonu", "en yakın benzinliğe git");
		kind(Kind.NAV_NEARBY, "eczane", "en yakın eczaneye götür");
		kind(Kind.NAV_NEARBY, "hastane", "en yakın hastane nerede");
		kind(Kind.NAV_NEARBY, "market", "yakındaki marketi göster");
		kind(Kind.NAV_NEARBY, "nöbetçi eczane", "en yakın nöbetçi eczane");
		kind(Kind.NAV_NEARBY, "otopark", "en yakın otoparka git");
		// Uygulama adı hedef değil
		kind(Kind.OPEN_APP, "youtube", "YouTube'a git");
	}

	// ---- ekranlar ----

	@Test
	public void ekranlar() {
		kind(Kind.OPEN_APP, "music", "müzik aç");
		kind(Kind.OPEN_APP, "youtube", "youtube aç");
		kind(Kind.OPEN_APP, "pharmacy", "nöbetçi eczaneler");
		kind(Kind.OPEN_APP, "nav", "haritayı aç");
		kind(Kind.OPEN_APP, "channels", "radyolar");
		kind(Kind.OPEN_APP, "web", "tarayıcıyı aç".replace("tarayıcıyı", "tarayıcı"));
		kind(Kind.OPEN_APP, "favs", "favorilerim");
		kind(Kind.HOME, "ana sayfaya dön");
		kind(Kind.HOME, "ana ekran");
		kind(Kind.BACK, "geri dön");
	}

	// ---- içerik ----

	@Test
	public void icerik() {
		kind(Kind.YT_SEARCH, "trt haber", "YouTube'da TRT Haber aç");
		kind(Kind.YT_SEARCH, "kedi videoları", "youtubeda kedi videoları ara");
		kind(Kind.YT_SEARCH, "galatasaray", "galatasaray videosunu aç");
		kind(Kind.SONG, "tarkan", "Tarkan çal");
		kind(Kind.SONG, "sezen aksu", "Sezen Aksu şarkılarını dinle");
		kind(Kind.SONG, "Türkçe pop", "müzik çal");
		kind(Kind.RADIO_SEARCH, "kral fm", "Kral FM aç");
		kind(Kind.RADIO_SEARCH, "joy türk", "Joy Türk radyosunu çal");
		kind(Kind.CHANNEL, "trt haber", "TRT Haber'i aç");
		kind(Kind.CHANNEL, "trt haber", "hey yolda trt haber açar mısın");
		kind(Kind.CHANNEL, "show tv", "show tv izle");
		kind(Kind.CHANNEL, "a spor", "A Spor");
		assertNull(p(""));
		assertNull(p("a"));
	}

	// ---- kanal adı eşleşmesi ----

	@Test
	public void kanalPuanlari() {
		assertEquals(100, YoldaIntents.channelScore("TRT Haber", "trt haber"));
		assertEquals(100, YoldaIntents.channelScore("Show TV", "şov tivi"));
		assertEquals(100, YoldaIntents.channelScore("ATV", "a tv"));
		assertEquals(100, YoldaIntents.channelScore("Kanal D", "kanal di"));
		assertEquals(100, YoldaIntents.channelScore("CNN Türk", "cnn turk"));
		assertTrue(YoldaIntents.channelScore("TRT Haber", "trt haber canlı") >= 70);
		assertTrue(YoldaIntents.channelScore("TRT Haber HD", "trt haber") >= 80);
		assertTrue("küçük telaffuz farkı", YoldaIntents.channelScore("Habertürk", "haber türkk") >= 50);
		assertEquals(0, YoldaIntents.channelScore("TRT 1", "kral pop"));
		assertEquals(0, YoldaIntents.channelScore("TV8", "tv"));
	}

	@Test
	public void radar() {
		kind(Kind.RADAR_OFF, "radarları kapat");
		kind(Kind.RADAR_OFF, "radar uyarılarını kapat");
		kind(Kind.RADAR_ON, "radarları aç");
		kind(Kind.RADAR_ON, "radar uyarısını aç");
		kind(Kind.RADAR_INFO, "önde radar var mı");
		kind(Kind.RADAR_INFO, "hız sınırı kaç");
		kind(Kind.RADAR_INFO, "en yakın radar nerede");
		kind(Kind.RADAR_ADD, "burada radar var");
		kind(Kind.RADAR_ADD, "buraya radar ekle");
		kind(Kind.OPEN_APP, "radar", "radar ekranı");
	}

	@Test
	public void sayilar() {
		assertEquals(35, YoldaIntents.number("otuz beş"));
		assertEquals(90, YoldaIntents.number("doksan"));
		assertEquals(7, YoldaIntents.number("7"));
		assertEquals(-1, YoldaIntents.number("hiç"));
	}
}
