package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import me.aap.fermata.ui.view.YoldaMotion;
import me.aap.fermata.ui.view.YoldaSky;

/** Paket B: hava durumuna göre zemin, ölçülü animasyonlar, yerleşim alanıyla hız sınırı tahmini. */
public class YoldaPaketBTest {
	@Test
	public void havaSahnesi() {
		assertEquals(YoldaSky.SUN, YoldaSky.scene(0, false));
		assertEquals(YoldaSky.STARS, YoldaSky.scene(1, true));
		assertEquals(YoldaSky.CLOUDS, YoldaSky.scene(3, false));
		assertEquals(YoldaSky.FOG, YoldaSky.scene(45, true));
		assertEquals(YoldaSky.RAIN, YoldaSky.scene(63, false));
		assertEquals(YoldaSky.RAIN, YoldaSky.scene(81, true)); // sağanak
		assertEquals(YoldaSky.SNOW, YoldaSky.scene(73, false));
		assertEquals(YoldaSky.SNOW, YoldaSky.scene(85, false));
		assertEquals(YoldaSky.STORM, YoldaSky.scene(95, false));
		assertEquals(YoldaSky.NONE, YoldaSky.scene(-1, false));
		assertEquals("yağmurlu", YoldaSky.label(YoldaSky.RAIN));
	}

	@Test
	public void hareketliSahnelerAkici() {
		// damla / kar / yıldız saniyede ~30 kare (hızlı), güneş / bulut / sis yavaş (pil)
		assertTrue(YoldaSky.fast(YoldaSky.RAIN));
		assertTrue(YoldaSky.fast(YoldaSky.SNOW));
		assertTrue(YoldaSky.fast(YoldaSky.STORM));
		assertTrue(YoldaSky.fast(YoldaSky.STARS));
		assertTrue(!YoldaSky.fast(YoldaSky.SUN) && !YoldaSky.fast(YoldaSky.CLOUDS) && !YoldaSky.fast(YoldaSky.FOG));
		assertTrue(!YoldaSky.fast(YoldaSky.NONE));
		// yumuşak sönme: uçlarda 0 ve 1, ortada yarım, tekdüze artan
		assertEquals(0f, YoldaSky.ease(0f), 0f);
		assertEquals(1f, YoldaSky.ease(1f), 0f);
		assertEquals(0.5f, YoldaSky.ease(0.5f), 1e-6f);
		assertEquals(0f, YoldaSky.ease(-3f), 0f);
		for (int i = 1; i <= 10; i++) assertTrue(YoldaSky.ease(i / 10f) >= YoldaSky.ease((i - 1) / 10f));
	}

	@Test
	public void parcaciklarOlcekli() {
		// küçük ekranda da en az 12, büyük ekranda üst sınır; güneşte parçacık yok
		assertTrue(YoldaSky.particles(YoldaSky.RAIN, 360, 640) >= 12);
		assertEquals(70, YoldaSky.particles(YoldaSky.RAIN, 1080, 1920));
		assertEquals(70, YoldaSky.particles(YoldaSky.RAIN, 2560, 1600));
		assertEquals(0, YoldaSky.particles(YoldaSky.SUN, 1080, 1920));
		// sözde rastgele: aralıkta ve her çağrıda aynı
		for (int i = 0; i < 200; i++) {
			float r = YoldaSky.rnd(i, 7);
			assertTrue(r >= 0f && r < 1f);
			assertEquals(r, YoldaSky.rnd(i, 7), 0f);
		}
		assertTrue(YoldaSky.rnd(1, 1) != YoldaSky.rnd(2, 1));
	}

	@Test
	public void animasyonSureleri() {
		assertEquals(0, YoldaMotion.enterMs(false, false)); // sistem animasyonları kapalı
		assertEquals(0, YoldaMotion.enterMs(true, false));
		assertTrue(YoldaMotion.enterMs(true, true) < YoldaMotion.enterMs(false, true)); // araçta daha kısa
		assertTrue(YoldaMotion.enterMs(false, true) <= 250); // ölçülü
		assertEquals(0f, YoldaMotion.enterRiseDp(true), 0f); // araçta yükselme yok
	}

