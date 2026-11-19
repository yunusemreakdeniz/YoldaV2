package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Hızlı mesaj (Hızlı mesaj kartı): hazır cevaplar tek dokunuşla. Son 30 dakikada gelen bir mesaj (WhatsApp,
 * Telegram, SMS) varsa ona yanıt olarak gider (bildirimin kendi yanıtlama yolu, ek izin yok); yoksa paylaşım
 * ekranı açılır, kişiyi kullanıcı seçer (otomatik SMS izni istenmez). "Varınca haber ver": yolculuk bitince
 * sesle hatırlatır ve mesajı hazır açar.
 */
public final class YoldaQuickMsg {
	private static final String PREFS = "yolda_quickmsg";
	static final String[] DEFAULTS = {"Geliyorum", "Yoldayım, birazdan oradayım", "Trafikteyim, biraz gecikeceğim",
			"Sürüyorum, sonra ararım", "Vardım"};

	private YoldaQuickMsg() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static List<String> presets(Context ctx) {
		String js = prefs(ctx).getString("presets", null);
		List<String> l = new ArrayList<>();
		if (js == null) {
			for (String s : DEFAULTS) l.add(s);
			return l;
		}
		try {
			JSONArray a = new JSONArray(js);
			for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
		} catch (Exception ignore) {
		}
		return l;
	}

	public static void savePresets(Context ctx, List<String> l) {
		JSONArray a = new JSONArray();
		for (String s : l) if ((s != null) && !s.trim().isEmpty()) a.put(s.trim());
		prefs(ctx).edit().putString("presets", a.toString()).apply();
	}

	/** Yanıtlanabilecek son mesaj (30 dakika içinde, yanıt yolu olan), yoksa null. */
	@Nullable
	static YoldaMessages.Msg replyTarget(@Nullable YoldaMessages.Msg m, long now) {
		if ((m == null) || (m.reply() == null)) return null;
		return (now - m.at() <= 30 * 60_000L) ? m : null;
	}

	/** Gönder: son mesaja yanıt (varsa) ya da paylaşım ekranı. @return kullanıcıya söylenecek sonuç */
	public static String send(Context ctx, String text) {
		YoldaMessages.Msg m = replyTarget(YoldaMessages.last, System.currentTimeMillis());
		if (m != null) return YoldaMessages.reply(ctx, text);
		try {
			Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
			ctx.startActivity(Intent.createChooser(i, "Kime gönderilsin?").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			return "Kime göndereceğini seç: " + text;
		} catch (Throwable ex) {
			return "Mesaj ekranı açılamadı";
		}
	}

	// ---- varınca haber ver ----

	/** Yolculuk bitince hatırlat (kime: serbest metin, ör. "Annem"; null = kapat). */
	public static void setArrivalNotice(Context ctx, @Nullable String who) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		if ((who == null) || who.trim().isEmpty()) e.remove("arrive");
		else e.putString("arrive", who.trim());
		e.apply();
	}

	@Nullable
	public static String arrivalNotice(Context ctx) {
		return prefs(ctx).getString("arrive", null);
	}

	/** Yolculuk bitti (sürüş kaydı yazıldı): istenmişse sesle hatırlat, "Vardım" mesajını hazır aç; bir kez. */
	static void onTripEnded(Context ctx) {
		String who = arrivalNotice(ctx);
		if (who == null) return;
		setArrivalNotice(ctx, null);
		String msg = who + " için vardığını haber vermeyi unutma";
		YoldaLog.add("Varınca haber ver: " + who);
		me.aap.fermata.ui.view.YoldaStyle.toast(ctx, "📨 " + msg);
		YoldaSpeak.sayAlways(ctx, msg);
		me.aap.utils.app.App.get().getHandler().postDelayed(() -> send(ctx, "Vardım"), 2500);
	}
}
