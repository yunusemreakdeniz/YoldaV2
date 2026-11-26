package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaRadar;

/**
 * Araç klavyesi (Mola'dan): Android Auto ekranında telefonun klavyesi açılmadığı için Türkçe Q düzeninde
 * ekran klavyesi. Güvenlik: yalnız araç dururken (5 km/s altı) açılır; sürüşte aynı dokunuş sesle yazmayı açar,
 * yazarken araç hareket ederse tuşlar kapanır. Tuşlar en az 48 dp.
 */
public final class YoldaCarKeyboard {
	static final String[][] LETTERS = {
			{"q", "w", "e", "r", "t", "y", "u", "ı", "o", "p", "ğ", "ü"},
			{"a", "s", "d", "f", "g", "h", "j", "k", "l", "ş", "i"},
			{"z", "x", "c", "v", "b", "n", "m", "ö", "ç", "⌫"}};
	static final String[][] NUMBERS = {
			{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
			{"-", "/", ":", ";", "(", ")", "&", "@", "\"", "'"},
			{".", ",", "?", "!", "+", "#", "%", "*", "⌫"}};
	static final int MAX_STILL_KMH = 5;

	@Nullable
	private static FrameLayout open;
	@Nullable
	private static EditText target;
	private static boolean numbers;

	private YoldaCarKeyboard() {
	}

	/** Klavye açılabilir mi: araç duruyor (hız bilinmiyorsa da açılır, yalnız hareket ederken kapalı). */
	static boolean allowed(boolean fix, int speedKmh) {
		return !fix || (speedKmh < MAX_STILL_KMH);
	}

	/** Tuşun yazıya etkisi: imleçte ekle ya da sil (⌫); yeni imleç konumu döner. */
	static int apply(StringBuilder text, int cursor, String key) {
		int c = Math.max(0, Math.min(cursor, text.length()));
		if ("⌫".equals(key)) {
			if (c == 0) return 0;
			text.deleteCharAt(c - 1);
			return c - 1;
		}
		text.insert(c, key);
		return c + key.length();
	}

	public static boolean isOpen() {
		return open != null;
	}

	/** Yazı kutusuna dokunuldu (araçta): dururken klavye, sürüşte sesle yazma. */
	public static void onTap(MainActivityDelegate a, EditText e) {
		if (!allowed(YoldaRadar.hasFix(), YoldaRadar.speed)) {
			YoldaStyle.toast(a.getContext(), "Sürüşte klavye kapalı — sesle yaz");
			YoldaSearchMic.dictate(a, e);
			return;
		}
		show(a, e);
	}

	public static void show(MainActivityDelegate a, EditText e) {
		hide();
		View host = a.findViewById(me.aap.fermata.R.id.main_activity);
		if (!(host instanceof ViewGroup hg)) return;
		Context ctx = a.getContext();
		target = e;
		e.requestFocus();
		e.setSelection(e.length());
		FrameLayout panel = new FrameLayout(ctx);
		panel.setElevation(dp(ctx, 40));
		panel.setClickable(true); // arkadaki sayfaya dokunuş geçmesin
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xF2131518);
		bg.setStroke(Math.max(1, dp(ctx, 1)), 0xFF2A2D33);
		bg.setCornerRadii(new float[]{dp(ctx, 22), dp(ctx, 22), dp(ctx, 22), dp(ctx, 22), 0, 0, 0, 0});
		panel.setBackground(bg);
		panel.setContentDescription("Araç klavyesi");
		panel.setTag(a);
		build(panel);
		ViewGroup.LayoutParams lp;
		if (hg instanceof androidx.constraintlayout.widget.ConstraintLayout) {
			androidx.constraintlayout.widget.ConstraintLayout.LayoutParams cl =
					new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
			cl.startToStart = cl.endToEnd = cl.bottomToBottom =
					androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
			lp = cl;
		} else {
			lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
		}
		hg.addView(panel, lp);
		open = panel;
		watchMotion(panel);
	}

	public static void hide() {
		FrameLayout p = open;
		open = null;
		target = null;
		numbers = false;
		if ((p != null) && (p.getParent() instanceof ViewGroup g)) g.removeView(p);
	}

