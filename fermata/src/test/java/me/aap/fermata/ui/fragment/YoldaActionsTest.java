package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
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
}
