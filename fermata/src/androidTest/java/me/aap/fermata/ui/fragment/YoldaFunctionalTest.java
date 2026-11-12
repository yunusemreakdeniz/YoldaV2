package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.Log;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.R;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Yolda fonksiyonel testleri: uygulama gerçekten açılır, ekranlar arasında gezilir, sesli komutlar
 * ve oynatıcı denetimleri çalıştırılır. Emülatörde (GitHub Actions) koşar.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaFunctionalTest {
	private static final String TAG = "YoldaTest";
	private ActivityScenario<MainActivity> scenario;

	/** Takılan test tüm işi kilitlemesin: süre aşılırsa yalnız o test başarısız olur. */
	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	@Before
	public void start() {
		// Kurulum ekranını atla (emülatör Play Store'dan kurulmuş görünmez)
		MainActivityDelegate.skipSetupThisRun = true;
		// Açılıştaki izin penceresi testi bekletmesin: izinleri önceden ver
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO",
				"android.permission.POST_NOTIFICATIONS", "android.permission.READ_MEDIA_AUDIO",
				"android.permission.READ_MEDIA_VIDEO", "android.permission.ACCESS_FINE_LOCATION"}) {
			try {
				InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		scenario = ActivityScenario.launch(MainActivity.class);
		waitIdle(2500);
	}

	@After
	public void stop() {
		if (scenario != null) scenario.close();
	}

	@Test
	public void acilistaAnaSayfa() {
		for (int i = 0; (i < 30) && (onMain(MainActivityDelegate::getActiveFragmentId) == 0); i++) waitIdle(500);
		assertEquals(R.id.home_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		assertTrue("Ana sayfa görünümü kurulmalı", onMain(a -> {
			ActivityFragment f = a.getActiveFragment();
			return (f != null) && (f.getView() != null) && (f.getView().getVisibility() == View.VISIBLE);
		}));
	}

	@Test
	public void tumYoldaEkranlariAcilir() {
		int[] ids = {R.id.yolda_channels_fragment, R.id.yolda_music, R.id.yolda_youtube, R.id.yolda_nav_fragment,
				R.id.yolda_menu_fragment, R.id.yolda_setup_fragment, R.id.home_fragment};
		for (int id : ids) {
			onMain(a -> a.showFragment(id));
			waitIdle(800);
			assertEquals("Ekran açılmalı: " + id, id, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
			assertNotNull("Ekran içeriği boş olmamalı: " + id,
					onMain(a -> (a.getActiveFragment() != null) ? a.getActiveFragment().getView() : null));
		}
	}

	@Test
	public void tvSekmesiKartEkraninaYonlenir() {
		onMain(a -> a.showFragment(R.id.tv_fragment));
		waitIdle(800);
		assertEquals(R.id.yolda_channels_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
	}

	@Test
	public void sesliKomutlarDogruEkranaGider() {
		voice("ana sayfa", R.id.home_fragment);
		voice("navigasyon", R.id.yolda_nav_fragment);
		voice("kanallar", R.id.yolda_channels_fragment);
		voice("menü", R.id.yolda_menu_fragment);
	}

	@Test
	public void oynaticiKapatmaCokmez() {
		onMain(a -> {
			a.getControlPanel().closePlayer();
			return null;
		});
		waitIdle(500);
		assertFalse("Kapatınca çalmıyor olmalı", onMain(a -> a.getMediaSessionCallback().isPlaying()));
		// İkinci kez kapatmak da çökmemeli
		onMain(a -> {
			a.getControlPanel().closePlayer();
			return null;
		});
	}

	@Test
	public void dondurmedeEkranlarYenidenKurulur() {
		scenario.onActivity(act -> act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		waitIdle(2000);
		onMain(a -> a.showFragment(R.id.yolda_music));
		waitIdle(1000);
		assertNotNull(onMain(a -> a.getActiveFragment().getView()));
		scenario.onActivity(act -> act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
		waitIdle(2000);
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1000);
		assertEquals(R.id.home_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
	}

	@Test
	public void muzikAramasiSonucDoner() throws Exception {
		List<YtMusic.Track> list = YtMusic.search("Tarkan").get(null, 45, TimeUnit.SECONDS);
		assertNotNull(list);
		assertFalse("Arama boş dönmemeli", list.isEmpty());
		YtMusic.Track t = list.get(0);
		assertFalse(t.title().isEmpty());
		assertFalse(t.id().isEmpty());
		Log.i(TAG, "Arama: " + list.size() + " şarkı, ilk: " + t.title() + " / " + t.artist() + " / " + t.thumb());
	}

	/**
	 * Doğrudan ses akışı: veri merkezi adresleri YouTube tarafından sık engellendiğinden başarısızlık
	 * testi düşürmez, sonuç günlüğe yazılır (telefondaki "ParsingException" ile karşılaştırmak için).
	 */
	@Test
	public void muzikAkisiCozumu() throws Exception {
		List<YtMusic.Track> list = YtMusic.search("Tarkan").get(null, 45, TimeUnit.SECONDS);
		if ((list == null) || list.isEmpty()) return;
		AtomicReference<String> res = new AtomicReference<>("zaman aşımı");
		YtMusic.resolve(list.get(0)).onCompletion((s, err) -> res.set((err != null)
				? ("HATA " + err.getClass().getName() + ": " + err.getMessage())
				: ("TAMAM ua=" + s.userAgent() + " süre=" + s.durationSec() + " url=" + s.url().substring(0, 60))));
		for (int i = 0; (i < 60) && "zaman aşımı".equals(res.get()); i++) Thread.sleep(500);
		Log.i(TAG, "Akış çözümü: " + res.get());
	}

	/** V2: ana ekran kart destesi; her kart kendi ekranını açar, sol alttaki geri düğmesi desteye döndürür. */
	@Test
	public void desteKartlariAcilirVeGeriDoner() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		assertTrue("Ana ekran kart destesi olmalı", onMain(a -> a.getActiveFragment() instanceof YoldaDeckFragment));
		// Deste gerçekten ekranda: yüksekliği ekranın en az yarısı (yerleşim çökerse boş ekran geliyordu)
		assertTrue("Kart destesi ekranı kaplamalı", onMain(a -> {
			View v = a.getActiveFragment().getView();
			int h = ((android.app.Activity) a.getContext()).getWindow().getDecorView().getHeight();
			return (v != null) && v.isShown() && (v.getHeight() > h / 2);
		}));
		assertTrue("Oynatıcı ve sesli düğme ekranın altında olmalı", onMain(a -> {
			int[] l = new int[2];
			a.getYoldaVoiceButton().getLocationOnScreen(l);
			int h = ((android.app.Activity) a.getContext()).getWindow().getDecorView().getHeight();
			return l[1] > h / 2;
		}));
		assertEquals("Destede 9 kart olmalı", 9, onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().size()).intValue());
		assertEquals("Ana ekranda geri düğmesi gizli", View.GONE, onMain(a -> a.getDeckBackButton().getVisibility()).intValue());
		assertEquals("Menü şeridi olmamalı (kart alanı tam ekran)", View.GONE, onMain(a -> a.getNavBar().getVisibility()).intValue());
		String[] keys = {YoldaDeck.YOUTUBE, YoldaDeck.MUSIC, YoldaDeck.TV, YoldaDeck.RADIO, YoldaDeck.NAV, YoldaDeck.WEB,
				YoldaDeck.SETTINGS};
		int[] ids = {R.id.yolda_youtube, R.id.yolda_music, R.id.yolda_channels_fragment, R.id.yolda_channels_fragment,
				R.id.yolda_nav_fragment, R.id.web_browser_fragment, R.id.yolda_menu_fragment};
		for (int i = 0; i < keys.length; i++) {
			String k = keys[i];
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).openCard(k);
				return null;
			});
			waitIdle(1500);
			assertEquals("Kart açılmalı: " + k, ids[i], onMain(MainActivityDelegate::getActiveFragmentId).intValue());
			assertEquals("Kartın içinde geri düğmesi görünmeli: " + k, View.VISIBLE,
					onMain(a -> a.getDeckBackButton().getVisibility()).intValue());
			onMain(a -> {
				a.getDeckBackButton().performClick();
				return null;
			});
			waitIdle(1200);
			assertEquals("Geri düğmesi desteye dönmeli: " + k, R.id.home_fragment,
					onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		}
	}

	/** Arama kutuları sesli düğmeyle aynı hizada (ortaları en çok 3 dp farklı) ve onun sağında. */
	@Test
	public void aramaSesliDugmeyleAyniHizada() {
		for (int id : new int[]{R.id.yolda_music, R.id.yolda_channels_fragment, R.id.yolda_youtube}) {
			onMain(a -> a.showFragment(id));
			waitIdle(1500);
			String r = onMain(a -> {
				View voice = a.getYoldaVoiceButton();
				android.widget.EditText e = findEdit(a.getActiveFragment().getView());
				if ((voice == null) || (e == null) || !e.isShown()) return "yok";
				View box = e; // arama kutusu doğrudan düzenleme alanı
				int[] lv = new int[2], le = new int[2];
				voice.getLocationOnScreen(lv);
				box.getLocationOnScreen(le);
				float d = voice.getResources().getDisplayMetrics().density;
				float cv = lv[1] + voice.getHeight() / 2f, ce = le[1] + box.getHeight() / 2f;
				if (Math.abs(cv - ce) / d > 3) return "hiza farkı " + Math.round(Math.abs(cv - ce) / d) + " dp";
				if (le[0] < lv[0] + voice.getWidth()) return "arama sesli düğmenin üstüne biniyor";
				return "tamam";
			});
			if (!"yok".equals(r)) assertEquals("Ekran " + id, "tamam", r);
		}
	}

	private static android.widget.EditText findEdit(View v) {
		if (v instanceof android.widget.EditText e) return e;
		if (v instanceof android.view.ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				android.widget.EditText e = findEdit(g.getChildAt(i));
				if (e != null) return e;
			}
		}
		return null;
	}

	/** Tema değişince açık sayfa, oynatıcı çubuğu ve sesli düğme yeni renklerle yeniden kurulur. */
	@Test
	public void temaDegisinceHerSeyYenilenir() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1000);
		int start = me.aap.fermata.ui.view.YoldaStyle.index(ctx());
		Object bar = onMain(a -> a.getControlPanel().getYoldaBar());
		Object voiceBg = onMain(a -> a.getYoldaVoiceButton().getBackground());
		Object page = onMain(a -> a.getActiveFragment().getView());
		me.aap.fermata.ui.view.YoldaStyle.next(ctx());
		waitIdle(1500);
		try {
			assertTrue("Tema değişmeli", me.aap.fermata.ui.view.YoldaStyle.index(ctx()) != start);
			assertTrue("Oynatıcı çubuğu yenilenmeli", onMain(a -> a.getControlPanel().getYoldaBar()) != bar);
			assertTrue("Sesli düğme yenilenmeli", onMain(a -> a.getYoldaVoiceButton().getBackground()) != voiceBg);
			assertTrue("Açık sayfa yenilenmeli", onMain(a -> a.getActiveFragment().getView()) != page);
			assertEquals("Ana sayfada kalmalı", R.id.home_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		} finally {
			for (int i = 0; (i < 10) && (me.aap.fermata.ui.view.YoldaStyle.index(ctx()) != start); i++) {
				me.aap.fermata.ui.view.YoldaStyle.next(ctx());
			}
			waitIdle(800);
		}
	}

	/** Sesli asistan düğmesi her sayfada görünür ve rahat basılacak kadar büyük. */
	@Test
	public void sesliDugmeGorunurVeBuyuk() {
		for (int id : new int[]{R.id.home_fragment, R.id.yolda_channels_fragment, R.id.yolda_music}) {
			onMain(a -> a.showFragment(id));
			waitIdle(800);
			assertTrue("Sesli düğme görünmeli: " + id, onMain(a -> {
				View v = a.getYoldaVoiceButton();
				return (v != null) && (v.getVisibility() == View.VISIBLE) && v.isClickable() && v.isShown();
			}));
			float d = ctx().getResources().getDisplayMetrics().density;
			int w = onMain(a -> a.getYoldaVoiceButton().getWidth());
			assertTrue("Sesli düğme en az 48 dp olmalı: " + (w / d), w / d >= 48);
		}
	}

	// ---- yardımcılar ----

	private void voice(String cmd, int expected) {
		assertTrue("Komut işlenmeli: " + cmd, onMain(a -> YoldaVoice.handle(a, cmd)));
		waitIdle(800);
		assertEquals("Komut: " + cmd, expected, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
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

	private static void waitIdle(long ms) {
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignore) {
		}
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
	}

	private static Context ctx() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}
}
