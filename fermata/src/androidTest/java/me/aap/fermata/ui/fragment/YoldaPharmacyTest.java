package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaPharmacy.Pharmacy;
import me.aap.fermata.ui.fragment.YoldaPharmacy.Place;

/**
 * Nöbetçi eczaneler (gerçek kaynaklarla): Ankara Eczacı Odası listesi koordinatlı; 81 il ve ilçeleri;
 * İstanbul / Kadıköy nöbetçileri ve konumları; gün değiştirme; ekran seçimi hatırlar, yıldızlılar üstte
 * kısayol olur, konum varsa uzaklık yazar ve en yakını önce gelir.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaPharmacyTest {
	private static final String TAG = "YoldaTest";
	private static final String SHOT_DIR = "/data/local/tmp/yolda-ui";
	private ActivityScenario<MainActivity> scenario;

	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	@Before
	public void start() {
		MainActivityDelegate.skipSetupThisRun = true;
		String pkg = ctx().getPackageName();
		for (String p : new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
				"android.permission.POST_NOTIFICATIONS", "android.permission.READ_EXTERNAL_STORAGE",
				"android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO"}) {
			try {
				InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(pkg, p);
			} catch (Exception ignore) {
			}
		}
		YoldaPharmacy.prefs(ctx()).edit().putBoolean("wake_enabled", false).remove("ecz_star_il")
				.remove("ecz_star_ilce").commit();
		scenario = ActivityScenario.launch(MainActivity.class);
		waitIdle(2500);
	}

	@After
	public void stop() {
		removeMockLocation();
		if (scenario != null) scenario.close();
	}

	@Test
	public void ankaraNobetcileriKoordinatli() throws Exception {
		Place ankara = new Place("ankara", "Ankara");
		YoldaPharmacy.Duty d = YoldaPharmacy.duty(ctx(), ankara, null, 0).get(null, 40, TimeUnit.SECONDS);
		Log.i(TAG, "Ankara nöbetçi: " + d.list().size() + " · " + d.validity());
		assertTrue("Ankara'da nöbetçi eczane olmalı", d.list().size() >= 10);
		int ll = 0, tel = 0;
		for (Pharmacy p : d.list()) {
			if (p.hasLocation()) ll++;
			if (!p.phone().isEmpty()) tel++;
			assertTrue("Ad boş olmamalı", !p.name().isEmpty());
			assertTrue("Ad düzgün yazılmalı (BÜYÜK HARF değil): " + p.name(), !p.name().equals(p.name().toUpperCase()));
		}
		Pharmacy f = d.list().get(0);
		Log.i(TAG, "Örnek: " + f.name() + " | " + f.district() + " | " + f.address() + " | " + f.note() + " | " + f.phone()
				+ " | " + f.lat() + "," + f.lon());
		assertTrue("Çoğunun koordinatı olmalı (" + ll + ")", ll >= d.list().size() * 0.8);
		assertTrue("Çoğunun telefonu olmalı (" + tel + ")", tel >= d.list().size() * 0.8);

		YoldaPharmacy.Duty c = YoldaPharmacy.duty(ctx(), ankara, new Place("cankaya", "Çankaya"), 0)
				.get(null, 40, TimeUnit.SECONDS);
		assertTrue("Çankaya'da nöbetçi olmalı", !c.list().isEmpty());
		for (Pharmacy p : c.list()) assertEquals("İlçe süzgeci", "Çankaya", p.district());

		YoldaPharmacy.Duty t = YoldaPharmacy.duty(ctx(), ankara, null, 3).get(null, 40, TimeUnit.SECONDS);
		Log.i(TAG, "Ankara +3 gün: " + t.list().size() + " · " + t.validity());
		assertTrue("Ankara'da ileri tarih de alınmalı", !t.list().isEmpty());
	}

	@Test
	public void illerIlcelerVeIstanbulNobetcileri() throws Exception {
		List<Place> iller = YoldaPharmacy.provinces(ctx()).get(null, 40, TimeUnit.SECONDS);
		Log.i(TAG, "İl sayısı: " + iller.size());
		assertTrue("81 il olmalı", iller.size() >= 81);
		Place ist = null;
		for (Place p : iller) if (p.slug().equals("istanbul")) ist = p;
		assertNotNull("İstanbul listede olmalı", ist);
		assertEquals("İstanbul", ist.name());
		List<Place> ilceler = YoldaPharmacy.districts(ctx(), ist).get(null, 40, TimeUnit.SECONDS);
		Log.i(TAG, "İstanbul ilçe sayısı: " + ilceler.size());
		assertTrue("İstanbul'un ilçeleri", ilceler.size() >= 30);
		Place kadikoy = null;
		for (Place p : ilceler) if (p.slug().equals("kadikoy")) kadikoy = p;
		assertNotNull("Kadıköy listede olmalı", kadikoy);
		assertEquals("Kadıköy", kadikoy.name());

		for (int off = 0; off <= 2; off++) {
			YoldaPharmacy.Duty d = YoldaPharmacy.duty(ctx(), ist, kadikoy, off).get(null, 40, TimeUnit.SECONDS);
			Calendar c = YoldaPharmacy.day(off);
			Log.i(TAG, "Kadıköy " + off + ": " + d.list().size() + " · " + d.validity());
			assertTrue("Gün " + off + " geçerlilik yazısı o günü göstermeli: " + d.validity(),
					d.validity().startsWith(c.get(Calendar.DAY_OF_MONTH) + " "));
			assertTrue("Kadıköy'de nöbetçi olmalı (gün " + off + ")", !d.list().isEmpty());
		}
		YoldaPharmacy.Duty d = YoldaPharmacy.duty(ctx(), ist, kadikoy, 0).get(null, 40, TimeUnit.SECONDS);
		Pharmacy f = d.list().get(0);
		assertTrue("Adres olmalı", !f.address().isEmpty());
		assertTrue("Telefon olmalı", !f.phone().isEmpty());
		assertEquals("İlçe", "Kadıköy", f.district());

		// Konumlar eczanenin sayfasından bulunur ve saklanır
		AtomicReference<Pharmacy> got = new AtomicReference<>();
		YoldaPharmacy.resolveLocations(ctx(), d.list(), (i, p) -> got.compareAndSet(null, p));
		assertTrue("En az bir eczanenin konumu bulunmalı", waitFor(30_000, () -> got.get() != null));
		Pharmacy g = got.get();
		Log.i(TAG, "Konum: " + g.name() + " " + g.lat() + "," + g.lon());
		assertTrue("Kadıköy civarı olmalı", Math.abs(g.lat() - 40.98) < 0.2 && Math.abs(g.lon() - 29.06) < 0.2);
		YoldaPharmacy.Duty again = YoldaPharmacy.duty(ctx(), ist, kadikoy, 0).get(null, 40, TimeUnit.SECONDS);
		boolean cached = false;
		for (Pharmacy p : again.list()) cached |= p.hasLocation();
		assertTrue("Bulunan konum saklanmalı", cached);
	}

	@Test
	public void uzaklikHesabi() {
		double km = YoldaPharmacy.km(39.925, 32.836, 41.008, 28.978); // Ankara – İstanbul kuş uçuşu ~350 km
		assertTrue("Ankara–İstanbul ~350 km: " + km, km > 330 && km < 370);
		assertEquals("850 m", YoldaPharmacy.fmtKm(0.851));
		assertEquals("1,2 km", YoldaPharmacy.fmtKm(1.24));
		assertEquals("14 km", YoldaPharmacy.fmtKm(13.6));
		assertEquals("Kadıköy", YoldaPharmacy.districtOf("Caferağa Mahallesi, Muvakkıthane Caddesi No:3 Kadıköy / İstanbul"));
		assertEquals("cankaya", YoldaPharmacy.fold("Çankaya"));
		assertEquals("Parlak Eczanesi", YoldaPharmacy.title("PARLAK ECZANESİ"));
	}

	/** Ekran: seçim hatırlanır, yıldızlılar kısayol olur, konumla uzaklık yazar ve en yakını önce gelir. */
	@Test
	public void ekranSecimYildizUzaklik() {
		boolean mock = setMockLocation(40.9875, 29.0270); // Kadıköy iskelesi
		YoldaPharmacy.select(ctx(), new Place("istanbul", "İstanbul"), new Place("kadikoy", "Kadıköy"));
		YoldaPharmacy.toggleStar(ctx(), "ilce", "istanbul/kadikoy", "Kadıköy");
		YoldaPharmacy.toggleStar(ctx(), "il", "ankara", "Ankara");
		onMain(a -> a.showFragment(R.id.yolda_pharmacy_fragment));
		assertTrue("Liste dolmalı", waitFor(40_000, () -> onMain(a -> fragment(a).shownCount() > 0)));
		assertTrue("Yıldızlı ilçe kısayolu görünmeli", onMain(a -> findText(a, "★ Kadıköy") != null));
		assertTrue("Yıldızlı il kısayolu görünmeli", onMain(a -> findText(a, "★ Ankara") != null));
		if (mock) {
			assertTrue("Uzaklık yazmalı: " + onMain(a -> fragment(a).statusText()),
					waitFor(40_000, () -> String.valueOf(onMain(a -> fragment(a).statusText())).contains("en yakını")));
		} else {
			Log.i(TAG, "Sahte konum kurulamadı; uzaklık denetlenmedi");
		}
		waitIdle(1500);
		shot("eczane_kadikoy");
		Log.i(TAG, "Eczane ekranı: " + onMain(a -> fragment(a).statusText()));

		// Ankara kısayolu: tek dokunuşla Ankara, seçim kalıcı
		onMain(a -> findText(a, "★ Ankara").performClick());
		assertTrue("Ankara listesi gelmeli", waitFor(40_000, () -> onMain(a -> fragment(a).shownCount() > 5)));
		assertEquals("ankara", YoldaPharmacy.selectedIl(ctx()).slug());
		assertEquals("İlçe sıfırlanmalı", null, YoldaPharmacy.selectedIlce(ctx()));
		shot("eczane_ankara");
		onMain(a -> a.showFragment(R.id.home_fragment));
		waitIdle(800);
		onMain(a -> a.showFragment(R.id.yolda_pharmacy_fragment));
		assertTrue("Son seçim hatırlanmalı", waitFor(40_000, () -> onMain(a -> (findText(a, "Ankara") != null)
				&& (fragment(a).shownCount() > 5))));
	}

	// ---- yardımcılar ----

	private PharmacyFragment fragment(MainActivityDelegate a) {
		return (PharmacyFragment) a.getActiveFragment();
	}

	private static TextView findText(MainActivityDelegate a, String s) {
		return find(((android.app.Activity) a.getContext()).getWindow().getDecorView(), s);
	}

	private static TextView find(View v, String s) {
		if (v.getVisibility() != View.VISIBLE) return null;
		if ((v instanceof TextView tv) && s.contentEquals(tv.getText())) return tv;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				TextView f = find(g.getChildAt(i), s);
				if (f != null) return f;
			}
		}
		return null;
	}

	private boolean setMockLocation(double lat, double lon) {
		try {
			shell("appops set " + ctx().getPackageName() + " android:mock_location allow");
			LocationManager lm = ctx().getSystemService(LocationManager.class);
			try {
				lm.removeTestProvider(LocationManager.GPS_PROVIDER);
			} catch (Exception ignore) {
			}
			lm.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
					android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE);
			lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
			Location l = new Location(LocationManager.GPS_PROVIDER);
			l.setLatitude(lat);
			l.setLongitude(lon);
			l.setAccuracy(5);
			l.setTime(System.currentTimeMillis());
			l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
			lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, l);
			waitIdle(500);
			double[] loc = YoldaPharmacy.myLocation(ctx());
			Log.i(TAG, "Sahte konum: " + ((loc != null) ? (loc[0] + "," + loc[1]) : "yok"));
			return (loc != null) && (Math.abs(loc[0] - lat) < 0.01);
		} catch (Throwable ex) {
			Log.i(TAG, "Sahte konum kurulamadı: " + ex);
			return false;
		}
	}

	private void removeMockLocation() {
		try {
			ctx().getSystemService(LocationManager.class).removeTestProvider(LocationManager.GPS_PROVIDER);
		} catch (Throwable ignore) {
		}
	}

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
			waitIdle(400);
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
