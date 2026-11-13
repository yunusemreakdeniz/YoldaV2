package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

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
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;
import me.aap.fermata.ui.view.YoldaChannelRail;
import me.aap.fermata.ui.view.YoldaNowPlaying;
import me.aap.fermata.ui.view.YoldaSwipe;

/**
 * Parmak hareketleri (gerçek dokunuş olayları ekrana gönderilir):
 * - "Şimdi çalıyor" ekranında sola kaydır → sonraki (radyo / şarkı), sağa → önceki; ilerleme çubuğunda sürükleme
 *   kanal değiştirmez;
 * - videoda sola kaydır → sonraki kanal (sarma değil);
 * - yayında videoya dokununca sağda kanal şeridi açılır, kaydırılır, dokunulan kanala geçilir, kontrollerle kapanır.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaGestureTest {
	private static final String TAG = "YoldaTest";
	private static final String SHOT_DIR = "/data/local/tmp/yolda-ui";
	private ActivityScenario<MainActivity> scenario;

	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		for (String p : new String[]{"android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS",
				"android.permission.READ_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_AUDIO",
				"android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_IMAGES"}) {
			try {
				InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(ctx().getPackageName(), p);
			} catch (Exception ignore) {
			}
		}
		ctx().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
				.putBoolean("wake_enabled", false).commit();
		scenario = ActivityScenario.launch(MainActivity.class);
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		waitIdle(2500);
	}

	@After
	public void stop() {
		try {
			onMain(a -> {
				YoldaNowPlaying.hide(a);
				a.getControlPanel().closePlayer();
				return null;
			});
		} catch (Throwable ignore) {
		}
		if (scenario != null) scenario.close();
	}

	@Test
	public void simdiCaliyordaKaydirincaSonrakiOnceki() {
		MediaLib.PlayableItem r = channel("TRT FM", false);
		onMain(a -> {
			a.getBody().playItem(r);
			YoldaNowPlaying.open(a);
			return null;
		});
		waitIdle(1500);
		assertTrue("Şimdi çalıyor açılmalı", onMain(YoldaNowPlaying::isShown));
		int[] box = onMain(a -> bounds(nowPlaying(a)));
		assertNotNull(box);

		// İlerleme çubuğunda yatay sürükleme: kanal değişmemeli
		View seek = onMain(a -> find(nowPlaying(a), me.aap.fermata.ui.view.YoldaSeekLine.class));
		if (seek != null) {
			int[] sb = onMain(a -> bounds(seek));
			long before = YoldaSwipe.lastAt;
			swipe(sb[0] + sb[2] * 0.8f, sb[1] + sb[3] / 2f, sb[0] + sb[2] * 0.2f, sb[1] + sb[3] / 2f);
			waitIdle(800);
			assertEquals("İlerleme çubuğunda sürükleme kaydırma sayılmamalı", before, YoldaSwipe.lastAt);
		}

		String first = name(onMain(this::cur));
		float y = box[1] + box[3] * 0.35f;
		swipe(box[0] + box[2] * 0.85f, y, box[0] + box[2] * 0.15f, y);
		assertTrue("Sola kaydırma algılanmalı", waitFor(3000, () -> (YoldaSwipe.lastDir == 1)
				&& (SystemClock.elapsedRealtime() - YoldaSwipe.lastAt < 5000)));
		assertTrue("Sonraki istasyona geçmeli (" + first + ")", waitFor(10_000, () -> {
			String n = name(onMain(this::cur));
			return (n != null) && !n.equals(first);
		}));
		shot("kaydirma_simdi_caliyor");
		Log.i(TAG, "Kaydırma: " + first + " → " + name(onMain(this::cur)));

		swipe(box[0] + box[2] * 0.15f, y, box[0] + box[2] * 0.85f, y);
		assertTrue("Sağa kaydırma önceki olmalı", waitFor(3000, () -> YoldaSwipe.lastDir == -1));
		assertTrue("Önceki istasyona dönmeli", waitFor(10_000, () -> first.equals(name(onMain(this::cur)))));
		assertTrue("Şimdi çalıyor açık kalmalı", onMain(YoldaNowPlaying::isShown));
	}

	@Test
	public void videodaKaydirincaSonrakiKanal() {
		VideoView vv = startTv();
		int[] b = onMain(a -> bounds(vv));
		String first = name(opening());
		long before = YoldaSwipe.lastAt;
		float y = b[1] + b[3] * 0.5f;
		swipe(b[0] + b[2] * 0.6f, y, b[0] + b[2] * 0.1f, y);
		assertTrue("Videoda sola kaydırma sonraki olmalı", waitFor(3000, () -> (YoldaSwipe.lastAt != before)
				&& (YoldaSwipe.lastDir == 1)));
		assertTrue("Başka kanala geçmeli (" + first + ")", waitFor(8000, () -> {
			String n = name(opening());
			return (n != null) && !n.equals(first);
		}));
		Log.i(TAG, "Video kaydırma: " + first + " → " + name(opening()));
	}

	@Test
	public void yayindaDokununcaKanalSeridi() {
		VideoView vv = startTv();
		onMain(a -> a.getControlPanel().onTouch(vv));
		assertTrue("Kanal şeridi açılmalı ve dolmalı", waitFor(10_000, () -> onMain(a -> rail(vv).channelCount() >= 3)));
		shot("kanal_seridi");
		int n = onMain(a -> rail(vv).channelCount());
		int[] rb = onMain(a -> bounds(rail(vv)));
		int[] vb = onMain(a -> bounds(vv));
		Log.i(TAG, "Kanal şeridi: " + n + " kanal, " + rb[2] + "×" + rb[3] + " px, video " + vb[2] + "×" + vb[3]);
		assertTrue("Şerit sağda olmalı", rb[0] + rb[2] >= vb[0] + vb[2] - 4);
		assertTrue("Şerit videonun yarısından dar olmalı", rb[2] < vb[2] / 2);

		// Şeride dokununca kontroller açık kalır; kanal seçilir
		String first = name(opening());
		boolean clicked = onMain(a -> rail(vv).clickChannel(first.equals(channelName(rail(vv), 0)) ? 1 : 0));
		assertTrue(clicked);
		assertTrue("Seçilen kanala geçmeli", waitFor(8000, () -> {
			String o = name(opening());
			return (o != null) && !o.equals(first);
		}));
		assertTrue("Günlükte kanal şeridi seçimi", YoldaLog.read(ctx()).contains("Kanal şeridi: "));
		assertEquals("Şerit seçimden sonra açık kalmalı", View.VISIBLE, onMain(a -> rail(vv).getVisibility()).intValue());

		// İkinci dokunuş kontrolleri ve şeridi kapatır
		onMain(a -> a.getControlPanel().onTouch(vv));
		assertTrue("Şerit kapanmalı", waitFor(3000, () -> onMain(a -> rail(vv).getVisibility() != View.VISIBLE)));
	}

	// ---- yardımcılar ----

	private VideoView startTv() {
		MediaLib.PlayableItem ch = channel(null, true);
		onMain(a -> {
			a.getBody().playItem(ch);
			return null;
		});
		boolean video = waitFor(8000, () -> onMain(a -> a.getBody().isVideoMode() && (a.getBody().getVideoView() != null)));
		Assume.assumeTrue("Video kipi açılmadı", video);
		return onMain(a -> a.getBody().getVideoView());
	}

	private static YoldaChannelRail rail(VideoView vv) {
		return YoldaChannelRail.of(vv);
	}

	private static String channelName(YoldaChannelRail r, int i) {
		ViewGroup list = (ViewGroup) ((ViewGroup) r.getChildAt(0)).getChildAt(0);
		if (i >= list.getChildCount()) return "";
		CharSequence d = list.getChildAt(i).getContentDescription();
		return (d != null) ? d.toString() : "";
	}

	private MediaLib.PlayableItem opening() {
		return onMain(a -> {
			MediaLib.PlayableItem o = MediaSessionCallback.yoldaOpeningItem();
			return (o != null) ? o : a.getCurrentPlayable();
		});
	}

	private MediaLib.PlayableItem cur(MainActivityDelegate a) {
		MediaLib.PlayableItem c = a.getCurrentPlayable();
		return (c != null) ? c : MediaSessionCallback.yoldaOpeningItem();
	}

	private static String name(MediaLib.PlayableItem pi) {
		return (pi != null) ? pi.getName() : null;
	}

	private static View nowPlaying(MainActivityDelegate a) {
		View root = ((android.app.Activity) a.getContext()).getWindow().getDecorView();
		return find(root, YoldaNowPlaying.class);
	}

	private static View find(View v, Class<?> c) {
		if ((v == null) || (v.getVisibility() != View.VISIBLE)) return null;
		if (c.isInstance(v)) return v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = find(g.getChildAt(i), c);
				if (f != null) return f;
			}
		}
		return null;
	}

	private static int[] bounds(View v) {
		if (v == null) return null;
		int[] l = new int[2];
		v.getLocationOnScreen(l);
		return new int[]{l[0], l[1], v.getWidth(), v.getHeight()};
	}

	/** Gerçek parmak hareketi gibi: basma, 8 adım kaydırma, bırakma (~200 ms). */
	private static void swipe(float x0, float y0, float x1, float y1) {
		Instrumentation in = InstrumentationRegistry.getInstrumentation();
		long t = SystemClock.uptimeMillis();
		in.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x0, y0, 0));
		for (int i = 1; i <= 8; i++) {
			float f = i / 8f;
			in.sendPointerSync(MotionEvent.obtain(t, t + i * 22L, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * f,
					y0 + (y1 - y0) * f, 0));
		}
		in.sendPointerSync(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, x1, y1, 0));
		in.waitForIdleSync();
	}

	/** Ad verilirse o kanal; video=true ise ilk TV (video) kanalı. */
	private MediaLib.PlayableItem channel(String name, boolean video) {
		AtomicReference<MediaLib.PlayableItem> found = new AtomicReference<>();
		long end = SystemClock.elapsedRealtime() + 30_000;
		while ((found.get() == null) && (SystemClock.elapsedRealtime() < end)) {
			try {
				me.aap.utils.async.FutureSupplier<List<MediaLib.PlayableItem>> f = onMain(a -> a.getLib().getItem("TV")
						.then(root -> ((MediaLib.BrowsableItem) root).getPlayableChildren(true)));
				List<MediaLib.PlayableItem> l = f.get(null, 30, TimeUnit.SECONDS);
				if (l != null) {
					for (MediaLib.PlayableItem pi : l) {
						if ((name != null) ? name.equals(pi.getName()) : (video && pi.isVideo())) {
							found.set(pi);
							break;
						}
					}
				}
			} catch (Exception ex) {
				Log.i(TAG, "Kanal listesi henüz hazır değil: " + ex);
			}
			if (found.get() == null) waitIdle(1000);
		}
		assertNotNull("Kanal bulunamadı: " + name, found.get());
		return found.get();
	}

	private void shot(String name) {
		try (ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
				.executeShellCommand("screencap -p " + SHOT_DIR + "/" + name + ".png");
				 FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
			byte[] buf = new byte[1024];
			//noinspection StatementWithEmptyBody
			while (in.read(buf) > 0) ;
		} catch (Exception ex) {
			Log.w(TAG, "Ekran görüntüsü: " + name, ex);
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
