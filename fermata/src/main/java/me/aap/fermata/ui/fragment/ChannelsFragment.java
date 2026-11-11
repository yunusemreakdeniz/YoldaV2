package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * Yolda kanallar ekranı: üstte arama, altında grup çipleri ve logolu kanal kartları.
 * Kanallar Fermata'nın TV kütüphanesinden okunur (Yolda Kanalları + kullanıcının eklediği listeler),
 * böylece oynatma, favoriler ve "kaldığın yerden devam" Fermata'nın kendi düzeniyle çalışır.
 */
public class ChannelsFragment extends MainActivityFragment {

	private static final String TV_ROOT = "TV";
	private static final String ALL = "Tümü";
	private static final String FAV = "⭐ Favoriler";

	private final List<MediaLib.PlayableItem> items = new ArrayList<>();
	private final List<String> groups = new ArrayList<>();
	private HomeFragment.Theme t;
	private LinearLayout grid;
	private LinearLayout chips;
	private TextView status;
	private String group = ALL;
	private String query = "";

	@Override
	public int getFragmentId() {
		return R.id.yolda_channels_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Kanallar";
	}

	@Override
	public boolean isVideoModeSupported() {
		return true;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();
		t = HomeFragment.THEMES[Math.floorMod(ctx.getApplicationContext()
				.getSharedPreferences("yolda_home", Context.MODE_PRIVATE).getInt("theme", 0), HomeFragment.THEMES.length)];

		FrameLayout root = new FrameLayout(ctx);
		root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.bg1, t.bg2}));
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(18), dp(14), dp(18), 0);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Arama
		MainActivityDelegate a = getActivityDelegate();
		EditText search = a.createEditText(ctx);
		search.setSingleLine();
		search.setHint("🔍  Kanal ya da radyo ara");
		search.setTextSize(18);
		search.setTextColor(t.ink);
		search.setHintTextColor(t.muted);
		search.setPadding(dp(18), dp(14), dp(18), dp(14));
		GradientDrawable sb = new GradientDrawable();
		sb.setColor(t.surface);
		sb.setStroke(dp(1), t.stroke);
		sb.setCornerRadius(dp(999));
		search.setBackground(sb);
		search.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int st, int c, int a1) {
			}

			@Override
			public void onTextChanged(CharSequence s, int st, int b, int c) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				query = norm(s.toString());
				showGrid();
			}
		});
		col.addView(search, new LinearLayout.LayoutParams(-1, -2));

		// Grup çipleri
		chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(12);
		col.addView(hs, hlp);

		status = text(ctx, "Kanallar yükleniyor…", 15, false, t.muted);
		status.setPadding(dp(4), dp(14), 0, 0);
		col.addView(status);

		ScrollView scroll = new ScrollView(ctx);
		grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		grid.setPadding(0, dp(12), 0, dp(24));
		scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
		col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

		load(a);
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

	// ---- veri ----

	private void load(MainActivityDelegate a) {
		a.getLib().getItem(TV_ROOT).main().onCompletion((root, err) -> {
			if (!isAdded()) return;
			if (!(root instanceof MediaLib.BrowsableItem)) {
				status.setText("TV eklentisi kapalı. Ayarlar → Eklentiler → TV'yi aç.");
				if (err != null) Log.e(err, "TV kökü bulunamadı");
				return;
			}
			((MediaLib.BrowsableItem) root).getPlayableChildren(true).main().onCompletion((list, err2) -> {
				if (!isAdded()) return;
				items.clear();
				if (list != null) items.addAll(list);
				Set<String> g = new LinkedHashSet<>();
				for (MediaLib.PlayableItem pi : items) g.add(groupOf(pi));
				groups.clear();
				groups.add(ALL);
				groups.add(FAV);
				groups.addAll(g);
				if (err2 != null) Log.e(err2, "Kanallar okunamadı");
				showChips();
				showGrid();
			});
		});
	}

	private static String groupOf(MediaLib.PlayableItem pi) {
		MediaLib.BrowsableItem p = pi.getParent();
		return (p != null) ? p.getName() : "Diğer";
	}

	// ---- çizim ----

	private void showChips() {
		Context ctx = requireContext();
		chips.removeAllViews();
		for (String g : groups) {
			boolean on = g.equals(group);
			TextView c = text(ctx, g, 15, true, on ? Color.WHITE : t.ink);
			c.setPadding(dp(18), dp(10), dp(18), dp(10));
			GradientDrawable bg = new GradientDrawable();
			bg.setColor(on ? t.accent : t.surface);
			bg.setStroke(dp(1), on ? t.accent : t.stroke);
			bg.setCornerRadius(dp(999));
			c.setBackground(bg);
			if (on) c.setTextColor(0xFF0B1026);
			c.setClickable(true);
			c.setOnClickListener(v -> {
				group = g;
				showChips();
				showGrid();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
			lp.rightMargin = dp(10);
			chips.addView(c, lp);
		}
	}

	private void showGrid() {
		if (grid == null || !isAdded()) return;
		Context ctx = requireContext();
		MainActivityDelegate a = getActivityDelegate();
		MediaLib.Favorites favs = a.getLib().getFavorites();
		List<MediaLib.PlayableItem> shown = new ArrayList<>();
		for (MediaLib.PlayableItem pi : items) {
			if (FAV.equals(group)) {
				if (!favs.isFavoriteItem(pi)) continue;
			} else if (!ALL.equals(group) && !group.equals(groupOf(pi))) continue;
			if (!query.isEmpty() && !norm(pi.getName()).contains(query)) continue;
			shown.add(pi);
		}
		status.setText(items.isEmpty() ? "Kanal bulunamadı" : shown.size() + " kanal");
		grid.removeAllViews();

		int cols = Math.max(2, Math.min(6, ctx.getResources().getConfiguration().screenWidthDp / 170));
		LinearLayout row = null;
		for (int i = 0; i < shown.size(); i++) {
			if (i % cols == 0) {
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(132));
				rlp.bottomMargin = dp(12);
				grid.addView(row, rlp);
			}
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
			if (i % cols != 0) lp.leftMargin = dp(12);
			row.addView(card(ctx, a, shown.get(i)), lp);
		}
		// Son satırı boşlukla doldur ki kartlar eşit genişlikte kalsın
		if (row != null) {
			for (int i = shown.size() % cols; (i > 0) && (i < cols); i++) {
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
				lp.leftMargin = dp(12);
				row.addView(new View(ctx), lp);
			}
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private View card(Context ctx, MainActivityDelegate a, MediaLib.PlayableItem pi) {
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(t.surface);
		bg.setStroke(dp(1), t.stroke);
		bg.setCornerRadius(dp(20));
		f.setBackground(bg);
		f.setClickable(true);
		f.setOnClickListener(v -> a.getBody().playItem(pi));
		f.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
			return false;
		});

		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(dp(10), dp(10), dp(10), dp(10));

		// Logo yoksa baş harfler
		FrameLayout logoBox = new FrameLayout(ctx);
		TextView initials = text(ctx, initials(pi.getName()), 22, true, Color.WHITE);
		initials.setGravity(Gravity.CENTER);
		GradientDrawable ib = new GradientDrawable();
		ib.setCornerRadius(dp(14));
		ib.setColor(color(pi.getName()));
		initials.setBackground(ib);
		logoBox.addView(initials, new FrameLayout.LayoutParams(-1, -1));
		ImageView logo = new ImageView(ctx);
		logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
		logo.setPadding(dp(6), dp(6), dp(6), dp(6));
		logo.setVisibility(View.GONE);
		logoBox.addView(logo, new FrameLayout.LayoutParams(-1, -1));
		box.addView(logoBox, new LinearLayout.LayoutParams(dp(110), dp(64)));

		pi.getIconUri().main().onCompletion((u, err) -> {
			if (u == null) return;
			a.getLib().getBitmap(u.toString(), true, true).main().onCompletion((bmp, e2) -> {
				if (bmp == null) return;
				logo.setImageBitmap(bmp);
				logo.setVisibility(View.VISIBLE);
				GradientDrawable lb = new GradientDrawable();
				lb.setCornerRadius(dp(14));
				lb.setColor(0x26FFFFFF);
				initials.setBackground(lb);
				initials.setText("");
			});
		});

		TextView name = text(ctx, pi.getName(), 15, true, t.ink);
		name.setGravity(Gravity.CENTER);
		name.setMaxLines(1);
		name.setEllipsize(android.text.TextUtils.TruncateAt.END);
		name.setPadding(0, dp(10), 0, 0);
		box.addView(name, new LinearLayout.LayoutParams(-1, -2));
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	// ---- yardımcılar ----

	private static String norm(String s) {
		if (s == null) return "";
		String l = s.toLowerCase(new Locale("tr")).replace('ı', 'i');
		return Normalizer.normalize(l, Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "")
				.replaceAll("[^a-z0-9]", "");
	}

	private static String initials(String name) {
		StringBuilder b = new StringBuilder();
		for (String w : name.trim().split("\\s+")) {
			if (!w.isEmpty() && b.length() < 3) b.appendCodePoint(w.codePointAt(0));
		}
		return b.toString().toUpperCase(new Locale("tr"));
	}

	private static int color(String name) {
		int[] c = {0xFFEF4444, 0xFF8B5CF6, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF6366F1};
		return c[Math.floorMod(name.hashCode(), c.length)];
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size);
		tv.setTextColor(color);
		if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
		return tv;
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}
}
