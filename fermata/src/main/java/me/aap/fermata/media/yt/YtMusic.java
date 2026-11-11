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

	public static FutureSupplier<List<Track>> search(String query) {
		Promise<List<Track>> p = new Promise<>();
		App.get().getExecutor().submit(() -> {
			try {
				init();
				SearchExtractor ex = ServiceList.YouTube.getSearchExtractor(query,
						Collections.singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "");
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

	/** Şarkının en iyi ses akışı (önce m4a, sonra en yüksek bit hızı; yalnız doğrudan HTTP). */
	public static FutureSupplier<Stream> resolve(Track t) {
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
				p.complete(new Stream(url, userAgentFor(url), info.getDuration(), thumb));
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
