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
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;

/**
 * Yolda kanallar ekranı: üstte arama, altında grup çipleri ve logolu kanal kartları.
 * Kanallar Fermata'nın TV kütüphanesinden okunur (Yolda Kanalları + kullanıcının eklediği listeler),
 * böylece oynatma, favoriler ve "kaldığın yerden devam" Fermata'nın kendi düzeniyle çalışır.
 */
public class ChannelsFragment extends MainActivityFragment {

	private static final String TV_ROOT = "TV";
	private static final String ALL = "Tümü";
	private static final String FAV = "Favoriler";

	private final List<MediaLib.PlayableItem> items = new ArrayList<>();
	private final List<String> groups = new ArrayList<>();
	private YoldaStyle.Theme t;
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
		t = YoldaStyle.current(ctx);

		FrameLayout root = new FrameLayout(ctx);
		YoldaStyle.applyScreenBackground(root, t);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(18), dp(14), dp(18), 0);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));
		MainActivityDelegate a = getActivityDelegate();

		// Başlık: "Kanallar" · sayı · Gelişmiş (Fermata'nın liste ekleme/düzenleme ekranı)
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout titles = new LinearLayout(ctx);
		titles.setOrientation(LinearLayout.VERTICAL);
		TextView big = text(ctx, "TV ve Radyo", 40, true, t.ink);
		big.setLetterSpacing(-0.025f);
		titles.addView(big);
		status = text(ctx, "Kanallar yükleniyor…", 14, false, t.muted);
		titles.addView(status);
		head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
		TextView adv = text(ctx, "+  Liste ekle", 15, true, t.ink);
		adv.setGravity(Gravity.CENTER);
		adv.setPadding(dp(18), dp(12), dp(18), dp(12));
		adv.setBackground(YoldaStyle.glass(ctx, t, 999));
		adv.setClickable(true);
		adv.setOnClickListener(v -> {
			bypassOnce();
			a.showFragment(R.id.tv_fragment);
		});
		head.addView(adv);
		col.addView(head, new LinearLayout.LayoutParams(-1, -2));

		// Arama: altta sabit, cam görünümlü (kartların üstünde süzülür)
		EditText search = a.createEditText(ctx);
		search.setSingleLine();
		search.setHint("Kanal ya da radyo ara");
		android.graphics.drawable.Drawable sIcon = ctx.getDrawable(R.drawable.yi_search);
		if (sIcon != null) {
			sIcon = sIcon.mutate();
			sIcon.setTint(t.muted);
			sIcon.setBounds(0, 0, dp(24), dp(24));
			search.setCompoundDrawablesRelative(sIcon, null, null, null);
			search.setCompoundDrawablePadding(dp(12));
		}
		search.setTextSize(18);
		search.setTextColor(t.ink);
		search.setHintTextColor(t.muted);
		search.setPadding(dp(22), dp(16), dp(22), dp(16));
		android.graphics.drawable.GradientDrawable sbg = new android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{(t.base & 0x00FFFFFF) | 0xCC000000, (t.base & 0x00FFFFFF) | 0xE6000000});
		sbg.setCornerRadius(dp(999));
		sbg.setStroke(Math.max(1, dp(1)), t.edge);
		search.setBackground(new android.graphics.drawable.LayerDrawable(
				new android.graphics.drawable.Drawable[]{sbg, YoldaStyle.glass(ctx, t, 999)}));
		search.setElevation(dp(8));
		FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
		slp.setMargins(dp(22), 0, dp(22), dp(16));
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
		// Grup çipleri
		chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(12);
		col.addView(hs, hlp);

		ScrollView scroll = new ScrollView(ctx);
		scroll.setClipToPadding(false);
		grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		grid.setPadding(0, dp(14), 0, dp(96)); // altta arama çubuğuna yer
		scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
		col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

		root.addView(search, slp);
		load(a);
		return root;
	}

	// ---- Fermata TV ekranına geçiş (TV her açıldığında buraya yönlendirilir) ----

	private static volatile boolean bypass;

	/** Bir sonraki TV açılışında Fermata'nın kendi TV ekranı gelsin (sesli arama, liste ekleme). */
	public static void bypassOnce() {
		bypass = true;
	}

	public static boolean consumeBypass() {
		boolean b = bypass;
		bypass = false;
		return b;
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
			TextView c = text(ctx, g, 15, true, 0xFFFFFFFF);
			c.setPadding(dp(18), dp(10), dp(18), dp(10));
			c.setBackground(on ? YoldaStyle.selected(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
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
		status.setText(items.isEmpty() ? "Kanal bulunamadı" : (shown.size() + " yayın · hepsi resmi yayın adresleri"));
		grid.removeAllViews();

		int cols = Math.max(2, Math.min(6, ctx.getResources().getConfiguration().screenWidthDp / 170));
		LinearLayout row = null;
		for (int i = 0; i < shown.size(); i++) {
			if (i % cols == 0) {
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(168));
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
		// Taslaktaki gibi: beyaz plaka üzerinde logo, altında kanal adı ve grubu
		LinearLayout f = new LinearLayout(ctx);
		f.setOrientation(LinearLayout.VERTICAL);
		f.setClickable(true);
		f.setOnClickListener(v -> a.getBody().playItem(pi));
		f.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
			return false;
		});

		FrameLayout plate = new FrameLayout(ctx);
		GradientDrawable pb = new GradientDrawable();
		pb.setColor(0xEBFFFFFF);
		pb.setCornerRadius(dp(22));
		pb.setStroke(Math.max(1, dp(1)), 0xFFFFFFFF);
		plate.setBackground(pb);
		plate.setElevation(dp(4));
		TextView initials = text(ctx, initials(pi.getName()), 22, true, color(pi.getName()));
		initials.setShadowLayer(0, 0, 0, 0);
		initials.setGravity(Gravity.CENTER);
		plate.addView(initials, new FrameLayout.LayoutParams(-1, -1));
		ImageView logo = new ImageView(ctx);
		logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
		logo.setPadding(dp(14), dp(12), dp(14), dp(12));
		logo.setVisibility(View.GONE);
		plate.addView(logo, new FrameLayout.LayoutParams(-1, -1));
		f.addView(plate, new LinearLayout.LayoutParams(-1, 0, 1f));

		pi.getIconUri().main().onCompletion((u, err) -> {
			if (u == null) return;
			a.getLib().getBitmap(u.toString(), true, true).main().onCompletion((bmp, e2) -> {
				if (bmp == null) return;
				logo.setImageBitmap(bmp);
				logo.setVisibility(View.VISIBLE);
				initials.setText("");
			});
		});

		TextView name = text(ctx, pi.getName(), 15, true, 0xFFFFFFFF);
		name.setMaxLines(1);
		name.setEllipsize(android.text.TextUtils.TruncateAt.END);
		name.setPadding(dp(4), dp(10), dp(4), 0);
		f.addView(name, new LinearLayout.LayoutParams(-1, -2));
		TextView grp = text(ctx, groupOf(pi), 12, false, 0xB3FFFFFF);
		grp.setMaxLines(1);
		grp.setPadding(dp(4), 0, dp(4), 0);
		f.addView(grp, new LinearLayout.LayoutParams(-1, -2));
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
