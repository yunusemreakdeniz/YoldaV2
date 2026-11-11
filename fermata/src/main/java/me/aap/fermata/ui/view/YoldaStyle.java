package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/**
 * Yolda Liquid Glass tasarım dili (Apple iOS 26 tarzı):
 * - Zemin: koyu taban üzerinde canlı, yavaşça süzülen dört renk ışığı (duvar kâğıdı).
 * - Cam: berrak (koyu değil); sol üstten sağ alta hafifleyen beyaz dolgu, üst yarıda ışık yansıması,
 *   ince parlak kenar. Yazılar beyaz.
 * Tüm Yolda ekranları ve Fermata'nın çerçevesi (alt menü, mini oynatıcı) bunu kullanır.
 */
public final class YoldaStyle {

	public static final String PREFS = "yolda_home";

	public static final class Theme {
		public final String name;
		public final boolean light = false;
		public final int base, accent, hero1, hero2;
		public final int ink = 0xFFFFFFFF, muted = 0xC7FFFFFF;
		/** Dört duvar kâğıdı ışığı. */
		public final int[] blobs;
		public final int glassTop = 0x38FFFFFF, glassBottom = 0x14FFFFFF, edge = 0x59FFFFFF;
		public final int surface = 0x24FFFFFF, stroke = 0x59FFFFFF;
		public final int bg1, bg2;

		Theme(String name, int base, int[] blobs, int accent) {
			this.name = name;
			this.base = base;
			this.blobs = blobs;
			this.accent = accent;
			this.hero1 = blobs[0];
			this.hero2 = blobs[1];
			this.bg1 = base;
			this.bg2 = base;
		}
	}

	public static final Theme[] THEMES = {
			new Theme("Gece", 0xFF0B0E2A, new int[]{0xFF6D5BFF, 0xFF00B8FF, 0xFFFF4FA0, 0xFF2E1A78}, 0xFFFFFFFF),
			new Theme("Gün batımı", 0xFF24110C, new int[]{0xFFFF8A3D, 0xFFFF4F6D, 0xFFFFC857, 0xFF6B1E3F}, 0xFFFFC857),
			new Theme("Okyanus", 0xFF061A24, new int[]{0xFF00C9A7, 0xFF1E90FF, 0xFF7BDFF2, 0xFF0B3B5C}, 0xFF7BDFF2),
			new Theme("Gül", 0xFF220C1F, new int[]{0xFFFF6FB5, 0xFFB26BFF, 0xFFFFB38A, 0xFF5A1446}, 0xFFFFB38A),
	};

	/** Fermata "Yolda Cam" teması etkinse true: Yolda ekranları kendi zeminini çizmez, kökteki ortak zemin görünür. */
	public static volatile boolean glassActive;

	private static WeakReference<View> backdropHost;

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

	static float dp(Context ctx, float v) {
		return v * ctx.getResources().getDisplayMetrics().density;
	}

	// ---- cam yüzeyler ----

	/** Liquid Glass yüzey: berrak dolgu + üst ışık yansıması + ince parlak kenar. */
	public static Drawable glass(Context ctx, Theme t, float radiusDp) {
		return glass(ctx, radiusDp, 0x38FFFFFF, 0x12FFFFFF, 0x24FFFFFF, 0x59FFFFFF);
	}

	/** Alt menü ve mini oynatıcı için biraz daha belirgin cam. */
	public static Drawable pill(Context ctx, Theme t) {
		return glass(ctx, 999, 0x47FFFFFF, 0x1AFFFFFF, 0x2EFFFFFF, 0x73FFFFFF);
	}

	/** Seçili öğe (menüdeki aktif sekme, seçili çip): dolgun cam. */
	public static Drawable selected(Context ctx, float radiusDp) {
		return glass(ctx, radiusDp, 0x5CFFFFFF, 0x33FFFFFF, 0x40FFFFFF, 0x99FFFFFF);
	}

