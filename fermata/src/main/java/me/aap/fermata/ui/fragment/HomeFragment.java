package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * Yolda ana ekranı (Liquid Glass, onaylanan taslağa göre).
 * <pre>
 *  ┌ Salı, 11 Kasım · 21:45                                   (mic) (tema) ┐
 *  │ İyi akşamlar                                                          │
 *  │ [Navigasyon] [TV ve Radyo] [YouTube] [YouTube Music] [Telefon] [Web]  │  ← uygulama kartları
 *  │ Canlı kanallar                                         Tümünü gör    │
 *  │ [TRT 1] [ATV] [Kanal D] …                                             │
 *  │ YouTube kanallarım  [..] [..] [+]                                     │
 *  └────────────── ( Ana sayfa · TV · YouTube · Müzik · Menü ) ────────────┘  ← Fermata alt menüsü, cam kapsül
 * </pre>
 */
public class HomeFragment extends MainActivityFragment {

	private static final String PREFS = YoldaStyle.PREFS;
	static final String MUSIC_URL = "https://music.youtube.com";

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

	@Override
	public int getFragmentId() {
		return R.id.home_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Yolda";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance; // tam ekran: başlık çubuğu yok
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance; // yüzen ≡ düğmesi yok
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
		if (!hidden && root != null) render();
	}

	private SharedPreferences prefs() {
		return requireContext().getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	// ---- çizim ----

	/** Döndürmede (dikey ↔ yatay) ölçüler yeni ekran genişliğine göre baştan hesaplansın. */
	@Override
	public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (getView() != null) getView().post(this::render);
	}

