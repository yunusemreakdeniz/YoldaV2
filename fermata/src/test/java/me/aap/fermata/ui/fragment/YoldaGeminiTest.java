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
}
