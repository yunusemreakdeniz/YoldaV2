package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Yolda ana ekranı: büyük, renk geçişli kartlarla TV, YouTube, tarayıcı, favoriler, videolar ve ayarlar.
 * Araç ekranında tek dokunuşla basılabilecek büyüklükte; dar ekranda 2, geniş ekranda 3 sütun.
 */
public class HomeFragment extends MainActivityFragment {

	private static final int BG = 0xFF0F1115;
	private static final int INK = 0xFFECEFF4;
	private static final int MUTED = 0xFF9AA3AF;

	@Override
	public int getFragmentId() {
		return R.id.home_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Yolda";
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return build(requireContext());
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

	private View build(Context ctx) {
		ScrollView scroll = new ScrollView(ctx);
		scroll.setBackgroundColor(BG);
		scroll.setFillViewport(true);

		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(ctx, 20);
		col.setPadding(pad, dp(ctx, 16), pad, pad);
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

		// Üst şerit: selam + saat/tarih
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout hello = new LinearLayout(ctx);
		hello.setOrientation(LinearLayout.VERTICAL);
		hello.addView(text(ctx, greeting(), 26, true, INK));
		TextClock date = new TextClock(ctx);
		date.setFormat24Hour("d MMMM EEEE");
		date.setFormat12Hour("d MMMM EEEE");
		date.setTextSize(15);
		date.setTextColor(MUTED);
		hello.addView(date);
		head.addView(hello, new LinearLayout.LayoutParams(0, -2, 1f));
		TextClock clock = new TextClock(ctx);
		clock.setFormat24Hour("HH:mm");
		clock.setFormat12Hour("HH:mm");
		clock.setTextSize(40);
		clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
		clock.setTextColor(INK);
		head.addView(clock);
		col.addView(head);

		MainActivityDelegate a = getActivityDelegate();

		// Kaldığın yerden devam
		if (a.hasCurrent()) {
			View resume = card(ctx, "▶", "Kaldığın yerden devam", "Son izlediğin yayın",
					0xFF2E7D32, 0xFF1B5E20, true, v -> a.goToCurrent());
			LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(ctx, 88));
			rlp.topMargin = dp(ctx, 18);
			col.addView(resume, rlp);
		}

		// Sesli komut: "TRT Haber'i aç", "YouTube'da Habertürk aç", "durdur", "favorileri oynat"
		View voice = card(ctx, "🎤", "Sesli komut", "“TRT Haber'i aç” · “YouTube'da Habertürk aç” · “durdur”",
				0xFF5E35B1, 0xFF311B92, true, v -> a.startVoiceAssistant());
		LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, dp(ctx, 88));
		vlp.topMargin = dp(ctx, 12);
		col.addView(voice, vlp);

		// Ana kartlar
		GridLayout grid = new GridLayout(ctx);
		int cols = ctx.getResources().getConfiguration().screenWidthDp >= 840 ? 3 : 2;
		grid.setColumnCount(cols);
		LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
		glp.topMargin = dp(ctx, 18);
		col.addView(grid, glp);

		AddonManager am = AddonManager.get();
		if (am.hasAddon(R.id.tv_fragment))
			addCard(grid, card(ctx, "📺", "TV", "Canlı kanallar", 0xFFE53935, 0xFF8E1B18, false,
					v -> a.showFragment(R.id.tv_fragment)));
		if (am.hasAddon(R.id.youtube_fragment))
			addCard(grid, card(ctx, "▶️", "YouTube", "Videolar ve canlı yayınlar", 0xFFFF0033, 0xFF7A0019, false,
					v -> a.showFragment(R.id.youtube_fragment)));
		if (am.hasAddon(R.id.web_browser_fragment))
			addCard(grid, card(ctx, "🌐", "Tarayıcı", "İnternette gezin", 0xFF1E88E5, 0xFF0D3C7A, false,
					v -> a.showFragment(R.id.web_browser_fragment)));
		addCard(grid, card(ctx, "⭐", "Favoriler", "Kaydettiklerin", 0xFFF9A825, 0xFF8A5A00, false,
				v -> a.showFragment(R.id.favorites_fragment)));
		addCard(grid, card(ctx, "🎬", "Videolar", "Telefondaki dosyalar", 0xFF8E24AA, 0xFF4A148C, false,
				v -> a.showFragment(R.id.folders_fragment)));
		addCard(grid, card(ctx, "⚙️", "Ayarlar", "Eklentiler, görünüm", 0xFF455A64, 0xFF263238, false,
				v -> a.showFragment(R.id.settings_fragment)));

