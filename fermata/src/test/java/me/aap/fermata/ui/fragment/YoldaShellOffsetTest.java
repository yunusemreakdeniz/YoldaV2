package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.aap.fermata.ui.view.YoldaStyle;

/** Sol düğmelerin yeri: Kenarda → İçeride → Daha içeride → Kenarda. */
public class YoldaShellOffsetTest {
	@Test
	public void sira() {
		assertEquals(40, YoldaStyle.nextShellOffset(0));
		assertEquals(80, YoldaStyle.nextShellOffset(40));
		assertEquals(0, YoldaStyle.nextShellOffset(80));
		assertEquals(0, YoldaStyle.nextShellOffset(13));
	}
}
