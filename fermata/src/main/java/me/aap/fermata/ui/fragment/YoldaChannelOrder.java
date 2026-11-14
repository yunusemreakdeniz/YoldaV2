package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Kanalların kullanıcı sırası (TV ve Radyo kartlarından "Sırala"): kartta ve Kanallar ekranında bu sırayla.
 * Sırada olmayan (yeni) kanallar sona, kendi sıralarıyla eklenir.
 */
public final class YoldaChannelOrder {
	private static final String PREFS = "yolda_channels";

	private YoldaChannelOrder() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static List<String> order(Context ctx) {
		List<String> l = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(prefs(ctx).getString("order", "[]"));
			for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
		} catch (Throwable ignore) {
		}
		return l;
	}

	public static boolean hasOrder(Context ctx) {
		return !order(ctx).isEmpty();
	}

	public static void save(Context ctx, List<String> ids) {
		JSONArray a = new JSONArray();
		for (String id : ids) a.put(id);
		prefs(ctx).edit().putString("order", a.toString()).apply();
	}

	public static void reset(Context ctx) {
		prefs(ctx).edit().remove("order").apply();
	}

	/** Listeyi kayıtlı sıraya göre diz (yerinde; sırada olmayanlar sona, kendi sıralarıyla). */
	public static <T> void sort(List<T> l, List<String> order, Function<T, String> id) {
		if (order.isEmpty() || (l.size() < 2)) return;
		Map<String, Integer> pos = new HashMap<>();
		for (int i = 0; i < order.size(); i++) pos.putIfAbsent(order.get(i), i);
		Map<T, Integer> orig = new HashMap<>();
		for (int i = 0; i < l.size(); i++) orig.put(l.get(i), i);
		l.sort((a, b) -> {
			Integer pa = pos.get(id.apply(a)), pb = pos.get(id.apply(b));
			if ((pa != null) && (pb != null)) return Integer.compare(pa, pb);
			if (pa != null) return -1;
			if (pb != null) return 1;
			return Integer.compare(orig.get(a), orig.get(b));
		});
	}

	/** Bir öğeyi yukarı (dir = -1) ya da aşağı (1) taşı; yeni konumu döner. */
	public static <T> int move(List<T> l, int i, int dir) {
		int j = i + dir;
		if ((i < 0) || (i >= l.size()) || (j < 0) || (j >= l.size())) return i;
		T x = l.remove(i);
		l.add(j, x);
		return j;
	}
}
