package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Yol tehlikesi uyarıları: yön, mesafe, bir kez uyarma, buzlanma ve keskin viraj. */
public class YoldaHazardsTest {
	// kuzeye giderken (yön 0) önde ~200 m'de kasis, arkada hemzemin geçit
	private static final double LA = 39.9, LO = 32.8, M = 1 / 111_320.0;
	private final List<YoldaHazards.Hazard> list = List.of(
			new YoldaHazards.Hazard(1, LA + 140 * M, LO, 'b'),
			new YoldaHazards.Hazard(2, LA - 200 * M, LO, 'l'),
			new YoldaHazards.Hazard(3, LA + 250 * M, LO, 'i'));

	@Test
	public void ondekiBirKezUyarilir() {
		YoldaHazards.Memory mem = new YoldaHazards.Memory();
		YoldaHazards.Hazard h = YoldaHazards.next(LA, LO, 0, 50, list, YoldaHazards.KINDS, false, mem, 1000);
		assertNotNull(h);
		assertEquals('b', h.kind());
		assertNull(YoldaHazards.next(LA, LO, 0, 50, list, YoldaHazards.KINDS, false, mem, 2000)); // bir kez
		assertNull(YoldaHazards.next(LA, LO, 180, 50, list.subList(0, 1), YoldaHazards.KINDS, false,
				new YoldaHazards.Memory(), 0)); // arkada kalan uyarılmaz
	}

	@Test
	public void kapaliTurVeBuzlanma() {
		assertNull(YoldaHazards.next(LA, LO, 0, 50, list.subList(0, 1), "l", false, new YoldaHazards.Memory(), 0));
		// köprü yalnız soğukta; 50 km/s'de köprü 300 m içinde
		YoldaHazards.Hazard h = YoldaHazards.next(LA, LO, 0, 50, list.subList(2, 3), YoldaHazards.KINDS, true,
				new YoldaHazards.Memory(), 0);
		assertNotNull(h);
		assertNull(YoldaHazards.next(LA, LO, 0, 50, list.subList(2, 3), YoldaHazards.KINDS, false, new YoldaHazards.Memory(), 0));
		assertEquals("250 metre sonra köprü, buzlanma olabilir", YoldaHazards.text('i', 252));
		assertEquals("Dikkat, kasis", YoldaHazards.text('b', 90));
	}

	@Test
	public void keskinViraj() {
		// 300 m kuzeye, sonra 300 m doğuya: 90 derecelik dönüş
		double[] pts = {LA, LO, LA + 150 * M, LO, LA + 300 * M, LO, LA + 300 * M, LO + 150 * M / Math.cos(Math.toRadians(LA)),
				LA + 300 * M, LO + 300 * M / Math.cos(Math.toRadians(LA))};
		List<YoldaHazards.Hazard> c = YoldaHazards.curves(7, pts);
		assertEquals(1, c.size());
		assertEquals('c', c.get(0).kind());
		assertTrue(c.get(0).id() < 0);
		// düz yol: viraj yok
		assertTrue(YoldaHazards.curves(8, new double[]{LA, LO, LA + 100 * M, LO, LA + 200 * M, LO}).isEmpty());
	}
}
