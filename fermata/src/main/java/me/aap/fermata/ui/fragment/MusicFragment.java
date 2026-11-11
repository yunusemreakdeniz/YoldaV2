package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.media.yt.YtMusicItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;

/**
 * Yolda Müzik, YouTube Music düzeninde: üstte logo, ruh hâli çipleri; ana sayfada "Hızlı seçimler"
 * (satır listesi), "Tekrar dinle" ve tür rafları (kare kapaklı kaydırılabilir kartlar); aramada satır listesi.
 * Şarkı önce doğrudan Yolda oynatıcısında açılır; YouTube akışı vermezse ya da çalmazsa YouTube oynatıcısına geçer.
 */
public class MusicFragment extends MainActivityFragment {
	private static final String HOME = "Ana sayfa";
	private static final String[][] MOODS = {
			{"Enerji", "enerjik türkçe şarkılar"}, {"Yolculuk", "yolculuk şarkıları"},
			{"Dinlendirici", "sakin türkçe şarkılar"}, {"Romantik", "romantik türkçe şarkılar"},
			{"Hüzünlü", "hüzünlü türkçe şarkılar"}, {"Parti", "parti şarkıları türkçe"},
			{"Odaklanma", "lofi odaklanma"}, {"Antrenman", "spor motivasyon şarkıları"}};
	private static final String[][] SHELVES = {
			{"Türkçe Pop", "türkçe pop hit"}, {"Yeni çıkanlar", "yeni çıkan türkçe şarkılar"},
			{"Arabesk", "arabesk şarkılar"}, {"Türkçe Rap", "türkçe rap"}, {"Türkçe Rock", "türkçe rock"},
			{"Yabancı hitler", "global top hits"}};
	private static final String RECENT_KEY = "ytm_recent";

	private static String pendingPlay;

	private YoldaStyle.Theme t;
	private LinearLayout content;
	private LinearLayout chips;
	private TextView status;
	private EditText search;
	private String chip = HOME;
	private int requestSeq;
	/** Liste görünümündeki şarkılar (arama / ruh hâli). */
	private final List<YtMusic.Track> listTracks = new ArrayList<>();
	private String listTitle;

	public static void playQuery(MainActivityDelegate a, String q) {
		pendingPlay = q;
		if (a.showFragment(R.id.yolda_music) instanceof MusicFragment f && (f.getView() != null)) f.consumePending();
	}

	@Override
	public int getFragmentId() {
		return R.id.yolda_music;
	}

	@Override
	public CharSequence getTitle() {
		return "Müzik";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance;
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance; // yüzen ≡ düğmesi yok
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		MainActivityDelegate a = getActivityDelegate();
		int side = YoldaStyle.compact(ctx) ? 16 : 24;

		FrameLayout root = new FrameLayout(ctx);
		YoldaStyle.applyScreenBackground(root, t);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(0, dp(12), 0, 0);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Üst çubuk: YouTube Music logosu + "Music", sağda durum
		LinearLayout top = new LinearLayout(ctx);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(side), 0, dp(side), 0);
		ImageView logo = new ImageView(ctx);
		logo.setImageResource(R.drawable.yi_brand_ytmusic);
		top.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
		TextView brand = text(ctx, "Music", 24, true, t.ink);
		brand.setLetterSpacing(-0.03f);
		brand.setPadding(dp(8), 0, 0, 0);
		top.addView(brand);
		status = text(ctx, "", 13, false, t.muted);
		status.setGravity(Gravity.END);
		status.setMaxLines(1);
		status.setEllipsize(TextUtils.TruncateAt.END);
		top.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
		col.addView(top);

		// Ruh hâli çipleri (YouTube Music'teki gibi kaydırılabilir)
		chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.setPadding(dp(side), 0, dp(side), 0);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(14);
		col.addView(hs, hlp);

		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(false);
		sv.setClipToPadding(false);
		content = new LinearLayout(ctx);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(0, dp(6), 0, dp(96));
		sv.addView(content);
		col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

