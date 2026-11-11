package me.aap.fermata.ui.fragment;

import android.view.View;

import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Yolda ekranlarında (Ana sayfa, TV, Navigasyon, Menü) üstteki başlık çubuğu yok: tam ekran.
 * Fermata'nın kendi ekranlarına geçince çubuk yeniden görünür (onların aracı VISIBLE yapar).
 */
final class YoldaFullScreen implements ToolBarView.Mediator {
	static final YoldaFullScreen instance = new YoldaFullScreen();

	private YoldaFullScreen() {
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		tb.removeAllViews();
		tb.setVisibility(View.GONE);
	}

	@Override
	public void disable(ToolBarView tb) {
		tb.setVisibility(View.VISIBLE);
	}
}
