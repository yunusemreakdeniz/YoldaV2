package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sesli notlar ve hatırlatmalar (Notlar kartı): "not al: süt al" ile kaydedilen notlar ve "10 dakika sonra su
 * içmeyi hatırlat" ile kurulan hatırlatmalar telefonda saklanır (uygulama kapanıp açılsa da listelenir; vakti
 * geçmemiş hatırlatmalar açılışta yeniden kurulur). En yeni önce, en çok 50 not.
 */
public final class YoldaNotes {
	private static final String PREFS = "yolda_notes";
	static final int MAX = 50;

	public record Note(long at, String text) {
	}

	/** Hatırlatma: ne zaman (duvar saati) ve ne. */
	public record Reminder(long due, String text) {
	}

	private YoldaNotes() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	static List<Note> decodeNotes(@Nullable String s) {
		List<Note> out = new ArrayList<>();
		if (s == null) return out;
		try {
			JSONArray a = new JSONArray(s);
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				out.add(new Note(o.optLong("t"), o.optString("x")));
			}
		} catch (Exception ignore) {
		}
		return out;
	}

	static String encodeNotes(List<Note> l) {
		JSONArray a = new JSONArray();
		try {
			for (int i = 0; i < Math.min(MAX, l.size()); i++) a.put(new JSONObject().put("t", l.get(i).at()).put("x", l.get(i).text()));
		} catch (Exception ignore) {
		}
		return a.toString();
	}

	/** Not metnini temizle: "not al", "şunu not et" gibi baş ve son ekler atılır, ilk harf büyük. */
	static String cleanNote(String s) {
		String t = s.trim().replaceAll("(?i)^(şunu |bunu )?(not al|not et|not düş|kaydet)[:,]?\\s*", "")
				.replaceAll("(?i)\\s*(diye )?(not al|not et|not düş|kaydet)$", "").trim();
		if (t.isEmpty()) return t;
		return t.substring(0, 1).toUpperCase(new Locale("tr")) + t.substring(1);
	}

	public static synchronized List<Note> notes(Context ctx) {
		return decodeNotes(prefs(ctx).getString("notes", null));
	}

	public static synchronized void add(Context ctx, String text) {
		String t = cleanNote(text);
		if (t.isEmpty()) return;
		List<Note> l = notes(ctx);
		l.add(0, new Note(System.currentTimeMillis(), t));
		prefs(ctx).edit().putString("notes", encodeNotes(l)).apply();
	}

	public static synchronized void remove(Context ctx, long at) {
		List<Note> l = notes(ctx);
		l.removeIf(n -> n.at() == at);
		prefs(ctx).edit().putString("notes", encodeNotes(l)).apply();
	}

	public static synchronized void clear(Context ctx) {
		prefs(ctx).edit().remove("notes").apply();
	}

	/** Notları okunacak metin: "3 not: Süt al. Lastikleri kontrol et. …". */
	public static String spoken(List<Note> l, int n) {
		if (l.isEmpty()) return "Kayıtlı not yok";
		StringBuilder b = new StringBuilder(l.size() + " not: ");
		for (int i = 0; i < Math.min(n, l.size()); i++) b.append((i == 0) ? "" : ". ").append(l.get(i).text());
		return b.toString();
	}

	// ---- hatırlatmalar (kalıcı liste) ----

	static List<Reminder> decodeReminders(@Nullable String s) {
		List<Reminder> out = new ArrayList<>();
		if (s == null) return out;
		try {
			JSONArray a = new JSONArray(s);
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				out.add(new Reminder(o.optLong("d"), o.optString("x")));
			}
		} catch (Exception ignore) {
		}
		return out;
	}

	static String encodeReminders(List<Reminder> l) {
		JSONArray a = new JSONArray();
		try {
			for (Reminder r : l) a.put(new JSONObject().put("d", r.due()).put("x", r.text()));
		} catch (Exception ignore) {
		}
		return a.toString();
	}

	/** Vakti geçmemiş hatırlatmalar, yakından uzağa. */
	public static synchronized List<Reminder> reminders(Context ctx) {
		long now = System.currentTimeMillis();
		List<Reminder> l = decodeReminders(prefs(ctx).getString("rem", null));
		l.removeIf(r -> r.due() <= now);
		l.sort((x, y) -> Long.compare(x.due(), y.due()));
		return l;
	}

	static synchronized void addReminder(Context ctx, long due, String text) {
		List<Reminder> l = reminders(ctx);
		l.add(new Reminder(due, text));
		prefs(ctx).edit().putString("rem", encodeReminders(l)).apply();
	}

	static synchronized void removeReminder(Context ctx, long due, String text) {
		List<Reminder> l = reminders(ctx);
		l.removeIf(r -> (r.due() == due) && r.text().equals(text));
		prefs(ctx).edit().putString("rem", encodeReminders(l)).apply();
	}

	static synchronized void clearReminders(Context ctx) {
		prefs(ctx).edit().remove("rem").apply();
	}

	/** "14:30'da" / "yarın 08:00'de" benzeri kısa zaman: kalan dakika ya da saat. */
	public static String left(long due, long now) {
		long m = Math.max(0, (due - now + 59_999) / 60_000);
		return (m < 60) ? (m + " dk sonra") : ((m / 60) + " sa " + (m % 60) + " dk sonra");
	}
}
