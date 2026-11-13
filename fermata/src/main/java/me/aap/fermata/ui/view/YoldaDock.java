package me.aap.fermata.ui.view;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Sol alttaki yüzen düğmelerin (uyarı göstergesi, geri, ana sayfa, sesli komut) ortak cam kapsülü. Düğmelerin
 * yeri değişmez; bu görünüm arkalarında, görünen düğmeleri saran yuvarlak bir cam çizer. Düğme eklenip
 * çıkınca (uyarı gelince) kapsül yumuşakça uzar / kısalır; uyarıda kenarı uyarının renginde nabız atar.
 * Dokunmaları almaz (altındaki ekrana geçer).
 */
@SuppressLint("ViewConstructor")
public class YoldaDock extends View {
	private final RectF cur = new RectF(), from = new RectF(), to = new RectF();
	private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float d;
	private ValueAnimator move;
	private ValueAnimator pulse;
	private float pulseA = 1f;
	private int alertColor;
	private boolean shown;
	@Nullable
	private Drawable glass;
	private int glassTheme = -1, glassW = -1;

	public YoldaDock(Context ctx) {
		super(ctx);
		d = ctx.getResources().getDisplayMetrics().density;
		setClickable(false);
		setFocusable(false);
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
		setOutlineProvider(null); // tam ekran görünüm: gölgesi olmasın
		edge.setStyle(Paint.Style.STROKE);
		setWillNotDraw(false);
	}

	/** Kapsülün saracağı alan (ebeveyne göre); null: gizli. alertColor 0: uyarı yok. */
	public void setTarget(@Nullable RectF r, int alertColor) {
		if (alertColor != this.alertColor) {
			this.alertColor = alertColor;
			updatePulse();
			invalidate();
		}
		if (r == null) {
			if (shown) {
				shown = false;
				invalidate();
			}
			return;
		}
		if (shown && r.equals(to)) return;
		boolean first = !shown || cur.isEmpty();
		shown = true;
		to.set(r);
		if (move != null) move.cancel();
		if (first) {
			cur.set(r);
			invalidate();
			return;
		}
		from.set(cur);
		move = ValueAnimator.ofFloat(0f, 1f);
		move.setDuration(240);
		move.setInterpolator(new DecelerateInterpolator());
		move.addUpdateListener(a -> {
			float f = (float) a.getAnimatedValue();
			cur.set(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f,
					from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f);
			invalidate();
		});
		move.start();
	}

	public boolean isShownDock() {
		return shown;
	}

	public RectF bounds() {
		return new RectF(cur);
	}

	public int alertColor() {
		return alertColor;
	}

	private void updatePulse() {
		if ((alertColor != 0) && (pulse == null)) {
			pulse = ValueAnimator.ofFloat(1f, 0.35f);
			pulse.setDuration(650);
			pulse.setRepeatMode(ValueAnimator.REVERSE);
			pulse.setRepeatCount(ValueAnimator.INFINITE);
			pulse.addUpdateListener(a -> {
				pulseA = (float) a.getAnimatedValue();
				invalidate();
			});
			pulse.start();
		} else if ((alertColor == 0) && (pulse != null)) {
			pulse.cancel();
			pulse = null;
			pulseA = 1f;
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		if (pulse != null) pulse.cancel();
		pulse = null;
		if (move != null) move.cancel();
		super.onDetachedFromWindow();
	}

	@Override
	protected void onDraw(Canvas c) {
		if (!shown || cur.isEmpty()) return;
		float r = cur.width() / 2f;
		int ti = YoldaStyle.index(getContext());
		if ((glass == null) || (ti != glassTheme) || (Math.round(cur.width()) != glassW)) {
			glassTheme = ti;
			glassW = Math.round(cur.width());
			glass = YoldaStyle.glass(getContext(), YoldaStyle.current(getContext()), cur.width() / 2f / d);
		}
		glass.setBounds(Math.round(cur.left), Math.round(cur.top), Math.round(cur.right), Math.round(cur.bottom));
		glass.draw(c);
		if (alertColor != 0) {
			edge.setStrokeWidth(2.5f * d);
			edge.setColor(alertColor);
			edge.setAlpha(Math.round(255 * pulseA));
			float h = edge.getStrokeWidth() / 2f;
			c.drawRoundRect(cur.left + h, cur.top + h, cur.right - h, cur.bottom - h, r, r, edge);
		}
	}
}
