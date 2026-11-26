package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Video izlerken sağ üstte kalite düğmesi ("480p ▾"): dokununca 360p · 480p · 720p · 1080p · Otomatik açılır,
 * seçilen kalite çalan videoya (TV ve YouTube) hemen uygulanır. Bir süre dokunulmazsa soluklaşır, videoyu
 * kapatmaz; video ekrandan kalkınca kendisi de kalkar.
 */
@SuppressLint("ViewConstructor")
public class YoldaQualityPill extends LinearLayout {
	private static final Object TAG = new Object();
	private final MainActivityDelegate a;
	private boolean open;
	private final Runnable dim = () -> animate().alpha(0.45f).setDuration(400).start();

	private YoldaQualityPill(MainActivityDelegate a) {
		super(a.getContext());
		this.a = a;
		setOrientation(HORIZONTAL);
		setGravity(Gravity.CENTER_VERTICAL);
		setTag(R.id.yolda_vq, TAG);
		setElevation(dp(40));
		render();
	}

	/** Video ekrandaysa düğmeyi göster, değilse kaldır (video modu ve YouTube ekranı değiştikçe çağrılır). */
	public static void update(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl)) return;
		YoldaQualityPill cur = find(cl);
		boolean want = a.yoldaVideoOnScreen() && !BodyLayout.yoldaInlineCard(a); // kart içi videoda yok (Pano düğmelerine biniyordu)
		if (want && (cur == null)) {
			YoldaQualityPill p = new YoldaQualityPill(a);
			ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(-2, -2);
			lp.topToTop = lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.topMargin = YoldaStyle.cutoutTop(a.getContext()) + p.dp(16);
			lp.rightMargin = p.dp(16 + 56 + 10); // sağ üstte ✕ (videoyu kapat) var: onun solunda
			cl.addView(p, lp);
			p.poke();
		} else if (!want && (cur != null)) {
			cl.removeView(cur);
		}
	}

	private static YoldaQualityPill find(ViewGroup g) {
		for (int i = 0; i < g.getChildCount(); i++) {
			if (g.getChildAt(i) instanceof YoldaQualityPill p) return p;
		}
		return null;
	}

	/** Görünür yap, birkaç saniye sonra soluklaştır. */
	private void poke() {
		removeCallbacks(dim);
		animate().alpha(1f).setDuration(150).start();
		postDelayed(dim, 4000);
	}

	private void render() {
		removeAllViews();
		Context ctx = getContext();
		String cur = YoldaStyle.videoQuality(ctx);
		if (!open) {
			TextView b = chip(ctx, "HD " + YoldaStyle.videoQualityLabel(cur) + "  ▾", false);
			b.setContentDescription("Video kalitesi: " + YoldaStyle.videoQualityLabel(cur) + ", değiştir");
			b.setOnClickListener(v -> {
				open = true;
				render();
				poke();
			});
			addView(b, new LayoutParams(-2, dp(48)));
			return;
		}
		for (String q : YoldaStyle.VIDEO_QUALITIES) {
			boolean sel = q.equals(cur);
			TextView b = chip(ctx, YoldaStyle.videoQualityLabel(q), sel);
			b.setContentDescription("Video kalitesi " + YoldaStyle.videoQualityLabel(q) + (sel ? ", seçili" : ""));
			b.setOnClickListener(v -> {
				YoldaStyle.setVideoQuality(ctx, q);
				MediaEngine eng = a.getMediaSessionCallback().getEngine();
				if (eng != null) eng.yoldaVideoQualityChanged();
				YoldaStyle.toast(ctx, "Video kalitesi: " + YoldaStyle.videoQualityLabel(q));
				open = false;
				render();
				poke();
			});
			LayoutParams lp = new LayoutParams(-2, dp(48));
			lp.leftMargin = dp(6);
			addView(b, lp);
		}
	}

	private TextView chip(Context ctx, String s, boolean sel) {
		TextView b = new TextView(ctx);
		b.setText(s);
		b.setTextSize(a.isCarActivity() ? 17 : 14);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		b.setTextColor(sel ? 0xFF111827 : 0xFFFFFFFF);
		b.setIncludeFontPadding(false);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(16), 0, dp(16), 0);
		b.setMinWidth(dp(56));
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(999));
		g.setColor(sel ? 0xFFFFF4D6 : 0xB3101420);
		g.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
		b.setBackground(g);
		b.setClickable(true);
		return b;
	}

	int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
