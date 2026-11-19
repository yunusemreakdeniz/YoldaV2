package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Gemini yanıtının ayrıştırılması ve sistem yönergesi (ağsız). */
public class YoldaGeminiTest {
	@Test
	public void yanit() throws Exception {
		String json = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
				+ "{\"text\":\"düşünce\",\"thought\":true},{\"text\":\"**Ankara**, Türkiye'nin başkentidir.\\n\"},"
				+ "{\"text\":\" Nüfusu yaklaşık 5,8 milyon.\"}]}}]}";
		assertEquals("Ankara, Türkiye'nin başkentidir. Nüfusu yaklaşık 5,8 milyon.", YoldaGemini.parse(json));
		assertNull(YoldaGemini.parse("{\"candidates\":[]}"));
		assertNull(YoldaGemini.parse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
	}

	@Test
	public void yonerge() {
		String s = YoldaGemini.system("Yenimahalle");
		assertTrue(s.contains("Türkçe"));
		assertTrue(s.contains("Yenimahalle"));
		assertTrue(!YoldaGemini.system(null).contains("bulunduğu yer"));
	}

	@Test
	public void yogunlukTekrarDenenir() {
		org.junit.Assert.assertTrue(YoldaGemini.overloaded(503));
		org.junit.Assert.assertTrue(YoldaGemini.overloaded(500));
		org.junit.Assert.assertTrue(!YoldaGemini.overloaded(429)); // kota: tekrar denenmez
		org.junit.Assert.assertTrue(!YoldaGemini.overloaded(403));
		org.junit.Assert.assertEquals("UNAVAILABLE The model is overloaded. Please try again later.", YoldaGemini.errorMessage(
				"{\"error\":{\"code\":503,\"message\":\"The model is overloaded. Please try again later.\",\"status\":\"UNAVAILABLE\"}}"));
		org.junit.Assert.assertEquals("", YoldaGemini.errorMessage("bozuk"));
	}

	@Test
	public void modelSirasiVeAnahtarHatasi() {
		// önce Lite (hızlı), 2.5 modelleri yok (yeni anahtarlara kapalı)
		org.junit.Assert.assertTrue(YoldaGemini.MODELS[0].contains("lite"));
		for (String m : YoldaGemini.MODELS) org.junit.Assert.assertTrue(m, !m.contains("2.5"));
		org.junit.Assert.assertTrue(YoldaGemini.keyError("{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\","
				+ "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}"));
		org.junit.Assert.assertTrue(!YoldaGemini.keyError("{\"error\":{\"code\":400,\"message\":\"Invalid value at generation_config\"}}"));
	}
}
