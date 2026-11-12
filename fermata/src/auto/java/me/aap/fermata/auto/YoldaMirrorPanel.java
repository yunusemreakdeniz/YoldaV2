package me.aap.fermata.auto;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.SurfaceContainer;

import me.aap.fermata.ui.fragment.YoldaLog;

/**
 * Yolda: telefonun ekranı Yolda'nın araç ekranının içinde (ayrı Android Auto uygulaması açmadan). Önceden
 * "Yolda Telefon" ayrı bir araç uygulaması olarak açılmaya çalışılıyordu; bazı araçlarda hiçbir şey olmuyordu.
 * Görüntü {@link MirrorDisplay} ile gelir, dokunuşlar telefona iletilir. Üstteki kapsül: Yolda'ya dön, telefonun
 * ana ekranı, telefonda geri.
 */
@SuppressLint("ViewConstructor")
public final class YoldaMirrorPanel extends FrameLayout implements SurfaceHolder.Callback {
	private final Runnable onClose;
	private final LinearLayout bar;
	private final Runnable hideBar = () -> setBarShown(false);
	@Nullable
	private MirrorDisplay md;
	@Nullable
	private SurfaceContainer sc;

	public YoldaMirrorPanel(Context ctx, Runnable onClose) {
		super(ctx);
		this.onClose = onClose;
		setBackgroundColor(Color.BLACK);
		setClickable(true);
		setFocusable(true);
		md = MirrorDisplay.get();

		SurfaceView s = new SurfaceView(ctx);
		s.getHolder().addCallback(this);
		addView(s, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

		bar = new LinearLayout(ctx);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		int p = toIntPx(ctx, 6);
		bar.setPadding(p, p, p, p);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xCC10131A);
		bg.setStroke(toIntPx(ctx, 1), 0x55FFFFFF);
		bg.setCornerRadius(toIntPx(ctx, 30));
		bar.setBackground(bg);
		bar.setElevation(toIntPx(ctx, 8));
		bar.addView(button(ctx, "⌂  Yolda", v -> close()));
		bar.addView(button(ctx, "Ana ekran", v -> MirrorActivity.onHomeButtonClick()));
		bar.addView(button(ctx, "‹ Geri", v -> MirrorActivity.onBackButtonClick()));
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.START);
		lp.leftMargin = lp.topMargin = toIntPx(ctx, 12);
		addView(bar, lp);
		setBarShown(true);
		YoldaLog.add("Telefon ekranı Yolda içinde açıldı");
	}

	private static TextView button(Context ctx, String text, View.OnClickListener l) {
		TextView t = new TextView(ctx);
		t.setText(text);
		t.setTextColor(Color.WHITE);
		t.setTextSize(17);
		t.setGravity(Gravity.CENTER);
		int h = toIntPx(ctx, 48);
		t.setMinHeight(h);
		t.setPadding(toIntPx(ctx, 18), 0, toIntPx(ctx, 18), 0);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0x33FFFFFF);
		bg.setCornerRadius(h / 2f);
		t.setBackground(bg);
		t.setOnClickListener(l);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, h);
		lp.leftMargin = lp.rightMargin = toIntPx(ctx, 4);
		t.setLayoutParams(lp);
		return t;
	}

	private void setBarShown(boolean shown) {
		bar.animate().cancel();
		bar.animate().alpha(shown ? 1f : 0.25f).setDuration(200).start();
		removeCallbacks(hideBar);
		if (shown) postDelayed(hideBar, 5000);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (e.getActionMasked() == MotionEvent.ACTION_DOWN) setBarShown(true);
		MirrorDisplay d = md;
		if (d == null) return true;
		if (sc != null) d.setSurface(sc);
		d.motionEvent(e);
		return true;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		// Kapsülün üstündeki dokunuşlar düğmelere; gerisi telefona
		return false;
	}

	/** Paneli kapatır, ekran paylaşımını bitirir; Yolda'nın kart destesi geri gelir. */
	public void close() {
		release();
		ViewGroup p = (ViewGroup) getParent();
		if (p != null) p.removeView(this);
		YoldaLog.add("Telefon ekranı kapatıldı");
		onClose.run();
	}

	private void release() {
		removeCallbacks(hideBar);
		MirrorDisplay d = md;
		if (d == null) return;
		md = null;
		if (sc != null) d.releaseSurface(sc);
		sc = null;
		d.release();
	}

	@Override
	protected void onDetachedFromWindow() {
		release();
		super.onDetachedFromWindow();
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder h) {
		var r = h.getSurfaceFrame();
		setSurface(h, r.width(), r.height());
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder h, int format, int width, int height) {
		setSurface(h, width, height);
	}

	private void setSurface(SurfaceHolder h, int w, int hgt) {
		if ((md == null) || (w <= 0) || (hgt <= 0)) return;
		sc = new SurfaceContainer(h.getSurface(), w, hgt, getResources().getDisplayMetrics().densityDpi);
		md.setSurface(sc);
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		if ((sc != null) && (md != null)) md.releaseSurface(sc);
		sc = null;
	}
}
