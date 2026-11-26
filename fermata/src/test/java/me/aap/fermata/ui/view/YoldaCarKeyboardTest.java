package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Araç klavyesi: yalnız dururken, tuş etkisi, sesle yazma sözleri. */
public class YoldaCarKeyboardTest {
	@Test
	public void yalnizDururken() {
		assertTrue("konum yoksa açılır", YoldaCarKeyboard.allowed(false, 80));
		assertTrue(YoldaCarKeyboard.allowed(true, 0));
		assertTrue(YoldaCarKeyboard.allowed(true, 4));
		assertFalse("sürüşte kapalı", YoldaCarKeyboard.allowed(true, 5));
		assertFalse(YoldaCarKeyboard.allowed(true, 90));
	}

	@Test
	public void tuslar() {
		StringBuilder sb = new StringBuilder("trt");
		int c = YoldaCarKeyboard.apply(sb, 3, " ");
		c = YoldaCarKeyboard.apply(sb, c, "1");
		assertEquals("trt 1", sb.toString());
		assertEquals(5, c);
		c = YoldaCarKeyboard.apply(sb, c, "⌫");
		assertEquals("trt ", sb.toString());
		assertEquals(4, c);
		assertEquals(0, YoldaCarKeyboard.apply(new StringBuilder(), 0, "⌫"));
		StringBuilder mid = new StringBuilder("ab");
		YoldaCarKeyboard.apply(mid, 1, "ş");
		assertEquals("aşb", mid.toString());
	}

	@Test
	public void sesleYazmaSozleri() {
		assertEquals(YoldaSearchMic.Edit.CLEAR, YoldaSearchMic.spokenEdit("Temizle"));
		assertEquals(YoldaSearchMic.Edit.CANCEL, YoldaSearchMic.spokenEdit("vazgeç."));
		assertEquals(YoldaSearchMic.Edit.SUBMIT, YoldaSearchMic.spokenEdit("ara"));
		assertEquals(YoldaSearchMic.Edit.TEXT, YoldaSearchMic.spokenEdit("tarkan şımarık"));
	}
}
