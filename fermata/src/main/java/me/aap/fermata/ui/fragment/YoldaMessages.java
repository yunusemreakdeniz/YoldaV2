package me.aap.fermata.ui.fragment;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Mesajları araçta sesli okuma ve sesle yanıtlama (WhatsApp, WhatsApp Business, Telegram, SMS). Android'in
 * bildirim okuma izniyle çalışır (telefonda bir kez: Ayarlar → Bildirimlere erişim → Yolda V2). Araç bağlıyken
 * gelen mesaj "Ahmet'ten WhatsApp mesajı: …" diye okunur; "yanıtla: geliyorum" deyince yanıt mesaj
 * uygulamasının kendi yanıtlama yoluyla gönderilir. Mesaj metni günlüğe yazılmaz.
 */
public class YoldaMessages extends NotificationListenerService {
	private static final String PREF = "msg_read";

	/** Son okunan mesaj (yanıt ve "tekrar oku" için). */
	public record Msg(String app, String sender, String text, @Nullable Notification.Action reply, long at) {
	}

	@Nullable
	public static volatile Msg last;

	public static boolean isEnabled(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).getBoolean(PREF, true);
	}

	public static void setEnabled(Context ctx, boolean on) {
		ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply();
	}

	/** Bildirim okuma izni verilmiş mi. */
	public static boolean hasAccess(Context ctx) {
		return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.getPackageName());
	}

	public static Intent accessSettings() {
		return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	}

	/** Uygulamanın görünen adı (yoksa null: okunmaz). */
	@Nullable
	static String appName(Context ctx, String pkg) {
		switch (pkg) {
			case "com.whatsapp":
				return "WhatsApp";
			case "com.whatsapp.w4b":
				return "WhatsApp Business";
			case "org.telegram.messenger":
				return "Telegram";
			default:
				String sms = android.provider.Telephony.Sms.getDefaultSmsPackage(ctx);
				return pkg.equals(sms) ? "SMS" : null;
		}
	}

	/** Okunacak cümle. */
	static String spoken(String app, String sender, String text) {
		String t = (text.length() > 300) ? (text.substring(0, 300) + "…") : text;
		return app + " mesajı, " + sender + " yazdı: " + t; // isim eki yok: her adla doğru okunur
	}

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		try {
			Context ctx = getApplicationContext();
			String app = appName(ctx, sbn.getPackageName());
			if ((app == null) || !isEnabled(ctx)) return;
			Notification n = sbn.getNotification();
			if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return; // özet bildirimi: tek tek okunur
			Bundle ex = n.extras;
			CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
			CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
			if ((title == null) || (text == null) || (text.length() == 0)) return;
			String sender = title.toString().replaceAll(" \\(\\d+ mesaj\\)$", "").trim();
			Notification.Action reply = null;
			if (n.actions != null) for (Notification.Action a : n.actions) {
				if ((a.getRemoteInputs() != null) && (a.getRemoteInputs().length > 0)) {
					reply = a;
					break;
				}
			}
			Msg prev = last;
			if ((prev != null) && prev.sender().equals(sender) && prev.text().equals(text.toString())) return; // aynısı
			last = new Msg(app, sender, text.toString(), reply, System.currentTimeMillis());
			boolean car = false;
			try {
				car = YoldaCar.isConnected();
			} catch (Throwable ignore) {
			}
			YoldaLog.add("Mesaj geldi: " + app + (car ? " (araçta okunuyor)" : ""));
			if (car) YoldaSpeak.sayAlways(ctx, spoken(app, sender, text.toString()) + ". Yanıtlamak için Hey Yolda, yanıtla de");
		} catch (Throwable ex) {
			YoldaLog.add("Mesaj okunamadı: " + ex.getClass().getSimpleName());
		}
	}

	/** Son mesajı yeniden oku; yoksa false. */
	static boolean readLast(Context ctx) {
		Msg m = last;
		if (m == null) return false;
		YoldaSpeak.sayAlways(ctx, spoken(m.app(), m.sender(), m.text()));
		return true;
	}

	/** Son mesaja yanıt gönder. @return sonuç cümlesi */
	static String reply(Context ctx, String text) {
		Msg m = last;
		if (m == null) return "Yanıtlanacak mesaj yok";
		if (m.reply() == null) return m.app() + " bu mesaja yanıtlamaya izin vermiyor";
		try {
			RemoteInput[] ins = m.reply().getRemoteInputs();
			Intent i = new Intent();
			Bundle b = new Bundle();
			for (RemoteInput in : ins) b.putCharSequence(in.getResultKey(), text);
			RemoteInput.addResultsToIntent(ins, i, b);
			m.reply().actionIntent.send(ctx, 0, i);
			YoldaLog.add("Mesaj yanıtlandı: " + m.app());
			return "Gönderildi (" + m.sender() + "): " + text;
		} catch (PendingIntent.CanceledException ex) {
			return "Yanıt gönderilemedi (bildirim kapanmış)";
		} catch (Throwable ex) {
			return "Yanıt gönderilemedi";
		}
	}

	@Override
	public void onListenerConnected() {
		YoldaLog.add("Mesaj okuma: bildirim erişimi açık");
	}

	/** Testler / ayarlar için bileşen adı. */
	static ComponentName component(Context ctx) {
		return new ComponentName(ctx, YoldaMessages.class);
	}

	static String lower(String s) {
		return s.toLowerCase(new Locale("tr"));
	}
}
