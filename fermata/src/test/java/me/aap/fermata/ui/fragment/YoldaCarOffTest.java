package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Araçtan inince durdur: yalnız ayar açıkken, bağlantı geri gelmediyse ve araya yeni olay girmediyse. */
public class YoldaCarOffTest {
	@Test
	public void kosullar() {
		assertTrue(YoldaCarOff.shouldPause(true, false, 3, 3));
		assertFalse("ayar kapalı", YoldaCarOff.shouldPause(false, false, 3, 3));
		assertFalse("yeniden bağlandı", YoldaCarOff.shouldPause(true, true, 3, 3));
		assertFalse("araya yeni olay girdi", YoldaCarOff.shouldPause(true, false, 3, 5));
	}
}