		// YouTube'da canlı yayın yapan kanallar: tek dokunuşla canlı yayın
		if (am.hasAddon(R.id.youtube_fragment)) {
			TextView yt = text(ctx, "YouTube'da canlı", 17, true, INK);
			yt.setPadding(dp(ctx, 4), dp(ctx, 10), 0, dp(ctx, 8));
			col.addView(yt);
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			String[][] live = {
					{"Habertürk", "UCK3mI2lsk3LSo8PBUc8JTSw"}, {"CNN Türk", "UCV6zcRug6Hqp1UX_FdyUeBg"},
					{"Show TV", "UC9JMe_We017gYrRc7kZHgmg"}, {"Haber Global", "UCtc-a9ZUIg0_5HpsPxEO7Qg"},
					{"TVNET", "UC8rh34IlJTN0lDZlTwzWzjg"}, {"tv100", "UCndsdUW_oPLqpQJY9J8oIRg"},
			};
			for (String[] ch : live) {
				TextView chip = text(ctx, "🔴 " + ch[0], 16, true, Color.WHITE);
				chip.setGravity(Gravity.CENTER);
				chip.setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14));
				GradientDrawable cb = new GradientDrawable();
				cb.setCornerRadius(dp(ctx, 16));
				cb.setColor(0xFF1B1E24);
				cb.setStroke(dp(ctx, 1), 0x55FF0033);
				chip.setBackground(cb);
				chip.setClickable(true);
				String url = "https://m.youtube.com/channel/" + ch[1] + "/live";
				chip.setOnClickListener(v -> openYoutube(a, url));
				LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
				clp.rightMargin = dp(ctx, 10);
				row.addView(chip, clp);
			}
			android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(ctx);
			hs.setHorizontalScrollBarEnabled(false);
			hs.addView(row);
			col.addView(hs);
		}

		TextView hint = text(ctx,
				"📱 Telefon ekranı: Android Auto uygulama listesinden “Yolda Telefon”u aç.", 14, false, MUTED);
		hint.setPadding(dp(ctx, 4), dp(ctx, 18), dp(ctx, 4), 0);
		col.addView(hint);

		// Kartlar ekran genişliğine göre eşit bölünsün
		grid.post(() -> {
			int w = grid.getWidth();
			if (w <= 0) return;
			int gap = dp(ctx, 12);
			int cw = (w - gap * (cols - 1)) / cols;
			for (int i = 0; i < grid.getChildCount(); i++) {
				View c = grid.getChildAt(i);
				GridLayout.LayoutParams p = (GridLayout.LayoutParams) c.getLayoutParams();
				p.width = cw;
				p.height = dp(ctx, 132);
				p.setMargins(0, 0, (i % cols == cols - 1) ? 0 : gap, gap);
				c.setLayoutParams(p);
			}
		});
		return scroll;
	}

	/** YouTube bölümünü açıp verilen adrese gider (YouTube eklentisi ayrı modülde, adıyla çağrılır). */
	private static void openYoutube(MainActivityDelegate a, String url) {
		Object f = a.showFragment(R.id.youtube_fragment);
		if (f == null) return;
		try {
			f.getClass().getMethod("loadUrl", String.class).invoke(f, url);
		} catch (Exception ex) {
			me.aap.utils.log.Log.e(ex, "YouTube açılamadı: ", url);
		}
	}

	private static void addCard(GridLayout grid, View card) {
		grid.addView(card, new GridLayout.LayoutParams());
	}

	@SuppressLint("ClickableViewAccessibility")
	private View card(Context ctx, String icon, String title, String sub, int c1, int c2, boolean wide,
										View.OnClickListener onClick) {
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
		bg.setCornerRadius(dp(ctx, 22));
		f.setBackground(bg);
		f.setElevation(dp(ctx, 4));
		f.setClickable(true);
		f.setFocusable(true);
		f.setOnClickListener(onClick);
		// Basınca hafif küçülsün
		f.setOnTouchListener((v, e) -> {
			if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
			else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
			return false;
		});

		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		box.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM);
		int p = dp(ctx, 16);
		box.setPadding(p, p, p, p);
		TextView ic = text(ctx, icon, wide ? 30 : 34, false, Color.WHITE);
		box.addView(ic);
		LinearLayout t = new LinearLayout(ctx);
		t.setOrientation(LinearLayout.VERTICAL);
		if (wide) t.setPadding(dp(ctx, 16), 0, 0, 0);
		t.addView(text(ctx, title, 21, true, Color.WHITE));
		TextView s = text(ctx, sub, 13, false, 0xDDFFFFFF);
		s.setMaxLines(1);
		t.addView(s);
		box.addView(t);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView t = new TextView(ctx);
		t.setText(s);
		t.setTextSize(size);
		t.setTextColor(color);
		if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
		return t;
	}

	private static String greeting() {
		int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		if (h < 5) return "İyi geceler";
		if (h < 12) return "Günaydın";
		if (h < 18) return "İyi günler";
		return "İyi akşamlar";
	}

	private static int dp(Context ctx, int v) {
		return Math.round(v * ctx.getResources().getDisplayMetrics().density);
	}
}
