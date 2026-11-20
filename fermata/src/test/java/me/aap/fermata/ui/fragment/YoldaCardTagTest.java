package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Kart etiketi adın tekrarıysa sadeleşir. */
public class YoldaCardTagTest {
	@Test
	public void etiket() {
		assertEquals("", YoldaDeckFragment.tagText("HAVA DURUMU", "Hava durumu"));
		assertEquals("", YoldaDeckFragment.tagText("NAMAZ VAKİTLERİ", "Namaz vakitleri"));
		assertEquals("300 KM", YoldaDeckFragment.tagText("SON DEPREMLER · 300 KM", "Son depremler"));
		assertEquals("SPOR · SÜPER LİG", YoldaDeckFragment.tagText("SPOR · SÜPER LİG", "Spor ve skorlar"));
		assertEquals("VİDEO", YoldaDeckFragment.tagText("VİDEO", "YouTube"));
	}
}
