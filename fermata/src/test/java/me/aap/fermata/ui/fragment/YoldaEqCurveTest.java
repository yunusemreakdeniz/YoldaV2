package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Ekolayzer eğrisi: dB → dikey konum, bant → yatay konum. */
public class YoldaEqCurveTest {
	@Test
	public void konumlar() {
		assertEquals(50f, YoldaEqCurve.y(0, 100, 10), 0.01f);
		assertEquals(10f, YoldaEqCurve.y(10, 100, 10), 0.01f);
		assertEquals(90f, YoldaEqCurve.y(-10, 100, 10), 0.01f);
		assertEquals("sınır dışı kırpılır", 10f, YoldaEqCurve.y(25, 100, 10), 0.01f);
		assertEquals(10f, YoldaEqCurve.x(0, 5, 200, 10), 0.01f);
		assertEquals(190f, YoldaEqCurve.x(4, 5, 200, 10), 0.01f);
		assertEquals(100f, YoldaEqCurve.x(2, 5, 200, 10), 0.01f);
	}
}
