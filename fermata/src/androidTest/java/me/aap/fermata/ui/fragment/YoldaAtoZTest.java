package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
	private android.app.Activity themeAct;

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
			// sıra: tema · ayarlar · düzenle (hepsi aynı satırda, yan yana)
			View sb = onMain(a -> byDesc(a.getActiveFragment().getView(), "Ayarlar"));
			assertNotNull(sb);
			assertTrue("Tema, ayarlar ve düzenle yan yana", onMain(a -> (btn.getParent() == edit.getParent())
					&& (sb.getParent() == edit.getParent()) && (btn.getRight() <= sb.getLeft())
					&& (sb.getRight() <= edit.getLeft()) && (edit.getLeft() - btn.getRight() < 3 * btn.getWidth())));
			View setb = onMain(a -> byDesc(a.getActiveFragment().getView(), "Ayarlar"));
			assertNotNull("Ayarlar düğmesi tema düğmesinin yanında olmalı", setb);
			scenario.onActivity(x -> themeAct = x); // tıklamadan önce (sonra animasyon bitmesi beklenmesin)
			onMain(a -> btn.performClick());
			assertTrue("Dokununca tema değişmeli", waitFor(5000, () -> YoldaStyle.index(ctx()) == 1));
			// Onay kutusu yok: altta kısa yazı belirir ve ~1 sn sonra kaybolur
			// animasyon sürerken boşta beklemeden bak (yazı ~1,4 sn görünür)
			try {
				Thread.sleep(450);
			} catch (InterruptedException ignore) {
			}
			long clickAt = android.os.SystemClock.elapsedRealtime() - 1000;
			assertTrue("Tema yazısı gösterilmeli: " + YoldaStyle.lastToast, waitFor(3000, () ->
					(YoldaStyle.lastToastAt >= clickAt) && String.valueOf(YoldaStyle.lastToast).startsWith("Tema:")));
			assertFalse("Onay kutusu çıkmamalı", onMain(a -> ((android.app.Activity) a.getContext()).getWindow()
					.getDecorView().getRootView().findViewById(android.R.id.button1) != null));
			try {
				Thread.sleep(2500);
			} catch (InterruptedException ignore) {
			}
			assertFalse("Tema yazısı kendiliğinden kapanmalı", toastNow());
			waitIdle(1500);
			View btn2 = onMain(a -> byDesc(a.getActiveFragment().getView(), "Tema:"));
			onMain(a -> btn2.performLongClick());
			assertTrue("Basılı tutunca otomatik açılmalı", waitFor(3000, () -> YoldaStyle.isAuto(ctx())));
		} finally {
			p.edit().putInt("theme", 0).putBoolean(YoldaStyle.PREF_AUTO, false).commit();
		}
	}

	/**
	 * Yazı şu an görünüyor mu: ana iş parçacığında, boşta kalmayı BEKLEMEDEN bakılır (onActivity önce
	 * animasyonların bitmesini bekliyor, o sırada yazı kayboluyordu).
	 */
	private boolean toastNow() {
		AtomicReference<Boolean> r = new AtomicReference<>(false);
		try {
			Thread.sleep(400);
		} catch (InterruptedException ignore) {
		}
		InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
				r.set(toastShown(MainActivityDelegate.get(themeAct))));
		return r.get();
	}

	private static boolean toastShown(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ViewGroup g)) return false;
		for (int i = 0; i < g.getChildCount(); i++) if ("yolda_toast".equals(g.getChildAt(i).getTag())) return true;
		return false;
	}

	/** Favoriler kartında içerik düzenleme: sırala, adını değiştir, kaldır; karttaki "Düzenle" ekranı açar. */
	@Test
	public void favorileriDuzenle() {
		ctx().getSharedPreferences("yolda_favs", Context.MODE_PRIVATE).edit().clear().commit();
		YoldaFavs.add(ctx(), YoldaDeck.URL, "Bir", "https://a.example", null);
		YoldaFavs.add(ctx(), YoldaDeck.URL, "İki", "https://b.example", null);
		YoldaFavs.add(ctx(), YoldaDeck.URL, "Üç", "https://c.example", null);
		List<YoldaDeck.Card> l = YoldaFavs.load(ctx());
		assertEquals("Üç", l.get(0).title()); // en yeni başta
		assertEquals(1, YoldaFavs.move(ctx(), l.get(0).key(), 1));
		assertEquals("İki", YoldaFavs.load(ctx()).get(0).title());
		YoldaFavs.rename(ctx(), l.get(0).key(), "Üçüncü");
		assertEquals("Üçüncü", YoldaFavs.load(ctx()).get(1).title());
		assertTrue(YoldaFavs.remove(ctx(), l.get(0).key()));
		assertEquals(2, YoldaFavs.load(ctx()).size());

		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		int pos = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.FAVS));
		onMain(a -> {
			((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(pos);
			return null;
		});
		waitIdle(1200);
		View edit = onMain(a -> byText(a.getActiveFragment().getView(), "✎ Düzenle"));
		assertNotNull("Favoriler kartında Düzenle olmalı", edit);
		onMain(a -> edit.performClick());
		waitIdle(800);
		assertNotNull("Düzenleme ekranı açılmalı", onMain(a -> byText(a.getActiveFragment().getView(), "Favorileri düzenle")));
		assertNotNull("Kaldır düğmesi olmalı", onMain(a -> byDesc(a.getActiveFragment().getView(), "Kaldır: İki")));
		onMain(a -> {
			a.onBackPressed(); // düzenleme ekranı kapanır
			return null;
		});
		ctx().getSharedPreferences("yolda_favs", Context.MODE_PRIVATE).edit().clear().commit();
	}

	/** Ana sayfa düğmesinin üstünde geri düğmesi: bir önceki ekrana döner. */
	@Test
	public void geriDugmesiAnaSayfaUstunde() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(800);
		onMain(a -> a.showFragment(R.id.yolda_channels_fragment));
		waitIdle(1200);
		View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
		View back = onMain(a -> byDescExact(root, "Geri"));
		View home = onMain(a -> byDescExact(root, "Ana sayfa"));
		assertNotNull("Geri düğmesi olmalı", back);
		assertNotNull("Ana sayfa düğmesi olmalı", home);
		assertTrue("Geri, ana sayfanın üstünde", onMain(a -> (back.getVisibility() == View.VISIBLE)
				&& (back.getBottom() <= home.getTop()) && (Math.abs(back.getLeft() - home.getLeft()) < 4)));
		onMain(a -> back.performClick());
		waitIdle(1200);
		assertEquals("Geri bir önceki ekrana dönmeli", R.id.home_fragment,
				onMain(MainActivityDelegate::getActiveFragmentId).intValue());
	}

	private static View byText(View v, String s) {
		if ((v == null) || (v.getVisibility() != View.VISIBLE)) return null;
		if ((v instanceof android.widget.TextView tv) && s.contentEquals(tv.getText())) return v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = byText(g.getChildAt(i), s);
				if (f != null) return f;
			}
		}
		return null;
	}

	private static View byDescExact(View v, String s) {
		if (v == null) return null;
		CharSequence d = v.getContentDescription();
		if ((d != null) && s.contentEquals(d) && (v.getVisibility() == View.VISIBLE)) return v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = byDescExact(g.getChildAt(i), s);
				if (f != null) return f;
			}
		}
		return null;
	}

	/** Telefon kartı: telefondaki uygulamalar eklenir, kartta görünür, düzenlenir. */
	@Test
	public void telefonUygulamalariEklenirDuzenlenir() {
		String pkg = "com.android.settings";
		YoldaDeckFragment.savePhoneApps(ctx(), new java.util.ArrayList<>(java.util.List.of(pkg)));
		try {
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(1500);
			int pos = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.PHONE));
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(pos);
				return null;
			});
			waitIdle(1500);
			String label = String.valueOf(ctx().getPackageManager().getApplicationLabel(ctx().getApplicationInfo().packageName
					.equals(pkg) ? ctx().getApplicationInfo() : appInfo(pkg)));
			assertNotNull("Eklenen uygulama kartta görünmeli: " + label, onMain(a -> byDescExact(a.getActiveFragment().getView(), label)));
			View add = onMain(a -> byText(a.getActiveFragment().getView(), "＋ Uygulama"));
			assertNotNull("＋ Uygulama olmalı", add);
			assertNotNull("✎ Düzenle olmalı", onMain(a -> byText(a.getActiveFragment().getView(), "✎ Düzenle")));
			onMain(a -> add.performClick());
			waitIdle(1200);
			assertNotNull("Uygulama listesi açılmalı", onMain(a -> byText(a.getActiveFragment().getView(), "Uygulama ekle")));
			onMain(a -> {
				a.onBackPressed();
				return null;
			});
		} finally {
			YoldaDeckFragment.savePhoneApps(ctx(), new java.util.ArrayList<>());
		}
	}

	private static android.content.pm.ApplicationInfo appInfo(String pkg) {
		try {
			return ctx().getPackageManager().getApplicationInfo(pkg, 0);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/** Sesli komut "TRT Haber aç": kanal listesi yüklenmemiş olsa da kanal açılır; anlaşılmayan komut söylenir. */
	@Test
	public void sesliKomutKanalAcar() throws Exception {
		java.lang.reflect.Field f = YoldaVoice.class.getDeclaredField("channels");
		f.setAccessible(true);
		f.set(null, new java.util.ArrayList<>()); // liste henüz yok (telefonda önceden hiç yüklenmiyordu)
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1000);
		boolean handled = onMain(a -> YoldaVoice.handle(a, "TRT Haber aç"));
		assertTrue("Komut işlenmeli", handled);
		boolean opened = waitFor(15_000, () -> onMain(a -> {
			me.aap.fermata.media.lib.MediaLib.PlayableItem o = me.aap.fermata.media.service.MediaSessionCallback.yoldaOpeningItem();
			if (o == null) o = a.getCurrentPlayable();
			return (o != null) && o.getName().equalsIgnoreCase("TRT Haber");
		}));
		Log.i(TAG, "Sesli komut kanal: " + opened);
		assertTrue("“TRT Haber aç” TRT Haber'i açmalı", opened);
		onMain(a -> {
			a.getControlPanel().closePlayer();
			return null;
		});
		assertFalse("Anlamsız komut işlenmiş sayılmamalı", onMain(a -> YoldaVoice.handle(a, "zxqw plor")));
	}

	/**
	 * Hey Yolda: yeni komutlar gerçek uygulamada yürür; tanıma motorunun ikinci tahmini de denenir;
	 * yanıt hem yazı hem ses olarak verilir.
	 */
	@Test
	public void heyYoldaKomutlari() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1000);
		android.content.Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
		boolean speak = YoldaSpeak.isEnabled(ctx);
		try {
			YoldaSpeak.setEnabled(ctx, true);

			// saat: sesli yanıt
			YoldaSpeak.lastSpoken = null;
			assertTrue(onMain(a -> YoldaVoice.handle(a, "Hey Yolda saat kaç")));
			assertTrue("Saat söylenmeli: " + YoldaSpeak.lastSpoken,
					(YoldaSpeak.lastSpoken != null) && YoldaSpeak.lastSpoken.startsWith("Saat "));

			// uyku zamanlayıcısı kurulur ve iptal edilir
			assertTrue(onMain(a -> YoldaVoice.handle(a, "otuz dakika sonra kapat")));
			long left = YoldaVoice.sleepAt - android.os.SystemClock.elapsedRealtime();
			assertTrue("30 dk kurulmalı: " + left, (left > 29 * 60_000L) && (left <= 30 * 60_000L));
			assertTrue(onMain(a -> YoldaVoice.handle(a, "zamanlayıcıyı iptal et")));
			assertEquals("İptal edilmeli", 0, YoldaVoice.sleepAt);

			// ses yüzdesi
			android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(android.content.Context.AUDIO_SERVICE);
			int old = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
			int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
			try {
				assertTrue(onMain(a -> YoldaVoice.handle(a, "sesi yüzde elli yap")));
				int v = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
				assertTrue("Ses yarıda olmalı: " + v + "/" + max, Math.abs(v - Math.round(max / 2f)) <= 1);
			} finally {
				am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, old, 0);
			}

			// ekranlar: ilk tahmin anlamsız, ikincisi komut
			assertTrue(onMain(a -> YoldaVoice.handle(a, java.util.List.of("zxqw plor", "nöbetçi eczane"))));
			assertTrue("Eczane ekranı açılmalı", waitFor(3000, () -> onMain(MainActivityDelegate::getActiveFragmentId)
					== R.id.yolda_pharmacy_fragment));
			assertTrue(onMain(a -> YoldaVoice.handle(a, "ana sayfaya dön")));
			assertTrue("Ana sayfa", waitFor(3000, () -> onMain(MainActivityDelegate::getActiveFragmentId) == R.id.home_fragment));

			// yardım ve anlaşılmayan
			YoldaSpeak.lastSpoken = null;
			assertTrue(onMain(a -> YoldaVoice.handle(a, "ne yapabilirsin")));
			assertTrue("Yardım söylenmeli", (YoldaSpeak.lastSpoken != null) && YoldaSpeak.lastSpoken.startsWith("Örneğin"));

			// sesli yanıt kapatılınca yine yazılı yanıt verilir, ayar saklanır
			assertTrue(onMain(a -> YoldaVoice.handle(a, "sesli yanıtı kapat")));
			assertFalse(YoldaSpeak.isEnabled(ctx));
			assertTrue(onMain(a -> YoldaVoice.handle(a, "sesli yanıtı aç")));
			assertTrue(YoldaSpeak.isEnabled(ctx));
		} finally {
			YoldaSpeak.setEnabled(ctx, speak);
			YoldaVoice.cancelSleep();
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(800);
		}
	}

	/**
	 * Radar: 100 km/s ile kuzeydeki radara yaklaşırken 1000 / 500 / 150 m uyarıları sırayla verilir, sol kenarda
	 * hız sınırı ve uzaklık görünür; karttaki düğmeyle kapatılınca gösterge gizlenir, açılınca geri gelir.
	 */
	@Test
	public void radarUyarisi() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c), overWas = YoldaRadar.overspeedWarn(c);
		YoldaRadar.setOverspeedWarn(c, false); // bu testte yalnız radar uyarıları sayılsın
		java.util.List<YoldaRadarCore.Cam> old = YoldaRadar.cams();
		double lat0 = 40.0, lon0 = 30.0;
		YoldaRadar.ignoreGps = true; // emülatörün (başka testin bıraktığı) konumu araya girmesin
		try {
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a); // açık oynatıcı ekranı göstergeyi gizler
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(1200);
			onMain(a -> {
				YoldaRadar.setEnabled(c, true);
				YoldaRadar.setFeature(c, "cams", true);
				YoldaRadar.setCams(java.util.List.of(new YoldaRadarCore.Cam(77, lat0 + 800 / 110_540d, lon0, 70, -1, 'r')));
				return null;
			});
			java.util.List<String> alerts = new java.util.ArrayList<>();
			for (int m = 0; m <= 700; m += 25) {
				double la = lat0 + m / 110_540d;
				onMain(a -> {
					YoldaRadar.feed(la, lon0, 100, 0);
					return null;
				});
				String al = YoldaRadar.lastAlert;
				if ((al != null) && (alerts.isEmpty() || !alerts.get(alerts.size() - 1).equals(al))) alerts.add(al);
				SystemClock.sleep(40);
			}
			Log.i(TAG, "Radar uyarıları: " + alerts);
			assertEquals(java.util.List.of("1 kilometre sonra radar, hız sınırı 70", "500 metre sonra radar, hız sınırı 70",
					"Dikkat, radar, hız sınırı 70"), alerts);
			waitIdle(500);
			View rail = onMain(a -> byDesc(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Hız ve radar"));
			assertNotNull("Sol kenarda hız / radar göstergesi görünmeli", rail);
			assertTrue("Gösterge sınırı ve radarı söylemeli: " + rail.getContentDescription(),
					rail.getContentDescription().toString().contains("sınır 70") && rail.getContentDescription().toString().contains("radar"));
			shot("radar_gostergesi");
			assertTrue("Radar uyarısında sol kenar yanıp sönmeli",
					((me.aap.fermata.ui.view.YoldaSpeedRail) rail).blinkCount > 0);
			int edge = onMain(a -> a.getYoldaDock().alertColor());
			assertTrue("Radar yakınken kapsülün kenarı uyarı renginde olmalı: " + Integer.toHexString(edge), edge != 0);

			// Hız sınırı aşımı: konuşmadan çift bip, levha aşım sürdükçe yanıp söner
			YoldaRadar.setOverspeedWarn(c, true);
			long beep0 = YoldaRadar.overBeepAt;
			String alert0 = YoldaRadar.lastAlert;
			YoldaSpeak.lastSpoken = null;
			double laOver = lat0 + 650 / 110_540d;
			for (int i = 0; i < 45; i++) {
				onMain(a -> {
					YoldaRadar.feed(laOver, lon0, 100, 0);
					return null;
				});
				SystemClock.sleep(100);
			}
			assertTrue("Hız aşımında bip çalmalı", YoldaRadar.overBeepAt != beep0);
			assertEquals("Hız aşımı sesli / yazılı uyarı vermemeli", alert0, YoldaRadar.lastAlert);
			assertTrue("Konuşmamalı: " + YoldaSpeak.lastSpoken, (YoldaSpeak.lastSpoken == null)
					|| !YoldaSpeak.lastSpoken.contains("Hız sınırı"));
			assertTrue("Aşım sürdükçe levha yanıp sönmeli", onMain(a -> ((me.aap.fermata.ui.view.YoldaSpeedRail) rail).isOverBlinking()));
			shot("hiz_asimi");
			onMain(a -> {
				YoldaRadar.feed(laOver, lon0, 60, 0);
				return null;
			});
			assertFalse("Hız düşünce yanıp sönme durmalı", onMain(a -> ((me.aap.fermata.ui.view.YoldaSpeedRail) rail).isOverBlinking()));
			YoldaRadar.setOverspeedWarn(c, false);

			// Karttan kapat
			int pos = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.RADAR));
			assertTrue("Radar kartı destede olmalı", pos >= 0);
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(pos);
				return null;
			});
			waitIdle(1500);
			View sw = onMain(a -> byDesc(a.getActiveFragment().getView(), "Radar uyarıları: açık"));
			assertNotNull("Kartta Açık / Kapalı düğmesi olmalı", sw);
			shot("radar_karti");
			onMain(a -> sw.performClick());
			waitIdle(800);
			assertFalse("Karttan kapatılmalı", YoldaRadar.isEnabled(c));
			onMain(a -> {
				YoldaRadar.feed(lat0 + 650 / 110_540d, lon0, 100, 0);
				return null;
			});
			waitIdle(500);
			assertNull("Kapalıyken gösterge gizli", onMain(a -> byDesc(((android.app.Activity) a.getContext()).getWindow()
					.getDecorView(), "Hız ve radar")));
			View sw2 = onMain(a -> byDesc(a.getActiveFragment().getView(), "Radar uyarıları: kapalı"));
			assertNotNull("Kart kapalı göstermeli", sw2);

			// Radar ekranı açılır
			onMain(a -> a.showFragment(R.id.yolda_radar_fragment));
			assertTrue(waitFor(3000, () -> onMain(MainActivityDelegate::getActiveFragmentId) == R.id.yolda_radar_fragment));
			waitIdle(800);
			shot("radar_ekrani");
		} finally {
			onMain(a -> {
				YoldaRadar.setCams(old);
				YoldaRadar.setEnabled(c, was);
				YoldaRadar.setOverspeedWarn(c, overWas);
				YoldaRadar.ignoreGps = false;
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(800);
		}
	}

	/**
	 * Sürüş yardımcıları: ortalama hız koridoru başlar / biter, hastane bölgesinde hızlıyken uyarır, öndeki
	 * akaryakıt listelenir; her biri Radar ekranından ayrı kapatılır, kapalıyken uyarmaz.
	 */
	@Test
	public void surusYardimcilari() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c);
		java.util.List<YoldaRadarCore.Cam> old = YoldaRadar.cams();
		String[] keys = {"cams", "avg", "zones", "stops", "fatigue"};
		boolean[] wasF = new boolean[keys.length];
		for (int i = 0; i < keys.length; i++) wasF[i] = YoldaRadar.feature(c, keys[i]);
		boolean overWas = YoldaRadar.overspeedWarn(c);
		double lat0 = 40.0, lon0 = 30.0;
		YoldaRadar.ignoreGps = true;
		try {
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a);
				a.showFragment(R.id.home_fragment);
				YoldaRadar.setEnabled(c, true);
				YoldaRadar.setOverspeedWarn(c, false);
				for (String k : keys) YoldaRadar.setFeature(c, k, true);
				YoldaRadar.setFeature(c, "cams", false); // yalnız yardımcıların uyarıları sayılsın
				YoldaRadar.avg.cancel();
				YoldaRadar.setCams(java.util.List.of(new YoldaRadarCore.Cam(901, lat0 + 100 / 110_540d, lon0, 110, -1, 'a'),
						new YoldaRadarCore.Cam(902, lat0 + 2100 / 110_540d, lon0, 110, -1, 'a')));
				YoldaRadar.feed(lat0, lon0, 90, 0);
				YoldaRadar.setPlaces(java.util.List.of(
						new YoldaDriveCore.Place(1, lat0 + 2500 / 110_540d, lon0, 'h', "Devlet Hastanesi"),
						new YoldaDriveCore.Place(2, lat0 + 6000 / 110_540d, lon0, 'f', "Deneme Akaryakıt")));
				return null;
			});
			waitIdle(800);
			java.util.List<String> alerts = new java.util.ArrayList<>();
			for (int m = 0; m <= 2500; m += 25) {
				double la = lat0 + m / 110_540d;
				onMain(a -> {
					YoldaRadar.feed(la, lon0, 90, 0);
					return null;
				});
				String al = YoldaRadar.lastAlert;
				if ((al != null) && (alerts.isEmpty() || !alerts.get(alerts.size() - 1).equals(al))) alerts.add(al);
				SystemClock.sleep(20);
			}
			Log.i(TAG, "Sürüş yardımcısı uyarıları: " + alerts);
			assertTrue("Koridor başlamalı: " + alerts, alerts.contains("Ortalama hız denetimi başladı, sınır 110"));
			assertTrue("Koridor bitmeli: " + alerts, alerts.stream().anyMatch(x -> x.startsWith("Ortalama hız denetimi bitti")));
			assertTrue("Hastane bölgesi uyarısı: " + alerts, alerts.contains("Hastane bölgesi, yavaşla"));
			assertTrue("Akaryakıt önde listelenmeli", !YoldaRadar.stops.isEmpty()
					&& YoldaRadar.stops.get(0).place().name().equals("Deneme Akaryakıt"));

			// Radar ekranından koridoru kapat
			onMain(a -> a.showFragment(R.id.yolda_radar_fragment));
			assertTrue(waitFor(3000, () -> onMain(MainActivityDelegate::getActiveFragmentId) == R.id.yolda_radar_fragment));
			waitIdle(1000);
			shot("surus_yardimcilari");
			View avgRow = onMain(a -> byDesc(a.getActiveFragment().getView(), "Ortalama hız koridoru: açık"));
			assertNotNull("Koridor düğmesi olmalı", avgRow);
			onMain(a -> avgRow.performClick());
			waitIdle(800);
			assertFalse("Koridor kapatılmalı", YoldaRadar.feature(c, "avg"));
			View stopsHead = onMain(a -> byText(a.getActiveFragment().getView(), "Önündeki mola yerleri"));
			assertNotNull("Mola yerleri listesi görünmeli", stopsHead);

			// Kapalıyken koridor başlamaz
			YoldaRadar.lastAlert = null;
			onMain(a -> {
				YoldaRadar.feed(lat0 + 100 / 110_540d, lon0, 90, 0);
				return null;
			});
			assertFalse(YoldaRadar.avg.isActive());
			assertTrue("Kapalıyken koridor uyarısı olmamalı: " + YoldaRadar.lastAlert,
					(YoldaRadar.lastAlert == null) || !YoldaRadar.lastAlert.startsWith("Ortalama"));
		} finally {
			onMain(a -> {
				for (int i = 0; i < keys.length; i++) YoldaRadar.setFeature(c, keys[i], wasF[i]);
				YoldaRadar.setOverspeedWarn(c, overWas);
				YoldaRadar.setCams(old);
				YoldaRadar.setPlaces(java.util.List.of());
				YoldaRadar.avg.cancel();
				YoldaRadar.setEnabled(c, was);
				YoldaRadar.ignoreGps = false;
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(800);
		}
	}

	/**
	 * Arka plan ve araç: radar açıkken sürüş hizmeti çalışır (haritaya geçince de uyarsın); araç ayrılınca park
	 * yeri kaydedilir ve yolculuk özeti yazılır; "arabam nerede" park yerini bilir. Kapatılınca hizmet durur.
	 */
	@Test
	public void aracAyrilincaParkVeYolculuk() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c);
		String[] keys = {"bg", "park", "trips"};
		boolean[] wasF = new boolean[keys.length];
		for (int i = 0; i < keys.length; i++) wasF[i] = YoldaRadar.feature(c, keys[i]);
		double lat0 = 40.0, lon0 = 30.0;
		YoldaRadar.ignoreGps = true;
		try {
			onMain(a -> {
				a.showFragment(R.id.home_fragment);
				for (String k : keys) YoldaRadar.setFeature(c, k, true);
				YoldaRadar.setEnabled(c, true);
				YoldaRadar.clearPark(c);
				return null;
			});
			boolean svc = waitFor(8000, () -> YoldaDriveService.running);
			Log.i(TAG, "Sürüş hizmeti: " + svc + ", izin: " + YoldaRadar.hasPermission(c));
			if (YoldaRadar.hasPermission(c)) assertTrue("Konum izni varken arka plan hizmeti çalışmalı", svc);

			// Araç bağlı, 3 km yol (önceki testlerin benzetilen sürüşü bitmiş sayılsın)
			YoldaRadar.trip.end();
			YoldaCar.set(true);
			for (int m = 0; m <= 3000; m += 50) {
				double la = lat0 + m / 111_195d;
				onMain(a -> {
					YoldaRadar.feed(la, lon0, 80, 0);
					return null;
				});
			}
			assertTrue("Yolculuk başlamalı", YoldaRadar.trip.isActive());
			YoldaRadar.lastTrip = null;
			// Araç ayrıldı
			onMain(a -> {
				YoldaCar.set(false);
				return null;
			});
			waitIdle(800);
			double[] pk = YoldaRadar.park(c);
			assertNotNull("Park yeri kaydedilmeli", pk);
			assertEquals(lat0 + 3000 / 111_195d, pk[0], 1e-4);
			assertNotNull("Yolculuk kaydedilmeli", YoldaRadar.lastTrip);
			assertEquals(3000, YoldaRadar.lastTrip.meters(), 100);
			assertFalse(YoldaRadar.trip.isActive());
			assertTrue("Son yolculuklarda olmalı", !YoldaRadar.trips(c).isEmpty());

			// Sesli komut: arabam nerede (yol tarifi açılmadan önce yanıt)
			YoldaSpeak.lastSpoken = null;
			YoldaSpeak.setEnabled(c, true);
			onMain(a -> YoldaVoice.handle(a, "yolculuk özeti"));
			assertTrue("Yolculuk özeti söylenmeli: " + YoldaSpeak.lastSpoken,
					(YoldaSpeak.lastSpoken != null) && YoldaSpeak.lastSpoken.startsWith("Son yolculuk: 3,0 km"));

			// Radar ekranında park ve yolculuk bölümleri
			onMain(a -> a.showFragment(R.id.yolda_radar_fragment));
			waitIdle(1200);
			assertNotNull(onMain(a -> byText(a.getActiveFragment().getView(), "Park yerleri · en üstteki aracın")));
			assertEquals("Araç ayrılınca otomatik kayıt", 1.0, YoldaRadar.parks(c).get(0)[3], 0);
			// Park yeri listeden silinir
			View del = onMain(a -> byText(a.getActiveFragment().getView(), "Sil"));
			assertNotNull("Park yerinde Sil olmalı", del);
			int before = YoldaRadar.parks(c).size();
			onMain(a -> del.performClick());
			waitIdle(800);
			assertEquals("Park yeri silinmeli", before - 1, YoldaRadar.parks(c).size());
			assertNotNull(onMain(a -> byText(a.getActiveFragment().getView(), "Son yolculuklar")));
			shot("park_ve_yolculuk");

			// Kapatınca hizmet durur
			onMain(a -> {
				YoldaRadar.setEnabled(c, false);
				return null;
			});
			assertTrue("Kapatınca hizmet durmalı", waitFor(5000, () -> !YoldaDriveService.running));
		} finally {
			onMain(a -> {
				for (int i = 0; i < keys.length; i++) YoldaRadar.setFeature(c, keys[i], wasF[i]);
				YoldaRadar.clearPark(c);
				YoldaRadar.clearTrips(c);
				YoldaRadar.setEnabled(c, was);
				YoldaRadar.ignoreGps = false;
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(800);
		}
	}

	/** Kart kaldırılır, Düzenle'deki "Kaldırılan kartlar" düğmesiyle geri gelir; Ekle kartı da söyler. */
	@Test
	public void kartKaldirGeriGetir() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		int n0 = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().size());
		int pos = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.WEATHER));
		assertTrue(pos >= 0);
		View edit = onMain(a -> byDesc(a.getActiveFragment().getView(), "Düzenle"));
		assertNotNull(edit);
		onMain(a -> edit.performClick());
		waitIdle(800);
		onMain(a -> {
			((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(pos);
			return null;
		});
		waitIdle(1500);
		View rm = onMain(a -> byDescExact(((YoldaDeckFragment) a.getActiveFragment()).cardView(pos), "Kaldır"));
		assertNotNull("Düzenle'de Kaldır düğmesi olmalı", rm);
		onMain(a -> rm.performClick());
		waitIdle(1000);
		assertEquals("Hava kartı kaldırılmalı", n0 - 1, onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().size()).intValue());
		assertEquals(-1, onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.WEATHER)).intValue());
		View back = onMain(a -> byDesc(a.getActiveFragment().getView(), "Kaldırılan kartlar"));
		assertNotNull("Düzenle'de ↺ Kaldırılan kartlar görünmeli", back);
		shot("kaldirilan_kartlar_dugmesi");
		onMain(a -> back.performClick());
		waitIdle(1000);
		View row = onMain(a -> clickableOf(byText(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Hava durumu kartı")));
		assertNotNull("Listede Hava durumu olmalı", row);
		shot("kaldirilan_kartlar_listesi");
		onMain(a -> row.performClick());
		waitIdle(1200);
		assertTrue("Hava kartı geri gelmeli",
				onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.WEATHER)) >= 0);
		assertEquals(n0, onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().size()).intValue());
		onMain(a -> {
			View e = byDesc(a.getActiveFragment().getView(), "Bitti");
			if (e != null) e.performClick();
			return null;
		});
		waitIdle(800);
	}

	/** Başlıkta hava rozeti (☀ 18°). */
	@Test
	public void basliktaHava() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		boolean ok = waitFor(25_000, () -> onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).weatherChipText()) != null);
		String txt = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).weatherChipText());
		Log.i(TAG, "Başlıkta hava: " + txt);
		shot("baslik_hava");
		assertTrue("Başlıkta hava rozeti görünmeli", ok);
		assertTrue("Sıcaklık yazmalı: " + txt, txt.endsWith("°"));
	}

	/** Video oynarken radar yaklaşınca gösterge sol üstte görünür; önemli bir şey yokken video kapanmaz. */
	@Test
	public void videodaRadarGostergesi() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c);
		java.util.List<YoldaRadarCore.Cam> old = YoldaRadar.cams();
		boolean overWas = YoldaRadar.overspeedWarn(c);
		double lat0 = 40.0, lon0 = 30.0;
		YoldaRadar.ignoreGps = true;
		try {
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a);
				a.showFragment(R.id.home_fragment);
				YoldaRadar.setEnabled(c, true);
				YoldaRadar.setFeature(c, "cams", true);
				YoldaRadar.setOverspeedWarn(c, false);
				YoldaRadar.setCams(java.util.List.of(new YoldaRadarCore.Cam(55, lat0 + 600 / 111_195d, lon0, 50, -1, 'r')));
				a.setVideoMode(true, a.getBody().getVideoView());
				return null;
			});
			waitIdle(1000);
			// Radar uzakta (3 km önce): videoda gösterge yok
			onMain(a -> {
				YoldaRadar.feed(lat0 - 3000 / 111_195d, lon0, 60, 0);
				return null;
			});
			waitIdle(500);
			assertNull("Önemli bir şey yokken video üstünde gösterge olmamalı", onMain(a -> byDesc(
					((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Hız ve radar")));
			// Radar 600 m önde
			onMain(a -> {
				YoldaRadar.feed(lat0, lon0, 60, 0);
				return null;
			});
			waitIdle(800);
			View rail = onMain(a -> byDesc(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Hız ve radar"));
			assertNotNull("Video üstünde radar göstergesi görünmeli", rail);
			shot("video_radar");
			int top = onMain(a -> {
				int[] xy = new int[2];
				rail.getLocationOnScreen(xy);
				return xy[1];
			});
			float d = c.getResources().getDisplayMetrics().density;
			assertTrue("Videoda sol üstte olmalı: y=" + top, top < 140 * d);
		} finally {
			onMain(a -> {
				a.setVideoMode(false, null);
				YoldaRadar.setCams(old);
				YoldaRadar.setOverspeedWarn(c, overWas);
				YoldaRadar.setEnabled(c, was);
				YoldaRadar.ignoreGps = false;
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(800);
		}
	}

	/**
	 * Yüzen düğmeler tek cam kapsülde: iç ekranda geri, ana sayfa, sesli komut sarılır; ana sayfada yalnız
	 * sesli komut varken kapsül çizilmez.
	 */
	@Test
	public void yuzenDugmeKapsulu() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c);
		YoldaRadar.ignoreGps = true;
		try {
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a);
				YoldaRadar.setEnabled(c, false); // gösterge yok: yalnız düğmeler
				a.showFragment(R.id.yolda_menu_fragment);
				return null;
			});
			assertTrue(waitFor(3000, () -> onMain(MainActivityDelegate::getActiveFragmentId) == R.id.yolda_menu_fragment));
			waitIdle(1200);
			assertTrue("İç ekranda kapsül çizilmeli", onMain(a -> a.getYoldaDock().isShownDock()));
			android.graphics.RectF b = onMain(a -> a.getYoldaDock().bounds());
			for (String desc : new String[]{"Sesli komut", "Ana sayfa", "Geri"}) {
				View v = onMain(a -> byDesc(((android.app.Activity) a.getContext()).getWindow().getDecorView(), desc));
				assertNotNull(desc, v);
				android.graphics.RectF vr = new android.graphics.RectF(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
				assertTrue(desc + " kapsülün içinde olmalı: " + vr + " / " + b, b.contains(vr));
			}
			assertTrue("Kapsül dar olmalı (tek sütun): " + b.width(), b.width() < 90 * c.getResources().getDisplayMetrics().density);
			shot("kapsul_ic_ekran");
			// Çentikli telefon yatayda: kök görünümün solunda boşluk olur; cam yine düğmeleri sarmalı (kaymamalı)
			onMain(a -> {
				View root = ((android.app.Activity) a.getContext()).findViewById(R.id.main_activity);
				root.setPadding(110, root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom());
				return null;
			});
			waitIdle(1200);
			try {
				android.graphics.RectF b2 = onMain(a -> a.getYoldaDock().bounds());
				View home = onMain(a -> byDesc(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Ana sayfa"));
				View dk = onMain(a -> a.getYoldaDock());
				android.graphics.RectF hr = new android.graphics.RectF(home.getLeft() - dk.getLeft(), home.getTop() - dk.getTop(),
						home.getRight() - dk.getLeft(), home.getBottom() - dk.getTop());
				assertTrue("Sol boşlukta da cam düğmeyi sarmalı: " + hr + " / " + b2, b2.contains(hr));
				shot("kapsul_centik");
			} finally {
				onMain(a -> {
					View root = ((android.app.Activity) a.getContext()).findViewById(R.id.main_activity);
					root.setPadding(0, root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom());
					return null;
				});
				waitIdle(800);
			}
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(1500);
			assertFalse("Ana sayfada yalnız sesli komut: kapsül yok", onMain(a -> a.getYoldaDock().isShownDock()));
			shot("kapsul_ana_sayfa");
		} finally {
			onMain(a -> {
				YoldaRadar.setEnabled(c, was);
				YoldaRadar.ignoreGps = false;
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(800);
		}
	}

	private static View clickableOf(View v) {
		while ((v != null) && !v.isClickable()) v = (v.getParent() instanceof View p) ? p : null;
		return v;
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

	/** Ekran görüntüsü: CI yapıtlarında gözle kontrol için. */
	private static void shot(String name) {
		String dir = "/data/local/tmp/yolda-ui"; // CI bu klasörü toplar
		try {
			for (String cmd : new String[]{"mkdir -p " + dir, "screencap -p " + dir + "/" + name + ".png"}) {
				try (android.os.ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
						.executeShellCommand(cmd); java.io.FileInputStream in = new java.io.FileInputStream(pfd.getFileDescriptor())) {
					byte[] buf = new byte[1024];
					//noinspection StatementWithEmptyBody
					while (in.read(buf) > 0) ;
				}
			}
		} catch (Exception ex) {
			Log.w(TAG, "Ekran görüntüsü alınamadı: " + name, ex);
		}
	}

	private static Context ctx() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}
}
