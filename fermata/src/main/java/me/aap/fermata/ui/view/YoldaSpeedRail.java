package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import me.aap.fermata.R;
import me.aap.fermata.ui.fragment.YoldaRadar;
import me.aap.fermata.ui.fragment.YoldaRadarCore;

/**
 * Sol kenar: hız sınırı levhası (kırmızı çember), anlık hız ve öndeki radarın uzaklığı. Yalnız radar
 * uyarıları açıkken ve konum tazeyken görünür; dokununca Radar ekranı açılır.
 */
@SuppressLint("ViewConstructor")
public class YoldaSpeedRail extends LinearLayout {
	private final TextView sign;
	private final TextView speed;
	private final LinearLayout radar;
	private final TextView radarText;
	private final Runnable listener = this::update;
	private boolean allowed = true;

	public YoldaSpeedRail(Context ctx) {
		super(ctx);
		setOrientation(VERTICAL);
		setGravity(Gravity.CENTER_HORIZONTAL);
		int p = dp(6);
		setPadding(p, dp(8), p, dp(8));
		setContentDescription("Hız ve radar");
		setClickable(true);
		setElevation(dp(12));

		sign = new TextView(ctx);
		sign.setGravity(Gravity.CENTER);
		sign.setTypeface(Typeface.DEFAULT_BOLD);
		sign.setTextColor(0xFF111111);
		sign.setIncludeFontPadding(false);
		GradientDrawable sd = new GradientDrawable();
		sd.setShape(GradientDrawable.OVAL);
		sd.setColor(0xFFFFFFFF);
		sd.setStroke(dp(5), 0xFFDC2626);
		sign.setBackground(sd);
		addView(sign, new LayoutParams(dp(46), dp(46)));

		speed = new TextView(ctx);
		speed.setGravity(Gravity.CENTER);
		speed.setTypeface(Typeface.DEFAULT_BOLD);
		speed.setIncludeFontPadding(false);
		speed.setTextColor(0xFFFFFFFF);
		LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		slp.topMargin = dp(8);
		addView(speed, slp);

		radar = new LinearLayout(ctx);
		radar.setOrientation(VERTICAL);
		radar.setGravity(Gravity.CENTER);
		radar.setPadding(dp(4), dp(6), dp(4), dp(6));
		GradientDrawable rd = new GradientDrawable();
		rd.setCornerRadius(dp(14));
		rd.setColor(0xFFF97316);
		radar.setBackground(rd);
		ImageView ic = new ImageView(ctx);
		ic.setImageResource(R.drawable.yi_radar);
		ic.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		radar.addView(ic, new LayoutParams(dp(22), dp(22)));
		radarText = new TextView(ctx);
		radarText.setTextColor(0xFFFFFFFF);
		radarText.setTypeface(Typeface.DEFAULT_BOLD);
		radarText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		radarText.setGravity(Gravity.CENTER);
		radar.addView(radarText);
		LayoutParams rlp = new LayoutParams(dp(48), LayoutParams.WRAP_CONTENT);
		rlp.topMargin = dp(8);
		addView(radar, rlp);
		avgText = badge(ctx, 0xFF2563EB);
		zoneText = badge(ctx, 0xFFEAB308);
		zoneText.setTextColor(0xFF111111);
		setVisibility(GONE);
	}

	private final TextView avgText;
	private final TextView zoneText;

