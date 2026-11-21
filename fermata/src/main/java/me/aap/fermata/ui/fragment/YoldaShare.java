package me.aap.fermata.ui.fragment;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.IntentPlayable;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Telefondan araca gönder (Mola'dan uyarlandı): başka bir uygulamada "Paylaş → Yolda" denince paylaşılan şey Yolda'da
 * açılır; Android Auto bağlıysa araçta çalar. YouTube bağlantısı oynatıcıda, yayın adresi (m3u8, mp3, aac…)
 * doğrudan, başka sayfa tarayıcıda; bağlantısız yazı YouTube'da aranır.
 */
public final class YoldaShare {
	private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>]+");
	private static final Pattern YT_ID = Pattern.compile(
			"(?:youtu\\.be/|[?&]v=|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})");

	private YoldaShare() {
	}

	/** Yazıdaki ilk bağlantı (sondaki noktalama atılır); yoksa null. */
	@Nullable
	static String firstUrl(@Nullable String text) {
		if (text == null) return null;
		Matcher m = URL.matcher(text);
		if (!m.find()) return null;
		return m.group().replaceAll("[.,;:!?)\\]]+$", "");
	}

	/** YouTube video kimliği (11 karakter); değilse null. */
	@Nullable
	static String youtubeId(String url) {
		String l = url.toLowerCase(Locale.ROOT);
		if (!l.contains("youtube.com") && !l.contains("youtu.be")) return null;
		Matcher m = YT_ID.matcher(url);
		return m.find() ? m.group(1) : null;
	}

	/** Doğrudan çalınacak yayın adresi mi (radyo / TV akışı, ses dosyası). */
	static boolean isStream(String url) {
		String l = url.replaceAll("[?#].*$", "").toLowerCase(Locale.ROOT); // sorgu kısmı atılır
		return l.endsWith(".m3u8") || l.endsWith(".m3u") || l.endsWith(".pls") || l.endsWith(".mp3") || l.endsWith(".aac")
				|| l.endsWith(".ogg") || l.endsWith(".opus") || l.endsWith(".mp4") || l.endsWith(".ts") || l.contains("/stream")
				|| l.contains("/listen") || l.contains("/live.");
	}

	/** Paylaşım ise açar ve true döner. */
	public static boolean handle(MainActivityDelegate a, Intent intent) {
		if (!Intent.ACTION_SEND.equals(intent.getAction())) return false;
		CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
		String text = (cs != null) ? cs.toString().trim() : "";
		if (text.isEmpty()) return false;
		intent.setAction(Intent.ACTION_MAIN); // döndürmede / yeniden açılışta tekrar çalışmasın
		String url = firstUrl(text);
		YoldaLog.add("Paylaşıldı: " + ((url != null) ? url : text));
		me.aap.utils.app.App.get().getHandler().postDelayed(() -> open(a, text, url), 300);
		return true;
	}

	private static void open(MainActivityDelegate a, String text, @Nullable String url) {
		if (url == null) { // bağlantı yok: YouTube'da ara
			YoldaStyle.toast(a.getContext(), "YouTube'da aranıyor: " + text);
			YoutubeHomeFragment.searchFor(a, text);
			return;
		}
		String id = youtubeId(url);
		if (id != null) {
			YoldaStyle.toast(a.getContext(), "Yolda'da açılıyor");
			MusicFragment.releaseYoutube();
			Object f = a.showFragment(R.id.youtube_fragment);
			if (f != null) try {
				f.getClass().getMethod("loadVideo", String.class).invoke(f, "https://m.youtube.com/watch?v=" + id);
			} catch (Throwable ex) {
				YoldaLog.add("Paylaşılan video açılamadı: " + ex.getClass().getSimpleName());
			}
			return;
		}
		if (isStream(url)) {
			YoldaStyle.toast(a.getContext(), "Yayın çalınıyor");
			a.getMediaServiceBinder().playItem(new IntentPlayable(a, Uri.parse(url)));
			return;
		}
		YoldaStyle.toast(a.getContext(), "Tarayıcıda açılıyor");
		HomeFragment.openWeb(a, url);
	}
}
