package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.view.View;

/**
 * Yolda cam (glass) tasarım dili: yarı saydam buzlu yüzeyler, ince parlak kenar, üstte ışık yansıması,
 * zeminde yavaşça süzülen pastel ışık lekeleri. Tüm Yolda ekranları buradaki temaları kullanır.
 */
public final class YoldaStyle {

	public static final String PREFS = "yolda_home";

	/** Tema: zemin, pastel ışıklar, vurgu ve büyük kartın geçişi. */
	public static final class Theme {
		public final String name;
		public final boolean light;
		public final int base, accent, hero1, hero2, ink, muted;
		public final int[] blobs;
		/** Cam yüzey (üst/alt), kenar ve ince bölme rengi. */
		public final int glassTop, glassBottom, edge, surface, stroke;
		/** Önceki sürümlerle uyum: zemin geçişi. */
		public final int bg1, bg2;

		Theme(String name, boolean light, int base, int[] blobs, int accent, int hero1, int hero2) {
			this.name = name;
			this.light = light;
			this.base = base;
			this.blobs = blobs;
			this.accent = accent;
			this.hero1 = hero1;
			this.hero2 = hero2;
			this.ink = light ? 0xFF1C2230 : 0xFFF5F3F7;
			this.muted = light ? 0xFF5E6678 : 0xFFB9B4C7;
			this.glassTop = light ? 0xB3FFFFFF : 0x33FFFFFF;
			this.glassBottom = light ? 0x80FFFFFF : 0x14FFFFFF;
			this.edge = light ? 0xE6FFFFFF : 0x4DFFFFFF;
			this.surface = light ? 0x99FFFFFF : 0x1FFFFFFF;
			this.stroke = edge;
			this.bg1 = base;
			this.bg2 = base;
		}
	}

	public static final Theme[] THEMES = {
			new Theme("Safir", false, 0xFF0E1424,
					new int[]{0xFFB8A9F5, 0xFF8EC5FF, 0xFFF4B6C2}, 0xFFC9BCFF, 0xFFA99BEF, 0xFF7FA8E8),
			new Theme("Şampanya", false, 0xFF19150F,
					new int[]{0xFFF3D9B1, 0xFFF6C1A6, 0xFFC9D8B6}, 0xFFF3D9B1, 0xFFE9C79A, 0xFFC99A7A),
			new Theme("Okyanus", false, 0xFF0A181C,
					new int[]{0xFFA8E6CF, 0xFF8FD3FE, 0xFFC3B1E1}, 0xFFA8E6CF, 0xFF7FCFC0, 0xFF6FA9D6),
			new Theme("Gül kurusu", false, 0xFF1A1317,
					new int[]{0xFFE8B4BC, 0xFFC8A2C8, 0xFFF5E6CC}, 0xFFEDC1C8, 0xFFD99AA7, 0xFFA884B5),
	};

	/** Fermata "Yolda Cam" teması etkinse true: Yolda ekranları kendi zeminini çizmez, kökteki ortak zemin görünür. */
	public static volatile boolean glassActive;

	private static java.lang.ref.WeakReference<View> backdropHost;

	/** Kök görünüme süzülen ışıklı zemini çizer (tüm uygulamanın arkası). */
	public static void applyBackdrop(View root) {
		backdropHost = new java.lang.ref.WeakReference<>(root);
		root.setBackground(new BackdropDrawable(root.getContext(), current(root.getContext())));
	}

	/** Tema değişince ortak zemini yenile. */
	public static void refreshBackdrop(Context ctx) {
		View v = (backdropHost != null) ? backdropHost.get() : null;
		if (v != null) v.setBackground(new BackdropDrawable(ctx, current(ctx)));
	}

	/** Yolda ekranının zemini: ortak zemin varsa saydam, yoksa kendi ışıklı zemini. */
	public static void applyScreenBackground(android.widget.FrameLayout root, Theme t) {
		if (glassActive) root.setBackground(null);
		else root.addView(new Backdrop(root.getContext(), t), 0, new android.widget.FrameLayout.LayoutParams(-1, -1));
	}

	/** {@link Backdrop} ile aynı çizim, arka plan (Drawable) olarak; kendini yavaşça yeniden çizer. */
	public static final class BackdropDrawable extends Drawable implements Runnable {
		private final Theme t;
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final long start = SystemClock.uptimeMillis();

		BackdropDrawable(Context ctx, Theme t) {
			this.t = t;
		}

