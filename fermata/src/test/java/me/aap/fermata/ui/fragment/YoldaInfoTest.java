package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

/** Bilgi kartları: TCMB kurları, altın, namaz vakitleri, RSS haberleri ve sesli komutlar. */
public class YoldaInfoTest {
	@Test
	public void tcmbKurlari() {
		String xml = "<Tarih_Date><Currency CrossOrder=\"0\" Kod=\"USD\" CurrencyCode=\"USD\"><Unit>1</Unit><Isim>ABD DOLARI</Isim>"
				+ "<CurrencyName>US DOLLAR</CurrencyName><ForexBuying>34.4848</ForexBuying><ForexSelling>34.5469</ForexSelling></Currency>"
				+ "<Currency CrossOrder=\"9\" Kod=\"JPY\" CurrencyCode=\"JPY\"><Unit>100</Unit><Isim>JAPON YENİ</Isim>"
				+ "<ForexBuying>22.1</ForexBuying><ForexSelling>22.3</ForexSelling></Currency>"
				+ "<Currency CrossOrder=\"9\" Kod=\"EUR\" CurrencyCode=\"EUR\"><Unit>1</Unit><Isim>EURO</Isim>"
				+ "<ForexBuying>37.0</ForexBuying><ForexSelling>37.1</ForexSelling></Currency></Tarih_Date>";
		List<YoldaInfo.Rate> l = YoldaInfo.parseTcmb(xml);
		assertEquals(2, l.size()); // yalnız dolar, euro, sterlin
		assertEquals("Dolar", l.get(0).name());
		assertEquals(34.5469, l.get(0).sell(), 1e-9);
		assertEquals("Euro", l.get(1).name());
	}

	@Test
	public void altinVeSayiBicimi() throws Exception {
		assertEquals(2950.12, YoldaInfo.trNum("2.950,12"), 1e-9);
		assertEquals(34.5, YoldaInfo.trNum("34.5"), 1e-9);
		assertEquals(4800, YoldaInfo.trNum("4.800"), 1e-9);
		assertEquals(34.5469, YoldaInfo.trNum("34.5469"), 1e-9);
		String js = "{\"gram-altin\":{\"Alış\":\"2.950,12\",\"Satış\":\"2.951,40\"},\"ceyrek-altin\":{\"Alış\":\"4.800\",\"Satış\":\"4.900,5\"}}";
		List<YoldaInfo.Rate> l = YoldaInfo.parseGold(js);
		assertEquals(2, l.size());
		assertEquals("Gram altın", l.get(0).name());
		assertEquals(2951.40, l.get(0).sell(), 1e-9);
		assertEquals("2.951 ₺", YoldaInfo.money(2951.4));
		assertEquals("34,55 ₺", YoldaInfo.money(34.5469));
	}

	@Test
	public void namazVakitleri() throws Exception {
		String js = "{\"data\":{\"timings\":{\"Fajr\":\"05:58\",\"Sunrise\":\"07:28\",\"Dhuhr\":\"12:30\",\"Asr\":\"15:03\","
				+ "\"Maghrib\":\"17:22 (+03)\",\"Isha\":\"18:46\"}}}";
		List<YoldaInfo.Prayer> l = YoldaInfo.parsePrayer(js);
		assertEquals(6, l.size());
		assertEquals("Akşam", l.get(4).name());
		assertEquals("17:22", l.get(4).time());
		YoldaInfo.Prayer n = YoldaInfo.next(l, 16 * 60 + 10);
		assertEquals("Akşam", n.name());
		assertEquals("1 sa 12 dk", YoldaInfo.left(n, 16 * 60 + 10));
		assertEquals("İmsak", YoldaInfo.next(l, 23 * 60).name()); // gece: ertesi günün imsakı
		assertEquals("7 sa 58 dk", YoldaInfo.left(YoldaInfo.next(l, 22 * 60), 22 * 60));
	}

	@Test
	public void rssHaberleri() {
		String x = "<rss><channel><title>TRT</title><item><title><![CDATA[Son dakika: Deprem &amp; artçı]]></title>"
				+ "<link>https://x/1</link></item><item><title>İkinci haber</title><link><![CDATA[https://x/2]]></link></item>"
				+ "<item><title>  </title></item></channel></rss>";
		List<YoldaInfo.News> l = YoldaInfo.parseRss(x);
		assertEquals(2, l.size());
		assertEquals("Son dakika: Deprem & artçı", l.get(0).title());
		assertEquals("https://x/2", l.get(1).link());
		assertEquals("Son dakika: Deprem & artçı. İkinci haber", YoldaInfo.newsSpoken(l, 5));
	}

	@Test
	public void sesliKomutlar() {
		assertEquals(YoldaIntents.Kind.FX, YoldaIntents.parse("dolar kaç").kind());
		assertEquals("Dolar", YoldaIntents.parse("dolar kaç").arg());
		assertEquals("Gram altın", YoldaIntents.parse("gram altın ne kadar").arg());
		assertEquals("Çeyrek altın", YoldaIntents.parse("çeyrek altın kaç").arg());
		assertEquals(YoldaIntents.Kind.PRAYER, YoldaIntents.parse("akşam ezanı ne zaman").kind());
		assertEquals("Akşam", YoldaIntents.parse("akşam ezanı ne zaman").arg());
		assertNull(YoldaIntents.parse("namaz vakitleri").arg());
		assertEquals(YoldaIntents.Kind.NEWS, YoldaIntents.parse("haberleri oku").kind());
		assertEquals(YoldaIntents.Kind.NEWS, YoldaIntents.parse("son dakika").kind());
	}
}
