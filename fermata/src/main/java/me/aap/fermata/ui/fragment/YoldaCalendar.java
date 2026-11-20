package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Takvim kartı: telefonun takviminden bugünkü ve yarınki etkinlikler (yalnız okuma izni; hiçbir şey yazılmaz,
 * dışarı gönderilmez). Konumu olan etkinliğe dokununca oraya yol tarifi.
 */
public final class YoldaCalendar {
	/** Etkinlik: başlangıç ve bitiş (duvar saati), başlık, konum (boş olabilir), tüm gün mü. */
	public record Event(long begin, long end, String title, String location, boolean allDay) {
	}

	private YoldaCalendar() {
	}

	public static boolean hasPermission(Context ctx) {
		return ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
	}

	private static volatile Context app;
	/** Kart yüzü için: takvim sorgusu ana iş parçacığında değil, arka planda (1 dk geçerli). */
	public static final YoldaBgCache<List<Event>> CACHE = new YoldaBgCache<>(60_000L,
			() -> (app != null) ? upcoming(app) : new ArrayList<>());

	/** Bellekteki son etkinlikler (yoksa boş); eskiyse arka planda yenilenir, bitince CACHE.onLoaded. */
	public static List<Event> upcomingCached(Context ctx) {
		app = ctx.getApplicationContext();
		return CACHE.get(new ArrayList<>());
	}

	/** Şimdiden itibaren 36 saat içindeki etkinlikler (bitmemiş olanlar), başlangıca göre. İzin yoksa boş. */
	public static List<Event> upcoming(Context ctx) {
		List<Event> out = new ArrayList<>();
		if (!hasPermission(ctx)) return out;
		long now = System.currentTimeMillis();
		Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
		ContentUris.appendId(b, now - 12 * 3600_000L);
		ContentUris.appendId(b, now + 36 * 3600_000L);
		String[] cols = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.TITLE,
				CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY};
		try (Cursor c = ctx.getContentResolver().query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
			if (c == null) return out;
			while (c.moveToNext() && (out.size() < 20)) {
				long be = c.getLong(0), en = c.getLong(1);
				if (en <= now) continue; // bitmiş
				String t = c.getString(2);
				out.add(new Event(be, en, (t == null) ? "Etkinlik" : t, (c.getString(3) == null) ? "" : c.getString(3), c.getInt(4) == 1));
			}
		} catch (Throwable ex) {
			YoldaLog.add("Takvim okunamadı: " + ex.getClass().getSimpleName());
		}
		return out;
	}

	/** "Şimdi", "14:30", "Yarın 09:00", "Tüm gün". */
	public static String when(Event e, long now) {
		if (e.allDay()) return "Tüm gün";
		if ((e.begin() <= now) && (e.end() > now)) return "Şimdi";
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(now);
		int today = c.get(Calendar.DAY_OF_YEAR);
		c.setTimeInMillis(e.begin());
		String hm = new SimpleDateFormat("HH:mm", new Locale("tr")).format(new Date(e.begin()));
		return (c.get(Calendar.DAY_OF_YEAR) == today) ? hm : ("Yarın " + hm);
	}

	/** Sesle: "Bugün 2 etkinlik. 14:30 Toplantı, Kızılay. …". */
	public static String spoken(List<Event> l, long now) {
		if (l.isEmpty()) return "Yakında bir etkinlik yok";
		StringBuilder b = new StringBuilder(l.size() + " etkinlik. ");
		for (int i = 0; i < Math.min(4, l.size()); i++) {
			Event e = l.get(i);
			b.append((i == 0) ? "" : ". ").append(when(e, now)).append(", ").append(e.title());
			if (!e.location().isEmpty()) b.append(", ").append(e.location());
		}
		return b.toString();
	}
}
