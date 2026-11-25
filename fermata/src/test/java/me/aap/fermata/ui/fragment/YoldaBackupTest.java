package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Ayar yedeği: API anahtarları yedek dosyasına girmez, diğer ayarlar aynen kalır. */
public class YoldaBackupTest {
	@Test
	public void anahtarlarCikar() {
		String xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
				+ "    <string name=\"gemini_key\">AIzaSECRET123</string>\n"
				+ "    <boolean name=\"gemini_voice\" value=\"true\" />\n"
				+ "    <string name=\"tomtom_key\">tt-SECRET</string>\n"
				+ "    <string name=\"phone_view\">pano</string>\n</map>\n";
		String out = YoldaBackup.sanitize(xml);
		assertFalse(out.contains("SECRET"));
		assertFalse(out.contains("gemini_key"));
		assertFalse(out.contains("tomtom_key"));
		assertTrue(out.contains("<boolean name=\"gemini_voice\" value=\"true\" />"));
		assertTrue(out.contains("<string name=\"phone_view\">pano</string>"));
		assertTrue(out.trim().endsWith("</map>"));
	}

	@Test
	public void yalnizYoldaDosyasi() {
		assertTrue(YoldaBackup.needsSanitize("yolda_home"));
		assertFalse(YoldaBackup.needsSanitize("fermata"));
	}
}
