package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaLog;

/**
 * Yatay hızlı kaydırma ile önceki / sonraki: sola kaydır → sonraki, sağa kaydır → önceki (şarkı, YouTube
 * videosu, TV kanalı). Dikey kaydırma, dokunma ve çok parmaklı hareketler etkilenmez; ilerleme çubuğu ya da
 * yatay şerit üzerinde başlayan sürükleme sayılmaz.
 */
public final class YoldaSwipe {
	/** Testler için: son kaydırmanın yönü ve zamanı. */
	public static volatile int lastDir;
	public static volatile long lastAt;

	private final float minPx;
	private float x0, y0;
	private long t0;
	private boolean active;

	public YoldaSwipe(Context ctx) {
		minPx = 80 * ctx.getResources().getDisplayMetrics().density;
	}

	/** Dokunma olayını izler: hareket hızlı ve yatay bir kaydırmaysa bitişte +1 (sonraki) / -1 (önceki), yoksa 0. */
	public int feed(MotionEvent e) {
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				x0 = e.getRawX();
				y0 = e.getRawY();
				t0 = e.getEventTime();
				active = true;
			}
			case MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> active = false;
			case MotionEvent.ACTION_UP -> {
				if (!active) return 0;
				active = false;
				float dx = e.getRawX() - x0;
				float dy = e.getRawY() - y0;
				long dt = e.getEventTime() - t0;
				if ((Math.abs(dx) >= minPx) && (Math.abs(dx) > 1.8f * Math.abs(dy)) && (dt <= 800)) return (dx < 0) ? 1 : -1;
			}
			default -> {
			}
		}
		return 0;
	}

	/** Bu dokunuş izlenmesin (ör. ilerleme çubuğunda başladı). */
	public void cancel() {
		active = false;
	}

	/** Noktanın altında kendi yatay hareketi olan bir öğe var mı (ilerleme çubuğu, yatay şerit). */
	public static boolean onHorizontalControl(View root, float rawX, float rawY) {
		if ((root.getVisibility() != View.VISIBLE) || !hit(root, rawX, rawY)) return false;
		if ((root instanceof YoldaSeekLine) || (root instanceof SeekBar) || (root instanceof HorizontalScrollView)
				|| root.getClass().getName().contains("RecyclerView")) return true;
		if (root instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				if (onHorizontalControl(g.getChildAt(i), rawX, rawY)) return true;
			}
		}
		return false;
	}

	private static boolean hit(View v, float x, float y) {
		int[] l = new int[2];
		v.getLocationOnScreen(l);
		return (x >= l[0]) && (x < l[0] + v.getWidth() * v.getScaleX()) && (y >= l[1]) && (y < l[1] + v.getHeight() * v.getScaleY());
	}

	/** Önceki / sonraki: Yolda Müzik sırası, TV kanal listesi ya da YouTube'un sıradaki videosu. */
	public static void skip(MainActivityDelegate a, int dir, @Nullable ViewGroup host) {
		lastDir = dir;
		lastAt = android.os.SystemClock.elapsedRealtime();
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (dir > 0) cb.onSkipToNext();
		else cb.onSkipToPrevious();
		YoldaLog.add("Kaydırma: " + ((dir > 0) ? "sonraki" : "önceki"));
		if (host != null) flash(host, (dir > 0) ? "Sonraki  ›" : "‹  Önceki");
	}

	/** Ekranın ortasında kısa süre görünen işaret ("Sonraki ›"). */
	public static void flash(ViewGroup host, String text) {
		Context ctx = host.getContext();
		float d = ctx.getResources().getDisplayMetrics().density;
		TextView t = new TextView(ctx);
		t.setText(text);
		t.setTextSize(20);
		t.setTextColor(0xFFFFFFFF);
		t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		t.setGravity(Gravity.CENTER);
		t.setPadding(Math.round(24 * d), Math.round(12 * d), Math.round(24 * d), Math.round(12 * d));
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(999 * d);
		g.setColor(0xB3000000);
		t.setBackground(g);
		t.setElevation(20 * d);
		ViewGroup.LayoutParams lp = (host instanceof FrameLayout)
				? new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER) : new ViewGroup.LayoutParams(-2, -2);
		try {
			host.addView(t, lp);
		} catch (Throwable ex) {
			return;
		}
		t.setAlpha(0f);
		t.animate().alpha(1f).setDuration(120).withEndAction(() ->
				t.animate().alpha(0f).setStartDelay(650).setDuration(250).withEndAction(() -> host.removeView(t)).start()).start();
	}
}
