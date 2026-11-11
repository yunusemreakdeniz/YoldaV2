package me.aap.fermata.ui.fragment;

import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * Yolda sayfalarında Fermata'nın yüzen menü düğmesi (≡) gösterilmez: alt menü ve sesli asistan zaten var,
 * düğme arama kutusunun ve oynatıcının üstüne biniyordu. Görünürlüğü oynatıcı da değiştirdiği için
 * düğme gizlenmez, boyutu sıfırlanır; başka bir sayfaya geçince eski haline döner.
 */
final class YoldaNoFab implements FloatingButton.Mediator {
	static final YoldaNoFab instance = new YoldaNoFab();

	private YoldaNoFab() {
	}

	@Override
	public void enable(FloatingButton fb, ActivityFragment f) {
		fb.setOnClickListener(null);
		fb.setOnLongClickListener(null);
		fb.setScaleX(0f);
		fb.setScaleY(0f);
		fb.setClickable(false);
		fb.setFocusable(false);
	}

	@Override
	public void disable(FloatingButton fb) {
		FloatingButton.Mediator.super.disable(fb);
		fb.setScaleX(1f);
		fb.setScaleY(1f);
		fb.setClickable(true);
		fb.setFocusable(true);
	}
}
