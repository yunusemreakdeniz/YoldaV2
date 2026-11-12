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
	/** TV | Radyo segmenti (şartname 3.2). */
	private static boolean radio;
	private static final String RADIO_GROUP = "Radyo";

	/** Ana sayfadaki "Radyo" kutusu: kanal ekranını radyo segmentinde aç. */
	public static void openSegment(MainActivityDelegate a, boolean radioSegment) {
		radio = radioSegment;
		if (a.showFragment(R.id.yolda_channels_fragment) instanceof ChannelsFragment f && (f.grid != null)) {
			f.group = ALL;
			f.rebuildView();
		}
	}
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
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance; // tam ekran: başlık çubuğu yok
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance; // yüzen ≡ düğmesi yok
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
		MainActivityDelegate a = getActivityDelegate();
		boolean carUi = a.isCarActivity();
		col.setPadding(dp(18), dp(carUi ? 8 : 14), dp(18), 0);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Başlık: "Kanallar" · sayı · Gelişmiş (Fermata'nın liste ekleme/düzenleme ekranı)
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout titles = new LinearLayout(ctx);
		titles.setOrientation(LinearLayout.VERTICAL);
		LinearLayout titleRow = new LinearLayout(ctx);
		titleRow.setOrientation(LinearLayout.HORIZONTAL);
		titleRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView big = text(ctx, "Canlı yayın", YoldaStyle.titleSp(ctx), true, t.ink);
		big.setLetterSpacing(-0.025f);
		big.setSingleLine(true);
		titleRow.addView(big);
		LinearLayout seg = new LinearLayout(ctx);
		seg.setOrientation(LinearLayout.HORIZONTAL);
		seg.setBackground(YoldaStyle.glass(ctx, t, 999));
		seg.setPadding(dp(4), dp(4), dp(4), dp(4));
		for (int i = 0; i < 2; i++) {
			boolean isRadio = (i == 1);
			boolean on = (radio == isRadio);
			TextView s = text(ctx, isRadio ? "Radyo" : "TV", 15, true, on ? YoldaStyle.ON_ACCENT : t.ink);
			s.setSingleLine(true);
			s.setGravity(Gravity.CENTER);
			s.setPadding(dp(18), dp(8), dp(18), dp(8));
			s.setMinHeight(dp(40));
			if (on) s.setBackground(YoldaStyle.accentFill(ctx, 999));
			s.setOnClickListener(v -> {
				if (radio == isRadio) return;
				radio = isRadio;
				group = ALL;
				rebuildView();
			});
			seg.addView(s);
		}
		titles.addView(titleRow);
		LinearLayout segRow = null;
		if (carUi) { // araç: seçici başlığın yanında, tek satır (kanallara daha çok yer)
			LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-2, -2);
			il.leftMargin = dp(16);
			titleRow.addView(seg, il);
		} else { // telefon: seçici başlığın altında (yan yanayken dar ekranda sıkışıp "Radyo" kayboluyordu)
			// seçici, + ve Düzenle aynı satırda; başlık tek başına üstte (dar ekranda sıkışmasın)
			segRow = new LinearLayout(ctx);
			segRow.setOrientation(LinearLayout.HORIZONTAL);
			segRow.setGravity(Gravity.CENTER_VERTICAL);
			segRow.addView(seg);
			segRow.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
			LinearLayout.LayoutParams sglp = new LinearLayout.LayoutParams(-1, -2);
			sglp.topMargin = dp(8);
			sglp.bottomMargin = dp(6);
			titles.addView(segRow, sglp);
		}
		status = text(ctx, "Kanallar yükleniyor…", 14, false, t.muted);
		status.setSingleLine(true);
		status.setEllipsize(android.text.TextUtils.TruncateAt.END);
		titles.addView(status);
		if (carUi) status.setVisibility(View.GONE);
		head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
		ImageView adv = new ImageView(ctx);
		adv.setImageResource(R.drawable.yi_add);
		adv.setColorFilter(t.ink);
		adv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		adv.setPadding(dp(12), dp(12), dp(12), dp(12));
		adv.setContentDescription("Kanal listesi ekle");
		adv.setBackground(YoldaStyle.glass(ctx, t, 999));
		adv.setClickable(true);
		adv.setOnClickListener(v -> {
			bypassOnce();
			a.showFragment(R.id.tv_fragment);
		});
		boolean car = a.isCarActivity();
		if (car) {
			TextView ara = text(ctx, "Kanal ara", 15, true, t.ink);
			ara.setGravity(Gravity.CENTER);
			ara.setPadding(dp(18), dp(12), dp(18), dp(12));
			ara.setMinHeight(dp(48));
			ara.setBackground(YoldaStyle.glass(ctx, t, 999));
			ara.setOnClickListener(v -> toggleSearch());
			LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
			alp.rightMargin = dp(10);
			head.addView(ara, alp);
		}
		LinearLayout actions = (segRow != null) ? segRow : head;
		actions.addView(adv, new LinearLayout.LayoutParams(dp(48), dp(48)));
		TextView edit = text(ctx, editing ? "Bitti" : "Düzenle", 15, true, t.ink);
		edit.setSingleLine(true);
		edit.setGravity(Gravity.CENTER);
		edit.setPadding(dp(18), dp(12), dp(18), dp(12));
		edit.setBackground(editing ? YoldaStyle.selected(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
		edit.setClickable(true);
		edit.setOnClickListener(v -> {
			editing = !editing;
			edit.setText(editing ? "Bitti" : "Düzenle");
			edit.setBackground(editing ? YoldaStyle.selected(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
			showGrid();
		});
		LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-2, -2);
		elp.leftMargin = dp(10);
		actions.addView(edit, elp);
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
		search.setPadding(dp(20), 0, dp(20), 0);
		search.setGravity(Gravity.CENTER_VERTICAL);
		android.graphics.drawable.GradientDrawable sbg = new android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{(t.base & 0x00FFFFFF) | 0xCC000000, (t.base & 0x00FFFFFF) | 0xE6000000});
		sbg.setCornerRadius(dp(999));
		sbg.setStroke(Math.max(1, dp(1)), t.edge);
		search.setBackground(YoldaStyle.searchField(ctx, t)); // Navigasyon aramasıyla aynı cam
		search.setElevation(dp(8));
		// Sesli asistan düğmesiyle aynı hizada, hemen sağında (düğme: solda 22dp, 56dp, alttan 14dp)
		FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(YoldaStyle.DECK ? YoldaStyle.VOICE_DP : 56), Gravity.BOTTOM);
		slp.setMargins(dp(YoldaStyle.searchLeft(ctx)), 0, dp(22), dp(YoldaStyle.DECK ? YoldaStyle.VOICE_BOTTOM_DP : 14));
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
		hlp.topMargin = dp(carUi ? 8 : 12);
		col.addView(hs, hlp);

		ScrollView scroll = new ScrollView(ctx);
		scroll.setClipToPadding(false);
		grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		grid.setPadding(0, dp(car ? 10 : 14), 0, car ? dp(20) : dp(96)); // telefonda altta arama + sesli asistan
		scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
		if (car) { // araç: arama başlığın altında, "Kanal ara" ile açılır
			search.setVisibility(View.GONE);
			search.setElevation(0);
			LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(52));
			sl.topMargin = dp(10);
			col.addView(search, 1, sl);
			searchView = search;
		}
		col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

		if (!car) root.addView(search, slp);
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

	/** Döndürmede kartlar, sütun sayısı ve arama kutusu yeni ekrana göre yeniden kurulsun. */
	@Override
	public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		View v = getView();
		if (v != null) v.post(this::rebuildView);
	}

	private void rebuildView() {
		if (!isAdded() || isStateSaved()) return;
		androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
		fm.beginTransaction().detach(this).commitNow();
		fm.beginTransaction().attach(this).commitNow();
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

	private EditText searchView;

	/** Ekrana dönünce "Oynatılıyor" işareti çalan kanala göre yenilensin. */
	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) showGrid();
	}

	/** "Kanal ara": arama satırını aç/kapat; kapanınca filtre temizlenir. */
	private void toggleSearch() {
		EditText s = searchView;
		if (s == null) return;
		android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
				s.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (s.getVisibility() == View.VISIBLE) {
			s.setText("");
			s.setVisibility(View.GONE);
			if (imm != null) imm.hideSoftInputFromWindow(s.getWindowToken(), 0);
		} else {
			s.setVisibility(View.VISIBLE);
			s.setAlpha(0f);
			s.animate().alpha(1f).setDuration(160).start();
			s.requestFocus();
			if (imm != null) imm.showSoftInput(s, 0);
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
			if (RADIO_GROUP.equals(g)) continue; // Radyo ayrı segmentte
			if (radio && !ALL.equals(g) && !FAV.equals(g)) continue;
			boolean on = g.equals(group);
			TextView c = text(ctx, g, 15, true, 0xFFFFFFFF);
			c.setPadding(dp(18), dp(10), dp(18), dp(10));
			c.setMinHeight(dp(44));
			c.setGravity(Gravity.CENTER);
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
			if (radio != RADIO_GROUP.equals(groupOf(pi))) continue; // TV | Radyo segmenti
			if (FAV.equals(group)) {
				if (!favs.isFavoriteItem(pi)) continue;
			} else if (!ALL.equals(group) && !group.equals(groupOf(pi))) continue;
			if (!query.isEmpty() && !norm(pi.getName()).contains(query)) continue;
			if (!editing && isHidden(ctx, pi.getName())) continue; // gizlenenler yalnız düzenlerken görünür
			shown.add(pi);
		}
		status.setText(items.isEmpty() ? "Kanal bulunamadı" : editing
				? "Düzenleme: ★ favori yap · ✕ gizle · + geri getir"
				: (shown.size() + " yayın · hepsi resmi yayın adresleri"));
		grid.removeAllViews();

		boolean car = a.isCarActivity();
		int cols = car ? Math.max(3, Math.min(5, YoldaStyle.widthDp(ctx) / 190))
				: Math.max(2, Math.min(6, ctx.getResources().getConfiguration().screenWidthDp / 170));
		MediaLib.PlayableItem cur = a.getCurrentPlayable();
		playingId = (cur != null) ? cur.getId() : null;
		LinearLayout row = null;
		for (int i = 0; i < shown.size(); i++) {
			if (i % cols == 0) {
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(car ? 150 : 168));
				rlp.bottomMargin = dp(12);
				grid.addView(row, rlp);
			}
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
			if (i % cols != 0) lp.leftMargin = dp(12);
			row.addView(card(ctx, a, shown.get(i), i + 1), lp);
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
	private String playingId;

	private View card(Context ctx, MainActivityDelegate a, MediaLib.PlayableItem pi, int no) {
		// Taslaktaki gibi: beyaz plaka üzerinde logo, altında kanal adı ve grubu
		LinearLayout f = new LinearLayout(ctx);
		f.setOrientation(LinearLayout.VERTICAL);
		f.setClickable(true);
		f.setOnClickListener(v -> {
			if (!editing) a.getBody().playItem(pi);
		});
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
		boolean playing = pi.getId().equals(playingId);
		pb.setStroke(playing ? dp(3) : Math.max(1, dp(1)), playing ? 0xFF4FD1C5 : 0xFFFFFFFF);
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
		TextView num = text(ctx, String.valueOf(no), 12, true, 0x99000000);
		num.setShadowLayer(0, 0, 0, 0);
		FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
		nlp.setMargins(dp(10), dp(6), 0, 0);
		plate.addView(num, nlp);
		if (playing) {
			TextView on = text(ctx, "Oynatılıyor", 11, true, 0xFFFFFFFF);
			on.setShadowLayer(0, 0, 0, 0);
			on.setPadding(dp(8), dp(3), dp(8), dp(3));
			GradientDrawable ob = new GradientDrawable();
			ob.setColor(0xE60A0D18);
			ob.setCornerRadius(dp(999));
			on.setBackground(ob);
			FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
			olp.setMargins(dp(8), 0, 0, dp(8));
			plate.addView(on, olp);
		}
		f.addView(plate, new LinearLayout.LayoutParams(-1, 0, 1f));
		if (editing) {
			boolean hidden = isHidden(ctx, pi.getName());
			MediaLib.Favorites favs = a.getLib().getFavorites();
			boolean fav = favs.isFavoriteItem(pi);
			if (hidden) f.setAlpha(0.4f);
			LinearLayout bar = new LinearLayout(ctx);
			bar.setOrientation(LinearLayout.HORIZONTAL);
			bar.addView(mini(ctx, fav ? R.drawable.yi_star : R.drawable.yi_star_border, v -> {
				String[] e = YoldaFavs.entryOf(pi); // Yolda favorileri (Favoriler kartı) de güncellenir
				if (favs.isFavoriteItem(pi)) {
					favs.removeItem(pi);
					if (e != null) YoldaFavs.remove(ctx, e[0], e[2]);
				} else {
					favs.addItem(pi);
					if (e != null) YoldaFavs.add(ctx, e[0], e[1], e[2], e[3]);
				}
				grid.postDelayed(this::showGrid, 150);
			}));
			bar.addView(mini(ctx, hidden ? R.drawable.yi_add : R.drawable.yi_close, v -> {
				setHidden(ctx, pi.getName(), !hidden);
				showGrid();
			}));
			plate.addView(bar, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
		}

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
		name.setGravity(Gravity.CENTER_HORIZONTAL);
		f.addView(name, new LinearLayout.LayoutParams(-1, -2));
		TextView grp = text(ctx, groupOf(pi), 12, false, 0xB3FFFFFF);
		grp.setMaxLines(1);
		grp.setEllipsize(android.text.TextUtils.TruncateAt.END);
		grp.setGravity(Gravity.CENTER_HORIZONTAL);
		grp.setPadding(dp(4), dp(2), dp(4), 0);
		f.addView(grp, new LinearLayout.LayoutParams(-1, -2));
		return f;
	}

	// ---- düzenleme: kanal gizleme (ana ekrandaki raf da buna uyar) ----

	private boolean editing;

	static boolean isHidden(Context ctx, String name) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE)
				.getStringSet("ch_hidden", java.util.Collections.emptySet()).contains(name);
	}

	private static void setHidden(Context ctx, String name, boolean hide) {
		android.content.SharedPreferences sp = ctx.getApplicationContext()
				.getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
		Set<String> set = new java.util.HashSet<>(sp.getStringSet("ch_hidden", java.util.Collections.emptySet()));
		if (hide) set.add(name);
		else set.remove(name);
		sp.edit().putStringSet("ch_hidden", set).apply();
	}

	private View mini(Context ctx, int res, View.OnClickListener click) {
		FrameLayout b = new FrameLayout(ctx);
		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor(0xE61C1A2B);
		b.setBackground(bg);
		b.setClickable(true);
		b.setOnClickListener(click);
		ImageView iv = new ImageView(ctx);
		iv.setImageResource(res);
		iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
		b.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
		lp.setMargins(dp(4), dp(6), dp(4), 0);
		b.setLayoutParams(lp);
		return b;
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
		tv.setTextSize(size * me.aap.fermata.ui.view.YoldaStyle.textScale(ctx)); // araçta küçük
		tv.setTextColor(color);
		if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
		return tv;
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}
}
