package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

import me.aap.utils.app.App;

/**
 * Hey Yolda'nın Gemini bağlantısı: Yolda'nın kendi komutlarıyla çözülemeyen sorular, sohbet ve bilgi soruları
 * Google Gemini'ye sorulur. Kullanıcı kendi API anahtarını girer (Google AI Studio'dan ücretsiz alınır);
 * anahtar yalnız bu telefonda saklanır, günlüğe yazılmaz. Konuşmanın son birkaç adımı hatırlanır (ardışık sorular).
 */
public final class YoldaGemini {
	private static final String PREFS = "yolda_ai";
	private static final String KEY = "gemini_key";
	/** Önce en yeni hızlı model takma adı; bulunamazsa sabit sürüm. */
	private static final String[] MODELS = {"gemini-flash-latest", "gemini-2.5-flash"};
	private static final int MAX_TURNS = 6;
	private static final ArrayDeque<String[]> history = new ArrayDeque<>();
	private static volatile int modelIdx;

	private YoldaGemini() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static boolean isEnabled(Context ctx) {
		String k = prefs(ctx).getString(KEY, null);
		return (k != null) && !k.trim().isEmpty();
	}

	public static void setKey(Context ctx, @Nullable String key) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		if ((key == null) || key.trim().isEmpty()) e.remove(KEY);
		else e.putString(KEY, key.trim());
		e.apply();
		synchronized (history) {
			history.clear();
		}
	}

	/** Sesli asistanın verdiği anlık durum (araç / telefon, çalan, hız); yönergeye eklenir. */
	@Nullable
	public static volatile String state;
	/** Son soru zamanı: 10 dakika sessizlikten sonra konuşma geçmişi unutulur (eski konu karışmasın). */
	private static volatile long lastAskAt;

	/** Sesle okunacak hale getir: markdown, madde işareti, emoji ve fazla boşluk atılır. */
	public static String spoken(@Nullable String s) {
		if (s == null) return "";
		String t = s.replaceAll("[*_#`>~]+", " ").replaceAll("(?m)^\\s*[-•]\\s+", "")
				.replaceAll("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]", "").replaceAll("\\s+", " ").trim();
		return t;
	}

	/** Sistem yönergesi: sürücüye, Türkçe, kısa, sesle okunacak düz metin. */
	static String system(@Nullable String place) {
		String now = new SimpleDateFormat("d MMMM yyyy EEEE, HH:mm", new Locale("tr")).format(new Date());
		return "Sen Yolda adlı araç içi sesli asistansın. Kullanıcı araba kullanıyor olabilir. Her zaman Türkçe yanıt ver. "
				+ "Yanıtların sesli okunacak: en fazla 2-3 kısa cümle, madde işareti, emoji, başlık ya da markdown kullanma. "
				+ "Sohbete sıcak ve doğal katıl. Bilmediğin güncel bilgiler için tahmin yürütme, bilmediğini söyle. "
				+ "Sürüş güvenliğini öncele; dikkat dağıtacak uzun içerik verme. "
				+ "Uygulamayı sen yönetirsin. Kullanıcı bir iş isterse (müzik, kanal, yol tarifi, arama, ses, tema, kart, "
				+ "sürüş uyarısı, hatırlatma...) yanıtın YALNIZCA tek satır olsun: \"KOMUT: <komut>\". Komut aşağıdaki "
				+ "katalogdaki kalıplardan biri, köşeli parantezleri gerçek değerle doldur; en çok 3 komutu \" ; \" ile ayır. "
				+ "Doğal istekleri anlamına göre çevir: \"biraz rahatlatıcı bir şey aç\" → \"KOMUT: rahatlatıcı müzik çal\", "
				+ "\"beni en yakın eczaneye götür\" → \"KOMUT: en yakın nöbetçi eczane\", \"çok gürültülü\" → \"KOMUT: sesi kıs\". "
				+ "Soru ya da sohbetse komut yazma, normal yanıt ver.\nKatalog:\n" + YoldaActions.forPrompt()
				+ "Şu an: " + now + ((place != null) ? (". Kullanıcının bulunduğu yer: " + place) : "") + ". "
				+ ((state != null) ? state : "");
	}

	/** Soruyu sor; yanıt ana iş parçacığında {@code out}'a gelir (hata olursa null ve neden {@code err}'e). */
	public static void ask(Context ctx, String question, Consumer<String> out, Consumer<String> err) {
		Context app = ctx.getApplicationContext();
		String key = prefs(app).getString(KEY, null);
		if ((key == null) || key.trim().isEmpty()) {
			err.accept("Gemini anahtarı girilmemiş");
			return;
		}
		long now0 = System.currentTimeMillis();
		if (now0 - lastAskAt > 10 * 60_000L) synchronized (history) {
			history.clear();
		}
		lastAskAt = now0;
		App.get().getExecutor().submit(() -> {
			String answer = null, problem = null;
			try {
				String place = null; // konum adı (varsa) hava durumundan
				try {
					YoldaWeather.Now n = YoldaWeather.cachedNow();
					if ((n != null) && (n.place() != null)) place = n.place();
				} catch (Throwable ignore) {
				}
				JSONObject body = new JSONObject();
				body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system(place)))));
				JSONArray contents = new JSONArray();
				synchronized (history) {
					for (String[] t : history) {
						contents.put(turn("user", t[0]));
						contents.put(turn("model", t[1]));
					}
				}
				contents.put(turn("user", question));
				body.put("contents", contents);
				body.put("generationConfig", new JSONObject().put("maxOutputTokens", 400).put("temperature", 0.7));
				for (int attempt = 0; attempt < MODELS.length; attempt++) {
					int mi = Math.min(modelIdx, MODELS.length - 1);
					int[] code = new int[1];
					String resp = post("https://generativelanguage.googleapis.com/v1beta/models/" + MODELS[mi] + ":generateContent",
							key.trim(), body.toString(), code);
					if (code[0] == 404 && (mi + 1 < MODELS.length)) { // model adı yok: sonrakini dene
						modelIdx = mi + 1;
						continue;
					}
					if ((code[0] == 400) || (code[0] == 401) || (code[0] == 403)) {
						problem = "Gemini anahtarı geçersiz ya da yetkisiz";
						break;
					}
					if (code[0] == 429) {
						problem = "Gemini kullanım sınırı doldu, biraz sonra tekrar dene";
						break;
					}
					if (code[0] != 200) {
						problem = "Gemini yanıt vermedi (" + code[0] + ")";
						break;
					}
					answer = parse(resp);
					if (answer == null) problem = "Gemini boş yanıt verdi";
					break;
				}
			} catch (java.net.UnknownHostException | java.net.SocketTimeoutException ex) {
				problem = "İnternet bağlantısı yok";
			} catch (Throwable ex) {
				problem = "Gemini'ye ulaşılamadı";
				YoldaLog.add("Gemini hatası: " + ex.getClass().getSimpleName());
			}
			if (answer != null) {
				synchronized (history) {
					history.addLast(new String[]{question, answer});
					while (history.size() > MAX_TURNS) history.removeFirst();
				}
			}
			String fa = answer, fp = problem;
			App.get().getHandler().post(() -> {
				if (fa != null) out.accept(fa);
				else err.accept(fp);
			});
		});
	}

	private static JSONObject turn(String role, String text) throws Exception {
		return new JSONObject().put("role", role).put("parts", new JSONArray().put(new JSONObject().put("text", text)));
	}

	/** Yanıttan düz metin: parçalar birleştirilir, markdown işaretleri atılır. */
	@Nullable
	static String parse(String json) throws Exception {
		JSONObject o = new JSONObject(json);
		JSONArray c = o.optJSONArray("candidates");
		if ((c == null) || (c.length() == 0)) return null;
		JSONObject content = c.getJSONObject(0).optJSONObject("content");
		if (content == null) return null;
		JSONArray parts = content.optJSONArray("parts");
		if (parts == null) return null;
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < parts.length(); i++) {
			JSONObject p = parts.getJSONObject(i);
			if (p.optBoolean("thought", false)) continue;
			b.append(p.optString("text", ""));
		}
		String t = b.toString().replaceAll("[*_#`>]+", "").replaceAll("\\s+", " ").trim();
		return t.isEmpty() ? null : t;
	}

	private static String post(String url, String key, String body, int[] code) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(20000);
		c.setRequestMethod("POST");
		c.setDoOutput(true);
		c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		c.setRequestProperty("x-goog-api-key", key);
		try (OutputStream os = c.getOutputStream()) {
			os.write(body.getBytes(StandardCharsets.UTF_8));
		}
		code[0] = c.getResponseCode();
		try (InputStream in = (code[0] < 400) ? c.getInputStream() : c.getErrorStream()) {
			if (in == null) return "";
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
			return b.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
	}
}
