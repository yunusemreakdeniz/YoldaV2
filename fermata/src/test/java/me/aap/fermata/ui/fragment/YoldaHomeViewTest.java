package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Görünüm düğmesi: her dokunuşta sıradaki görünüm (pencere yok). */
public class YoldaHomeViewTest {
	@Test
	public void sira() {
		assertEquals("phone", YoldaDeckFragment.nextHomeView("deck", false));
		assertEquals("pano", YoldaDeckFragment.nextHomeView("phone", false));
		assertEquals("grid", YoldaDeckFragment.nextHomeView("pano", false));
		assertEquals("dash", YoldaDeckFragment.nextHomeView("grid", false));
		assertEquals("deck", YoldaDeckFragment.nextHomeView("dash", false));
		assertEquals("pano", YoldaDeckFragment.nextHomeView("deck", true));
		assertEquals("grid", YoldaDeckFragment.nextHomeView("pano", true));
		assertEquals("dash", YoldaDeckFragment.nextHomeView("grid", true));
		assertEquals("deck", YoldaDeckFragment.nextHomeView("dash", true));
		assertEquals("Panel", YoldaDeckFragment.homeViewName("dash"));
		assertEquals("Telefon", YoldaDeckFragment.homeViewName("phone"));
		assertEquals("Izgara", YoldaDeckFragment.homeViewName("grid"));
	}

	@Test
	public void izgaraSutun() {
		assertEquals(2, YoldaDeckFragment.gridCols(360));
		assertEquals(4, YoldaDeckFragment.gridCols(720));
		assertEquals(5, YoldaDeckFragment.gridCols(1280));
	}
}
