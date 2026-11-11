package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Yolda sesli asistan küresi: dinlerken nefes alır gibi atar, konuştukça sesin şiddetine göre büyür,
 * çevresinden dalgalar yayılır. {@link #setLevel(float)} ile 0..1 ses düzeyi verilir.
 */
public class VoiceOrbView extends View {
	private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final long start = SystemClock.uptimeMillis();
	private int c1 = 0xFF22D3EE, c2 = 0xFF6366F1, c3 = 0xFF7C3AED;
	private float level;
	private float shown;
	private boolean thinking;

	public VoiceOrbView(Context ctx) {
		super(ctx);
		ring.setStyle(Paint.Style.STROKE);
		shine.setColor(0x55FFFFFF);
	}

	/** Ses düzeyi 0..1. */
	public void setLevel(float l) {
		level = Math.max(0f, Math.min(1f, l));
	}

	/** Konuşma bitti, sonuç bekleniyor: daha hızlı, dönen bir atım. */
	public void setThinking(boolean thinking) {
		this.thinking = thinking;
		if (thinking) level = 0f;
	}

	public void setColors(int c1, int c2, int c3) {
		this.c1 = c1;
		this.c2 = c2;
		this.c3 = c3;
	}

	@Override
	protected void onDraw(Canvas c) {
		super.onDraw(c);
		float w = getWidth(), h = getHeight();
		if (w <= 0 || h <= 0) return;
		float t = (SystemClock.uptimeMillis() - start) / 1000f;
		shown += (level - shown) * 0.22f;
		float cx = w / 2f, cy = h / 2f;
		float r = Math.min(w, h) / 2f * 0.46f;
		float d = getResources().getDisplayMetrics().density;

		// Yayılan dalgalar
		ring.setStrokeWidth(3f * d);
		float speed = thinking ? 1.4f : 0.55f;
		for (int i = 0; i < 3; i++) {
			float ph = (t * speed + i / 3f) % 1f;
			float rr = r * (1f + ph * (0.75f + shown * 0.5f));
			int a = (int) ((1f - ph) * (110 + shown * 120));
			ring.setColor(Color.argb(Math.min(255, a), Color.red(c1), Color.green(c1), Color.blue(c1)));
			c.drawCircle(cx, cy, rr, ring);
		}

		// Çekirdek: nefes + ses düzeyi
		float breath = (float) Math.sin(t * (thinking ? 7f : 2.6f)) * 0.04f;
		float cr = r * (0.92f + breath + shown * 0.38f);
		core.setShader(new RadialGradient(cx - cr * 0.25f, cy - cr * 0.35f, cr * 1.45f,
				new int[]{c1, c2, c3}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
		c.drawCircle(cx, cy, cr, core);
		c.drawCircle(cx - cr * 0.32f, cy - cr * 0.38f, cr * 0.22f, shine);

		postInvalidateOnAnimation();
	}
}
