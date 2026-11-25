package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Gemini doğal sesi (Mola'dan uyarlandı): Hey Yolda yanıtları isteğe bağlı olarak Gemini'nin konuşma modeliyle
 * okunur (telefonun robotik sesi yerine). Kullanıcının kendi Gemini anahtarıyla; uyarılar (radar) her zaman
 * telefonun sesiyle (hızlı olsun). Ses gelmezse ya da hata olursa telefonun sesine düşülür. Varsayılan kapalı.
 */
public final class YoldaGeminiVoice {
	static final String PREF = "gemini_voice";
	static final String[] MODELS = {"gemini-2.5-flash-preview-tts", "gemini-2.5-flash-tts"};
	static final String VOICE = "Kore";
	static final int RATE = 24000;
	private static volatile int modelIdx;

	private YoldaGeminiVoice() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	/** Açık mı (ayar açık ve Gemini anahtarı var). */
	public static boolean on(Context ctx) {
		return prefs(ctx).getBoolean(PREF, false) && YoldaGemini.isEnabled(ctx);
	}

	public static boolean setting(Context ctx) {
		return prefs(ctx).getBoolean(PREF, false);
	}

	public static void set(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean(PREF, on).apply();
	}

	/** İstek gövdesi: metin → ses (tek konuşmacı, Türkçe metin). */
	static String body(String text) throws Exception {
		return new JSONObject()
				.put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", text)))))
				.put("generationConfig", new JSONObject()
						.put("responseModalities", new JSONArray().put("AUDIO"))
						.put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
								.put("prebuiltVoiceConfig", new JSONObject().put("voiceName", VOICE)))))
				.toString();
	}

	/** Yanıttaki ses verisi (base64, 16 bit PCM, 24 kHz, mono); yoksa null. */
	@Nullable
	static String audioBase64(String json) throws Exception {
		JSONArray c = new JSONObject(json).optJSONArray("candidates");
		if ((c == null) || (c.length() == 0)) return null;
		JSONObject content = c.getJSONObject(0).optJSONObject("content");
		JSONArray parts = (content != null) ? content.optJSONArray("parts") : null;
		if (parts == null) return null;
		for (int i = 0; i < parts.length(); i++) {
			JSONObject in = parts.getJSONObject(i).optJSONObject("inlineData");
			if ((in != null) && !in.optString("data").isEmpty()) return in.optString("data");
		}
		return null;
	}

	/** Gemini sesiyle oku; olmazsa {@code fallback} (telefonun sesi). Arka planda çalışır. */
	public static void speak(Context ctx, String text, Runnable fallback) {
		Context app = ctx.getApplicationContext();
		String key = YoldaGemini.key(app);
		if (key == null) {
			fallback.run();
			return;
		}
		new Thread(() -> {
			byte[] pcm = null;
			try {
				String b = body(text);
				for (int mi = modelIdx; (mi < MODELS.length) && (pcm == null); mi++) {
					int[] code = new int[1];
					String resp = YoldaGemini.postTo("https://generativelanguage.googleapis.com/v1beta/models/" + MODELS[mi]
							+ ":generateContent", key, b, code, 9000);
					if (code[0] == 200) {
						String a = audioBase64(resp);
						if (a != null) {
							pcm = Base64.decode(a, Base64.DEFAULT);
							modelIdx = mi;
						}
					} else {
						YoldaLog.add("Gemini sesi " + MODELS[mi] + ": " + code[0]);
						if (code[0] != 404) break; // model yoksa sıradaki, diğer hatada telefonun sesi
					}
				}
			} catch (Throwable ex) {
				YoldaLog.add("Gemini sesi alınamadı: " + ex.getClass().getSimpleName());
			}
			if (pcm == null) {
				me.aap.utils.app.App.get().getHandler().post(fallback);
				return;
			}
			play(app, pcm);
		}, "gemini-voice").start();
	}

	private static void play(Context ctx, byte[] pcm) {
		AudioManager am = ctx.getSystemService(AudioManager.class);
		AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
		AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
				.setAudioAttributes(attrs).build();
		AudioTrack t = null;
		try {
			if (am != null) am.requestAudioFocus(focus); // çalan müzik kısılır
			t = new AudioTrack.Builder().setAudioAttributes(attrs)
					.setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
							.setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
					.setBufferSizeInBytes(pcm.length).setTransferMode(AudioTrack.MODE_STATIC).build();
			t.write(pcm, 0, pcm.length);
			t.play();
			long ms = pcm.length * 1000L / (RATE * 2L);
			Thread.sleep(ms + 200);
		} catch (Throwable ex) {
			YoldaLog.add("Gemini sesi çalınamadı: " + ex.getClass().getSimpleName());
		} finally {
			if (t != null) try {
				t.stop();
				t.release();
			} catch (Throwable ignore) {
			}
			if (am != null) am.abandonAudioFocusRequest(focus);
		}
	}
}
