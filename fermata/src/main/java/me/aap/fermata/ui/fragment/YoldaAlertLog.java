package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Uyarı geçmişi: verilen her sürüş uyarısı (radar, hız aşımı, ortalama hız, bölge, mola, yol tehlikesi) zaman,
 * tür ve konumla telefonda saklanır (son 300). Sürüş ekranında ve Yolculuklar özetinde listelenir.
 */
public final class YoldaAlertLog {
	private static final String PREFS = "yolda_alert_log";
	private static final String KEY = "log";
	static final int MAX = 300;

	/** Bir uyarı: zaman (duvar saati), tür harfi, metin, konum (bilinmiyorsa NaN). */
	public record Entry(long at, char kind, String text, double lat, double lon) {
		/** Saklama satırı: at|kind|lat|lon|text (metinde | yok sayılır). */
		String line() {
			return at + "|" + kind + "|" + (Double.isNaN(lat) ? "" : String.format(Locale.ROOT, "%.5f", lat)) + "|"
					+ (Double.isNaN(lon) ? "" : String.format(Locale.ROOT, "%.5f", lon)) + "|" + text.replace('|', '/').replace('\n', ' ');
		}

		static Entry parse(String s) {
			String[] p = s.split("\\|", 5);
			if (p.length < 5) return null;
			try {
				return new Entry(Long.parseLong(p[0]), p[1].isEmpty() ? '?' : p[1].charAt(0), p[4],
						p[2].isEmpty() ? Double.NaN : Double.parseDouble(p[2]), p[3].isEmpty() ? Double.NaN : Double.parseDouble(p[3]));
			} catch (NumberFormatException ex) {
				return null;
			}
		}

		/** Türün kısa adı (listede). */
		public String kindLabel() {
			return label(kind);
		}

		public boolean hasPlace() {
			return !Double.isNaN(lat) && !Double.isNaN(lon);
		}
	}

	private YoldaAlertLog() {
	}

	public static String label(char kind) {
		return switch (kind) {
			case 'r' -> "Radar";
			case 'o' -> "Hız aşımı";
			case 'a' -> "Ortalama hız";
			case 'z' -> "Okul / hastane";
			case 'f' -> "Mola";
			case 'h' -> "Yol tehlikesi";
			case 'w' -> "Hava";
			default -> "Uyarı";
		};
	}

	/** Metinden kayıt dizisi (en yeni önce). */
	static List<Entry> decode(String s) {
		List<Entry> out = new ArrayList<>();
		if ((s == null) || s.isEmpty()) return out;
		for (String l : s.split("\n")) {
			Entry e = Entry.parse(l);
			if (e != null) out.add(e);
		}
		return out;
	}

	/** Yeni kaydı öne ekle, en fazla MAX tut. */
	static String encode(List<Entry> l) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < Math.min(MAX, l.size()); i++) {
			if (i > 0) b.append('\n');
			b.append(l.get(i).line());
		}
		return b.toString();
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static synchronized void add(Context ctx, char kind, String text, double lat, double lon) {
		try {
			List<Entry> l = decode(prefs(ctx).getString(KEY, ""));
			l.add(0, new Entry(System.currentTimeMillis(), kind, text, lat, lon));
			prefs(ctx).edit().putString(KEY, encode(l)).apply();
		} catch (Throwable ignore) {
		}
	}

	/** Tüm kayıtlar, en yeni önce. */
	public static synchronized List<Entry> all(Context ctx) {
		return decode(prefs(ctx).getString(KEY, ""));
	}

	/** Bir zaman aralığındaki kayıtlar (yolculuk özeti için). */
	public static List<Entry> between(Context ctx, long from, long to) {
		return between(all(ctx), from, to);
	}

	static List<Entry> between(List<Entry> l, long from, long to) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : l) if ((e.at() >= from) && (e.at() <= to)) out.add(e);
		return out;
	}

	/** Türlere göre sayım: "3 radar · 1 hız aşımı". */
	public static String summary(List<Entry> l) {
		java.util.LinkedHashMap<Character, Integer> c = new java.util.LinkedHashMap<>();
		for (Entry e : l) c.merge(e.kind(), 1, Integer::sum);
		StringBuilder b = new StringBuilder();
		for (java.util.Map.Entry<Character, Integer> e : c.entrySet()) {
			if (b.length() > 0) b.append(" · ");
			b.append(e.getValue()).append(' ').append(label(e.getKey()).toLowerCase(new Locale("tr")));
		}
		return b.toString();
	}

	public static synchronized void clear(Context ctx) {
		prefs(ctx).edit().remove(KEY).apply();
	}
}
