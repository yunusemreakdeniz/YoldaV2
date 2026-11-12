package me.aap.fermata.media.yt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * Yolda Müzik: YouTube Music'te arama ve şarkının ses akışını bulma (açık kaynak NewPipeExtractor, GPL-3).
 * Tarayıcı yok, reklam yok; ses Yolda'nın kendi oynatıcısında çalar.
 * Ağ işleri arka planda yapılır; sonuçlar {@link FutureSupplier} ile döner.
 */
public final class YtMusic {
	private static final Localization LOC = new Localization("tr", "TR");
	private static boolean initialized;

	private YtMusic() {
	}

	/** Bir şarkı: arama sonucundan gelen bilgiler. */
	public record Track(String url, String title, String artist, @Nullable String thumb, long durationSec) {
		public String id() {
			int i = url.indexOf("v=");
			if (i < 0) return url;
			int e = url.indexOf('&', i);
			return (e < 0) ? url.substring(i + 2) : url.substring(i + 2, e);
		}
	}

	/** Çalınacak akış: adres ve o adres için gereken tarayıcı kimliği. */
	public record Stream(String url, @Nullable String userAgent, long durationSec, @Nullable String thumb) {
	}

	private static synchronized void init() {
		if (initialized) return;
		NewPipe.init(new YtDownloader(), LOC, new ContentCountry("TR"));
		initialized = true;
	}

	/** YouTube video araması (Yolda'nın YouTube ekranı): kanal adı "artist" alanında. */
	public static FutureSupplier<List<Track>> searchVideos(String query) {
		return search(query, YoutubeSearchQueryHandlerFactory.VIDEOS);
	}

	public static FutureSupplier<List<Track>> search(String query) {
		return search(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS);
	}

	private static FutureSupplier<List<Track>> search(String query, String filter) {
		Promise<List<Track>> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				init();
				SearchExtractor ex = ServiceList.YouTube.getSearchExtractor(query,
						Collections.singletonList(filter), "");
				ex.fetchPage();
				List<Track> out = new ArrayList<>();
				for (InfoItem i : ex.getInitialPage().getItems()) {
					if (!(i instanceof StreamInfoItem s)) continue;
					out.add(new Track(s.getUrl(), s.getName(), s.getUploaderName(), bestImage(s.getThumbnails()),
							Math.max(0, s.getDuration())));
				}
				p.complete(out);
			} catch (Throwable ex) {
				Log.e(ex, "Müzik araması başarısız: ", query);
				p.completeExceptionally(ex);
			}
		});
		return p;
	}

	/** Bulunan akışlar 2 saat saklanır (YouTube adresleri ~6 saat geçerli): sıradaki şarkı beklemeden başlar. */
	private static final long CACHE_MS = 2 * 60 * 60 * 1000L;
	private static final java.util.Map<String, Stream> cache = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Map<String, Long> cacheTime = new java.util.concurrent.ConcurrentHashMap<>();

	/** Şarkının akışını önceden bul (sıradaki şarkı için). */
	public static void prefetch(Track t) {
		if (cached(t) == null) resolve(t);
	}

	/** Akış adresi çalışmadıysa (süresi dolmuş olabilir) unut: bir dahakinde yeniden aranır. */
	public static void forget(Track t) {
		cache.remove(t.id());
		cacheTime.remove(t.id());
	}

	@Nullable
	private static Stream cached(Track t) {
		Long at = cacheTime.get(t.id());
		if ((at == null) || (System.currentTimeMillis() - at > CACHE_MS)) return null;
		return cache.get(t.id());
	}

	/** Şarkının en iyi ses akışı (önce m4a, sonra en yüksek bit hızı; yalnız doğrudan HTTP). */
	public static FutureSupplier<Stream> resolve(Track t) {
		Stream hit = cached(t);
		if (hit != null) return me.aap.utils.async.Completed.completed(hit);
		Promise<Stream> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				init();
				StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, t.url());
				AudioStream best = null;
				for (AudioStream a : info.getAudioStreams()) {
					if (a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || !a.isUrl()) continue;
					if ((best == null) || score(a) > score(best)) best = a;
				}
				if (best == null) throw new IllegalStateException("Ses akışı bulunamadı");
				String url = best.getContent();
				String thumb = (t.thumb() != null) ? t.thumb() : bestImage(info.getThumbnails());
				Stream s = new Stream(url, userAgentFor(url), info.getDuration(), thumb);
				cache.put(t.id(), s);
				cacheTime.put(t.id(), System.currentTimeMillis());
				p.complete(s);
			} catch (Throwable ex) {
				Log.e(ex, "Şarkı açılamadı: ", t.url());
				p.completeExceptionally(ex);
			}
		});
		return p;
	}

	private static int score(AudioStream a) {
		int s = Math.max(a.getAverageBitrate(), a.getBitrate() / 1000);
		if (a.getFormat() == MediaFormat.M4A) s += 10_000; // MediaPlayer m4a'yı her cihazda çalar
		return s;
	}

	/** YouTube akış adresini hangi istemcinin verdiğine göre o istemcinin kimliği gerekir; yoksa 403. */
	@Nullable
	private static String userAgentFor(String url) {
		try {
			if (YoutubeParsingHelper.isVisionOsStreamingUrl(url))
				return YoutubeParsingHelper.getVisionOsUserAgent(LOC);
		} catch (Throwable ignore) {
		}
		return YtDownloader.UA;
	}

	@Nullable
	private static String bestImage(@NonNull List<Image> images) {
		Image best = null;
		for (Image i : images) {
			if ((best == null) || (i.getHeight() > best.getHeight())) best = i;
		}
		return (best != null) ? best.getUrl() : null;
	}
}