		@Override
		public void draw(Canvas c) {
			android.graphics.Rect b = getBounds();
			drawLights(c, t, p, b.width(), b.height(), (SystemClock.uptimeMillis() - start) / 1000f);
			scheduleSelf(this, SystemClock.uptimeMillis() + 66); // ~15 fps yeter, pil dostu
		}

		@Override
		public void run() {
			invalidateSelf();
		}

		@Override
		public void setAlpha(int alpha) {
		}

		@Override
		public void setColorFilter(android.graphics.ColorFilter cf) {
		}

		@Override
		public int getOpacity() {
			return android.graphics.PixelFormat.OPAQUE;
		}
	}

	/** Taban rengi ve üç pastel ışık. */
	static void drawLights(Canvas c, Theme t, Paint p, float w, float h, float s) {
		if (w <= 0 || h <= 0) return;
		c.drawColor(t.base);
		float m = Math.max(w, h);
		float[][] pos = {{0.15f, 0.10f, 0.62f}, {0.92f, 0.30f, 0.55f}, {0.40f, 1.00f, 0.70f}};
		for (int i = 0; i < t.blobs.length && i < pos.length; i++) {
			float dx = (float) Math.sin(s * 0.07 + i * 2.1) * 0.06f * w;
			float dy = (float) Math.cos(s * 0.05 + i * 1.7) * 0.05f * h;
			float cx = pos[i][0] * w + dx, cy = pos[i][1] * h + dy, r = pos[i][2] * m;
			int col = t.blobs[i];
			int a = t.light ? 0xCC : 0x70;
			p.setShader(new RadialGradient(cx, cy, r,
					new int[]{Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)),
							Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))},
					null, Shader.TileMode.CLAMP));
			c.drawCircle(cx, cy, r, p);
		}
	}

	private YoldaStyle() {
	}

	public static int index(Context ctx) {
		return Math.floorMod(ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.getInt("theme", 0), THEMES.length);
	}

	public static Theme current(Context ctx) {
		return THEMES[index(ctx)];
	}

	public static void next(Context ctx) {
		ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
				.putInt("theme", Math.floorMod(index(ctx) + 1, THEMES.length)).apply();
	}

	/** Buzlu cam yüzey: üstten alta hafifleyen beyaz, ince parlak kenar, üst yarıda ışık yansıması. */
	public static Drawable glass(Context ctx, Theme t, float radiusDp) {
		float r = dp(ctx, radiusDp);
		GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{t.glassTop, t.glassBottom});
		body.setCornerRadius(r);
		body.setStroke(Math.max(1, Math.round(dp(ctx, 1))), t.edge);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{t.light ? 0x66FFFFFF : 0x1FFFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		return new LayerDrawable(new Drawable[]{body, sheen});
	}

	/** Pastel geçişli büyük kart, üstünde cam parıltısı. */
	public static Drawable hero(Context ctx, Theme t, float radiusDp) {
		float r = dp(ctx, radiusDp);
		GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.hero1, t.hero2});
		body.setCornerRadius(r);
		body.setStroke(Math.max(1, Math.round(dp(ctx, 1))), 0x66FFFFFF);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x40FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		return new LayerDrawable(new Drawable[]{body, sheen});
	}

	/** Pastel ikon dairesi rengi (doygun değil, yumuşak). */
	public static int pastel(int i) {
		int[] c = {0xFFF4A7A7, 0xFFB8A9F5, 0xFF9ED9C5, 0xFF9CC9F5, 0xFFF6CFA0, 0xFFE9B3D6, 0xFFAFC0D9};
		return c[Math.floorMod(i, c.length)];
	}

	/** Büyük kart üzerindeki yazı rengi: pastel zeminde koyu, okunur. */
	public static int onHero(Theme t) {
		return 0xFF1C1A2B;
	}

	static float dp(Context ctx, float v) {
		return v * ctx.getResources().getDisplayMetrics().density;
	}

	/** Zemin: koyu (ya da açık) taban ve üzerinde yavaşça süzülen üç pastel ışık. */
	public static final class Backdrop extends View {
		private final Theme t;
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final long start = SystemClock.uptimeMillis();

		public Backdrop(Context ctx, Theme t) {
			super(ctx);
			this.t = t;
		}

		@Override
		protected void onDraw(Canvas c) {
			drawLights(c, t, p, getWidth(), getHeight(), (SystemClock.uptimeMillis() - start) / 1000f);
			postInvalidateDelayed(66);
		}
	}
}