	@Test
	public void tasarimOlcegi() {
		// yazı: ölçekteki değer değişmez, ara değer en yakına (eşitlikte küçüğe), çok küçük 12'ye
		assertEquals(15f, me.aap.fermata.ui.view.YoldaStyle.type(15), 0f);
		assertEquals(17f, me.aap.fermata.ui.view.YoldaStyle.type(17), 0f);
		assertEquals(18f, me.aap.fermata.ui.view.YoldaStyle.type(19), 0f);
		assertEquals(24f, me.aap.fermata.ui.view.YoldaStyle.type(26), 0f);
		assertEquals(12f, me.aap.fermata.ui.view.YoldaStyle.type(10), 0f);
		assertEquals(100f, me.aap.fermata.ui.view.YoldaStyle.type(100), 0f); // büyük saat olduğu gibi
		// köşe: basamağa oturur, hap / daire olduğu gibi
		assertEquals(12f, me.aap.fermata.ui.view.YoldaStyle.radius(14), 0f);
		assertEquals(16f, me.aap.fermata.ui.view.YoldaStyle.radius(17), 0f);
		assertEquals(999f, me.aap.fermata.ui.view.YoldaStyle.radius(999), 0f);
		for (float t : me.aap.fermata.ui.view.YoldaStyle.TYPE_SCALE) assertEquals(t, me.aap.fermata.ui.view.YoldaStyle.type(t), 0f);
	}

	@Test
	public void aracSadelestirme() {
		assertEquals(6, YoldaDeck.carItems(20)); // sürüşte liste sınırı
		assertEquals(3, YoldaDeck.carItems(3));
		assertEquals(0, YoldaDeck.carItems(0));
		assertEquals("Galatasaray 2-1 Fenerbahçe".length() > YoldaDeck.CAR_MAX_CHARS ? "Galatasaray 2-1…" : "Galatasaray 2-1 Fenerbahçe",
				YoldaDeck.carLabel("Galatasaray 2-1 Fenerbahçe"));
		assertEquals("TRT 1", YoldaDeck.carLabel("TRT 1"));
		assertEquals("Benzin 71,09 ₺", YoldaDeck.carLabel(" Benzin  71,09 ₺ "));
		String l = YoldaDeck.carLabel("▶ Fularsız Entellik · son bölüm · 42 dk · %30");
		assertTrue(l, l.length() <= YoldaDeck.CAR_MAX_CHARS && l.endsWith("…"));
		assertTrue(l, !l.contains("·…")); // ayraçta bitmez
		assertTrue(YoldaDeck.carLabel("Çokuzunsözcükaralarındaboşlukyokbuyazıda").endsWith("…"));
		assertEquals("", YoldaDeck.carLabel(null));
	}

	/** Saydam rengi zemine bindir (0..1 kanallar). */
	private static double[] over(int c, double[] bg) {
		double a = ((c >>> 24) & 0xFF) / 255.0;
		return new double[]{((c >> 16) & 0xFF) / 255.0 * a + bg[0] * (1 - a), ((c >> 8) & 0xFF) / 255.0 * a + bg[1] * (1 - a),
				(c & 0xFF) / 255.0 * a + bg[2] * (1 - a)};
	}

	private static double lum(double[] c) {
		double[] l = new double[3];
		for (int i = 0; i < 3; i++) l[i] = (c[i] <= 0.03928) ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
		return 0.2126 * l[0] + 0.7152 * l[1] + 0.0722 * l[2];
	}

	@Test
	public void yaziRenkleriOkunur() {
		// her temada cam kutu (beyaz %14) üstünde: ikincil yazı en az 4.5:1, üçüncül en az 3:1 (WCAG)
		for (me.aap.fermata.ui.view.YoldaStyle.Theme t : me.aap.fermata.ui.view.YoldaStyle.THEMES) {
			double[] bg = over(0x24FFFFFF, over(t.base | 0xFF000000, new double[]{0, 0, 0}));
			double lb = lum(bg);
			double c2 = (lum(over(me.aap.fermata.ui.view.YoldaStyle.TEXT_2, bg)) + 0.05) / (lb + 0.05);
			double c3 = (lum(over(me.aap.fermata.ui.view.YoldaStyle.TEXT_3, bg)) + 0.05) / (lb + 0.05);
			assertTrue(t.name + " ikincil " + c2, c2 >= 4.5);
			assertTrue(t.name + " üçüncül " + c3, c3 >= 3.0);
		}
	}

