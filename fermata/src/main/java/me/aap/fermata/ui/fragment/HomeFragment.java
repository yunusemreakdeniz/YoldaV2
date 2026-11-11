package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

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
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * Yolda ana ekranı.
 * <pre>
 *  ┌ saat · selam · tarih ─────────────────── 🎤  🎨 ┐
 *  │ ┌ Devam et / Sesli komut ┐ ┌ TV ┐ ┌ YouTube ┐   │
 *  │ │       (büyük kart)     │ ├────┤ ├─────────┤   │
 *  │ └────────────────────────┘ └Tel ┘ └Tarayıcı ┘   │
 *  │ ⭐ Favoriler · 🎬 Videolar · ⚙️ Ayarlar           │
 *  │ ▶ YouTube kanallarım   [kart] [kart] … [+ Ekle] │
 *  └─────────────────────────────────────────────────┘
 * </pre>
 * Tema 🎨 ile değişir (Gece mavisi, Grafit, Gece yeşili). YouTube kanalları kullanıcı tarafından
 * eklenir/silinir; telefonda saklanır.
 */
public class HomeFragment extends MainActivityFragment {

	private static final String PREFS = "yolda_home";

	private static final String[][] DEFAULT_YT = {
			{"Habertürk", "https://m.youtube.com/channel/UCK3mI2lsk3LSo8PBUc8JTSw/live"},
			{"CNN Türk", "https://m.youtube.com/channel/UCV6zcRug6Hqp1UX_FdyUeBg/live"},
			{"Show TV", "https://m.youtube.com/channel/UC9JMe_We017gYrRc7kZHgmg/live"},
			{"Haber Global", "https://m.youtube.com/channel/UCtc-a9ZUIg0_5HpsPxEO7Qg/live"},
			{"TVNET", "https://m.youtube.com/channel/UC8rh34IlJTN0lDZlTwzWzjg/live"},
			{"tv100", "https://m.youtube.com/channel/UCndsdUW_oPLqpQJY9J8oIRg/live"},
	};

	private FrameLayout root;
	private YoldaStyle.Theme t;
	/** Büyük (pastel) kart üzerindeki yazı rengi. */
	private int heroInk;

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

