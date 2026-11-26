package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** "Kaldığın yerden" geçmişi: başa alma, sınır, ilerleme, zaman yazısı. */
public class YoldaPlayHistoryTest {
	private static YoldaPlayHistory.Entry e(String id, long pos, long dur, long at) {
		return new YoldaPlayHistory.Entry(id, "Ad " + id, "Müzik", null, pos, dur, at);
	}

	@Test
	public void basaAlirVeSinirlar() {
		List<YoldaPlayHistory.Entry> l = new ArrayList<>();
		for (int i = 0; i < 20; i++) l = YoldaPlayHistory.push(l, e("x" + i, 0, 0, i));
		assertEquals(YoldaPlayHistory.MAX, l.size());
		assertEquals("x19", l.get(0).id());
		l = YoldaPlayHistory.push(l, e("x15", 0, 0, 99)); // yeniden çalındı: başa, tek kez
		assertEquals("x15", l.get(0).id());
		assertEquals(YoldaPlayHistory.MAX, l.size());
		assertEquals(1, l.stream().filter(x -> x.id().equals("x15")).count());
		List<YoldaPlayHistory.Entry> back = YoldaPlayHistory.decode(YoldaPlayHistory.encode(l));
		assertEquals(l, back);
	}

	@Test
	public void ilerlemeVeZaman() {
		assertEquals(42, e("a", 42_000, 100_000, 0).percent());
		assertEquals(-1, e("a", 0, 0, 0).percent());
		long now = 10L * 24 * 3600_000;
		assertEquals("az önce", YoldaPlayHistory.ago(now - 30_000, now));
		assertEquals("5 dk önce", YoldaPlayHistory.ago(now - 5 * 60_000, now));
		assertEquals("2 sa önce", YoldaPlayHistory.ago(now - 2 * 3600_000, now));
		assertEquals("dün", YoldaPlayHistory.ago(now - 25 * 3600_000L, now));
		assertEquals("3 gün önce", YoldaPlayHistory.ago(now - 3 * 24 * 3600_000L, now));
		List<YoldaPlayHistory.Entry> l = List.of(new YoldaPlayHistory.Entry("t", "TRT 1", "TV", null, 0, 0, now - 5 * 60_000),
				e("s", 42_000, 100_000, 0));
		assertEquals("TRT 1 · 5 dk önce · Ad s %42", YoldaPlayHistory.line(l, now));
	}

	/** Çıplak adres yerine okunur ad (Panel'de "https://test-streams.mux…" yazıyordu). */
	@Test
	public void adrestenOkunurAd() {
		assertEquals("Film Adı", YoldaPlayHistory.displayName("https://site.com/yol/Film_Ad%C4%B1.mp4?x=1", null));
		assertEquals("x36xhzz", YoldaPlayHistory.displayName("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", null));
		assertEquals("radyo.example.com", YoldaPlayHistory.displayName("https://www.radyo.example.com:8000/live/index.m3u8", null));
		assertEquals("Şarkı", YoldaPlayHistory.displayName("Şarkı", "https://a.b/c.mp3"));
		assertEquals("c", YoldaPlayHistory.displayName("", "https://a.b/x/cc.mp3").substring(0, 1));
		assertEquals("a.b", YoldaPlayHistory.displayName(null, "https://a.b/"));
		assertEquals("Film Adı", new YoldaPlayHistory.Entry("1", "https://s.com/Film_Adı.mkv", "Video", null, 0, 0, 0).name());
		// aynı adres farklı kimlikle: tek kayıt
		List<YoldaPlayHistory.Entry> l = new ArrayList<>();
		for (int i = 0; i < 3; i++)
			l = YoldaPlayHistory.push(l, new YoldaPlayHistory.Entry("id" + i, "x", "Video", "https://a.b/x.m3u8", 0, 0, i));
		assertEquals(1, l.size());
	}
}
