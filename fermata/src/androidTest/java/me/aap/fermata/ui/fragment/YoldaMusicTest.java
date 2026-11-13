package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
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

/**
 * Yolda Müzik (YouTube Music): kapak resimleri gerçekten yüklenir, öneriler dolar, şarkı açılınca ses gelir
 * (oynatıcı sessiz değil), "Sonraki" şarkıyı değiştirir, liste bitince benzer şarkılarla sürer.
 * Ekran görüntüleri /data/local/tmp/yolda-ui altına (CI raporuna) kaydedilir.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaMusicTest {
	private static final String TAG = "YoldaTest";
	private static final String SHOT_DIR = "/data/local/tmp/yolda-ui";
	private ActivityScenario<MainActivity> scenario;

	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(300);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS",
				"android.permission.READ_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_AUDIO",
				"android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_IMAGES",
				"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"}) {
			try {
				InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		ctx().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
				.putBoolean("wake_enabled", false).commit();
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

	/** Müzik ekranı: öneri ızgarası dolar ve kapak resimleri yüklenir (renkli yer tutucu kalmaz). */
	@Test
	public void muzikEkraniResimlerVeOneriler() {
		onMain(a -> a.showFragment(R.id.yolda_music));
		boolean ok = waitFor(45_000, () -> onMain(a -> loadedCovers(a) >= 4));
		int covers = onMain(this::loadedCovers);
		int all = onMain(this::coverSlots);
		shot("muzik_ana");
		Log.i(TAG, "Müzik ana sayfa: " + all + " kapak yeri, " + covers + " resim yüklendi");
		assertTrue("Öneriler gelmeli (kapak yeri: " + all + ")", all >= 6);
		assertTrue("Kapak resimleri yüklenmeli (" + covers + "/" + all + ")", ok);
	}

	/**
	 * Şarkı açılır, ses gerçekten çıkar (ses akışı etkin; YouTube oynatıcısına düşüldüyse video sessiz değil),
	 * "Sonraki" başka şarkıya geçer. CI sunucusunda YouTube çoğu zaman "bot değilim" doğrulaması ister: o zaman
	 * hangi yolun denendiği kaydedilir ve test atlanır.
	 */
	@Test
	public void muzikCalarSesGelirSonrakineGecer() throws Exception {
		List<YtMusic.Track> list = YtMusic.search("Tarkan").get(null, 45, TimeUnit.SECONDS);
		Assume.assumeTrue("Arama sonucu yok", (list != null) && (list.size() >= 3));
		onMain(a -> {
			MusicFragment.playTracks(a, list, 0);
			return null;
		});
		boolean sound = waitFor(50_000, this::soundActive);
		shot("muzik_caliyor");
		String path = MusicFragment.playingViaYoutube() ? "YouTube oynatıcısı" : "doğrudan akış";
		String js = MusicFragment.playingViaYoutube() ? videoState() : "-";
		Log.i(TAG, "Müzik çalma: yol=" + path + " ses=" + sound + " video=" + js);
		for (String l : YoldaLog.read(ctx()).split("\n")) if (l.contains("Müzik")) Log.i(TAG, "GÜNLÜK " + l);
		if (!sound) {
			String log = YoldaLog.read(ctx());
			boolean blocked = log.contains("Sign in") || log.contains("bot") || log.contains("LOGIN_REQUIRED")
					|| js.contains("video-yok");
			Assume.assumeFalse("YouTube bu ağdan çalmaya izin vermiyor (" + path + ", " + js + ")", blocked);
		}
		assertTrue("Şarkı sesli çalmalı (" + path + ", video: " + js + ")", sound);
		if (MusicFragment.playingViaYoutube()) {
			assertTrue("YouTube videosu sessiz olmamalı: " + js, !js.contains("sessiz"));
		}
		// Ses sürmeli: ekran YouTube'a geçmeden, arkadaki sayfa duraklatılmadan (önceden birkaç sn sonra kesiliyordu)
		assertEquals("Ekran Müzik'te kalmalı (YouTube'a geçmemeli)", R.id.yolda_music,
				onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		waitIdle(10_000);
		String later = MusicFragment.playingViaYoutube() ? videoState() : "-";
		Log.i(TAG, "Müzik 10 sn sonra: " + later + " ses=" + soundActive());
		assertTrue("10 sn sonra da ses sürmeli (" + later + ")", soundActive());

		String first = MusicFragment.nowPlayingId();
		onMain(a -> {
			a.getMediaSessionCallback().onSkipToNext();
			return null;
		});
		boolean changed = waitFor(30_000, () -> {
			String id = MusicFragment.nowPlayingId();
			if ((id == null) || id.equals(first)) return false;
			MediaLib.PlayableItem cur = onMain(MainActivityDelegate::getCurrentPlayable);
			if (cur instanceof YtMusicItem m) return m.getTrack().id().equals(id);
			String u = onMain(this::youtubeUrl);
			return (u != null) && u.contains(id);
		});
		shot("muzik_sonraki");
		assertTrue("Sonraki şarkıya geçmeli (" + first + " → " + MusicFragment.nowPlayingId() + ")", changed);
		assertTrue("Sonraki şarkı da sesli çalmalı", waitFor(40_000, this::soundActive));

		// YouTube'da müzik dışı bir video açılınca müzik sayfayı bırakır (haber açınca müzik oynatıcısı gelmesin)
		List<YtMusic.Track> vids = YtMusic.searchVideos("trt haber").get(null, 45, TimeUnit.SECONDS);
		if ((vids != null) && !vids.isEmpty()) {
			String vurl = "https://m.youtube.com/watch?v=" + vids.get(0).id();
			onMain(a -> {
				Object f = a.showFragment(R.id.youtube_fragment);
				try {
					f.getClass().getMethod("loadVideo", String.class).invoke(f, vurl);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
				return null;
			});
			boolean released = waitFor(20_000, () -> !MusicFragment.playingViaYoutube());
			Log.i(TAG, "YouTube videosu açılınca müzik bıraktı: " + released);
			assertTrue("YouTube'da video açılınca müzik sayfayı bırakmalı", released);
		}
	}

	/** Tek şarkılık liste (ör. favoriden): bitince benzer şarkılarla sürer, "Sonraki" boşa düşmez. */
	@Test
	public void tekSarkiListesiBenzerlerleSurer() throws Exception {
		List<YtMusic.Track> list = YtMusic.search("Sezen Aksu").get(null, 45, TimeUnit.SECONDS);
		Assume.assumeTrue("Arama sonucu yok", (list != null) && !list.isEmpty());
		YtMusic.Track one = list.get(0);
		List<YtMusic.Track> more = YtMusic.search(YtMusicItem.related(one)).get(null, 45, TimeUnit.SECONDS);
		List<YtMusic.Track> ext = YtMusicItem.extend(java.util.Collections.singletonList(one), more);
		Log.i(TAG, "Benzer şarkılar: " + (ext.size() - 1) + " (" + YtMusicItem.related(one) + ")");
		assertTrue("Benzer şarkı bulunmalı", ext.size() >= 3);
		assertTrue("İlk şarkı başta kalmalı", ext.get(0).id().equals(one.id()));
		java.util.Set<String> ids = new java.util.HashSet<>();
		for (YtMusic.Track t : ext) assertTrue("Tekrar eden şarkı olmamalı: " + t.title(), ids.add(t.id()));
	}

	// ---- denetimler ----

	/** Ses gerçekten çıkıyor mu: müzik akışı etkin (sessiz web videosu ses akışı açmaz). */
	private boolean soundActive() {
		// Emülatör ses kartsız (-noaudio): web sayfasının sesi sisteme "çalan müzik" olarak görünmeyebilir. Doğrudan
		// akışta oynatıcının durumu, YouTube oynatıcısında videonun sessiz olmayıp ilerlemesi ölçülür.
		if (!MusicFragment.playingViaYoutube()) {
			AudioManager am = (AudioManager) ctx().getSystemService(Context.AUDIO_SERVICE);
			return onMain(a -> a.getMediaSessionCallback().isPlaying()) && am.isMusicActive();
		}
		return videoState().contains("sesli-caliyor");
	}

	private String videoState() {
		AtomicReference<String> r = new AtomicReference<>("sayfa-yok");
		java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
		boolean started = onMain(a -> {
			WebView w = youtubeWeb(a);
			if (w == null) return false;
			w.evaluateJavascript("(function(){var v=document.querySelector('video');if(!v)return 'video-yok';"
					+ "return (v.muted||v.volume==0?'sessiz':'sesli')+(v.paused?'-durgun':'-caliyor')+' '+location.pathname;})()", s -> {
				r.set(s);
				l.countDown();
			});
			return true;
		});
		if (!started) return "sayfa-yok";
		try {
			l.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException ignore) {
		}
		return r.get();
	}

	private String youtubeUrl(MainActivityDelegate a) {
		WebView w = youtubeWeb(a);
		return (w != null) ? w.getUrl() : null;
	}

	private WebView youtubeWeb(MainActivityDelegate a) {
		for (androidx.fragment.app.Fragment f : a.getSupportFragmentManager().getFragments()) {
			if ((f instanceof me.aap.utils.ui.fragment.ActivityFragment af) && (af.getFragmentId() == R.id.youtube_fragment)) {
				WebView w = findWeb(f.getView());
				if (w != null) return w;
			}
		}
		return null;
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

	/** Müzik ekranındaki kapak resmi yerleri (kapak: resim + nota yer tutucusu olan kare). */
	private int coverSlots(MainActivityDelegate a) {
		View v = musicView(a);
		return (v == null) ? 0 : count(v, false);
	}

	private int loadedCovers(MainActivityDelegate a) {
		View v = musicView(a);
		return (v == null) ? 0 : count(v, true);
	}

	private View musicView(MainActivityDelegate a) {
		for (androidx.fragment.app.Fragment f : a.getSupportFragmentManager().getFragments()) {
			if ((f instanceof MusicFragment) && (f.getView() != null)) return f.getView();
		}
		return null;
	}

	/** Kapak: çocuğu iki ImageView olan çerçeve (nota + resim); resim görünür ve bitmap'liyse yüklenmiş. */
	private static int count(View v, boolean loaded) {
		int n = 0;
		if ((v instanceof android.widget.FrameLayout f) && (f.getChildCount() >= 2)
				&& (f.getChildAt(0) instanceof ImageView) && (f.getChildAt(1) instanceof ImageView img)) {
			if (!loaded) return 1;
			return ((img.getVisibility() == View.VISIBLE) && (img.getDrawable() instanceof BitmapDrawable)) ? 1 : 0;
		}
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) n += count(g.getChildAt(i), loaded);
		}
		return n;
	}

	// ---- yardımcılar ----

	private void shot(String name) {
		shell("mkdir -p " + SHOT_DIR);
		shell("screencap -p " + SHOT_DIR + "/" + name + ".png");
	}

	private static void shell(String cmd) {
		try (ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(cmd);
				 FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
			byte[] buf = new byte[1024];
			//noinspection StatementWithEmptyBody
			while (in.read(buf) > 0) ;
		} catch (Exception ex) {
			Log.w(TAG, "Kabuk komutu: " + cmd, ex);
		}
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

	private static boolean waitFor(long ms, BooleanSupplier cond) {
		long end = SystemClock.elapsedRealtime() + ms;
		while (SystemClock.elapsedRealtime() < end) {
			if (cond.getAsBoolean()) return true;
			waitIdle(500);
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