	private void render() {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		root.setBackground(null);
		YoldaStyle.applyScreenBackground(root, t);

		ScrollView scroll = new ScrollView(ctx);
		scroll.setFillViewport(true);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(me.aap.fermata.ui.view.YoldaStyle.side(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.top(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.side(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.bottom(ctx)));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		MainActivityDelegate a = getActivityDelegate();
		YoldaVoice.preload(a); // sesli komutta kanal adları hazır olsun
		col.addView(header(ctx, a));
		if (picking) {
			col.addView(picker(ctx), topMargin(new LinearLayout.LayoutParams(-1, -2), 20));
			animateIn(col);
			return;
		}
		col.addView(apps(ctx, a), topMargin(new LinearLayout.LayoutParams(-1, -2), 24));
		if (!editing) {
			if (AddonManager.get().hasAddon(R.id.tv_fragment)) liveShelf(ctx, a, col);
			if (AddonManager.get().hasAddon(R.id.youtube_fragment)) ytShelf(ctx, a, col);
		} else {
			TextView hint = text(ctx, "Kartları ok tuşlarıyla taşı, ✕ ile kaldır, + ile telefonundaki uygulamaları ekle. Bitince ✓.", 14, false);
			hint.setTextColor(0xCCFFFFFF);
			hint.setPadding(dp(4), dp(20), 0, 0);
			col.addView(hint);
			if (AddonManager.get().hasAddon(R.id.youtube_fragment)) ytShelf(ctx, a, col);
		}
		animateIn(col);
	}

	// ---- düzenlenebilir uygulama kartları ----

	private boolean editing, picking;
	private static final String[] BUILTIN = {"nav", "tv", "yt", "music", "phone", "web"};

	private List<String> order(Context ctx) {
		String s = prefs().getString("apps", null);
		List<String> l = new ArrayList<>();
		if (s == null) {
			for (String k : BUILTIN) if (available(k)) l.add(k);
		} else {
			for (String k : s.split(",")) if (!k.isEmpty() && available(k)) l.add(k);
		}
		return l;
	}

	private void saveOrder(List<String> l) {
		prefs().edit().putString("apps", TextUtils.join(",", l)).apply();
	}

	private boolean available(String k) {
		AddonManager am = AddonManager.get();
		return switch (k) {
			case "tv" -> am.hasAddon(R.id.tv_fragment);
			case "yt" -> am.hasAddon(R.id.youtube_fragment);
			case "music", "web" -> am.hasAddon(R.id.web_browser_fragment);
			case "nav", "phone" -> true;
			default -> k.startsWith("pkg:") && (launchIntent(k.substring(4)) != null);
		};
	}

	private Intent launchIntent(String pkg) {
		try {
			return requireContext().getPackageManager().getLaunchIntentForPackage(pkg);
		} catch (Exception ex) {
			return null;
		}
	}

	/** Kartın görünümü ve işi. */
	private View cardFor(Context ctx, MainActivityDelegate a, String k) {
		switch (k) {
			case "nav":
				return appCard(ctx, "Navigasyon", YoldaStyle.appIcon(ctx, 0xFF4FD1C5, 0xFF0E9F8E, 28), glyph(ctx, R.drawable.yi_nav, true), 54,
						v -> a.showFragment(R.id.yolda_nav_fragment));
			case "tv":
				return appCard(ctx, "TV ve Radyo", YoldaStyle.appIcon(ctx, 0xFFFF8A65, 0xFFF4511E, 28), glyph(ctx, R.drawable.yi_tv, true), 54,
						v -> a.showFragment(R.id.yolda_channels_fragment));
			case "yt":
				return appCard(ctx, "YouTube", YoldaStyle.appIcon(ctx, 0xFFFFFFFF, 0xFFF1F1F1, 28), glyph(ctx, R.drawable.yi_brand_youtube, false), 70,
						v -> a.showFragment(R.id.yolda_youtube));
			case "music":
				return appCard(ctx, "YouTube Music", YoldaStyle.appIcon(ctx, 0xFFFF2A2A, 0xFFE00000, 28), glyph(ctx, R.drawable.yi_brand_ytmusic, false), 70,
						v -> openWeb(a, MUSIC_URL));
			case "phone":
				return appCard(ctx, "Telefon", YoldaStyle.appIcon(ctx, 0xFF5EE07B, 0xFF1DB954, 28), glyph(ctx, R.drawable.yi_phone, true), 54,
						v -> phone(ctx));
			case "web":
				return appCard(ctx, "Tarayıcı", YoldaStyle.appIcon(ctx, 0xFF6BB8FF, 0xFF1E6FE6, 28), glyph(ctx, R.drawable.yi_globe, true), 54,
						v -> {
							YoldaStyle.webNoToolbar = false; // genel tarayıcı: adres çubuğu görünsün
							a.showFragment(R.id.web_browser_fragment);
							a.postBroadcastEvent(me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED);
						});
		}
		// Telefondaki uygulama: kendi simgesi ve adı
		String pkg = k.substring(4);
		android.content.pm.PackageManager pm = ctx.getPackageManager();
		String label = pkg;
		android.graphics.drawable.Drawable icon = null;
		try {
			android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
			label = String.valueOf(pm.getApplicationLabel(ai));
			icon = pm.getApplicationIcon(ai);
		} catch (Exception ignore) {
		}
		ImageView iv = new ImageView(ctx);
		if (icon != null) iv.setImageDrawable(icon);
		iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
		return appCard(ctx, label, null, iv, 104, v -> launchApp(a, pkg));
	}

	private void launchApp(MainActivityDelegate a, String pkg) {
		Intent i = launchIntent(pkg);
		if (i == null) return;
		try {
			a.getContext().getApplicationContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			if (a.isCarActivity()) UiUtils.showInfo(a.getContext(),
					"Telefonda açıldı. Araç ekranında kullanmak için uygulama listesinden “Yolda Telefon”u aç.");
		} catch (Exception ex) {
			Log.e(ex, "Uygulama açılamadı: ", pkg);
		}
	}

	private ImageView glyph(Context ctx, @DrawableRes int res, boolean tintWhite) {
		ImageView g = new ImageView(ctx);
		g.setImageResource(res);
		if (tintWhite) g.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		g.setScaleType(ImageView.ScaleType.FIT_CENTER);
		return g;
	}

	private View header(Context ctx, MainActivityDelegate a) {
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout hello = new LinearLayout(ctx);
		hello.setOrientation(LinearLayout.VERTICAL);
		TextClock date = new TextClock(ctx);
		date.setFormat24Hour("EEEE, d MMMM · HH:mm");
		date.setFormat12Hour("EEEE, d MMMM · HH:mm");
		date.setTextSize(15 * YoldaStyle.textScale(ctx));
		date.setTextColor(0xBFFFFFFF);
		date.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
		hello.addView(date);
		TextView g = text(ctx, greeting(), YoldaStyle.compact(ctx) ? 30 : 40, true);
		g.setLetterSpacing(-0.025f);
		hello.addView(g);
		head.addView(hello, new LinearLayout.LayoutParams(0, -2, 1f));

		head.addView(roundGlass(ctx, (editing || picking) ? R.drawable.yi_check : R.drawable.yi_edit, v -> {
			if (picking) picking = false;
			else editing = !editing;
			render();
		}), leftMargin(sq(btnDp(ctx)), 12));
		head.addView(roundGlass(ctx, R.drawable.yi_palette, v -> {
			YoldaStyle.next(ctx);
			YoldaStyle.refreshBackdrop(ctx);
			render();
			UiUtils.showInfo(ctx, "Tema: " + YoldaStyle.current(ctx).name);
		}), leftMargin(sq(btnDp(ctx)), 12));
		head.addView(roundGlass(ctx, R.drawable.yi_settings, v -> a.showFragment(R.id.yolda_menu_fragment)),
				leftMargin(sq(btnDp(ctx)), 12));
		return head;
	}

	// ---- uygulama kartları (kendi simgeleriyle) ----

	private View apps(Context ctx, MainActivityDelegate a) {
		List<String> keys = order(ctx);
		List<View> cards = new ArrayList<>();
		List<Integer> spans = new ArrayList<>();
		// "Kaldığın yerden devam et": ilk sırada 2 hücre genişliğinde
		if (!editing && (a.hasCurrent() || !MusicFragment.recent(ctx).isEmpty())) {
			cards.add(resumeCard(ctx, a));
			spans.add(2);
		}
		for (int i = 0; i < keys.size(); i++) {
			View c = cardFor(ctx, a, keys.get(i));
			if (editing) c = withEditControls(ctx, c, keys, i);
			cards.add(c);
			spans.add(1);
		}
		if (editing) {
			cards.add(addAppCard(ctx));
			spans.add(1);
		}

		// Geniş ekranda 6'lı, dar ekranda 3'lü sıralar
		int w = YoldaStyle.widthDp(ctx);
		int perRow = (w >= 900) ? 6 : (w >= 600) ? 4 : 3;
		int gap = YoldaStyle.compact(ctx) ? 12 : 18;
		LinearLayout grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		LinearLayout row = null;
		int used = perRow; // geçerli sırada dolu hücre sayısı
		for (int i = 0; i < cards.size(); i++) {
			int span = Math.min(spans.get(i), perRow);
			if (used + span > perRow) {
				if (row != null) fillRow(ctx, row, perRow - used, gap);
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(cardH(ctx)));
				if (grid.getChildCount() > 0) rlp.topMargin = dp(gap);
				grid.addView(row, rlp);
				used = 0;
			}
			// Çok hücreli kart aradaki boşlukları da kaplar: ağırlık = hücre, ek genişlik = boşluk
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, span);
			if (used > 0) lp.leftMargin = dp(gap);
			if (span > 1) lp.width = dp(gap) * (span - 1);
			row.addView(cards.get(i), lp);
			used += span;
		}
		// Son sırayı boşlukla doldur: kartlar her sırada aynı boyda kalsın
		if (row != null) fillRow(ctx, row, perRow - used, gap);
		return grid;
	}