	private SharedPreferences prefs() {
		return requireContext().getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	// ---- çizim ----

	private void render() {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		heroInk = YoldaStyle.onHero(t);
		root.removeAllViews();
		root.setBackground(null);
		root.addView(new YoldaStyle.Backdrop(ctx, t), new FrameLayout.LayoutParams(-1, -1));

		ScrollView scroll = new ScrollView(ctx);
		scroll.setFillViewport(true);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(20);
		col.setPadding(pad, dp(14), pad, pad);
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		MainActivityDelegate a = getActivityDelegate();
		boolean wide = ctx.getResources().getConfiguration().screenWidthDp >= 600;

		col.addView(header(ctx, a));

		if (prefs().getInt("layout", 0) == 1) {
			renderFlow(ctx, a, col);
			if (AddonManager.get().hasAddon(R.id.youtube_fragment)) ytShelf(ctx, a, col);
			animateIn(col);
			return;
		}

		// Büyük kart + 2x2 hızlı erişim
		LinearLayout hero = new LinearLayout(ctx);
		hero.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		int heroH = wide ? dp(228) : dp(132);
		hero.addView(heroCard(ctx, a), wide ? new LinearLayout.LayoutParams(0, heroH, 1.15f)
				: new LinearLayout.LayoutParams(-1, heroH));
		LinearLayout quad = quad(ctx, a);
		LinearLayout.LayoutParams qlp = wide ? new LinearLayout.LayoutParams(0, heroH, 1f)
				: new LinearLayout.LayoutParams(-1, dp(232));
		if (wide) qlp.leftMargin = dp(14);
		else qlp.topMargin = dp(14);
		hero.addView(quad, qlp);
		LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
		hlp.topMargin = dp(16);
		col.addView(hero, hlp);

		// İkincil kısayollar
		LinearLayout chips = new LinearLayout(ctx);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.addView(chip(ctx, "⭐  Favoriler", v -> a.showFragment(R.id.favorites_fragment)));
		chips.addView(chip(ctx, "🎬  Videolar", v -> a.showFragment(R.id.folders_fragment)));
		chips.addView(chip(ctx, "⚙️  Ayarlar", v -> a.showFragment(R.id.settings_fragment)));
		HorizontalScrollView chs = new HorizontalScrollView(ctx);
		chs.setHorizontalScrollBarEnabled(false);
		chs.addView(chips);
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
		clp.topMargin = dp(16);
		col.addView(chs, clp);

		if (AddonManager.get().hasAddon(R.id.youtube_fragment)) ytShelf(ctx, a, col);
		animateIn(col);
	}

	// ---- akıcı görünüm (Mola tarzı: şimdi oynatılıyor paneli + büyük satırlar) ----

	private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
	private Runnable npTick;

	private void renderFlow(Context ctx, MainActivityDelegate a, LinearLayout col) {
		LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(150));
		plp.topMargin = dp(16);
		col.addView(nowPlaying(ctx, a), plp);

		AddonManager am = AddonManager.get();
		if (am.hasAddon(R.id.tv_fragment))
			col.addView(row(ctx, "📺", "TV ve Radyo", "51 kanal · 22 radyo", 0xFFEF4444, v -> a.showFragment(R.id.yolda_channels_fragment)));
		if (am.hasAddon(R.id.youtube_fragment))
			col.addView(row(ctx, "▶️", "YouTube", "Videolar, canlı yayınlar, kanalların", 0xFFFF0033, v -> a.showFragment(R.id.youtube_fragment)));
		col.addView(row(ctx, "📱", "Telefon", "Telefon ekranını araçtan kullan", 0xFF22C55E, v -> phone(ctx)));
		if (am.hasAddon(R.id.web_browser_fragment))
			col.addView(row(ctx, "🌐", "Tarayıcı", "İnternette gezin", 0xFF3B82F6, v -> a.showFragment(R.id.web_browser_fragment)));
		col.addView(row(ctx, "⭐", "Favoriler", "Kaydettiğin kanallar ve videolar", 0xFFF59E0B, v -> a.showFragment(R.id.favorites_fragment)));
		col.addView(row(ctx, "🎬", "Videolar", "Telefondaki dosyalar", 0xFF8B5CF6, v -> a.showFragment(R.id.folders_fragment)));
		col.addView(row(ctx, "⚙️", "Ayarlar", "Eklentiler, görünüm, sesli komut", 0xFF64748B, v -> a.showFragment(R.id.settings_fragment)));
	}

