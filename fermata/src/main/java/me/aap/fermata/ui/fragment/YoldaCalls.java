package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;

/**
 * Arama: seçtiğin kişiler (Kişiler kartı, tek dokunuşla) ve sesle "Annemi ara". Önce seçtiğin kişilerde, sonra
 * rehberde (izinle) aranır; adlar telaffuza ve eklere dayanıklı eşleşir. Birden çok benzer kişi varsa "Hangisi?"
 * diye sorulur (ekranda seçim + sesle cevap).
 */
public final class YoldaCalls {
	private static final String PREFS = "yolda_calls";

	private YoldaCalls() {
	}

	/** Kişi: ad, numara, fotoğraf adresi (yoksa null). */
	public record Contact(String name, String number, @Nullable String photo) {
		public String initials() {
			StringBuilder b = new StringBuilder();
			for (String w : name.trim().split("\\s+")) {
				if (!w.isEmpty() && (b.length() < 2)) b.append(Character.toUpperCase(w.charAt(0)));
			}
			return (b.length() == 0) ? "?" : b.toString();
		}
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	// ---- seçilen kişiler ----

	public static List<Contact> favorites(Context ctx) {
		List<Contact> l = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(prefs(ctx).getString("favs", "[]"));
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				l.add(new Contact(o.getString("n"), o.getString("p"), o.has("ph") ? o.optString("ph") : null));
			}
		} catch (Throwable ignore) {
		}
		return l;
	}

	private static void save(Context ctx, List<Contact> l) {
		try {
			JSONArray a = new JSONArray();
			for (Contact c : l) {
				JSONObject o = new JSONObject().put("n", c.name()).put("p", c.number());
				if (c.photo() != null) o.put("ph", c.photo());
				a.put(o);
			}
			prefs(ctx).edit().putString("favs", a.toString()).apply();
		} catch (Throwable ignore) {
		}
	}

	/** Kişi ekle (aynı numara varsa güncellenir); en çok 8. */
	public static void addFavorite(Context ctx, Contact c) {
		List<Contact> l = favorites(ctx);
		l.removeIf(x -> norm(x.number()).equals(norm(c.number())));
		l.add(c);
		while (l.size() > 8) l.remove(0);
		save(ctx, l);
	}

	public static void removeFavorite(Context ctx, String number) {
		List<Contact> l = favorites(ctx);
		l.removeIf(x -> norm(x.number()).equals(norm(number)));
		save(ctx, l);
	}

	private static String norm(String n) {
		return (n == null) ? "" : n.replaceAll("[^0-9+]", "");
	}

	/** Kişi seçiciden dönen adres → kişi (seçici izin verir, rehber izni gerekmez). */
	@Nullable
	public static Contact fromPicked(Context ctx, Uri uri) {
		try (Cursor c = ctx.getContentResolver().query(uri, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
				ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI}, null, null, null)) {
			if ((c != null) && c.moveToFirst()) return new Contact(c.getString(0), c.getString(1), c.getString(2));
		} catch (Throwable ex) {
			YoldaLog.add("Kişi okunamadı: " + ex);
		}
		return null;
	}

	// ---- arama ----

	/** Ara: izin varsa doğrudan, yoksa izni iste; izin verilmezse tuşlanmış numara ekranı. */
	public static void call(MainActivityDelegate a, Contact c) {
		Context ctx = a.getContext();
		YoldaLog.add("Arama: " + c.name());
		if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
			start(ctx, new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(c.number()))), c);
			return;
		}
		a.getAppActivity().checkPermissions(Manifest.permission.CALL_PHONE).onCompletion((r, err) -> {
			boolean ok = (r != null) && (r.length > 0) && (r[0] == PackageManager.PERMISSION_GRANTED);
			start(ctx, new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number()))), c);
		});
	}

	private static void start(Context ctx, Intent i, Contact c) {
		try {
			ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} catch (Throwable ex) {
			YoldaLog.add("Arama başlatılamadı: " + ex);
			YoldaStyle.toast(ctx, c.name() + " aranamadı");
		}
	}

	// ---- sesle: "Annemi ara" ----

	/** Bekleyen "Hangisi?" seçenekleri (sesle cevap: ad ya da "birinci / ikinci"). */
	@Nullable
	static volatile List<Contact> pending;

	/** Sesle arama: eşleşen kişi(ler)i bul, tek ise ara, birkaç ise sor. */
	static void voiceCall(MainActivityDelegate a, String who) {
		Context ctx = a.getContext();
		List<Contact> found = match(favorites(ctx), who);
		if (found.isEmpty()) {
			if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
				YoldaVoice.reply(a, "Rehberde aramak için izin gerekiyor");
				a.getAppActivity().checkPermissions(Manifest.permission.READ_CONTACTS).onCompletion((r, err) -> {
					if ((r != null) && (r.length > 0) && (r[0] == PackageManager.PERMISSION_GRANTED)) voiceCall(a, who);
				});
				return;
			}
			found = match(contacts(ctx), who);
		}
		if (found.isEmpty()) {
			YoldaVoice.reply(a, "Rehberde “" + who + "” bulunamadı");
			return;
		}
		if (found.size() == 1) {
			Contact c = found.get(0);
			YoldaVoice.reply(a, c.name() + " aranıyor");
			App.get().getHandler().postDelayed(() -> call(a, c), 1400); // söylenen bitsin, sonra ara
			return;
		}
		List<Contact> opts = new ArrayList<>(found.subList(0, Math.min(3, found.size())));
		pending = opts;
		StringBuilder q = new StringBuilder("Hangisi? ");
		for (int i = 0; i < opts.size(); i++) q.append((i == 0) ? "" : (i == opts.size() - 1) ? " mı, yoksa " : " mı, ")
				.append(opts.get(i).name());
		q.append(" mı?");
		YoldaVoice.reply(a, q.toString());
		showChooser(a, opts);
		// soru söylendikten sonra cevabı dinle
		App.get().getHandler().postDelayed(() -> {
			if (pending == opts) a.startVoiceAssistant();
		}, 2600 + 350L * opts.size());
	}

	/** "Hangisi?" cevabı: ad ya da sıra ("birinci", "ikincisi", "sonuncu"). @return işlendiyse true. */
	static boolean answer(MainActivityDelegate a, String raw) {
		List<Contact> opts = pending;
		if (opts == null) return false;
		String c = YoldaIntents.clean(raw);
		int idx = -1;
		String w = " " + c + " ";
		if (w.matches(".* (bir|birinci|birincisi|ilk|ilki|1)( .*|$)")) idx = 0;
		else if (w.matches(".* (iki|ikinci|ikincisi|2)( .*|$)")) idx = 1;
		else if (w.matches(".* (üç|üçüncü|üçüncüsü|3)( .*|$)")) idx = 2;
		else if (w.matches(".* (son|sonuncu|sonuncusu)( .*|$)")) idx = opts.size() - 1;
		else if (c.matches("(vazgeç|iptal|hiçbiri|boşver|kapat)")) {
			pending = null;
			hideChooser(a);
			YoldaVoice.reply(a, "Tamam, aramıyorum");
			return true;
		} else {
			List<Contact> m = match(opts, c);
			if (!m.isEmpty()) idx = opts.indexOf(m.get(0));
		}
		if ((idx < 0) || (idx >= opts.size())) return false;
		pending = null;
		hideChooser(a);
		Contact ch = opts.get(idx);
		YoldaVoice.reply(a, ch.name() + " aranıyor");
		App.get().getHandler().postDelayed(() -> call(a, ch), 1400);
		return true;
	}

	/** Rehberdeki telefonlu kişiler (izin varsa). */
	static List<Contact> contacts(Context ctx) {
		List<Contact> l = new ArrayList<>();
		try (Cursor c = ctx.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
						ContactsContract.CommonDataKinds.Phone.PHOTO_URI}, null, null, null)) {
			java.util.Set<String> seen = new java.util.HashSet<>();
			while ((c != null) && c.moveToNext() && (l.size() < 5000)) {
				String n = c.getString(0), p = c.getString(1);
				if ((n == null) || (p == null) || !seen.add(n + "|" + norm(p))) continue;
				l.add(new Contact(n, p, c.getString(2)));
			}
		} catch (Throwable ex) {
			YoldaLog.add("Rehber okunamadı: " + ex);
		}
		return l;
	}

	/**
	 * Söylenen ad → en iyi eşleşenler (en iyi önce). Ek farkları ("annemi" ↔ "Annem", "Ahmet'i" ↔ "Ahmet") ve
	 * küçük telaffuz farkları tolere edilir; en iyinin 10 puan yakınındakiler de döner ("Hangisi?").
	 */
	public static List<Contact> match(List<Contact> all, String spoken) {
		List<String> tries = YoldaIntents.nameForms(spoken);
		List<Contact> out = new ArrayList<>();
		List<Integer> scores = new ArrayList<>();
		int best = 0;
		java.util.Set<String> seenNames = new java.util.HashSet<>();
		for (Contact c : all) {
			int s = 0;
			for (String t : tries) s = Math.max(s, nameScore(c.name(), t));
			if (s < 60) continue;
			if (!seenNames.add(c.name().toLowerCase(YoldaIntents.TR))) continue; // aynı kişinin ikinci numarası
			out.add(c);
			scores.add(s);
			best = Math.max(best, s);
		}
		List<Contact> res = new ArrayList<>();
		for (int s = 100; s >= 60; s--) {
			for (int i = 0; i < out.size(); i++) if ((scores.get(i) == s) && (s >= best - 10)) res.add(out.get(i));
		}
		return res;
	}

	/** Kişi adı ile söylenen: tam 100, ilk ad / soyadı tam 90, başlangıç 75, küçük fark 60-70. */
	static int nameScore(String name, String q) {
		String n = YoldaIntents.phon(name), s = YoldaIntents.phon(q);
		if (n.isEmpty() || (s.length() < 2)) return 0;
		if (n.equals(s)) return 100;
		for (String w : name.toLowerCase(YoldaIntents.TR).split("\\s+")) if (YoldaIntents.phon(w).equals(s)) return 90;
		if (n.startsWith(s) && (s.length() >= 3)) return 75;
		int d = YoldaIntents.lev(n, s);
		if ((n.length() >= 4) && (d <= Math.max(1, Math.min(n.length(), s.length()) / 4))) return 70 - 5 * d;
		return 0;
	}

	// ---- "Hangisi?" seçim paneli ----

	private static final Object CHOOSER = new Object();

	private static void showChooser(MainActivityDelegate a, List<Contact> opts) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl)) return;
		hideChooser(a);
		Context ctx = a.getContext();
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		float d = ctx.getResources().getDisplayMetrics().density;
		LinearLayout p = new LinearLayout(ctx);
		p.setTag(CHOOSER);
		p.setOrientation(LinearLayout.VERTICAL);
		p.setPadding(Math.round(20 * d), Math.round(16 * d), Math.round(20 * d), Math.round(16 * d));
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setColor((t.base & 0x00FFFFFF) | 0xE6000000);
		bg.setCornerRadius(28 * d);
		p.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg,
				YoldaStyle.glass(ctx, t, 28)}));
		p.setElevation(45 * d);
		TextView h = new TextView(ctx);
		h.setText("Kimi arayayım?");
		h.setTextColor(t.ink);
		h.setTextSize(18);
		h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		h.setPadding(Math.round(4 * d), 0, 0, Math.round(10 * d));
		p.addView(h);
		for (int i = 0; i < opts.size(); i++) {
			Contact c = opts.get(i);
			TextView b = new TextView(ctx);
			b.setText((i + 1) + ".  " + c.name());
			b.setTextColor(t.ink);
			b.setTextSize(18);
			b.setGravity(Gravity.CENTER_VERTICAL);
			b.setPadding(Math.round(16 * d), 0, Math.round(16 * d), 0);
			b.setBackground(YoldaStyle.glass(ctx, t, 999));
			b.setContentDescription(c.name() + " ara");
			b.setOnClickListener(v -> {
				pending = null;
				hideChooser(a);
				call(a, c);
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.round(56 * d));
			lp.topMargin = Math.round(8 * d);
			p.addView(b, lp);
		}
		TextView cancel = new TextView(ctx);
		cancel.setText("Vazgeç");
		cancel.setTextColor(t.muted);
		cancel.setTextSize(15);
		cancel.setGravity(Gravity.CENTER);
		cancel.setOnClickListener(v -> {
			pending = null;
			hideChooser(a);
		});
		p.addView(cancel, new LinearLayout.LayoutParams(-1, Math.round(48 * d)));
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(Math.round(Math.min(460 * d,
				ctx.getResources().getDisplayMetrics().widthPixels - 32 * d)), -2);
		lp.startToStart = lp.endToEnd = lp.topToTop = lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		cl.addView(p, lp);
		// 20 sn içinde seçilmezse kapanır
		p.postDelayed(() -> {
			if (pending == opts) pending = null;
			if (p.getParent() != null) cl.removeView(p);
		}, 20_000);
	}

	static void hideChooser(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl)) return;
		for (int i = cl.getChildCount() - 1; i >= 0; i--) {
			if (cl.getChildAt(i).getTag() == CHOOSER) cl.removeViewAt(i);
		}
	}
}
