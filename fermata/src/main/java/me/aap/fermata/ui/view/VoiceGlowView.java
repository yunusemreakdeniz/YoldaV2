package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sesli asistan (pencere değil, uygulamanın üstünde): ekranın kenarlarında dönen renkli ışık (sese göre
 * kalınlaşır) ve altta cam panel: sese göre oynayan ses dalgası + durum, söylenen cümle (canlı), anlaşılan
 * komutun sonucu ("✓ TRT Haber açılıyor") ve bulunduğun ekrana göre dokunulabilir örnek komutlar.
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
	private final TextView result;
	private final Wave wave;
	private final HorizontalScrollView chipsScroll;
	/** Örnek komuta dokununca (komut olarak çalıştırılır). */
	@Nullable
	public Consumer<String> onSuggestion;
	/** Kapat düğmesi. */
	@Nullable
	public Runnable onCancel;

	public VoiceGlowView(Context ctx, YoldaStyle.Theme t, TextView transcript) {
		this(ctx, t, transcript, null);
	}

	public VoiceGlowView(Context ctx, YoldaStyle.Theme t, TextView transcript, @Nullable List<String> suggestions) {
		super(ctx);
		setWillNotDraw(false);
		setClickable(true);
		// Pastel: temanın renkleri beyaza doğru açılır (keskin beyaz şerit yok)
		colors = new int[]{soft(t.blobs[0]), soft(t.blobs[1]), soft(t.blobs[2]), soft(t.blobs[1]), soft(t.blobs[0])};
		p.setStyle(Paint.Style.STROKE);
		float d = getResources().getDisplayMetrics().density;

		pill = new LinearLayout(ctx);
		pill.setOrientation(LinearLayout.VERTICAL);
		pill.setClickable(true); // panele dokunmak kapatmasın
		pill.setPadding(Math.round(22 * d), Math.round(14 * d), Math.round(16 * d), Math.round(16 * d));
		android.graphics.drawable.GradientDrawable dark = new android.graphics.drawable.GradientDrawable();
		dark.setColor((t.base & 0x00FFFFFF) | 0xCC000000);
		dark.setCornerRadius(30 * d);
		pill.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
				dark, YoldaStyle.glass(ctx, t, 30)}));
		pill.setElevation(8 * d);

		// Üst satır: ses dalgası + durum + kapat
		LinearLayout top = new LinearLayout(ctx);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		wave = new Wave(ctx, t.accent);
		top.addView(wave, new LinearLayout.LayoutParams(Math.round(56 * d), Math.round(26 * d)));
		status = new TextView(ctx);
		status.setText("DİNLİYORUM");
		status.setTextSize(12);
		status.setLetterSpacing(0.16f);
		status.setTextColor(0xD9FFFFFF);
		status.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		status.setPadding(Math.round(10 * d), 0, 0, 0);
		top.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
		ImageView close = new ImageView(ctx);
		close.setImageResource(me.aap.fermata.R.drawable.yi_close);
		close.setImageTintList(android.content.res.ColorStateList.valueOf(0xCCFFFFFF));
		int cp = Math.round(10 * d);
		close.setPadding(cp, cp, cp, cp);
		close.setContentDescription("Sesli asistanı kapat");
		close.setOnClickListener(v -> {
			if (onCancel != null) onCancel.run();
		});
		top.addView(close, new LinearLayout.LayoutParams(Math.round(40 * d), Math.round(40 * d)));
		pill.addView(top);

		// Söylenen (canlı)
		if (transcript.getParent() instanceof android.view.ViewGroup g) g.removeView(transcript);
		transcript.setTextColor(0xFFFFFFFF);
		transcript.setTextSize(24);
		transcript.setGravity(Gravity.START);
		transcript.setMaxLines(3);
		transcript.setEllipsize(TextUtils.TruncateAt.START);
		transcript.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		transcript.setPadding(Math.round(2 * d), Math.round(8 * d), Math.round(6 * d), 0);
		pill.addView(transcript, new LinearLayout.LayoutParams(-1, -2));

		// Sonuç
		result = new TextView(ctx);
		result.setTextColor(t.accent | 0xFF000000);
		result.setTextSize(17);
		result.setTypeface(Typeface.DEFAULT_BOLD);
		result.setPadding(Math.round(2 * d), Math.round(6 * d), 0, 0);
		result.setVisibility(GONE);
		pill.addView(result);

		// Örnek komutlar
		LinearLayout chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		if (suggestions != null) {
			for (String s : suggestions) {
				TextView c = new TextView(ctx);
				c.setText(s);
				c.setTextColor(0xF2FFFFFF);
				c.setTextSize(14);
				c.setGravity(Gravity.CENTER);
				c.setSingleLine(true);
				c.setPadding(Math.round(14 * d), 0, Math.round(14 * d), 0);
				android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
				g.setCornerRadius(999 * d);
				g.setColor(0x26FFFFFF);
				g.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
				c.setBackground(g);
				c.setContentDescription("Söyle: " + s);
				c.setOnClickListener(v -> {
					if (onSuggestion != null) onSuggestion.accept(s);
				});
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Math.round(40 * d));
				lp.rightMargin = Math.round(8 * d);
				chips.addView(c, lp);
			}
		}
		chipsScroll = new HorizontalScrollView(ctx);
		chipsScroll.setHorizontalScrollBarEnabled(false);
		chipsScroll.addView(chips);
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
		clp.topMargin = Math.round(12 * d);
		if (chips.getChildCount() > 0) pill.addView(chipsScroll, clp);

		int maxW = Math.round(Math.min(640 * d, getResources().getDisplayMetrics().widthPixels - 32 * d));
		LayoutParams lp = new LayoutParams(maxW, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		lp.bottomMargin = Math.round(96 * d);
		addView(pill, lp);
		pill.setAlpha(0f);
		pill.setTranslationY(24 * d);
		pill.setScaleX(0.96f);
		pill.setScaleY(0.96f);
		pill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(320)
				.setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
	}

	/** Anlaşılan komutun sonucu: "✓ TRT Haber açılıyor" (örnek komutlar gizlenir). */
	public void showResult(@Nullable String text) {
		status.setText("ANLADIM");
		wave.setDone(true);
		chipsScroll.setVisibility(GONE);
		if ((text == null) || text.isEmpty()) return;
		boolean bad = text.startsWith("Anlayamadım") || text.contains("anlaşılamadı");
		result.setText((bad ? "✕  " : "✓  ") + text);
		if (bad) result.setTextColor(0xFFFCA5A5);
		result.setVisibility(VISIBLE);
		result.setAlpha(0f);
		result.animate().alpha(1f).setDuration(180).start();
	}

	/** Testler için: gösterilen sonuç. */
	public CharSequence resultText() {
		return (result.getVisibility() == VISIBLE) ? result.getText() : "";
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
		wave.setLevel(level);
	}

	public void setThinking(boolean t) {
		thinking = t;
		if (t) {
			level = 0f;
			status.setText("ANLIYORUM…");
			wave.setLevel(0f);
		}
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
		float breath = 0.85f + 0.15f * (float) Math.sin(t * 2.2);
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

	/** Ses dalgası: 7 çubuk, sese göre yükselir; dinlenmiyorken hafif dalgalanır, bitince düz çizgi. */
	private static final class Wave extends View {
		private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final float[] h = new float[7];
		private float level;
		private boolean done;
		private final long start = SystemClock.uptimeMillis();

		Wave(Context ctx, int accent) {
			super(ctx);
			bar.setColor(accent | 0xFF000000);
			bar.setStrokeCap(Paint.Cap.ROUND);
		}

		void setLevel(float l) {
			level = l;
		}

		void setDone(boolean d) {
			done = d;
			invalidate();
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), hh = getHeight();
			if (w <= 0) return;
			float t = (SystemClock.uptimeMillis() - start) / 1000f;
			float step = w / h.length;
			bar.setStrokeWidth(step * 0.5f);
			for (int i = 0; i < h.length; i++) {
				float target = done ? 0.12f : (0.18f + 0.82f * level * (0.55f + 0.45f * (float) Math.abs(Math.sin(t * 7 + i * 1.3))));
				if (!done && (level < 0.05f)) target = 0.16f + 0.1f * (float) Math.abs(Math.sin(t * 2.5 + i * 0.8));
				h[i] += (target - h[i]) * 0.3f;
				float x = step * (i + 0.5f), half = Math.max(step * 0.25f, hh * h[i] / 2f);
				c.drawLine(x, hh / 2f - half, x, hh / 2f + half, bar);
			}
			if (!done) postInvalidateOnAnimation();
		}
	}
}
