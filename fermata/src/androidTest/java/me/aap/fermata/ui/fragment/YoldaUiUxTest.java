package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
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
import me.aap.fermata.ui.view.YoldaNowPlaying;

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
			R.id.yolda_nav_fragment, R.id.yolda_menu_fragment, R.id.yolda_setup_fragment, R.id.yolda_music, R.id.yolda_pharmacy_fragment,
			R.id.yolda_radar_fragment};

	private ActivityScenario<MainActivity> scenario;
	private final List<String> report = new ArrayList<>();

	/** Takılan test tüm işi kilitlemesin: süre aşılırsa yalnız o test başarısız olur. */
	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO",
				"android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"}) {
			try {
				ui().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		// "Hey Yolda" varsayılan açık; emülatörde ses aygıtı yok, dinleyici testleri yavaşlatmasın (ayrı testi var)
		InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences(
				me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).edit().putBoolean("wake_enabled", false).commit();
		YoldaPharmacy.select(ctx(), new YoldaPharmacy.Place("ankara", "Ankara"), null);
		scenario = ActivityScenario.launch(MainActivity.class);
		waitIdle(3000);
	}

	@After
	public void stop() {
		shell("wm size reset");
		shell("wm density reset");
		shell("settings put system font_scale 1.0");
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

	/**
	 * Yatay tutulan geniş telefon (2340×1080, 420 dpi ≈ 891×411 dp; kullanıcının telefonu): alçak ekranda kartın
	 * içi kesilmemeli (önceden "Aç" düğmesi ve kutucuklar kartın altından taşıyordu). Oynatıcı çubuğu açıkken de.
	 */
	@Test
	public void yatayGenisTelefon() {
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		shell("wm size 1080x2340");
		shell("wm density 420");
		waitIdle(3000);
		List<String> all = new ArrayList<>(deckAllCards("genis_yatay"));
		// Yatayda kart geniş ve üstte (oynatıcı çubuğunun yeri altta)
		int[] cw = onMain(a -> {
			if (!(a.getActiveFragment() instanceof YoldaDeckFragment d)) return new int[]{0, 1, 0, 0};
			androidx.recyclerview.widget.RecyclerView rv = findRv(d.getView());
			if (rv == null) return new int[]{0, 1, 0, 0};
			View best = null;
			int mid = rv.getWidth() / 2, bd = Integer.MAX_VALUE;
			for (int i = 0; i < rv.getChildCount(); i++) {
				View c = rv.getChildAt(i);
				int dd = Math.abs((c.getLeft() + c.getRight()) / 2 - mid);
				if (dd < bd) {
					bd = dd;
					best = c;
				}
			}
			View card = (best instanceof ViewGroup g && g.getChildCount() > 0) ? g.getChildAt(0) : best;
			return (card == null) ? new int[]{0, 1, 0, 0} : new int[]{card.getWidth(), rv.getWidth(), card.getTop(), rv.getHeight()};
		});
		android.util.Log.i(TAG, "Yatay kart: " + cw[0] + "/" + cw[1] + " px genişlik, üstten " + cw[2] + " px");
		assertTrue("Yatayda kart listenin en az %70'i genişliğinde olmalı: " + cw[0] + "/" + cw[1], cw[0] >= cw[1] * 0.7f);
		assertTrue("Yatayda kart üstte olmalı: " + cw[2], cw[2] <= Math.round(8 * ctx().getResources().getDisplayMetrics().density));
		onMain(a -> {
			a.getControlPanel().setVisibility(View.VISIBLE);
			return null;
		});
		waitIdle(1500);
		all.addAll(audit("genis_yatay_cubuklu_deste"));
		onMain(a -> {
			a.getControlPanel().setVisibility(View.GONE);
			return null;
		});
		for (int id : SCREENS) {
			onMain(a -> a.showFragment(id));
			waitIdle(1800);
			all.addAll(audit("genis_yatay_" + ctx().getResources().getResourceEntryName(id)));
		}
		assertTrue(fmt(all), all.isEmpty());
	}

	/**
	 * Oynatıcı çubuğu açılınca içerik itilmez: arama kutusu yerinde kalır, ızgara küçülmez; çubuk arama
	 * kutusunun üstünde yüzer. Kart destesinde çubuk gösterilmez (kartlar tüm yüksekliği kullanır).
	 */
	@Test
	public void cubukAcilincaDuzenKaymaz() {
		onMain(a -> a.showFragment(R.id.yolda_channels_fragment));
		waitIdle(1800);
		int[] before = onMain(this::searchAndBody);
		onMain(a -> {
			a.getControlPanel().setVisibility(View.VISIBLE);
			return null;
		});
		waitIdle(1500);
		int[] after = onMain(this::searchAndBody);
		int[] bar = onMain(a -> {
			int[] l = new int[2];
			a.getControlPanel().getLocationOnScreen(l);
			return new int[]{l[1], l[1] + a.getControlPanel().getHeight(), a.getControlPanel().getVisibility()};
		});
		android.util.Log.i(TAG, "Arama y " + before[0] + "→" + after[0] + ", gövde " + before[1] + "→" + after[1]
				+ ", çubuk " + bar[0] + "-" + bar[1]);
		assertEquals("Arama kutusu yerinde kalmalı", before[0], after[0]);
		assertEquals("İçerik küçülmemeli", before[1], after[1]);
		assertEquals("Çubuk görünmeli", View.VISIBLE, bar[2]);
		assertTrue("Çubuk arama kutusunun üstünde olmalı", bar[1] <= after[0] + 2);
		screenshot("cubuk_kanallar");
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		assertEquals("Destede çubuk gizli", View.GONE, onMain(a -> a.getControlPanel().getVisibility()).intValue());
		onMain(a -> {
			a.getControlPanel().setVisibility(View.GONE);
			return null;
		});
	}

	/** Kanallar ekranındaki arama kutusunun üst kenarı ve gövdenin yüksekliği. */
	private int[] searchAndBody(MainActivityDelegate a) {
		View root = a.getActiveFragment().getView();
		View search = findEdit(root);
		int[] l = new int[2];
		if (search != null) search.getLocationOnScreen(l);
		return new int[]{(search != null) ? l[1] : -1, a.getBody().getHeight()};
	}

	private static View findEdit(View v) {
		if (v instanceof android.widget.EditText) return v;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				View f = findEdit(g.getChildAt(i));
				if (f != null) return f;
			}
		}
		return null;
	}

	/** Büyük yazı (Erişilebilirlik → yazı boyutu %130): yatayda kartlar ve ekranlar taşmamalı. */
	@Test
	public void buyukYazi() {
		shell("settings put system font_scale 1.3");
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		waitIdle(3000);
		onMain(a -> a.showFragment(R.id.yolda_menu_fragment));
		waitIdle(800);
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(2000);
		List<String> all = new ArrayList<>(deckAllCards("buyuk_yazi"));
		assertTrue(fmt(all), all.isEmpty());
	}

	/** Destedeki her kart sırayla ortaya getirilip denetlenir (her kartın içeriği farklı). */
	private List<String> deckAllCards(String mode) {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(2000);
		int n = onMain(a -> (a.getActiveFragment() instanceof YoldaDeckFragment d) ? d.getDeck().size() : 0);
		List<String> all = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int pos = i;
			onMain(a -> {
				if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.scrollToCard(pos);
				return null;
			});
			waitIdle(900);
			String key = onMain(a -> (a.getActiveFragment() instanceof YoldaDeckFragment d)
					? d.getDeck().get(pos).type() : "?");
			all.addAll(audit(mode + "_kart_" + pos + "_" + key));
		}
		return all;
	}

	/** Oynatıcı çubuğu: görünür yapılıp ayrıca denetlenir (dikey ve araç boyutu). */
	/** Her tema (canlı, Asil, Yumuşak) kart destesinde denetlenir; ekran görüntüleri kaydedilir. */
	@Test
	public void temalarDeste() {
		scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
		android.content.SharedPreferences p = ctx().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS,
				android.content.Context.MODE_PRIVATE);
		List<String> all = new ArrayList<>();
		try {
			for (int i = 0; i < me.aap.fermata.ui.view.YoldaStyle.THEMES.length; i++) {
				p.edit().putInt("theme", i).commit();
				onMain(a -> a.showFragment(R.id.yolda_menu_fragment));
				waitIdle(800);
				onMain(a -> a.showFragment(R.id.home_fragment));
				waitIdle(2000);
				int idx = i;
				assertEquals("Tema uygulanmalı", idx, onMain(a -> me.aap.fermata.ui.view.YoldaStyle.index(a.getContext())).intValue());
				all.addAll(audit("tema_" + i + "_deste"));
			}
		} finally {
			p.edit().putInt("theme", 0).commit();
		}
		assertTrue(fmt(all), all.isEmpty());
	}

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

	/** Tek "Şimdi çalıyor" ekranı: dikey telefon ve araç boyutunda. */
	@Test
	public void simdiCaliyorEkrani() {
		onMain(a -> {
			YoldaNowPlaying.expect("Şımarık", "Tarkan", null);
			YoldaNowPlaying.show(a);
			return null;
		});
		waitIdle(1500);
		assertTrue("Şimdi çalıyor açılmalı", onMain(YoldaNowPlaying::isShown));
		List<String> issues = audit("simdi_caliyor_dikey");
		shell("wm size 1600x800");
		shell("wm density 320");
		waitIdle(3000);
		onMain(a -> {
			YoldaNowPlaying.expect("Şımarık", "Tarkan", null);
			YoldaNowPlaying.show(a);
			return null;
		});
		waitIdle(1500);
		issues.addAll(audit("simdi_caliyor_arac"));
		onMain(YoldaNowPlaying::hide);
		YoldaNowPlaying.clearExpected();
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
		clipped(v, out, d, name);
		if ((v.isClickable() || v.hasOnClickListeners()) && v.isEnabled() && fullyVisible && !(v instanceof ViewGroup
				&& ((ViewGroup) v).getChildCount() > 3 && full.height() > sh / 2)) {
			int wd = Math.round(full.width() / d), hd = Math.round(full.height() / d);
			if (Math.min(wd, hd) < MIN_TOUCH_DP) {
				out.add(name + ": küçük dokunma alanı " + wd + "×" + hd + " dp — " + desc(v));
			}
			clickables.add(v);
		}
		if ((v instanceof android.widget.TextView tv) && !(v instanceof android.widget.EditText) && fullyVisible) {
			textIssues(tv, out, d, name);
		}
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), clickables, out, d, sw, sh, name);
		}
	}

	/**
	 * Yazı kusurları: sözcüğün ortadan bölünmesi ("Na/vig/as/yon", "Rady/o") ve büyük başlığın "…" ile kesilmesi
	 * ("Kaldığı…"). İkisi de ekranı amatör gösterir.
	 */
	private static void textIssues(android.widget.TextView tv, List<String> out, float d, String name) {
		android.text.Layout l = tv.getLayout();
		CharSequence t = tv.getText();
		if ((l == null) || (t == null) || (t.length() < 2)) return;
		for (int i = 0; i < l.getLineCount() - 1; i++) {
			int end = l.getLineEnd(i);
			if ((end <= 0) || (end >= t.length())) continue;
			if (Character.isLetter(t.charAt(end - 1)) && Character.isLetter(t.charAt(end))) {
				out.add(name + ": sözcük ortadan bölünmüş — " + desc(tv));
				return;
			}
		}
		float sp = tv.getTextSize() / tv.getResources().getDisplayMetrics().scaledDensity;
		if (sp >= 20) {
			for (int i = 0; i < l.getLineCount(); i++) {
				if (l.getEllipsisCount(i) > 0) {
					out.add(name + ": başlık kesik — " + desc(tv));
					return;
				}
			}
		}
	}

	/**
	 * Kabına sığmayan öğe: düğme, yazı ya da resim kendi kabının (kart, satır) dışına taşıyor ve kesik görünüyor.
	 * Kaydırılan kaplar (liste, yatay şerit) sayılmaz; çok soluk süs simgeleri (zemin işareti) bilerek taşar.
	 */
	private static void clipped(View v, List<String> out, float d, String name) {
		if (v.getAlpha() < 0.3f) return;
		boolean leaf = (v instanceof android.widget.TextView) || (v instanceof android.widget.ImageView)
				|| v.isClickable() || v.hasOnClickListeners();
		if (!leaf || (v.getWidth() <= 0) || (v.getHeight() <= 0)) return;
		int tol = Math.round(3 * d);
		// v'nin konumu, sırayla her üst kabın içinde (yerleşim koordinatları; ölçek / kaydırma animasyonu hariç)
		int x = v.getLeft(), y = v.getTop();
		ViewParent p = v.getParent();
		for (int depth = 0; (depth < 10) && (p instanceof ViewGroup pg); depth++) {
			if (isScrollContainer(pg) || (pg.getWidth() <= 0) || (pg.getHeight() <= 0)) return;
			int over = Math.max(Math.max(-x, -y), Math.max(x + v.getWidth() - pg.getWidth(), y + v.getHeight() - pg.getHeight()));
			if (over > tol) {
				String m = name + ": kesik / kabına sığmıyor (" + Math.round(over / d) + " dp taşıyor) — " + desc(v)
						+ " ⊂ " + desc(pg);
				if (!out.contains(m)) out.add(m);
				return;
			}
			x += pg.getLeft() - pg.getScrollX();
			y += pg.getTop() - pg.getScrollY();
			p = pg.getParent();
		}
	}

	private static androidx.recyclerview.widget.RecyclerView findRv(View v) {
		if (v instanceof androidx.recyclerview.widget.RecyclerView r) return r;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				androidx.recyclerview.widget.RecyclerView r = findRv(g.getChildAt(i));
				if (r != null) return r;
			}
		}
		return null;
	}

		private static boolean isScrollContainer(ViewGroup g) {
		String n = g.getClass().getName();
		return (g instanceof ScrollView) || (g instanceof HorizontalScrollView) || (g instanceof android.widget.AbsListView)
				|| n.contains("RecyclerView") || n.contains("ViewPager") || n.contains("NestedScrollView")
				|| n.contains("DecorView") || (g.getParent() == null);
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
		// Kabuk ile doğrudan /data/local/tmp'ye: test bitince uygulama kaldırılsa da CI dosyaları alır
		shell("mkdir -p " + SHOT_DIR);
		shell("screencap -p " + SHOT_DIR + "/" + name + ".png");
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
		shell("mkdir -p " + SHOT_DIR);
		shell("cp " + new File(outDir(), "rapor.txt").getAbsolutePath() + " " + SHOT_DIR + "/rapor.txt");
		for (String s : report) Log.i(TAG, "RAPOR " + s);
	}

	private static final String SHOT_DIR = "/data/local/tmp/yolda-ui";

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
