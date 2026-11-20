package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Arka plan önbelleği: ne zaman yeniden yüklenir. */
public class YoldaBgCacheTest {
	@Test
	public void eskime() {
		assertTrue(YoldaBgCache.stale(1000, 0, 60_000, false, false)); // hiç yok: yükle
		assertFalse(YoldaBgCache.stale(1000, 0, 60_000, false, true)); // yükleniyor: ikinci kez başlatma
		assertFalse(YoldaBgCache.stale(30_000, 0, 60_000, true, false)); // taze
		assertTrue(YoldaBgCache.stale(61_000, 0, 60_000, true, false)); // süresi geçti
	}
}