	@Test
	public void kartYonetimiKomutlari() {
		YoldaIntents.Intent h = YoldaIntents.parse("hava durumu kartını gizle");
		assertEquals(YoldaIntents.Kind.CARD_HIDE, h.kind());
		assertEquals("hava durumu", h.arg());
		assertEquals(YoldaIntents.Kind.CARD_HIDE, YoldaIntents.parse("spor kartını kaldır").kind());
		YoldaIntents.Intent g = YoldaIntents.parse("podcast kartını geri getir");
		assertEquals(YoldaIntents.Kind.CARD_SHOW, g.kind());
		assertEquals("podcast", g.arg());
		assertEquals("radyo|first", YoldaIntents.parse("radyo kartını başa al").arg());
		assertEquals("eczane|last", YoldaIntents.parse("eczane kartını en sona taşı").arg());
		YoldaIntents.Intent mv = YoldaIntents.parse("spor kartını trafik kartının ardına taşı");
		assertEquals(YoldaIntents.Kind.CARD_MOVE, mv.kind());
		assertEquals("spor|after|trafik", mv.arg());
		assertEquals("spor|before|trafik", YoldaIntents.parse("spor kartını trafik kartının önüne al").arg());
		// eski kart komutları değişmedi
		assertEquals(YoldaIntents.Kind.CARD, YoldaIntents.parse("radyo kartına geç").kind());
		assertEquals(YoldaIntents.Kind.CARD_OPEN, YoldaIntents.parse("akaryakıt kartını aç").kind());
	}

	@Test
	public void geminiKartListesiniBilir() {
		YoldaDeck d = YoldaDeck.decode(null); // varsayılan deste
		String l = YoldaVoice.cardsLine(d.cards(), java.util.List.of(YoldaDeck.SPORTS));
		assertTrue(l, l.startsWith("Kartlar sırayla: Kaldığın yerden, YouTube"));
		assertTrue(l, !l.contains("Ekle,") && !l.endsWith("Ekle. "));
		assertTrue(l, l.contains("Kaldırılmış (geri getirilebilir): Spor."));
		assertTrue(YoldaVoice.cardsLine(d.cards(), java.util.List.of()).indexOf("Kaldırılmış") < 0);
	}

