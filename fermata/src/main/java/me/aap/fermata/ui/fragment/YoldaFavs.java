package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.radio.YoldaStreamItem;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.media.yt.YtMusicItem;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Yolda favorileri: TV kanalı, radyo (listeden ya da internetten), YouTube kanalı, şarkı, müzik araması ve web
 * sayfası bir arada. "Favoriler" kartında görünür; dokununca çalar ya da açılır. Kayıt kartlardakiyle aynı
 * biçimde ({@link YoldaDeck.Card}); en yeni başta.
 */
public final class YoldaFavs {
	/** Şarkı: data = parçanın JSON'u (adres, ad, sanatçı, kapak, süre). */
	public static final String SONG = "song";
	/** Müzik araması / sanatçı: data = arama sözcüğü. */
	public static final String MUSIC_QUERY = "musicq";

	private static final String PREF = "yolda_favs";
	private static final String PREF_IMPORTED = "yolda_favs_imported";
	private static final char FIELD = '\u001F';
	private static final char RECORD = '\u001E';
	private static final int MAX = 60;

	private YoldaFavs() {
	}

	public static List<YoldaDeck.Card> load(Context ctx) {
		return decode(prefs(ctx).getString(PREF, ""));
	}

	static List<YoldaDeck.Card> decode(@Nullable String s) {
		List<YoldaDeck.Card> l = new ArrayList<>();
		if ((s == null) || s.isEmpty()) return l;
		for (String rec : s.split(String.valueOf(RECORD))) {
			String[] f = rec.split(String.valueOf(FIELD), -1);
			if (f.length < 4) continue;
			l.add(new YoldaDeck.Card(f[0], f[1], f[2], f[3], ((f.length > 4) && !f[4].isEmpty()) ? f[4] : null));
		}
		return l;
	}

	static String encode(List<YoldaDeck.Card> l) {
		StringBuilder b = new StringBuilder();
		for (YoldaDeck.Card c : l) {
			if (b.length() > 0) b.append(RECORD);
			b.append(clean(c.key())).append(FIELD).append(clean(c.type())).append(FIELD).append(clean(c.title()))
					.append(FIELD).append(clean(c.data())).append(FIELD).append((c.logo() != null) ? clean(c.logo()) : "");
		}
		return b.toString();
	}

	private static String clean(String s) {
		return (s == null) ? "" : s.replace(FIELD, ' ').replace(RECORD, ' ');
	}

	private static void save(Context ctx, List<YoldaDeck.Card> l) {
		prefs(ctx).edit().putString(PREF, encode(l)).apply();
	}

	public static boolean contains(Context ctx, String type, String data) {
		for (YoldaDeck.Card c : load(ctx)) if (c.type().equals(type) && c.data().equals(data)) return true;
		return false;
	}

	/** Ekler (zaten varsa başa alır). @return true yeni eklendiyse. */
	public static boolean add(Context ctx, String type, String title, String data, @Nullable String logo) {
		List<YoldaDeck.Card> l = load(ctx);
		boolean had = l.removeIf(c -> c.type().equals(type) && c.data().equals(data));
		String key = type + ":" + Integer.toHexString((type + data).hashCode());
		l.add(0, new YoldaDeck.Card(key, type, title, data, logo));
		while (l.size() > MAX) l.remove(l.size() - 1);
		save(ctx, l);
		YoldaLog.add("Favorilere eklendi: " + title);
		return !had;
	}

	public static boolean remove(Context ctx, String key) {
		List<YoldaDeck.Card> l = load(ctx);
		boolean r = l.removeIf(c -> c.key().equals(key));
		if (r) save(ctx, l);
		return r;
	}

	/** Sırada bir öne (dir = -1) ya da arkaya (+1) taşır. @return yeni konum (taşınamadıysa eskisi). */
	public static int move(Context ctx, String key, int dir) {
		List<YoldaDeck.Card> l = load(ctx);
		int i = -1;
		for (int k = 0; k < l.size(); k++) if (l.get(k).key().equals(key)) i = k;
		int j = i + dir;
		if ((i < 0) || (j < 0) || (j >= l.size())) return i;
		java.util.Collections.swap(l, i, j);
		save(ctx, l);
		return j;
	}

