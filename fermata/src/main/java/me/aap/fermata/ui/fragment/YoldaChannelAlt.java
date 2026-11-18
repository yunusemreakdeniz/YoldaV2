package me.aap.fermata.ui.fragment;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.aap.utils.app.App;

/**
 * Kanalların yedek yayın adresleri: bir kanal açılmazsa herkese açık iptv-org Türkiye listesindeki aynı adlı
 * kanalın diğer adresleri sırayla denenir; çalışan adres hatırlanır (sonraki açılışta önce o). Liste haftada bir
 * telefonda yenilenir. Denetim telefonda yapılır: yurt dışı sunucudan denemek yanıltıcıydı (TRT gibi kanallar
 * yurt dışından engelli görünüyor).
 */
public final class YoldaChannelAlt {
	private static final String SRC = "https://iptv-org.github.io/iptv/countries/tr.m3u";
	private static final String FILE = "kanal_yedek.m3u";
	private static final long WEEK = 7L * 24 * 3600_000L;
	private static volatile Map<String, List<String>> byName;
	/** Bu oturumda denenen adresler (kanal adı → adresler): sonsuz döngü olmasın. */
	private static final Map<String, Set<String>> tried = new HashMap<>();

	private YoldaChannelAlt() {
	}

	/** Ad karşılaştırma biçimi: küçük harf, aksansız, HD / TV / Türkiye gibi ekler atılmış. */
	static String norm(String name) {
		String n = name.toLowerCase(new Locale("tr")).replace('ı', 'i');
		n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "");
		n = n.replaceAll("\\(.*?\\)|\\[.*?\\]", "");
		n = n.replaceAll("\\b(hd|fhd|sd|tv|1080p|720p|480p|turkiye|turkey|tr)\\b", "");
		return n.replaceAll("[^a-z0-9]", "");
	}

	/** m3u metninden ad → adresler. */
	static Map<String, List<String>> parse(String text) {
		Map<String, List<String>> m = new HashMap<>();
		String head = null;
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.startsWith("#EXTINF")) head = line;
			else if (!line.isEmpty() && !line.startsWith("#") && (head != null)) {
				int c = head.lastIndexOf(',');
				String name = (c >= 0) ? head.substring(c + 1).trim() : "";
				if (!name.isEmpty()) m.computeIfAbsent(norm(name), k -> new ArrayList<>()).add(line);
				head = null;
			}
		}
		return m;
	}

	/** Yedek listeyi yükle (yoksa / eskiyse arka planda indir). */
	public static void warmUp(Context ctx) {
		Context app = ctx.getApplicationContext();
		App.get().getExecutor().submit(() -> load(app));
	}

	private static synchronized Map<String, List<String>> load(Context app) {
		File f = new File(app.getFilesDir(), FILE);
		if (!f.isFile() || (System.currentTimeMillis() - f.lastModified() > WEEK)) {
			try {
				HttpURLConnection c = (HttpURLConnection) new URL(SRC).openConnection();
				c.setConnectTimeout(8000);
				c.setReadTimeout(15000);
				try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
					byte[] b = new byte[16384];
					for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
				}
				byName = null;
				YoldaLog.add("Kanal yedek listesi güncellendi");
			} catch (Throwable ex) {
				YoldaLog.add("Kanal yedek listesi alınamadı: " + ex.getClass().getSimpleName());
			}
		}
		if ((byName == null) && f.isFile()) {
			try {
				byName = parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
			} catch (Throwable ignore) {
			}
		}
		return byName;
	}

	/**
	 * Açılamayan kanal için denenmemiş sıradaki yedek adres; yoksa null. Hatırlanan çalışan adres önce gelir.
	 */
	@Nullable
	public static synchronized String next(Context ctx, String name, String failedUrl) {
		Context app = ctx.getApplicationContext();
		Set<String> t = tried.computeIfAbsent(name, k -> new HashSet<>());
		t.add(failedUrl);
		List<String> cand = new ArrayList<>();
		String good = prefs(app).getString(norm(name), null);
		if (good != null) cand.add(good);
		Map<String, List<String>> m = byName;
		if (m == null) m = load(app);
		if (m != null) {
			List<String> l = m.get(norm(name));
			if (l != null) cand.addAll(l);
		}
		for (String u : cand) if (!t.contains(u)) {
			t.add(u);
			return u;
		}
		return null;
	}

	/** Yedek adres çalıştı: bir dahakine önce o denensin. */
	public static void worked(Context ctx, String name, String url) {
		prefs(ctx.getApplicationContext()).edit().putString(norm(name), url).apply();
		synchronized (YoldaChannelAlt.class) {
			tried.remove(name);
		}
	}

	private static android.content.SharedPreferences prefs(Context app) {
		return app.getSharedPreferences("yolda_kanal_yedek", Context.MODE_PRIVATE);
	}
}
