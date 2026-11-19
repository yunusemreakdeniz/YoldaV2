package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Tema seçimi: renk örnekleriyle bütün temalar. Üstte kip: "Tek tema" ya da "Gündüz / gece otomatik"
 * (otomatikte gündüz ve gece için ayrı tema seçilir; telefonda gün batımına, araçta farlara göre değişir).
 * Seçim hemen uygulanır (bütün ekranlar tema dinleyicisiyle yenilenir).
 */
public final class YoldaThemePicker extends FrameLayout {
	private static final int TAG_KEY = 0x7f0b5e31;
	private final LinearLayout col;

	public static void show(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl) || (find(cl) != null)) return;
		YoldaThemePicker v = new YoldaThemePicker(cl.getContext());
		v.setTag(TAG_KEY, Boolean.TRUE);
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
		lp.startToStart = lp.endToEnd = lp.topToTop = lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		v.setElevation(v.dp(45));
		cl.addView(v, lp);
		v.setAlpha(0f);
		v.animate().alpha(1f).setDuration(180).start();
	}

	/** Açıksa kapatır (geri tuşu). */
	public static boolean hide(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ViewGroup g)) return false;
		YoldaThemePicker v = find(g);
		if (v == null) return false;
		v.close();
		return true;
	}

	@Nullable
	private static YoldaThemePicker find(ViewGroup g) {
		for (int i = 0; i < g.getChildCount(); i++)
			if ((g.getChildAt(i) instanceof YoldaThemePicker v) && (v.getTag(TAG_KEY) != null)) return v;
		return null;
	}

	private YoldaThemePicker(Context ctx) {
		super(ctx);
		setClickable(true);
		setBackgroundColor(0xE6000000);
		setOnClickListener(v -> close());
		LinearLayout panel = new LinearLayout(ctx);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setClickable(true);
		panel.setPadding(dp(20), dp(16), dp(20), dp(16));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xF2141821);
		bg.setCornerRadius(dp(28));
		bg.setStroke(dp(1), 0x33FFFFFF);
		panel.setBackground(bg);
		LayoutParams plp = new LayoutParams(Math.min(dp(720), Math.round(getResources().getDisplayMetrics().widthPixels * 0.94f)),
				-1, Gravity.CENTER);
		plp.topMargin = plp.bottomMargin = dp(24);
		addView(panel, plp);

		LinearLayout head = new LinearLayout(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = text(ctx, "Temalar", 22, true, 0xFFFFFFFF);
		head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
		TextView x = text(ctx, "✕", 20, true, 0xFFFFFFFF);
		x.setGravity(Gravity.CENTER);
		x.setContentDescription("Kapat");
		x.setOnClickListener(v -> close());
		head.addView(x, new LinearLayout.LayoutParams(dp(48), dp(48)));
		panel.addView(head);

		ScrollView sv = new ScrollView(ctx);
		col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		sv.addView(col);
		panel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
		render();
	}

	private void render() {
		Context ctx = getContext();
		col.removeAllViews();
		boolean auto = YoldaStyle.isAuto(ctx);

		// Kip: tek tema / gündüz-gece otomatik
		LinearLayout mode = new LinearLayout(ctx);
		mode.setPadding(0, dp(8), 0, dp(4));
		mode.addView(seg(ctx, "Tek tema", !auto, () -> {
			YoldaStyle.setAuto(ctx, false);
			render();
		}), new LinearLayout.LayoutParams(0, dp(48), 1f));
		LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, dp(48), 1f);
		ml.leftMargin = dp(10);
		mode.addView(seg(ctx, "☀ / 🌙 Otomatik", auto, () -> {
			YoldaStyle.setAuto(ctx, true);
			render();
		}), ml);
		col.addView(mode);

		// Hava efekti: Otomatik (havaya göre), Kapalı ya da her zaman seçilen efekt
		col.addView(section(ctx, "Hava efekti"));
		String fx = YoldaSky.mode;
		LinearLayout fr = null;
		for (int i = 0; i < YoldaSky.MODES.length; i++) {
			if (i % 3 == 0) {
				fr = new LinearLayout(ctx);
				fr.setPadding(0, dp(4), 0, dp(4));
				col.addView(fr);
			}
			String m = YoldaSky.MODES[i][0];
			LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, dp(48), 1f);
			if (i % 3 != 0) fl.leftMargin = dp(8);
			fr.addView(seg(ctx, YoldaSky.MODES[i][1], m.equals(fx), () -> {
				YoldaSky.setMode(ctx, m);
				YoldaStyle.invalidateBackdrop();
				render();
			}), fl);
		}
		col.addView(note(ctx, "auto".equals(fx) ? ("Otomatik: havaya göre, şu an " + YoldaSky.label(YoldaSky.current())
				+ ". Araçta ve animasyonlar kapalıyken sahne sabit durur.")
				: ("off".equals(fx) ? "Kapalı: yalnız tema ışıltısı." : (YoldaSky.modeLabel(fx) + ": hava ne olursa olsun her zaman."))));

		if (!auto) {
			col.addView(note(ctx, "Seçtiğin tema her zaman kullanılır."));
			grid(ctx, YoldaStyle.index(ctx), i -> YoldaStyle.setTheme(ctx, i));
		} else {
			boolean night = YoldaStyle.isNight(ctx);
			col.addView(note(ctx, "Gündüz ve gece için ayrı tema seç. Telefonda gün doğumu / batımına göre, araçta "
					+ "farlara göre kendiliğinden değişir. Şu an: " + (night ? "🌙 gece" : "☀ gündüz") + "."));
			col.addView(section(ctx, "☀ Gündüz teması"));
			grid(ctx, YoldaStyle.dayTheme(ctx), i -> YoldaStyle.setDayTheme(ctx, i));
			col.addView(section(ctx, "🌙 Gece teması"));
			grid(ctx, YoldaStyle.nightTheme(ctx), i -> YoldaStyle.setNightTheme(ctx, i));
		}
	}

	/** Tema örnekleri ızgarası (satırda 2-4). */
	private void grid(Context ctx, int selected, java.util.function.IntConsumer pick) {
		int w = getResources().getDisplayMetrics().widthPixels;
		int per = (w > dp(900)) ? 4 : (w > dp(560)) ? 3 : 2;
		LinearLayout row = null;
		YoldaStyle.Theme[] all = YoldaStyle.THEMES;
		for (int i = 0; i < all.length; i++) {
			if (i % per == 0) {
				row = new LinearLayout(ctx);
				LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
				rl.topMargin = dp(10);
				col.addView(row, rl);
			}
			int idx = i;
			LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(96), 1f);
			if (i % per != 0) sl.leftMargin = dp(10);
			row.addView(swatch(ctx, all[i], i == selected, () -> {
				pick.accept(idx);
				YoldaStyle.haptic(this);
				render();
			}), sl);
		}
		// son satırı doldur (örnekler aynı genişlikte kalsın)
		if ((row != null) && (all.length % per != 0)) {
			for (int k = all.length % per; k < per; k++) {
				LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(96), 1f);
				sl.leftMargin = dp(10);
				row.addView(new View(ctx), sl);
			}
		}
	}

	private View swatch(Context ctx, YoldaStyle.Theme t, boolean sel, Runnable click) {
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable base = new GradientDrawable();
		base.setColor(t.base);
		base.setCornerRadius(dp(18));
		GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[]{YoldaStyle.alpha(t.blobs[0], 0.95f), YoldaStyle.alpha(t.blobs[1], 0.75f),
						YoldaStyle.alpha(t.blobs[2], 0.55f), YoldaStyle.alpha(t.base, 0f)});
		glow.setCornerRadius(dp(18));
		GradientDrawable edge = new GradientDrawable();
		edge.setCornerRadius(dp(18));
		edge.setColor(0);
		edge.setStroke(dp(sel ? 3 : 1), sel ? 0xFFFFFFFF : 0x33FFFFFF);
		f.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{base, glow, edge}));
		f.setClickable(true);
		f.setOnClickListener(v -> click.run());
		f.setContentDescription("Tema: " + t.name + (sel ? " (seçili)" : ""));
		TextView n = text(ctx, t.name, 16, true, 0xFFFFFFFF);
		n.setShadowLayer(dp(3), 0, dp(1), 0x80000000);
		FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
		nl.leftMargin = dp(12);
		nl.bottomMargin = dp(10);
		f.addView(n, nl);
		String kind = (t.cards == YoldaStyle.CARDS_NOBLE) ? "sade" : (t.cards == YoldaStyle.CARDS_SOFT) ? "yumuşak" : "canlı";
		TextView k = text(ctx, kind, 11, false, 0xCCFFFFFF);
		FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
		kl.leftMargin = dp(12);
		kl.topMargin = dp(8);
		f.addView(k, kl);
		View dot = new View(ctx); // vurgu rengi
		GradientDrawable dd = new GradientDrawable();
		dd.setShape(GradientDrawable.OVAL);
		dd.setColor(t.accent);
		dot.setBackground(dd);
		FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP | Gravity.END);
		dl.rightMargin = dl.topMargin = dp(10);
		f.addView(dot, dl);
		if (sel) {
			TextView ck = text(ctx, "✓", 22, true, 0xFFFFFFFF);
			ck.setShadowLayer(dp(3), 0, dp(1), 0x80000000);
			FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
			cl.rightMargin = dp(12);
			cl.bottomMargin = dp(6);
			f.addView(ck, cl);
		}
		return f;
	}

	private TextView seg(Context ctx, String label, boolean on, Runnable click) {
		TextView b = text(ctx, label, 15, true, on ? 0xFF111111 : 0xFFFFFFFF);
		b.setGravity(Gravity.CENTER);
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(999));
		g.setColor(on ? 0xFFFFFFFF : 0x22FFFFFF);
		b.setBackground(g);
		b.setClickable(true);
		b.setOnClickListener(v -> click.run());
		return b;
	}

	private TextView section(Context ctx, String s) {
		TextView t = text(ctx, s, 17, true, 0xFFFFFFFF);
		t.setPadding(0, dp(16), 0, 0);
		return t;
	}

	private TextView note(Context ctx, String s) {
		TextView t = text(ctx, s, 14, false, 0xCCFFFFFF);
		t.setPadding(0, dp(6), 0, dp(2));
		return t;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size);
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private void close() {
		if (getTag(TAG_KEY) == null) return;
		setTag(TAG_KEY, null);
		animate().alpha(0f).setDuration(160).withEndAction(() -> {
			if (getParent() instanceof ViewGroup g) g.removeView(this);
		}).start();
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
