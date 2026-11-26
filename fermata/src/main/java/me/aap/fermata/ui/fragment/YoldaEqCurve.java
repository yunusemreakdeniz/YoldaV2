package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/**
 * Ekolayzer eğrisi (Mola'daki EqualizerCurveView'dan uyarlandı): bantların kazançlarından geçen yumuşak eğri,
 * altı yarı saydam dolgulu; ortada 0 dB çizgisi. Sürgüler oynatıldıkça güncellenir.
 */
public class YoldaEqCurve extends View {
	static final int MAX_DB = 10;
	private int[] gains = new int[5];
	private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint zero = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path path = new Path();
	private final int color;

	public YoldaEqCurve(Context ctx, int color) {
		super(ctx);
		this.color = color;
		float d = getResources().getDisplayMetrics().density;
		line.setStyle(Paint.Style.STROKE);
		line.setStrokeWidth(3 * d);
		line.setStrokeCap(Paint.Cap.ROUND);
		line.setColor(color);
		fill.setStyle(Paint.Style.FILL);
		zero.setStyle(Paint.Style.STROKE);
		zero.setStrokeWidth(d);
		zero.setColor(0x33FFFFFF);
		dot.setColor(color);
		setContentDescription("Ekolayzer eğrisi");
	}

	public void setGains(int[] g) {
		gains = g.clone();
		invalidate();
	}

	/** Kazancın (dB) dikey konumu: +10 üstte, 0 ortada, -10 altta (pay kadar içeride). */
	static float y(int db, float h, float pad) {
		int v = Math.max(-MAX_DB, Math.min(MAX_DB, db));
		return h / 2f - (v / (float) MAX_DB) * (h / 2f - pad);
	}

	/** Bandın yatay konumu: eşit aralıklı, kenarlardan pay kadar içeride. */
	static float x(int i, int n, float w, float pad) {
		return (n <= 1) ? (w / 2f) : (pad + i * (w - 2 * pad) / (n - 1));
	}

	@Override
	protected void onDraw(Canvas c) {
		int n = gains.length;
		float w = getWidth(), h = getHeight();
		if ((n == 0) || (w <= 0) || (h <= 0)) return;
		float d = getResources().getDisplayMetrics().density, pad = 10 * d;
		c.drawLine(0, h / 2f, w, h / 2f, zero);
		float[] px = new float[n], py = new float[n];
		for (int i = 0; i < n; i++) {
			px[i] = x(i, n, w, pad);
			py[i] = y(gains[i], h, pad);
		}
		path.reset();
		path.moveTo(0, py[0]);
		path.lineTo(px[0], py[0]);
		for (int i = 0; i < n - 1; i++) { // Catmull-Rom → kübik eğri: noktalardan yumuşak geçer
			float x0 = (i > 0) ? px[i - 1] : px[i], y0 = (i > 0) ? py[i - 1] : py[i];
			float x3 = (i + 2 < n) ? px[i + 2] : px[i + 1], y3 = (i + 2 < n) ? py[i + 2] : py[i + 1];
			path.cubicTo(px[i] + (px[i + 1] - x0) / 6f, py[i] + (py[i + 1] - y0) / 6f,
					px[i + 1] - (x3 - px[i]) / 6f, py[i + 1] - (y3 - py[i]) / 6f, px[i + 1], py[i + 1]);
		}
		path.lineTo(w, py[n - 1]);
		Path area = new Path(path);
		area.lineTo(w, h);
		area.lineTo(0, h);
		area.close();
		fill.setShader(new LinearGradient(0, 0, 0, h, (color & 0x00FFFFFF) | 0x55000000, color & 0x00FFFFFF,
				Shader.TileMode.CLAMP));
		c.drawPath(area, fill);
		c.drawPath(path, line);
		for (int i = 0; i < n; i++) c.drawCircle(px[i], py[i], 4 * d, dot);
	}
}
