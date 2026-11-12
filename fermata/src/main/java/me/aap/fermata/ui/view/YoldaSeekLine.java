package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Yolda ilerleme çubuğu: kalın, yuvarlak uçlu, tutamaklı. Dokunma alanı görünen çizgiden çok daha geniş
 * (görünüm yüksekliğinin tamamı); sürüklerken çizgi kalınlaşır, tutamak büyür. Sürüklerken {@link Listener#onDrag}
 * (gidilecek yeri göstermek için), bırakınca {@link Listener#onSeek} çağrılır.
 */
public class YoldaSeekLine extends View {
	public interface Listener {
		void onDrag(float frac);

		void onSeek(float frac);
	}

	private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
	private float frac;
	private boolean dragging;
	private boolean seekable = true;
	/** Çizginin dikey konumu: 0.5 ortada, 1'e yakın altta. */
	private float lineY = 0.5f;
	private float inset;
	private float thin = 6;
	@Nullable
	private Listener listener;

	public YoldaSeekLine(Context ctx) {
		super(ctx);
		float d = getResources().getDisplayMetrics().density;
		bg.setColor(0x47FFFFFF);
		fg.setColor(0xFFFFFFFF);
		knob.setColor(0xFFFFFFFF);
		// gölge yok: yazılımla çizim katmanı gerektiriyordu (akıcılık)
		thin = 6 * d;
		inset = 8 * d;
		setContentDescription("İlerleme");
	}

	public void setListener(@Nullable Listener l) {
		listener = l;
	}

	/** Çizginin konumu ve kenar boşluğu (oynatıcı çubuğunda alt kenara oturur). */
	public void setLine(float yFrac, float insetPx, float thicknessPx) {
		lineY = yFrac;
		inset = insetPx;
		thin = thicknessPx;
		invalidate();
	}

	public void setSeekable(boolean s) {
		seekable = s;
	}

	public boolean isDragging() {
		return dragging;
	}

	public void setProgress(float f) {
		if (dragging) return;
		f = Math.max(0f, Math.min(1f, f));
		if (Math.abs(f - frac) > 0.0005f) {
			frac = f;
			invalidate();
		}
	}

	@Override
	protected void onDraw(@NonNull Canvas c) {
		float l = inset, r = getWidth() - inset;
		if (r <= l) return;
		float h = dragging ? thin * 1.6f : thin;
		float y = getHeight() * lineY;
		c.drawRoundRect(l, y - h / 2, r, y + h / 2, h, h, bg);
		float x = l + (r - l) * frac;
		c.drawRoundRect(l, y - h / 2, Math.max(l + h, x), y + h / 2, h, h, fg);
		if (seekable) c.drawCircle(x, y, dragging ? thin * 2.2f : thin * 1.35f, knob);
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (!seekable) return false;
		float l = inset, r = getWidth() - inset;
		float f = Math.max(0f, Math.min(1f, (e.getX() - l) / Math.max(1f, r - l)));
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
				dragging = true;
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
				frac = f;
				invalidate();
				if (listener != null) listener.onDrag(f);
				return true;
			}
			case MotionEvent.ACTION_UP -> {
				dragging = false;
				frac = f;
				invalidate();
				if (listener != null) listener.onSeek(f);
				return true;
			}
			case MotionEvent.ACTION_CANCEL -> {
				dragging = false;
				invalidate();
				return true;
			}
		}
		return false;
	}
}
