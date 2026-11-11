package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.ControlPanelView;

/**
 * Yolda UI/UX testleri: her ekran dikey, yatay ve araç boyutunda (800×400 dp) açılır, ekran görüntüsü
 * alınır ve ölçülebilir kurallar denetlenir:
 * <ul>
 *   <li>dokunulabilir her öğe en az 36 dp (parmakla, araçta sürüş sırasında rahat basılsın)</li>
 *   <li>dokunulabilir öğeler birbirinin üstüne binmez</li>
 *   <li>kaydırılabilir alan dışında hiçbir öğe ekrandan taşmaz</li>
 * </ul>
 * Ekran görüntüleri ve bulgular cihazda Android/data/&lt;paket&gt;/files/ui altına yazılır (CI bunları toplar).
 */
@RunWith(AndroidJUnit4.class)
public class YoldaUiUxTest {
	private static final String TAG = "YoldaUiUx";
	private static final int MIN_TOUCH_DP = 36;
	private static final int[] SCREENS = {R.id.home_fragment, R.id.yolda_channels_fragment, R.id.yolda_youtube,
			R.id.yolda_nav_fragment, R.id.yolda_menu_fragment, R.id.yolda_setup_fragment};

	private ActivityScenario<MainActivity> scenario;
	private final List<String> report = new ArrayList<>();

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO",
				"android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION"}) {
			try {
				ui().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		scenario = ActivityScenario.launch(MainActivity.class);
		waitIdle(3000);
	}

	@After
	public void stop() {
		shell("wm size reset");
		shell("wm density reset");
		if (scenario != null) scenario.close();
		writeReport();
	}

	@Test
	public void dikeyEkranlar() {
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
		waitIdle(1500);
		auditAll("dikey");
	}

	@Test
	public void yatayEkranlar() {
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		waitIdle(2000);
		auditAll("yatay");
	}

	/** Android Auto ekranına benzer: geniş ve alçak (800×400 dp). */
	@Test
	public void aracBoyutu() {
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		shell("wm size 1600x800");
		shell("wm density 320");
		waitIdle(3000);
		auditAll("arac");
	}

	/** Oynatıcı çubuğu: görünür yapılıp ayrıca denetlenir (dikey ve araç boyutu). */
	@Test
	public void oynaticiCubugu() {
		onMain(a -> {
			a.showFragment(R.id.home_fragment);
			ControlPanelView cp = a.getControlPanel();
			cp.setVisibility(View.VISIBLE);
			return null;
		});
		waitIdle(1500);
		List<String> issues = audit("oynatici_dikey");
		assertTrue("Oynatıcı çubuğu kurulmalı", onMain(a -> a.getControlPanel().getYoldaBar() != null));
		shell("wm size 1600x800");
		shell("wm density 320");
		waitIdle(2500);
		onMain(a -> {
			a.getControlPanel().setVisibility(View.VISIBLE);
			return null;
		});
		waitIdle(1000);
		issues.addAll(audit("oynatici_arac"));
		assertTrue(fmt(issues), issues.isEmpty());
	}

	// ---- denetim ----

	private void auditAll(String mode) {
		List<String> all = new ArrayList<>();
		for (int id : SCREENS) {
			onMain(a -> a.showFragment(id));
			waitIdle(1800);
			String name = mode + "_" + ctx().getResources().getResourceEntryName(id);
			all.addAll(audit(name));
		}
		assertTrue(fmt(all), all.isEmpty());
	}

	/** Etkin pencerenin görünür öğelerini kurallara göre denetler, ekran görüntüsünü kaydeder. */
	private List<String> audit(String name) {
		screenshot(name);
		List<String> issues = onMain(a -> {
			List<String> out = new ArrayList<>();
			Activity act = (Activity) a.getContext();
			View root = act.getWindow().getDecorView();
			float d = root.getResources().getDisplayMetrics().density;
			int sw = root.getWidth();
			int sh = root.getHeight();
			List<View> clickables = new ArrayList<>();
			collect(root, clickables, out, d, sw, sh, name);
			// üst üste binen dokunulabilir öğeler
			for (int i = 0; i < clickables.size(); i++) {
				for (int j = i + 1; j < clickables.size(); j++) {
					View v1 = clickables.get(i), v2 = clickables.get(j);
					if (isAncestor(v1, v2) || isAncestor(v2, v1)) continue;
					// Kaydırılabilir içeriğin üstünde yüzen düğme (sesli asistan) sorun değil: içerik kaydırılınca
					// altındaki öğe açığa çıkar ve sayfalar altta bunun için boşluk bırakır
					if (inScrollable(v1) != inScrollable(v2)) continue;
					Rect r1 = rect(v1), r2 = rect(v2);
					Rect in = new Rect();
					if (!in.setIntersect(r1, r2)) continue;
					long ia = (long) in.width() * in.height();
					long small = Math.min((long) r1.width() * r1.height(), (long) r2.width() * r2.height());
					if ((small > 0) && (ia * 4 > small)) {
						out.add(name + ": üst üste binme — " + desc(v1) + " ↔ " + desc(v2));
					}
				}
			}
			return out;
		});
		for (String s : issues) Log.w(TAG, s);
		report.add("== " + name + ": " + (issues.isEmpty() ? "sorun yok" : (issues.size() + " sorun")));
		report.addAll(issues);
		return issues;
	}

	private void collect(View v, List<View> clickables, List<String> out, float d, int sw, int sh, String name) {
		if ((v.getVisibility() != View.VISIBLE) || (v.getAlpha() < 0.05f) || (v.getScaleX() < 0.05f)) return;
		if (v instanceof WebView) return; // web sayfasının içi denetlenmez
		Rect full = rect(v);
		Rect vis = new Rect();
		boolean shown = v.getGlobalVisibleRect(vis);
		if (!shown || full.isEmpty()) return;
		boolean fullyVisible = (vis.width() == full.width()) && (vis.height() == full.height());
		boolean inScroll = inScrollable(v);

		if (!inScroll && ((full.left < -2) || (full.right > sw + 2)) && (full.width() < sw * 2)) {
			out.add(name + ": ekrandan taşıyor — " + desc(v));
		}
		if ((v.isClickable() || v.hasOnClickListeners()) && v.isEnabled() && fullyVisible && !(v instanceof ViewGroup
				&& ((ViewGroup) v).getChildCount() > 3 && full.height() > sh / 2)) {
			int wd = Math.round(full.width() / d), hd = Math.round(full.height() / d);
			if (Math.min(wd, hd) < MIN_TOUCH_DP) {
				out.add(name + ": küçük dokunma alanı " + wd + "×" + hd + " dp — " + desc(v));
			}
			clickables.add(v);
		}
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), clickables, out, d, sw, sh, name);
		}
	}

	private static boolean inScrollable(View v) {
		for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
			if ((p instanceof HorizontalScrollView) || (p instanceof ScrollView)
					|| p.getClass().getName().contains("RecyclerView")) return true;
		}
		return false;
	}

	private static boolean isAncestor(View a, View b) {
		for (ViewParent p = b.getParent(); p instanceof View; p = p.getParent()) if (p == a) return true;
		return false;
	}

	private static Rect rect(View v) {
		int[] l = new int[2];
		v.getLocationOnScreen(l);
		return new Rect(l[0], l[1], l[0] + v.getWidth(), l[1] + v.getHeight());
	}

	private static String desc(View v) {
		String id = "";
		try {
			if (v.getId() != View.NO_ID) id = "#" + v.getResources().getResourceEntryName(v.getId());
		} catch (Exception ignore) {
		}
		CharSequence cd = v.getContentDescription();
		String text = (v instanceof android.widget.TextView tv) ? ("\"" + tv.getText() + "\"") : "";
		return v.getClass().getSimpleName() + id + " " + text + ((cd != null) ? (" (" + cd + ")") : "");
	}

	private static String fmt(List<String> issues) {
		return issues.isEmpty() ? "" : (issues.size() + " UI/UX sorunu:\n" + String.join("\n", issues));
	}

	// ---- ekran görüntüsü ve rapor ----

	private void screenshot(String name) {
		try {
			Bitmap b = ui().takeScreenshot();
			if (b == null) return;
			File f = new File(outDir(), name + ".png");
			try (FileOutputStream out = new FileOutputStream(f)) {
				b.compress(Bitmap.CompressFormat.PNG, 90, out);
			}
		} catch (Exception ex) {
			Log.w(TAG, "Ekran görüntüsü alınamadı: " + name, ex);
		}
	}

	private void writeReport() {
		try (FileOutputStream out = new FileOutputStream(new File(outDir(), "rapor.txt"), true)) {
			for (String s : report) out.write((s + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} catch (Exception ignore) {
		}
	}

	private static File outDir() {
		File d = new File(ctx().getExternalFilesDir(null), "ui");
		//noinspection ResultOfMethodCallIgnored
		d.mkdirs();
		return d;
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
