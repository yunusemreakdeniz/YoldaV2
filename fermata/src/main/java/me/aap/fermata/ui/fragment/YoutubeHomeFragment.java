package me.aap.fermata.ui.fragment;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;

/**
 * Yolda'nın YouTube ekranı: araç ekranına uygun büyük video kartları, kategori çipleri ve arama.
 * Mobil YouTube sitesi araçta kullanışsız olduğundan liste Yolda'da çizilir; dokunulan video
 * YouTube oynatıcısında (Fermata YouTube eklentisi) açılır.
 */
public class YoutubeHomeFragment extends MainActivityFragment {
	private static final String[][] CATS = {
			{"Senin için", "türkiye gündem"}, {"Haber", "haber son dakika"}, {"Müzik", "türkçe müzik klip"},
			{"Spor", "maç özetleri"}, {"Komedi", "komedi skeç"}, {"Belgesel", "belgesel türkçe"},
			{"Çocuk", "çocuk çizgi film"}, {"Yemek", "yemek tarifleri"}, {"Otomobil", "otomobil inceleme"}};

	private static String pendingQuery;

	private YoldaStyle.Theme t;
	private LinearLayout chips;
	private LinearLayout grid;
	private TextView status;
	private EditText search;
	private String chip = CATS[0][0];
	private int seq;

	/** Sesli komut: "<şey> youtube'da ara" gibi istekler. */
	public static void searchFor(MainActivityDelegate a, String q) {
		pendingQuery = q;
		if (a.showFragment(R.id.yolda_youtube) instanceof YoutubeHomeFragment f && (f.grid != null)) f.consume();
	}

	@Override
	public int getFragmentId() {
		return R.id.yolda_youtube;
	}

