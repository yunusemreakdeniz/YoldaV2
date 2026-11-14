package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import me.aap.fermata.R;

/**
 * Kapsüldeki küçük oynatıcı (yatay ekranda oynatıcı çubuğu buraya iner): çalanın kapağı yuvarlak, çevresinde
 * temanın renginde halka; çalarken halkanın parlak yayı döner. Dokununca çubuk açılır, basılı tutunca kapanır.
 */
@SuppressLint("ViewConstructor")
public class YoldaMiniOrb extends FrameLayout {
	private final ImageView cover;
	private final ImageView ph;
	private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF oval = new RectF();
	private final float d;
	private boolean playing;
	private boolean expanded;

	/** Çubuk açık mı (halka tam yanar; dokununca kapanır). */
	public void setExpanded(boolean e) {
		if (expanded == e) return;
		expanded = e;
		setContentDescription(e ? "Çalan: oynatıcıyı küçült" : "Çalan: oynatıcıyı aç");
		invalidate();
	}

	public boolean isExpanded() {
		return expanded;
	}
	private float angle;
	private final Runnable spin = new Runnable() {
		@Override
		public void run() {
			if (!isAttachedToWindow() || !playing) return;
			angle = (angle + 6) % 360;
			invalidate();
			postDelayed(this, 50);
		}
	};

	public YoldaMiniOrb(Context ctx) {
		super(ctx);
		d = ctx.getResources().getDisplayMetrics().density;
		setWillNotDraw(false);
		setClickable(true);
		setFocusable(true);
		setContentDescription("Çalan: oynatıcıyı aç");
		int in = Math.round(5 * d);
		FrameLayout clip = new FrameLayout(ctx);
		clip.setClipToOutline(true);
		clip.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(android.view.View v, android.graphics.Outline o) {
				o.setOval(0, 0, v.getWidth(), v.getHeight());
			}
		});
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{t.blobs[0], t.blobs[2]});
		bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		clip.setBackground(bg);
		ph = new ImageView(ctx);
		ph.setImageResource(R.drawable.yi_music);
		ph.setColorFilter(0xE6FFFFFF);
		int pp = Math.round(11 * d);
		ph.setPadding(pp, pp, pp, pp);
		clip.addView(ph, new LayoutParams(-1, -1));
		cover = new ImageView(ctx);
		cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
		clip.addView(cover, new LayoutParams(-1, -1));
		LayoutParams lp = new LayoutParams(-1, -1, Gravity.CENTER);
		lp.setMargins(in, in, in, in);
		addView(clip, lp);
		ring.setStyle(Paint.Style.STROKE);
		ring.setStrokeWidth(2.5f * d);
		arc.setStyle(Paint.Style.STROKE);
		arc.setStrokeWidth(3f * d);
		arc.setStrokeCap(Paint.Cap.ROUND);
		setElevation(12 * d);
		setOnTouchListener((v, e) -> { // basınca hafif küçülme
			if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
				v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
			else if ((e.getActionMasked() == android.view.MotionEvent.ACTION_UP)
					|| (e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL))
				v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
			return false;
		});
	}

	/** Kapak (yoksa nota simgesi) ve çalıyor mu. */
	public void set(@Nullable Drawable art, boolean playing) {
		if (art != null) {
			if (cover.getDrawable() != art) cover.setImageDrawable(art);
			cover.setVisibility(VISIBLE);
		} else {
			cover.setImageDrawable(null);
			cover.setVisibility(GONE);
		}
		if (this.playing != playing) {
			this.playing = playing;
			removeCallbacks(spin);
			if (playing) post(spin);
			invalidate();
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(spin);
		super.onDetachedFromWindow();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (playing) post(spin);
	}

	@Override
	protected void dispatchDraw(Canvas c) {
		super.dispatchDraw(c);
		int accent = YoldaStyle.current(getContext()).accent;
		float h = 1.5f * d;
		oval.set(h, h, getWidth() - h, getHeight() - h);
		ring.setColor(expanded ? (accent | 0xFF000000) : ((accent & 0x00FFFFFF) | 0x66000000));
		ring.setStrokeWidth((expanded ? 3.5f : 2.5f) * d);
		c.drawOval(oval, ring);
		if (playing) {
			arc.setColor(accent | 0xFF000000);
			c.drawArc(oval, angle, 80, false, arc);
		}
	}
}
