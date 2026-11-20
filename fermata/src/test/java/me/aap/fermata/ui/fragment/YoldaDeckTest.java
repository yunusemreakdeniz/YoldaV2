package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Yolda V2 kart destesi: varsayılan sıra, taşıma, kaldırma, ekleme, geri getirme, kayıt. */
public class YoldaDeckTest {

	@Test
	public void eskiAdGuncellenir() {
		YoldaDeck d = YoldaDeck.decode(new YoldaDeck().encode().replace("Sürüş", "Radar ve hız"));
		assertEquals("Radar ve hız", d.get(d.indexOf("radar")).title());
		assertTrue(d.retitle("radar", "Radar ve hız", "Sürüş"));
		assertEquals("Sürüş", d.get(d.indexOf("radar")).title());
		assertFalse(d.retitle("radar", "Radar ve hız", "Sürüş"));
	}

	@Test
	public void kaldirilanKartlarKaydi() {
		java.util.List<YoldaDeck.Card> l = java.util.List.of(new YoldaDeck.Card("tvch:1", YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null),
				new YoldaDeck.Card("url:2", YoldaDeck.URL, "Haber", "https://x", "https://x/logo.png"));
		assertEquals(l, YoldaDeck.decodeCards(YoldaDeck.encodeCards(l)));
		assertTrue(YoldaDeck.decodeCards("").isEmpty());
		assertTrue(YoldaDeck.decodeCards(null).isEmpty());
	}

	@Test
	public void varsayilanSira() {
		YoldaDeck d = new YoldaDeck();
		String[] want = {"resume", "youtube", "music", "podcast", "media", "tv", "radio", "favs", "nav", "radar", "park", "trips", "weather", "pharmacy", "fuel", "fx", "prayer", "news", "status", "charger", "notes", "quickmsg", "calendar", "carcare", "traffic", "sports", "quake", "phone", "calls",
				"mirror", "web", "eq", "settings", "add"};
		assertEquals(want.length, d.size());
		for (int i = 0; i < want.length; i++) assertEquals(want[i], d.get(i).type());
		assertEquals("Kaldığın yerden", d.get(0).title());
		assertEquals("YouTube", d.get(1).title());
		assertEquals("Sürüş", d.get(9).title());
		assertEquals("Park", d.get(10).title());
		assertEquals("Yolculuklar", d.get(11).title());
		assertEquals("Hava durumu", d.get(12).title());
		assertEquals("Nöbetçi eczane", d.get(13).title());
		assertEquals("Akaryakıt", d.get(14).title());
		assertEquals("Döviz ve altın", d.get(15).title());
		assertEquals("Namaz vakitleri", d.get(16).title());
		assertEquals("Haberler", d.get(17).title());
		assertEquals("Telefon durumu", d.get(18).title());
		assertEquals("Şarj istasyonları", d.get(19).title());
		assertEquals("Notlar", d.get(20).title());
		assertEquals("Hızlı mesaj", d.get(21).title());
		assertEquals("Takvim", d.get(22).title());
		assertEquals("Araç bakımı", d.get(23).title());
		assertEquals("Ekolayzer", d.get(31).title());
		assertEquals("Ekle", d.get(33).title());
	}

	@Test
	public void telefonSirasiAyri() {
		YoldaDeck d = new YoldaDeck();
		List<YoldaDeck.Card> all = d.cards();
		// sıra yoksa destedeki sırayla, "Ekle" yok
		List<YoldaDeck.Card> p = YoldaDeck.phoneCards(all, List.of(), java.util.Set.of());
		assertEquals(all.size() - 1, p.size());
		assertEquals("resume", p.get(0).type());
		// telefon sırası: önce verilenler, sonra kalanlar destedeki sırayla; gizlenen yok
		p = YoldaDeck.phoneCards(all, List.of("weather", "radio"), java.util.Set.of("youtube"));
		assertEquals("weather", p.get(0).type());
		assertEquals("radio", p.get(1).type());
		assertEquals("resume", p.get(2).type());
		for (YoldaDeck.Card c : p) assertFalse("gizli kart görünmemeli", "youtube".equals(c.type()));
		assertEquals(all.size() - 2, p.size());
		// deste (araç) sırası değişmedi
		assertEquals("youtube", d.get(1).type());
	}

	@Test
	public void tasima() {
		YoldaDeck d = new YoldaDeck();
		assertEquals(1, d.move(0, 1));
		assertEquals("youtube", d.get(0).type());
		assertEquals("resume", d.get(1).type());
		assertEquals(0, d.move(0, -1)); // en baştan sola gidilmez
		assertEquals(33, d.move(33, 1)); // en sondan sağa gidilmez
	}

	@Test
	public void kaldirmaVeGeriGetirme() {
		YoldaDeck d = new YoldaDeck();
		assertTrue(d.remove(d.indexOf("phone")));
		assertFalse("Ekle kaldırılamaz", d.remove(d.indexOf("add")));
		assertEquals(List.of("phone"), d.missingBuiltIns());
		int at = d.restore("phone");
		assertEquals(27, at); // depremin ardına, ekran yansıtmanın önüne
		assertTrue(d.missingBuiltIns().isEmpty());
	}

	@Test
	public void eklemeEkleKartininOnune() {
		YoldaDeck d = new YoldaDeck();
		int at = d.add(YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null);
		assertEquals(33, at);
		assertEquals("add", d.get(34).type());
		assertEquals("TRT 1", d.get(33).title());
		assertEquals("Aynı kanal ikinci kez eklenmez", 33, d.add(YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null));
		assertEquals(35, d.size());
	}

	@Test
	public void kayitGidipGelir() {
		YoldaDeck d = new YoldaDeck();
		d.add(YoldaDeck.RADIO_STREAM, "Kral FM", "https://example/kral", "https://example/logo.png");
		d.add(YoldaDeck.URL, "sozcu.com.tr", "https://sozcu.com.tr", null);
		d.move(0, 2);
		d.remove(d.indexOf("phone"));
		YoldaDeck e = YoldaDeck.decode(d.encode());
		assertEquals(d.size(), e.size());
		for (int i = 0; i < d.size(); i++) assertEquals(d.get(i), e.get(i));
	}

	@Test
	public void bozukKayitVarsayilanaDoner() {
		assertEquals(34, YoldaDeck.decode(null).size());
		assertEquals(34, YoldaDeck.decode("").size());
		assertEquals(34, YoldaDeck.decode("çöp").size());
		// "Ekle" kaydı kaybolsa da eklenir
		YoldaDeck d = YoldaDeck.decode("tv\u001Ftv\u001FTV\u001F\u001F");
		assertEquals(2, d.size());
		assertEquals("add", d.get(1).type());
	}
}
