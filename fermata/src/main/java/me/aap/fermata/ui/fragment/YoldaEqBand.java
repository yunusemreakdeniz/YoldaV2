package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import me.aap.fermata.media.engine.YoldaEq;

/**
 * Ekolayzer bandı için büyük dikey sürgü: kalın iz, ortada 0 dB çizgisi, sıfırdan değere dolgu, iri tutamak.
 * Parmakla yukarı / aşağı sürüklenir (1 dB adım); erişilebilirlikte kaydırma eylemiyle ±1 dB.
 */
final class YoldaEqBand extends View {
	interface Listener {
		void onChange(int db, boolean done);
	}

	Listener listener;
	private int db;
	private final int accent;
	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF r = new RectF();

	YoldaEqBand(Context ctx, int db, int accent) {
		super(ctx);
		this.db = db;
		this.accent = accent;
		setClickable(true);
		setFocusable(true);
	}

	static String fmt(int db) {
		return (db > 0 ? "+" : "") + db;
	}

	private float pad() {
		return 18 * getResources().getDisplayMetrics().density; // tutamağın yarısı: uçlarda kesilmesin
	}

	private float yOf(int v) {
		float top = pad(), h = getHeight() - 2 * pad();
		return top + h * (1f - (v - YoldaEq.MIN_DB) / (float) (YoldaEq.MAX_DB - YoldaEq.MIN_DB));
	}

	@Override
	protected void onDraw(Canvas c) {
		float d = getResources().getDisplayMetrics().density;
		float cx = getWidth() / 2f, top = pad(), bot = getHeight() - pad();
		float tw = 10 * d;
		p.setStyle(Paint.Style.FILL);
		p.setColor(0x33FFFFFF); // iz
		r.set(cx - tw / 2, top, cx + tw / 2, bot);
		c.drawRoundRect(r, tw / 2, tw / 2, p);
		float y0 = yOf(0), y = yOf(db);
		p.setColor(0x80FFFFFF); // 0 dB çizgisi
		c.drawRect(cx - 16 * d, y0 - d, cx + 16 * d, y0 + d, p);
		p.setColor(accent); // sıfırdan değere dolgu
		r.set(cx - tw / 2, Math.min(y, y0), cx + tw / 2, Math.max(y, y0));
		c.drawRoundRect(r, tw / 2, tw / 2, p);
		p.setColor(0xFFFFFFFF); // tutamak
		c.drawCircle(cx, y, 15 * d, p);
		p.setColor(accent);
		c.drawCircle(cx, y, 7 * d, p);
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int act = e.getActionMasked();
		if ((act == MotionEvent.ACTION_DOWN) || (act == MotionEvent.ACTION_MOVE) || (act == MotionEvent.ACTION_UP)) {
			if (act == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true); // kaydırma çalmasın
			int v = YoldaEq.dbAt(e.getY(), pad(), getHeight() - 2 * pad());
			boolean done = act == MotionEvent.ACTION_UP;
			if ((v != db) || done) {
				db = v;
				invalidate();
				if (listener != null) listener.onChange(db, done);
			}
			if (done) performClick();
			return true;
		}
		return super.onTouchEvent(e);
	}

	@Override
	public boolean performClick() {
		return super.performClick();
	}

	@Override
	public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
		super.onInitializeAccessibilityNodeInfo(info);
		info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
		info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
	}

	@Override
	public boolean performAccessibilityAction(int action, android.os.Bundle args) {
		int step = (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ? 1
				: (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ? -1 : 0;
		if (step == 0) return super.performAccessibilityAction(action, args);
		db = Math.max(YoldaEq.MIN_DB, Math.min(YoldaEq.MAX_DB, db + step));
		invalidate();
		if (listener != null) listener.onChange(db, true);
		return true;
	}
}
