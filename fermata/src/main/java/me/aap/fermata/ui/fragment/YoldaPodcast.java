package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Podcast kartı: Apple'ın açık podcast dizininde arama (iTunes Search API, anahtar gerekmez), bölümler
 * podcast'in kendi RSS akışından. Abonelikler telefonda; kartta her aboneliğin son bölümü tek dokunuşla çalar.
 */
public final class YoldaPodcast {
	private static final String PREFS = "yolda_podcast";

	/** Podcast: ad, yayıncı, RSS adresi, kapak. */
	public record Show(String name, String author, String feed, @Nullable String art) {
	}

	/** Bölüm: başlık, ses adresi, yayın tarihi (metin), süre (sn, bilinmiyorsa 0). */
	public record Episode(String title, String audio, String date, long durSec) {
	}

	private YoldaPodcast() {
	}

	static List<Show> parseSearch(String json) throws Exception {
		JSONArray r = new JSONObject(json).optJSONArray("results");
		List<Show> out = new ArrayList<>();
		if (r == null) return out;
		for (int i = 0; i < r.length(); i++) {
			JSONObject o = r.getJSONObject(i);
			String feed = o.optString("feedUrl", "");
			if (feed.isEmpty()) continue;
			String art = o.optString("artworkUrl100", o.optString("artworkUrl60", ""));
			out.add(new Show(o.optString("collectionName", o.optString("trackName", "Podcast")), o.optString("artistName", ""), feed,
					art.isEmpty() ? null : art));
		}
		return out;
	}

	private static final Pattern ITEM = Pattern.compile("<item[ >](.*?)</item>", Pattern.DOTALL);
	private static final Pattern TITLE = Pattern.compile("<title>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</title>", Pattern.DOTALL);
	private static final Pattern ENCL = Pattern.compile("<enclosure[^>]*url=\"([^\"]+)\"");
	private static final Pattern DATE = Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.DOTALL);
	private static final Pattern DUR = Pattern.compile("<itunes:duration>(.*?)</itunes:duration>", Pattern.DOTALL);

	/** RSS → bölümler (en yeni önce, en çok 30; ses dosyası olmayan atlanır). */
	static List<Episode> parseFeed(String xml) {
		List<Episode> out = new ArrayList<>();
		Matcher m = ITEM.matcher(xml);
		while (m.find() && (out.size() < 30)) {
			String it = m.group(1);
			Matcher e = ENCL.matcher(it);
			if (!e.find()) continue;
			Matcher t = TITLE.matcher(it);
			Matcher d = DATE.matcher(it);
			Matcher du = DUR.matcher(it);
			String date = d.find() ? d.group(1).trim() : "";
			if (date.length() > 16) date = date.substring(0, 16); // "Tue, 18 Nov 2026"
			out.add(new Episode(t.find() ? YoldaInfo.unescape(t.group(1)).trim() : "Bölüm", YoldaInfo.unescape(e.group(1)), date,
					du.find() ? duration(du.group(1).trim()) : 0));
		}
		return out;
	}

	/** "1:02:03" / "45:10" / "2710" → saniye. */
	static long duration(String s) {
		try {
			String[] p = s.split(":");
			long v = 0;
			for (String x : p) v = v * 60 + Long.parseLong(x.trim());
			return v;
		} catch (Exception ex) {
			return 0;
		}
	}

	public static String durText(long sec) {
		if (sec <= 0) return "";
		long m = sec / 60;
		return (m >= 60) ? ((m / 60) + " sa " + (m % 60) + " dk") : (m + " dk");
	}

	/** Arama (arka planda çağır). */
	public static List<Show> search(String term) throws Exception {
		return parseSearch(YoldaInfo.get("https://itunes.apple.com/search?media=podcast&country=TR&limit=15&term="
				+ URLEncoder.encode(term, "UTF-8")));
	}

	/** Bölümler (arka planda çağır). */
	public static List<Episode> episodes(String feed) throws Exception {
		return parseFeed(YoldaInfo.get(feed));
	}

	// ---- abonelikler ----

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static List<Show> subscriptions(Context ctx) {
		List<Show> out = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(prefs(ctx).getString("subs", "[]"));
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				out.add(new Show(o.optString("n"), o.optString("a"), o.optString("f"), o.has("i") ? o.optString("i") : null));
			}
		} catch (Exception ignore) {
		}
		return out;
	}

	public static void saveSubscriptions(Context ctx, List<Show> l) {
		JSONArray a = new JSONArray();
		try {
			for (Show s : l) {
				JSONObject o = new JSONObject().put("n", s.name()).put("a", s.author()).put("f", s.feed());
				if (s.art() != null) o.put("i", s.art());
				a.put(o);
			}
		} catch (Exception ignore) {
		}
		prefs(ctx).edit().putString("subs", a.toString()).apply();
	}

	public static void subscribe(Context ctx, Show s) {
		List<Show> l = subscriptions(ctx);
		l.removeIf(x -> x.feed().equals(s.feed()));
		l.add(0, s);
		saveSubscriptions(ctx, l);
	}
}