	private static Drawable glass(Context ctx, float radiusDp, int c1, int c2, int c3, int edge) {
		float r = dp(ctx, radiusDp);
		GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2, c3});
		fill.setCornerRadius(r);
		// Üst yarıda yumuşak ışık (camın üst kenarından düşen yansıma)
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		GradientDrawable border = new GradientDrawable();
		border.setColor(0x00000000);
		border.setCornerRadius(r);
		border.setStroke(Math.max(1, Math.round(dp(ctx, 1))), edge);
		return new LayerDrawable(new Drawable[]{fill, sheen, border});
	}

	/** Büyük vurgulu kart: temanın ilk iki ışığının geçişi + cam parıltısı. */
	public static Drawable hero(Context ctx, Theme t, float radiusDp) {
		float r = dp(ctx, radiusDp);
		GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.hero1, t.hero2});
		body.setCornerRadius(r);
		body.setStroke(Math.max(1, Math.round(dp(ctx, 1))), 0x73FFFFFF);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x4DFFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		return new LayerDrawable(new Drawable[]{body, sheen});
	}

	/** Uygulama simgesi zemini (iOS gibi yuvarlatılmış kare): renk geçişi + üst parlama + ince kenar. */
	public static Drawable appIcon(Context ctx, int c1, int c2, float radiusDp) {
		float r = dp(ctx, radiusDp);
		GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
		body.setCornerRadius(r);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x40FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		GradientDrawable border = new GradientDrawable();
		border.setColor(0x00000000);
		border.setCornerRadius(r);
		border.setStroke(Math.max(1, Math.round(dp(ctx, 1))), 0x66FFFFFF);
		return new LayerDrawable(new Drawable[]{body, sheen, border});
	}

	public static int onHero(Theme t) {
		return 0xFFFFFFFF;
	}

	public static int pastel(int i) {
		int[] c = {0xFFF4A7A7, 0xFFB8A9F5, 0xFF9ED9C5, 0xFF9CC9F5, 0xFFF6CFA0, 0xFFE9B3D6, 0xFFAFC0D9};
		return c[Math.floorMod(i, c.length)];
	}

	// ---- zemin ----

	/** Kök görünüme süzülen ışıklı zemini çizer (tüm uygulamanın arkası). */
	public static void applyBackdrop(View root) {
		backdropHost = new WeakReference<>(root);
		root.setBackground(new BackdropDrawable(current(root.getContext())));
	}

	/** Tema değişince ortak zemini yenile. */
	public static void refreshBackdrop(Context ctx) {
		View v = (backdropHost != null) ? backdropHost.get() : null;
		if (v != null) v.setBackground(new BackdropDrawable(current(ctx)));
	}

	/** Yolda ekranının zemini: ortak zemin varsa saydam, yoksa kendi ışıklı zemini. */
	public static void applyScreenBackground(FrameLayout root, Theme t) {
		if (glassActive) root.setBackground(null);
		else root.addView(new Backdrop(root.getContext(), t), 0, new FrameLayout.LayoutParams(-1, -1));
	}

	/** Duvar kâğıdı: taban + dört ışık; ışıklar çok yavaş süzülür. */
	static void drawLights(Canvas c, Theme t, Paint p, float w, float h, float s) {
		if (w <= 0 || h <= 0) return;
		c.drawColor(t.base);
		// Konum (genişlik/yükseklik oranı), yarıçap (genişliğe oranla), saydamlık — tasarım taslağıyla aynı
		float[][] pos = {{0.10f, 0.12f, 0.42f, 0.80f}, {0.88f, 0.18f, 0.44f, 0.73f},
				{0.62f, 0.92f, 0.50f, 0.67f}, {0.20f, 0.95f, 0.55f, 0.93f}};
		for (int i = 0; i < t.blobs.length && i < pos.length; i++) {
			float dx = (float) Math.sin(s * 0.06 + i * 2.1) * 0.05f * w;
			float dy = (float) Math.cos(s * 0.045 + i * 1.7) * 0.05f * h;
			float cx = pos[i][0] * w + dx, cy = pos[i][1] * h + dy, r = pos[i][2] * Math.max(w, h);
			int col = t.blobs[i];
			int a = Math.round(pos[i][3] * 255);
			p.setShader(new RadialGradient(cx, cy, r,
					new int[]{Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)),
							Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))},
					null, Shader.TileMode.CLAMP));
			c.drawCircle(cx, cy, r, p);
		}
	}

	/** Zemin (View olarak): Fermata teması Yolda Cam değilse Yolda ekranları bunu kendi arkasına koyar. */
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

	/** Zemin (arka plan olarak): kendini ~15 fps yeniden çizer; pil dostu. */
	public static final class BackdropDrawable extends Drawable implements Runnable {
		private final Theme t;
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final long start = SystemClock.uptimeMillis();

		BackdropDrawable(Theme t) {
			this.t = t;
		}

		@Override
		public void draw(Canvas c) {
			Rect b = getBounds();
			drawLights(c, t, p, b.width(), b.height(), (SystemClock.uptimeMillis() - start) / 1000f);
			scheduleSelf(this, SystemClock.uptimeMillis() + 66);
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
			return PixelFormat.OPAQUE;
		}
	}
}
