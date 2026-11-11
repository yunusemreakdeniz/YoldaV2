package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * Yolda ana ekranı (cam tasarım).
 * <pre>
 *  ┌ 21:45  İyi akşamlar · tarih ─────────────── (mic) (görünüm) (tema) ┐
 *  │ KART: ┌ Devam et / Sesli komut ┐ ┌ TV ┐ ┌ YouTube ┐                │
 *  │       │      (pastel kart)     │ ├────┤ ├─────────┤                │
 *  │       └────────────────────────┘ └Tel ┘ └ Music   ┘                │
 *  │ AKICI: [Şimdi oynatılıyor ⏮ ⏯ ⏭ ☆] + büyük satırlar                │
 *  │ (Favoriler) (Tarayıcı) (Videolar) (Ayarlar)                         │
 *  │ YouTube kanallarım  [kart] [kart] … [+]                             │
 *  └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 * Simgeler vektör çizim (emoji yok). Tema 🎨, görünüm ☰/▦ düğmesiyle değişir; ikisi de hatırlanır.
 */
public class HomeFragment extends MainActivityFragment {

	private static final String PREFS = YoldaStyle.PREFS;
	private static final int HERO_INK = 0xFF1C1A2B;
	private static final String MUSIC_URL = "https://music.youtube.com";

	private static final String[][] DEFAULT_YT = {
			{"Habertürk", "https://m.youtube.com/channel/UCK3mI2lsk3LSo8PBUc8JTSw/live"},
			{"CNN Türk", "https://m.youtube.com/channel/UCV6zcRug6Hqp1UX_FdyUeBg/live"},
			{"Show TV", "https://m.youtube.com/channel/UC9JMe_We017gYrRc7kZHgmg/live"},
			{"Haber Global", "https://m.youtube.com/channel/UCtc-a9ZUIg0_5HpsPxEO7Qg/live"},
			{"TVNET", "https://m.youtube.com/channel/UC8rh34IlJTN0lDZlTwzWzjg/live"},
			{"tv100", "https://m.youtube.com/channel/UCndsdUW_oPLqpQJY9J8oIRg/live"},
	};

	/** Kart renkleri (yumuşatılarak pastel daireye dönüşür). */
	private static final int C_TV = 0xFFF87171, C_YT = 0xFFFB7185, C_MUSIC = 0xFFE879F9, C_PHONE = 0xFF4ADE80,
			C_WEB = 0xFF60A5FA, C_FAV = 0xFFFBBF24, C_VIDEO = 0xFFA78BFA, C_SET = 0xFF94A3B8;

