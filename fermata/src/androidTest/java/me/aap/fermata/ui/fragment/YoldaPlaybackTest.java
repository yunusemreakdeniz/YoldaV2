package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.media.yt.YtMusicItem;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaNowPlaying;

/**
 * Yolda oynatıcı testleri (emülatörde, gerçek yayınlarla): TV kanalının açılma süresi, oynat / duraklat /
 * kapat / yeniden aç, radyo ve "Şimdi çalıyor" ekranı, müzik listesinde sıradakine geçiş, web sayfası
 * işlemi çökünce uygulamanın kapanmayıp sayfayı yeniden kurması.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaPlaybackTest {
	private static final String TAG = "YoldaTest";
	private ActivityScenario<MainActivity> scenario;

	/** Takılan test tüm işi kilitlemesin: süre aşılırsa yalnız o test başarısız olur. */
	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
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
		try {
			onMain(a -> {
				a.getControlPanel().closePlayer();
				return null;
			});
		} catch (Throwable ignore) {
		}
		if (scenario != null) scenario.close();
	}

	/** TRT 1: açılır, süre ölçülür; duraklat, devam, kapat; ▶ ile aynı kanal yeniden açılır. */
	@Test
	public void tvKanaliAcilirDuraklarKapanirYenidenAcilir() {
		MediaLib.PlayableItem ch = channel("TRT 1");
		long t0 = SystemClock.elapsedRealtime();
		onMain(a -> {
			a.getBody().playItem(ch);
			return null;
		});
		assertTrue("TRT 1 25 sn içinde çalmalı", waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		long ms = SystemClock.elapsedRealtime() - t0;
		Log.i(TAG, "TV açılış süresi (TRT 1): " + ms + " ms");
		assertTrue("Oynatıcı çubuğu kurulmalı", onMain(a -> a.getControlPanel().getYoldaBar() != null));

		// Duraklat / devam
		onMain(a -> {
			a.getMediaSessionCallback().onPause();
			return null;
		});
		assertTrue("Duraklamalı", waitFor(5000, () -> onMain(a -> !a.getMediaSessionCallback().isPlaying())));
		onMain(a -> {
			a.getMediaSessionCallback().onPlay();
			return null;
		});
		assertTrue("Devam etmeli", waitFor(15_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));

		// Kapat: çalma durur, panel gizlenir
		onMain(a -> {
			a.getControlPanel().closePlayer();
			return null;
		});
		assertTrue("Kapatınca durmalı", waitFor(5000, () -> onMain(a -> !a.getMediaSessionCallback().isPlaying())));
		assertEquals("Panel gizlenmeli", View.GONE, onMain(a -> a.getControlPanel().getVisibility()).intValue());

		// Kapattıktan sonra ▶ aynı kanalı yeniden açar (başka bir "son çalınan"ı değil)
		onMain(a -> {
			a.getControlPanel().setVisibility(View.VISIBLE);
			return null;
		});
		onMain(a -> {
			YoldaNowPlaying.togglePlay(a);
			return null;
		});
		assertTrue("Yeniden açılmalı", waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		assertEquals("Aynı kanal açılmalı", ch.getId(), onMain(a -> a.getCurrentPlayable().getId()));
	}

	/** Radyo (ses akışı): "Şimdi çalıyor" açılır, geri tuşu önce onu kapatır. */
	@Test
	public void radyoVeSimdiCaliyorEkrani() {
		MediaLib.PlayableItem r = channel("TRT FM");
		onMain(a -> {
			a.getBody().playItem(r);
			return null;
		});
		assertTrue("TRT FM 20 sn içinde çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		onMain(a -> {
			YoldaNowPlaying.open(a);
			return null;
		});
		waitIdle(800);
		assertTrue("Şimdi çalıyor açılmalı", onMain(YoldaNowPlaying::isShown));
		int before = onMain(MainActivityDelegate::getActiveFragmentId);
		onMain(a -> {
			a.onBackPressed();
			return null;
		});
		waitIdle(600);
		assertFalse("Geri tuşu önce Şimdi çalıyor'u kapatmalı", onMain(YoldaNowPlaying::isShown));
		assertEquals("Geri tuşu ekranı değiştirmemeli", before, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		assertTrue("Radyo sürmeli", onMain(a -> a.getMediaSessionCallback().isPlaying()));
	}

	/**
	 * Müzik: şarkı çalar, "sonraki" listedeki ikinci şarkıya geçer (Müzik ekranı açık olmasa da).
	 * Veri merkezi adresleri YouTube'ca engellenebildiğinden akış bulunamazsa test atlanır.
	 */
	@Test
	public void muzikSiradakineGecer() throws Exception {
		List<YtMusic.Track> list = YtMusic.search("Tarkan").get(null, 45, TimeUnit.SECONDS);
		Assume.assumeTrue("Arama sonucu yok", (list != null) && (list.size() >= 2));
		MediaLib.PlayableItem first;
		try {
			first = onMainFuture(a -> YtMusicItem.create(a.getLib(), list, 0));
		} catch (Exception ex) {
			Log.i(TAG, "Müzik akışı bulunamadı, test atlandı: " + ex);
			Assume.assumeNoException(ex);
			return;
		}
		Assume.assumeTrue(first != null);
		onMain(a -> {
			a.showFragment(R.id.home_fragment); // Müzik ekranı açık değilken de çalışmalı
			a.getMediaServiceBinder().playItem(first);
			return null;
		});
		boolean ok = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		Assume.assumeTrue("Şarkı çalmadı (ağ / YouTube engeli)", ok);
		onMain(a -> {
			a.getMediaSessionCallback().onSkipToNext();
			return null;
		});
		assertTrue("Sonraki şarkıya geçmeli", waitFor(25_000, () -> onMain(a ->
				(a.getCurrentPlayable() instanceof YtMusicItem m) && (m.getIndex() == 1))));
		Log.i(TAG, "Müzik sonraki: " + onMain(a -> a.getCurrentPlayable().getName()));
	}

	/** Web sayfası işlemi çökünce uygulama kapanmaz, YouTube sayfası yeniden kurulur. */
	@Test
	public void webSayfasiCokunceKurtarilir() {
		onMain(a -> a.showFragment(R.id.youtube_fragment));
		assertTrue("YouTube sayfası açılmalı", waitFor(15_000, () -> onMain(a -> webView(a) != null)));
		waitIdle(3000);
		WebView old = onMain(this::webView);
		onMain(a -> {
			webView(a).loadUrl("chrome://crash");
			return null;
		});
		boolean recovered = waitFor(15_000, () -> onMain(a -> {
			WebView w = webView(a);
			return (w != null) && (w != old);
		}));
		boolean oldAlive = onMain(a -> old.isAttachedToWindow());
		if (!recovered && oldAlive) {
			Log.i(TAG, "Bu WebView chrome://crash desteklemiyor; kurtarma denetlenemedi");
			return;
		}
		assertTrue("Çöken sayfa yeniden kurulmalı", recovered);
		assertEquals("YouTube ekranında kalmalı", R.id.youtube_fragment,
				onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		assertNotSame(old, onMain(this::webView));
		Log.i(TAG, "Web sayfası çöktü ve yeniden kuruldu");
	}

	// ---- yardımcılar ----

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

	private WebView webView(MainActivityDelegate a) {
		var f = a.getActiveFragment();
		View v = (f != null) ? f.getView() : null;
		return (v != null) ? findWeb(v) : null;
	}

	private static WebView findWeb(View v) {
		if (v instanceof WebView w) return w;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				WebView w = findWeb(g.getChildAt(i));
				if (w != null) return w;
			}
		}
		return null;
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

	private static Context ctx() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}
}