	/** Görünen adı değiştirir. */
	public static void rename(Context ctx, String key, String title) {
		String t = clean(title).trim();
		if (t.isEmpty()) return;
		List<YoldaDeck.Card> l = load(ctx);
		for (int k = 0; k < l.size(); k++) {
			YoldaDeck.Card c = l.get(k);
			if (c.key().equals(key)) l.set(k, new YoldaDeck.Card(c.key(), c.type(), t, c.data(), c.logo()));
		}
		save(ctx, l);
	}

	public static boolean remove(Context ctx, String type, String data) {
		List<YoldaDeck.Card> l = load(ctx);
		boolean r = l.removeIf(c -> c.type().equals(type) && c.data().equals(data));
		if (r) save(ctx, l);
		return r;
	}

	// ---- çalan öğe ----

	/** Çalan öğenin favori karşılığı: {tür, ad, veri, logo}; desteklenmiyorsa null. */
	@Nullable
	public static String[] entryOf(@Nullable MediaLib.PlayableItem pi) {
		if (pi == null) return null;
		if (pi instanceof YtMusicItem m) {
			YtMusic.Track t = m.getTrack();
			return new String[]{SONG, t.title() + ((t.artist() != null) ? " · " + t.artist() : ""), songData(t), t.thumb()};
		}
		if (pi instanceof YoldaStreamItem s) {
			return new String[]{YoldaDeck.RADIO_STREAM, s.getName(), s.getStreamUrl(), s.getLogo()};
		}
		MediaLib.BrowsableItem p = pi.getParent();
		boolean radio = !pi.isVideo() || ((p != null) && "Radyo".equals(p.getName()));
		String id = pi.getId();
		if ((id == null) || id.startsWith("yt") || id.contains("youtube")) return null;
		return new String[]{radio ? YoldaDeck.RADIO_CHANNEL : YoldaDeck.TV_CHANNEL, pi.getName(), id, null};
	}

	public static boolean isFavorite(Context ctx, @Nullable MediaLib.PlayableItem pi) {
		String[] e = entryOf(pi);
		return (e != null) && contains(ctx, e[0], e[2]);
	}

	/** Çalanı favorilere ekler ya da çıkarır. @return yeni durum (favori mi); desteklenmiyorsa null. */
	@Nullable
	public static Boolean toggle(Context ctx, @Nullable MediaLib.PlayableItem pi) {
		String[] e = entryOf(pi);
		if (e == null) return null;
		if (contains(ctx, e[0], e[2])) {
			remove(ctx, e[0], e[2]);
			return false;
		}
		add(ctx, e[0], e[1], e[2], e[3]);
		return true;
	}

	public static String songData(YtMusic.Track t) {
		try {
			JSONObject o = new JSONObject();
			o.put("u", t.url());
			o.put("t", t.title());
			if (t.artist() != null) o.put("a", t.artist());
			if (t.thumb() != null) o.put("i", t.thumb());
			o.put("d", t.durationSec());
			return o.toString();
		} catch (Exception ex) {
			return t.url();
		}
	}

	@Nullable
	static YtMusic.Track songOf(@NonNull String data) {
		try {
			JSONObject o = new JSONObject(data);
			return new YtMusic.Track(o.getString("u"), o.getString("t"), o.optString("a", null), o.optString("i", null),
					o.optLong("d", 0));
		} catch (Exception ex) {
			return null;
		}
	}

	// ---- eski Fermata favorileri bir kez aktarılır ----

	static boolean imported(Context ctx) {
		return prefs(ctx).getBoolean(PREF_IMPORTED, false);
	}

	static void importLib(Context ctx, List<MediaLib.PlayableItem> items) {
		if (imported(ctx)) return;
		List<YoldaDeck.Card> l = load(ctx);
		for (MediaLib.PlayableItem pi : items) {
			String[] e = entryOf(pi);
			if (e == null) continue;
			boolean have = false;
			for (YoldaDeck.Card c : l) if (c.type().equals(e[0]) && c.data().equals(e[2])) have = true;
			if (!have) l.add(new YoldaDeck.Card(e[0] + ":" + Integer.toHexString((e[0] + e[2]).hashCode()), e[0], e[1], e[2],
					e[3]));
		}
		prefs(ctx).edit().putString(PREF, encode(l)).putBoolean(PREF_IMPORTED, true).apply();
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}
}
