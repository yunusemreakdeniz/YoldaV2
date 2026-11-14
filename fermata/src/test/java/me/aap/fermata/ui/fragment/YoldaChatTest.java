package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.fragment.YoldaIntents.Intent;
import me.aap.fermata.ui.fragment.YoldaIntents.Kind;

/** Hey Yolda: kısa sohbet ve yeni bilgi komutları; eski komutlar sohbete kaçmamalı. */
public class YoldaChatTest {

	private static Intent p(String s) {
		return YoldaIntents.parse(s);
	}

	private static void kind(Kind k, String s) {
		Intent i = p(s);
		assertTrue("“" + s + "” → " + i, (i != null) && (i.kind() == k));
	}

	@Test
	public void sohbet() {
		for (String s : new String[]{"Merhaba", "Hey Yolda nasılsın", "teşekkürler", "sağ ol", "sen kimsin", "adın ne",
				"şaka yap", "fıkra anlat", "günaydın", "iyi geceler", "iyiyim", "canım sıkıldı", "acıktım", "uykum geldi",
				"benzinim bitiyor", "görüşürüz", "aferin", "orada mısın", "yazı tura at"})
			kind(Kind.CHAT, s);
		assertTrue(YoldaChat.answer("merhaba", 9, null).text().startsWith("Günaydın"));
		assertTrue(YoldaChat.answer("merhaba", 20, null).text().startsWith("İyi akşamlar"));
		YoldaChat.Reply r = YoldaChat.answer("acıktım", 12, null);
		assertEquals(Kind.NAV_NEARBY, r.then().kind());
		assertEquals("restoran", r.then().arg());
		assertEquals("dinlenme tesisi", YoldaChat.answer("uykum geldi", 12, null).then().arg());
		assertEquals("benzin istasyonu", YoldaChat.answer("benzinim bitiyor", 12, null).then().arg());
		assertEquals(Kind.SONG, YoldaChat.answer("sıkıldım", 12, null).then().kind());
		assertNull(YoldaChat.answer("trt haber aç", 12, null));
	}

	@Test
	public void eskiKomutlarSohbeteKacmaz() {
		kind(Kind.PAUSE, "duraklat");
		kind(Kind.STOP, "kapat");
		kind(Kind.NEXT, "sonraki");
		kind(Kind.WEATHER, "hava nasıl");
		kind(Kind.TIME, "saat kaç");
		kind(Kind.NAV_HOME, "eve git");
		kind(Kind.CALL, "annemi ara");
		kind(Kind.SLEEP, "otuz dakika sonra kapat");
		kind(Kind.RADAR_INFO, "radar var mı");
		kind(Kind.HELP, "ne yapabilirsin");
		kind(Kind.NAV_NEARBY, "en yakın benzinlik");
	}

	@Test
	public void kartaGec() {
		Intent i = p("Hey Yolda radyo kartına geç");
		assertEquals(Kind.CARD, i.kind());
		assertEquals("radyo", i.arg());
		assertEquals("hava durumu", p("hava durumu kartını göster").arg());
		assertEquals("kişiler", p("kişiler kartı").arg());
		kind(Kind.HOME, "ana kartına geç");
		YoldaDeck d = new YoldaDeck();
		assertEquals(d.indexOf(YoldaDeck.RADIO), d.find("radyo"));
		assertEquals(d.indexOf(YoldaDeck.WEATHER), d.find("hava"));
		assertEquals(d.indexOf(YoldaDeck.NAV), d.find("harita"));
		assertEquals(d.indexOf(YoldaDeck.RADAR), d.find("radar"));
		assertEquals(d.indexOf(YoldaDeck.CALLS), d.find("Kişiler"));
		assertEquals(-1, d.find("uzay gemisi"));
	}

