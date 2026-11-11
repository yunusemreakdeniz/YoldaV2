package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sesli asistan efekti (pencere değil, uygulamanın üstünde): ekranın kenarlarında dönen renkli ışık
 * (sese göre kalınlaşır) ve altta cam şeritte durum + söylenen cümle. Hafif: tek SweepGradient, yalnız
 * matrisi döndürülür; dinleme bitince kendini kaldırır.
 */
public class VoiceGlowView extends FrameLayout {
	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Matrix m = new Matrix();
	private final RectF r = new RectF();
	private final long start = SystemClock.uptimeMillis();
	private final int[] colors;
	private SweepGradient shader;
	private float level, shown;
	private boolean thinking;
	public final TextView status;
	public final LinearLayout pill;

	public VoiceGlowView(Context ctx, YoldaStyle.Theme t, TextView transcript) {
		super(ctx);
		setWillNotDraw(false);
		setClickable(true);
		colors = new int[]{t.blobs[0], t.blobs[1], t.blobs[2], 0xFFFFFFFF, t.blobs[0]};
		p.setStyle(Paint.Style.STROKE);
		float d = getResources().getDisplayMetrics().density;

		pill = new LinearLayout(ctx);
		pill.setOrientation(LinearLayout.VERTICAL);
		pill.setGravity(Gravity.CENTER_HORIZONTAL);
		pill.setPadding(Math.round(28 * d), Math.round(14 * d), Math.round(28 * d), Math.round(16 * d));
		android.graphics.drawable.GradientDrawable dark = new android.graphics.drawable.GradientDrawable();
		dark.setColor((t.base & 0x00FFFFFF) | 0x99000000);
		dark.setCornerRadius(999 * d);
		pill.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
				dark, YoldaStyle.pill(ctx, t)}));

		status = new TextView(ctx);
		status.setText("Dinliyorum…");
		status.setTextSize(13);
		status.setLetterSpacing(0.12f);
		status.setTextColor(0xD9FFFFFF);
		status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
		pill.addView(status);
		if (transcript.getParent() instanceof android.view.ViewGroup g) g.removeView(transcript);
		transcript.setTextColor(0xFFFFFFFF);
		transcript.setTextSize(22);
		transcript.setGravity(Gravity.CENTER);
		transcript.setMaxLines(2);
		transcript.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
		pill.addView(transcript, new LinearLayout.LayoutParams(-2, -2));

		LayoutParams lp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		lp.bottomMargin = Math.round(120 * d);
		lp.leftMargin = lp.rightMargin = Math.round(40 * d);
		addView(pill, lp);
		pill.setAlpha(0f);
		pill.setTranslationY(24 * d);
		pill.animate().alpha(1f).translationY(0f).setDuration(220).start();
	}

	public void setLevel(float l) {
		level = Math.max(0f, Math.min(1f, l));
	}

	public void setThinking(boolean t) {
		thinking = t;
		if (t) level = 0f;
	}

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);
		shader = new SweepGradient(w / 2f, h / 2f, colors, null);
		p.setShader(shader);
	}

	@Override
	protected void onDraw(Canvas c) {
		super.onDraw(c);
		float w = getWidth(), h = getHeight();
		if (shader == null || w <= 0) return;
		float d = getResources().getDisplayMetrics().density;
		float t = (SystemClock.uptimeMillis() - start) / 1000f;
		shown += (level - shown) * 0.25f;
		m.setRotate((t * (thinking ? 220 : 110)) % 360, w / 2f, h / 2f);
		shader.setLocalMatrix(m);
		float rad = 26 * d;
		// İçten dışa üç kat: geniş ve soluk (parıltı) → ince ve parlak (kenar)
		float base = (thinking ? 5 : 4) * d + shown * 10 * d;
		int[] alphas = {50, 95, 255};
		float[] widths = {base * 3.2f, base * 1.8f, base * 0.8f};
		for (int i = 0; i < 3; i++) {
			p.setAlpha(alphas[i]);
			p.setStrokeWidth(widths[i]);
			float in = widths[i] / 2f;
			r.set(in, in, w - in, h - in);
			c.drawRoundRect(r, rad, rad, p);
		}
		postInvalidateOnAnimation();
	}
}
