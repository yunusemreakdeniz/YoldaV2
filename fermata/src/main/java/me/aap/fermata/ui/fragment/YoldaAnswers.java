package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.os.BatteryManager;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;

/**
 * Hey Yolda'nın bilgi yanıtları: tarih, şarj, hız, konum, yarınki / haftalık hava, hatırlatıcı ve
 * Vikipedi'den kısa tanım ("X nedir / kimdir"). Hepsi ücretsiz: Vikipedi ve Open-Meteo herkese açık.
 */
final class YoldaAnswers {
	static final Locale TR = new Locale("tr");
	private static final Random RND = new Random();

	private YoldaAnswers() {
	}

	static String chat(MainActivityDelegate a, String c, Consumer<YoldaIntents.Intent> then) {
		int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
		YoldaChat.Reply r = YoldaChat.answer(c, hour, RND);
		if (r == null) return "Anlayamadım";
		if (r.then() != null) App.get().getHandler().postDelayed(() -> then.accept(r.then()), 1200);
		return r.text();
	}

	static String date() {
		return "Bugün " + new SimpleDateFormat("d MMMM yyyy, EEEE", TR).format(new Date());
	}

	static String battery(Context ctx) {
		try {
			BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
			int p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
			boolean ch = bm.isCharging();
			if (p <= 0) return "Şarj durumu okunamadı";
			return "Şarj yüzde " + p + (ch ? ", şarj oluyor" : ((p < 20) ? ". Şarj azalmış, kabloyu takmanı öneririm" : ""));
		} catch (Throwable ex) {
			return "Şarj durumu okunamadı";
		}
	}

	static String speed() {
		if (!YoldaRadar.hasFix() || (YoldaRadar.speed < 0)) return "Hızını şu an bilmiyorum, konum bekleniyor";
		int s = YoldaRadar.speed, l = YoldaRadar.limit;
		String t = "Saatte " + s + " kilometre";
		if (l > 0) t += ", sınır " + l + ((s > l + 5) ? ". Biraz yavaşla" : "");
		return t;
	}

	// ---- konum ----

	static void whereAmI(Context ctx, Consumer<String> out) {
		App.get().getExecutor().submit(() -> {
			String r;
			try {
				double[] ll = YoldaRadar.hasFix() ? new double[]{YoldaRadar.lat, YoldaRadar.lon} : YoldaRadar.lastKnown(ctx);
				if (ll == null) r = "Konumunu bilmiyorum, konum izni ya da sinyal yok";
				else {
					android.location.Geocoder g = new android.location.Geocoder(ctx, new Locale("tr", "TR"));
					@SuppressWarnings("deprecation") List<android.location.Address> l = g.getFromLocation(ll[0], ll[1], 1);
					if ((l == null) || l.isEmpty()) r = "Adresi bulamadım";
					else {
						android.location.Address ad = l.get(0);
						List<String> p = new ArrayList<>();
						if (ad.getThoroughfare() != null) p.add(ad.getThoroughfare());
						if (ad.getSubLocality() != null) p.add(ad.getSubLocality());
						if (ad.getSubAdminArea() != null) p.add(ad.getSubAdminArea());
						if (ad.getAdminArea() != null) p.add(ad.getAdminArea());
						r = p.isEmpty() ? "Adresi bulamadım" : ("Şu an buradasın: " + String.join(", ", p));
					}
				}
			} catch (Throwable ex) {
				r = "Adresi bulamadım";
			}
			String fr = r;
			App.get().getHandler().post(() -> out.accept(fr));
		});
	}

	// ---- hava tahmini ----

