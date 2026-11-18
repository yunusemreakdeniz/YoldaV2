package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Kişiler sayfası (Kişiler kartına dokununca): seçtiğin kişiler büyük satırlarda (dokun → ara), altta
 * "＋ Kişi ekle"; sağ üstte "Düzenle" ile sıra (▲ ▼) ve kaldırma (✕). Sesle: "Annemi ara".
 * Boşluklar 8dp ızgarasında; satırlar 72dp (araçta da rahat dokunulur).
 */
public class ContactsFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private boolean editing;

	@Override
	public int getFragmentId() {
		return R.id.yolda_contacts_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Kişiler";
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
		editing = false;
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
		if (!hidden) {
			editing = false;
			render();
		}
	}

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
		MainActivityDelegate a = MainActivityDelegate.get(ctx);
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

		List<YoldaCalls.Contact> l = YoldaCalls.favorites(ctx);
		LinearLayout head = new LinearLayout(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.addView(text(ctx, "Kişiler", YoldaStyle.titleSp(ctx), true, t.ink), new LinearLayout.LayoutParams(0, -2, 1f));
		if (!l.isEmpty()) {
			TextView ed = pill(ctx, editing ? "Bitti" : "Düzenle");
			ed.setOnClickListener(v -> {
				editing = !editing;
				render();
			});
			head.addView(ed, new LinearLayout.LayoutParams(-2, dp(48)));
		}
		col.addView(head);
		TextView hint = text(ctx, editing ? "▲ ▼ ile sırala, ✕ ile kaldır" : "Dokun → ara · sesle: \"Hey Yolda, annemi ara\"",
				14, false, t.muted);
		hint.setPadding(0, dp(8), 0, dp(8));
		col.addView(hint);

		for (int i = 0; i < l.size(); i++) {
			YoldaCalls.Contact c = l.get(i);
			int idx = i;
			LinearLayout row = new LinearLayout(ctx);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(dp(12), 0, dp(8), 0);
			row.setBackground(YoldaStyle.glass(ctx, t, 20));
			// baş harfler
			TextView av = text(ctx, c.initials(), 18, true, 0xFFFFFFFF);
			av.setGravity(Gravity.CENTER);
			GradientDrawable ab = new GradientDrawable();
			ab.setShape(GradientDrawable.OVAL);
			ab.setColor(AVATAR[Math.floorMod(c.name().hashCode(), AVATAR.length)]);
			av.setBackground(ab);
			row.addView(av, new LinearLayout.LayoutParams(dp(48), dp(48)));
			LinearLayout nm = new LinearLayout(ctx);
			nm.setOrientation(LinearLayout.VERTICAL);
			nm.setPadding(dp(16), 0, dp(8), 0);
			TextView n = text(ctx, c.name(), 18, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			nm.addView(n);
			nm.addView(text(ctx, c.number(), 13, false, t.muted));
			row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
			if (editing) {
				row.addView(btn(ctx, R.drawable.yi_up, "Yukarı: " + c.name(), (idx > 0), v -> move(ctx, idx, -1)), sq());
				row.addView(btn(ctx, R.drawable.yi_down, "Aşağı: " + c.name(), (idx < l.size() - 1), v -> move(ctx, idx, 1)), sq());
				row.addView(btn(ctx, R.drawable.yi_close, "Kaldır: " + c.name(), true, v -> {
					YoldaCalls.removeFavorite(ctx, c.number());
					YoldaStyle.toast(ctx, "Kaldırıldı: " + c.name());
					render();
				}), sq());
			} else {
				row.addView(btn(ctx, R.drawable.yi_phone, c.name() + " ara", true, v -> YoldaCalls.call(a, c)), sq());
				row.setClickable(true);
				row.setOnClickListener(v -> YoldaCalls.call(a, c));
			}
			row.setContentDescription(c.name());
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(72));
			rl.topMargin = dp(8);
			col.addView(row, rl);
		}

		// ＋ Kişi ekle (listenin sonunda)
		LinearLayout add = new LinearLayout(ctx);
		add.setGravity(Gravity.CENTER);
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(20));
		g.setStroke(Math.max(1, dp(1)), 0x4DFFFFFF, dp(6), dp(5));
		add.setBackground(g);
		add.setClickable(true);
		add.setContentDescription("Kişi ekle");
		ImageView ai = new ImageView(ctx);
		ai.setImageResource(R.drawable.yi_add);
		ai.setImageTintList(android.content.res.ColorStateList.valueOf(t.ink));
		add.addView(ai, new LinearLayout.LayoutParams(dp(24), dp(24)));
		TextView at = text(ctx, "  Kişi ekle", 16, true, t.ink);
		add.addView(at);
		add.setOnClickListener(v -> YoldaCalls.pick(a, ok -> {
			if (isAdded()) render();
		}));
		LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(64));
		al.topMargin = dp(16);
		col.addView(add, al);
		if (l.isEmpty()) {
			TextView e = text(ctx, "Henüz kişi yok. Eklediğin kişiler burada ve Kişiler kartında görünür; tek dokunuşla aranır.",
					15, false, t.muted);
			e.setPadding(0, dp(16), 0, 0);
			col.addView(e);
		}
	}

	private static final int[] AVATAR = {0xFF2563EB, 0xFF059669, 0xFFD97706, 0xFFDB2777, 0xFF7C3AED, 0xFF0891B2};

	private void move(Context ctx, int i, int dir) {
		List<YoldaCalls.Contact> l = YoldaCalls.favorites(ctx);
		int j = i + dir;
		if ((j < 0) || (j >= l.size())) return;
		l.add(j, l.remove(i));
		YoldaCalls.setFavorites(ctx, l);
		render();
	}

	private ImageView btn(Context ctx, int icon, String desc, boolean enabled, View.OnClickListener click) {
		ImageView b = new ImageView(ctx);
		b.setImageResource(icon);
		b.setImageTintList(android.content.res.ColorStateList.valueOf(t.ink));
		int p = dp(12);
		b.setPadding(p, p, p, p);
		b.setContentDescription(desc);
		b.setEnabled(enabled);
		b.setAlpha(enabled ? 1f : 0.3f);
		b.setClickable(true);
		b.setOnClickListener(click);
		return b;
	}

	private LinearLayout.LayoutParams sq() {
		return new LinearLayout.LayoutParams(dp(48), dp(48));
	}

	private TextView pill(Context ctx, String s) {
		TextView b = text(ctx, s, 15, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(16), 0, dp(16), 0);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setClickable(true);
		b.setContentDescription(s);
		return b;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size);
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
