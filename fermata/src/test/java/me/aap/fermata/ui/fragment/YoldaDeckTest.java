package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Yolda V2 kart destesi: varsayılan sıra, taşıma, kaldırma, ekleme, geri getirme, kayıt. */
public class YoldaDeckTest {

	@Test
	public void varsayilanSira() {
		YoldaDeck d = new YoldaDeck();
		String[] want = {"resume", "youtube", "music", "tv", "radio", "favs", "nav", "weather", "pharmacy", "phone", "mirror", "web",
				"settings", "add"};
		assertEquals(want.length, d.size());
		for (int i = 0; i < want.length; i++) assertEquals(want[i], d.get(i).type());
		assertEquals("Kaldığın yerden", d.get(0).title());
		assertEquals("YouTube", d.get(1).title());
		assertEquals("Hava durumu", d.get(7).title());
		assertEquals("Nöbetçi eczane", d.get(8).title());
		assertEquals("Ekle", d.get(13).title());
	}

	@Test
	public void tasima() {
		YoldaDeck d = new YoldaDeck();
		assertEquals(1, d.move(0, 1));
		assertEquals("youtube", d.get(0).type());
		assertEquals("resume", d.get(1).type());
		assertEquals(0, d.move(0, -1)); // en baştan sola gidilmez
		assertEquals(13, d.move(13, 1)); // en sondan sağa gidilmez
	}

	@Test
	public void kaldirmaVeGeriGetirme() {
		YoldaDeck d = new YoldaDeck();
		assertTrue(d.remove(d.indexOf("phone")));
		assertFalse("Ekle kaldırılamaz", d.remove(d.indexOf("add")));
		assertEquals(List.of("phone"), d.missingBuiltIns());
		int at = d.restore("phone");
		assertEquals(9, at); // eczanenin ardına, ekran yansıtmanın önüne
		assertTrue(d.missingBuiltIns().isEmpty());
	}

	@Test
	public void eklemeEkleKartininOnune() {
		YoldaDeck d = new YoldaDeck();
		int at = d.add(YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null);
		assertEquals(13, at);
		assertEquals("add", d.get(14).type());
		assertEquals("TRT 1", d.get(13).title());
		assertEquals("Aynı kanal ikinci kez eklenmez", 13, d.add(YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null));
		assertEquals(15, d.size());
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
		assertEquals(13, YoldaDeck.decode(null).size());
		assertEquals(13, YoldaDeck.decode("").size());
		assertEquals(13, YoldaDeck.decode("çöp").size());
		// "Ekle" kaydı kaybolsa da eklenir
		YoldaDeck d = YoldaDeck.decode("tv\u001Ftv\u001FTV\u001F\u001F");
		assertEquals(2, d.size());
		assertEquals("add", d.get(1).type());
	}
}
