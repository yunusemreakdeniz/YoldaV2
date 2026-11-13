package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaNowPlaying;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * A'dan Z'ye kullanım: kartları düzenleme (sırala, renk, kaldır, geri getir), kullanıcı kartları (web,
 * YouTube kanalı, radyo kanalı), sesli kısa komutlar, kaldığın yerden devam, bölünmüş ekran, hava durumu.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaAtoZTest {
	private static final String TAG = "YoldaTest";
	private ActivityScenario<MainActivity> scenario;

	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO",
				"android.permission.POST_NOTIFICATIONS", "android.permission.READ_MEDIA_AUDIO",
				"android.permission.READ_MEDIA_VIDEO", "android.permission.ACCESS_FINE_LOCATION",
				"android.permission.ACCESS_COARSE_LOCATION"}) {
			try {
				InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		prefs().edit().putBoolean("wake_enabled", false).putInt("theme", 0).commit();
		scenario = ActivityScenario.launch(MainActivity.class);
		waitIdle(2500);
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
	}

	@After
	public void stop() {
		try {
			onMain(a -> {
				a.getControlPanel().closePlayer();
				YoldaNowPlaying.hide(a);
				if (a.getActiveFragment() instanceof YoldaDeckFragment d) {
					d.getDeck().reset();
					call(d, "afterChange", 0);
				}
				return null;
			});
			waitIdle(500);
		} catch (Throwable ignore) {
		}
		prefs().edit().putInt("theme", 0).commit();
		if (scenario != null) scenario.close();
	}

	// ---- kart düzenleme ----

	@Test
	public void duzenleSiralaRenkKaldirGeriGetir() {
		String before = onMain(a -> deck(a).getDeck().encode());
		int size = onMain(a -> deck(a).getDeck().size());
		assertTrue("Düzenle düğmesi olmalı", onMain(a -> click(a, "Düzenle")));
		waitIdle(900);
		assertTrue("Düzenle kipinde ‘Bitti’ görünmeli", onMain(a -> find(a, "Bitti") != null));

		// ▶ sağa taşı: sıra değişmeli
		assertTrue("‘Sağa taşı’ olmalı", onMain(a -> click(a, "Sağa taşı")));
		waitIdle(900);
		String moved = onMain(a -> deck(a).getDeck().encode());
		assertNotEquals("Sıra değişmeli", before, moved);
		assertEquals("Kart sayısı aynı kalmalı", size, onMain(a -> deck(a).getDeck().size()).intValue());

		// 🎨 renk: seçici açılır, geri ile kapanır, çökmez
		assertTrue("‘Rengi değiştir’ olmalı", onMain(a -> click(a, "Rengi değiştir")));
		waitIdle(900);
		onMain(a -> {
			a.onBackPressed();
			return null;
		});
		waitIdle(600);

		// ✕ kaldır: bir kart azalır, sonra "Ekle" menüsünden geri getirilebilir
		String removedType = onMain(a -> {
			YoldaDeckFragment d = deck(a);
			for (YoldaDeck.Card c : d.getDeck().cards()) if (c.isBuiltIn() && c.isRemovable()) return null;
			return null;
		});
		assertTrue("‘Kaldır’ olmalı", onMain(a -> click(a, "Kaldır")));
		waitIdle(900);
		assertEquals("Bir kart kaldırılmalı", size - 1, onMain(a -> deck(a).getDeck().size()).intValue());
		List<String> missing = onMain(a -> deck(a).getDeck().missingBuiltIns());
		assertEquals("Kaldırılan sabit kart geri getirilebilir olmalı", 1, missing.size());
		onMain(a -> {
			YoldaDeckFragment d = deck(a);
			call(d, "afterChange", d.getDeck().restore(missing.get(0)));
			return null;
		});
		waitIdle(700);
		assertEquals("Geri getirilmeli", size, onMain(a -> deck(a).getDeck().size()).intValue());

		// Bitti: düzenle kipi kapanır; kayıt kalıcı
		assertTrue(onMain(a -> click(a, "Bitti")));
		waitIdle(700);
		assertTrue("Düzenle kipi kapanmalı", onMain(a -> find(a, "Düzenle") != null));
		String saved = prefs().getString("deck", null);
		Log.i(TAG, "Deste kaydı var: " + (saved != null) + " · kaldırılan tür: " + removedType);
	}

	// ---- kullanıcı kartları ----

	@Test
	public void kullaniciKartlariAcilir() {
		// Web sayfası kartı → tarayıcı
		String webKey = addCard(YoldaDeck.URL, "Örnek", "https://example.com/");
		onMain(a -> {
			deck(a).openCard(webKey);
			return null;
		});
		assertTrue("Web kartı tarayıcıyı açmalı", waitFor(8000,
				() -> onMain(a -> a.getActiveFragmentId() == R.id.web_browser_fragment)));
		onMain(a -> a.getDeckBackButton().performClick());
		waitIdle(1200);
		for (int i = 0; (i < 4) && (onMain(MainActivityDelegate::getActiveFragmentId) != R.id.home_fragment); i++) {
			onMain(a -> a.getDeckBackButton().performClick());
			waitIdle(900);
		}
		assertEquals("Desteye dönmeli", R.id.home_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());

		// YouTube kanalı kartı → YouTube ekranı (aramayla)
		String ytKey = addCard(YoldaDeck.YT_CHANNEL, "TRT Belgesel", "TRT Belgesel");
		onMain(a -> {
			deck(a).openCard(ytKey);
			return null;
		});
		waitIdle(2500);
		int ytId = onMain(MainActivityDelegate::getActiveFragmentId);
		Log.i(TAG, "YouTube kanalı kartı ekranı: " + ctx().getResources().getResourceEntryName(ytId));
		assertNotEquals("YouTube kartı bir ekran açmalı", R.id.home_fragment, ytId);
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);

		// Radyo kanalı kartı → çalar
		MediaLib.PlayableItem fm = channel("TRT FM");
		String radioKey = addCard(YoldaDeck.RADIO_CHANNEL, fm.getName(), fm.getId());
		onMain(a -> {
			deck(a).openCard(radioKey);
			return null;
		});
		assertTrue("Radyo kartı çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
	}

	// ---- favoriler: kanal, radyo, YouTube kanalı, şarkı, web bir arada ----

	@Test
	public void favorilerKarisikVeKarttanCalar() {
		Context c = ctx();
		prefs().edit().remove("yolda_favs").putBoolean("yolda_favs_imported", true).commit();
		MediaLib.PlayableItem fm = channel("TRT FM");
		String[] e = YoldaFavs.entryOf(fm);
		assertNotNull("Radyo kanalı favori olabilmeli", e);
		YoldaFavs.add(c, e[0], e[1], e[2], e[3]);
		YoldaFavs.add(c, YoldaDeck.YT_CHANNEL, "TRT Belgesel", "TRT Belgesel", null);
		YoldaFavs.add(c, YoldaFavs.MUSIC_QUERY, "Sezen Aksu", "Sezen Aksu", null);
		YoldaFavs.add(c, YoldaDeck.URL, "example.com", "https://example.com/", null);
		assertFalse("Aynısı iki kez eklenmemeli", YoldaFavs.add(c, YoldaDeck.URL, "example.com", "https://example.com/", null));
		assertEquals(4, YoldaFavs.load(c).size());
		assertEquals("En yeni başta", YoldaDeck.URL, YoldaFavs.load(c).get(0).type());

		// Deste yeniden kurulunca Favoriler kartında kutucuklar görünür; radyoya dokununca çalar
		onMain(a -> a.showFragment(R.id.yolda_menu_fragment));
		waitIdle(600);
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		onMain(a -> {
			YoldaDeckFragment d = deck(a);
			call(d, "afterChange", d.getDeck().indexOf(YoldaDeck.FAVS));
			return null;
		});
		waitIdle(1500);
		assertTrue("Favori radyo kutucuğu görünmeli", waitFor(5000, () -> onMain(a -> find(a, "Radyo: TRT FM") != null)));
		assertTrue("Favori şarkı kutucuğu görünmeli", onMain(a -> find(a, "Müzik: Sezen Aksu") != null));
		assertTrue(onMain(a -> click(a, "Radyo: TRT FM")));
		assertTrue("Favori radyo çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));

		// Şimdi çalıyor: ★ durumu doğru; çıkar / ekle
		onMain(a -> {
			YoldaNowPlaying.show(a);
			return null;
		});
		waitIdle(1500);
		assertTrue("★ dolu görünmeli", waitFor(4000, () -> onMain(a -> find(a, "Favorilerden çıkar") != null)));
		assertTrue(onMain(a -> click(a, "Favorilerden çıkar")));
		waitIdle(500);
		assertFalse("Çıkarılmalı", YoldaFavs.contains(c, e[0], e[2]));
		assertTrue("Sesli komutla eklenmeli", onMain(a -> YoldaVoice.handle(a, "favorilere ekle")));
		assertTrue("Eklenmeli", YoldaFavs.contains(c, e[0], e[2]));
		onMain(YoldaNowPlaying::hide);

		// Listeden çıkarma
		String key = YoldaFavs.load(c).get(0).key();
		assertTrue(YoldaFavs.remove(c, key));
		assertEquals(3, YoldaFavs.load(c).size());
		prefs().edit().remove("yolda_favs").commit();
	}

	// ---- sesli kısa komutlar ----

	@Test
	public void sesliKisaKomutlar() {
		int t0 = YoldaStyle.index(ctx());
		assertTrue(onMain(a -> YoldaVoice.handle(a, "Tema değiştir")));
		waitIdle(800);
		assertNotEquals("Tema değişmeli", t0, YoldaStyle.index(ctx()));
		assertTrue("Saat sorusu yanıtlanmalı", onMain(a -> YoldaVoice.handle(a, "saat kaç")));
		assertTrue(onMain(a -> YoldaVoice.handle(a, "müzik aç")));
		waitIdle(1200);
		assertEquals(R.id.yolda_music, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		assertTrue(onMain(a -> YoldaVoice.handle(a, "ana sayfa")));
		waitIdle(1200);
		assertEquals(R.id.home_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		assertTrue(onMain(a -> YoldaVoice.handle(a, "Navigasyon aç")));
		waitIdle(1200);
		assertEquals(R.id.yolda_nav_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());

		// Oynatma komutları
		MediaLib.PlayableItem fm = channel("TRT FM");
		onMain(a -> {
			a.getBody().playItem(fm);
			return null;
		});
		assertTrue("TRT FM çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		assertTrue(onMain(a -> YoldaVoice.handle(a, "duraklat")));
		assertTrue("‘duraklat’ durdurmalı", waitFor(5000, () -> !onMain(a -> a.getMediaSessionCallback().isPlaying())));
		assertTrue(onMain(a -> YoldaVoice.handle(a, "devam et")));
		assertTrue("‘devam et’ sürdürmeli", waitFor(15_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		assertTrue(onMain(a -> YoldaVoice.handle(a, "sesi aç")));
		assertTrue(onMain(a -> YoldaVoice.handle(a, "sesi kıs")));
		assertFalse("Anlamsız söz işlenmemeli", onMain(a -> YoldaVoice.handle(a, "qqxz")));
	}

	// ---- kaldığın yerden devam ----

	@Test
	public void kaldiginYerdenDevam() {
		MediaLib.PlayableItem fm = channel("TRT FM");
		onMain(a -> {
			a.getBody().playItem(fm);
			return null;
		});
		assertTrue("TRT FM çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		waitIdle(2000);
		onMain(a -> {
			a.getControlPanel().closePlayer();
			YoldaNowPlaying.hide(a);
			return null;
		});
		waitIdle(1000);
		assertFalse(onMain(a -> a.getMediaSessionCallback().isPlaying()));
		// deste yeniden kurulur (son çalınan yüklenir)
		onMain(a -> a.showFragment(R.id.yolda_menu_fragment));
		waitIdle(700);
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(2500);
		onMain(a -> {
			deck(a).openCard(YoldaDeck.RESUME);
			return null;
		});
		assertTrue("Devam et son çalınanı çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		String name = onMain(a -> (a.getCurrentPlayable() != null) ? a.getCurrentPlayable().getName() : null);
		assertEquals("Son çalınan TRT FM olmalı", "TRT FM", name);
	}

	// ---- bölünmüş ekran ----

	@Test
	public void bolunmusEkranAcilipKapanir() {
		// Telefonda "videonun yanında harita": video küçük pencereye geçer, harita uygulaması açılır
		// (web haritası değil). Emülatörde harita uygulaması olmayabilir; istek ve küçük pencere denenmiş olmalı.
		onMain(a -> {
			call(a, "toggleSplit");
			return null;
		});
		waitIdle(2500);
		String log = YoldaLog.read(ctx());
		Log.i(TAG, "Video + harita: " + log.substring(Math.max(0, log.lastIndexOf("Video + harita"))));
		assertTrue("Telefonda harita uygulaması istenmeli", log.contains("Video + harita"));
		assertFalse("Web haritası açılmamalı", onMain(a -> a.getBody().isBothMode()));
		// Küçük pencereden çıkılınca düğmeler ve çubuk geri gelir
		onMain(a -> {
			a.yoldaPipChanged(true);
			a.yoldaPipChanged(false);
			return null;
		});
		assertFalse("Küçük pencere bitmeli", onMain(MainActivityDelegate::isYoldaPip));
		scenario.onActivity(act -> {
		});
		InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(
				android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
		waitIdle(1500);
	}

	// ---- hava durumu ----

	@Test
	public void havaDurumuCozumlenir() throws Exception {
		havaDurumuCozumlenir0();
	}

	/**
	 * Otomatik tema: gündüz ve gece için ayrı tema. Gün batımı hesabı doğru; araç gece kipine geçince (farlar)
	 * açık ekran kendiliğinden gece temasıyla yeniden kurulur, gündüze dönünce gündüz teması.
	 */
	@Test
	public void otomatikTemaGunduzGece() {
		// Ankara: 13 Kasım öğlen gündüz, akşam 20:00 ve sabah 05:00 gece; 21 Haziran 19:00 hâlâ gündüz
		assertFalse("Öğlen gündüz", YoldaStyle.sunDown(utc(2026, 11, 13, 9, 0), 39.9, 32.85));
		assertTrue("Akşam 20:00 gece", YoldaStyle.sunDown(utc(2026, 11, 13, 17, 0), 39.9, 32.85));
		assertTrue("Sabah 05:00 gece", YoldaStyle.sunDown(utc(2026, 11, 13, 2, 0), 39.9, 32.85));
		assertFalse("Yazın 19:00 gündüz", YoldaStyle.sunDown(utc(2026, 6, 21, 16, 0), 39.9, 32.85));
		assertTrue("Yazın 21:30 gece", YoldaStyle.sunDown(utc(2026, 6, 21, 18, 30), 39.9, 32.85));
		android.content.SharedPreferences p = ctx().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
		try {
			p.edit().putInt("theme", 0).putInt(YoldaStyle.PREF_DAY, 2).putInt(YoldaStyle.PREF_NIGHT, 5).commit();
			YoldaStyle.setCarNight(false);
			YoldaStyle.setAuto(ctx(), true);
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(2000);
			assertEquals("Gündüz teması", 2, YoldaStyle.index(ctx()));
			assertTrue("Açık ekran gündüz temasında", waitFor(8000, () -> "Gün batımı".equals(deckTheme())));

			YoldaStyle.setCarNight(true); // araç gece kipine geçti
			onMain(a -> {
				a.yoldaAutoThemeTick();
				return null;
			});
			waitIdle(1500);
			assertEquals("Gece teması", 5, YoldaStyle.index(ctx()));
			assertTrue("Ekran kendiliğinden gece temasına geçmeli: " + deckTheme(),
					waitFor(8000, () -> "Asil".equals(deckTheme())));
			assertTrue("Etiket otomatik gece", YoldaStyle.label(ctx()).contains("otomatik gece"));

			YoldaStyle.next(ctx()); // otomatikte "Tema" düğmesi o dilimin temasını değiştirir
			waitIdle(800);
			assertEquals("Gece teması değişmeli", 6, YoldaStyle.nightTheme(ctx()));
			assertEquals("Gündüz teması aynı kalmalı", 2, YoldaStyle.dayTheme(ctx()));

			YoldaStyle.setCarNight(false);
			onMain(a -> {
				a.yoldaAutoThemeTick();
				return null;
			});
			assertTrue("Gündüze dönünce gündüz teması: " + deckTheme(),
					waitFor(8000, () -> "Gün batımı".equals(deckTheme())));
		} finally {
			YoldaStyle.setAuto(ctx(), false);
			p.edit().putInt("theme", 0).commit();
			YoldaStyle.setCarNight(false);
		}
	}

	/** Ana sayfada düzenle düğmesinin yanındaki tema düğmesi: dokun → sıradaki tema, basılı tut → otomatik. */
	@Test
	public void temaDugmesiDuzenleYaninda() {
		android.content.SharedPreferences p = ctx().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
		try {
			p.edit().putInt("theme", 0).putBoolean(YoldaStyle.PREF_AUTO, false).commit();
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(1500);
			View btn = onMain(a -> byDesc(a.getActiveFragment().getView(), "Tema:"));
			assertNotNull("Tema düğmesi olmalı", btn);
			View edit = onMain(a -> byDesc(a.getActiveFragment().getView(), "Düzenle"));
			assertNotNull(edit);
			assertTrue("Tema düğmesi düzenle düğmesinin hemen solunda", onMain(a -> (btn.getParent() == edit.getParent())
					&& (btn.getRight() <= edit.getLeft()) && (edit.getLeft() - btn.getRight() < btn.getWidth())));
			View setb = onMain(a -> byDesc(a.getActiveFragment().getView(), "Ayarlar"));
			assertNotNull("Ayarlar düğmesi tema düğmesinin yanında olmalı", setb);
			onMain(a -> btn.performClick());
			assertTrue("Dokununca tema değişmeli", waitFor(5000, () -> YoldaStyle.index(ctx()) == 1));
			// Onay kutusu yok: altta kısa yazı belirir ve ~1 sn sonra kaybolur
			assertTrue("Tema yazısı görünmeli", waitFor(1500, () -> onMain(a -> toastShown(a))));
			assertFalse("Onay kutusu çıkmamalı", onMain(a -> ((android.app.Activity) a.getContext()).getWindow()
					.getDecorView().getRootView().findViewById(android.R.id.button1) != null));
			assertTrue("Tema yazısı kendiliğinden kapanmalı", waitFor(2500, () -> !onMain(a -> toastShown(a))));
			waitIdle(1500);
			View btn2 = onMain(a -> byDesc(a.getActiveFragment().getView(), "Tema:"));
			onMain(a -> btn2.performLongClick());
			assertTrue("Basılı tutunca otomatik açılmalı", waitFor(3000, () -> YoldaStyle.isAuto(ctx())));
		} finally {
			p.edit().putInt("theme", 0).putBoolean(YoldaStyle.PREF_AUTO, false).commit();
		}
	}

	private static boolean toastShown(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ViewGroup g)) return false;
		for (int i = 0; i < g.getChildCount(); i++) if ("yolda_toast".equals(g.getChildAt(i).getTag())) return true;
		return false;
	}

	/** Üstteki ayarlar düğmesi ayarlar (menü) ekranını açar. */
	@Test
	public void ayarlarDugmesiUstte() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		View b = onMain(a -> byDesc(a.getActiveFragment().getView(), "Ayarlar"));
		assertNotNull("Ayarlar düğmesi olmalı", b);
		onMain(a -> b.performClick());
		assertTrue("Ayarlar ekranı açılmalı", waitFor(3000, () -> onMain(MainActivityDelegate::getActiveFragmentId)
				== R.id.yolda_menu_fragment));
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(800);
	}

		private static View byDesc(View v, String prefix) {
		if (v == null) return null;
		CharSequence d = v.getContentDescription();
		if ((d != null) && d.toString().startsWith(prefix) && (v.getVisibility() == View.VISIBLE)) return v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = byDesc(g.getChildAt(i), prefix);
				if (f != null) return f;
			}
		}
		return null;
	}

	private static long utc(int y, int m, int d, int h, int min) {
		java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
		c.clear();
		c.set(y, m - 1, d, h, min, 0);
		return c.getTimeInMillis();
	}

	/** Açık destenin kurulduğu temanın adı. */
	private String deckTheme() {
		return onMain(a -> {
			if (!(a.getActiveFragment() instanceof YoldaDeckFragment d)) return "?";
			try {
				java.lang.reflect.Field f = YoldaDeckFragment.class.getDeclaredField("t");
				f.setAccessible(true);
				Object t = f.get(d);
				return (t instanceof YoldaStyle.Theme th) ? th.name : "?";
			} catch (Exception ex) {
				return "?" + ex;
			}
		});
	}

	private void havaDurumuCozumlenir0() throws Exception {
		String json = "{\"current\":{\"temperature_2m\":12.6,\"weather_code\":61,\"wind_speed_10m\":14.2},"
				+ "\"hourly\":{\"time\":[\"2026-11-12T19:00\",\"2026-11-12T20:00\"],\"temperature_2m\":[12.1,11.4],"
				+ "\"weather_code\":[61,3]}}";
		YoldaWeather.Now n = YoldaWeather.parse(json, "Kadıköy");
		assertEquals(13, n.temp());
		assertEquals(61, n.code());
		assertEquals(14, n.wind());
		assertEquals(2, n.hours().size());
		assertEquals("19:00", n.hours().get(0).time());
		assertEquals("Yağmurlu", YoldaWeather.text(61));
		assertEquals("Açık", YoldaWeather.text(0));
		assertEquals("Kapalı", YoldaWeather.text(3));
		assertNotNull(YoldaWeather.emoji(95));
	}

	// ---- yardımcılar ----

	private String addCard(String type, String title, String data) {
		return onMain(a -> {
			YoldaDeckFragment d = deck(a);
			int at = d.getDeck().add(type, title, data, null);
			call(d, "afterChange", at);
			return d.getDeck().get(at).key();
		});
	}

	private static YoldaDeckFragment deck(MainActivityDelegate a) {
		if (!(a.getActiveFragment() instanceof YoldaDeckFragment)) a.showFragment(R.id.home_fragment);
		return (YoldaDeckFragment) a.getActiveFragment();
	}

	private static Object call(Object o, String name, Object... args) {
		try {
			for (java.lang.reflect.Method m : o.getClass().getDeclaredMethods()) {
				if (m.getName().equals(name) && (m.getParameterCount() == args.length)) {
					m.setAccessible(true);
					return m.invoke(o, args);
				}
			}
			throw new IllegalArgumentException("Yöntem yok: " + name);
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/** Ekranda görünen, açıklaması {@code desc} olan ilk öğe (ortadaki karta en yakın). */
	private static View find(MainActivityDelegate a, String desc) {
		View root = ((android.app.Activity) a.getContext()).getWindow().getDecorView();
		List<View> all = new ArrayList<>();
		collect(root, desc, all);
		if (all.isEmpty()) return null;
		int cx = root.getWidth() / 2;
		View best = null;
		int bestD = Integer.MAX_VALUE;
		int[] loc = new int[2];
		for (View v : all) {
			v.getLocationOnScreen(loc);
			int d = Math.abs(loc[0] + v.getWidth() / 2 - cx);
			if (d < bestD) {
				bestD = d;
				best = v;
			}
		}
		return best;
	}

	private static void collect(View v, String desc, List<View> out) {
		if ((v.getVisibility() != View.VISIBLE) || !v.isShown()) return;
		CharSequence cd = v.getContentDescription();
		if ((cd != null) && desc.contentEquals(cd)) out.add(v);
		if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), desc, out);
	}

	private static boolean click(MainActivityDelegate a, String desc) {
		View v = find(a, desc);
		return (v != null) && v.performClick();
	}

	private MediaLib.PlayableItem channel(String name) {
		AtomicReference<MediaLib.PlayableItem> found = new AtomicReference<>();
		long end = SystemClock.elapsedRealtime() + 30_000;
		while ((found.get() == null) && (SystemClock.elapsedRealtime() < end)) {
			try {
				List<MediaLib.PlayableItem> l = onMainFuture(a -> a.getLib().getItem("TV").then(root ->
						((MediaLib.BrowsableItem) root).getPlayableChildren(true)));
				if (l != null) for (MediaLib.PlayableItem pi : l) if (name.equals(pi.getName())) found.set(pi);
			} catch (Exception ex) {
				Log.i(TAG, "Kanal listesi henüz hazır değil: " + ex);
			}
			if (found.get() == null) waitIdle(1000);
		}
		assertNotNull("Kanal bulunamadı: " + name, found.get());
		return found.get();
	}

	private interface Fn<T> {
		T apply(MainActivityDelegate a);
	}

	private <T> T onMain(Fn<T> fn) {
		AtomicReference<T> ref = new AtomicReference<>();
		AtomicReference<RuntimeException> fail = new AtomicReference<>();
		scenario.onActivity(act -> {
			try {
				ref.set(fn.apply(MainActivityDelegate.get(act)));
			} catch (RuntimeException ex) {
				fail.set(ex);
			}
		});
		if (fail.get() != null) throw fail.get();
		return ref.get();
	}

	private <T> T onMainFuture(Fn<me.aap.utils.async.FutureSupplier<T>> fn) throws Exception {
		me.aap.utils.async.FutureSupplier<T> f = onMain(fn);
		return f.get(null, 40, TimeUnit.SECONDS);
	}

	private static boolean waitFor(long ms, BooleanSupplier cond) {
		long end = SystemClock.elapsedRealtime() + ms;
		while (SystemClock.elapsedRealtime() < end) {
			if (cond.getAsBoolean()) return true;
			waitIdle(250);
		}
		return cond.getAsBoolean();
	}

	private static void waitIdle(long ms) {
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignore) {
		}
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
	}

	private static android.content.SharedPreferences prefs() {
		return ctx().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	private static Context ctx() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}
}
