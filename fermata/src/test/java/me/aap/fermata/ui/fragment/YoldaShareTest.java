package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Paylaş → Yolda: bağlantı bulma, YouTube kimliği, yayın adresi. */
public class YoldaShareTest {
	@Test
	public void baglanti() {
		assertEquals("https://youtu.be/dQw4w9WgXcQ", YoldaShare.firstUrl("Şuna bak: https://youtu.be/dQw4w9WgXcQ."));
		assertEquals("https://x.com/a?b=1", YoldaShare.firstUrl("https://x.com/a?b=1)"));
		assertNull(YoldaShare.firstUrl("sezen aksu gülümse"));
	}

	@Test
	public void youtube() {
		assertEquals("dQw4w9WgXcQ", YoldaShare.youtubeId("https://youtu.be/dQw4w9WgXcQ?si=abc"));
		assertEquals("dQw4w9WgXcQ", YoldaShare.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"));
		assertEquals("abcdefghijk", YoldaShare.youtubeId("https://youtube.com/shorts/abcdefghijk"));
		assertEquals("abcdefghijk", YoldaShare.youtubeId("https://m.youtube.com/live/abcdefghijk?feature=share"));
		assertNull(YoldaShare.youtubeId("https://www.youtube.com/@cnnturk"));
		assertNull(YoldaShare.youtubeId("https://example.com/watch?v=dQw4w9WgXcQ"));
	}

	@Test
	public void yayin() {
		assertTrue(YoldaShare.isStream("https://tv.example/live/kanal.m3u8?token=1"));
		assertTrue(YoldaShare.isStream("http://radyo.example:8000/stream"));
		assertTrue(YoldaShare.isStream("https://cdn.example/podcast/bolum.mp3"));
		assertFalse(YoldaShare.isStream("https://www.hurriyet.com.tr/gundem/haber-123"));
	}
}
