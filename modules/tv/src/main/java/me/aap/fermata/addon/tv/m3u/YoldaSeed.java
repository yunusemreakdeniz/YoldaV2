package me.aap.fermata.addon.tv.m3u;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

/**
 * Yolda: ilk açılışta uygulamayla gelen kanal listesini (assets/yolda.m3u) TV'ye kendiliğinden ekler.
 * Liste yalnız kanalların kendi yayın sunucularındaki adresleri içerir. Kullanıcı silerse geri eklenmez.
 * Uygulama güncellenince liste dosyası yenilenir; TV kaynağı aynı dosyayı okumaya devam eder.
 */
public final class YoldaSeed {
	private static final String FILE = "yolda.m3u";
	private static final Pref<BooleanSupplier> SEEDED = Pref.b("YOLDA_SEEDED", false).withInheritance(false);

	private YoldaSeed() {
	}

	public static void seed(TvRootItem root) {
		// Xtream Codes (Mola'dan): Yolda menüsünden verilen adresler TV kaynağı olarak eklenir
		me.aap.fermata.ui.fragment.YoldaXtream.adder = (name, url, epg, done) -> {
			BasicPreferenceStore xs = new BasicPreferenceStore();
			try (PreferenceStore.Edit e = xs.editPreferenceStore()) {
				e.setStringPref(M3uFile.NAME, name);
				e.setStringPref(M3uFile.URL, url);
				e.setStringPref(TvM3uFile.EPG_URL, epg);
			}
			TvM3uFileSystemProvider.loadSource(xs).main().onCompletion((m3u, err) -> {
				if (m3u != null) root.addSource(m3u);
				else Log.e(err, "Xtream listesi eklenemedi");
				done.accept(m3u != null);
			});
		};
		try {
			Context ctx = FermataApplication.get();
			File f = new File(ctx.getFilesDir(), FILE);
			copyAsset(ctx, f); // her açılışta güncel listeyi yaz
			if (root.getBooleanPref(SEEDED)) return;
			try (PreferenceStore.Edit e = root.editPreferenceStore()) {
				e.setBooleanPref(SEEDED, true);
			}

			BasicPreferenceStore ps = new BasicPreferenceStore();
			try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
				e.setStringPref(M3uFile.NAME, "Yolda Kanalları");
				e.setStringPref(M3uFile.URL, f.getAbsolutePath());
			}
			TvM3uFileSystemProvider.loadSource(ps).main().onCompletion((m3u, err) -> {
				if (m3u != null) root.addSource(m3u);
				else Log.e(err, "Yolda kanal listesi eklenemedi");
			});
		} catch (Exception ex) {
			Log.e(ex, "Yolda kanal listesi eklenemedi");
		}
	}

	private static void copyAsset(Context ctx, File dst) throws Exception {
		try (InputStream in = ctx.getAssets().open(FILE); OutputStream out = new FileOutputStream(dst)) {
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
		}
	}
}
