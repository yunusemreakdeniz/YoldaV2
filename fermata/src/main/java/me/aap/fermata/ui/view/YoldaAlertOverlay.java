package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Radar uyarısı (her ekranda, videonun da üstünde): sesli asistandaki ışık efektinin kırmızısı ekranın
 * kenarlarında nabız gibi atar, ortada uyarı kartı. 5 saniye sonra kendiliğinden kapanır; ✕ ya da karta
 * dokunuş kapatır. Kartın dışına dokunuş alttaki ekrana geçer.
 */
public final class YoldaAlertOverlay extends FrameLayout {
	private static final int TAG_KEY = 0x7f0b5e21;
	public static final long SHOW_MS = 5000;

	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF r = new RectF();
	private final long start = SystemClock.uptimeMillis();
	private SweepGradient shader;
	private final TextView msg;
	private final Runnable close = this::dismiss;
	private final boolean urgent;

	/** Video ekrandaysa uyarıyı göster (yoksa bir şey yapmaz). @return gösterildiyse true */
	public static boolean showOnVideo(MainActivityDelegate a, String text, boolean urgent) {
		if (!a.yoldaVideoOnScreen()) return false;
		return show(a, text, urgent);
	}

	public static boolean show(MainActivityDelegate a, String text, boolean urgent) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl)) return false;
		YoldaAlertOverlay old = find(cl);
		if (old != null) { // yenisi geldi: aynı kartta yazıyı değiştir, süreyi yeniden başlat
			old.msg.setText(text);
			old.removeCallbacks(old.close);
			old.postDelayed(old.close, SHOW_MS);
			return true;
		}
		YoldaAlertOverlay v = new YoldaAlertOverlay(cl.getContext(), text, urgent);
		v.setTag(TAG_KEY, Boolean.TRUE);
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
		lp.startToStart = lp.endToEnd = lp.topToTop = lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		v.setElevation(v.dp(50)); // videonun ve kapsülün üstünde
		cl.addView(v, lp);
		v.setAlpha(0f);
		v.animate().alpha(1f).setDuration(180).start();
		v.postDelayed(v.close, SHOW_MS);
		return true;
	}

	@Nullable
	private static YoldaAlertOverlay find(ViewGroup g) {
		for (int i = 0; i < g.getChildCount(); i++)
			if ((g.getChildAt(i) instanceof YoldaAlertOverlay v) && (v.getTag(TAG_KEY) != null)) return v;
		return null;
	}

	public static boolean isShown(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		return (host instanceof ViewGroup g) && (find(g) != null);
	}

	private YoldaAlertOverlay(Context ctx, String text, boolean urgent) {
		super(ctx);
		this.urgent = urgent;
		setWillNotDraw(false);
		// Kartın dışına dokunuş alttaki ekrana geçer (sürerken düğmeler 5 sn kilitlenmesin); kart ve ✕ kapatır
		setClickable(false);
		p.setStyle(Paint.Style.STROKE);

		LinearLayout card = new LinearLayout(ctx);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setGravity(Gravity.CENTER_HORIZONTAL);
		card.setClickable(true);
		card.setOnClickListener(v -> dismiss());
		card.setPadding(dp(28), dp(18), dp(28), dp(22));
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0xF2B71C1C, 0xF27F0000});
		bg.setCornerRadius(dp(30));
		bg.setStroke(dp(2), 0x66FFFFFF);
		card.setBackground(bg);
		card.setElevation(dp(12));

		FrameLayout top = new FrameLayout(ctx);
		ImageView icon = new ImageView(ctx);
		icon.setImageResource(R.drawable.yi_radar);
		icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
		top.addView(icon, new LayoutParams(dp(54), dp(54), Gravity.CENTER));
		card.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

		msg = new TextView(ctx);
		msg.setText(text);
		msg.setTextColor(0xFFFFFFFF);
		msg.setTextSize(26);
		msg.setGravity(Gravity.CENTER);
		msg.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		msg.setPadding(0, dp(8), 0, 0);
		msg.setMaxLines(3);
		card.addView(msg, new LinearLayout.LayoutParams(-2, -2));

		int cw = Math.min(dp(560), Math.round(getResources().getDisplayMetrics().widthPixels * 0.8f));
		addView(card, new LayoutParams(cw, -2, Gravity.CENTER));

		// ✕ kapat: kartın sağ üstünde
		ImageView x = new ImageView(ctx);
		x.setImageResource(R.drawable.yi_close);
		x.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
		x.setPadding(dp(10), dp(10), dp(10), dp(10));
		GradientDrawable xb = new GradientDrawable();
		xb.setShape(GradientDrawable.OVAL);
		xb.setColor(0x33FFFFFF);
		x.setBackground(xb);
		x.setContentDescription("Uyarıyı kapat");
		x.setOnClickListener(v -> dismiss());
		FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
		xl.topMargin = dp(8);
		xl.rightMargin = dp(8);
		top.addView(x, xl);
	}

	public void dismiss() {
		if (getTag(TAG_KEY) == null) return;
		setTag(TAG_KEY, null);
		removeCallbacks(close);
		animate().alpha(0f).setDuration(220).withEndAction(() -> {
			if (getParent() instanceof ViewGroup g) g.removeView(this);
		}).start();
	}

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);
		int a = urgent ? 0xFFFF1744 : 0xFFFF5252, b = 0xFFFF8A80;
		shader = new SweepGradient(w / 2f, h / 2f, new int[]{a, b, a, b, a}, null);
		p.setShader(shader);
	}

	@Override
	protected void onDraw(Canvas c) {
		super.onDraw(c);
		float w = getWidth(), h = getHeight();
		if ((shader == null) || (w <= 0)) return;
		float d = getResources().getDisplayMetrics().density;
		float t = (SystemClock.uptimeMillis() - start) / 1000f;
		float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(t * (urgent ? 5.5 : 3.5))); // nabız
		float base = 6 * d;
		int[] alphas = {28, 60, 110, 220};
		float[] widths = {base * 7f, base * 4f, base * 2f, base * 0.7f};
		for (int i = 0; i < 4; i++) {
			p.setAlpha(Math.round(alphas[i] * pulse));
			p.setStrokeWidth(widths[i]);
			float in = widths[i] / 2f;
			r.set(in, in, w - in, h - in);
			c.drawRoundRect(r, 28 * d, 28 * d, p);
		}
		postInvalidateOnAnimation();
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
