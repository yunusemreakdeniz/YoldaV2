package me.aap.fermata.ui.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Yolda V2 arka planı: uygulamanın en dış katmanına (kenar payları dahil tüm ekran) çizilen zemin ve
 * ortadaki kartın renginde ışıltılar. Kart destesi renkleri bildirir; renk yumuşakça geçer, ışıklar yavaşça
 * süzülür (araçta ve animasyonlar kapalıyken sabit).
 */
public final class YoldaGlow extends Drawable {
	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int base;
	private int c1, c2, t1, t2;
	private final long start = SystemClock.uptimeMillis();
	private boolean drift = true;

	public YoldaGlow(YoldaStyle.Theme t) {
		base = t.base | 0xFF000000;
		c1 = t1 = t.blobs[0];
		c2 = t2 = t.blobs[2];
	}

	/** Etkinliğin kökündeki ışıltı (yoksa null). */
	@Nullable
	public static YoldaGlow of(@Nullable View root) {
		return ((root != null) && (root.getBackground() instanceof YoldaGlow g)) ? g : null;
	}

	public void setColors(int a, int b) {
		if ((a == t1) && (b == t2)) return;
		t1 = a;
		t2 = b;
		invalidateSelf();
	}

	public int[] colors() {
		return new int[]{t1, t2};
	}

	public void setDrift(boolean d) {
		if (drift == d) return;
		drift = d;
		invalidateSelf();
	}

	private static boolean motionOn() {
		if (android.os.Build.VERSION.SDK_INT < 26) return true;
		return android.animation.ValueAnimator.areAnimatorsEnabled();
	}

	private static int towards(int from, int to, float k) {
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	@Override
	public void draw(@NonNull Canvas c) {
		Rect bd = getBounds();
		float w = bd.width(), h = bd.height();
		if ((w <= 0) || (h <= 0)) return;
		c.drawColor(base);
		boolean motion = motionOn();
		boolean anim = drift && motion;
		c1 = motion ? towards(c1, t1, 0.12f) : t1;
		c2 = motion ? towards(c2, t2, 0.12f) : t2;
		float s = anim ? (SystemClock.uptimeMillis() - start) / 1000f : 0f;
		float r = Math.max(w, h) * 0.62f;
		blob(c, c1, bd.left + w * (0.25f + 0.08f * (float) Math.sin(s * 0.21)),
				bd.top + h * (0.30f + 0.10f * (float) Math.cos(s * 0.17)), r, 0x99);
		blob(c, c2, bd.left + w * (0.78f + 0.07f * (float) Math.cos(s * 0.19)),
				bd.top + h * (0.72f + 0.09f * (float) Math.sin(s * 0.23)), r, 0x88);
		blob(c, towards(c1, 0xFFFFFFFF, 0.25f), bd.left + w * (0.55f + 0.12f * (float) Math.sin(s * 0.13)),
				bd.top + h * 0.10f, r * 0.55f, 0x33);
		boolean settling = motion && ((c1 != t1) || (c2 != t2));
		if (anim || settling) invalidateSelf();
	}

	private void blob(Canvas c, int color, float x, float y, float r, int alpha) {
		p.setShader(new RadialGradient(x, y, r, new int[]{(color & 0x00FFFFFF) | (alpha << 24),
				(color & 0x00FFFFFF) | ((alpha / 3) << 24), color & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f},
				Shader.TileMode.CLAMP));
		c.drawCircle(x, y, r, p);
	}

	@Override
	public void setAlpha(int alpha) {
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter cf) {
	}

	@Override
	public int getOpacity() {
		return PixelFormat.OPAQUE;
	}
}
