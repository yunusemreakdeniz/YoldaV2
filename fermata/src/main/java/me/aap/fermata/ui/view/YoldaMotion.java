package me.aap.fermata.ui.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Ölçülü animasyonlar: ekran açılışı (kısa soluklaşma ve hafif yükselme), kart / düğme basışı (hafif küçülme).
 * Sistem animasyonları kapalıysa hiçbiri çalışmaz; araçta yalnız kısa soluklaşma (yükselme yok, sürücüyü
 * oyalamasın). Dokunma dinleyicisi kullanılmaz: basış durum animatörüyle olur, kaydırma ve tıklama bozulmaz.
 */
public final class YoldaMotion {
	private YoldaMotion() {
	}

	public static boolean motionOn() {
		if (android.os.Build.VERSION.SDK_INT < 26) return true;
		return android.animation.ValueAnimator.areAnimatorsEnabled();
	}

	/** Ekran açılış süresi (ms); 0: animasyon yok. */
	public static long enterMs(boolean car, boolean motion) {
		if (!motion) return 0;
		return car ? 140 : 220;
	}

	/** Açılışta yükselme (dp); araçta yok. */
	public static float enterRiseDp(boolean car) {
		return car ? 0f : 12f;
	}

	/** Yeni açılan ekranı yumuşakça getir. */
	public static void enter(@Nullable View v, boolean car) {
		if (v == null) return;
		long ms = enterMs(car, motionOn());
		v.animate().cancel();
		if (ms == 0) {
			v.setAlpha(1f);
			v.setTranslationY(0f);
			return;
		}
		float d = v.getResources().getDisplayMetrics().density;
		v.setAlpha(0f);
		v.setTranslationY(enterRiseDp(car) * d);
		v.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(new DecelerateInterpolator(1.6f))
				.withEndAction(() -> {
					v.setAlpha(1f);
					v.setTranslationY(0f);
				}).start();
	}

	/** Basınca %3 küçülüp bırakınca geri gelen kart / düğme. */
	public static void press(@Nullable View v) {
		if ((v == null) || !motionOn()) return;
		StateListAnimator s = new StateListAnimator();
		s.addState(new int[]{android.R.attr.state_pressed}, scale(v, 0.97f, 90));
		s.addState(new int[]{}, scale(v, 1f, 160));
		v.setStateListAnimator(s);
	}

	private static AnimatorSet scale(View v, float to, long ms) {
		AnimatorSet a = new AnimatorSet();
		a.playTogether(ObjectAnimator.ofFloat(v, View.SCALE_X, to), ObjectAnimator.ofFloat(v, View.SCALE_Y, to));
		a.setDuration(ms);
		a.setInterpolator(new DecelerateInterpolator());
		return a;
	}
}
