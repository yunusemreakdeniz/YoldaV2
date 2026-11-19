package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Her tıklamanın testi (O): her Yolda ekranındaki görünür, dokunulabilir öğeler sırayla tıklanır. Her
 * tıklamadan sonra uygulama çökmemiş olmalı ve ekran boş kalmamalı. Tıklama başka uygulamayı açarsa
 * (harita, arama) Yolda öne getirilir; açılan pencere / seçici geri tuşuyla kapatılır, ekran yeniden açılır.
 * Hangi öğenin sorun çıkardığı günlüğe "YoldaClick" etiketiyle yazılır (çökmede son satır suçludur).
 */
@RunWith(AndroidJUnit4.class)
public class YoldaClickAllTest {
	private static final String TAG = "YoldaClick";
	/** Ekran başına en çok bu kadar öğe (süre sınırı; ilk görünenler en sık kullanılanlar). */
	private static final int MAX_PER_SCREEN = 10;
	private static final int[] FIRST = {R.id.home_fragment, R.id.yolda_menu_fragment, R.id.yolda_nav_fragment,
			R.id.yolda_radar_fragment, R.id.yolda_weather_fragment, R.id.yolda_fuel_fragment, R.id.yolda_park_fragment,
			R.id.yolda_info_fragment, R.id.yolda_status_fragment};
	private static final int[] SECOND = {R.id.yolda_notes_fragment, R.id.yolda_nearby_fragment,
			R.id.yolda_history_fragment, R.id.yolda_eq_fragment, R.id.yolda_pharmacy_fragment, R.id.yolda_trip_fragment,
			R.id.yolda_contacts_fragment, R.id.yolda_channels_fragment, R.id.yolda_music};

	private ActivityScenario<MainActivity> scenario;
	private final List<String> problems = new ArrayList<>();
	private int clicks;

	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(600);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO",
				"android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION",
				"android.permission.ACCESS_COARSE_LOCATION"}) {
			try {
				ui().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		ctx().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
				.putBoolean("wake_enabled", false).commit();
		scenario = ActivityScenario.launch(MainActivity.class);
		waitIdle(3000);
	}

	@After
	public void stop() {
		Log.i(TAG, "Toplam tıklama: " + clicks + ", sorun: " + problems.size());
		for (String p : problems) Log.i(TAG, "SORUN " + p);
		if (scenario != null) scenario.close();
	}

	@Test
	public void ilkEkranlar() {
		crawl(FIRST);
	}

	@Test
	public void digerEkranlar() {
		crawl(SECOND);
	}

	private void crawl(int[] screens) {
		for (int id : screens) {
			String name = ctx().getResources().getResourceEntryName(id);
			show(id);
			int n = onMain(a -> targets(a).size());
			for (int i = 0; i < Math.min(n, MAX_PER_SCREEN); i++) {
				final int k = i;
				String what = onMain(a -> {
					List<View> t = targets(a);
					if (k >= t.size()) return null;
					View v = t.get(k);
					String d = desc(v);
					Log.i(TAG, name + " #" + k + " tıklanıyor: " + d);
					v.performClick();
					return d;
				});
				if (what == null) break;
				clicks++;
				waitIdle(700);
				recover(id, name, what);
			}
		}
		assertTrue("Tıklanacak öğe bulunamadı", clicks > 0);
		assertTrue("Tıklamadan sonra sorun: " + problems, problems.isEmpty());
	}

	/** Tıklamadan sonra: uygulamayı öne getir, açılanı kapat, ekranı yeniden aç ve boş olmadığını denetle. */
	private void recover(int id, String name, String what) {
		if (scenario.getState() != Lifecycle.State.RESUMED) { // başka uygulama açıldı
			shell("am start -n " + ctx().getPackageName() + "/me.aap.fermata.ui.activity.MainActivity");
			waitIdle(1500);
		}
		if (scenario.getState() == Lifecycle.State.DESTROYED) { // tıklama etkinliği kapattı: yeniden aç
			problems.add(name + " / " + what + ": uygulama kapandı");
			scenario = ActivityScenario.launch(MainActivity.class);
			waitIdle(2500);
		}
		// İletişim kutusu odağı aldıysa geri tuşuyla kapat (ana ekranda geri tuşu uygulamadan çıkabilir: yalnız o zaman)
		Boolean focus = onMain(a -> ((android.app.Activity) a.getAppActivity()).getWindow().getDecorView().hasWindowFocus());
		if (!Boolean.TRUE.equals(focus)) {
			shell("input keyevent 4");
			waitIdle(400);
		}
		show(id);
		Boolean ok = onMain(a -> {
			ActivityFragment f = a.getActiveFragment();
			View v = (f != null) ? f.getView() : null;
			return (v != null) && (!(v instanceof ViewGroup g) || (g.getChildCount() > 0));
		});
		if (!Boolean.TRUE.equals(ok)) problems.add(name + " / " + what + ": ekran boş kaldı");
	}

	private void show(int id) {
		onMain(a -> {
			a.showFragment(id);
			return null;
		});
		waitIdle(1200);
	}

	/** Etkin ekrandaki görünür, etkin, tıklanabilir öğeler (yazı kutuları hariç; ekrandaki sırayla). */
	private static List<View> targets(MainActivityDelegate a) {
		List<View> out = new ArrayList<>();
		ActivityFragment f = a.getActiveFragment();
		View root = (f != null) ? f.getView() : null;
		if (root != null) collect(root, out);
		return out;
	}

	private static void collect(View v, List<View> out) {
		if ((v.getVisibility() != View.VISIBLE) || !v.isShown()) return;
		if (v.isClickable() && v.isEnabled() && v.hasOnClickListeners() && !(v instanceof EditText)) {
			Rect r = new Rect();
			if (v.getGlobalVisibleRect(r) && (r.width() > 0) && (r.height() > 0)) out.add(v);
		}
		if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
	}

	private static String desc(View v) {
		CharSequence c = v.getContentDescription();
		if ((c == null) && (v instanceof android.widget.TextView tv)) c = tv.getText();
		String s = (c == null) ? "" : c.toString().replace('\n', ' ');
		if (s.length() > 40) s = s.substring(0, 40);
		return v.getClass().getSimpleName() + "(" + s + ")";
	}

	// ---- yardımcılar ----

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

	private static void shell(String cmd) {
		try (ParcelFileDescriptor pfd = ui().executeShellCommand(cmd);
				 FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
			byte[] buf = new byte[1024];
			//noinspection StatementWithEmptyBody
			while (in.read(buf) > 0) ;
		} catch (Exception ex) {
			Log.w(TAG, "Kabuk komutu: " + cmd, ex);
		}
	}

	private static UiAutomation ui() {
		return InstrumentationRegistry.getInstrumentation().getUiAutomation();
	}

	private static Context ctx() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	private static void waitIdle(long ms) {
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignore) {
		}
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
	}
}
