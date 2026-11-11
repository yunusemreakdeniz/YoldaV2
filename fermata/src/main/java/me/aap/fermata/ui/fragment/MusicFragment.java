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

/**
 * Yolda Müzik: YouTube Music araması ve şarkı kartları; tarayıcı yok, reklam yok.
 * Şarkı Yolda'nın kendi oynatıcısında çalar (alttaki oynatıcı, araç ekranı, direksiyon tuşları),
 * liste bitene kadar sıradaki şarkıya kendiliğinden geçer.
 */
public class MusicFragment extends MainActivityFragment {
	private static final String RECENT = "Son dinlenenler";
	private static final String[] GENRES = {"Türkçe Pop", "Yeni Çıkanlar", "Arabesk", "Türkçe Rap", "Türkçe Rock",
			"Slow Şarkılar", "90'lar Türkçe", "Yabancı Hitler", "Lo-fi", "Türk Halk Müziği", "Klasik"};
	private static final String RECENT_KEY = "ytm_recent";

	/** Sesli komuttan gelen "… çal" isteği; ekran açılınca aranıp ilk şarkı çalınır. */
	private static String pendingPlay;

	private final List<YtMusic.Track> tracks = new ArrayList<>();
	private YoldaStyle.Theme t;
	private LinearLayout grid;
	private LinearLayout chips;
	private TextView status;
	private EditText search;
	private String chip;
	private int requestSeq;

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

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		MainActivityDelegate a = getActivityDelegate();

