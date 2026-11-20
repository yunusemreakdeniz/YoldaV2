package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Spor kartı: Süper Lig'in son sonuçları ve sıradaki maçları (TheSportsDB, herkese açık deneme anahtarı; günde
 * az istek, 30 dakika önbellek). Tuttuğun takım seçilirse onun maçları öne gelir. Skorlar canlı değildir.
 */
public final class YoldaSports {
	private static final String PREFS = "yolda_sports";
	static final String LEAGUE = "4339"; // Türkiye Süper Lig
	private static final String API = "https://www.thesportsdb.com/api/v1/json/3/";

	/** Maç: ev, deplasman, skorlar (oynanmadıysa -1), tarih (yyyy-MM-dd), saat (HH:mm, boş olabilir). */
	public record Match(String home, String away, int hs, int as, String date, String time) {
		public boolean played() {
			return (hs >= 0) && (as >= 0);
		}

		public String line() {
			return played() ? (home + " " + hs + "-" + as + " " + away) : (home + " - " + away + " · " + date.substring(Math.min(5, date.length()))
					.replace('-', '.') + (time.isEmpty() ? "" : (" " + time)));
		}

		boolean has(String team) {
			String t = YoldaChannelAlt.norm(team);
			return !t.isEmpty() && (YoldaChannelAlt.norm(home).contains(t) || YoldaChannelAlt.norm(away).contains(t));
		}
	}

	private YoldaSports() {
	}

	static List<Match> parse(String json) throws Exception {
		JSONArray ev = new JSONObject(json).optJSONArray("events");
		List<Match> out = new ArrayList<>();
		if (ev == null) return out;
		for (int i = 0; i < ev.length(); i++) {
			JSONObject e = ev.getJSONObject(i);
			String hs = e.optString("intHomeScore", ""), as = e.optString("intAwayScore", "");
			String time = e.optString("strTime", "");
			if (time.length() >= 5) time = time.substring(0, 5);
			out.add(new Match(e.optString("strHomeTeam"), e.optString("strAwayTeam"), num(hs), num(as), e.optString("dateEvent", ""),
					"00:00".equals(time) ? "" : time));
		}
		return out;
	}

	private static int num(String s) {
		try {
			return ((s == null) || s.isEmpty() || "null".equals(s)) ? -1 : Integer.parseInt(s.trim());
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	@Nullable
	public static String team(Context ctx) {
		return prefs(ctx).getString("team", null);
	}

	public static void setTeam(Context ctx, @Nullable String t) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		if ((t == null) || t.trim().isEmpty()) e.remove("team");
		else e.putString("team", t.trim());
		e.apply();
	}

	/** Son sonuçlar ve sıradaki maçlar: {son, sıradaki}; tutulan takımınki öne. Arka planda çağır (30 dk önbellek). */
	@SuppressWarnings("unchecked")
	public static List<Match>[] matches(Context ctx, boolean refresh) {
		SharedPreferences p = prefs(ctx);
		if (refresh && (System.currentTimeMillis() - p.getLong("at", 0) > 30 * 60_000L)) {
			try {
				String past = YoldaInfo.get(API + "eventspastleague.php?id=" + LEAGUE);
				String next = YoldaInfo.get(API + "eventsnextleague.php?id=" + LEAGUE);
				p.edit().putString("past", past).putString("next", next).putLong("at", System.currentTimeMillis()).apply();
			} catch (Throwable ex) {
				YoldaLog.add("Spor sonuçları alınamadı: " + ex.getClass().getSimpleName());
			}
		}
		String t0 = team(ctx);
		long at = p.getLong("at", 0);
		if ((memo != null) && (memoAt == at) && java.util.Objects.equals(memoTeam, t0))
			return new List[]{new ArrayList<>(memo[0]), new ArrayList<>(memo[1])}; // aynı veri: yeniden ayrıştırma
		List<Match> past = new ArrayList<>(), next = new ArrayList<>();
		try {
			past = parse(p.getString("past", "{}"));
			java.util.Collections.reverse(past); // en yeni önce
			next = parse(p.getString("next", "{}"));
		} catch (Throwable ignore) {
		}
		String t = team(ctx);
		if (t != null) {
			past.sort((a, b) -> Boolean.compare(b.has(t), a.has(t)));
			next.sort((a, b) -> Boolean.compare(b.has(t), a.has(t)));
		}
		memo = new List[]{past, next};
		memoAt = at;
		memoTeam = t;
		return new List[]{new ArrayList<>(past), new ArrayList<>(next)};
	}

	private static volatile List<Match>[] memo;
	private static volatile long memoAt;
	private static volatile String memoTeam;
}