	private final Handler ui = new Handler(Looper.getMainLooper());
	private Runnable npTick;
	private FrameLayout root;
	private YoldaStyle.Theme t;

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
		greetOnce();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && root != null) render(); // geri dönünce "devam et" güncel olsun
	}

	@Override
	public void onDestroyView() {
		if (npTick != null) ui.removeCallbacks(npTick);
		super.onDestroyView();
	}

	private SharedPreferences prefs() {
		return requireContext().getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	private boolean flowLayout() {
		return prefs().getInt("layout", 0) == 1;
	}

	// ---- çizim ----

	private void render() {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		if (npTick != null) ui.removeCallbacks(npTick);
		root.removeAllViews();
		root.setBackground(null);
		YoldaStyle.applyScreenBackground(root, t);

		ScrollView scroll = new ScrollView(ctx);
		scroll.setFillViewport(true);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(24), dp(16), dp(24), dp(24));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		MainActivityDelegate a = getActivityDelegate();
		col.addView(header(ctx, a));

		if (flowLayout()) flow(ctx, a, col);
		else cards(ctx, a, col);

		col.addView(shortcuts(ctx, a), topMargin(new LinearLayout.LayoutParams(-1, -2), 18));
		if (AddonManager.get().hasAddon(R.id.youtube_fragment)) ytShelf(ctx, a, col);
		animateIn(col);
	}

	private View header(Context ctx, MainActivityDelegate a) {
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);

		TextClock clock = new TextClock(ctx);
		clock.setFormat24Hour("HH:mm");
		clock.setFormat12Hour("HH:mm");
		clock.setTextSize(46);
		clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
		clock.setTextColor(t.ink);
		clock.setIncludeFontPadding(false);
		head.addView(clock);

		LinearLayout hello = new LinearLayout(ctx);
		hello.setOrientation(LinearLayout.VERTICAL);
		hello.setPadding(dp(18), 0, 0, 0);
		hello.addView(text(ctx, greeting(), 20, true, t.ink));
		TextClock date = new TextClock(ctx);
		date.setFormat24Hour("d MMMM EEEE");
		date.setFormat12Hour("d MMMM EEEE");
		date.setTextSize(14);
		date.setTextColor(t.muted);
		hello.addView(date);
		head.addView(hello, new LinearLayout.LayoutParams(0, -2, 1f));

		head.addView(accentButton(ctx, R.drawable.yi_mic, 56, v -> a.startVoiceAssistant()));
		boolean flow = flowLayout();
		head.addView(glassButton(ctx, flow ? R.drawable.yi_grid : R.drawable.yi_list, 56, v -> {
			prefs().edit().putInt("layout", flow ? 0 : 1).apply();
			render();
		}), leftMargin(sq(56), 10));
		head.addView(glassButton(ctx, R.drawable.yi_palette, 56, v -> {
			YoldaStyle.next(ctx);
			YoldaStyle.refreshBackdrop(ctx);
			render();
			UiUtils.showInfo(ctx, "Tema: " + YoldaStyle.current(ctx).name);
		}), leftMargin(sq(56), 10));
		return head;
	}

	// ---- kart görünümü ----

	private void cards(Context ctx, MainActivityDelegate a, LinearLayout col) {
		boolean wide = ctx.getResources().getConfiguration().screenWidthDp >= 600;
		LinearLayout hero = new LinearLayout(ctx);
		hero.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		int h = wide ? dp(236) : dp(140);
		hero.addView(heroCard(ctx, a), wide ? new LinearLayout.LayoutParams(0, h, 1.1f) : new LinearLayout.LayoutParams(-1, h));
		LinearLayout.LayoutParams qlp = wide ? new LinearLayout.LayoutParams(0, h, 1f) : new LinearLayout.LayoutParams(-1, dp(236));
		if (wide) qlp.leftMargin = dp(14);
		else qlp.topMargin = dp(14);
		hero.addView(quad(ctx, a), qlp);
		col.addView(hero, topMargin(new LinearLayout.LayoutParams(-1, -2), 18));
	}

	/** Çalan/son yayın varsa "Devam et", yoksa "Sesli komut". */
	private View heroCard(Context ctx, MainActivityDelegate a) {
		boolean has = a.hasCurrent();
		String sub = "Son izlediğin yayın";
		MediaLib.PlayableItem pi = a.getMediaServiceBinder().getCurrentItem();
		if (pi != null) sub = String.valueOf(pi.getName());
		FrameLayout f = pressable(ctx, YoldaStyle.hero(ctx, t, 30), has ? v -> resume(a) : v -> a.startVoiceAssistant());
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.BOTTOM);
		box.setPadding(dp(24), dp(20), dp(24), dp(22));
		box.addView(glyphDisc(ctx, has ? R.drawable.yi_play : R.drawable.yi_mic, 0x40FFFFFF, HERO_INK, 52));
		TextView title = text(ctx, has ? "Kaldığın yerden devam" : "Sesli komut", 25, true, HERO_INK);
		title.setPadding(0, dp(14), 0, 0);
		box.addView(title);
		TextView s = text(ctx, has ? sub : "“TRT Haber'i aç” · “YouTube'da Habertürk aç”", 14, false, 0xB31C1A2B);
		s.setMaxLines(2);
		box.addView(s);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private LinearLayout quad(Context ctx, MainActivityDelegate a) {
		AddonManager am = AddonManager.get();
		List<View> tiles = new ArrayList<>();
		if (am.hasAddon(R.id.tv_fragment))
			tiles.add(tile(ctx, R.drawable.yi_tv, "TV ve Radyo", C_TV, v -> a.showFragment(R.id.yolda_channels_fragment)));
		if (am.hasAddon(R.id.youtube_fragment))
			tiles.add(tile(ctx, R.drawable.yi_youtube, "YouTube", C_YT, v -> a.showFragment(R.id.youtube_fragment)));
		tiles.add(tile(ctx, R.drawable.yi_phone, "Telefon", C_PHONE, v -> phone(ctx)));
		if (am.hasAddon(R.id.web_browser_fragment))
			tiles.add(tile(ctx, R.drawable.yi_music, "YouTube Music", C_MUSIC, v -> openWeb(a, MUSIC_URL)));
		else tiles.add(tile(ctx, R.drawable.yi_star, "Favoriler", C_FAV, v -> a.showFragment(R.id.favorites_fragment)));

		LinearLayout q = new LinearLayout(ctx);
		q.setOrientation(LinearLayout.VERTICAL);
		for (int r = 0; r < 2; r++) {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (int c = 0; c < 2; c++) {
				int i = r * 2 + c;
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
				if (c == 1) lp.leftMargin = dp(14);
				row.addView(i < tiles.size() ? tiles.get(i) : new View(ctx), lp);
			}
			LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
			if (r == 1) rlp.topMargin = dp(14);
			q.addView(row, rlp);
		}
		return q;
	}

	private View tile(Context ctx, @DrawableRes int icon, String label, int color, View.OnClickListener click) {
		FrameLayout f = pressable(ctx, YoldaStyle.glass(ctx, t, 26), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(dp(18), dp(14), dp(14), dp(14));
		box.addView(iconDisc(ctx, icon, color, 46));
		TextView l = text(ctx, label, 16, true, t.ink);
		l.setMaxLines(1);
		l.setEllipsize(TextUtils.TruncateAt.END);
		l.setPadding(0, dp(10), 0, 0);
		box.addView(l);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	// ---- akıcı görünüm ----

	private void flow(Context ctx, MainActivityDelegate a, LinearLayout col) {
		col.addView(nowPlaying(ctx, a), topMargin(new LinearLayout.LayoutParams(-1, dp(148)), 18));
		AddonManager am = AddonManager.get();
		if (am.hasAddon(R.id.tv_fragment))
			col.addView(row(ctx, R.drawable.yi_tv, "TV ve Radyo", "51 kanal · 22 radyo", C_TV, v -> a.showFragment(R.id.yolda_channels_fragment)));
		if (am.hasAddon(R.id.youtube_fragment))
			col.addView(row(ctx, R.drawable.yi_youtube, "YouTube", "Videolar, canlı yayınlar", C_YT, v -> a.showFragment(R.id.youtube_fragment)));
		if (am.hasAddon(R.id.web_browser_fragment))
			col.addView(row(ctx, R.drawable.yi_music, "YouTube Music", "Müzik, çalma listeleri, podcast", C_MUSIC, v -> openWeb(a, MUSIC_URL)));
		col.addView(row(ctx, R.drawable.yi_phone, "Telefon", "Telefon ekranını araçtan kullan", C_PHONE, v -> phone(ctx)));
	}

	/** Apple "Şimdi Çalıyor" gibi cam kart: pastel kapak, başlık, ⏮ ⏯ ⏭ ☆; saniyede bir güncellenir. */
	private View nowPlaying(Context ctx, MainActivityDelegate a) {
		FrameLayout f = pressable(ctx, YoldaStyle.glass(ctx, t, 28), v -> {
			if (a.hasCurrent()) resume(a);
			else a.startVoiceAssistant();
		});
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.HORIZONTAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(dp(16), dp(14), dp(16), dp(14));

		FrameLayout art = new FrameLayout(ctx);
		art.setBackground(YoldaStyle.hero(ctx, t, 22));
		ImageView artIcon = icon(ctx, R.drawable.yi_tv, HERO_INK);
		art.addView(artIcon, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
		box.addView(art, new LinearLayout.LayoutParams(dp(104), dp(104)));

		LinearLayout info = new LinearLayout(ctx);
		info.setOrientation(LinearLayout.VERTICAL);
		info.setPadding(dp(18), 0, dp(10), 0);
		TextView eyebrow = text(ctx, "", 12, true, t.accent);
		eyebrow.setLetterSpacing(0.14f);
		TextView title = text(ctx, "", 23, true, t.ink);
		title.setMaxLines(1);
		title.setEllipsize(TextUtils.TruncateAt.END);
		TextView sub = text(ctx, "", 14, false, t.muted);
		sub.setMaxLines(1);
		sub.setEllipsize(TextUtils.TruncateAt.END);
		info.addView(eyebrow);
		info.addView(title);
		info.addView(sub);
		box.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

		FermataServiceUiBinder b = a.getMediaServiceBinder();
		LinearLayout ctl = new LinearLayout(ctx);
		ctl.setOrientation(LinearLayout.HORIZONTAL);
		ctl.setGravity(Gravity.CENTER_VERTICAL);
		View prev = glassButton(ctx, R.drawable.yi_prev, 52, v -> b.onPrevNextButtonClick(false));
		FrameLayout play = (FrameLayout) accentButton(ctx, R.drawable.yi_play, 60, v -> b.onPlayPauseButtonClick());
		View next = glassButton(ctx, R.drawable.yi_next, 52, v -> b.onPrevNextButtonClick(true));
		FrameLayout fav = (FrameLayout) glassButton(ctx, R.drawable.yi_star_border, 52, v -> {
			MediaLib.PlayableItem pi = b.getCurrentItem();
			if (pi == null) return;
			MediaLib.Favorites fs = a.getLib().getFavorites();
			if (fs.isFavoriteItem(pi)) fs.removeItem(pi);
			else fs.addItem(pi);
		});
		ctl.addView(prev, sq(52));
		ctl.addView(play, leftMargin(sq(60), 10));
		ctl.addView(next, leftMargin(sq(52), 10));
		ctl.addView(fav, leftMargin(sq(52), 10));
		box.addView(ctl);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));

		ImageView playIcon = (ImageView) play.getChildAt(0);
		ImageView favIcon = (ImageView) fav.getChildAt(0);
		npTick = new Runnable() {
			@Override
			public void run() {
				if (!isAdded() || isHidden() || f.getParent() == null) return;
				MediaLib.PlayableItem pi = b.getCurrentItem();
				boolean playing = b.isPlaying();
				boolean has = (pi != null) || a.hasCurrent();
				eyebrow.setText(playing ? "ŞİMDİ OYNATILIYOR" : (has ? "DURAKLATILDI" : "HAZIR"));
				title.setText(pi != null ? String.valueOf(pi.getName()) : (has ? "Kaldığın yerden devam" : "Sesli komut"));
				sub.setText(pi != null ? "Dokun: yayını aç" : (has ? "Son izlediğin yayın" : "“TRT Haber'i aç” · “YouTube'da Habertürk aç”"));
				artIcon.setImageResource(pi != null ? R.drawable.yi_tv : (has ? R.drawable.yi_play : R.drawable.yi_mic));
				playIcon.setImageResource(playing ? R.drawable.yi_pause : R.drawable.yi_play);
				boolean isFav = false;
				try {
					isFav = (pi != null) && a.getLib().getFavorites().isFavoriteItem(pi);
				} catch (Exception ignore) {
				}
				favIcon.setImageResource(isFav ? R.drawable.yi_star : R.drawable.yi_star_border);
				ctl.setVisibility(has ? View.VISIBLE : View.GONE);
				ui.postDelayed(this, 1000);
			}
		};
		ui.post(npTick);
		return f;
	}

	private View row(Context ctx, @DrawableRes int icon, String title, String sub, int color, View.OnClickListener click) {
		FrameLayout f = pressable(ctx, YoldaStyle.glass(ctx, t, 24), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.HORIZONTAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(dp(16), dp(10), dp(16), dp(10));
		box.addView(iconDisc(ctx, icon, color, 50));
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.setPadding(dp(16), 0, 0, 0);
		tx.addView(text(ctx, title, 18, true, t.ink));
		TextView s = text(ctx, sub, 13, false, t.muted);
		s.setMaxLines(1);
		tx.addView(s);
		box.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
		box.addView(icon(ctx, R.drawable.yi_chevron, t.muted), sq(26));
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		f.setLayoutParams(topMargin(new LinearLayout.LayoutParams(-1, dp(80)), 12));
		return f;
	}

	// ---- kısayollar ve YouTube kanallarım ----

	private View shortcuts(Context ctx, MainActivityDelegate a) {
		LinearLayout chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.addView(chip(ctx, R.drawable.yi_star, "Favoriler", v -> a.showFragment(R.id.favorites_fragment)));
		if (AddonManager.get().hasAddon(R.id.web_browser_fragment))
			chips.addView(chip(ctx, R.drawable.yi_globe, "Tarayıcı", v -> a.showFragment(R.id.web_browser_fragment)));
		chips.addView(chip(ctx, R.drawable.yi_movie, "Videolar", v -> a.showFragment(R.id.folders_fragment)));
		chips.addView(chip(ctx, R.drawable.yi_settings, "Ayarlar", v -> a.showFragment(R.id.settings_fragment)));
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		return hs;
	}

	private View chip(Context ctx, @DrawableRes int icon, String label, View.OnClickListener click) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.HORIZONTAL);
		c.setGravity(Gravity.CENTER_VERTICAL);
		c.setPadding(dp(16), dp(11), dp(20), dp(11));
		c.setBackground(YoldaStyle.glass(ctx, t, 999));
		c.setClickable(true);
		c.setOnClickListener(click);
		c.addView(icon(ctx, icon, t.accent), sq(20));
		TextView l = text(ctx, label, 15, true, t.ink);
		l.setPadding(dp(10), 0, 0, 0);
		c.addView(l);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
		lp.rightMargin = dp(10);
		c.setLayoutParams(lp);
		return c;
	}

	private void ytShelf(Context ctx, MainActivityDelegate a, LinearLayout col) {
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(dp(4), dp(24), 0, dp(12));
		head.addView(icon(ctx, R.drawable.yi_live, t.accent), sq(22));
		TextView title = text(ctx, "YouTube kanallarım", 18, true, t.ink);
		title.setPadding(dp(10), 0, 0, 0);
		head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
		head.addView(text(ctx, "Uzun bas: kaldır", 12, false, t.muted));
		col.addView(head);

		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		List<String[]> list = ytList();
		for (int i = 0; i < list.size(); i++) {
			String[] ch = list.get(i);
			final int idx = i;
			View card = ytCard(ctx, ch[0], v -> openYoutube(a, ch[1]));
			card.setOnLongClickListener(v -> {
				UiUtils.showQuestion(ctx, "Kanalı kaldır", "“" + ch[0] + "” kaldırılsın mı?", null)
						.onSuccess(x -> {
							List<String[]> l = ytList();
							if (idx < l.size()) l.remove(idx);
							saveYt(l);
							render();
						});
				return true;
			});
			row.addView(card, shelfLp());
		}
		row.addView(addCard(ctx), shelfLp());

		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(row);
		col.addView(hs);
	}

	private LinearLayout.LayoutParams shelfLp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(124));
		lp.rightMargin = dp(12);
		return lp;
	}

	private View ytCard(Context ctx, String name, View.OnClickListener click) {
		FrameLayout f = pressable(ctx, YoldaStyle.glass(ctx, t, 24), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(dp(10), dp(12), dp(10), dp(10));
		TextView av = text(ctx, initials(name), 18, true, HERO_INK);
		av.setGravity(Gravity.CENTER);
		av.setBackground(discBg(avatarColor(name)));
		box.addView(av, sq(54));
		TextView n = text(ctx, name, 14, true, t.ink);
		n.setGravity(Gravity.CENTER);
		n.setMaxLines(1);
		n.setEllipsize(TextUtils.TruncateAt.END);
		n.setPadding(0, dp(10), 0, 0);
		box.addView(n, new LinearLayout.LayoutParams(-1, -2));
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private View addCard(Context ctx) {
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0x0DFFFFFF);
		bg.setStroke(dp(2), (t.accent & 0x00FFFFFF) | 0x99000000, dp(8), dp(6));
		bg.setCornerRadius(dp(24));
		FrameLayout f = pressable(ctx, bg, v -> addYoutube(ctx));
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.addView(icon(ctx, R.drawable.yi_add, t.accent), sq(34));
		TextView l = text(ctx, "Kanal ekle", 14, true, t.accent);
		l.setPadding(0, dp(8), 0, 0);
		box.addView(l);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private void addYoutube(Context ctx) {
		UiUtils.queryText(ctx, R.string.yolda_add_yt, R.drawable.yi_youtube).onSuccess(in -> {
			String[] ch = parseYoutube(in);
			if (ch == null) {
				UiUtils.showAlert(ctx, "Kanal anlaşılamadı. Örnek: @cnnturk ya da youtube.com/@cnnturk");
				return;
			}
			List<String[]> l = ytList();
			l.add(ch);
			saveYt(l);
			render();
		});
	}

	/** "@cnnturk", "youtube.com/@x", "youtube.com/channel/UC…", ya da düz ad (arama). */
	static String[] parseYoutube(String in) {
		if (in == null) return null;
		String s = in.trim();
		if (s.isEmpty()) return null;
		if (s.startsWith("@")) {
			String h = s.substring(1).replaceAll("[^A-Za-z0-9._-]", "");
			return h.isEmpty() ? null : new String[]{h, "https://m.youtube.com/@" + h};
		}
		if (s.contains("youtube.com") || s.contains("youtu.be")) {
			String u = s.startsWith("http") ? s : "https://" + s;
			u = u.replace("://www.youtube.com", "://m.youtube.com").replace("://youtube.com", "://m.youtube.com");
			List<String> seg = Uri.parse(u).getPathSegments();
			String name = seg.isEmpty() ? "YouTube" : seg.get(seg.size() - 1);
			if (("live".equals(name) || "videos".equals(name) || "featured".equals(name)) && seg.size() >= 2)
				name = seg.get(seg.size() - 2);
			if (name.startsWith("@")) name = name.substring(1);
			if (name.startsWith("UC") && name.length() == 24) name = "Kanal";
			return new String[]{name, u};
		}
		return new String[]{s, "https://m.youtube.com/results?search_query=" + Uri.encode(s)};
	}

	private List<String[]> ytList() {
		List<String[]> l = new ArrayList<>();
		String json = prefs().getString("yt", null);
		if (json == null) {
			for (String[] d : DEFAULT_YT) l.add(d.clone());
			return l;
		}
		try {
			JSONArray arr = new JSONArray(json);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.getJSONObject(i);
				l.add(new String[]{o.getString("n"), o.getString("u")});
			}
		} catch (Exception ex) {
			Log.e(ex, "YouTube kanalları okunamadı");
		}
		return l;
	}

	private void saveYt(List<String[]> l) {
		JSONArray arr = new JSONArray();
		try {
			for (String[] c : l) arr.put(new JSONObject().put("n", c[0]).put("u", c[1]));
		} catch (Exception ignore) {
		}
		prefs().edit().putString("yt", arr.toString()).apply();
	}

	// ---- işler ----

	/** Kaldığın yerden devam: çalan varsa sürdür ve videoyu göster; yoksa son yayını başlat. */
	private static void resume(MainActivityDelegate a) {
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		MediaLib.PlayableItem cur = b.getCurrentItem();
		if (cur != null) {
			if (!b.isPlaying()) b.onPlayPauseButtonClick();
			if (cur.isVideo()) a.getBody().setMode(BodyLayout.Mode.VIDEO);
			return;
		}
		a.getLib().getLastPlayedItem().main().onSuccess(pi -> {
			if (pi != null) a.getBody().playItem(pi);
		});
	}

	/** Tarayıcı bölümünde bir site açar (YouTube Music gibi). */
	private static void openWeb(MainActivityDelegate a, String url) {
		invokeLoadUrl(a.showFragment(R.id.web_browser_fragment), url);
	}

	/** YouTube bölümünü açıp verilen adrese gider. */
	private static void openYoutube(MainActivityDelegate a, String url) {
		invokeLoadUrl(a.showFragment(R.id.youtube_fragment), url);
	}

	/** Eklenti ayrı modülde; loadUrl adıyla çağrılır (proguard-rules.pro'da korunuyor). */
	private static void invokeLoadUrl(Object f, String url) {
		if (f == null) return;
		try {
			f.getClass().getMethod("loadUrl", String.class).invoke(f, url);
		} catch (Exception ex) {
			Log.e(ex, "Sayfa açılamadı: ", url);
		}
	}

	/**
	 * Telefon ekranı ayrı bir Android Auto uygulaması ("Yolda Telefon") olarak açılır; araç ekranındaki
	 * dokunuşların telefona geçmesi için Yolda'nın erişilebilirlik hizmeti açık olmalı.
	 */
	private void phone(Context ctx) {
		if (remoteTouchEnabled(ctx)) {
			UiUtils.showInfo(ctx, "Araç ekranının uygulama listesinden “Yolda Telefon”u aç. "
					+ "Telefon ekranı burada görünür; dokunarak, kaydırarak kullanabilirsin.");
		} else {
			UiUtils.showQuestion(ctx, "Uzaktan dokunma kapalı",
					"Telefonu araç ekranından yönetmek için telefonda Ayarlar → Erişilebilirlik → Yolda'yı aç. "
							+ "Ayarları telefonda açayım mı?", null).onSuccess(v -> {
				try {
					ctx.getApplicationContext().startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} catch (Exception ex) {
					Log.e(ex, "Erişilebilirlik ayarı açılamadı");
				}
			});
		}
	}

	private static boolean remoteTouchEnabled(Context ctx) {
		String s = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
		return (s != null) && s.contains(ctx.getPackageName() + "/") && s.contains("AccessibilityEventDispatcherService");
	}

	// ---- yapı taşları ----

	/** Basınca hafifçe küçülen yüzey. */
	@SuppressLint("ClickableViewAccessibility")
	private FrameLayout pressable(Context ctx, android.graphics.drawable.Drawable bg, View.OnClickListener click) {
		FrameLayout f = new FrameLayout(ctx);
		f.setBackground(bg);
		f.setClickable(true);
		f.setFocusable(true);
		f.setOnClickListener(click);
		f.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
			return false;
		});
		return f;
	}

	private ImageView icon(Context ctx, @DrawableRes int res, int tint) {
		ImageView iv = new ImageView(ctx);
		iv.setImageResource(res);
		iv.setImageTintList(ColorStateList.valueOf(tint));
		iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
		return iv;
	}

	/** Pastel daire içinde koyu simge (kartlar ve satırlar). */
	private View iconDisc(Context ctx, @DrawableRes int res, int color, int sizeDp) {
		return glyphDisc(ctx, res, 0, HERO_INK, sizeDp, discBg(color));
	}

	private View glyphDisc(Context ctx, @DrawableRes int res, int bgColor, int tint, int sizeDp) {
		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor(bgColor);
		return glyphDisc(ctx, res, bgColor, tint, sizeDp, bg);
	}

	private View glyphDisc(Context ctx, @DrawableRes int res, int bgColor, int tint, int sizeDp,
												 android.graphics.drawable.Drawable bg) {
		FrameLayout d = new FrameLayout(ctx);
		d.setBackground(bg);
		int g = Math.round(sizeDp * 0.52f);
		d.addView(icon(ctx, res, tint), new FrameLayout.LayoutParams(dp(g), dp(g), Gravity.CENTER));
		d.setLayoutParams(sq(sizeDp));
		return d;
	}

	/** Yuvarlak cam düğme. */
	private View glassButton(Context ctx, @DrawableRes int res, int sizeDp, View.OnClickListener click) {
		View b = glyphDisc(ctx, res, 0, t.ink, sizeDp, YoldaStyle.glass(ctx, t, 999));
		b.setClickable(true);
		b.setOnClickListener(click);
		return b;
	}

	/** Vurgu renginde dolu yuvarlak düğme (mikrofon, oynat). */
	private View accentButton(Context ctx, @DrawableRes int res, int sizeDp, View.OnClickListener click) {
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[]{soften(t.accent, 0.15f), t.accent});
		bg.setShape(GradientDrawable.OVAL);
		bg.setStroke(Math.max(1, dp(1)), 0x66FFFFFF);
		View b = glyphDisc(ctx, res, 0, HERO_INK, sizeDp, bg);
		b.setClickable(true);
		b.setOnClickListener(click);
		return b;
	}

	/** Verilen rengin yumuşatılmış (pastel) hali, ince parlak kenarlı daire. */
	private GradientDrawable discBg(int color) {
		GradientDrawable ib = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[]{soften(color, 0.50f), soften(color, 0.25f)});
		ib.setShape(GradientDrawable.OVAL);
		ib.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
		return ib;
	}

	private static int soften(int c, float white) {
		int r = Math.round(Color.red(c) + (255 - Color.red(c)) * white);
		int g = Math.round(Color.green(c) + (255 - Color.green(c)) * white);
		int b = Math.round(Color.blue(c) + (255 - Color.blue(c)) * white);
		return Color.rgb(r, g, b);
	}

	/** Apple tarzı yazı: başlıklar orta kalınlıkta, gövde normal. */
	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size);
		tv.setTextColor(color);
		tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
		if (bold && size >= 20) tv.setLetterSpacing(-0.01f);
		return tv;
	}

	/** İçerik sırayla, hafifçe yukarı kayarak gelsin. */
	private void animateIn(LinearLayout col) {
		float dy = dp(20);
		for (int i = 0; i < col.getChildCount(); i++) {
			View v = col.getChildAt(i);
			v.setAlpha(0f);
			v.setTranslationY(dy);
			v.animate().alpha(1f).translationY(0f).setStartDelay(i * 50L).setDuration(300)
					.setInterpolator(new DecelerateInterpolator()).start();
		}
	}

	private LinearLayout.LayoutParams sq(int sizeDp) {
		return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
	}

	private LinearLayout.LayoutParams leftMargin(LinearLayout.LayoutParams lp, int dp) {
		lp.leftMargin = dp(dp);
		return lp;
	}

	private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams lp, int dp) {
		lp.topMargin = dp(dp);
		return lp;
	}

	private static String initials(String name) {
		StringBuilder b = new StringBuilder();
		for (String w : name.trim().split("\\s+")) {
			if (!w.isEmpty() && b.length() < 2) b.appendCodePoint(w.codePointAt(0));
		}
		return b.toString().toUpperCase(new Locale("tr"));
	}

	private static int avatarColor(String name) {
		int[] c = {C_TV, C_VIDEO, C_WEB, C_PHONE, C_FAV, C_MUSIC, 0xFF818CF8};
		return c[Math.floorMod(name.hashCode(), c.length)];
	}

	private static String greeting() {
		int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		if (h < 5) return "İyi geceler";
		if (h < 12) return "Günaydın";
		if (h < 18) return "İyi günler";
		return "İyi akşamlar";
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}

	// ---- sesli karşılama ----

	private static boolean greeted;

	/** Araç ekranında ilk açılışta kısa sesli karşılama ("İyi akşamlar, Yolda hazır."). */
	private void greetOnce() {
		if (greeted) return;
		MainActivityDelegate a = getActivityDelegate();
		if (a == null || !a.isCarActivity()) return;
		greeted = true;
		Context app = requireContext().getApplicationContext();
		final TextToSpeech[] tts = new TextToSpeech[1];
		tts[0] = new TextToSpeech(app, status -> {
			if (status != TextToSpeech.SUCCESS) return;
			TextToSpeech s = tts[0];
			s.setLanguage(new Locale("tr", "TR"));
			s.setOnUtteranceProgressListener(new UtteranceProgressListener() {
				@Override
				public void onStart(String id) {
				}

				@Override
				public void onDone(String id) {
					s.shutdown();
				}

				@Override
				public void onError(String id) {
					s.shutdown();
				}
			});
			s.speak(greeting() + ", Yolda hazır.", TextToSpeech.QUEUE_FLUSH, null, "yolda_hello");
		});
	}
}