	private TextView badge(Context ctx, int color) {
		TextView v = new TextView(ctx);
		v.setGravity(Gravity.CENTER);
		v.setTypeface(Typeface.DEFAULT_BOLD);
		v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		v.setTextColor(0xFFFFFFFF);
		v.setPadding(dp(2), dp(5), dp(2), dp(5));
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(12));
		g.setColor(color);
		v.setBackground(g);
		v.setVisibility(GONE);
		LayoutParams lp = new LayoutParams(dp(48), LayoutParams.WRAP_CONTENT);
		lp.topMargin = dp(8);
		addView(v, lp);
		return v;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		YoldaRadar.addListener(listener);
		update();
	}

	@Override
	protected void onDetachedFromWindow() {
		YoldaRadar.removeListener(listener);
		if (overAnim != null) {
			overAnim.cancel();
			overAnim = null;
		}
		super.onDetachedFromWindow();
	}

	private boolean video;

	/**
	 * Video (tam ekran / YouTube) oynarken: gösterge yalnız önemli anda görünür — radar 1 km içinde, hız
	 * aşımı, okul / hastane bölgesi, ortalama hız koridoru. Düz hız göstergesi videoyu kapatmasın.
	 */
	public void setVideo(boolean video) {
		if (this.video == video) return;
		this.video = video;
		update();
	}

	public boolean isVideo() {
		return video;
	}

	/** Küçük pencere, oynatıcı ekranı gibi durumlarda gizli kalsın. */
	public void setAllowed(boolean allowed) {
		if (this.allowed == allowed) return;
		this.allowed = allowed;
		update();
	}

	public void update() {
		Context ctx = getContext();
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		setBackground(null); // cam: sol alttaki ortak kapsül (YoldaDock)
		boolean on = allowed && YoldaRadar.isEnabled(ctx) && YoldaRadar.hasFix();
		YoldaRadarCore.Ahead a = YoldaRadar.ahead;
		int lim = YoldaRadar.limit;
		int sp = YoldaRadar.speed;
		boolean avgOn = YoldaRadar.avg.isActive() && YoldaRadar.feature(ctx, "avg");
		me.aap.fermata.ui.fragment.YoldaDriveCore.Place z = YoldaRadar.zone;
		boolean show = on && ((lim > 0) || (sp >= 3) || (a != null) || avgOn || (z != null));
		if (video) {
			boolean overV = YoldaRadarCore.overSpeed(sp, lim) && !YoldaRadar.limitEstimated;
			show = show && (((a != null) && (a.meters() <= 1000)) || overV || avgOn || (z != null));
		}
		if (show) {
			int av = YoldaRadar.avg.average();
			if (avgOn) {
				avgText.setVisibility(VISIBLE);
				setIf(avgText, "ORT\n" + ((av > 0) ? av : "…"));
				int al = (YoldaRadar.avg.limit() > 0) ? YoldaRadar.avg.limit() : lim;
				((GradientDrawable) avgText.getBackground()).setColor(((av > 0) && YoldaRadarCore.overSpeed(av, al))
						? 0xFFDC2626 : 0xFF2563EB);
			} else {
				avgText.setVisibility(GONE);
			}
			if (z != null) {
				zoneText.setVisibility(VISIBLE);
				setIf(zoneText, (z.kind() == 's') ? "OKUL" : "HAST.");
			} else {
				zoneText.setVisibility(GONE);
			}
		}
		if (!show) {
			if (overAnim != null) {
				overAnim.cancel();
				overAnim = null;
			}
			if (getVisibility() != GONE) setVisibility(GONE);
			alertColor = 0;
			if (onChange != null) onChange.run();
			return;
		}
		if (lim > 0) {
			sign.setVisibility(VISIBLE);
			setIf(sign, String.valueOf(lim));
			sign.setTextSize(TypedValue.COMPLEX_UNIT_SP, (lim >= 100) ? 15 : 18);
			if (overAnim == null) sign.setAlpha(YoldaRadar.limitEstimated ? 0.75f : 1f); // tahmini sınır (etiketsiz yol) soluk
		} else {
			sign.setVisibility(GONE);
		}
		boolean over = (lim > 0) && (sp > lim);
		// Kısa ekranda (araç) levha ve radar yetsin: hız aracın göstergesinde zaten var (aşımda yine görünür)
		android.view.View parent = (android.view.View) getParent();
		boolean compact = (parent != null) && (parent.getHeight() > 0) && (parent.getHeight() < dp(470));
		if ((sp >= 0) && ((!compact && !video) || (lim == 0) || over)) {
			speed.setVisibility(VISIBLE);
			setIf(speed, sp + "\nkm/s");
			speed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
			speed.setTextColor(YoldaRadarCore.overSpeed(sp, lim) ? 0xFFEF4444 : over ? 0xFFFBBF24 : t.ink);
		} else {
			speed.setVisibility(GONE);
		}
		if (a != null) {
			radar.setVisibility(VISIBLE);
			setIf(radarText, a.meters() >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f\nkm", a.meters() / 1000f)
					: (a.meters() + "\nm"));
			((GradientDrawable) radar.getBackground()).setColor((a.meters() <= 300) ? 0xFFDC2626 : 0xFFF97316);
		} else {
			radar.setVisibility(GONE);
		}
		// Hız aşımı: aşım sürdükçe levha (yoksa hız) yanıp söner
		boolean overNow = YoldaRadarCore.overSpeed(sp, lim) && !YoldaRadar.limitEstimated;
		android.view.View overTarget = (sign.getVisibility() == VISIBLE) ? sign : speed;
		if (overNow && (overAnim == null)) overAnim = blink(overTarget, -1);
		else if (!overNow && (overAnim != null)) {
			overAnim.cancel();
			overAnim = null;
		}
		// Yeni uyarı: ilgili gösterge birkaç kez yanıp söner
		long at = YoldaRadar.alertAt;
		if (at != seenAlertAt) {
			seenAlertAt = at;
			if (android.os.SystemClock.elapsedRealtime() - at < 3000) {
				android.view.View v = switch (YoldaRadar.alertKind) {
					case 'r' -> radar;
					case 'a' -> avgText;
					case 'z' -> zoneText;
					case 'f' -> this;
					default -> null; // 'o': yukarıda sürekli
				};
				if ((v != null) && (v.getVisibility() == VISIBLE)) {
					blink(v, 5);
					blinkCount++;
				}
			}
		}
		StringBuilder d = new StringBuilder("Hız ve radar");
		if (lim > 0) d.append(", sınır ").append(lim);
		if (sp >= 0) d.append(", hız ").append(sp);
		if (a != null) d.append(", radar ").append(a.meters()).append(" metre");
		if (avgOn) d.append(", ortalama hız ").append(YoldaRadar.avg.average());
		if (z != null) d.append((z.kind() == 's') ? ", okul bölgesi" : ", hastane bölgesi");
		setContentDescription(d);
		if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
		int ac = 0;
		if (overNow || ((a != null) && (a.meters() <= 300))) ac = 0xFFEF4444;
		else if (a != null) ac = 0xFFF97316;
		else if (z != null) ac = 0xFFEAB308;
		else if (avgOn) {
			int av = YoldaRadar.avg.average(), al = (YoldaRadar.avg.limit() > 0) ? YoldaRadar.avg.limit() : lim;
			ac = ((av > 0) && YoldaRadarCore.overSpeed(av, al)) ? 0xFFEF4444 : 0xFF3B82F6;
		}
		alertColor = ac;
		if (onChange != null) onChange.run();
	}

	private int alertColor;
	/** Durum değişince (kapsülün kenar rengi için). */
	@androidx.annotation.Nullable
	public Runnable onChange;

	/** Uyarının rengi (kırmızı: hız aşımı / radar çok yakın, turuncu: radar, sarı: bölge, mavi: ortalama); yoksa 0. */
	public int alertColor() {
		return alertColor;
	}

	private android.animation.ObjectAnimator overAnim;
	private long seenAlertAt;
	/** Testler için: kaç uyarıda yanıp söndü. */
	public int blinkCount;

	public boolean isOverBlinking() {
		return overAnim != null;
	}

	/** times &lt; 0: durdurulana kadar. */
	private android.animation.ObjectAnimator blink(android.view.View v, int times) {
		android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofFloat(v, android.view.View.ALPHA, 1f, 0.15f);
		an.setDuration(230);
		an.setRepeatMode(android.animation.ValueAnimator.REVERSE);
		an.setRepeatCount((times < 0) ? android.animation.ValueAnimator.INFINITE : (times * 2 - 1));
		an.addListener(new android.animation.AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(android.animation.Animator animation) {
				v.setAlpha(1f);
			}
		});
		an.start();
		return an;
	}

	/** Metin değiştiyse yaz (aynı metni yazmak bile yeniden yerleşime yol açıyor). */
	private static void setIf(TextView t, CharSequence s) {
		if (!android.text.TextUtils.equals(t.getText(), s)) t.setText(s);
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