	@Test
	public void yeniKomutlar() {
		kind(Kind.NEXT_CARD, "sonraki karta geç");
		kind(Kind.PREV_CARD, "önceki kart");
		kind(Kind.NEXT_CARD, "sağa kaydır");
		Intent o = p("radyo kartını aç");
		assertEquals(Kind.CARD_OPEN, o.kind());
		assertEquals("radyo", o.arg());
		kind(Kind.EDIT_CARDS, "kartları düzenle");
		Intent s = p("10 saniye geri sar");
		assertEquals(Kind.SEEK, s.kind());
		assertEquals(-10, s.num());
		assertEquals(15, p("ileri sar").num());
		assertEquals(60, p("bir dakika ileri").num());
		kind(Kind.SEEK_START, "başa sar");
		kind(Kind.NEXT, "ileri"); // tek "ileri" hâlâ sonraki
		kind(Kind.PREV, "geri");
		kind(Kind.BACK, "geri git");
		Intent f = p("okul uyarısını kapat");
		assertEquals(Kind.FEATURE, f.kind());
		assertEquals("zones", f.arg());
		assertEquals(0, f.num());
		assertEquals("fatigue", p("mola hatırlatıcısını aç").arg());
		assertEquals(1, p("mola hatırlatıcısını aç").num());
		assertEquals("overspeed", p("hız aşımı uyarısını kapat").arg());
		kind(Kind.RADAR_OFF, "radar uyarısını kapat"); // eski komut değişmedi
		kind(Kind.WAKE_OFF, "hey yoldayı kapat");
		kind(Kind.TEST_ALERT, "radar uyarısını dene");
		assertEquals("waze", p("waze kullan").arg());
		assertEquals("set", p("gün batımı ne zaman").arg());
		assertEquals("rise", p("güneş kaçta doğuyor").arg());
		Intent t = p("okyanus temasına geç");
		assertEquals(Kind.THEME_SET, t.kind());
		assertEquals("okyanus", t.arg());
		kind(Kind.THEME_NEXT, "temayı değiştir");
		kind(Kind.THEME_AUTO, "otomatik tema");
		kind(Kind.OPEN_APP, "sürüş ekranını aç");
	}

	@Test
	public void hesap() {
		Intent i = p("15 çarpı 4 kaç");
		assertEquals(Kind.CALC, i.kind());
		assertEquals("15 çarpı 4 = 60", i.arg());
		assertEquals("100 bölü 8 = 12,5", p("yüz bölü sekiz").arg());
		assertEquals("20 eksi 7 = 13", p("yirmi eksi yedi kaç eder").arg());
		assertEquals("Sıfıra bölünmez", YoldaIntents.calc("5 bölü 0"));
		assertNull(YoldaIntents.calc("trt 1 aç"));
	}

	@Test
	public void hatirlatici() {
		Intent i = p("10 dakika sonra su içmeyi hatırlat");
		assertEquals(Kind.TIMER, i.kind());
		assertEquals(10, i.num());
		assertEquals("su içmeyi", i.arg());
		i = p("yarım saat sonra bana haber ver");
		assertEquals(Kind.TIMER, i.kind());
		assertEquals(30, i.num());
		i = p("beş dakikalık zamanlayıcı kur");
		assertEquals(Kind.TIMER, i.kind());
		assertEquals(5, i.num());
		kind(Kind.TIMER_CANCEL, "hatırlatmayı iptal et");
	}

	@Test
	public void bilgi() {
		kind(Kind.DATE, "bugün ayın kaçı");
		kind(Kind.DATE, "bugün günlerden ne");
		kind(Kind.BATTERY, "şarjım kaç");
		kind(Kind.SPEED, "kaçla gidiyorum");
		kind(Kind.WHERE_AM_I, "neredeyim");
		Intent f = p("yarın hava nasıl");
		assertEquals(Kind.FORECAST, f.kind());
		assertEquals("yarın", f.arg());
		assertEquals("yağış", p("yağmur yağacak mı").arg());
		assertEquals("hafta", p("haftalık hava durumu").arg());
	}

	@Test
	public void vikipedi() throws Exception {
		Intent i = p("Atatürk kimdir");
		assertEquals(Kind.WIKI, i.kind());
		assertEquals("atatürk", i.arg());
		assertEquals("fotosentez", p("fotosentez nedir").arg());
		assertEquals("kapadokya", p("Kapadokya hakkında bilgi ver").arg());
		String t = YoldaAnswers.wikiParse("{\"query\":{\"pages\":[{\"title\":\"Ankara\",\"extract\":"
				+ "\"Ankara (eski adıyla Angora), Türkiye'nin başkentidir. İkinci büyük şehridir.\"}]}}");
		assertEquals("Ankara, Türkiye'nin başkentidir. İkinci büyük şehridir.", t);
		assertNull(YoldaAnswers.wikiParse("{\"batchcomplete\":true}"));
		assertNotNull(YoldaChat.answer("nasılsın", 12, null));
	}
}