	@Override
	public CharSequence getTitle() {
		return "YouTube";
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
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		MainActivityDelegate a = getActivityDelegate();
		int side = YoldaStyle.compact(ctx) ? 16 : 22;

		FrameLayout root = new FrameLayout(ctx);
		YoldaStyle.applyScreenBackground(root, t);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(0, dp(10), 0, 0);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Üst: logo, durum, "YouTube'u aç" (tam site gerekirse)
		LinearLayout top = new LinearLayout(ctx);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(side), 0, dp(side), 0);
		ImageView logo = new ImageView(ctx);
		logo.setImageResource(R.drawable.yi_brand_youtube);
		top.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(26)));
		TextView brand = text(ctx, "YouTube", 22, true, t.ink);
		brand.setPadding(dp(8), 0, 0, 0);
		top.addView(brand);
		status = text(ctx, "", 13, false, t.muted);
		status.setGravity(Gravity.END);
		status.setMaxLines(1);
		status.setEllipsize(TextUtils.TruncateAt.END);
		status.setPadding(dp(8), 0, dp(10), 0);
		top.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
		TextView full = text(ctx, "Tam site", 14, true, t.ink);
		full.setPadding(dp(14), dp(8), dp(14), dp(8));
		full.setBackground(YoldaStyle.glass(ctx, t, 999));
		full.setOnClickListener(v -> a.showFragment(R.id.youtube_fragment));
		boolean car = a.isCarActivity();
		if (car) { // araç: arama sağ üstte (içeriğin üstüne binen yüzen kutu yok)
			TextView ara = text(ctx, "Ara", 15, true, t.ink);
			ara.setGravity(Gravity.CENTER);
			ara.setMinHeight(dp(48));
			ara.setPadding(dp(20), 0, dp(20), 0);
			ara.setBackground(YoldaStyle.glass(ctx, t, 999));
			ara.setOnClickListener(v -> toggleSearch());
			LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, dp(48));
			alp.rightMargin = dp(10);
			top.addView(ara, alp);
		}
		full.setMinHeight(dp(48));
		full.setGravity(Gravity.CENTER);
		top.addView(full);
		col.addView(top);

		chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.setPadding(dp(side), 0, dp(side), 0);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(10);
		col.addView(hs, hlp);

		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(false);
		sv.setClipToPadding(false);
		grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		grid.setPadding(dp(side), dp(12), dp(side), dp(190));
		sv.addView(grid);
		col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

		search = a.createEditText(ctx);
		search.setSingleLine();
		search.setHint("YouTube'da ara");
		search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		android.graphics.drawable.Drawable sIcon = ctx.getDrawable(R.drawable.yi_search);
		if (sIcon != null) {
			sIcon = sIcon.mutate();
			sIcon.setTint(t.muted);
			sIcon.setBounds(0, 0, dp(22), dp(22));
			search.setCompoundDrawablesRelative(sIcon, null, null, null);
			search.setCompoundDrawablePadding(dp(10));
		}
		search.setTextSize(17 * YoldaStyle.textScale(ctx));
		search.setTextColor(t.ink);
		search.setHintTextColor(t.muted);
		search.setPadding(dp(18), 0, dp(18), 0);
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
					load("“" + q + "”", q);
				}
				return true;
			}
			return false;
		});
		FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(YoldaStyle.DECK ? YoldaStyle.VOICE_DP : 52), Gravity.BOTTOM);
		slp.setMargins(dp(YoldaStyle.searchLeft(ctx)), 0, dp(22), dp(YoldaStyle.DECK ? YoldaStyle.VOICE_BOTTOM_DP : 14));
		if (car) {
			search.setVisibility(View.GONE);
			search.setElevation(0);
			LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(52));
			sl.setMargins(dp(side), dp(10), dp(side), 0);
			col.addView(search, 1, sl);
			grid.setPadding(dp(side), dp(12), dp(side), dp(20));
		} else {
			root.addView(YoldaStyle.bottomScrim(ctx, t), YoldaStyle.bottomScrimLp(ctx));
			root.addView(search, slp);
		}

		showChips();
		if (pendingQuery == null) load(CATS[0][0], CATS[0][1]);
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
		consume();
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

	private void toggleSearch() {
		if (search == null) return;
		InputMethodManager imm = (InputMethodManager) search.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (search.getVisibility() == View.VISIBLE) {
			search.setVisibility(View.GONE);
			if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
		} else {
			search.setVisibility(View.VISIBLE);
			search.requestFocus();
			if (imm != null) imm.showSoftInput(search, 0);
		}
	}

	private void consume() {
		String q = pendingQuery;
		if ((q == null) || (grid == null)) return;
		pendingQuery = null;
		chip = null;
		showChips();
		search.setText(q);
		load("“" + q + "”", q);
	}

	private void showChips() {
		Context ctx = requireContext();
		chips.removeAllViews();
		for (String[] c : CATS) {
			boolean sel = c[0].equals(chip);
			TextView v = text(ctx, c[0], 15, true, sel ? 0xFF0B0E2A : t.ink);
			v.setPadding(dp(15), dp(8), dp(15), dp(8));
			v.setMinHeight(dp(getActivityDelegate().isCarActivity() ? 52 : 44));
			v.setGravity(Gravity.CENTER);
			if (sel) {
				GradientDrawable w = new GradientDrawable();
				w.setColor(0xF2FFFFFF);
				w.setCornerRadius(dp(10));
				v.setBackground(w);
			} else {
				v.setBackground(YoldaStyle.glass(ctx, t, 10));
			}
			v.setOnClickListener(x -> {
				chip = c[0];
				showChips();
				load(c[0], c[1]);
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
			if (chips.getChildCount() > 0) lp.leftMargin = dp(8);
			chips.addView(v, lp);
		}
	}

	private void load(String title, String query) {
		int s = ++seq;
		Context ctx = requireContext();
		grid.removeAllViews();
		status.setText("Yükleniyor…");
		// yüklenirken yer tutucu kartlar (sayfa boş görünmesin)
		showCards(ctx, null);
		YtMusic.searchVideos(query).main().onCompletion((list, err) -> {
			if (!isAdded() || (s != seq)) return;
			if ((err != null) || (list == null) || list.isEmpty()) {
				grid.removeAllViews();
				status.setText((err != null) ? "Bağlantı sorunu" : "Sonuç yok");
				if (err != null) {
					Log.e(err, "YouTube listesi yüklenemedi");
					YoldaLog.add("YouTube listesi: " + err);
				}
				return;
			}
			boolean isCat = false; // seçili konu zaten çipte görünüyor: iki kez yazmasın; aramada aranan görünür
			for (String[] c : CATS) isCat |= c[0].equals(title);
			status.setText(isCat ? "" : title);
			showCards(ctx, list);
		});
	}

	/** Kartlar: geniş ekranda 3-4 sütun, telefonda tek sütun büyük kart. */
	private void showCards(Context ctx, @Nullable List<YtMusic.Track> list) {
		grid.removeAllViews();
		int w = YoldaStyle.widthDp(ctx);
		int cols = Math.max(1, Math.min(4, w / 280));
		int n = (list == null) ? cols * 2 : list.size();
		LinearLayout row = null;
		for (int i = 0; i < n; i++) {
			if (i % cols == 0) {
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
				rlp.bottomMargin = dp(16);
				grid.addView(row, rlp);
			}
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
			if (i % cols != 0) lp.leftMargin = dp(14);
			row.addView((list == null) ? placeholder(ctx) : card(ctx, list.get(i)), lp);
		}
		if (row != null) {
			for (int i = n % cols; (i > 0) && (i < cols); i++) {
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
				lp.leftMargin = dp(14);
				row.addView(new View(ctx), lp);
			}
		}
	}

	private View card(Context ctx, YtMusic.Track tr) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setClickable(true);
		c.setOnClickListener(v -> play(tr));
		c.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
			else if ((act == MotionEvent.ACTION_UP) || (act == MotionEvent.ACTION_CANCEL))
				v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
			return false;
		});

		// 16:9 kapak, köşede süre
		WideFrame th = new WideFrame(ctx);
		GradientDrawable ph = new GradientDrawable();
		ph.setColor(0x33FFFFFF);
		ph.setCornerRadius(dp(14));
		th.setBackground(ph);
		th.setClipToOutline(true);
		th.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
			}
		});
		ImageView img = new ImageView(ctx);
		img.setScaleType(ImageView.ScaleType.CENTER_CROP);
		img.setAlpha(0f);
		th.addView(img, new FrameLayout.LayoutParams(-1, -1));
		if (tr.durationSec() > 0) {
			TextView d = text(ctx, MusicFragment.fmt(tr.durationSec()), 12, true, 0xFFFFFFFF);
			d.setPadding(dp(6), dp(2), dp(6), dp(2));
			GradientDrawable db = new GradientDrawable();
			db.setColor(0xCC000000);
			db.setCornerRadius(dp(6));
			d.setBackground(db);
			FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
			dlp.setMargins(0, 0, dp(8), dp(8));
			th.addView(d, dlp);
		}
		c.addView(th, new LinearLayout.LayoutParams(-1, -2));
		if (tr.thumb() != null) {
			getActivityDelegate().getLib().getBitmap(tr.thumb(), true, true).main().onCompletion((bmp, err) -> {
				if (!isAdded() || (bmp == null)) return;
				img.setImageBitmap(bmp);
				img.animate().alpha(1f).setDuration(200).start(); // kapak yumuşakça belirir
			});
		}

		TextView title = text(ctx, tr.title(), 15, true, t.ink);
		title.setMaxLines(2);
		title.setEllipsize(TextUtils.TruncateAt.END);
		title.setPadding(dp(2), dp(8), dp(2), 0);
		c.addView(title);
		TextView ch = text(ctx, (tr.artist() != null) ? tr.artist() : "", 13, false, t.muted);
		ch.setMaxLines(1);
		ch.setEllipsize(TextUtils.TruncateAt.END);
		ch.setPadding(dp(2), dp(2), dp(2), 0);
		c.addView(ch);
		return c;
	}

	private View placeholder(Context ctx) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		WideFrame th = new WideFrame(ctx);
		GradientDrawable ph = new GradientDrawable();
		ph.setColor(0x26FFFFFF);
		ph.setCornerRadius(dp(14));
		th.setBackground(ph);
		c.addView(th, new LinearLayout.LayoutParams(-1, -2));
		View l1 = new View(ctx);
		GradientDrawable lb = new GradientDrawable();
		lb.setColor(0x1FFFFFFF);
		lb.setCornerRadius(dp(6));
		l1.setBackground(lb);
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, dp(14));
		llp.topMargin = dp(10);
		llp.rightMargin = dp(30);
		c.addView(l1, llp);
		c.setAlpha(0.5f);
		c.animate().alpha(1f).setDuration(600).withEndAction(() -> c.animate().alpha(0.5f).setDuration(600).start()).start();
		return c;
	}

	/** Videoyu YouTube oynatıcısında aç. */
	private void play(YtMusic.Track tr) {
		MainActivityDelegate a = getActivityDelegate();
		String url = "https://m.youtube.com/watch?v=" + tr.id();
		Object f = a.showFragment(R.id.youtube_fragment);
		if (f == null) return;
		try { // doğrudan tam ekran (yoksa sayfa olarak)
			f.getClass().getMethod("loadVideo", String.class).invoke(f, url);
		} catch (NoSuchMethodException nm) {
			try {
				f.getClass().getMethod("loadUrl", String.class).invoke(f, url);
			} catch (Exception ex) {
				Log.e(ex, "Video açılamadı");
			}
		} catch (Exception ex) {
			Log.e(ex, "Video açılamadı");
		}
	}

	private void hideKeyboard(View v) {
		InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size * YoldaStyle.textScale(ctx));
		tv.setTextColor(color);
		if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
		return tv;
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}

	/** 16:9 oranlı çerçeve. */
	private static final class WideFrame extends FrameLayout {
		WideFrame(Context ctx) {
			super(ctx);
		}

		@Override
		protected void onMeasure(int w, int h) {
			int width = MeasureSpec.getSize(w);
			super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
		}
	}
}
