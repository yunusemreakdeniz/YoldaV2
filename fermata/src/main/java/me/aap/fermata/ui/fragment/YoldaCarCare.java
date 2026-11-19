package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Locale;

/**
 * Araç bakım defteri (Araç bakımı kartı): muayene, trafik sigortası, kasko, yağ değişimi tarihleri. Kaç gün
 * kaldığı kartta; 7 gün kala (günde bir kez) sesli ve yazılı hatırlatır. Tarihler yalnız telefonda.
 */
public final class YoldaCarCare {
	private static final String PREFS = "yolda_carcare";
	/** {anahtar, görünen ad}. */
	static final String[][] ITEMS = {{"muayene", "Muayene"}, {"sigorta", "Trafik sigortası"}, {"kasko", "Kasko"},
			{"yag", "Yağ değişimi"}};

	private YoldaCarCare() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	/** "15.03.2027" / "15/3/2027" / "2027-03-15" → gün başlangıcı (ms), anlaşılmazsa -1. */
	static long parseDate(String s) {
		if (s == null) return -1;
		String t = s.trim();
		int d, m, y;
		try {
			if (t.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
				String[] p = t.split("-");
				y = Integer.parseInt(p[0]);
				m = Integer.parseInt(p[1]);
				d = Integer.parseInt(p[2]);
			} else if (t.matches("\\d{1,2}[./ -]\\d{1,2}[./ -]\\d{2,4}")) {
				String[] p = t.split("[./ -]");
				d = Integer.parseInt(p[0]);
				m = Integer.parseInt(p[1]);
				y = Integer.parseInt(p[2]);
				if (y < 100) y += 2000;
			} else {
				return -1;
			}
		} catch (NumberFormatException ex) {
			return -1;
		}
		if ((m < 1) || (m > 12) || (d < 1) || (d > 31)) return -1;
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(y, m - 1, d);
		return c.getTimeInMillis();
	}

	/** Bugünden o güne kalan gün (geçtiyse eksi). */
	static int daysLeft(long due, long now) {
		Calendar a = Calendar.getInstance();
		a.setTimeInMillis(now);
		a.set(Calendar.HOUR_OF_DAY, 0);
		a.set(Calendar.MINUTE, 0);
		a.set(Calendar.SECOND, 0);
		a.set(Calendar.MILLISECOND, 0);
		return (int) Math.round((due - a.getTimeInMillis()) / 86_400_000.0);
	}

	/** "42 gün", "yarın", "bugün", "3 gün geçti". */
	public static String left(int d) {
		if (d == 0) return "bugün";
		if (d == 1) return "yarın";
		if (d < 0) return (-d) + " gün geçti";
		return d + " gün";
	}

	public static long due(Context ctx, String key) {
		return prefs(ctx).getLong(key, -1);
	}

	public static void setDue(Context ctx, String key, long due) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		if (due < 0) e.remove(key);
		else e.putLong(key, due);
		e.apply();
	}

	/** En yakın tarihli kalem: {ad, kalan gün} ya da null. */
	@Nullable
	public static Object[] nearest(Context ctx, long now) {
		Object[] best = null;
		for (String[] it : ITEMS) {
			long d = due(ctx, it[0]);
			if (d < 0) continue;
			int left = daysLeft(d, now);
			if ((best == null) || (left < (int) best[1])) best = new Object[]{it[1], left};
		}
		return best;
	}

	/** Açılışta (günde bir kez): 7 gün ya da daha az kalan kalemleri hatırlat. */
	public static void remindIfDue(Context ctx) {
		SharedPreferences p = prefs(ctx);
		Calendar c = Calendar.getInstance();
		String day = c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
		if (day.equals(p.getString("reminded", ""))) return;
		StringBuilder b = new StringBuilder();
		long now = System.currentTimeMillis();
		for (String[] it : ITEMS) {
			long d = due(ctx, it[0]);
			if (d < 0) continue;
			int left = daysLeft(d, now);
			if (left <= 7) b.append((b.length() > 0) ? ", " : "").append(it[1]).append(' ').append(left(left));
		}
		if (b.length() == 0) return;
		p.edit().putString("reminded", day).apply();
		String msg = "Araç bakımı: " + b;
		YoldaLog.add(msg);
		me.aap.fermata.ui.view.YoldaStyle.toast(ctx, "🔧 " + msg);
		YoldaSpeak.sayAlways(ctx, msg);
	}

	public static String format(long due) {
		return (due < 0) ? "tarih yok" : new java.text.SimpleDateFormat("d MMMM yyyy", new Locale("tr")).format(new java.util.Date(due));
	}
}