	@Test
	public void kartHedefSirasi() {
		assertEquals(0, YoldaDeck.targetIndex(5, "first", -1, 10));
		assertEquals(9, YoldaDeck.targetIndex(5, "last", -1, 10));
		assertEquals(2, YoldaDeck.targetIndex(5, "before", 2, 10)); // öbürü solda: onun yerine
		assertEquals(3, YoldaDeck.targetIndex(5, "after", 2, 10));
		assertEquals(7, YoldaDeck.targetIndex(2, "before", 8, 10)); // öbürü sağda: çıkınca bir kayar
		assertEquals(8, YoldaDeck.targetIndex(2, "after", 8, 10));
		assertEquals(5, YoldaDeck.targetIndex(5, "after", 5, 10)); // kendi yanına: yer değişmez
		assertEquals(4, YoldaDeck.targetIndex(4, "before", -1, 10)); // öbürü bulunamadı
		// liste üzerinde gerçekten doğru yere gidiyor mu
		java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of("a", "b", "c", "d", "e"));
		int to = YoldaDeck.targetIndex(0, "after", 3, l.size()); // a'yı d'nin ardına
		l.add(to, l.remove(0));
		assertEquals(java.util.List.of("b", "c", "d", "a", "e"), l);
		l = new java.util.ArrayList<>(java.util.List.of("a", "b", "c", "d", "e"));
		to = YoldaDeck.targetIndex(4, "before", 1, l.size()); // e'yi b'nin önüne
		l.add(to, l.remove(4));
		assertEquals(java.util.List.of("a", "e", "b", "c", "d"), l);
		assertEquals(YoldaDeck.PODCAST, YoldaDeck.builtInOfSpoken("podcast"));
		assertEquals(null, YoldaDeck.builtInOfSpoken("olmayan bir şey"));
	}

	@Test
	public void yerlesimAlani() throws Exception {
		JSONArray el = new JSONArray("[{\"type\":\"way\",\"tags\":{\"highway\":\"primary\"}},"
				+ "{\"type\":\"area\",\"tags\":{\"landuse\":\"residential\"}}]");
		assertTrue(YoldaRadarCore.settlementFromAreas(el));
		assertTrue(YoldaRadarCore.settlementFromAreas(new JSONArray("[{\"type\":\"area\",\"tags\":{\"place\":\"town\",\"name\":\"Gerede\"}}]")));
		// idari sınır, tarla, sanayi yerleşim sayılmaz
		assertFalse(YoldaRadarCore.settlementFromAreas(new JSONArray("[{\"type\":\"area\",\"tags\":{\"boundary\":\"administrative\"}},"
				+ "{\"type\":\"area\",\"tags\":{\"landuse\":\"farmland\"}},{\"type\":\"area\",\"tags\":{\"landuse\":\"industrial\"}}]")));
		assertFalse(YoldaRadarCore.settlementFromAreas(new JSONArray("[{\"type\":\"way\",\"tags\":{\"landuse\":\"residential\"}}]")));
		assertFalse(YoldaRadarCore.settlementFromAreas(null));
	}

	@Test
	public void yakindakiYerlesimNoktasi() throws Exception {
		// köy noktası 400 m ötede: yerleşim içi; 900 m ötede: dışı (köy yarıçapı 600 m)
		double la = 40.0, lo = 32.0;
		String near = "[{\"type\":\"node\",\"lat\":40.0036,\"lon\":32.0,\"tags\":{\"place\":\"village\",\"name\":\"Köy\"}}]";
		String far = "[{\"type\":\"node\",\"lat\":40.0081,\"lon\":32.0,\"tags\":{\"place\":\"village\"}}]";
		assertTrue(YoldaRadarCore.settlementNear(new JSONArray(near), la, lo));
		assertFalse(YoldaRadarCore.settlementNear(new JSONArray(far), la, lo));
		// kasaba noktası 900 m: içi (kasaba 1500 m); yol (way) ve yerleşim olmayan düğüm sayılmaz
		assertTrue(YoldaRadarCore.settlementNear(new JSONArray(far.replace("village", "town")), la, lo));
		assertFalse(YoldaRadarCore.settlementNear(new JSONArray(near.replace("village", "locality")), la, lo));
		assertFalse(YoldaRadarCore.settlementNear(new JSONArray(near.replace("\"node\"", "\"way\"")), la, lo));
		assertFalse(YoldaRadarCore.settlementNear(null, la, lo));
	}

	@Test
	public void hizSiniriGunlugu() {
		assertEquals("Hız sınırı ~50 · yol: primary · tahmin, yerleşim içi", YoldaRadarCore.limitLog("primary", 50, true, true));
		assertEquals("Hız sınırı 82 · yol: trunk · haritada yazılı", YoldaRadarCore.limitLog("trunk", 82, false, false));
		assertEquals("Hız sınırı yok · yol: service", YoldaRadarCore.limitLog("service", 0, false, false));
	}

	@Test
	public void yerlesimdeHizSiniri() {
		// adsız ana yol: dışarıda 90, yerleşim alanında 50
		assertEquals(90, YoldaRadarCore.estimateLimit("primary", false, null, null, false, null, false));
		assertEquals(50, YoldaRadarCore.estimateLimit("primary", false, null, null, false, null, true));
		// bölünmüş yol (trunk): dışarıda 110, yerleşimde 70
		assertEquals(110, YoldaRadarCore.estimateLimit("trunk", true, null, "D100", false, null, false));
		assertEquals(70, YoldaRadarCore.estimateLimit("trunk", true, null, "D100", false, null, true));
		// otoyol yerleşimde de 120; açık kırsal etiketi yerleşim alanından önce gelir
		assertEquals(120, YoldaRadarCore.estimateLimit("motorway", true, null, "O-4", false, null, true));
		assertEquals(90, YoldaRadarCore.estimateLimit("primary", false, null, null, false, "TR:rural", true));
		// eski imza değişmedi
		assertEquals(90, YoldaRadarCore.estimateLimit("primary", false, null, "D 750", false, null));
	}
}
