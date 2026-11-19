package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

/** Mobil veri sayacı: yüzde, uyarı aşaması (bir kez), ay anahtarı, boyut yazısı; telefon durumu komutu. */
public class YoldaDataTest {
	private static final long MB = 1024L * 1024L;

	@Test
	public void yuzdeVeAsama() {
		assertEquals(-1, YoldaData.percent(500 * MB, 0)); // sınır kapalı
		assertEquals(50, YoldaData.percent(512 * MB, 1024));
		assertEquals(0, YoldaData.stage(50, 0));
		assertEquals(1, YoldaData.stage(85, 0)); // %80 uyarısı
		assertEquals(0, YoldaData.stage(90, 1)); // aynı uyarı ikinci kez yok
		assertEquals(2, YoldaData.stage(100, 1)); // sınır
		assertEquals(2, YoldaData.stage(130, 0)); // atlanan %80 yerine doğrudan sınır
		assertEquals(0, YoldaData.stage(150, 2));
	}

	@Test
	public void ayVeBoyut() {
		Calendar c = Calendar.getInstance();
		c.set(2026, Calendar.NOVEMBER, 19);
		assertEquals("2026-11", YoldaData.month(c));
		assertEquals("350 MB", YoldaData.size(350 * MB));
		assertEquals("1,5 GB", YoldaData.size(1536 * MB));
	}

	@Test
	public void sesliKomut() {
		assertEquals(YoldaIntents.Kind.STATUS, YoldaIntents.parse("ne kadar veri kullandım").kind());
		assertEquals(YoldaIntents.Kind.STATUS, YoldaIntents.parse("internetim ne kadar kaldı").kind());
		assertEquals(YoldaIntents.Kind.STATUS, YoldaIntents.parse("telefon durumu").kind());
	}
}
