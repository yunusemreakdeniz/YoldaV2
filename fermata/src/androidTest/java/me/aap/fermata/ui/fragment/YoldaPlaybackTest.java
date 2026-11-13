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
				"android.permission.READ_MEDIA_VIDEO", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"}) {
			try {
				InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		// "Hey Yolda" varsayılan açık; emülatörde ses aygıtı yok, dinleyici testleri yavaşlatmasın (ayrı testi var)
		InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences(
				me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).edit().putBoolean("wake_enabled", false).commit();
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
		boolean started = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		if (!started) {
			dumpDiag("TRT 1 başlamadı");
			// CI sunucusu yurt dışında: TRT yayını oradan açılmıyor (Source error). Gerçek ağ sorunuysa atla.
			Assume.assumeFalse("TRT 1 yayını bu ağdan açılmıyor (yurt dışı engeli)",
					YoldaLog.read(ctx()).contains("Oynatma hatası: TRT 1"));
		}
		assertTrue("TRT 1 25 sn içinde çalmalı", started);
		long ms = SystemClock.elapsedRealtime() - t0;
		Log.i(TAG, "TV açılış süresi (TRT 1): " + ms + " ms");
		assertTrue("Oynatıcı çubuğu kurulmalı", onMain(a -> a.getControlPanel().getYoldaBar() != null));

		// Tam ekran videodan geri ile çıkılır: yayın sürer, oynatıcı altta kalır, menü görünür
		waitFor(3000, () -> onMain(a -> a.getBody().isVideoMode()));
		onMain(a -> {
			a.onBackPressed();
			return null;
		});
		waitIdle(800);
		assertFalse("Geri ile tam ekrandan çıkılmalı", onMain(a -> a.getBody().isVideoMode()));
		assertTrue("Yayın sürmeli", onMain(a -> a.getMediaSessionCallback().isPlaying()));
		// Ana sayfada (kart destesi) çubuk yerine üstte "▶ çalan" düğmesi; diğer ekranlarda çubuk
		waitIdle(1800);
		assertTrue("Çalan şey görünmeli (destede üst düğme, diğer ekranlarda çubuk)", onMain(a ->
				(a.getActiveFragment() instanceof YoldaDeckFragment d) ? d.nowPlayingShown()
						: (a.getControlPanel().getVisibility() == View.VISIBLE)));
		onMain(a -> a.showFragment(R.id.yolda_channels_fragment));
		waitIdle(800);
		assertEquals("Diğer ekranda oynatıcı çubuğu görünmeli", View.VISIBLE,
				onMain(a -> a.getControlPanel().getVisibility()).intValue());
		assertEquals("Sesli düğme görünmeli", View.VISIBLE, onMain(a -> a.getYoldaVoiceButton().getVisibility()).intValue());

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
		boolean again = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		if (!again) dumpDiag("TRT 1 yeniden açılmadı");
		assertTrue("Yeniden açılmalı", again);
		assertEquals("Aynı kanal açılmalı", ch.getId(), onMain(a -> a.getCurrentPlayable().getId()));
	}

	/** Kanal değişince eski kanalın sesi susar: aynı anda yalnız bir ses akışı çalar (sesler üst üste binmez). */
	@Test
	public void kanalDegisinceSesUstUsteBinmez() {
		MediaLib.PlayableItem a1 = channel("TRT 1");
		MediaLib.PlayableItem a2 = channel("TRT Haber");
		onMain(a -> {
			a.getBody().playItem(a1);
			return null;
		});
		boolean ok = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		Assume.assumeTrue("TRT 1 bu ağdan açılmıyor", ok);
		waitIdle(3000);
		onMain(a -> {
			a.getBody().playItem(a2);
			return null;
		});
		boolean ok2 = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()
				&& (a.getCurrentPlayable() != null) && "TRT Haber".equals(a.getCurrentPlayable().getName())));
		Assume.assumeTrue("TRT Haber bu ağdan açılmıyor", ok2);
		waitIdle(4000);
		android.media.AudioManager am = (android.media.AudioManager) ctx().getSystemService(Context.AUDIO_SERVICE);
		int active = playing(am);
		Log.i(TAG, "Kanal değişince etkin ses akışı: " + active);
		for (android.media.AudioPlaybackConfiguration c : am.getActivePlaybackConfigurations()) Log.i(TAG, "SES AKIŞI " + c);
		for (String l : YoldaLog.read(ctx()).split("\n")) if (l.contains("Açıldı") || l.contains("hatası")) Log.i(TAG, "GÜNLÜK " + l);
		// Eski kanal anında susturulur; ses çıkışı, eski yayın arka planda kapanınca (takılı yayında birkaç sn) bırakılır
		long t0 = android.os.SystemClock.elapsedRealtime();
		boolean released = waitFor(15_000, () -> playing(am) <= 1);
		Log.i(TAG, "Eski kanalın ses çıkışı bırakıldı: " + released + " (" + (android.os.SystemClock.elapsedRealtime() - t0)
				+ " ms sonra, başta " + active + " akış)");
		assertTrue("Eski kanalın ses çıkışı bırakılmalı (" + playing(am) + " akış)", released);
		// Kapat: hiç ses kalmamalı
		onMain(a -> {
			a.getControlPanel().closePlayer();
			return null;
		});
		// Eski oynatıcı hemen susturulur; ses çıkışının bırakılması arka planda biraz sürebilir
		boolean silent = waitFor(20_000, () -> playing(am) == 0);
		Log.i(TAG, "Kapatınca çalan ses akışı: " + playing(am));
		assertTrue("Kapatınca ses tamamen kesilmeli (" + playing(am) + " akış)", silent);
	}

	/** Gerçekten çalan (başlatılmış) ses oynatıcılarının sayısı; duraklatılmış / kapanmakta olanlar sayılmaz. */
	private static int playing(android.media.AudioManager am) {
		int n = 0;
		for (android.media.AudioPlaybackConfiguration c : am.getActivePlaybackConfigurations()) {
			try {
				int st = (Integer) c.getClass().getMethod("getPlayerState").invoke(c);
				if (st == 2) n++; // PLAYER_STATE_STARTED
			} catch (Exception ex) {
				n++;
			}
		}
		return n;
	}

	/** Kanal değişince oynatıcı çubuğunda yeni kanalın adı hemen görünür (çalmaya başlamasını beklemeden). */
	@Test
	public void kanalDegisinceAdHemenGuncellenir() {
		MediaLib.PlayableItem a1 = channel("TRT 1");
		MediaLib.PlayableItem a2 = channel("TRT Haber");
		onMain(a -> {
			a.getBody().playItem(a1);
			return null;
		});
		boolean trt = waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		Assume.assumeTrue("TRT 1 yayını bu ağdan açılmıyor (yurt dışı engeli)",
				trt || !YoldaLog.read(ctx()).contains("Oynatma hatası: TRT 1"));
		assertTrue("TRT 1 çalmalı", trt);
		assertTrue("Çubukta TRT 1 yazmalı, yazan: " + barTitle(), waitFor(4000, () -> "TRT 1".contentEquals(barTitle())));
		long t0 = SystemClock.elapsedRealtime();
		onMain(a -> {
			a.getBody().playItem(a2);
			return null;
		});
		assertTrue("Yeni kanalın adı 1,5 sn içinde görünmeli", waitFor(1500, () -> "TRT Haber".contentEquals(barTitle())));
		Log.i(TAG, "Ad güncellenme süresi: " + (SystemClock.elapsedRealtime() - t0) + " ms");
		assertTrue("TRT Haber çalmalı", waitFor(25_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()
				&& a2.getId().equals(a.getCurrentPlayable().getId()))));
		waitIdle(1500);
		assertTrue("Çalarken de ad doğru kalmalı", barTitle().toString().contains("TRT Haber"));
	}

	private CharSequence barTitle() {
		return onMain(a -> {
			var b = a.getControlPanel().getYoldaBar();
			return (b != null) ? b.getTitleText() : "";
		});
	}

	/**
	 * Ses kısa süre başka uygulamaya geçince (Android Auto bildirimi, asistan) radyo / müzik sessize alınır;
	 * ses geri gelmeli: ▶ ile hemen, dokunulmazsa en geç 8 sn sonra kendiliğinden.
	 */
	@Test
	public void sesOdakKaybindanSonraGeriGelir() {
		MediaLib.PlayableItem r = channel("TRT FM");
		onMain(a -> {
			a.getBody().playItem(r);
			return null;
		});
		assertTrue("TRT FM çalmalı", waitFor(20_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		onMain(a -> {
			a.getMediaSessionCallback().onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
			return null;
		});
		waitIdle(500);
		if (onMain(a -> a.getMediaSessionCallback().isYoldaMuted())) {
			onMain(a -> {
				YoldaNowPlaying.togglePlay(a);
				return null;
			});
			waitIdle(500);
			assertFalse("▶ sesi geri getirmeli", onMain(a -> a.getMediaSessionCallback().isYoldaMuted()));
			assertTrue("Çalmaya devam etmeli", onMain(a -> a.getMediaSessionCallback().isPlaying()));
			onMain(a -> {
				a.getMediaSessionCallback().onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
				return null;
			});
			assertTrue("Ses 8 sn sonra kendiliğinden açılmalı",
					waitFor(12_000, () -> !onMain(a -> a.getMediaSessionCallback().isYoldaMuted())));
		} else { // duraklatan motor: odak gelince sürer
			onMain(a -> {
				a.getMediaSessionCallback().onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_GAIN);
				return null;
			});
			assertTrue("Odak gelince çalmalı", waitFor(15_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying())));
		}
	}

	/**
	 * Hızlı kanal değiştirme arayüzü dondurmaz: TV ve radyo arasında art arda geçilirken (önceki yayın ağda takılı
	 * olsa da) ana iş parçacığı hiçbir an 2,5 sn'den uzun kilitlenmez. Önceden VLC eski yayını kapatırken
	 * uygulama 5 sn donuyordu.
	 */
	@Test
	public void hizliKanalDegistirmeDondurmaz() {
		String[] names = {"TRT 1", "TRT FM", "ATV", "TRT Haber", "Show TV", "TRT FM"};
		List<MediaLib.PlayableItem> items = new java.util.ArrayList<>();
		for (String n : names) {
			try {
				items.add(channel(n));
			} catch (Throwable ex) {
				Log.i(TAG, "Kanal yok: " + n);
			}
		}
		Assume.assumeTrue("Kanal listesi yok", items.size() >= 3);
		long[] maxGap = {0};
		long[] last = {SystemClock.elapsedRealtime()};
		boolean[] run = {true};
		Runnable[] beat = new Runnable[1];
		android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
		beat[0] = () -> {
			long now = SystemClock.elapsedRealtime();
			maxGap[0] = Math.max(maxGap[0], now - last[0]);
			last[0] = now;
			if (run[0]) h.postDelayed(beat[0], 50);
		};
		h.post(beat[0]);
		for (int round = 0; round < 2; round++) {
			for (MediaLib.PlayableItem pi : items) {
				onMain(a -> {
					a.getBody().playItem(pi);
					return null;
				});
				SystemClock.sleep(700);
			}
		}
		SystemClock.sleep(6000); // son geçişin kapanışları da bitsin
		run[0] = false;
		SystemClock.sleep(200);
		Log.i(TAG, "Kanal değiştirirken en uzun ana iş parçacığı beklemesi: " + maxGap[0] + " ms");
		assertTrue("Arayüz donmamalı: en uzun bekleme " + maxGap[0] + " ms", maxGap[0] < 2500);
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

	/** Yolda'nın YouTube ekranından açılan video sayfa olarak değil, doğrudan tam ekran açılır. */
	@Test
	public void youtubeVideosuTamEkranAcilir() throws Exception {
		List<YtMusic.Track> l = YtMusic.searchVideos("trt haber").get(null, 45, TimeUnit.SECONDS);
		Assume.assumeTrue("Video araması sonuç vermedi", (l != null) && !l.isEmpty());
		String url = "https://m.youtube.com/watch?v=" + l.get(0).id();
		onMain(a -> {
			Object f = a.showFragment(R.id.youtube_fragment);
			try {
				f.getClass().getMethod("loadVideo", String.class).invoke(f, url);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			return null;
		});
		boolean playing = waitFor(40_000, () -> onMain(a -> a.getMediaSessionCallback().isPlaying()));
		Assume.assumeTrue("YouTube videosu bu ağdan oynamadı", playing);
		boolean full = waitFor(10_000, () -> onMain(a -> {
			Object f = a.getActiveFragment();
			try {
				return (Boolean) f.getClass().getMethod("isVideoFullScreen").invoke(f);
			} catch (Exception ex) {
				return false;
			}
		}));
		Log.i(TAG, "YouTube tam ekran: " + full + " · " + YoldaLog.read(ctx()).contains("YouTube: video tam ekran"));
		assertTrue("Video doğrudan tam ekran açılmalı", full);
		onMain(a -> {
			a.onBackPressed(); // geri: tam ekrandan sayfaya
			return null;
		});
		waitIdle(1500);
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

	/** Başarısızlıkta: oynatıcı durumu, motor, gövde kipi ve uygulamanın tanı günlüğü test kaydına. */
	private void dumpDiag(String why) {
		try {
			String st = onMain(a -> {
				var cb = a.getMediaSessionCallback();
				var s = cb.getPlaybackState();
				return "durum=" + ((s != null) ? s.getState() + " hata=" + s.getErrorMessage() : "yok")
						+ " motor=" + ((cb.getEngine() != null) ? cb.getEngine().getClass().getSimpleName() : "yok")
						+ " kip=" + a.getBody().getMode() + " öğe=" + ((a.getCurrentPlayable() != null)
						? a.getCurrentPlayable().getName() : "yok");
			});
			Log.i(TAG, "TANI " + why + ": " + st);
			for (String l : YoldaLog.read(ctx()).split("\n")) Log.i(TAG, "GÜNLÜK " + l);
		} catch (Throwable ex) {
			Log.i(TAG, "Tanı alınamadı: " + ex);
		}
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