	private void fillRow(Context ctx, LinearLayout row, int empty, int gap) {
		for (int i = 0; i < empty; i++) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
			lp.leftMargin = dp(gap);
			row.addView(new View(ctx), lp);
		}
	}

	/** Son çalınan/izlenen: kapak görseli zemin, altta ad; dokununca kaldığı yerden sürer. */
	private View resumeCard(Context ctx, MainActivityDelegate a) {
		FrameLayout f = pressable(ctx, YoldaStyle.appIcon(ctx, 0xFF8B5CF6, 0xFF4C1D95, 28), v -> resumePlayback(a));
		f.setClipToOutline(true);
		f.setElevation(dp(6));
		ImageView art = new ImageView(ctx);
		art.setScaleType(ImageView.ScaleType.CENTER_CROP);
		art.setVisibility(View.GONE);
		f.addView(art, new FrameLayout.LayoutParams(-1, -1));
		// Yazı okunsun diye alttan koyulaşan perde
		View shade = new View(ctx);
		shade.setBackground(new android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x10000000, 0x40000000, 0xC0000000}));
		f.addView(shade, new FrameLayout.LayoutParams(-1, -1));

		boolean small = YoldaStyle.compact(ctx);
		LinearLayout txt = new LinearLayout(ctx);
		txt.setOrientation(LinearLayout.VERTICAL);
		txt.setPadding(dp(small ? 14 : 20), 0, dp(small ? 60 : 84), dp(small ? 12 : 18));
		TextView cap = text(ctx, "Kaldığın yerden devam et", small ? 12 : 14, false);
		cap.setAlpha(0.85f);
		cap.setMaxLines(1);
		cap.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(cap);
		TextView name = text(ctx, "…", small ? 16 : 22, true);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(name);
		TextView sub = text(ctx, "", small ? 12 : 14, false);
		sub.setAlpha(0.75f);
		sub.setMaxLines(1);
		sub.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(sub);
		f.addView(txt, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START));

		// Sağ altta oynat düğmesi
		FrameLayout play = new FrameLayout(ctx);
		play.setBackground(YoldaStyle.selected(ctx, 999));
		ImageView pi = new ImageView(ctx);
		pi.setImageResource(R.drawable.yi_play);
		pi.setColorFilter(0xFFFFFFFF);
		int ps = small ? 40 : 56;
		play.addView(pi, new FrameLayout.LayoutParams(dp(ps / 2), dp(ps / 2), Gravity.CENTER));
		FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(ps), dp(ps), Gravity.BOTTOM | Gravity.END);
		plp.setMargins(0, 0, dp(small ? 12 : 16), dp(small ? 12 : 16));
		f.addView(play, plp);

		currentOrLast(a).onCompletion((item, err) -> {
			if (!isAdded()) return;
			if ((item == null) || item.isExternal()) {
				// Son dinlenen müzik (YouTube Music) ya da hiçbir şey
				List<me.aap.fermata.media.yt.YtMusic.Track> rec = MusicFragment.recent(ctx);
				if (rec.isEmpty()) {
					name.setText("Henüz bir şey dinlemedin");
					return;
				}
				me.aap.fermata.media.yt.YtMusic.Track tr = rec.get(0);
				name.setText(tr.title());
				sub.setText((tr.artist() != null) ? tr.artist() : "");
				if (tr.thumb() != null) {
					a.getLib().getBitmap(tr.thumb(), true, true).main().onCompletion((bmp, e2) -> {
						if (!isAdded() || (bmp == null)) return;
						art.setImageBitmap(bmp);
						art.setVisibility(View.VISIBLE);
					});
				}
				return;
			}
			name.setText(item.getName());
			MediaLib.BrowsableItem p = item.getParent();
			sub.setText((p != null) ? p.getName() : "");
			item.getIconUri().main().onCompletion((u, uerr) -> {
				if (!isAdded() || (u == null)) return;
				a.getLib().getBitmap(u.toString(), true, true).main().onCompletion((bmp, e2) -> {
					if (!isAdded() || (bmp == null)) return;
					art.setImageBitmap(bmp);
					art.setVisibility(View.VISIBLE);
				});
			});
		});
		return f;
	}

	private static me.aap.utils.async.FutureSupplier<MediaLib.PlayableItem> currentOrLast(MainActivityDelegate a) {
		MediaLib.PlayableItem pi = a.getCurrentPlayable();
		if ((pi != null) && !pi.isExternal()) return me.aap.utils.async.Completed.completed(pi);
		return a.getLib().getLastPlayedItem().main();
	}

	/** Duraklatılmışsa kaldığı saniyeden sürdür; çalıyorsa oynatıcıya git. */
	private void resumePlayback(MainActivityDelegate a) {
		MediaLib.PlayableItem cur = a.getCurrentPlayable();
		me.aap.fermata.media.service.MediaSessionCallback cb = a.getMediaSessionCallback();
		if ((cur != null) && !cur.isExternal()) {
			if (!cb.isPlaying()) cb.onPlay();
			a.goToCurrent();
			return;
		}
		if (cur instanceof me.aap.fermata.media.yt.YtMusicItem) { // duraklatılmış müzik: kaldığı yerden
			if (!cb.isPlaying()) cb.onPlay();
			return;
		}
		a.getLib().getLastPlayedItem().main().onCompletion((item, err) -> {
			if ((item != null) && !item.isExternal()) {
				a.getBody().playItem(item);
				return;
			}
			List<me.aap.fermata.media.yt.YtMusic.Track> rec = MusicFragment.recent(a.getContext());
			if (!rec.isEmpty()) MusicFragment.playTracks(a, rec, 0); // son dinlenen şarkıdan devam
			else UiUtils.showInfo(a.getContext(), "Henüz bir şey dinlemedin");
		});
	}

	/** Düzenleme modunda kartın üstünde ◀ ✕ ▶ düğmeleri. */
	private View withEditControls(Context ctx, View card, List<String> keys, int idx) {
		FrameLayout f = new FrameLayout(ctx);
		f.addView(card, new FrameLayout.LayoutParams(-1, -1));
		card.setAlpha(0.85f);
		LinearLayout bar = new LinearLayout(ctx);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER);
		if (idx > 0) bar.addView(miniButton(ctx, R.drawable.yi_left, v -> {
			List<String> l = new ArrayList<>(keys);
			l.add(idx - 1, l.remove(idx));
			saveOrder(l);
			render();
		}));
		bar.addView(miniButton(ctx, R.drawable.yi_close, v -> {
			List<String> l = new ArrayList<>(keys);
			l.remove(idx);
			saveOrder(l);
			render();
		}));
		if (idx < keys.size() - 1) bar.addView(miniButton(ctx, R.drawable.yi_right, v -> {
			List<String> l = new ArrayList<>(keys);
			l.add(idx + 1, l.remove(idx));
			saveOrder(l);
			render();
		}));
		FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
		blp.topMargin = dp(10);
		f.addView(bar, blp);
		return f;
	}

	private View miniButton(Context ctx, @DrawableRes int res, View.OnClickListener click) {
		FrameLayout b = pressable(ctx, YoldaStyle.selected(ctx, 999), click);
		ImageView iv = glyph(ctx, res, true);
		b.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
		lp.leftMargin = lp.rightMargin = dp(4);
		b.setLayoutParams(lp);
		return b;
	}

	private View addAppCard(Context ctx) {
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0x14FFFFFF);
		bg.setStroke(dp(2), 0x8CFFFFFF, dp(10), dp(8));
		bg.setCornerRadius(dp(34));
		FrameLayout f = pressable(ctx, bg, v -> {
			picking = true;
			render();
		});
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.addView(glyph(ctx, R.drawable.yi_add, true), sq(48));
		TextView l = text(ctx, "Ekle", 17, true);
		l.setPadding(0, dp(12), 0, 0);
		box.addView(l);
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	/** Ana ekrana ekle: gizlenmiş Yolda kartları + telefondaki uygulamalar. */
	private View picker(Context ctx) {
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.addView(text(ctx, "Ana ekrana ekle", 26, true));
		List<String> cur = order(ctx);

		List<String> hidden = new ArrayList<>();
		for (String k : BUILTIN) if (available(k) && !cur.contains(k)) hidden.add(k);
		if (!hidden.isEmpty()) {
			TextView h = text(ctx, "YOLDA", 12, true);
			h.setTextColor(0xBFFFFFFF);
			h.setLetterSpacing(0.1f);
			h.setPadding(dp(4), dp(18), 0, dp(10));
			col.addView(h);
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (String k : hidden) {
				View c = cardFor(ctx, getActivityDelegate(), k);
				c.setOnClickListener(v -> addKey(k));
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(170), dp(200));
				lp.rightMargin = dp(14);
				row.addView(c, lp);
			}
			HorizontalScrollView hs = new HorizontalScrollView(ctx);
			hs.setHorizontalScrollBarEnabled(false);
			hs.addView(row);
			col.addView(hs);
		}

		TextView h2 = text(ctx, "TELEFONUNDAKİ UYGULAMALAR", 12, true);
		h2.setTextColor(0xBFFFFFFF);
		h2.setLetterSpacing(0.1f);
		h2.setPadding(dp(4), dp(22), 0, dp(10));
		col.addView(h2);

		android.content.pm.PackageManager pm = ctx.getPackageManager();
		Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
		List<android.content.pm.ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(main, 0));
		apps.sort((x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
		int perRow = (YoldaStyle.widthDp(ctx) >= 900) ? 8 : (YoldaStyle.widthDp(ctx) >= 600) ? 6 : 4;
		LinearLayout row = null;
		int n = 0;
		for (android.content.pm.ResolveInfo ri : apps) {
			String pkg = ri.activityInfo.packageName;
			if (pkg.equals(ctx.getPackageName()) || cur.contains("pkg:" + pkg)) continue;
			if (n % perRow == 0) {
				row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				col.addView(row, new LinearLayout.LayoutParams(-1, -2));
			}
			LinearLayout cell = new LinearLayout(ctx);
			cell.setOrientation(LinearLayout.VERTICAL);
			cell.setGravity(Gravity.CENTER_HORIZONTAL);
			cell.setPadding(dp(6), dp(10), dp(6), dp(10));
			cell.setClickable(true);
			cell.setOnClickListener(v -> addKey("pkg:" + pkg));
			ImageView iv = new ImageView(ctx);
			iv.setImageDrawable(ri.loadIcon(pm));
			cell.addView(iv, sq(64));
			TextView l = text(ctx, String.valueOf(ri.loadLabel(pm)), 13, false);
			l.setGravity(Gravity.CENTER);
			l.setMaxLines(1);
			l.setEllipsize(TextUtils.TruncateAt.END);
			l.setPadding(0, dp(6), 0, 0);
			cell.addView(l, new LinearLayout.LayoutParams(-1, -2));
			row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
			n++;
		}
		if ((row != null) && (n % perRow != 0)) {
			for (int i = n % perRow; i < perRow; i++) row.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
		}
		return col;
	}

	private void addKey(String k) {
		List<String> l = order(requireContext());
		if (!l.contains(k)) l.add(k);
		saveOrder(l);
		picking = false;
		render();
		UiUtils.showInfo(requireContext(), "Ana ekrana eklendi");
	}

	/**
	 * Büyük cam kart, ortada iOS uygulama simgesi gibi yuvarlatılmış kare.
	 * @param iconBg simge zemini; null ise simge (telefondaki uygulamanın kendi simgesi) tam boy çizilir
	 */
	private View appCard(Context ctx, String label, @Nullable android.graphics.drawable.Drawable iconBg, ImageView g,
											 int glyphDp, View.OnClickListener click) {
		// Çerçevesiz: simge hücreyi kaplayan büyük yuvarlatılmış kare, altında ad
		FrameLayout f = pressable(ctx, null, click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER_HORIZONTAL);
		SquareIcon ic = new SquareIcon(ctx, (iconBg == null) ? 1f : (glyphDp / 104f));
		if (iconBg != null) {
			ic.setBackground(iconBg);
			ic.setElevation(dp(6));
		}
		ic.addView(g, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
		FrameLayout holder = new FrameLayout(ctx);
		holder.addView(ic, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
		box.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));
		TextView l = text(ctx, label, YoldaStyle.compact(ctx) ? 13 : 17, true);
		l.setGravity(Gravity.CENTER);
		l.setMaxLines(1);
		l.setEllipsize(TextUtils.TruncateAt.END);
		l.setPadding(dp(4), dp(YoldaStyle.compact(ctx) ? 10 : 16), dp(4), 0);
		box.addView(l, new LinearLayout.LayoutParams(-1, -2));
		f.addView(box, new FrameLayout.LayoutParams(-1, -1));
		return f;
	}

	// ---- canlı kanallar rafı ----

	private void liveShelf(Context ctx, MainActivityDelegate a, LinearLayout col) {
		col.addView(shelfHead(ctx, "Canlı kanallar", "Tümünü gör", v -> a.showFragment(R.id.yolda_channels_fragment)));
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(row);
		col.addView(hs);

		a.getLib().getItem("TV").main().onCompletion((r, err) -> {
			if (!isAdded() || !(r instanceof MediaLib.BrowsableItem)) return;
			((MediaLib.BrowsableItem) r).getPlayableChildren(true).main().onCompletion((list, e2) -> {
				if (!isAdded() || (list == null)) return;
				int n = 0;
				for (MediaLib.PlayableItem pi : list) {
					MediaLib.BrowsableItem p = pi.getParent();
					if ((p != null) && "Radyo".equals(p.getName())) continue;
					if (ChannelsFragment.isHidden(ctx, pi.getName())) continue;
					row.addView(channelCard(ctx, a, pi));
					if (++n == 12) break;
				}
			});
		});
	}

	private View channelCard(Context ctx, MainActivityDelegate a, MediaLib.PlayableItem pi) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		c.setClickable(true);
		c.setOnClickListener(v -> a.getBody().playItem(pi));
		FrameLayout plate = new FrameLayout(ctx);
		GradientDrawable pb = new GradientDrawable();
		pb.setColor(0xEBFFFFFF);
		pb.setCornerRadius(dp(22));
		pb.setStroke(Math.max(1, dp(1)), 0xFFFFFFFF);
		plate.setBackground(pb);
		plate.setElevation(dp(4));
		TextView initials = text(ctx, initials(pi.getName()), 20, true);
		initials.setTextColor(0xFF1C1A2B);
		initials.setShadowLayer(0, 0, 0, 0);
		initials.setGravity(Gravity.CENTER);
		plate.addView(initials, new FrameLayout.LayoutParams(-1, -1));
		ImageView logo = new ImageView(ctx);
		logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
		logo.setPadding(dp(12), dp(10), dp(12), dp(10));
		logo.setVisibility(View.GONE);
		plate.addView(logo, new FrameLayout.LayoutParams(-1, -1));
		c.addView(plate, new LinearLayout.LayoutParams(dp(124), dp(86)));
		pi.getIconUri().main().onCompletion((u, err) -> {
			if (u == null) return;
			a.getLib().getBitmap(u.toString(), true, true).main().onCompletion((bmp, e2) -> {
				if (bmp == null) return;
				logo.setImageBitmap(bmp);
				logo.setVisibility(View.VISIBLE);
				initials.setText("");
			});
		});
		TextView n = text(ctx, pi.getName(), 14, true);
		n.setGravity(Gravity.CENTER);
		n.setMaxLines(1);
		n.setEllipsize(TextUtils.TruncateAt.END);
		n.setPadding(0, dp(8), 0, 0);
		c.addView(n, new LinearLayout.LayoutParams(dp(124), -2));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
		lp.rightMargin = dp(16);
		c.setLayoutParams(lp);
		return c;
	}

	// ---- YouTube kanallarım ----

	private void ytShelf(Context ctx, MainActivityDelegate a, LinearLayout col) {
		col.addView(shelfHead(ctx, "YouTube kanallarım", "Uzun bas: kaldır", null));
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		List<String[]> list = ytList();
		for (int i = 0; i < list.size(); i++) {
			String[] ch = list.get(i);
			final int idx = i;
			View card = ytCard(ctx, ch[0], v -> {
				if (!editing) openYoutube(a, ch[1]);
			});
			if (editing) {
				// Düzenleme: kanalı sola/sağa taşı ya da kaldır
				FrameLayout wrap = new FrameLayout(ctx);
				wrap.addView(card, new FrameLayout.LayoutParams(-1, -1));
				LinearLayout bar = new LinearLayout(ctx);
				bar.setOrientation(LinearLayout.HORIZONTAL);
				final int n = list.size();
				if (idx > 0) bar.addView(miniButton(ctx, R.drawable.yi_left, v -> moveYt(idx, idx - 1)));
				bar.addView(miniButton(ctx, R.drawable.yi_close, v -> {
					List<String[]> l = ytList();
					if (idx < l.size()) l.remove(idx);
					saveYt(l);
					render();
				}));
				if (idx < n - 1) bar.addView(miniButton(ctx, R.drawable.yi_right, v -> moveYt(idx, idx + 1)));
				wrap.addView(bar, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
				LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(170), dp(150));
				wlp.rightMargin = dp(14);
				row.addView(wrap, wlp);
				continue;
			}
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

	private void moveYt(int from, int to) {
		List<String[]> l = ytList();
		if ((from < 0) || (from >= l.size()) || (to < 0) || (to >= l.size())) return;
		l.add(to, l.remove(from));
		saveYt(l);
		render();
	}

	private View shelfHead(Context ctx, String title, String action, @Nullable View.OnClickListener click) {
		LinearLayout h = new LinearLayout(ctx);
		h.setOrientation(LinearLayout.HORIZONTAL);
		h.setGravity(Gravity.CENTER_VERTICAL);
		h.setPadding(dp(2), dp(28), dp(2), dp(14));
		h.addView(text(ctx, title, 22, true), new LinearLayout.LayoutParams(0, -2, 1f));
		TextView act = text(ctx, action, 14, click != null);
		act.setTextColor(0xD9FFFFFF);
		if (click != null) {
			act.setClickable(true);
			act.setOnClickListener(click);
		}
		h.addView(act);
		return h;
	}

	private LinearLayout.LayoutParams shelfLp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(132));
		lp.rightMargin = dp(14);
		return lp;
	}

	private View ytCard(Context ctx, String name, View.OnClickListener click) {
		FrameLayout f = pressable(ctx, YoldaStyle.glass(ctx, t, 28), click);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.setPadding(dp(10), dp(12), dp(10), dp(10));
		FrameLayout av = new FrameLayout(ctx);
		av.setBackground(YoldaStyle.appIcon(ctx, 0xFFFFFFFF, 0xFFF1F1F1, 999));
		ImageView yt = new ImageView(ctx);
		yt.setImageResource(R.drawable.yi_brand_youtube);
		av.addView(yt, new FrameLayout.LayoutParams(dp(30), dp(22), Gravity.CENTER));
		box.addView(av, sq(56));
		TextView n = text(ctx, name, 14, true);
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
		bg.setColor(0x14FFFFFF);
		bg.setStroke(dp(2), 0x8CFFFFFF, dp(8), dp(6));
		bg.setCornerRadius(dp(28));
		FrameLayout f = pressable(ctx, bg, v -> addYoutube(ctx));
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		ImageView plus = new ImageView(ctx);
		plus.setImageResource(R.drawable.yi_add);
		plus.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		box.addView(plus, sq(40));
		f.setContentDescription("YouTube kanalı ekle");
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

	/** Tarayıcı bölümünde bir site açar (YouTube Music gibi). Alt menüdeki "Müzik" de bunu kullanır. */
	static void openWeb(MainActivityDelegate a, String url) {
		// YouTube Music: tarayıcı çubuğu yok, tam ekran (eskisi gibi site, ama adres çubuğu görünmez)
		YoldaStyle.webNoToolbar = url.contains("music.youtube.com");
		Object f = a.showFragment(R.id.web_browser_fragment);
		a.postBroadcastEvent(me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED);
		// YouTube Music zaten açıksa sayfayı baştan yükleme: çalan şarkı kesilip ekran donmuş gibi oluyordu
		if (MUSIC_URL.equals(url) && isMusicPage(f)) return;
		invokeLoadUrl(f, url);
	}

	private static boolean isMusicPage(Object f) {
		if (f == null) return false;
		try {
			Object u = f.getClass().getMethod("getUrl").invoke(f);
			return (u instanceof String s) && s.contains("music.youtube.com");
		} catch (Exception ex) {
			return false;
		}
	}

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

	/** Telefon ekranı: Android Auto listesindeki "Yolda Telefon"; dokunmak için erişilebilirlik izni gerekir. */
	static void phone(Context ctx) {
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

	@SuppressLint("ClickableViewAccessibility")
	private FrameLayout pressable(Context ctx, android.graphics.drawable.Drawable bg, View.OnClickListener click) {
		FrameLayout f = new FrameLayout(ctx);
		f.setBackground(bg);
		f.setClickable(true);
		f.setFocusable(true);
		f.setOnClickListener(click);
		f.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
			return false;
		});
		return f;
	}

	/** Yuvarlak cam düğme (mikrofon, tema). */
	private View roundGlass(Context ctx, @DrawableRes int res, View.OnClickListener click) {
		FrameLayout b = pressable(ctx, YoldaStyle.pill(ctx, t), click);
		ImageView iv = new ImageView(ctx);
		iv.setImageResource(res);
		iv.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		b.addView(iv, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
		return b;
	}

	/** Beyaz yazı, hafif gölgeyle (canlı zeminde okunur). */
	private TextView text(Context ctx, String s, float size, boolean bold) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size * me.aap.fermata.ui.view.YoldaStyle.textScale(ctx)); // araçta küçük
		tv.setTextColor(0xFFFFFFFF);
		tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
		tv.setShadowLayer(dp(8), 0, dp(1), 0x4D000000);
		return tv;
	}

	private void animateIn(LinearLayout col) {
		float dy = dp(20);
		for (int i = 0; i < col.getChildCount(); i++) {
			View v = col.getChildAt(i);
			v.setAlpha(0f);
			v.setTranslationY(dy);
			v.animate().alpha(1f).translationY(0f).setStartDelay(i * 60L).setDuration(320)
					.setInterpolator(new DecelerateInterpolator()).start();
		}
	}

	/** Uygulama kartı yüksekliği: dar telefonda küçük, alçak araç ekranında ekrana sığacak kadar. */
	private static int cardH(Context ctx) {
		if (YoldaStyle.compact(ctx)) return 132;
		if (YoldaStyle.shortScreen(ctx)) return Math.max(170, Math.min(210, YoldaStyle.heightDp(ctx) / 2 - 20));
		return 224;
	}

	private static int iconDp(Context ctx) {
		if (YoldaStyle.compact(ctx)) return 64;
		if (YoldaStyle.shortScreen(ctx)) return Math.round(cardH(ctx) * 0.46f);
		return 104;
	}

	private static int btnDp(Context ctx) {
		return YoldaStyle.compact(ctx) ? 48 : 58;
	}

	/** Kendine verilen alanın kısa kenarı kadar kare; içindeki simge kenarın {@code frac} oranında. */
	private static final class SquareIcon extends FrameLayout {
		private final float frac;

		SquareIcon(Context ctx, float frac) {
			super(ctx);
			this.frac = frac;
		}

		@Override
		protected void onMeasure(int wSpec, int hSpec) {
			int s = Math.min(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
			int g = Math.round(s * frac);
			for (int i = 0; i < getChildCount(); i++) {
				android.view.ViewGroup.LayoutParams lp = getChildAt(i).getLayoutParams();
				lp.width = g;
				lp.height = g;
			}
			int e = MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY);
			super.onMeasure(e, e);
		}
	}

	private LinearLayout.LayoutParams sq(int sizeDp) {
		return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
	}

	private LinearLayout.LayoutParams leftMargin(LinearLayout.LayoutParams lp, int v) {
		lp.leftMargin = dp(v);
		return lp;
	}

	private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams lp, int v) {
		lp.topMargin = dp(v);
		return lp;
	}

	private static String initials(String name) {
		StringBuilder b = new StringBuilder();
		for (String w : name.trim().split("\\s+")) {
			if (!w.isEmpty() && b.length() < 3) b.appendCodePoint(w.codePointAt(0));
		}
		return b.toString().toUpperCase(new Locale("tr"));
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
