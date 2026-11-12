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
		// Pastel: temanın renkleri beyaza doğru açılır (keskin beyaz şerit yok)
		colors = new int[]{soft(t.blobs[0]), soft(t.blobs[1]), soft(t.blobs[2]), soft(t.blobs[1]), soft(t.blobs[0])};
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
		pill.setTranslationY(18 * d);
		pill.setScaleX(0.96f);
		pill.setScaleY(0.96f);
		pill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(320)
				.setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
	}

	private static int soft(int c) {
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		r += (255 - r) * 35 / 100;
		g += (255 - g) * 35 / 100;
		b += (255 - b) * 35 / 100;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static boolean motionOn() {
		if (android.os.Build.VERSION.SDK_INT < 26) return true;
		return android.animation.ValueAnimator.areAnimatorsEnabled();
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
		shown += (level - shown) * 0.12f; // sese yumuşak tepki
		m.setRotate((t * (thinking ? 90 : 45)) % 360, w / 2f, h / 2f); // yavaş dönüş
		shader.setLocalMatrix(m);
		float rad = 28 * d;
		// nefes: konuşma yokken ışık hafifçe açılıp kapanır
		float breath = 0.85f + 0.15f * (float) Math.sin(t * 2.2);
		// İçten dışa dört kat: çok geniş ve çok soluk ışıma → ince, yumuşak kenar
		float base = (thinking ? 4 : 3) * d + shown * 7 * d;
		int[] alphas = {16, 34, 64, 140};
		float[] widths = {base * 6f, base * 3.4f, base * 1.8f, base * 0.6f};
		for (int i = 0; i < 4; i++) {
			p.setAlpha(Math.round(alphas[i] * breath));
			p.setStrokeWidth(widths[i]);
			float in = widths[i] / 2f;
			r.set(in, in, w - in, h - in);
			c.drawRoundRect(r, rad, rad, p);
		}
		if (motionOn()) postInvalidateOnAnimation();
	}
}
