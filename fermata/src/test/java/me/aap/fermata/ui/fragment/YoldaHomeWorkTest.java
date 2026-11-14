package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Kanal sırası (TV / Radyo kartında "Sırala") ve ev / iş tahmini (Navigasyon kartı). */
public class YoldaHomeWorkTest {
	private static final TimeZone TZ = TimeZone.getTimeZone("Europe/Istanbul");
	private static final double HOME_LAT = 41.0, HOME_LON = 29.0, WORK_LAT = 41.05, WORK_LON = 28.95;

	private static long at(int day, int hour) { // 2026-11-02 Pazartesi + day
		Calendar c = Calendar.getInstance(TZ);
		c.clear();
		c.set(2026, Calendar.NOVEMBER, 2 + day, hour, 15);
		return c.getTimeInMillis();
	}

	private static double[] stop(double la, double lo, long t) {
		return new double[]{la, lo, t};
	}

	@Test
	public void evVeIsTahmini() {
		List<double[]> s = new ArrayList<>();
		for (int d = 0; d < 5; d++) { // hafta içi: sabah işe, akşam eve
			s.add(stop(WORK_LAT + 0.0005 * (d % 2), WORK_LON, at(d, 9)));
			s.add(stop(HOME_LAT, HOME_LON + 0.0004 * (d % 3), at(d, 21)));
		}
		s.add(stop(40.9, 29.2, at(5, 14))); // cumartesi alışveriş
		double[] g = YoldaDriveCore.guessHomeWork(s, TZ);
		assertEquals(HOME_LAT, g[0], 0.002);
		assertEquals(HOME_LON, g[1], 0.002);
		assertEquals(WORK_LAT, g[2], 0.002);
		assertEquals(WORK_LON, g[3], 0.002);
	}

	@Test
	public void azVeriyleTahminYok() {
		List<double[]> s = new ArrayList<>();
		s.add(stop(HOME_LAT, HOME_LON, at(0, 22)));
		s.add(stop(HOME_LAT, HOME_LON, at(1, 22)));
		s.add(stop(WORK_LAT, WORK_LON, at(1, 10)));
		double[] g = YoldaDriveCore.guessHomeWork(s, TZ);
		assertTrue(Double.isNaN(g[0]));
		assertTrue(Double.isNaN(g[2]));
	}

	@Test
	public void isEvleAyniYerOlamaz() {
		List<double[]> s = new ArrayList<>();
		for (int d = 0; d < 5; d++) { // evden çalışan: gündüz de evde
			s.add(stop(HOME_LAT, HOME_LON, at(d, 10)));
			s.add(stop(HOME_LAT, HOME_LON, at(d, 22)));
		}
		double[] g = YoldaDriveCore.guessHomeWork(s, TZ);
		assertEquals(HOME_LAT, g[0], 0.001);
		assertTrue("ev iş sayılmamalı", Double.isNaN(g[2]));
	}

	@Test
	public void kanalSirasi() {
		List<String> l = new ArrayList<>(List.of("trt1", "atv", "kanald", "star", "yeni"));
		YoldaChannelOrder.sort(l, List.of("star", "trt1", "atv", "kanald"), x -> x);
		assertEquals(List.of("star", "trt1", "atv", "kanald", "yeni"), l); // sırada olmayan sona
		assertEquals(0, YoldaChannelOrder.move(l, 1, -1));
		assertEquals(List.of("trt1", "star", "atv", "kanald", "yeni"), l);
		assertEquals(4, YoldaChannelOrder.move(l, 4, 1)); // sondan aşağı gidilmez
		List<String> u = new ArrayList<>(List.of("b", "a"));
		YoldaChannelOrder.sort(u, List.of(), x -> x);
		assertEquals(List.of("b", "a"), u); // sıra yoksa dokunulmaz
	}
}
