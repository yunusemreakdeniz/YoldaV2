package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Eylem kataloğu: Gemini'nin "KOMUT:" yanıtı ve çevirdiği komutların yerel çözücüde karşılığı. */
public class YoldaActionsTest {
	@Test
	public void komutYanitiOkunur() {
		assertEquals(List.of("rahatlatıcı müzik çal"), YoldaActions.commands("KOMUT: rahatlatıcı müzik çal"));
		assertEquals(List.of("radyo kartına geç", "Kral FM aç"), YoldaActions.commands("KOMUT: radyo kartına geç ; Kral FM aç."));
		assertEquals(List.of("sesi kıs"), YoldaActions.commands("komut: \"sesi kıs\"\nTamam"));
		assertNull(YoldaActions.commands("Ankara Türkiye'nin başkentidir."));
		assertNull(YoldaActions.commands("Bunun için KOMUT: eve git diyebilirsin")); // başta değil: sohbet
		assertEquals(3, YoldaActions.commands("KOMUT: a ; b ; c ; d").size());
	}

	@Test
	public void ceviriKomutlariYereldeAnlasilir() {
		// Gemini'nin örnek çevirileri yerel çözücüde bir niyete karşılık gelmeli (yoksa "yapamadım" denir)
		for (String c : new String[]{"rahatlatıcı müzik çal", "en yakın nöbetçi eczane", "sesi kıs", "radyo kartına geç",
				"eve git", "uyarıyı dene", "sonraki kart", "saat kaç", "on dakika sonra su içmeyi hatırlat", "sesi yüzde elli yap"}) {
			assertNotNull("çözülemedi: " + c, YoldaIntents.parse(c));
		}
		assertTrue(YoldaActions.forPrompt().contains("kartına geç"));
	}

	@Test
	public void geminiYerelAyrimi() {
		// kesin cihaz komutları yerelde
		assertFalse(YoldaVoice.allForGemini(List.of("sesi kıs")));
		assertFalse(YoldaVoice.allForGemini(List.of("TRT Haber aç")));
		assertFalse(YoldaVoice.allForGemini(List.of("Tarkan çal")));
		assertFalse(YoldaVoice.allForGemini(List.of("eve git")));
		assertFalse(YoldaVoice.allForGemini(List.of("sesi aç ve TRT Haber aç"))); // iki komut: yerel ayırır
		// tahminlerden biri kesin komutsa yerel
		assertFalse(YoldaVoice.allForGemini(List.of("ankaranın nüfusu ne kadar", "sesi kıs")));
		// sohbet, bilgi sorusu, anlaşılmayan ve uzun doğal cümle Gemini'ye
		assertTrue(YoldaVoice.allForGemini(List.of("nasılsın")));
		assertTrue(YoldaVoice.allForGemini(List.of("yol boyunca dinleyebileceğim güzel şarkılar çal")));
		assertTrue(YoldaVoice.allForGemini(List.of("bugün çok yorgunum ne yapsam")));
	}

	@Test
	public void geminiYanitiSeslendirilir() {
		assertEquals("Merhaba", YoldaGemini.spoken("**Merhaba** 😊"));
		assertEquals("bir iki", YoldaGemini.spoken("- bir\n- iki"));
		assertEquals("", YoldaGemini.spoken(null));
	}

	@Test
	public void videoKalitesi() {
		assertEquals(YoldaIntents.Kind.VIDEO_Q, YoldaIntents.parse("video kalitesini 720 yap").kind());
		assertEquals(YoldaIntents.Kind.VIDEO_Q, YoldaIntents.parse("kaliteyi düşür").kind());
		assertEquals(YoldaIntents.Kind.EQ, YoldaIntents.parse("rock ekolayzer").kind());
		assertEquals(1, YoldaIntents.parse("telefon görünümüne geç").num());
		assertEquals(0, YoldaIntents.parse("araç görünümüne geç").num());
		assertEquals(YoldaIntents.Kind.VIEW_MODE, YoldaIntents.parse("telefon görünümü").kind());
		assertEquals("720", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("video kalitesini 720 yap", "480"));
		assertEquals("1080", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("full hd yap", "480"));
		assertEquals("720", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("hd olsun", "480"));
		assertEquals("auto", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("kalite otomatik", "480"));
		assertEquals("360", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("kaliteyi düşür", "480"));
		assertEquals("720", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("kaliteyi artır", "480"));
		assertEquals("1080", me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("kaliteyi artır", "1080"));
		assertEquals(null, me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality("video kalitesi ne", "480"));
	}
}
