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
		assertEquals("Destede 14 kart olmalı", 14, onMain(a -> ((YoldaDeckFragment) a.getActiveFragment()).getDeck().size()).intValue());
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
			// tarayıcıda geri önce sayfada geri gider: en çok birkaç basışta kartlara dönmeli
			for (int tries = 0; (tries < 4) && (onMain(MainActivityDelegate::getActiveFragmentId) != R.id.home_fragment); tries++) {
				onMain(a -> {
					a.getDeckBackButton().performClick();
					return null;
				});
				waitIdle(1200);
			}
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

	/** Telefon / ekran yansıtma kartı telefonda izin eksikse kurulum ekranını açar; çökmez. */
	@Test
	public void telefonKartiIzinEksikseKurulumaGider() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1500);
		boolean missing = !YoldaPhonePerms.missing(ctx()).isEmpty();
		Log.i(TAG, "Telefon izinleri eksik: " + YoldaPhonePerms.missing(ctx()));
		for (String k : new String[]{YoldaDeck.PHONE, YoldaDeck.MIRROR}) {
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).openCard(k);
				return null;
			});
			waitIdle(1500);
			if (missing) {
				assertEquals("İzin eksikken kurulum açılmalı: " + k, R.id.yolda_setup_fragment,
						onMain(MainActivityDelegate::getActiveFragmentId).intValue());
			}
			assertFalse("Telefonda yansıtma paneli açılmamalı", onMain(a -> a.getAppActivity().yoldaMirrorShown()));
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(1200);
		}
	}

	/** Harita isteği araçta Yolda'nın içindeki Google Haritalar adresine çevrilir. */
	@Test
	public void haritaAdresiDogru() {
		String nav = NavFragment.webMapUrl(new android.content.Intent(android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse("google.navigation:q=Taksim+Meydan%C4%B1")));
		assertNotNull(nav);
		assertTrue(nav, nav.startsWith("https://www.google.com/maps/dir/?api=1") && nav.contains("destination=Taksim"));
		String geo = NavFragment.webMapUrl(new android.content.Intent(android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse("geo:0,0?q=Ankara")));
		assertNotNull(geo);
		assertTrue(geo, geo.contains("query=Ankara"));
		assertEquals(null, NavFragment.webMapUrl(new android.content.Intent(android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse("https://example.com"))));
		// Araçta Android Auto navigasyonuna verilecek hedef
		assertEquals("Taksim Meydanı", NavFragment.destOf(new android.content.Intent(android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse("google.navigation:q=Taksim+Meydan%C4%B1"))).replace('+', ' '));
		assertEquals("Ankara", NavFragment.destOf(new android.content.Intent(android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse("geo:0,0?q=Ankara"))));
		assertEquals(null, NavFragment.destOf(new android.content.Intent(android.content.Intent.ACTION_VIEW,
				android.net.Uri.parse("geo:39.9,32.8"))));
		// Telefonda araç navigasyonu seçeneği kalıcı
		NavFragment.setInAppMap(ctx(), true);
		assertTrue(NavFragment.inAppMap(ctx()));
		NavFragment.setInAppMap(ctx(), false);
		assertFalse(NavFragment.inAppMap(ctx()));
	}

	/**
	 * Sayfa (Google Haritalar gibi) "uygulamada aç" için intent:// adresine giderse "Web sayfası mevcut değil"
	 * hatası çıkmaz: web karşılığı (https) açılır.
	 */
	@Test
	public void uygulamaBaglantisiHataSayfasiAcmaz() {
		Object f = onMain(a -> a.showFragment(R.id.web_browser_fragment));
		assertNotNull("Tarayıcı açılmalı", f);
		waitIdle(1500);
		String html = "<html><body><a id='a' href='intent://www.google.com/maps?yolda=1#Intent;scheme=https;"
				+ "package=com.google.android.apps.maps;end;'>harita</a><script>setTimeout(function(){"
				+ "location.href=document.getElementById('a').href;},500);</script></body></html>";
		String url = "data:text/html;base64," + android.util.Base64.encodeToString(
				html.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
		onMain(a -> {
			try {
				Object fr = a.getActiveFragment();
				fr.getClass().getMethod("loadUrl", String.class).invoke(fr, url);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			return null;
		});
		long end = android.os.SystemClock.elapsedRealtime() + 20_000;
		String cur = null;
		while (android.os.SystemClock.elapsedRealtime() < end) {
			waitIdle(500);
			cur = onMain(a -> {
				android.webkit.WebView w = findWeb(a.getActiveFragment().getView());
				return (w != null) ? w.getUrl() : null;
			});
			if ((cur != null) && cur.contains("google.com/maps")) break;
		}
		String log = YoldaLog.read(ctx());
		Log.i(TAG, "Uygulama bağlantısı sonrası adres: " + cur);
		assertTrue("intent:// yakalanmalı: " + log, log.contains("uygulama bağlantısı (intent) web karşılığı açıldı"));
		assertFalse("Hata sayfası açılmamalı: " + cur, (cur != null) && cur.startsWith("intent:"));
		assertTrue("Web karşılığı açılmalı: " + cur, (cur != null) && cur.contains("google.com/maps"));
	}

	/**
	 * Tarayıcıda sayfa içi yazı (araçta klavye): çok satırlı kutu ve ChatGPT tarzı düzenlenebilir alan; kesme
	 * işareti ve yeni satır içeren metin doğru yazılır.
	 */
	@Test
	public void tarayicidaSayfaIciYazi() throws Exception {
		Object f = onMain(a -> a.showFragment(R.id.web_browser_fragment));
		assertNotNull(f);
		waitIdle(1500);
		String html = "<html><body><textarea id='t'></textarea><div id='c' contenteditable='true'></div></body></html>";
		String url = "data:text/html;base64," + android.util.Base64.encodeToString(
				html.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
		onMain(a -> {
			android.webkit.WebView w = findWeb(a.getActiveFragment().getView());
			w.loadUrl(url);
			return null;
		});
		waitIdle(2500);
		String text = "Ankara'ya \"gidelim\" yarın";
		for (String id : new String[]{"t", "c"}) {
			String got = typeInto(id, text);
			Log.i(TAG, "Sayfa içi yazı (" + id + "): " + got);
			// evaluateJavascript sonucu JSON metni: tırnak içinde, " kaçışlı
			String plain = new org.json.JSONArray("[" + got + "]").getString(0).trim();
			assertEquals("Yazı alanına (" + id + ") metin aynen yazılmalı", text, plain);
		}
	}

	/** Alanı odaklayıp tarayıcının araç klavyesi köprüsüyle (setTextInput) yazar; alanın değerini döner. */
	private String typeInto(String id, String text) throws Exception {
		java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
		AtomicReference<String> r = new AtomicReference<>("");
		onMain(a -> {
			android.webkit.WebView w = findWeb(a.getActiveFragment().getView());
			w.evaluateJavascript("document.getElementById('" + id + "').focus()", v -> {
				try {
					java.lang.reflect.Method m = w.getClass().getSuperclass().getDeclaredMethod("setTextInput", CharSequence.class);
					m.setAccessible(true);
					m.invoke(w, text);
				} catch (Exception ex) {
					try {
						java.lang.reflect.Method m = w.getClass().getDeclaredMethod("setTextInput", CharSequence.class);
						m.setAccessible(true);
						m.invoke(w, text);
					} catch (Exception ex2) {
						Log.e(TAG, "setTextInput bulunamadı", ex2);
					}
				}
				w.postDelayed(() -> w.evaluateJavascript("(function(){var e=document.getElementById('" + id + "');"
						+ "return (e.value!==undefined&&e.tagName=='TEXTAREA')?e.value:e.innerText;})()", res -> {
					r.set(res);
					l.countDown();
				}), 600);
			});
			return null;
		});
		l.await(10, TimeUnit.SECONDS);
		return r.get();
	}

	@androidx.annotation.Nullable
	private static android.webkit.WebView findWeb(@androidx.annotation.Nullable View v) {
		if (v instanceof android.webkit.WebView w) return w;
		if (v instanceof android.view.ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				android.webkit.WebView w = findWeb(g.getChildAt(i));
				if (w != null) return w;
			}
		}
		return null;
	}

	/**
	 * Uzun kullanım: kartlara tekrar tekrar girip çıkılır; bellek sürekli büyümemeli (sızıntı), uygulama
	 * yanıt vermeye devam etmeli.
	 */
	@Test
	public void uzunKullanimdaBellekSismez() {
		String[] keys = {YoldaDeck.YOUTUBE, YoldaDeck.MUSIC, YoldaDeck.TV, YoldaDeck.RADIO, YoldaDeck.NAV,
				YoldaDeck.WEATHER, YoldaDeck.FAVS, YoldaDeck.SETTINGS};
		long first = 0;
		long last = 0;
		for (int round = 0; round < 8; round++) {
			for (String k : keys) {
				onMain(a -> a.showFragment(R.id.home_fragment));
				waitIdle(400);
				onMain(a -> {
					if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.openCard(k);
					return null;
				});
				waitIdle(500);
			}
			onMain(a -> a.showFragment(R.id.home_fragment));
			waitIdle(800);
			long used = usedHeap();
			Log.i(TAG, "Bellek tur " + round + ": " + (used / 1024 / 1024) + " MB");
			if (round == 2) first = used;
			last = used;
		}
		assertEquals("Desteye dönülmeli", R.id.home_fragment, onMain(MainActivityDelegate::getActiveFragmentId).intValue());
		long growMb = (last - first) / 1024 / 1024;
		assertTrue("5 turda bellek " + growMb + " MB büyüdü (sızıntı?)", growMb < 40);
	}

	private static long usedHeap() {
		Runtime r = Runtime.getRuntime();
		for (int i = 0; i < 3; i++) {
			r.gc();
			r.runFinalization();
			android.os.SystemClock.sleep(300);
		}
		return r.totalMemory() - r.freeMemory();
	}

	/**
	 * Ana ekrandaki ortadaki kartın görünen boyu (ölçek dahil) hiçbir durumda küçülmez: karta girip çıkınca,
	 * oynatıcı çubuğu açılınca / kapanınca, kartlar arasında kaydırıp dönünce, ekran döndürülüp geri gelince.
	 */
	@Test
	public void kartBoyuHicKuculmez() {
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(1800);
		int[] base = onMain(this::centerCardSize);
		Log.i(TAG, "Kart boyu: " + base[0] + "×" + base[1]);
		assertTrue("Kart görünmeli", base[1] > 0);
		java.util.function.Consumer<String> check = (when) -> {
			int[] now = onMain(this::centerCardSize);
			Log.i(TAG, "Kart boyu (" + when + "): " + now[0] + "×" + now[1]);
			assertTrue(when + ": kart küçülmemeli " + base[0] + "×" + base[1] + " → " + now[0] + "×" + now[1],
					(Math.abs(now[0] - base[0]) <= 6) && (Math.abs(now[1] - base[1]) <= 6));
		};

		// 1) karta gir, ⌂ ile dön
		for (String k : new String[]{YoldaDeck.TV, YoldaDeck.MUSIC, YoldaDeck.SETTINGS}) {
			onMain(a -> {
				((YoldaDeckFragment) a.getActiveFragment()).openCard(k);
				return null;
			});
			waitIdle(1200);
			onMain(a -> a.getDeckBackButton().performClick());
			waitIdle(1800);
			check.accept("karttan dönünce: " + k);
		}

		// 2) oynatıcı çubuğu açılınca ve kapanınca
		onMain(a -> {
			a.getControlPanel().setVisibility(View.VISIBLE);
			return null;
		});
		waitIdle(1800);
		check.accept("oynatıcı çubuğu açıkken");
		onMain(a -> {
			a.getControlPanel().setVisibility(View.GONE);
			return null;
		});
		waitIdle(1800);
		check.accept("oynatıcı çubuğu kapanınca");

		// 3) kaydırıp geri dön
		onMain(a -> {
			androidx.recyclerview.widget.RecyclerView rv = findRv(a.getActiveFragment().getView());
			rv.scrollBy(rv.getWidth() / 2, 0);
			return null;
		});
		waitIdle(1500);
		onMain(a -> {
			androidx.recyclerview.widget.RecyclerView rv = findRv(a.getActiveFragment().getView());
			rv.scrollBy(-rv.getWidth() / 2, 0);
			return null;
		});
		waitIdle(2000);
		check.accept("kaydırıp dönünce");

		// 4) TV açılınca (video) ⌂ ile ana sayfa, sonra TV kapatılınca: gövde bölünmemeli, kartlar küçülmemeli
		me.aap.fermata.media.lib.MediaLib.PlayableItem tv = null;
		try {
			List<me.aap.fermata.media.lib.MediaLib.PlayableItem> l = onMain(a -> a.getLib().getItem("TV").then(r ->
					((me.aap.fermata.media.lib.MediaLib.BrowsableItem) r).getPlayableChildren(true))).get(null, 30, TimeUnit.SECONDS);
			if (l != null) for (me.aap.fermata.media.lib.MediaLib.PlayableItem pi : l) if (pi.isVideo()) {
				tv = pi;
				break;
			}
		} catch (Exception ex) {
			Log.i(TAG, "Kanal listesi alınamadı: " + ex);
		}
		if (tv != null) {
			me.aap.fermata.media.lib.MediaLib.PlayableItem ch = tv;
			onMain(a -> {
				a.getBody().playItem(ch);
				return null;
			});
			waitIdle(4000);
			onMain(a -> a.getDeckBackButton().performClick());
			waitIdle(2000);
			assertFalse("Video sürerken ana sayfada gövde bölünmemeli", onMain(a -> a.getBody().isBothMode()));
			check.accept("TV açıkken ana sayfa");
			onMain(a -> {
				a.getControlPanel().closePlayer();
				return null;
			});
			waitIdle(2000);
			assertFalse("TV kapanınca gövde bölünmemeli", onMain(a -> a.getBody().isBothMode()));
			check.accept("TV kapanınca");
		}
	}

	/** Ortadaki kartın ekranda görünen eni ve boyu (ölçek dahil). */
	private int[] centerCardSize(MainActivityDelegate a) {
		androidx.recyclerview.widget.RecyclerView rv = findRv(a.getActiveFragment().getView());
		if ((rv == null) || (rv.getChildCount() == 0)) return new int[]{0, 0};
		float cx = rv.getWidth() / 2f;
		View best = null;
		float bd = Float.MAX_VALUE;
		for (int i = 0; i < rv.getChildCount(); i++) {
			View c = rv.getChildAt(i);
			float d = Math.abs((c.getLeft() + c.getRight()) / 2f + c.getTranslationX() - cx);
			if (d < bd) {
				bd = d;
				best = c;
			}
		}
		View card = (best instanceof android.view.ViewGroup g && g.getChildCount() > 0) ? g.getChildAt(0) : best;
		return new int[]{Math.round(card.getWidth() * best.getScaleX()), Math.round(card.getHeight() * best.getScaleY())};
	}

	private static androidx.recyclerview.widget.RecyclerView findRv(View v) {
		if (v instanceof androidx.recyclerview.widget.RecyclerView r) return r;
		if (v instanceof android.view.ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				androidx.recyclerview.widget.RecyclerView r = findRv(g.getChildAt(i));
				if (r != null) return r;
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