	static void forecast(Context ctx, String what, Consumer<String> out) {
		YoldaWeather.detail(ctx, false).main().onCompletion((d, err) -> {
			if ((d == null) || d.days().isEmpty()) {
				out.accept("Hava tahmini alınamadı");
				return;
			}
			String place = ((d.place() != null) && !d.place().isEmpty()) ? (d.place() + ": ") : "";
			if ("yarın".equals(what) && (d.days().size() > 1)) {
				YoldaWeather.Day t = d.days().get(1);
				out.accept(place + "Yarın " + YoldaWeather.text(t.code()).toLowerCase(TR) + ", en yüksek " + t.max()
						+ ", en düşük " + t.min() + " derece" + ((t.pop() >= 30) ? (", yağış ihtimali yüzde " + t.pop()) : ""));
			} else if ("hafta".equals(what)) {
				StringBuilder b = new StringBuilder(place);
				int n = Math.min(5, d.days().size());
				for (int i = 0; i < n; i++) {
					YoldaWeather.Day t = d.days().get(i);
					if (i > 0) b.append("; ");
					b.append(dayName(t.date(), i)).append(' ').append(t.max()).append(" derece");
					if (t.pop() >= 40) b.append(", yağışlı");
				}
				out.accept(b.toString());
			} else {
				YoldaWeather.Day t = d.days().get(0);
				int pop = t.pop();
				out.accept(place + ((pop >= 50) ? ("Bugün yağış bekleniyor, ihtimal yüzde " + pop + ". Şemsiyeni al")
						: (pop >= 20) ? ("Bugün yağış ihtimali düşük, yüzde " + pop)
						: "Bugün yağış beklenmiyor"));
			}
		});
	}

	private static String dayName(String iso, int i) {
		if (i == 0) return "Bugün";
		if (i == 1) return "Yarın";
		try {
			return new SimpleDateFormat("EEEE", TR).format(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso));
		} catch (Throwable ex) {
			return iso;
		}
	}

	// ---- hatırlatıcı ----

	private static final List<Runnable> reminders = new ArrayList<>();

	static void remind(Context ctx, int min, String what) {
		Context app = ctx.getApplicationContext();
		String msg = ((what == null) || what.isEmpty()) ? (min + " dakika doldu") : ("Hatırlatma: " + what);
		Runnable r = new Runnable() {
			@Override
			public void run() {
				synchronized (reminders) {
					if (!reminders.remove(this)) return;
				}
				YoldaLog.add("Hatırlatıcı: " + msg);
				YoldaSpeak.wakeBeep();
				me.aap.fermata.ui.view.YoldaStyle.toast(app, "⏰ " + msg);
				App.get().getHandler().postDelayed(() -> YoldaSpeak.sayAlways(app, msg), 600);
			}
		};
		synchronized (reminders) {
			reminders.add(r);
		}
		App.get().getHandler().postDelayed(r, min * 60_000L);
	}

	static int cancelReminders() {
		synchronized (reminders) {
			int n = reminders.size();
			for (Runnable r : reminders) App.get().getHandler().removeCallbacks(r);
			reminders.clear();
			return n;
		}
	}

	// ---- Vikipedi ----

	/** "X nedir / kimdir": Türkçe Vikipedi'deki en uygun maddenin ilk iki cümlesi. */
	static void wiki(String q, Consumer<String> out) {
		App.get().getExecutor().submit(() -> {
			String r;
			try {
				String url = "https://tr.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1"
						+ "&generator=search&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&exsentences=2&gsrsearch="
						+ URLEncoder.encode(q, "UTF-8");
				r = wikiParse(get(url));
				if (r == null) r = "“" + q + "” hakkında bir bilgi bulamadım";
			} catch (Throwable ex) {
				r = "Bilgiye ulaşamadım, internet bağlantını kontrol et";
			}
			String fr = r;
			App.get().getHandler().post(() -> out.accept(fr));
		});
	}

	/** Vikipedi yanıtından kısa metin (parantez içleri atılır, en çok ~300 harf); yoksa null. */
	@Nullable
	static String wikiParse(String json) throws Exception {
		JSONObject o = new JSONObject(json);
		JSONObject qy = o.optJSONObject("query");
		if (qy == null) return null;
		var pages = qy.optJSONArray("pages");
		if ((pages == null) || (pages.length() == 0)) return null;
		String t = pages.getJSONObject(0).optString("extract", "").trim();
		if (t.isEmpty()) return null;
		String prev;
		do {
			prev = t;
			t = t.replaceAll("\\s*\\([^()]*\\)", "");
		} while (!t.equals(prev));
		t = t.replaceAll("\\s+", " ").trim();
		if (t.length() > 320) {
			int cut = t.lastIndexOf(". ", 320);
			t = (cut > 80) ? t.substring(0, cut + 1) : (t.substring(0, 320) + "…");
		}
		return t;
	}

	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(8000);
		c.setRequestProperty("User-Agent", "YoldaV2/2.0 (Android sesli asistan)");
		try (InputStream in = c.getInputStream()) {
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
			return b.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
	}
}
