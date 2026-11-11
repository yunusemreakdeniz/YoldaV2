package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.ui.UiUtils;

/**
 * Yolda menüsü (alt menüdeki "Menü"): Fermata'nın uzun açılır listesi yerine üç cam grup —
 * Kitaplık · Bağlantılar · Ayarlar. Fermata'nın eski menüsü "Diğer seçenekler"de duruyor.
 */
public class YoldaMenuFragment extends MainActivityFragment {

	private YoldaStyle.Theme t;
	private FrameLayout root;

	@Override
	public int getFragmentId() {
		return R.id.yolda_menu_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Menü";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance; // tam ekran: başlık çubuğu yok
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		root = new FrameLayout(requireContext());
		render();
		return root;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		ViewGroup.LayoutParams lp = view.getLayoutParams();
		if (lp != null) {
			lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
			lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
			view.setLayoutParams(lp);
		}
	}

	/** Döndürmede (dikey ↔ yatay) ölçüler yeni ekran genişliğine göre baştan hesaplansın. */
	@Override
	public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (getView() != null) getView().post(this::render);
	}

	private void render() {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		YoldaStyle.applyScreenBackground(root, t);
		MainActivityDelegate a = getActivityDelegate();
		AddonManager am = AddonManager.get();

		ScrollView scroll = new ScrollView(ctx);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(me.aap.fermata.ui.view.YoldaStyle.side(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.top(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.side(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.bottom(ctx)));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		TextView title = text(ctx, "Menü", 40, true);
		title.setLetterSpacing(-0.025f);
		col.addView(title);

		boolean wide = ctx.getResources().getConfiguration().screenWidthDp >= 900;
		LinearLayout groups = new LinearLayout(ctx);
		groups.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

		LinearLayout lib = group(ctx, "KİTAPLIK");
		lib.addView(item(ctx, R.drawable.yi_star, "Favoriler", 0xFFFCD34D, 0xFFF59E0B,
				v -> a.showFragment(R.id.favorites_fragment)));
		lib.addView(item(ctx, R.drawable.yi_history, "Kaldığın yerden devam", 0xFFC4B5FD, 0xFF8B5CF6, v -> {
			if (a.hasCurrent()) a.goToCurrent();
			else UiUtils.showInfo(ctx, "Henüz bir şey izlemedin");
		}));
		lib.addView(item(ctx, R.drawable.yi_movie, "Videolar", 0xFFA5B4FC, 0xFF6366F1,
				v -> a.showFragment(R.id.folders_fragment)));
		lib.addView(item(ctx, R.drawable.yi_list, "Çalma listeleri", 0xFFF9A8D4, 0xFFEC4899,
				v -> a.showFragment(R.id.playlists_fragment)));

		LinearLayout links = group(ctx, "BAĞLANTILAR");
		links.addView(item(ctx, R.drawable.yi_nav, "Navigasyon", 0xFF5EEAD4, 0xFF0D9488,
				v -> a.showFragment(R.id.yolda_nav_fragment)));
		links.addView(item(ctx, R.drawable.yi_phone, "Telefonu kullan", 0xFF86EFAC, 0xFF16A34A,
				v -> HomeFragment.phone(ctx)));
		if (am.hasAddon(R.id.web_browser_fragment))
			links.addView(item(ctx, R.drawable.yi_globe, "Tarayıcı", 0xFF93C5FD, 0xFF2563EB,
					v -> a.showFragment(R.id.web_browser_fragment)));
		if (am.hasAddon(R.id.tv_fragment))
			links.addView(item(ctx, R.drawable.yi_add, "Kanal listesi ekle", 0xFFFDA4AF, 0xFFE11D48, v -> {
				ChannelsFragment.bypassOnce();
				a.showFragment(R.id.tv_fragment);
			}));

		LinearLayout set = group(ctx, "AYARLAR");
		set.addView(item(ctx, R.drawable.yi_palette, "Tema: " + t.name, 0xFFDDD6FE, 0xFF7C3AED, v -> {
			YoldaStyle.next(ctx);
			YoldaStyle.refreshBackdrop(ctx);
			render();
		}));
		set.addView(item(ctx, R.drawable.yi_mic, "Sesli komut", 0xFFA7F3D0, 0xFF059669, v -> a.startVoiceAssistant()));
		set.addView(item(ctx, R.drawable.yi_settings, "Gelişmiş ayarlar", 0xFFE2E8F0, 0xFF64748B,
				v -> a.showFragment(R.id.settings_fragment)));
		set.addView(item(ctx, R.drawable.yi_grid, "Diğer seçenekler", 0xFFBAE6FD, 0xFF0284C7,
				v -> getNavBarMediator().showMenu(a)));

		LinearLayout[] gs = {lib, links, set};
		for (int i = 0; i < gs.length; i++) {
			LinearLayout.LayoutParams lp = wide ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2);
			if (i > 0) {
				if (wide) lp.leftMargin = dp(16);
				else lp.topMargin = dp(16);
			}
			groups.addView(gs[i], lp);
		}
		LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
		glp.topMargin = dp(22);
		col.addView(groups, glp);

		// Araç modu durumu: dokununca kurulum ekranı
		boolean ok = YoldaInstaller.isCarReady(ctx) && YoldaInstaller.seenInCar(ctx);
		LinearLayout st = new LinearLayout(ctx);
		st.setOrientation(LinearLayout.HORIZONTAL);
		st.setGravity(Gravity.CENTER_VERTICAL);
		st.setPadding(dp(18), dp(16), dp(18), dp(16));
		st.setBackground(YoldaStyle.glass(ctx, t, 22));
		st.setClickable(true);
		st.setOnClickListener(v -> a.showFragment(R.id.yolda_setup_fragment));
		TextView stt = text(ctx, ok
				? "Araç modu hazır ✓  Araçta Yolda kendi tasarımıyla açılır."
				: (YoldaInstaller.isCarReady(ctx)
				? "Kurulum tamam; Android Auto adımları kaldı. Dokun: kontrol listesi."
				: "Araç modu kurulu değil — araçta düz liste çıkar. Dokun: araç modunu kur."), 15, true);
		stt.setTextColor(ok ? 0xFFB9F6CA : 0xFFFFD180);
		st.addView(stt, new LinearLayout.LayoutParams(0, -2, 1f));
		ImageView chev = new ImageView(ctx);
		chev.setImageResource(R.drawable.yi_chevron);
		chev.setImageTintList(android.content.res.ColorStateList.valueOf(0x99FFFFFF));
		st.addView(chev, new LinearLayout.LayoutParams(dp(22), dp(22)));
		LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-1, -2);
		stlp.topMargin = dp(16);
		col.addView(st, stlp);
	}

	private LinearLayout group(Context ctx, String title) {
		LinearLayout g = new LinearLayout(ctx);
		g.setOrientation(LinearLayout.VERTICAL);
		g.setPadding(dp(12), dp(12), dp(12), dp(12));
		g.setBackground(YoldaStyle.glass(ctx, t, 28));
		TextView tt = text(ctx, title, 12, true);
		tt.setTextColor(0xBFFFFFFF);
		tt.setLetterSpacing(0.1f);
		tt.setPadding(dp(10), dp(6), dp(10), dp(8));
		g.addView(tt);
		return g;
	}

	@SuppressLint("ClickableViewAccessibility")
	private View item(Context ctx, @DrawableRes int ic, String label, int c1, int c2, View.OnClickListener click) {
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(dp(10), 0, dp(10), 0);
		r.setClickable(true);
		r.setOnClickListener(click);
		r.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.setBackground(YoldaStyle.selected(ctx, 18));
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) v.setBackground(null);
			return false;
		});
		FrameLayout d = new FrameLayout(ctx);
		d.setBackground(YoldaStyle.appIcon(ctx, c1, c2, 12));
		ImageView iv = new ImageView(ctx);
		iv.setImageResource(ic);
		iv.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		d.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
		r.addView(d, new LinearLayout.LayoutParams(dp(40), dp(40)));
		TextView l = text(ctx, label, 16, true);
		l.setPadding(dp(14), 0, 0, 0);
		l.setMaxLines(1);
		r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
		ImageView ch = new ImageView(ctx);
		ch.setImageResource(R.drawable.yi_chevron);
		ch.setImageTintList(ColorStateList.valueOf(0x99FFFFFF));
		r.addView(ch, new LinearLayout.LayoutParams(dp(20), dp(20)));
		r.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(58)));
		return r;
	}

	private TextView text(Context ctx, String s, float size, boolean bold) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size);
		tv.setTextColor(0xFFFFFFFF);
		tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
		tv.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
		return tv;
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}
}
