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
		String[] want = {"youtube", "music", "tv", "radio", "nav", "phone", "mirror", "web", "settings", "add"};
		assertEquals(want.length, d.size());
		for (int i = 0; i < want.length; i++) assertEquals(want[i], d.get(i).type());
		assertEquals("YouTube", d.get(0).title());
		assertEquals("Navigasyon", d.get(4).title());
		assertEquals("Ekran yansıtma", d.get(6).title());
		assertEquals("Ekle", d.get(9).title());
	}

	@Test
	public void tasima() {
		YoldaDeck d = new YoldaDeck();
		assertEquals(1, d.move(0, 1));
		assertEquals("music", d.get(0).type());
		assertEquals("youtube", d.get(1).type());
		assertEquals(0, d.move(0, -1)); // en baştan sola gidilmez
		assertEquals(9, d.move(9, 1)); // en sondan sağa gidilmez
	}

	@Test
	public void kaldirmaVeGeriGetirme() {
		YoldaDeck d = new YoldaDeck();
		assertTrue(d.remove(d.indexOf("phone")));
		assertFalse("Ekle kaldırılamaz", d.remove(d.indexOf("add")));
		assertEquals(List.of("phone"), d.missingBuiltIns());
		int at = d.restore("phone");
		assertEquals(5, at); // navigasyonun ardına, tarayıcının önüne
		assertTrue(d.missingBuiltIns().isEmpty());
	}

	@Test
	public void eklemeEkleKartininOnune() {
		YoldaDeck d = new YoldaDeck();
		int at = d.add(YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null);
		assertEquals(9, at);
		assertEquals("add", d.get(10).type());
		assertEquals("TRT 1", d.get(9).title());
		assertEquals("Aynı kanal ikinci kez eklenmez", 9, d.add(YoldaDeck.TV_CHANNEL, "TRT 1", "tv://trt1", null));
		assertEquals(11, d.size());
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
		assertEquals(10, YoldaDeck.decode(null).size());
		assertEquals(10, YoldaDeck.decode("").size());
		assertEquals(10, YoldaDeck.decode("çöp").size());
		// "Ekle" kaydı kaybolsa da eklenir
		YoldaDeck d = YoldaDeck.decode("tv\u001Ftv\u001FTV\u001F\u001F");
		assertEquals(2, d.size());
		assertEquals("add", d.get(1).type());
	}
}
