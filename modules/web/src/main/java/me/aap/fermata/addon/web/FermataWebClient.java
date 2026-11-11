package me.aap.fermata.addon.web;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebClient extends WebViewClientCompat {
	BooleanConsumer loading;

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (loading != null) {
			loading.accept(true);
		} else {
			MainActivityDelegate.getActivityDelegate(view.getContext())
					.onSuccess(a -> a.setContentLoading(new Promise<>()));
		}
		super.onPageStarted(view, url, favicon);
	}

	// ---- Yolda: reklam engelleme ----

	/** Reklam / izleme sunucuları: istek boş yanıtla kesilir. */
	private static final String[] AD_HOSTS = {
			"doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
			"googletagservices.com", "googletagmanager.com", "adservice.google.com", "imasdk.googleapis.com",
			"pagead2.googlesyndication.com", "static.doubleclick.net", "ad.doubleclick.net",
			"adnxs.com", "criteo.com", "taboola.com", "outbrain.com", "scorecardresearch.com",
			"amazon-adsystem.com", "moatads.com", "pubmatic.com", "rubiconproject.com", "smartadserver.com",
			"yandex.ru/ads", "an.yandex.ru", "mc.yandex.ru", "adform.net", "openx.net", "teads.tv"};

	/** YouTube içindeki reklam uçları (video akışına dokunmaz). */
	private static final String[] YT_AD_PATHS = {
			"/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info", "/youtubei/v1/log_event",
			"/api/stats/qoe?adformat", "/pcs/activeview", "/ad_data_204", "/generate_204?ad"};

	static boolean isAd(Uri u) {
		String host = u.getHost();
		if (host == null) return false;
		for (String h : AD_HOSTS) {
			if (h.contains("/")) {
				if ((host + u.getPath()).contains(h)) return true;
			} else if (host.equals(h) || host.endsWith("." + h)) return true;
		}
		if (host.endsWith("youtube.com")) {
			String pq = u.getEncodedPath() + ((u.getEncodedQuery() != null) ? "?" + u.getEncodedQuery() : "");
			for (String p : YT_AD_PATHS) if (pq.startsWith(p)) return true;
		}
		return false;
	}

	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
		if (isAd(request.getUrl())) {
			return new WebResourceResponse("text/plain", "utf-8", 204, "No Content",
					java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
		}
		return super.shouldInterceptRequest(view, request);
	}

	/**
	 * YouTube / YouTube Music sayfasında: video reklamını sessize alıp sonuna atlar, "Reklamı atla"ya basar,
	 * Premium / uygulamada aç açılır pencerelerini kapatır, sayfa içi reklam kutularını gizler.
	 * Bir kez kurulur; sayfa içinde dolaşırken çalışmaya devam eder.
	 */
	static final String AD_JS = """
			(function() {
			  if (window.__yoldaAds) return; window.__yoldaAds = 1;
			  var css = document.createElement('style');
			  css.textContent = 'ytmusic-mealbar-promo-renderer, ytmusic-statement-banner-renderer,'
			    + ' ytmusic-brand-video-singleton-renderer, ytd-ad-slot-renderer, ytd-promoted-sparkles-web-renderer,'
			    + ' ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, ad-slot-renderer,'
			    + ' ytm-mealbar-promo-renderer, ytm-statement-banner-renderer, #player-ads, .ytp-ad-overlay-container,'
			    + ' .ytp-ad-image-overlay, ytm-paid-content-overlay-renderer {display: none !important}';
			  (document.head || document.documentElement).appendChild(css);
			  setInterval(function() {
			    var v = document.querySelector('video');
			    var ad = document.querySelector('.ad-showing, .ad-interrupting');
			    if (v) {
			      if (ad) {
			        if (!v.muted) { v.muted = true; v.__yoldaMuted = 1; }
			        if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.3)
			          v.currentTime = v.duration;
			      } else if (v.__yoldaMuted) { v.muted = false; v.__yoldaMuted = 0; }
			    }
			    var s = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-skip-ad-button');
			    if (s) s.click();
			    var d = document.querySelector('ytmusic-mealbar-promo-renderer #dismiss-button, ytm-mealbar-promo-renderer button, tp-yt-paper-dialog .dismiss-button, ytmusic-you-there-renderer button');
			    if (d) d.click();
			  }, 700);
			})()""";

	@Override
	public void onPageFinished(WebView view, String url) {
		try {
			String h = Uri.parse(url).getHost();
			if ((h != null) && h.endsWith("youtube.com")) view.evaluateJavascript(AD_JS, null);
		} catch (Exception ignore) {
		}
		FermataWebView v = (FermataWebView) view;
		FutureSupplier<MainActivityDelegate> f =
				MainActivityDelegate.getActivityDelegate(v.getContext());
		f.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));

		if (loading != null) {
			loading.accept(false);
			loading = null;
		}

		super.onPageFinished(view, url);
		((FermataWebView) view).hideKeyboard();
		v.pageLoaded(url);
		f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));
	}

	@Override
	public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
		super.doUpdateVisitedHistory(view, url, isReload);
		((FermataWebView) view).urlChanged(url);
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
																					@NonNull WebResourceRequest request) {
		if (isYoutubeUri(request.getUrl())) {
			try {
				MainActivityDelegate a =
						MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;
				if (!(a.showFragment(me.aap.fermata.R.id.youtube_fragment) instanceof YoutubeFragment f))
					return false;
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		// Yolda: YouTube Music (music.youtube.com) tarayıcıda kalır; yoksa YouTube sekmesine taşınıp
		// onu ele geçiriyor, menüden YouTube'a geri dönülemiyordu.
		return ((host != null) && ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com")
				&& !host.endsWith("music.youtube.com")) ||
				host.equals("youtu.be")));
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
															@NonNull WebResourceErrorCompat error) {
		if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
			Log.e("Web error received: " + error.getDescription());
		} else {
			Log.e("Web error received");
		}

		super.onReceivedError(view, request, error);
	}
}
