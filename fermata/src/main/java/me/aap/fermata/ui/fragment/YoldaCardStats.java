package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Kart yüzlerinin küçük hesapları (park süresi, haftalık sürüş, döviz günlük değişimi, namaz uyarısı) ve haftalık
 * çubuk çizimi. Hesaplar saf yöntemler: birim testinde denenir.
 */
public final class YoldaCardStats {
	private YoldaCardStats() {
	}

	/** Uyku zamanlayıcısının sıradaki değeri (dk): kapalı → 15 → 30 → 60 → kapalı (0). */
	public static int nextSleep(int leftMin) {
		if (leftMin <= 0) return 15;
		if (leftMin <= 15) return 30;
		if (leftMin <= 30) return 60;
		return 0;
	}

	/** "1 sa 25 dk" / "25 dk" / "2 gün 3 sa". */
	public static String elapsed(long ms) {
		long m = Math.max(0, ms / 60_000L);
		if (m < 60) return m + " dk";
		long h = m / 60;
		if (h < 24) return h + " sa " + (m % 60) + " dk";
		return (h / 24) + " gün " + (h % 24) + " sa";
	}

	/** Yürüyerek dakika (saatte ~4,8 km), en az 1. */
	public static int walkMin(double meters) {
		return (int) Math.max(1, Math.ceil(meters / 80.0)); // yukarı yuvarla: süreyi az gösterme
	}

	/** Bu haftanın (pazartesiden) sürüşü: gün gün metre, toplam metre, yolculuk sayısı, süre ve bugünün sırası. */
	public record Week(int[] dayMeters, int meters, int count, long ms, int today) {
	}

	public static Week week(List<YoldaDriveCore.TripRec> l, long nowMs) {
		Calendar c = Calendar.getInstance();
		c.setFirstDayOfWeek(Calendar.MONDAY);
		c.setTimeInMillis(nowMs);
		int today = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // pazartesi 0 … pazar 6
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.DAY_OF_MONTH, -today);
		long start = c.getTimeInMillis();
		int[] d = new int[7];
		int meters = 0, n = 0;
		long ms = 0;
		for (YoldaDriveCore.TripRec r : l) {
			if ((r.startWall() < start) || (r.startWall() > nowMs)) continue;
			Calendar t = Calendar.getInstance();
			t.setTimeInMillis(r.startWall());
			int i = (t.get(Calendar.DAY_OF_WEEK) + 5) % 7;
			d[i] += r.meters();
			meters += r.meters();
			ms += r.durationMs();
			n++;
		}
		return new Week(d, meters, n, ms, today);
	}

	/** Ortalama hız (km/s), süre yoksa 0. */
	public static int avgKmh(Week w) {
		return (w.ms() <= 0) ? 0 : (int) Math.round(w.meters() / 1000.0 / (w.ms() / 3_600_000.0));
	}

	/**
	 * Döviz günlük değişimi için saklanan kayıt "gün|dünkü son|bugünkü son". Gün değişince önceki günün son değeri
	 * "dünkü" olur. İlk gün dünkü bilinmez (0).
	 */
	public static String fxRoll(String stored, String today, double sell) {
		String[] f = (stored == null) ? new String[0] : stored.split("\\|");
		if ((f.length == 3) && f[0].equals(today)) return today + "|" + f[1] + "|" + sell;
		String base = (f.length == 3) ? f[2] : "0"; // dünkü son değer
		return today + "|" + base + "|" + sell;
	}

	/** Dünden yüzde değişim; dünkü bilinmiyorsa NaN. */
	public static double fxChange(String stored, double sell) {
		String[] f = (stored == null) ? new String[0] : stored.split("\\|");
		if (f.length != 3) return Double.NaN;
		try {
			double b = Double.parseDouble(f[1]);
			return (b <= 0) ? Double.NaN : (sell - b) / b * 100.0;
		} catch (NumberFormatException ex) {
			return Double.NaN;
		}
	}

	/** "▲ %0,3" / "▼ %0,2" / "= %0,0"; bilinmiyorsa boş. */
	public static String fxText(double pct) {
		if (Double.isNaN(pct)) return "";
		String a = (pct > 0.05) ? "▲" : (pct < -0.05) ? "▼" : "=";
		return a + String.format(new Locale("tr"), " %%%.1f", Math.abs(pct));
	}

	/** Yükselen kırmızı, düşen yeşil, değişmeyen soluk. */
	public static int fxColor(double pct) {
		if (Double.isNaN(pct) || (Math.abs(pct) <= 0.05)) return 0xCCFFFFFF;
		return (pct > 0) ? 0xFFF87171 : 0xFF34D399;
	}

	/** Namaz uyarısı zamanı mı: sıradaki vakte 10 dk (ya da 9 dk: dakikalık yoklama kaçırmasın) kaldı. */
	public static boolean prayerWarnNow(int minutesLeft) {
		return (minutesLeft == 10) || (minutesLeft == 9);
	}

	/** Kalan dakika (gece yarısını geçerse ertesi gün). */
	public static int minutesLeft(int targetMinute, int nowMinute) {
		int d = targetMinute - nowMinute;
		return (d < 0) ? (d + 24 * 60) : d;
	}

	/** Haftalık çubuklar: pazartesi → pazar, bugün vurgulu, boş gün ince çizgi. */
	public static View weekBars(Context ctx, int[] dayMeters, int today, int accent) {
		float dens = ctx.getResources().getDisplayMetrics().density;
		return new View(ctx) {
			final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
			final RectF r = new RectF();

			@Override
			protected void onDraw(Canvas c) {
				int max = 1;
				for (int v : dayMeters) max = Math.max(max, v);
				float w = getWidth(), h = getHeight(), gap = 6 * dens, bw = (w - 6 * gap) / 7f;
				for (int i = 0; i < 7; i++) {
					float bh = Math.max(3 * dens, (h - 2 * dens) * dayMeters[i] / (float) max);
					float x = i * (bw + gap);
					r.set(x, h - bh, x + bw, h);
					p.setColor((i == today) ? accent : ((dayMeters[i] > 0) ? 0x99FFFFFF : 0x44FFFFFF));
					c.drawRoundRect(r, 4 * dens, 4 * dens, p);
				}
			}
		};
	}
}