	/** Yazarken araç hareket ederse tuşlar kapanır (saniyede bir bakılır). */
	private static void watchMotion(FrameLayout panel) {
		panel.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (open != panel) return;
				if (!allowed(YoldaRadar.hasFix(), YoldaRadar.speed)) {
					YoldaStyle.toast(panel.getContext(), "Sürüşte klavye kapalı — sesle yaz");
					hide();
					return;
				}
				panel.postDelayed(this, 1000);
			}
		}, 1000);
	}

	private static void build(FrameLayout panel) {
		Context ctx = panel.getContext();
		panel.removeAllViews();
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(ctx, 8);
		col.setPadding(pad, pad, pad, pad);
		for (String[] row : numbers ? NUMBERS : LETTERS) {
			LinearLayout r = new LinearLayout(ctx);
			r.setGravity(Gravity.CENTER);
			for (String k : row) r.addView(key(ctx, k, "⌫".equals(k) ? "Sil" : k, v -> type(k)), keyLp(ctx, "⌫".equals(k) ? 1.5f : 1f));
			col.addView(r, new LinearLayout.LayoutParams(-1, dp(ctx, 52)));
		}
		LinearLayout last = new LinearLayout(ctx);
		last.setGravity(Gravity.CENTER);
		last.addView(key(ctx, numbers ? "abc" : "123", numbers ? "Harfler" : "Rakamlar", v -> {
			numbers = !numbers;
			build(panel);
		}), keyLp(ctx, 1.4f));
		last.addView(key(ctx, "🎤", "Sesle yaz", v -> {
			EditText e = target;
			Object a = panel.getTag();
			if ((e != null) && (a instanceof MainActivityDelegate d)) YoldaSearchMic.dictate(d, e);
		}), keyLp(ctx, 1.2f));
		last.addView(key(ctx, "boşluk", "Boşluk", v -> type(" ")), keyLp(ctx, 4f));
		last.addView(key(ctx, "Ara", "Ara", v -> submit()), keyLp(ctx, 1.6f));
		last.addView(key(ctx, "✕", "Klavyeyi kapat", v -> hide()), keyLp(ctx, 1.1f));
		col.addView(last, new LinearLayout.LayoutParams(-1, dp(ctx, 52)));
		panel.addView(col, new FrameLayout.LayoutParams(-1, -2));
	}

	private static void type(String k) {
		EditText e = target;
		if (e == null) return;
		Editable ed = e.getText();
		StringBuilder sb = new StringBuilder(ed);
		int c = apply(sb, e.getSelectionStart(), k);
		e.setText(sb);
		e.setSelection(Math.min(c, e.length()));
	}

	private static void submit() {
		EditText e = target;
		hide();
		if (e == null) return;
		int act = e.getImeOptions() & EditorInfo.IME_MASK_ACTION;
		e.onEditorAction((act == EditorInfo.IME_ACTION_NONE) || (act == EditorInfo.IME_ACTION_UNSPECIFIED)
				? EditorInfo.IME_ACTION_SEARCH : act);
	}

	private static TextView key(Context ctx, String label, String desc, View.OnClickListener click) {
		TextView t = new TextView(ctx);
		t.setText(label);
		t.setTextSize(label.length() > 2 ? 15 : 20);
		t.setTextColor(0xFFF4F4F5);
		t.setGravity(Gravity.CENTER);
		t.setIncludeFontPadding(false);
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(ctx, 10));
		g.setColor(0xFF1F2126);
		g.setStroke(Math.max(1, dp(ctx, 1)), 0xFF2A2D33);
		t.setBackground(g);
		t.setContentDescription(desc);
		t.setClickable(true);
		t.setOnClickListener(v -> {
			YoldaStyle.haptic(v);
			click.onClick(v);
		});
		return t;
	}

	private static LinearLayout.LayoutParams keyLp(Context ctx, float w) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ctx, 48), w);
		lp.leftMargin = lp.rightMargin = dp(ctx, 3);
		return lp;
	}

	private static int dp(Context ctx, int v) {
		return Math.round(v * ctx.getResources().getDisplayMetrics().density);
	}
}
