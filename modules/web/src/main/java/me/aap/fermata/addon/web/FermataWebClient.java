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
			"yandex.ru/ads", "an.yandex.ru", "mc.yandex.ru", "adform.net", "openx.net", "teads.tv",
			// ek reklam ağları ve izleyiciler
			"adsrvr.org", "adroll.com", "advertising.com", "adcolony.com", "applovin.com", "appnexus.com",
			"bidswitch.net", "casalemedia.com", "contextweb.com", "demdex.net", "doubleverify.com",
			"everesttech.net", "exelator.com", "eyeota.net", "gumgum.com", "id5-sync.com", "indexww.com",
			"inmobi.com", "ipredictive.com", "krxd.net", "lijit.com", "mathtag.com", "media.net",
			"mgid.com", "mookie1.com", "nativeads.com", "omtrdc.net", "onetag-sys.com", "popads.net",
			"propellerads.com", "quantserve.com", "revcontent.com", "rlcdn.com", "sharethrough.com",
			"smaato.net", "spotxchange.com", "springserve.com", "tapad.com", "tremorhub.com",
			"unrulymedia.com", "yieldmo.com", "zemanta.com", "33across.com", "adhigh.net",
			"adtelligent.com", "admixer.net", "adocean.pl", "admatic.com.tr", "reklamstore.com",
			"reklamaction.com", "adnet.com.tr", "ads.yahoo.com", "analytics.yahoo.com",
			"app-measurement.com", "firebase-settings.crashlytics.com", "hotjar.com", "mixpanel.com",
			"segment.io", "branch.io", "adjust.com", "appsflyer.com", "facebook.net/tr",
			"connect.facebook.net", "ads.tiktok.com", "analytics.tiktok.com", "ads.linkedin.com"};

	/** Menüden açılıp kapatılır (varsayılan açık). */
	public static volatile boolean enabled = true;

	/** YouTube içindeki reklam uçları (video akışına dokunmaz). */
	private static final String[] YT_AD_PATHS = {
			"/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info", "/youtubei/v1/log_event",
			"/api/stats/qoe?adformat", "/pcs/activeview", "/ad_data_204", "/generate_204?ad"};

	static boolean isAd(Uri u) {
		if (!enabled) return false;
		return isAd(u.getHost(), u.getEncodedPath(), u.getEncodedQuery());
	}

	/** Adresin parçalarına göre reklam/izleme isteği mi (birim testlerde Android'siz çağrılır). */
	static boolean isAd(String host, String path, String query) {
		if (host == null) return false;
		host = host.toLowerCase(java.util.Locale.ROOT);
		if (path == null) path = "";
		for (String h : AD_HOSTS) {
			if (h.contains("/")) {
				if ((host + path).contains(h)) return true;
			} else if (host.equals(h) || host.endsWith("." + h)) return true;
		}
		if (host.endsWith("youtube.com")) {
			String pq = path + ((query != null) ? "?" + query : "");
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
	 * Belge başlamadan çalışan betik (YouTube, YouTube Music): oynatıcı yanıtındaki reklam yerleşimleri
	 * (adPlacements, playerAds, adSlots, adBreakHeartbeatParams) JSON.parse, fetch ve sayfaya gömülü
	 * ytInitialPlayerResponse üzerinden silinir; reklam hiç başlamaz.
	 */
	static final String PRUNE_JS = """
			(function() {
			  if (window.__yoldaPrune) return; window.__yoldaPrune = 1;
			  var KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adBreakParams'];
			  function prune(o) {
			    try {
			      if (!o || typeof o !== 'object') return o;
			      KEYS.forEach(function(k) { if (k in o) delete o[k]; });
			      if (o.playerResponse) prune(o.playerResponse);
			      if (o.response) prune(o.response);
			      if (Array.isArray(o)) o.forEach(prune);
			    } catch (e) {}
			    return o;
			  }
			  var parse = JSON.parse;
			  JSON.parse = function() { return prune(parse.apply(this, arguments)); };
			  var f = window.fetch;
			  if (f) window.fetch = function() {
			    return f.apply(this, arguments).then(function(r) {
			      try {
			        var u = r.url || '';
			        if (u.indexOf('/youtubei/v1/player') < 0 && u.indexOf('/youtubei/v1/next') < 0) return r;
			        return r.clone().text().then(function(t) {
			          try {
			            var j = prune(parse(t));
			            return new Response(JSON.stringify(j), {status: r.status, statusText: r.statusText, headers: r.headers});
			          } catch (e) { return r; }
			        });
			      } catch (e) { return r; }
			    });
			  };
			  ['ytInitialPlayerResponse', 'playerResponse'].forEach(function(n) {
			    var v;
			    try {
			      Object.defineProperty(window, n, {configurable: true,
			        get: function() { return v; }, set: function(x) { v = prune(x); }});
			    } catch (e) {}
			  });
			})()""";

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
			  var start = Date.now(), href = location.href;
			  // Kullanıcı sesi kendisi kapatırsa bir daha açmayalım
			  document.addEventListener('click', function(e) {
			    var b = e.target && e.target.closest && e.target.closest('.ytp-mute-button, .ytmMuteButton, [aria-label*="Sesi kapat"], [aria-label*="Mute"]');
			    if (b) window.__yoldaUserMute = 1;
			  }, true);
			  setInterval(function() {
			    if (location.href !== href) { href = location.href; start = Date.now(); window.__yoldaUserMute = 0; window.__yoldaUnmuted = 0; }
			    var v = document.querySelector('video');
			    var ad = document.querySelector('.ad-showing, .ad-interrupting');
			    if (v) {
			      // Reklam: sona atla (ses kısılmaz; kısılan ses bazen geri açılmıyordu)
			      if (ad && isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.3)
			        v.currentTime = v.duration;
			      // Mobil YouTube kendiliğinden başlayan videoyu sessiz oynatır ("sesi açmak için dokun"): sesi aç
			      // Yalnız izleme sayfasında (ana sayfadaki sessiz önizlemeler açılmasın)
			      var watch = /\\/watch|\\/shorts\\//.test(location.pathname);
			      if (watch && !window.__yoldaUnmuted && !v.paused && (v.muted || v.volume === 0) && !window.__yoldaUserMute && (Date.now() - start < 30000)) {
			        window.__yoldaUnmuted = 1;
			        v.muted = false;
			        if (v.volume === 0) v.volume = 1;
			        var u = document.querySelector('.ytp-unmute, .ytmUnmuteButton, ytm-unmute-button button, button.ytp-unmute-inner, .ytp-unmute-inner');
			        if (u) u.click();
			      }
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
			if (enabled && (h != null) && h.endsWith("youtube.com")) view.evaluateJavascript(AD_JS, null);
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
		try { // ekranı kapanmış sayfadan geç gelen bildirim uygulamayı çökertmesin
			((FermataWebView) view).hideKeyboard();
			v.pageLoaded(url);
		} catch (RuntimeException ex) {
			Log.e(ex, "Sayfa yüklenmesi işlenemedi");
			me.aap.fermata.ui.fragment.YoldaLog.add("Web: sayfa bildirimi yok sayıldı (" + ex + ")");
		}
		f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));
	}

	@Override
	public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
		super.doUpdateVisitedHistory(view, url, isReload);
		try { // ekranı kapanmış sayfadan geç gelen bildirim uygulamayı çökertmesin
			((FermataWebView) view).urlChanged(url);
		} catch (RuntimeException ex) {
			Log.e(ex, "Sayfa adresi işlenemedi");
			me.aap.fermata.ui.fragment.YoldaLog.add("Web: adres bildirimi yok sayıldı (" + ex + ")");
		}
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
																					@NonNull WebResourceRequest request) {
		if (isAppLink(request.getUrl())) return openAppLink(view, request);
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

	/** Web dışı adres (intent://, geo:, tel:, market:…): sayfada açılamaz, "Web sayfası mevcut değil" hatası çıkar. */
	static boolean isAppLink(Uri u) {
		String sc = u.getScheme();
		if (sc == null) return false;
		sc = sc.toLowerCase(java.util.Locale.ROOT);
		return !(sc.equals("http") || sc.equals("https") || sc.equals("about") || sc.equals("data")
				|| sc.equals("blob") || sc.equals("javascript") || sc.equals("file"));
	}

	/**
	 * Yolda: sayfalar (özellikle Google Haritalar) "uygulamada aç" için intent:// adresine gider; WebView bunu
	 * açamaz ve hata sayfası gösterirdi. Uygulama bağlantısının web karşılığı varsa (browser_fallback_url, https
	 * verisi, geo: / google.navigation: haritası) o açılır; yoksa geçiş yok sayılır, sayfa olduğu yerde kalır.
	 */
	private static String lastAppTarget;
	private static long lastAppTargetAt;

	static boolean openAppLink(WebView view, WebResourceRequest request) {
		Uri u = request.getUrl();
		String target = webTarget(u);
		String cur = view.getUrl();
		long now = android.os.SystemClock.elapsedRealtime();
		// Aynı web karşılığına 15 sn içinde yeniden yönlendiriliyorsa döngüdür: sayfada kalınır
		boolean loop = (target != null) && target.equals(lastAppTarget) && (now - lastAppTargetAt < 15_000);
		boolean open = (target != null) && request.isForMainFrame() && !target.equals(cur) && !loop;
		me.aap.fermata.ui.fragment.YoldaLog.add("Web: uygulama bağlantısı (" + u.getScheme() + ") "
				+ (open ? "web karşılığı açıldı" : (loop ? "döngü, sayfada kalındı" : "yok sayıldı, sayfada kalındı")));
		if (open) {
			lastAppTarget = target;
			lastAppTargetAt = now;
			view.loadUrl(target);
		}
		return true;
	}

	/** Uygulama bağlantısının web adresi (yoksa null). */
	@androidx.annotation.Nullable
	public static String webTarget(Uri u) {
		String sc = u.getScheme();
		if ("intent".equalsIgnoreCase(sc)) {
			try {
				android.content.Intent i = android.content.Intent.parseUri(u.toString(), android.content.Intent.URI_INTENT_SCHEME);
				String fb = i.getStringExtra("browser_fallback_url");
				if ((fb != null) && fb.startsWith("http")) return fb;
				String d = i.getDataString();
				if ((d != null) && d.startsWith("http")) return d;
				if (i.getData() != null) return me.aap.fermata.ui.fragment.NavFragment.webMapUrl(i);
			} catch (Exception ex) {
				Log.d(ex, "intent adresi çözülemedi");
			}
			return null;
		}
		try {
			return me.aap.fermata.ui.fragment.NavFragment.webMapUrl(new android.content.Intent(android.content.Intent.ACTION_VIEW, u));
		} catch (Exception ex) {
			return null;
		}
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		// Yolda: YouTube Music (music.youtube.com) tarayıcıda kalır; yoksa YouTube sekmesine taşınıp
		// onu ele geçiriyor, menüden YouTube'a geri dönülemiyordu.
		return ((host != null) && ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com")
				&& !host.endsWith("music.youtube.com")) ||
				host.equals("youtu.be")));
	}

	/**
	 * Yolda: web sayfasını çalıştıran işlem kapandı (çoğunlukla bellek azaldığı için Android kapattı).
	 * Ele alınmazsa Android uygulamanın tamamını kapatır ("Yolda durduruldu"); burada sayfa sessizce
	 * yeniden kurulur.
	 */
	@Override
	public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
		boolean crash = (android.os.Build.VERSION.SDK_INT >= 26) && (detail != null) && detail.didCrash();
		me.aap.fermata.ui.fragment.YoldaLog.add("Web sayfası işlemi kapandı (" + (crash ? "çöktü" : "bellek için kapatıldı")
				+ "); sayfa yeniden kuruluyor");
		try {
			MainActivityDelegate.get(view.getContext()).recoverDeadWebView(view);
		} catch (Throwable ex) {
			Log.e(ex, "Web sayfası yeniden kurulamadı");
		}
		return true;
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
