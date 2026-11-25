package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** Deste nokta penceresi: çok kartta en çok 11 nokta, çalan kart ortada. */
public class YoldaDotWindowTest {
	@Test
	public void azKartHepsi() {
		assertArrayEquals(new int[]{0, 5}, YoldaDeckFragment.dotWindow(5, 2, 11));
		assertArrayEquals(new int[]{0, 11}, YoldaDeckFragment.dotWindow(11, 10, 11));
	}

	@Test
	public void cokKartPencere() {
		assertArrayEquals(new int[]{0, 11}, YoldaDeckFragment.dotWindow(34, 0, 11));
		assertArrayEquals(new int[]{15, 26}, YoldaDeckFragment.dotWindow(34, 20, 11));
		assertArrayEquals(new int[]{23, 34}, YoldaDeckFragment.dotWindow(34, 33, 11));
		assertArrayEquals(new int[]{23, 34}, YoldaDeckFragment.dotWindow(34, 99, 11));
	}
}
