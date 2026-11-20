package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** Kanallar sayfası "Son açılanlar": türe göre, yinelenmeden, en yeni önce. */
public class YoldaRecentTest {
	private static YoldaPlayHistory.Entry e(String id, String kind) {
		return new YoldaPlayHistory.Entry(id, id, kind, null, 0, 0, 0);
	}

	@Test
	public void sonAcilanlar() {
		List<YoldaPlayHistory.Entry> h = List.of(e("trt1", "TV"), e("kral", "Radyo"), e("atv", "TV"), e("trt1", "TV"),
				e("sozcu", "TV"), e("film", "Video"));
		assertEquals(List.of("trt1", "atv", "sozcu"), YoldaPlayHistory.recentIds(h, "TV", 6));
		assertEquals(List.of("trt1", "atv"), YoldaPlayHistory.recentIds(h, "TV", 2));
		assertEquals(List.of("kral"), YoldaPlayHistory.recentIds(h, "Radyo", 6));
		assertEquals(List.of(), YoldaPlayHistory.recentIds(List.of(), "TV", 6));
	}
}
