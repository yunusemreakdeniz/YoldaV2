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
			if (btn == null) { // dar başlıkta tema düğmesinin yerinde telefon görünümü düğmesi (tema Ayarlar'da)
				View pv = onMain(a -> byDesc(a.getActiveFragment().getView(), "Görünüm seç"));
				View ed = onMain(a -> byDesc(a.getActiveFragment().getView(), "Düzenle"));
				assertNotNull("Tema düğmesi ya da yerinde telefon görünümü düğmesi olmalı", pv);
				assertNotNull(ed);
				assertTrue("Telefon görünümü düğmesi düzenle ile aynı satırda", onMain(a -> pv.getParent() == ed.getParent()));
				return;
			}
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
			// Dokununca tema seçimi açılır; basılı tutunca sıradaki tema
			onMain(a -> btn.performClick());
			assertTrue("Dokununca tema seçimi açılmalı", waitFor(3000, () -> onMain(a ->
					byDesc(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Tema: Gece mavi") != null)));
			onMain(a -> me.aap.fermata.ui.view.YoldaThemePicker.hide(a));
			waitIdle(600);
			onMain(a -> btn.performLongClick());
			assertTrue("Basılı tutunca tema değişmeli", waitFor(5000, () -> YoldaStyle.index(ctx()) == 1));
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
			// Gündüz / gece otomatik: seçim ekranındaki kip düğmesi
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaThemePicker.show(a);
				return null;
			});
			waitIdle(800);
			View auto = onMain(a -> byText(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "☀ / 🌙 Otomatik"));
			assertNotNull("Otomatik kip düğmesi olmalı", auto);
			onMain(a -> auto.performClick());
			assertTrue("Otomatik açılmalı", waitFor(3000, () -> YoldaStyle.isAuto(ctx())));
			onMain(a -> me.aap.fermata.ui.view.YoldaThemePicker.hide(a));
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
		// ✎ düzenle: Düzenle kipinde (karta basılı tutunca) ✕'in altında
		onMain(a -> {
			((YoldaDeckFragment) a.getActiveFragment()).startEditing();
			return null;
		});
		waitIdle(1000);
		View edit = onMain(a -> byDesc(a.getActiveFragment().getView(), "Favorileri düzenle"));
		assertNotNull("Favoriler kartında Düzenle olmalı", edit);
		onMain(a -> edit.performClick());
		waitIdle(800);
		assertNotNull("Düzenleme ekranı açılmalı", onMain(a -> byText(a.getActiveFragment().getView(), "Favorileri düzenle")));
		assertNotNull("Kaldır düğmesi olmalı", onMain(a -> byDesc(a.getActiveFragment().getView(), "Kaldır: İki")));
		onMain(a -> {
			a.onBackPressed(); // düzenleme ekranı kapanır
			return null;
		});
		onMain(a -> {
			if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.stopEditing();
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

	/**
	 * Telefon görünümü (tasarım taslağındaki düzen): Telefon | Araç anahtarı, hava, hızlı erişim (Ev, İş, Rota,
	 * Hey Yolda), destedeki her kart ızgarada ve "Kart ekle"; dokunulabilen her şey en az 48dp; Araç'a geri dönülür.
	 */
	@Test
	public void telefonGorunumuTasarimdakiGibi() {
		onMain(a -> {
			call(deck(a), "setPhoneView", true);
			return null;
		});
		waitIdle(2000);
		try {
			View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			for (String d : new String[]{"Hava durumu", "Ev", "İş", "Rota", "Benzinlik", "Kart ekle"}) {
				View v = onMain(a -> byDescExact(root, d));
				assertNotNull("Telefon görünümünde olmalı: " + d, v);
				int h = onMain(a -> v.getHeight());
				float dens = onMain(a -> v.getResources().getDisplayMetrics().density);
				assertTrue(d + " en az 48dp olmalı: " + (h / dens), h >= Math.round(48 * dens) - 1);
			}
			List<String> titles = onMain(a -> {
				List<String> l = new ArrayList<>();
				for (YoldaDeck.Card c : deck(a).getDeck().cards()) if (!YoldaDeck.ADD.equals(c.type())) l.add(c.title());
				return l;
			});
			for (String t : titles) assertNotNull("Izgarada kart olmalı: " + t, onMain(a -> byDescExact(root, t)));
			shot("telefon_gorunumu");
			// Görünüm seç düğmesi her görünümde: ızgarada var; desteye geçince orada da var; ızgaraya dönülür
			assertNotNull("Görünüm seç düğmesi olmalı", onMain(a -> byDescExact(root, "Görünüm seç")));
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				return null;
			});
			waitIdle(1500);
			assertNotNull("Destede de görünüm seç düğmesi olmalı",
					onMain(a -> byDescExact(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Görünüm seç")));
			onMain(a -> {
				call(deck(a), "setHomeView", "phone");
				return null;
			});
			waitIdle(1500);
			assertNotNull("Telefon görünümüne dönülmeli",
					onMain(a -> byDescExact(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Kart ekle")));
		} finally {
			onMain(a -> {
				call(deck(a), "setPhoneView", false);
				return null;
			});
			waitIdle(1500);
		}
	}

	/**
	 * Pano görünümü (Mola tarzı): üstte geniş Şimdi çalıyor kartı (önceki / oynat / sonraki), altında her kart;
	 * dokunulabilenler en az 48dp. Ekran görüntüsü: pano_gorunumu.
	 */
	@Test
	public void panoGorunumu() {
		onMain(a -> {
			call(deck(a), "setHomeView", "pano");
			return null;
		});
		waitIdle(2500);
		try {
			View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			for (String d : new String[]{"Önceki", "Oynat / duraklat", "Sonraki", "Hava durumu", "Kart ekle"}) {
				View v = onMain(a -> byDescExact(root, d));
				assertNotNull("Pano'da olmalı: " + d, v);
				int h = onMain(a -> v.getHeight());
				float dens = onMain(a -> v.getResources().getDisplayMetrics().density);
				assertTrue(d + " en az 48dp olmalı: " + (h / dens), h >= Math.round(48 * dens) - 1);
			}
			shot("pano_gorunumu");
			// Yatay (araç ekranı gibi): solda Şimdi çalıyor, sağda kartlar, altta Ev · İş · Benzinlik · Otopark
			scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
			waitIdle(3000);
			View wroot = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			for (String d : new String[]{"Oynat / duraklat", "Benzinlik", "Otopark", "Görünüm seç", "Hava durumu"}) {
				View v = onMain(a -> byDescExact(wroot, d));
				assertNotNull("Yatay Pano'da olmalı: " + d, v);
				int h = onMain(a -> v.getHeight());
				float dens = onMain(a -> v.getResources().getDisplayMetrics().density);
				assertTrue(d + " en az 48dp olmalı: " + (h / dens), h >= Math.round(48 * dens) - 1);
			}
			shot("pano_yatay");
		} finally {
			scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			waitIdle(1500);
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				return null;
			});
			waitIdle(1500);
		}
	}

	/**
	 * Yatay Pano'da video küçük pencerede: herkese açık bir HLS test yayını (Mux) çalarken ana ekrana dönülünce
	 * video tam ekran değil, sağda küçük pencerede (BOTH) sürer; Pano kartları solda. Ekran görüntüsü: pano_video.
	 * Yayın bu ağdan açılmazsa atlanır.
	 */
	/**
	 * Deste (yığın) görünümünde hız sınırı hep görünür: üst satırda (geniş ekran) ya da tarih satırında (dar
	 * telefon) levha + anlık hız. Kabuktaki gösterge değil, üst kısımdaki.
	 */
	@Test
	public void desteHizSabit() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c);
		YoldaRadar.setEnabled(c, true);
		try {
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(2500);
			float d = c.getResources().getDisplayMetrics().density;
			java.util.List<int[]> found = onMain(a -> {
				java.util.List<int[]> out = new java.util.ArrayList<>();
				View dv = ((android.app.Activity) a.getContext()).getWindow().getDecorView();
				java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
				q.add(dv);
				int[] xy = new int[2];
				while (!q.isEmpty()) {
					View v = q.poll();
					if (!v.isShown()) continue;
					if (v instanceof android.view.ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i));
					if ((v.getContentDescription() != null) && "Hız sınırı göstergesi".contentEquals(v.getContentDescription())) {
						v.getLocationOnScreen(xy);
						out.add(new int[]{xy[1], v.getHeight()});
					}
				}
				return out;
			});
			boolean top = false;
			for (int[] f : found) if (f[0] < 200 * d) top = true;
			shot("deste_hiz_sabit");
			assertTrue("Destede üst kısımda hız göstergesi görünmeli: " + found.size(), top);
		} finally {
			YoldaRadar.setEnabled(c, was);
		}
	}

	/**
	 * Şimdi çalıyor işlem satırı (Mola'dan): sarılabilen içerikte 15 sn geri / 30 sn ileri / hız; uyku ve ses
	 * her zaman. Hız düğmesi sırayla değişir, uyku düğmesi kalan süreyi gösterir, hepsi en az 48dp.
	 */
	@Test
	public void oynaticiIslemSatiri() {
		try {
			onMain(a -> {
				a.getBody().playItem(new me.aap.fermata.media.lib.IntentPlayable(a,
						android.net.Uri.parse("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")));
				return null;
			});
			boolean playing = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
			org.junit.Assume.assumeTrue("Test yayını bu ağdan açılmadı", playing);
			onMain(a -> {
				a.getBody().setMode(me.aap.fermata.ui.view.BodyLayout.Mode.FRAME);
				me.aap.fermata.ui.view.YoldaNowPlaying.show(a);
				return null;
			});
			waitIdle(2500);
			View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			float d = ctx().getResources().getDisplayMetrics().density;
			boolean seekable = onMain(a -> (a.getMediaSessionCallback().getEngine() != null)
					&& a.getMediaSessionCallback().getEngine().canSeek());
			java.util.List<String> must = new java.util.ArrayList<>(java.util.List.of("Uyku zamanlayıcısı"));
			if (seekable) must.addAll(java.util.List.of("15 saniye geri", "30 saniye ileri", "Oynatma hızı"));
			for (String desc : must) {
				View v = onMain(a -> byDescExact(root, desc));
				assertNotNull("Oynatıcıda olmalı: " + desc, v);
				assertTrue(desc + " en az 48dp", onMain(a -> v.getHeight()) >= Math.round(48 * d) - 1);
			}
			if (!seekable) assertNull("Sarılamayan içerikte sarma düğmesi olmamalı", onMain(a -> {
				View v = byDescExact(root, "30 saniye ileri");
				return ((v != null) && v.isShown()) ? v : null;
			}));
			shot("oynatici_islem_satiri");
			View sl0 = onMain(a -> byDescExact(root, "Uyku zamanlayıcısı"));
			if (!seekable) {
				onMain(a -> sl0.performClick());
				waitIdle(500);
				assertEquals("15 dk", onMain(a -> ((android.widget.TextView) sl0).getText().toString()));
				onMain(a -> {
					YoldaVoice.cancelSleep();
					return null;
				});
				return;
			}
			View sp = onMain(a -> byDescExact(root, "Oynatma hızı"));
			onMain(a -> sp.performClick());
			waitIdle(500);
			assertEquals("1,25×", onMain(a -> ((android.widget.TextView) sp).getText().toString()));
			// sarma: 30 sn ileri konumu ilerletir
			long p0 = onMain(a -> me.aap.fermata.ui.view.YoldaNowPlaying.positionOf(a.getMediaSessionCallback()));
			View fw = onMain(a -> byDescExact(root, "30 saniye ileri"));
			onMain(a -> fw.performClick());
			waitIdle(2500);
			long p1 = onMain(a -> me.aap.fermata.ui.view.YoldaNowPlaying.positionOf(a.getMediaSessionCallback()));
			assertTrue("30 sn ileri sarmalı: " + p0 + " → " + p1, p1 >= p0 + 20_000);
			// uyku: Kapalı → 15 dk → … ; sonra kapat
			View sl = onMain(a -> byDescExact(root, "Uyku zamanlayıcısı"));
			onMain(a -> sl.performClick());
			waitIdle(500);
			assertEquals("15 dk", onMain(a -> ((android.widget.TextView) sl).getText().toString()));
			onMain(a -> {
				YoldaVoice.cancelSleep();
				a.getMediaSessionCallback().onSetPlaybackSpeed(1f);
				return null;
			});
		} finally {
			onMain(a -> {
				a.getMediaSessionCallback().onStop();
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a);
				return null;
			});
			waitIdle(1000);
		}
	}

	/** ✕ düğmesi: tam ekran videoda ve Şimdi çalıyor'da çalanı doğrudan kapatır (durur, liste görünür). */
	@Test
	public void kapatDugmesi() {
		java.util.function.Supplier<Boolean> start = () -> {
			onMain(a -> {
				a.getBody().playItem(new me.aap.fermata.media.lib.IntentPlayable(a,
						android.net.Uri.parse("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")));
				return null;
			});
			return waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		};
		try {
			org.junit.Assume.assumeTrue("Test yayını bu ağdan açılmadı", start.get());
			onMain(a -> {
				a.getBody().setMode(me.aap.fermata.ui.view.BodyLayout.Mode.VIDEO);
				return null;
			});
			waitIdle(1500);
			View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			View x = onMain(a -> byDescExact(root, "Videoyu kapat"));
			assertNotNull("Tam ekran videoda ✕ olmalı", x);
			float d = ctx().getResources().getDisplayMetrics().density;
			assertTrue("✕ en az 48dp", onMain(a -> x.getHeight()) >= Math.round(48 * d) - 1);
			shot("video_kapat_dugmesi");
			onMain(a -> x.performClick());
			assertTrue("✕ sonrası oynatma durmalı", waitFor(5000, () -> onMain(a -> !a.getMediaSessionCallback().isPlaying())));
			assertTrue("✕ sonrası video ekranından çıkmalı", waitFor(3000, () -> onMain(a -> a.getBody().isFrameMode())));
			// Şimdi çalıyor ekranında da ✕
			org.junit.Assume.assumeTrue("Test yayını ikinci kez açılmadı", start.get());
			onMain(a -> {
				a.getBody().setMode(me.aap.fermata.ui.view.BodyLayout.Mode.FRAME);
				me.aap.fermata.ui.view.YoldaNowPlaying.show(a);
				return null;
			});
			waitIdle(2000);
			View root2 = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			View x2 = onMain(a -> byDescExact(root2, "Oynatıcıyı kapat"));
			assertNotNull("Şimdi çalıyor'da ✕ olmalı", x2);
			onMain(a -> x2.performClick());
			assertTrue("Şimdi çalıyor ✕ sonrası oynatma durmalı",
					waitFor(5000, () -> onMain(a -> !a.getMediaSessionCallback().isPlaying())));
			assertTrue("Şimdi çalıyor kapanmalı", waitFor(3000, () -> onMain(a -> !me.aap.fermata.ui.view.YoldaNowPlaying.isShown(a))));
		} finally {
			onMain(a -> {
				a.getMediaSessionCallback().onStop();
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a);
				return null;
			});
			waitIdle(1000);
		}
	}

	/**
	 * Pano'da küçük pencerede oynayan video, yığına (Deste) geçince bölünmüş kalmamalı (sağda dar şerit
	 * hatası); tam ekrana geçince geri ve ✕ düğmeleri her zaman görünmeli.
	 */
	@Test
	public void destedeVideoTamEkran() {
		onMain(a -> {
			call(deck(a), "setHomeView", "pano");
			return null;
		});
		scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		waitIdle(3000);
		try {
			onMain(a -> {
				a.getBody().playItem(new me.aap.fermata.media.lib.IntentPlayable(a,
						android.net.Uri.parse("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")));
				return null;
			});
			boolean playing = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
			org.junit.Assume.assumeTrue("Test yayını bu ağdan açılmadı", playing);
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitFor(6000, () -> onMain(a -> a.getBody().isBothMode()));
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				return null;
			});
			waitIdle(3000);
			assertFalse("Destede video bölünmüş (sağda dar şerit) kalmamalı", onMain(a -> a.getBody().isBothMode()));
			shot("deste_video_sonrasi");
			// tam ekran video: geri ve ✕
			onMain(a -> {
				a.getBody().setMode(me.aap.fermata.ui.view.BodyLayout.Mode.VIDEO);
				return null;
			});
			waitIdle(1500);
			View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			for (String desc : new String[]{"Geri", "Videoyu kapat"}) {
				View v = onMain(a -> byDescExact(root, desc));
				assertTrue("Tam ekran videoda görünmeli: " + desc, (v != null) && onMain(a -> v.isShown()));
			}
			shot("deste_video_tam_ekran");
		} finally {
			onMain(a -> {
				a.getMediaSessionCallback().onStop();
				a.getBody().setMode(me.aap.fermata.ui.view.BodyLayout.Mode.FRAME);
				return null;
			});
			scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			waitIdle(1500);
		}
	}

	/** Izgara görünümü (Mola'daki ızgara): yatay ve dikeyde kartlar, alt şerit ve başlık; düğmeler en az 48dp. */
	@Test
	public void izgaraGorunumu() {
		float d = ctx().getResources().getDisplayMetrics().density;
		try {
			for (boolean land : new boolean[]{true, false}) {
				scenario.onActivity(act -> act.setRequestedOrientation(land
						? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
						: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
				waitIdle(2000);
				onMain(a -> {
					call(deck(a), "setHomeView", "grid");
					a.showFragment(R.id.home_fragment);
					return null;
				});
				waitIdle(2500);
				assertEquals("grid", onMain(a -> (String) call(deck(a), "homeView")));
				View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
				for (String desc : new String[]{"Görünüm seç", "Ayarlar", "Benzinlik", "Otopark", "YouTube"}) {
					View v = onMain(a -> byDescExact(root, desc));
					assertNotNull("Izgarada olmalı (" + (land ? "yatay" : "dikey") + "): " + desc, v);
					assertTrue(desc + " en az 48dp", onMain(a -> v.getHeight()) >= Math.round(48 * d) - 1);
				}
				shot(land ? "izgara_yatay" : "izgara_dikey");
			}
		} finally {
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				return null;
			});
			scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			waitIdle(1500);
		}
	}

	/** Panel (Mola'nın ilk görünümü): Şimdi çalıyor kartı, oynatıcı düğmeleri ve kutular; yatay ve dikey. */
	@Test
	public void panelGorunumu() {
		float d = ctx().getResources().getDisplayMetrics().density;
		try {
			for (boolean land : new boolean[]{true, false}) {
				scenario.onActivity(act -> act.setRequestedOrientation(land
						? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
						: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
				waitIdle(2000);
				onMain(a -> {
					call(deck(a), "setHomeView", "dash");
					a.showFragment(R.id.home_fragment);
					return null;
				});
				waitIdle(2500);
				assertEquals("dash", onMain(a -> (String) call(deck(a), "homeView")));
				View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
				for (String desc : new String[]{"Şimdi çalıyor", "Oynat / duraklat", "Önceki", "Sonraki", "Favoriler", "YouTube", "Görünüm seç"}) {
					View v = onMain(a -> byDescExact(root, desc));
					assertNotNull("Panelde olmalı (" + (land ? "yatay" : "dikey") + "): " + desc, v);
					assertTrue(desc + " en az 48dp", onMain(a -> v.getHeight()) >= Math.round(48 * d) - 1);
				}
				shot(land ? "panel_yatay" : "panel_dikey");
			}
		} finally {
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				return null;
			});
			scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			waitIdle(1500);
		}
	}

	/** Araç klavyesi: YouTube aramasında harf yazar, siler, 48dp tuşlar, ✕ kapatır. */
	@Test
	public void aracKlavyesi() {
		onMain(a -> a.showFragment(R.id.yolda_youtube));
		waitIdle(2500);
		android.widget.EditText e = onMain(a -> {
			java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
			q.add(((android.app.Activity) a.getContext()).getWindow().getDecorView());
			while (!q.isEmpty()) {
				View v = q.poll();
				if ((v instanceof android.widget.EditText t) && v.isShown()) return t;
				if (v instanceof android.view.ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i));
			}
			return null;
		});
		assertNotNull("YouTube'da arama kutusu olmalı", e);
		try {
			onMain(a -> {
				e.setText("");
				me.aap.fermata.ui.view.YoldaCarKeyboard.show(a, e);
				return null;
			});
			waitIdle(800);
			View root = onMain(a -> ((android.app.Activity) a.getContext()).getWindow().getDecorView());
			assertNotNull("Klavye açılmalı", onMain(a -> byDescExact(root, "Araç klavyesi")));
			float d = ctx().getResources().getDisplayMetrics().density;
			for (String k : new String[]{"t", "r"}) {
				View kv = onMain(a -> byDescExact(root, k));
				assertNotNull("Tuş olmalı: " + k, kv);
				assertTrue("Tuş en az 48dp", onMain(a -> kv.getHeight()) >= Math.round(48 * d) - 1);
				onMain(a -> kv.performClick());
			}
			assertEquals("tr", onMain(a -> e.getText().toString()));
			onMain(a -> byDescExact(root, "Sil").performClick());
			assertEquals("t", onMain(a -> e.getText().toString()));
			shot("arac_klavyesi");
			onMain(a -> byDescExact(root, "Klavyeyi kapat").performClick());
			waitIdle(500);
			assertFalse("✕ ile kapanmalı", onMain(a -> me.aap.fermata.ui.view.YoldaCarKeyboard.isOpen()));
		} finally {
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaCarKeyboard.hide();
				a.showFragment(R.id.home_fragment);
				return null;
			});
		}
	}

	@Test
	public void panoVideoKucukPencerede() {
		onMain(a -> {
			call(deck(a), "setHomeView", "pano");
			return null;
		});
		scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		waitIdle(3000);
		try {
			onMain(a -> {
				a.getBody().playItem(new me.aap.fermata.media.lib.IntentPlayable(a,
						android.net.Uri.parse("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")));
				return null;
			});
			boolean playing = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
			org.junit.Assume.assumeTrue("Test yayını bu ağdan açılmadı", playing);
			onMain(a -> a.showFragment(R.id.home_fragment));
			assertTrue("Pano'ya dönünce video küçük pencerede (BOTH) sürmeli",
					waitFor(6000, () -> onMain(a -> a.getBody().isBothMode())));
			waitIdle(2500);
			assertNotNull("Pano kartları görünmeli", onMain(a -> byDescExact(
					((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Benzinlik")));
			shot("pano_video");
			// Araçta solda sistem çubuğu (≈110 px güvenli alan): sol kenarda yarım kalan öğe olmamalı
			onMain(a -> {
				View root = ((android.app.Activity) a.getContext()).findViewById(R.id.main_activity);
				root.setPadding(110, root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom());
				return null;
			});
			waitIdle(1500);
			try {
				shot("pano_video_sol_bosluk");
				java.util.List<String> cut = onMain(a -> {
					java.util.List<String> out = new java.util.ArrayList<>();
					View dv = ((android.app.Activity) a.getContext()).getWindow().getDecorView();
					java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
					q.add(dv);
					int[] xy = new int[2];
					while (!q.isEmpty()) {
						View v = q.poll();
						if (!v.isShown()) continue;
						if (v instanceof android.view.ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i));
						if ((v.getWidth() == 0) || (v.getWidth() >= dv.getWidth() / 2)) continue;
						v.getLocationOnScreen(xy);
						if ((xy[0] < 110) && (xy[0] + v.getWidth() > 110))
							out.add(v.getClass().getSimpleName() + " " + v.getContentDescription() + " x=" + xy[0] + " w=" + v.getWidth());
					}
					return out;
				});
				android.util.Log.i("YoldaTest", "sol boşlukta kesilen: " + cut);
				assertTrue("Sol güvenli alanın sınırında kesilen öğe olmamalı: " + cut, cut.isEmpty());
			} finally {
				onMain(a -> {
					View root = ((android.app.Activity) a.getContext()).findViewById(R.id.main_activity);
					root.setPadding(0, root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom());
					return null;
				});
			}
		} finally {
			onMain(a -> {
				a.getMediaSessionCallback().onStop();
				return null;
			});
			scenario.onActivity(act -> act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			waitIdle(1500);
			onMain(a -> {
				call(deck(a), "setHomeView", "deck");
				return null;
			});
			waitIdle(1500);
		}
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
			View add = onMain(a -> byDesc(a.getActiveFragment().getView(), "Uygulama ekle"));
			assertNotNull("＋ (Uygulama ekle) olmalı", add);
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).startEditing();
				return null;
			});
			waitIdle(1000);
			assertNotNull("✎ (Uygulamaları düzenle) Düzenle kipinde olmalı", onMain(a -> byDesc(a.getActiveFragment().getView(), "Uygulamaları düzenle")));
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).stopEditing();
				return null;
			});
			waitIdle(1000);
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
			YoldaRadar.resetOverWarn(); // önceki adımda bip çaldıysa 20 sn aralığı yenisini engellemesin
			long beep0 = YoldaRadar.overBeepAt;
			String alert0 = YoldaRadar.lastAlert;
			YoldaSpeak.lastSpoken = null;
			double laOver = lat0 + 650 / 110_540d;
			for (int i = 0; i < 45; i++) {
				onMain(a -> {
					YoldaRadar.limitEstimated = false; // ağdan gelen tahmini sınır bipi engellemesin (kararsızlık)
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
			// kart yenilemesi bir sonraki karede gelir: birkaç saniye bekle
			assertTrue("Kart kapalı göstermeli", waitFor(3000,
					() -> onMain(a -> byDesc(a.getActiveFragment().getView(), "Radar uyarıları: kapalı")) != null));
			View sw2 = onMain(a -> byDesc(a.getActiveFragment().getView(), "Radar uyarıları: kapalı"));

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

			// Park sayfası (park ve yolculuklar artık kendi sayfalarında)
			onMain(a -> a.showFragment(R.id.yolda_park_fragment));
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
			onMain(a -> a.showFragment(R.id.yolda_trip_fragment));
			waitIdle(1200);
			assertNotNull(onMain(a -> byText(a.getActiveFragment().getView(), "Yolculuk özeti")));
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
			// Radar uyarısı videonun üstünde kırmızı kartla; ✕ ile kapanır (600 m / 60 km/s'de uyarı yok:
			// 1000 m uyarısı 70 km/s üstünde; burada Sürüş ekranındaki "Uyarıyı dene"nin yolu)
			onMain(a -> {
				YoldaRadar.testAlert(c, false);
				return null;
			});
			assertTrue("Videoda radar uyarısı kartı görünmeli",
					waitFor(2000, () -> onMain(me.aap.fermata.ui.view.YoldaAlertOverlay::isShown)));
			// ✕ ekran görüntüsünden önce aranır: uyarı 5 sn'de kendiliğinden kapanır, görüntü almak yavaş olabilir
			View x = onMain(a -> byDesc(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Uyarıyı kapat"));
			assertNotNull("Uyarıda kapat düğmesi olmalı", x);
			shot("video_radar_uyarisi");
			onMain(a -> x.performClick());
			assertTrue("✕ ile kapanmalı", waitFor(1500, () -> !onMain(me.aap.fermata.ui.view.YoldaAlertOverlay::isShown)));
		} finally {
			onMain(a -> {
				View host = a.findViewById(R.id.main_activity);
				if (host instanceof android.view.ViewGroup g) for (int i = g.getChildCount() - 1; i >= 0; i--)
					if (g.getChildAt(i) instanceof me.aap.fermata.ui.view.YoldaAlertOverlay o) o.dismiss();
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
			// dikey telefonda tek sıra (listelerin soluna binmesin), yatayda tek sütun
			boolean portrait = false; // kabuk her yerde tek sütun
			float thin = portrait ? b.height() : b.width();
			assertTrue("Kapsül ince olmalı (" + (portrait ? "tek sıra" : "tek sütun") + "): " + thin,
					thin < 90 * c.getResources().getDisplayMetrics().density);
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

	/** Hava kartı tarayıcı değil uygulama içi ekranı açar: şimdi, 24 saat, 7 gün. */
	@Test
	public void havaEkrani() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1000);
		int pos = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.WEATHER));
		org.junit.Assume.assumeTrue("Hava kartı destede", pos >= 0);
		onMain(a -> {
			((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(pos);
			return null;
		});
		waitIdle(1200);
		// dokunuşu kartın kendisi dinler (taşıyıcı kutunun içindeki ilk görünüm)
		View box = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).cardView(pos));
		assertNotNull(box);
		View card = (box instanceof ViewGroup g && (g.getChildCount() > 0)) ? g.getChildAt(0) : box;
		onMain(a -> card.performClick()); // kenardaysa önce ortaya gelir
		waitIdle(1200);
		if (onMain(MainActivityDelegate::getActiveFragmentId) != R.id.yolda_weather_fragment) {
			View b2 = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).cardView(pos));
			View c2 = (b2 instanceof ViewGroup g2 && (g2.getChildCount() > 0)) ? g2.getChildAt(0) : b2;
			if (c2 != null) onMain(a -> c2.performClick()); // ortadaki karta dokununca açılır
		}
		assertTrue("Hava ekranı uygulama içinde açılmalı", waitFor(4000,
				() -> onMain(MainActivityDelegate::getActiveFragmentId) == R.id.yolda_weather_fragment));
		boolean got = waitFor(25_000, () -> onMain(a -> (a.getActiveFragment() instanceof WeatherFragment w) && (w.getData() != null)));
		org.junit.Assume.assumeTrue("Open-Meteo bu ağdan erişilemiyor", got);
		YoldaWeather.Detail d = onMain(a -> ((WeatherFragment) a.getActiveFragment()).getData());
		Log.i(TAG, "Hava ekranı: " + d.place() + " " + d.temp() + "° · " + d.hours().size() + " saat, " + d.days().size() + " gün");
		assertEquals("7 günlük tahmin", 7, d.days().size());
		assertTrue("24 saatlik tahmin", d.hours().size() >= 20);
		waitIdle(800);
		shot("hava_ekrani");
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(800);
	}

	/** Klavye açıkken yazılan kutu klavyenin altında kalmamalı (ekran kenara kadar çizildiğinde). */
	@Test
	public void klavyeYazilaniOrtmez() {
		onMain(a -> a.showFragment(R.id.yolda_channels_fragment));
		waitIdle(1500);
		android.widget.EditText et = onMain(a -> findEdit(a.getActiveFragment().getView()));
		assertNotNull("Arama kutusu olmalı", et);
		onMain(a -> {
			et.requestFocus();
			((android.view.inputmethod.InputMethodManager) a.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
					.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
			return null;
		});
		boolean ime = waitFor(5000, () -> onMain(a -> {
			androidx.core.view.WindowInsetsCompat in = androidx.core.view.ViewCompat.getRootWindowInsets(et);
			return (in != null) && in.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
		}));
		try {
			org.junit.Assume.assumeTrue("Emülatörde klavye açılamadı", ime);
			waitIdle(800);
			int[] r = onMain(a -> {
				androidx.core.view.WindowInsetsCompat in = androidx.core.view.ViewCompat.getRootWindowInsets(et);
				int imeH = in.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
				int[] xy = new int[2];
				et.getLocationOnScreen(xy);
				int screenH = ((android.app.Activity) a.getContext()).getWindow().getDecorView().getHeight();
				return new int[]{xy[1] + et.getHeight(), screenH - imeH};
			});
			Log.i(TAG, "Klavye: kutunun altı " + r[0] + ", klavyenin üstü " + r[1]);
			shot("klavye_acik");
			assertTrue("Yazı kutusu klavyenin üstünde olmalı: alt " + r[0] + " > klavye " + r[1], r[0] <= r[1] + 2);
		} finally {
			onMain(a -> {
				((android.view.inputmethod.InputMethodManager) a.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
						.hideSoftInputFromWindow(et.getWindowToken(), 0);
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(800);
		}
	}

	@androidx.annotation.Nullable
	private static android.widget.EditText findEdit(View v) {
		if ((v == null) || (v.getVisibility() != View.VISIBLE)) return null;
		if (v instanceof android.widget.EditText e) return e;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				android.widget.EditText e = findEdit(g.getChildAt(i));
				if (e != null) return e;
			}
		}
		return null;
	}

	/** Sürüş kartı ortadayken konum her saniye gelse de kart yeniden kurulmamalı (titreme). */
	@Test
	public void surusKartiTitremez() {
		android.content.Context c = ctx();
		boolean was = YoldaRadar.isEnabled(c);
		YoldaRadar.ignoreGps = true;
		try {
			onMain(a -> {
				YoldaRadar.setEnabled(c, true);
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(1200);
			int pos = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().indexOf(YoldaDeck.RADAR));
			assertTrue(pos >= 0);
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(pos);
				return null;
			});
			waitIdle(2000);
			int b0 = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).radarBinds());
			double lat0 = 40.0, lon0 = 30.0;
			for (int i = 0; i < 20; i++) {
				double la = lat0 + i * 25 / 111_195d;
				int sp = 60 + (i % 5) * 7;
				onMain(a -> {
					YoldaRadar.feed(la, lon0, sp, 0);
					return null;
				});
				SystemClock.sleep(500);
			}
			waitIdle(800);
			int b1 = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).radarBinds());
			Log.i(TAG, "Sürüş kartı 10 sn'de " + (b1 - b0) + " kez yeniden kuruldu");
			assertTrue("Sürüş kartı konum güncellemelerinde yeniden kurulmamalı: " + (b1 - b0), (b1 - b0) <= 1);
		} finally {
			onMain(a -> {
				YoldaRadar.setEnabled(c, was);
				YoldaRadar.ignoreGps = false;
				return null;
			});
			waitIdle(500);
		}
	}

	/** Ana kart: başka karttayken kapsülde ⌂ çıkar, basınca ana karta kayılır. */
	@Test
	public void anaKart() {
		android.content.SharedPreferences p = ctx().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS,
				android.content.Context.MODE_PRIVATE);
		String old = p.getString("home_card", null);
		try {
			p.edit().putString("home_card", YoldaDeck.WEATHER).commit();
			onMain(a -> {
				me.aap.fermata.ui.view.YoldaNowPlaying.hide(a);
				a.showFragment(R.id.home_fragment);
				return null;
			});
			waitIdle(1200);
			int hc = onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).homeCardIndex());
			assertTrue("Ana kart destede", hc >= 0);
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).scrollToCard(0);
				return null;
			});
			waitIdle(1500);
			onMain(a -> {
				a.yoldaDockChanged();
				return null;
			});
			waitIdle(500);
			View home = onMain(a -> byDescExact(((android.app.Activity) a.getContext()).getWindow().getDecorView(), "Ana sayfa"));
			assertNotNull("Ana karttan uzakken ⌂ görünmeli", home);
			shot("ana_kart_dugmesi");
			onMain(a -> home.performClick());
			assertTrue("⌂ ile ana karta dönülmeli", waitFor(4000,
					() -> onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).currentCard()) == hc));
		} finally {
			if (old == null) p.edit().remove("home_card").commit();
			else p.edit().putString("home_card", old).commit();
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
