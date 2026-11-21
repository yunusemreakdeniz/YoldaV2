package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Görünüm düğmesi: her dokunuşta sıradaki görünüm (pencere yok). */
public class YoldaHomeViewTest {
	@Test
	public void sira() {
		assertEquals("phone", YoldaDeckFragment.nextHomeView("deck", false));
		assertEquals("pano", YoldaDeckFragment.nextHomeView("phone", false));
		assertEquals("deck", YoldaDeckFragment.nextHomeView("pano", false));
		assertEquals("pano", YoldaDeckFragment.nextHomeView("deck", true));
		assertEquals("deck", YoldaDeckFragment.nextHomeView("pano", true));
		assertEquals("Izgara", YoldaDeckFragment.homeViewName("phone"));
	}
}