		// Arama: altta, sesli asistan düğmesinin hemen sağında
		search = a.createEditText(ctx);
		search.setSingleLine();
		search.setHint("Şarkı, sanatçı ara");
		search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
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
		search.setBackground(YoldaStyle.searchField(ctx, t));
		search.setElevation(dp(8));
		search.setOnEditorActionListener((v, id, ev) -> {
			boolean enter = (ev != null) && (ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)
					&& (ev.getAction() == KeyEvent.ACTION_DOWN);
			if ((id == EditorInfo.IME_ACTION_SEARCH) || (id == EditorInfo.IME_ACTION_DONE) || enter) {
				String q = v.getText().toString().trim();
				if (!q.isEmpty()) {
					hideKeyboard(v);
					chip = null;
					showChips();
					showList("“" + q + "” için sonuçlar", q, false);
				}
				return true;
			}
			return false;
		});
		FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM);
		slp.setMargins(dp(22 + 56 + 12), 0, dp(22), dp(14));
		root.addView(search, slp);

		showChips();
		if (pendingPlay == null) showHome();
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
		consumePending();
	}

	@Override
	public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		View v = getView();
		if (v != null) v.post(() -> {
			if (!isAdded() || isStateSaved()) return;
			androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
			fm.beginTransaction().detach(this).commitNow();
			fm.beginTransaction().attach(this).commitNow();
		});
	}

	private void consumePending() {
		String q = pendingPlay;
		if ((q == null) || (content == null)) return;
		pendingPlay = null;
		chip = null;
		showChips();
		search.setText(q);
		showList("“" + q + "” için sonuçlar", q, true);
	}

	// ---- çipler ----

	private void showChips() {
		Context ctx = requireContext();
		chips.removeAllViews();
		List<String> all = new ArrayList<>();
		all.add(HOME);
		for (String[] m : MOODS) all.add(m[0]);
		for (String g : all) {
			boolean sel = g.equals(chip);
			TextView c = text(ctx, g, 15, true, sel ? 0xFF0B0E2A : t.ink);
			c.setPadding(dp(16), dp(9), dp(16), dp(9));
			if (sel) { // YouTube Music'teki gibi seçili çip beyaz
				GradientDrawable w = new GradientDrawable();
				w.setColor(0xF2FFFFFF);
				w.setCornerRadius(dp(10));
				c.setBackground(w);
			} else {
				c.setBackground(YoldaStyle.glass(ctx, t, 10));
			}
			c.setClickable(true);
			c.setOnClickListener(v -> selectChip(g));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
			if (chips.getChildCount() > 0) lp.leftMargin = dp(8);
			chips.addView(c, lp);
		}
	}

	private void selectChip(String g) {
		chip = g;
		showChips();
		if (HOME.equals(g)) {
			showHome();
			return;
		}
		for (String[] m : MOODS) {
			if (m[0].equals(g)) showList(m[0], m[1], false);
		}
	}

	// ---- ana sayfa: raflar ----

	private void showHome() {
		Context ctx = requireContext();
		int seq = ++requestSeq;
		content.removeAllViews();
		status.setText("");

		// Hızlı seçimler: son dinlenenler varsa onlar, yoksa Türkçe pop
		List<YtMusic.Track> recent = recent(ctx);
		LinearLayout quick = section(ctx, "Hızlı seçimler", recent.isEmpty() ? "Senin için başlangıç" : "Son dinlediklerinden");
		if (recent.isEmpty()) {
			YtMusic.search("türkçe pop hit").main().onCompletion((list, err) -> {
				if (!isAdded() || (seq != requestSeq)) return;
				fillQuick(ctx, quick, (list != null) ? list : new ArrayList<>(), err);
			});
		} else {
			fillQuick(ctx, quick, recent, null);
		}

		if (recent.size() >= 3) {
			LinearLayout again = section(ctx, "Tekrar dinle", null);
			fillShelf(ctx, again, recent, null);
		}

		for (String[] s : SHELVES) {
			LinearLayout shelf = section(ctx, s[0], null);
			YtMusic.search(s[1]).main().onCompletion((list, err) -> {
				if (!isAdded() || (seq != requestSeq)) return;
				fillShelf(ctx, shelf, (list != null) ? list : new ArrayList<>(), err);
			});
		}
	}

	/** Başlıklı bölüm; içerik sonradan dolar. */
	private LinearLayout section(Context ctx, String title, @Nullable String sub) {
		int side = YoldaStyle.compact(ctx) ? 16 : 24;
		LinearLayout s = new LinearLayout(ctx);
		s.setOrientation(LinearLayout.VERTICAL);
		if (sub != null) {
			TextView st = text(ctx, sub.toUpperCase(new java.util.Locale("tr")), 12, false, t.muted);
			st.setLetterSpacing(0.06f);
			st.setPadding(dp(side), dp(22), dp(side), 0);
			s.addView(st);
		}
		TextView h = text(ctx, title, 24, true, t.ink);
		h.setLetterSpacing(-0.02f);
		h.setPadding(dp(side), dp(sub == null ? 24 : 2), dp(side), dp(12));
		s.addView(h);
		TextView loading = text(ctx, "Yükleniyor…", 14, false, t.muted);
		loading.setPadding(dp(side), 0, dp(side), dp(8));
		s.addView(loading);
		content.addView(s);
		return s;
	}

	private void dropLoading(LinearLayout s) {
		View last = s.getChildAt(s.getChildCount() - 1);
		if (last instanceof TextView tv && "Yükleniyor…".contentEquals(tv.getText())) s.removeView(last);
	}

	/** Hızlı seçimler: 4'lü satır sütunları, yana kayar. */
	private void fillQuick(Context ctx, LinearLayout s, List<YtMusic.Track> list, @Nullable Throwable err) {
		dropLoading(s);
		if (list.isEmpty()) {
			s.addView(errorText(ctx, err));
			return;
		}
		int side = YoldaStyle.compact(ctx) ? 16 : 24;
		int colW = Math.min(dp(380), Math.round(YoldaStyle.widthDp(ctx) * getResources().getDisplayMetrics().density * 0.86f));
		LinearLayout cols = new LinearLayout(ctx);
		cols.setOrientation(LinearLayout.HORIZONTAL);
		cols.setPadding(dp(side), 0, dp(side), 0);
		List<YtMusic.Track> q = new ArrayList<>(list.subList(0, Math.min(16, list.size())));
		LinearLayout c = null;
		for (int i = 0; i < q.size(); i++) {
			if (i % 4 == 0) {
				c = new LinearLayout(ctx);
				c.setOrientation(LinearLayout.VERTICAL);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(colW, -2);
				if (i > 0) lp.leftMargin = dp(12);
				cols.addView(c, lp);
			}
			c.addView(row(ctx, q, i, 52), new LinearLayout.LayoutParams(-1, -2));
		}
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(cols);
		s.addView(hs);
	}

	/** Kare kapaklı kartlar rafı, yana kayar. */
	private void fillShelf(Context ctx, LinearLayout s, List<YtMusic.Track> list, @Nullable Throwable err) {
		dropLoading(s);
		if (list.isEmpty()) {
			s.addView(errorText(ctx, err));
			return;
		}
		int side = YoldaStyle.compact(ctx) ? 16 : 24;
		int w = YoldaStyle.compact(ctx) ? 150 : 180;
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setPadding(dp(side), 0, dp(side), 0);
		List<YtMusic.Track> q = new ArrayList<>(list.subList(0, Math.min(15, list.size())));
		for (int i = 0; i < q.size(); i++) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), -2);
			if (i > 0) lp.leftMargin = dp(14);
			r.addView(card(ctx, q, i), lp);
		}
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(r);
		s.addView(hs);
	}

	// ---- liste görünümü (arama / ruh hâli) ----

	private void showList(String title, String query, boolean playFirst) {
		Context ctx = requireContext();
		int seq = ++requestSeq;
		content.removeAllViews();
		LinearLayout s = section(ctx, title, null);
		status.setText("Aranıyor…");
		YtMusic.search(query).main().onCompletion((list, err) -> {
			if (!isAdded() || (seq != requestSeq)) return;
			dropLoading(s);
			listTracks.clear();
			if (list != null) listTracks.addAll(list);
			listTitle = title;
			status.setText(listTracks.isEmpty() ? "" : (listTracks.size() + " şarkı"));
			if (listTracks.isEmpty()) {
				s.addView(errorText(ctx, err));
				return;
			}
			int side = YoldaStyle.compact(ctx) ? 16 : 24;
			// Geniş ekranda iki sütun
			int cols = (YoldaStyle.widthDp(ctx) >= 900) ? 2 : 1;
			LinearLayout grid = new LinearLayout(ctx);
			grid.setOrientation(LinearLayout.HORIZONTAL);
			grid.setPadding(dp(side), 0, dp(side), 0);
			List<LinearLayout> colViews = new ArrayList<>();
			for (int c = 0; c < cols; c++) {
				LinearLayout cv = new LinearLayout(ctx);
				cv.setOrientation(LinearLayout.VERTICAL);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
				if (c > 0) lp.leftMargin = dp(18);
				grid.addView(cv, lp);
				colViews.add(cv);
			}
			List<YtMusic.Track> q = new ArrayList<>(listTracks);
			for (int i = 0; i < q.size(); i++) {
				colViews.get(i % cols).addView(row(ctx, q, i, 60), new LinearLayout.LayoutParams(-1, -2));
			}
			s.addView(grid);
			if (playFirst) play(q, 0);
		});
	}

	// ---- görünümler ----

	/** YouTube Music satırı: küçük kapak, ad, "Şarkı • sanatçı • süre". */
	private View row(Context ctx, List<YtMusic.Track> queue, int idx, int thumbDp) {
		YtMusic.Track tr = queue.get(idx);
		boolean playing = tr.id().equals(playingId());
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(dp(6), dp(7), dp(6), dp(7));
		r.setBackground(playing ? YoldaStyle.selected(ctx, 14) : rowBg(ctx));
		r.setClickable(true);
		r.setOnClickListener(v -> play(queue, idx));

		FrameLayout th = thumb(ctx, tr, 8, playing);
		r.addView(th, new LinearLayout.LayoutParams(dp(thumbDp), dp(thumbDp)));

		LinearLayout txt = new LinearLayout(ctx);
		txt.setOrientation(LinearLayout.VERTICAL);
		txt.setPadding(dp(14), 0, dp(6), 0);
		TextView n = text(ctx, tr.title(), 16, true, t.ink);
		n.setMaxLines(1);
		n.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(n);
		TextView sub = text(ctx, subtitle(tr), 13, false, t.muted);
		sub.setMaxLines(1);
		sub.setEllipsize(TextUtils.TruncateAt.END);
		sub.setPadding(0, dp(3), 0, 0);
		txt.addView(sub);
		r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
		return r;
	}

	/** Raf kartı: kare kapak, altında ad ve sanatçı. */
	private View card(Context ctx, List<YtMusic.Track> queue, int idx) {
		YtMusic.Track tr = queue.get(idx);
		boolean playing = tr.id().equals(playingId());
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setClickable(true);
		c.setOnClickListener(v -> play(queue, idx));
		c.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
			else if ((act == MotionEvent.ACTION_UP) || (act == MotionEvent.ACTION_CANCEL))
				v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
			return false;
		});
		SquareFrame sq = new SquareFrame(ctx);
		sq.addView(thumb(ctx, tr, 12, playing), new FrameLayout.LayoutParams(-1, -1));
		c.addView(sq, new LinearLayout.LayoutParams(-1, -2));
		TextView n = text(ctx, tr.title(), 15, true, t.ink);
		n.setMaxLines(2);
		n.setEllipsize(TextUtils.TruncateAt.END);
		n.setPadding(0, dp(9), 0, 0);
		c.addView(n);
		TextView a = text(ctx, (tr.artist() != null) ? tr.artist() : "", 13, false, t.muted);
		a.setMaxLines(1);
		a.setEllipsize(TextUtils.TruncateAt.END);
		a.setPadding(0, dp(2), 0, 0);
		c.addView(a);
		return c;
	}

	/** Kapak görseli: yüklenene kadar renkli zemin ve nota; çalıyorsa üstünde oynat işareti. */
	private FrameLayout thumb(Context ctx, YtMusic.Track tr, int radiusDp, boolean playing) {
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[]{0xFF8B5CF6, 0xFFEC4899});
		ph.setCornerRadius(dp(radiusDp));
		f.setBackground(ph);
		f.setClipToOutline(true);
		f.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(radiusDp));
			}
		});
		ImageView note = new ImageView(ctx);
		note.setImageResource(R.drawable.yi_music);
		note.setColorFilter(0xCCFFFFFF);
		note.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		note.setPadding(dp(8), dp(8), dp(8), dp(8));
		f.addView(note, new FrameLayout.LayoutParams(-1, -1));
		ImageView img = new ImageView(ctx);
		img.setScaleType(ImageView.ScaleType.CENTER_CROP);
		img.setVisibility(View.GONE);
		f.addView(img, new FrameLayout.LayoutParams(-1, -1));
		if (playing) {
			View dim = new View(ctx);
			dim.setBackgroundColor(0x66000000);
			f.addView(dim, new FrameLayout.LayoutParams(-1, -1));
			ImageView p = new ImageView(ctx);
			p.setImageResource(R.drawable.yi_play);
			p.setColorFilter(0xFFFFFFFF);
			f.addView(p, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
		}
		if (tr.thumb() != null) {
			getActivityDelegate().getLib().getBitmap(tr.thumb(), true, true).main().onCompletion((bmp, err) -> {
				if (!isAdded() || (bmp == null)) return;
				img.setImageBitmap(bmp);
				img.setVisibility(View.VISIBLE);
			});
		}
		return f;
	}

	private android.graphics.drawable.Drawable rowBg(Context ctx) {
		android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
		sl.addState(new int[]{android.R.attr.state_pressed}, YoldaStyle.selected(ctx, 14));
		sl.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
		return sl;
	}

	private TextView errorText(Context ctx, @Nullable Throwable err) {
		int side = YoldaStyle.compact(ctx) ? 16 : 24;
		TextView e = text(ctx, (err != null) ? ("Yüklenemedi: " + reason(err)) : "Sonuç yok", 14, false, t.muted);
		e.setPadding(dp(side), 0, dp(side), dp(8));
		return e;
	}

	private static String subtitle(YtMusic.Track tr) {
		StringBuilder b = new StringBuilder("Şarkı");
		if (tr.artist() != null && !tr.artist().isEmpty()) b.append(" • ").append(tr.artist());
		if (tr.durationSec() > 0) b.append(" • ").append(fmt(tr.durationSec()));
		return b.toString();
	}

	// ---- çalma ----

	/** Doğrudan çalma bu oturumda bir kez başarısız olduysa her şarkıda beklemeden YouTube oynatıcısı. */
	private static boolean directBroken;

	private void play(List<YtMusic.Track> list, int idx) {
		if ((idx < 0) || (idx >= list.size())) return;
		MainActivityDelegate a = getActivityDelegate();
		List<YtMusic.Track> queue = new ArrayList<>(list);
		YtMusic.Track tr = queue.get(idx);
		addRecent(requireContext(), tr);
		if (directBroken) {
			status.setText("Çalıyor: " + tr.title());
			playInYoutube(a, tr);
			return;
		}
		status.setText("Açılıyor…");
		YtMusicItem.create(a.getLib(), queue, idx).onCompletion((item, err) -> {
			if (!isAdded()) return;
			if ((err != null) || (item == null)) {
				// YouTube doğrudan ses akışı vermedi (ör. "bot değilim" doğrulaması): YouTube oynatıcısında çal
				Log.e(err, "Doğrudan çalınamadı, YouTube oynatıcısına geçiliyor");
				directBroken = true;
				status.setText("YouTube oynatıcısında (" + reason(err) + ")");
				playInYoutube(a, tr);
				return;
			}
			a.getBody().playItem(item);
			status.setText("Çalıyor: " + tr.title());
			checkStarted(a, tr, 0);
		});
	}

	/** Çalma başladı mı: 6 ve 12. saniyede bakılır; başlamadıysa durdurup YouTube oynatıcısına geçilir. */
	private void checkStarted(MainActivityDelegate a, YtMusic.Track tr, int attempt) {
		content.postDelayed(() -> {
			if (!isAdded()) return;
			MediaLib.PlayableItem cur = a.getCurrentPlayable();
			boolean mine = (cur instanceof YtMusicItem m) && m.getTrack().id().equals(tr.id());
			if (!mine) return; // bu arada başka bir şey seçildi
			if (a.getMediaSessionCallback().isPlaying()) {
				refresh();
				return;
			}
			if (attempt == 0) {
				status.setText("Bağlanıyor…");
				checkStarted(a, tr, 1);
				return;
			}
			directBroken = true;
			try {
				a.getMediaSessionCallback().onStop();
			} catch (Throwable ignore) {
			}
			status.setText("YouTube oynatıcısında");
			playInYoutube(a, tr);
		}, 6000);
	}

	/** Yedek yol: şarkıyı YouTube sekmesinin oynatıcısında aç, sonra Müzik ekranına dön (ses sürer). */
	private void playInYoutube(MainActivityDelegate a, YtMusic.Track tr) {
		String url = "https://m.youtube.com/watch?v=" + tr.id();
		Object f = a.showFragment(R.id.youtube_fragment);
		if (f == null) return;
		loadInto(f, url);
		// Sekme ilk kez açılıyorsa kendi ana sayfasını yükleyip bizimkinin üstüne yazabilir: kısa süre sonra yeniden dene
		me.aap.utils.app.App.get().getHandler().postDelayed(() -> {
			try {
				Object u = f.getClass().getMethod("getUrl").invoke(f);
				if (!(u instanceof String s) || !s.contains(tr.id())) loadInto(f, url);
			} catch (Exception ignore) {
				loadInto(f, url);
			}
		}, 800);
		me.aap.utils.app.App.get().getHandler().postDelayed(() -> {
			if (a.getActiveFragmentId() == R.id.youtube_fragment) a.showFragment(R.id.yolda_music);
		}, 4000);
	}

	private static void loadInto(Object f, String url) {
		try {
			f.getClass().getMethod("loadUrl", String.class).invoke(f, url);
		} catch (Exception ex) {
			Log.e(ex, "YouTube oynatıcısı açılamadı");
		}
	}

	private void refresh() {
		if (HOME.equals(chip)) return; // raflar yeniden yüklenmesin
		if (!listTracks.isEmpty() && (listTitle != null)) {
			// yalnız işaretleri güncellemek için listeyi yeniden çiz (ağ isteği yok)
			Context ctx = requireContext();
			content.removeAllViews();
			LinearLayout s = section(ctx, listTitle, null);
			dropLoading(s);
			int side = YoldaStyle.compact(ctx) ? 16 : 24;
			LinearLayout l = new LinearLayout(ctx);
			l.setOrientation(LinearLayout.VERTICAL);
			l.setPadding(dp(side), 0, dp(side), 0);
			List<YtMusic.Track> q = new ArrayList<>(listTracks);
			for (int i = 0; i < q.size(); i++) l.addView(row(ctx, q, i, 60), new LinearLayout.LayoutParams(-1, -2));
			s.addView(l);
		}
	}

	@Nullable
	private String playingId() {
		try {
			MediaLib.PlayableItem cur = getActivityDelegate().getCurrentPlayable();
			if (cur instanceof YtMusicItem m) return m.getTrack().id();
		} catch (Exception ignore) {
		}
		return null;
	}

	private static String reason(@Nullable Throwable err) {
		if (err == null) return "bilinmeyen hata";
		String n = err.getClass().getSimpleName();
		if (n.contains("NotBot") || n.contains("SignIn")) return "YouTube doğrulama istedi";
		if (n.contains("ReCaptcha")) return "çok fazla istek";
		if ((err instanceof java.io.IOException) || n.contains("Timeout")) return "bağlantı sorunu";
		if (n.contains("ContentNotAvailable") || n.contains("Private")) return "şarkı erişilemez";
		String m = err.getMessage();
		if (m != null) {
			m = m.replaceAll("\\s+", " ").trim();
			if (m.length() > 70) m = m.substring(0, 70) + "…";
			return n + ": " + m;
		}
		return n;
	}

	// ---- son dinlenenler ----

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	static List<YtMusic.Track> recent(Context ctx) {
		List<YtMusic.Track> l = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(prefs(ctx).getString(RECENT_KEY, "[]"));
			for (int i = 0; i < a.length(); i++) {
				JSONObject o = a.getJSONObject(i);
				l.add(new YtMusic.Track(o.getString("u"), o.getString("t"), o.optString("a", null),
						o.optString("i", null), o.optLong("d", 0)));
			}
		} catch (Exception ignore) {
		}
		return l;
	}

	private static void addRecent(Context ctx, YtMusic.Track tr) {
		List<YtMusic.Track> l = recent(ctx);
		l.removeIf(x -> x.id().equals(tr.id()));
		l.add(0, tr);
		JSONArray a = new JSONArray();
		for (int i = 0; i < Math.min(30, l.size()); i++) {
			YtMusic.Track x = l.get(i);
			try {
				JSONObject o = new JSONObject();
				o.put("u", x.url());
				o.put("t", x.title());
				if (x.artist() != null) o.put("a", x.artist());
				if (x.thumb() != null) o.put("i", x.thumb());
				o.put("d", x.durationSec());
				a.put(o);
			} catch (Exception ignore) {
			}
		}
		prefs(ctx).edit().putString(RECENT_KEY, a.toString()).apply();
	}

	// ---- yardımcılar ----

	private static String fmt(long s) {
		return (s / 60) + ":" + ((s % 60 < 10) ? "0" : "") + (s % 60);
	}

	private void hideKeyboard(View v) {
		InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
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

	/** Genişliği kadar yüksek kare. */
	private static final class SquareFrame extends FrameLayout {
		SquareFrame(Context ctx) {
			super(ctx);
		}

		@Override
		protected void onMeasure(int w, int h) {
			super.onMeasure(w, w);
		}
	}
}