	/** Çalan yayın: ikon, etiket, başlık ve ⏮ ⏯ ⏭ ★ düğmeleri; oynatma durumu saniyede bir yenilenir. */
	private View nowPlaying(Context ctx, MainActivityDelegate a) {
		// Apple "Şimdi Çalıyor" gibi: cam kart, solda pastel kapak, sağda yuvarlak cam düğmeler
		FrameLayout f = base(ctx, YoldaStyle.glass(ctx, t, 28), v -> {
			if (a.hasCurrent()) a.goToCurrent();
			else a.startVoiceAssistant();
		});
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.HORIZONTAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(dp(16), dp(14), dp(14), dp(14));

		TextView art = text(ctx, "📺", 34, false, heroInk);
		art.setGravity(Gravity.CENTER);
		art.setBackground(YoldaStyle.hero(ctx, t, 22));
		box.addView(art, new LinearLayout.LayoutParams(dp(100), dp(100)));

		LinearLayout info = new LinearLayout(ctx);
		info.setOrientation(LinearLayout.VERTICAL);
		info.setPadding(dp(18), 0, dp(8), 0);
		TextView eyebrow = text(ctx, "", 12, true, t.accent);
		eyebrow.setLetterSpacing(0.14f);
		TextView title = text(ctx, "", 23, true, t.ink);
		title.setMaxLines(1);
		title.setEllipsize(android.text.TextUtils.TruncateAt.END);
		TextView sub = text(ctx, "", 14, false, t.muted);
		sub.setMaxLines(1);
		info.addView(eyebrow);
		info.addView(title);
		info.addView(sub);
		box.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

		me.aap.fermata.media.service.FermataServiceUiBinder b = a.getMediaServiceBinder();
		LinearLayout ctl = new LinearLayout(ctx);
		ctl.setOrientation(LinearLayout.HORIZONTAL);
		ctl.setGravity(Gravity.CENTER_VERTICAL);
		View prev = glassButton(ctx, "⏮", v -> b.onPrevNextButtonClick(false));
		TextView play = (TextView) roundButton(ctx, "▶", t.accent, v -> b.onPlayPauseButtonClick());
		play.setTextColor(heroInk);
		View next = glassButton(ctx, "⏭", v -> b.onPrevNextButtonClick(true));
		TextView fav = (TextView) glassButton(ctx, "☆", v -> {
			MediaLib.PlayableItem pi = b.getCurrentItem();
			if (pi == null) return;
			MediaLib.Favorites fs = a.getLib().getFavorites();
			if (fs.isFavoriteItem(pi)) fs.removeItem(pi);
			else fs.addItem(pi);
		});
		for (View v : new View[]{prev, play, next, fav}) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
			lp.leftMargin = dp(8);
			ctl.addView(v, lp);
		}
		box.addView(ctl);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));

		// Durumu canlı tut: çalıyor/duraklatıldı, başlık, favori yıldızı
		if (npTick != null) ui.removeCallbacks(npTick);
		npTick = new Runnable() {
			@Override
			public void run() {
				if (!isAdded() || isHidden() || f.getParent() == null) return;
				MediaLib.PlayableItem pi = b.getCurrentItem();
				boolean playing = b.isPlaying();
				boolean has = (pi != null) || a.hasCurrent();
				eyebrow.setText(playing ? "ŞİMDİ OYNATILIYOR" : (has ? "DURAKLATILDI · DEVAM ET" : "HAZIR"));
				title.setText(pi != null ? String.valueOf(pi.getName()) : (has ? "Kaldığın yerden devam" : "Sesli komut"));
				sub.setText(pi != null ? "Dokun: yayına git" : (has ? "Son izlediğin yayın" : "“TRT Haber'i aç” · “YouTube'da Habertürk aç”"));
				art.setText(pi != null ? "📺" : (has ? "▶" : "🎤"));
				play.setText(playing ? "⏸" : "▶");
				boolean isFav = false;
				try {
					isFav = (pi != null) && a.getLib().getFavorites().isFavoriteItem(pi);
				} catch (Exception ignore) {
				}
				fav.setText(isFav ? "★" : "☆");
				ctl.setVisibility(has ? View.VISIBLE : View.GONE);
				ui.postDelayed(this, 1000);
			}
		};
		ui.post(npTick);
		return f;
	}

	private View row(Context ctx, String icon, String title, String sub, int color, View.OnClickListener click) {
		FrameLayout f = base(ctx, YoldaStyle.glass(ctx, t, 22), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.HORIZONTAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(dp(14), dp(10), dp(18), dp(10));
		TextView ic = text(ctx, icon, 22, false, Color.WHITE);
		ic.setGravity(Gravity.CENTER);
		ic.setBackground(iconBg(color));
		box.addView(ic, new LinearLayout.LayoutParams(dp(50), dp(50)));
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.setPadding(dp(16), 0, 0, 0);
		tx.addView(text(ctx, title, 18, true, t.ink));
		TextView s = text(ctx, sub, 13, false, t.muted);
		s.setMaxLines(1);
		tx.addView(s);
		box.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
		box.addView(text(ctx, "›", 28, false, t.muted));
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(78));
		lp.topMargin = dp(10);
		f.setLayoutParams(lp);
		return f;
	}

	/** İçerik sırayla, hafifçe yukarı kayarak gelsin. */
	private void animateIn(LinearLayout col) {
		float dy = dp(22);
		for (int i = 0; i < col.getChildCount(); i++) {
			View v = col.getChildAt(i);
			v.setAlpha(0f);
			v.setTranslationY(dy);
			v.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(260)
					.setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
		}
	}

	@Override
	public void onDestroyView() {
		if (npTick != null) ui.removeCallbacks(npTick);
		super.onDestroyView();
	}

	private View header(Context ctx, MainActivityDelegate a) {
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);

		TextClock clock = new TextClock(ctx);
		clock.setFormat24Hour("HH:mm");
		clock.setFormat12Hour("HH:mm");
		clock.setTextSize(44);
		clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
		clock.setTextColor(t.ink);
		head.addView(clock);

		LinearLayout hello = new LinearLayout(ctx);
		hello.setOrientation(LinearLayout.VERTICAL);
		hello.setPadding(dp(16), 0, 0, 0);
		hello.addView(text(ctx, greeting(), 20, true, t.ink));
		TextClock date = new TextClock(ctx);
		date.setFormat24Hour("d MMMM EEEE");
		date.setFormat12Hour("d MMMM EEEE");
		date.setTextSize(14);
		date.setTextColor(t.muted);
		hello.addView(date);
		head.addView(hello, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView mic = (TextView) roundButton(ctx, "🎤", t.accent, v -> a.startVoiceAssistant());
		head.addView(mic);
		boolean flow = prefs().getInt("layout", 0) == 1;
		View layoutBtn = glassButton(ctx, flow ? "▦" : "☰", v -> {
			prefs().edit().putInt("layout", flow ? 0 : 1).apply();
			render();
			UiUtils.showInfo(ctx, flow ? "Kart görünümü" : "Akıcı görünüm");
		});
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(52), dp(52));
		llp.leftMargin = dp(10);
		head.addView(layoutBtn, llp);
		View themeBtn = glassButton(ctx, "🎨", v -> {
			YoldaStyle.next(ctx);
			render();
			UiUtils.showInfo(ctx, "Tema: " + YoldaStyle.current(ctx).name);
		});
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(52), dp(52));
		tlp.leftMargin = dp(10);
		head.addView(themeBtn, tlp);
		return head;
	}

	/** Çalan/son yayın varsa "Devam et", yoksa "Sesli komut". */
	private View heroCard(Context ctx, MainActivityDelegate a) {
		boolean resume = a.hasCurrent();
		String title = "Kaldığın yerden devam";
		String sub = "Son izlediğin yayın";
		if (resume) {
			try {
				MediaLib.PlayableItem pi = a.getMediaServiceBinder().getCurrentItem();
				if (pi != null) sub = String.valueOf(pi.getName());
			} catch (Exception ignore) {
			}
		}
		View.OnClickListener click = resume ? v -> a.goToCurrent() : v -> a.startVoiceAssistant();
		FrameLayout f = base(ctx, YoldaStyle.hero(ctx, t, 30), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.BOTTOM);
		box.setPadding(dp(22), dp(18), dp(22), dp(20));
		box.addView(text(ctx, resume ? "▶" : "🎤", 40, false, heroInk));
		box.addView(text(ctx, resume ? title : "Sesli komut", 25, true, heroInk));
		TextView s = text(ctx, resume ? sub : "“TRT Haber'i aç” · “YouTube'da Habertürk aç”", 14, false, (heroInk & 0x00FFFFFF) | 0xB3000000);
		s.setMaxLines(2);
		box.addView(s);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private LinearLayout quad(Context ctx, MainActivityDelegate a) {
		AddonManager am = AddonManager.get();
		List<View> tiles = new ArrayList<>();
		if (am.hasAddon(R.id.tv_fragment))
			tiles.add(tile(ctx, "📺", "TV ve Radyo", 0xFFEF4444, v -> a.showFragment(R.id.yolda_channels_fragment)));
		if (am.hasAddon(R.id.youtube_fragment))
			tiles.add(tile(ctx, "▶️", "YouTube", 0xFFFF0033, v -> a.showFragment(R.id.youtube_fragment)));
		tiles.add(tile(ctx, "📱", "Telefon", 0xFF22C55E, v -> phone(ctx)));
		if (am.hasAddon(R.id.web_browser_fragment))
			tiles.add(tile(ctx, "🌐", "Tarayıcı", 0xFF3B82F6, v -> a.showFragment(R.id.web_browser_fragment)));
		else tiles.add(tile(ctx, "⭐", "Favoriler", 0xFFF59E0B, v -> a.showFragment(R.id.favorites_fragment)));

		LinearLayout q = new LinearLayout(ctx);
		q.setOrientation(LinearLayout.VERTICAL);
		for (int r = 0; r < 2; r++) {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (int c = 0; c < 2; c++) {
				int i = r * 2 + c;
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
				if (c == 1) lp.leftMargin = dp(12);
				row.addView(i < tiles.size() ? tiles.get(i) : new View(ctx), lp);
			}
			LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
			if (r == 1) rlp.topMargin = dp(12);
			q.addView(row, rlp);
		}
		return q;
	}

	// ---- YouTube kanallarım ----

	private void ytShelf(Context ctx, MainActivityDelegate a, LinearLayout col) {
		TextView title = text(ctx, "▶  YouTube kanallarım", 17, true, t.ink);
		title.setPadding(dp(4), dp(22), 0, dp(10));
		col.addView(title);

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

		TextView hint = text(ctx, "Dokun: aç  ·  Uzun bas: kaldır  ·  + ile @kanal adı ya da bağlantı ekle", 12, false, t.muted);
		hint.setPadding(dp(4), dp(8), 0, 0);
		col.addView(hint);
	}

	private LinearLayout.LayoutParams shelfLp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(118));
		lp.rightMargin = dp(12);
		return lp;
	}

	private View ytCard(Context ctx, String name, View.OnClickListener click) {
		FrameLayout f = base(ctx, YoldaStyle.glass(ctx, t, 22), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(dp(10), dp(10), dp(10), dp(10));
		TextView av = text(ctx, initials(name), 18, true, 0xFF1C1A2B);
		av.setGravity(Gravity.CENTER);
		av.setBackground(iconBg(avatarColor(name)));
		box.addView(av, new LinearLayout.LayoutParams(dp(52), dp(52)));
		TextView n = text(ctx, name, 14, true, t.ink);
		n.setGravity(Gravity.CENTER);
		n.setMaxLines(1);
		n.setEllipsize(android.text.TextUtils.TruncateAt.END);
		n.setPadding(0, dp(8), 0, 0);
		box.addView(n, new LinearLayout.LayoutParams(-1, -2));
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private View addCard(Context ctx) {
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(t.light ? 0x40FFFFFF : 0x0DFFFFFF);
		bg.setStroke(dp(2), t.accent, dp(8), dp(6));
		bg.setCornerRadius(dp(22));
		FrameLayout f = base(ctx, bg, v -> addYoutube(ctx));
		TextView plus = text(ctx, "＋\nKanal ekle", 15, true, t.accent);
		plus.setGravity(Gravity.CENTER);
		f.addView(plus, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private void addYoutube(Context ctx) {
		UiUtils.queryText(ctx, R.string.yolda_add_yt, R.drawable.yolda_home).onSuccess(in -> {
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
			Uri uri = Uri.parse(u);
			List<String> seg = uri.getPathSegments();
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

	/** YouTube bölümünü açıp verilen adrese gider (YouTube eklentisi ayrı modülde, adıyla çağrılır). */
	private static void openYoutube(MainActivityDelegate a, String url) {
		Object f = a.showFragment(R.id.youtube_fragment);
		if (f == null) return;
		try {
			f.getClass().getMethod("loadUrl", String.class).invoke(f, url);
		} catch (Exception ex) {
			Log.e(ex, "YouTube açılamadı: ", url);
		}
	}

	// ---- telefonu araçtan kullanma ----

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

	// ---- parçalar ----

	@SuppressLint("ClickableViewAccessibility")
	private FrameLayout base(Context ctx, android.graphics.drawable.Drawable bg, View.OnClickListener click) {
		FrameLayout f = new FrameLayout(ctx);
		f.setBackground(bg);
		f.setElevation(0); // cam yüzeyde gölge, saydamlıktan görünüp kirli durur
		f.setClickable(true);
		f.setFocusable(true);
		f.setOnClickListener(click);
		f.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
			return false;
		});
		return f;
	}

	private View tile(Context ctx, String icon, String label, int color, View.OnClickListener click) {
		FrameLayout f = base(ctx, YoldaStyle.glass(ctx, t, 26), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setPadding(dp(16), dp(12), dp(16), dp(12));
		TextView ic = text(ctx, icon, 22, false, Color.WHITE);
		ic.setGravity(Gravity.CENTER);
		ic.setBackground(iconBg(color));
		box.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
		TextView l = text(ctx, label, 16, true, t.ink);
		l.setMaxLines(1);
		l.setPadding(0, dp(8), 0, 0);
		box.addView(l);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	private View chip(Context ctx, String label, View.OnClickListener click) {
		TextView c = text(ctx, label, 15, true, t.ink);
		c.setGravity(Gravity.CENTER);
		c.setPadding(dp(18), dp(12), dp(18), dp(12));
		c.setBackground(YoldaStyle.glass(ctx, t, 999));
		c.setClickable(true);
		c.setOnClickListener(click);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
		lp.rightMargin = dp(10);
		c.setLayoutParams(lp);
		return c;
	}

	private View roundButton(Context ctx, String icon, int color, View.OnClickListener click) {
		TextView b = text(ctx, icon, 22, false, Color.WHITE);
		b.setGravity(Gravity.CENTER);
		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor(color);
		b.setBackground(bg);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
		return b;
	}

	/** Yuvarlak cam düğme (tema/görünüm/oynatıcı düğmeleri). */
	private View glassButton(Context ctx, String icon, View.OnClickListener click) {
		TextView b = text(ctx, icon, 21, false, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
		return b;
	}

	/** Pastel ikon dairesi: verilen rengin beyazla yumuşatılmış hali, ince parlak kenar. */
	private GradientDrawable iconBg(int color) {
		GradientDrawable ib = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
				new int[]{soften(color, 0.55f), soften(color, 0.30f)});
		ib.setShape(GradientDrawable.OVAL);
		ib.setStroke(Math.max(1, dp(1)), 0x66FFFFFF);
		return ib;
	}

	private static int soften(int c, float white) {
		int r = Math.round(Color.red(c) + (255 - Color.red(c)) * white);
		int g = Math.round(Color.green(c) + (255 - Color.green(c)) * white);
		int b = Math.round(Color.blue(c) + (255 - Color.blue(c)) * white);
		return Color.rgb(r, g, b);
	}

	/** Apple tarzı yazı: başlıklar orta kalınlıkta (sans-serif-medium), gövde normal. */
	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size);
		tv.setTextColor(color);
		tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
		if (bold && size >= 20) tv.setLetterSpacing(-0.01f);
		return tv;
	}

	private static String initials(String name) {
		StringBuilder b = new StringBuilder();
		for (String w : name.trim().split("\\s+")) {
			if (!w.isEmpty() && b.length() < 2) b.appendCodePoint(w.codePointAt(0));
		}
		return b.toString().toUpperCase(new Locale("tr"));
	}

	private static int avatarColor(String name) {
		int[] c = {0xFFEF4444, 0xFF8B5CF6, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF6366F1};
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
