package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

import me.aap.utils.app.App;
import me.aap.utils.log.Log;

/**
 * Hey Yolda'nın sesli yanıtı: komut anlaşılınca kısa bir Türkçe cümleyle söylenir (araçta ekrana bakmadan
 * ne olduğunu duyarsın). Konuşurken çalan müzik kısılır (ducking), bitince eski sesine döner.
 * Ayar: "Sesli yanıt" (Menü) ya da "sesli yanıtı kapat / aç" komutu.
 */
public final class YoldaSpeak {
	private static final String PREFS = "yolda_voice";
	private static final String PREF_REPLY = "voice_reply";
	private static TextToSpeech tts;
	private static boolean ready;
	private static String pending;
	private static AudioFocusRequest focus;
	/** Testler için: son söylenen (ya da söylenmesi istenen) cümle. */
	public static volatile String lastSpoken;

	private YoldaSpeak() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static boolean isEnabled(Context ctx) {
		return prefs(ctx).getBoolean(PREF_REPLY, true);
	}

	public static void setEnabled(Context ctx, boolean on) {
		prefs(ctx).edit().putBoolean(PREF_REPLY, on).apply();
	}

	/** Motoru önceden hazırla (ilk yanıt gecikmesin). */
	public static void warmUp(Context ctx) {
		if (isEnabled(ctx)) init(ctx.getApplicationContext());
	}

	/** Kısa yanıtı söyle (ayar kapalıysa yalnız kaydedilir). */
	public static void say(Context ctx, String text) {
		if ((text == null) || text.isEmpty()) return;
		lastSpoken = text;
		YoldaLog.add("Hey Yolda yanıtı: " + text);
		if (!isEnabled(ctx)) return;
		speakNow(ctx, text);
	}

	/** Uyarılar (radar): Hey Yolda yanıt ayarından bağımsız; kendi ayarı radar ekranında. */
	public static void sayAlways(Context ctx, String text) {
		if ((text == null) || text.isEmpty()) return;
		speakNow(ctx, text);
	}

	private static void speakNow(Context ctx, String text) {
		Context app = ctx.getApplicationContext();
		App.get().getHandler().post(() -> {
			try {
				init(app);
				if (!ready) {
					pending = text;
					return;
				}
				speak(app, text);
			} catch (Throwable ex) {
				Log.e(ex, "Sesli yanıt verilemedi");
			}
		});
	}

	private static void init(Context app) {
		if (tts != null) return;
		tts = new TextToSpeech(app, status -> {
			if (status != TextToSpeech.SUCCESS) {
				YoldaLog.add("Sesli yanıt: konuşma motoru açılamadı (" + status + ")");
				tts = null;
				return;
			}
			Locale tr = new Locale("tr", "TR");
			int r = tts.setLanguage(tr);
			if ((r == TextToSpeech.LANG_MISSING_DATA) || (r == TextToSpeech.LANG_NOT_SUPPORTED))
				YoldaLog.add("Sesli yanıt: Türkçe ses paketi yok, varsayılan dil kullanılıyor");
			tts.setSpeechRate(1.08f);
			tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
					.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
			tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
				@Override
				public void onStart(String id) {
				}

				@Override
				public void onDone(String id) {
					abandon(app);
				}

				@Override
				public void onError(String id) {
					abandon(app);
				}
			});
			ready = true;
			String p = pending;
			pending = null;
			if (p != null) speak(app, p);
		});
	}

	private static void speak(Context app, String text) {
		AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if (am != null) {
			focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
					.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
							.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
					.setOnAudioFocusChangeListener(f -> {
					}).build();
			am.requestAudioFocus(focus);
		}
		tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yolda-" + System.nanoTime());
	}

	private static void abandon(Context app) {
		AudioFocusRequest f = focus;
		focus = null;
		AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if ((am != null) && (f != null)) am.abandonAudioFocusRequest(f);
	}

	/** "Hey Yolda" duyulunca kısa bip: dinlemeye başladığını ekrana bakmadan anla. */
	public static void wakeBeep() {
		try {
			ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
			tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 160);
			App.get().getHandler().postDelayed(tg::release, 400);
		} catch (Throwable ex) {
			Log.d(ex, "Bip çalınamadı");
		}
	}
}
