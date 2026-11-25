package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Gemini doğal sesi: istek gövdesi ve yanıttan ses verisi. */
public class YoldaGeminiVoiceTest {
	@Test
	public void govde() throws Exception {
		String b = YoldaGeminiVoice.body("Merhaba");
		assertTrue(b, b.contains("\"responseModalities\":[\"AUDIO\"]"));
		assertTrue(b, b.contains("\"voiceName\":\"Kore\""));
		assertTrue(b, b.contains("\"text\":\"Merhaba\""));
	}

	@Test
	public void sesVerisi() throws Exception {
		String js = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/L16;rate=24000\",\"data\":\"AAEC\"}}]}}]}";
		assertEquals("AAEC", YoldaGeminiVoice.audioBase64(js));
		assertNull(YoldaGeminiVoice.audioBase64("{\"candidates\":[]}"));
		assertNull(YoldaGeminiVoice.audioBase64("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}"));
	}
}