		FrameLayout root = new FrameLayout(ctx);
		YoldaStyle.applyScreenBackground(root, t);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(YoldaStyle.side(ctx) > 20 ? 24 : 18), dp(14), dp(YoldaStyle.side(ctx) > 20 ? 24 : 18), 0);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Başlık
		TextView big = text(ctx, "Müzik", YoldaStyle.compact(ctx) ? 32 : 40, true, t.ink);
		big.setLetterSpacing(-0.025f);
		col.addView(big);
		status = text(ctx, "YouTube Music · reklamsız", 14, false, t.muted);
		col.addView(status);

		// Tür çipleri
		chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(12);
		col.addView(hs, hlp);

		// Şarkı kartları
		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(false);
		sv.setClipToPadding(false);
		grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		grid.setPadding(0, dp(14), 0, dp(96));
		sv.addView(grid);
		col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

		// Arama: altta, sesli asistan düğmesinin hemen sağında
		search = a.createEditText(ctx);
		search.setSingleLine();
		search.setHint("Şarkı, sanatçı ya da albüm ara");
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
		GradientDrawable sbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{(t.base & 0x00FFFFFF) | 0xCC000000, (t.base & 0x00FFFFFF) | 0xE6000000});
		sbg.setCornerRadius(dp(999));
		sbg.setStroke(Math.max(1, dp(1)), t.edge);
		search.setBackground(YoldaStyle.pill(ctx, t)); // Navigasyon aramasıyla aynı cam
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
					runSearch(q, false);
				}
				return true;
			}
			return false;
		});
		FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM);
		slp.setMargins(dp(22 + 56 + 12), 0, dp(22), dp(14));
		root.addView(search, slp);

		chip = recent(ctx).isEmpty() ? GENRES[0] : RECENT;
		showChips();
		if (pendingPlay == null) selectChip(chip);
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
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) showGrid(); // çalan şarkının işareti güncellensin
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
		if ((q == null) || (grid == null)) return;
		pendingPlay = null;
		chip = null;
		showChips();
		search.setText(q);
		runSearch(q, true);
	}

	// ---- çipler ----

	private void showChips() {
		Context ctx = requireContext();
		chips.removeAllViews();
		List<String> all = new ArrayList<>();
		if (!recent(ctx).isEmpty()) all.add(RECENT);
		for (String g : GENRES) all.add(g);
		for (String g : all) {
			boolean sel = g.equals(chip);
			TextView c = text(ctx, g, 15, true, t.ink);
			c.setPadding(dp(18), dp(10), dp(18), dp(10));
			c.setBackground(sel ? YoldaStyle.selected(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
			c.setClickable(true);
			c.setOnClickListener(v -> selectChip(g));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
			if (chips.getChildCount() > 0) lp.leftMargin = dp(10);
			chips.addView(c, lp);
		}
	}

	private void selectChip(String g) {
		chip = g;
		showChips();
		if (RECENT.equals(g)) {
			requestSeq++;
			tracks.clear();
			tracks.addAll(recent(requireContext()));
			status.setText(tracks.size() + " şarkı · son dinlediklerin");
			showGrid();
		} else {
			runSearch(g + ("Klasik".equals(g) ? " müzik" : " şarkılar"), false);
		}
	}

	// ---- arama ----

	private void runSearch(String q, boolean playFirst) {
		int seq = ++requestSeq;
		status.setText("Aranıyor: " + q + "…");
		YtMusic.search(q).main().onCompletion((list, err) -> {
			if (!isAdded() || (seq != requestSeq)) return;
			if ((err != null) || (list == null)) {
				status.setText("Bağlantı sorunu, tekrar dene");
				return;
			}
			tracks.clear();
			tracks.addAll(list);
			status.setText(list.isEmpty() ? "Sonuç bulunamadı" : (list.size() + " şarkı · reklamsız"));
			showGrid();
			if (playFirst && !list.isEmpty()) play(0);
		});
	}

	// ---- kartlar ----

	private void showGrid() {
		if (grid == null) return;
		Context ctx = requireContext();
		grid.removeAllViews();
		int cols = Math.max(2, Math.min(6, YoldaStyle.widthDp(ctx) / 170));
		String playing = playingId();
		LinearLayout row = null;
		for (int i = 0; i < tracks.size(); i++) {
			if (i % cols == 0) {
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
				rlp.bottomMargin = dp(14);
				grid.addView(row, rlp);
			}
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
			if (i % cols != 0) lp.leftMargin = dp(12);
			YtMusic.Track tr = tracks.get(i);
			row.addView(card(ctx, tr, i, tr.id().equals(playing)), lp);
		}
		if (row != null) {
			for (int i = tracks.size() % cols; (i > 0) && (i < cols); i++) {
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
				lp.leftMargin = dp(12);
				row.addView(new View(ctx), lp);
			}
		}
	}

	private View card(Context ctx, YtMusic.Track tr, int idx, boolean playing) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setClickable(true);
		c.setOnClickListener(v -> play(idx));
		c.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == android.view.MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
			else if ((act == android.view.MotionEvent.ACTION_UP) || (act == android.view.MotionEvent.ACTION_CANCEL))
				v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
			return false;
		});

		// Kapak: kare, yuvarlak köşe; çalıyorsa parlayan çerçeve ve dalga simgesi
		SquareFrame art = new SquareFrame(ctx);
		GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[]{0xFF8B5CF6, 0xFFEC4899});
		ph.setCornerRadius(dp(22));
		art.setBackground(ph);
		art.setClipToOutline(true);
		art.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(22));
			}
		});
		art.setElevation(dp(4));
		ImageView note = new ImageView(ctx);
		note.setImageResource(R.drawable.yi_music);
		note.setColorFilter(0xCCFFFFFF);
		art.addView(note, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
		ImageView img = new ImageView(ctx);
		img.setScaleType(ImageView.ScaleType.CENTER_CROP);
		img.setVisibility(View.GONE);
		art.addView(img, new FrameLayout.LayoutParams(-1, -1));
		if (playing) {
			View ring = new View(ctx);
			GradientDrawable rb = new GradientDrawable();
			rb.setCornerRadius(dp(22));
			rb.setStroke(dp(3), 0xFFFFFFFF);
			ring.setBackground(rb);
			art.addView(ring, new FrameLayout.LayoutParams(-1, -1));
			FrameLayout badge = new FrameLayout(ctx);
			badge.setBackground(YoldaStyle.selected(ctx, 999));
			ImageView eq = new ImageView(ctx);
			eq.setImageResource(R.drawable.yi_play);
			eq.setColorFilter(0xFFFFFFFF);
			badge.addView(eq, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
			FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.BOTTOM | Gravity.END);
			blp.setMargins(0, 0, dp(8), dp(8));
			art.addView(badge, blp);
		}
		c.addView(art, new LinearLayout.LayoutParams(-1, -2));

		if (tr.thumb() != null) {
			getActivityDelegate().getLib().getBitmap(tr.thumb(), true, true).main().onCompletion((bmp, err) -> {
				if (!isAdded() || (bmp == null)) return;
				img.setImageBitmap(bmp);
				img.setVisibility(View.VISIBLE);
			});
		}

		TextView name = text(ctx, tr.title(), 15, true, 0xFFFFFFFF);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		name.setPadding(dp(4), dp(10), dp(4), 0);
		c.addView(name, new LinearLayout.LayoutParams(-1, -2));
		String sub = (tr.artist() != null) ? tr.artist() : "";
		if (tr.durationSec() > 0) sub += (sub.isEmpty() ? "" : " · ") + fmt(tr.durationSec());
		TextView art2 = text(ctx, sub, 12, false, 0xB3FFFFFF);
		art2.setMaxLines(1);
		art2.setEllipsize(TextUtils.TruncateAt.END);
		art2.setPadding(dp(4), dp(2), dp(4), 0);
		c.addView(art2, new LinearLayout.LayoutParams(-1, -2));
		return c;
	}

	// ---- çalma ----

	private void play(int idx) {
		if ((idx < 0) || (idx >= tracks.size())) return;
		MainActivityDelegate a = getActivityDelegate();
		List<YtMusic.Track> queue = new ArrayList<>(tracks);
		YtMusic.Track tr = queue.get(idx);
		status.setText("Açılıyor: " + tr.title() + "…");
		addRecent(requireContext(), tr);
		YtMusicItem.create(a.getLib(), queue, idx).onCompletion((item, err) -> {
			if (!isAdded()) return;
			if ((err != null) || (item == null)) {
				status.setText("Bu şarkı açılamadı, başka birini dene");
				return;
			}
			a.getBody().playItem(item);
			status.setText("Çalıyor: " + tr.title());
			grid.postDelayed(this::showGrid, 600);
		});
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

	/** Genişliği kadar yüksek kare kapak. */
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
