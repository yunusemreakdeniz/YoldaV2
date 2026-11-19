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
