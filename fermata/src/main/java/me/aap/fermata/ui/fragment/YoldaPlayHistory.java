package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * "Kaldığın yerden" için oynatma geçmişi: son çalınan / izlenen öğeler (en yenisi önce, en çok 12), türü
 * (TV, radyo, müzik / video), ne zaman ve ne kadarı dinlendi. Aynı öğe yeniden çalınınca başa geçer.
 */
public final class YoldaPlayHistory {
	private static final String PREFS = "yolda_history";
	static final int MAX = 12;

	/**
	 * Bir kayıt. {@code url}: adresle açılan akış (radyo dizini, yedek TV adresi) için; yoksa kütüphane kimliği
	 * {@code id} ile bulunur. Süre bilinmiyorsa 0.
	 */
	public record Entry(String id, String name, String kind, @Nullable String url, long posMs, long durMs, long at) {
		/** İlerleme yüzdesi (0-100); canlı yayında ya da süre bilinmiyorsa -1. */
		public int percent() {
			if ((durMs <= 0) || (posMs <= 0)) return -1;
			return (int) Math.max(0, Math.min(100, posMs * 100 / durMs));
		}

		JSONObject json() throws Exception {
			JSONObject o = new JSONObject().put("id", id).put("n", name).put("k", kind).put("p", posMs).put("d", durMs).put("t", at);
			if (url != null) o.put("u", url);
			return o;
		}

		static Entry of(JSONObject o) {
			return new Entry(o.optString("id"), o.optString("n"), o.optString("k"), o.has("u") ? o.optString("u") : null,
					o.optLong("p"), o.optLong("d"), o.optLong("t"));
		}
	}

	private YoldaPlayHistory() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	static List<Entry> decode(@Nullable String s) {
		List<Entry> out = new ArrayList<>();
		if ((s == null) || s.isEmpty()) return out;
		try {
			JSONArray a = new JSONArray(s);
			for (int i = 0; i < a.length(); i++) out.add(Entry.of(a.getJSONObject(i)));
		} catch (Exception ignore) {
		}
		return out;
	}

	static String encode(List<Entry> l) {
		JSONArray a = new JSONArray();
		try {
			for (int i = 0; i < Math.min(MAX, l.size()); i++) a.put(l.get(i).json());
		} catch (Exception ignore) {
		}
		return a.toString();
	}

	/** Yeni kaydı başa koy (aynı kimlik ya da adres varsa eskisini çıkar). */
	static List<Entry> push(List<Entry> l, Entry e) {
		List<Entry> out = new ArrayList<>();
		out.add(e);
		for (Entry x : l) {
			if (x.id().equals(e.id()) || ((e.url() != null) && e.url().equals(x.url()))) continue;
			if (out.size() < MAX) out.add(x);
		}
		return out;
	}

	public static synchronized void add(Context ctx, String id, String name, String kind, @Nullable String url,
																			long posMs, long durMs) {
		if ((name == null) || name.isEmpty()) return;
		List<Entry> l = push(decode(prefs(ctx).getString("l", null)),
				new Entry(id, name, kind, url, Math.max(0, posMs), Math.max(0, durMs), System.currentTimeMillis()));
		prefs(ctx).edit().putString("l", encode(l)).apply();
	}

	public static synchronized List<Entry> all(Context ctx) {
		return decode(prefs(ctx).getString("l", null));
	}

	/** Bir türün (TV / Radyo) son açılan, yinelenmeyen kimlikleri (en yeni önce, en çok max). */
	public static List<String> recentIds(List<Entry> l, String kind, int max) {
		List<String> out = new java.util.ArrayList<>();
		for (Entry e : l) {
			if (out.size() >= max) break;
			if (kind.equals(e.kind()) && !out.contains(e.id())) out.add(e.id());
		}
		return out;
	}

	public static synchronized void clear(Context ctx) {
		prefs(ctx).edit().remove("l").apply();
	}

	/** "az önce", "5 dk önce", "2 sa önce", "dün", "3 gün önce". */
	public static String ago(long at, long now) {
		long m = Math.max(0, (now - at) / 60_000);
		if (m < 2) return "az önce";
		if (m < 60) return m + " dk önce";
		long h = m / 60;
		if (h < 24) return h + " sa önce";
		long d = h / 24;
		return (d == 1) ? "dün" : (d + " gün önce");
	}

	/** Kartın alt satırı: "TRT 1 · 5 dk önce · Sımarık %42". */
	public static String line(List<Entry> l, long now) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < Math.min(2, l.size()); i++) {
			Entry e = l.get(i);
			if (b.length() > 0) b.append(" · ");
			b.append(e.name());
			int p = e.percent();
			if (p >= 0) b.append(" %").append(p);
			if (i == 0) b.append(" · ").append(ago(e.at(), now));
		}
		return b.toString();
	}
}
