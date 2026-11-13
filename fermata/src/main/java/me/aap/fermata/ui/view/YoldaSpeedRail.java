package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import me.aap.fermata.R;
import me.aap.fermata.ui.fragment.YoldaRadar;
import me.aap.fermata.ui.fragment.YoldaRadarCore;

/**
 * Sol kenar: hız sınırı levhası (kırmızı çember), anlık hız ve öndeki radarın uzaklığı. Yalnız radar
 * uyarıları açıkken ve konum tazeyken görünür; dokununca Radar ekranı açılır.
 */
@SuppressLint("ViewConstructor")
public class YoldaSpeedRail extends LinearLayout {
	private final TextView sign;
	private final TextView speed;
	private final LinearLayout radar;
	private final TextView radarText;
	private final Runnable listener = this::update;
	private boolean allowed = true;

	public YoldaSpeedRail(Context ctx) {
		super(ctx);
		setOrientation(VERTICAL);
		setGravity(Gravity.CENTER_HORIZONTAL);
		int p = dp(6);
		setPadding(p, dp(8), p, dp(8));
		setContentDescription("Hız ve radar");
		setClickable(true);
		setElevation(dp(12));

		sign = new TextView(ctx);
		sign.setGravity(Gravity.CENTER);
		sign.setTypeface(Typeface.DEFAULT_BOLD);
		sign.setTextColor(0xFF111111);
		sign.setIncludeFontPadding(false);
		GradientDrawable sd = new GradientDrawable();
		sd.setShape(GradientDrawable.OVAL);
		sd.setColor(0xFFFFFFFF);
		sd.setStroke(dp(5), 0xFFDC2626);
		sign.setBackground(sd);
		addView(sign, new LayoutParams(dp(46), dp(46)));

		speed = new TextView(ctx);
		speed.setGravity(Gravity.CENTER);
		speed.setTypeface(Typeface.DEFAULT_BOLD);
		speed.setIncludeFontPadding(false);
		speed.setTextColor(0xFFFFFFFF);
		LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		slp.topMargin = dp(8);
		addView(speed, slp);

		radar = new LinearLayout(ctx);
		radar.setOrientation(VERTICAL);
		radar.setGravity(Gravity.CENTER);
		radar.setPadding(dp(4), dp(6), dp(4), dp(6));
		GradientDrawable rd = new GradientDrawable();
		rd.setCornerRadius(dp(14));
		rd.setColor(0xFFF97316);
		radar.setBackground(rd);
		ImageView ic = new ImageView(ctx);
		ic.setImageResource(R.drawable.yi_radar);
		ic.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		radar.addView(ic, new LayoutParams(dp(22), dp(22)));
		radarText = new TextView(ctx);
		radarText.setTextColor(0xFFFFFFFF);
		radarText.setTypeface(Typeface.DEFAULT_BOLD);
		radarText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		radarText.setGravity(Gravity.CENTER);
		radar.addView(radarText);
		LayoutParams rlp = new LayoutParams(dp(48), LayoutParams.WRAP_CONTENT);
		rlp.topMargin = dp(8);
		addView(radar, rlp);
		setVisibility(GONE);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		YoldaRadar.addListener(listener);
		update();
	}

	@Override
	protected void onDetachedFromWindow() {
		YoldaRadar.removeListener(listener);
		super.onDetachedFromWindow();
	}

	/** Tam ekran video, küçük pencere, oynatıcı ekranı gibi durumlarda gizli kalsın. */
	public void setAllowed(boolean allowed) {
		if (this.allowed == allowed) return;
		this.allowed = allowed;
		update();
	}

	public void update() {
		Context ctx = getContext();
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		setBackground(YoldaStyle.glass(ctx, t, 22));
		boolean on = allowed && YoldaRadar.isEnabled(ctx) && YoldaRadar.hasFix();
		YoldaRadarCore.Ahead a = YoldaRadar.ahead;
		int lim = YoldaRadar.limit;
		int sp = YoldaRadar.speed;
		boolean show = on && ((lim > 0) || (sp >= 3) || (a != null));
		if (!show) {
			if (getVisibility() != GONE) setVisibility(GONE);
			return;
		}
		if (lim > 0) {
			sign.setVisibility(VISIBLE);
			sign.setText(String.valueOf(lim));
			sign.setTextSize(TypedValue.COMPLEX_UNIT_SP, (lim >= 100) ? 15 : 18);
			sign.setAlpha(YoldaRadar.limitEstimated ? 0.75f : 1f); // tahmini sınır (etiketsiz yol) soluk
		} else {
			sign.setVisibility(GONE);
		}
		boolean over = (lim > 0) && (sp > lim);
		// Kısa ekranda (araç) levha ve radar yetsin: hız aracın göstergesinde zaten var (aşımda yine görünür)
		android.view.View parent = (android.view.View) getParent();
		boolean compact = (parent != null) && (parent.getHeight() > 0) && (parent.getHeight() < dp(470));
		if ((sp >= 0) && (!compact || (lim == 0) || over)) {
			speed.setVisibility(VISIBLE);
			speed.setText(sp + "\nkm/s");
			speed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
			speed.setTextColor(YoldaRadarCore.overSpeed(sp, lim) ? 0xFFEF4444 : over ? 0xFFFBBF24 : t.ink);
		} else {
			speed.setVisibility(GONE);
		}
		if (a != null) {
			radar.setVisibility(VISIBLE);
			radarText.setText(a.meters() >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f\nkm", a.meters() / 1000f)
					: (a.meters() + "\nm"));
			((GradientDrawable) radar.getBackground()).setColor((a.meters() <= 300) ? 0xFFDC2626 : 0xFFF97316);
		} else {
			radar.setVisibility(GONE);
		}
		StringBuilder d = new StringBuilder("Hız ve radar");
		if (lim > 0) d.append(", sınır ").append(lim);
		if (sp >= 0) d.append(", hız ").append(sp);
		if (a != null) d.append(", radar ").append(a.meters()).append(" metre");
		setContentDescription(d);
		if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
