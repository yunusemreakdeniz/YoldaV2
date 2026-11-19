package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * "Kaldığın yerden" sayfası (karta dokununca): son çalınan / izlenenlerin tamamı (en yenisi üstte), türü,
 * ne zaman ve ne kadarı dinlendiği; her satırda Devam et; en üstte "Sonuncuyu sürdür"; tümünü temizle.
 */
public class HistoryFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private long clearAskedAt;

	@Override
	public int getFragmentId() {
		return R.id.yolda_history_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Kaldığın yerden";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance;
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
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

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) render();
	}

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		YoldaStyle.applyScreenBackground(root, t);
		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(YoldaStyle.side(ctx)), dp(YoldaStyle.top(ctx)), dp(YoldaStyle.side(ctx)), dp(160));
		sv.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

		col.addView(text(ctx, "Kaldığın yerden", YoldaStyle.titleSp(ctx), true, t.ink));
		List<YoldaPlayHistory.Entry> l = YoldaPlayHistory.all(ctx);
		if (l.isEmpty()) {
			TextView m = text(ctx, "Henüz bir şey dinlemedin. Kanal, radyo, şarkı ya da video açınca burada listelenir.", 16, false, t.muted);
			m.setPadding(0, dp(16), 0, 0);
			col.addView(m);
			return;
		}
		long now = System.currentTimeMillis();
		MainActivityDelegate a = getActivityDelegate();

		// En üstte: sonuncuyu sürdür
		YoldaPlayHistory.Entry first = l.get(0);
		LinearLayout hero = new LinearLayout(ctx);
		hero.setOrientation(LinearLayout.VERTICAL);
		hero.setBackground(YoldaStyle.selected(ctx, 20));
		hero.setPadding(dp(16), dp(16), dp(16), dp(16));
		hero.addView(text(ctx, first.kind() + " · " + YoldaPlayHistory.ago(first.at(), now), 14, false, t.ink));
		TextView hn = text(ctx, "▶  " + first.name(), 24, true, t.ink);
		hn.setMaxLines(2);
		hn.setEllipsize(TextUtils.TruncateAt.END);
		hero.addView(hn);
		if (first.percent() >= 0) hero.addView(progress(ctx, first.percent()));
		hero.setClickable(true);
		hero.setContentDescription("Sürdür: " + first.name());
		hero.setOnClickListener(v -> play(a, first));
		LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
		hl.topMargin = dp(16);
		col.addView(hero, hl);

		// Geri kalanı
		if (l.size() > 1) {
			TextView sec = text(ctx, "Önceki", 18, true, t.ink);
			sec.setPadding(0, dp(24), 0, dp(4));
			col.addView(sec);
		}
		for (int i = 1; i < l.size(); i++) {
			YoldaPlayHistory.Entry e = l.get(i);
			LinearLayout row = new LinearLayout(ctx);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setBackground(YoldaStyle.glass(ctx, t, 16));
			row.setPadding(dp(16), dp(8), dp(8), dp(8));
			row.setMinimumHeight(dp(64));
			LinearLayout tx = new LinearLayout(ctx);
			tx.setOrientation(LinearLayout.VERTICAL);
			TextView n = text(ctx, e.name(), 16, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			tx.addView(n);
			int pc = e.percent();
			tx.addView(text(ctx, e.kind() + " · " + YoldaPlayHistory.ago(e.at(), now) + ((pc >= 0) ? (" · %" + pc + " dinlendi") : ""),
					13, false, t.muted));
			if (pc >= 0) tx.addView(progress(ctx, pc));
			row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
			row.addView(button(ctx, (pc > 0) ? "Devam et" : "Aç", v -> play(a, e)), new LinearLayout.LayoutParams(-2, dp(48)));
			row.setContentDescription(e.name() + ", " + YoldaPlayHistory.ago(e.at(), now));
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
			rl.topMargin = dp(8);
			col.addView(row, rl);
		}

		boolean sure = System.currentTimeMillis() - clearAskedAt < 4000;
		TextView clr = button(ctx, sure ? "Emin misin? Tekrar dokun" : "Geçmişi temizle", v -> {
			if (System.currentTimeMillis() - clearAskedAt < 4000) {
				clearAskedAt = 0;
				YoldaPlayHistory.clear(ctx);
				YoldaStyle.toast(ctx, "Geçmiş temizlendi");
			} else {
				clearAskedAt = System.currentTimeMillis();
			}
			render();
		});
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(52));
		cl.topMargin = dp(24);
		col.addView(clr, cl);
	}

	/** Geçmişteki öğeyi aç: adresiyle (radyo dizini / yedek TV) ya da kütüphane kimliğiyle. */
	static void play(MainActivityDelegate a, YoldaPlayHistory.Entry e) {
		if (e.url() != null) {
			a.getBody().playItem("TV".equals(e.kind())
					? me.aap.fermata.media.radio.YoldaStreamItem.createVideo(a.getLib(), e.url(), e.name())
					: me.aap.fermata.media.radio.YoldaStreamItem.create(a.getLib(), e.url(), e.name(), null, null));
			return;
		}
		a.getLib().getItem(e.id()).main().onCompletion((it, err) -> {
			if (it instanceof MediaLib.PlayableItem pi) a.getBody().playItem(pi);
			else YoldaStyle.toast(a.getContext(), e.name() + " bulunamadı");
		});
	}

	private View progress(Context ctx, int percent) {
		FrameLayout bar = new FrameLayout(ctx);
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setCornerRadius(dp(3));
		bg.setColor(0x33FFFFFF);
		bar.setBackground(bg);
		View fill = new View(ctx);
		android.graphics.drawable.GradientDrawable fg = new android.graphics.drawable.GradientDrawable();
		fg.setCornerRadius(dp(3));
		fg.setColor(t.accent | 0xFF000000);
		fill.setBackground(fg);
		bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
		bar.post(() -> {
			ViewGroup.LayoutParams lp = fill.getLayoutParams();
			lp.width = bar.getWidth() * percent / 100;
			fill.setLayoutParams(lp);
		});
		LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, dp(6));
		l.topMargin = dp(8);
		bar.setLayoutParams(l);
		return bar;
	}

	private TextView button(Context ctx, String label, View.OnClickListener click) {
		TextView b = text(ctx, label, 15, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setSingleLine(true);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setPadding(dp(16), 0, dp(16), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription(label);
		return b;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size * YoldaStyle.textScale(ctx));
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
