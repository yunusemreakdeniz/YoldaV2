package me.aap.fermata.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.media.yt.YtMusicItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MusicFragment;

/**
 * Yolda "Şimdi çalıyor": uygulamanın her yerinde tek, tam ekran oynatıcı. Müzik (büyük kapak, ilerleme,
 * sıradakiler), radyo ve canlı yayın (logo, CANLI) aynı ekranı kullanır. Oynatıcı çubuğuna dokununca açılır;
 * aşağı ok, geri tuşu ya da yeniden çubuk ile kapanır. Bilgiler doğrudan oynatıcıdan okunur, böylece
 * direksiyon tuşuyla şarkı değişse de ekran hep doğru şarkıyı gösterir.
 */
public final class YoldaNowPlaying extends FrameLayout implements MediaSessionCallback.Listener {
	private static final int TAG_KEY = 0x7f0b5e11;

	// ---- dokunulan ama henüz çalmaya başlamamış içerik (ör. şarkı akışı aranıyor) ----

	private static final class Pending {
		final String title;
		final String sub;
		@Nullable
		final String thumb;
		final long at = SystemClock.elapsedRealtime();

		Pending(String title, String sub, @Nullable String thumb) {
			this.title = title;
			this.sub = sub;
			this.thumb = thumb;
		}
	}

	@Nullable
	private static volatile Pending pending;

	/** Bir şarkıya dokunuldu: akış bulunana kadar çubuk ve bu ekran o şarkıyı "Bağlanıyor…" ile gösterir. */
	public static void expect(String title, @Nullable String sub, @Nullable String thumb) {
		pending = new Pending(title, (sub != null) ? sub : "", thumb);
	}

	public static void clearExpected() {
		pending = null;
	}

	/** Bekleyen içerik (çalma başladı, hata verdi ya da 25 sn geçtiyse yok sayılır). */
	@Nullable
	static Pending pending(MediaSessionCallback cb) {
		Pending p = pending;
		if (p == null) return null;
		long now = SystemClock.elapsedRealtime();
		PlaybackStateCompat st = cb.getPlaybackState();
		boolean settled = (st != null) && (st.getLastPositionUpdateTime() > p.at + 300)
				&& ((st.getState() == PlaybackStateCompat.STATE_PLAYING) || (st.getState() == PlaybackStateCompat.STATE_ERROR));
		if (settled || (now - p.at > 25_000)) {
			if (pending == p) pending = null;
			return null;
		}
		return p;
	}

	@Nullable
	static String pendingTitle(MediaSessionCallback cb) {
		Pending p = pending(cb);
		return (p != null) ? p.title : null;
	}

	@Nullable
	static String pendingSub(MediaSessionCallback cb) {
		Pending p = pending(cb);
		return (p != null) ? p.sub : null;
	}

	@Nullable
	static String pendingThumb(MediaSessionCallback cb) {
		Pending p = pending(cb);
		return (p != null) ? p.thumb : null;
	}

	// ---- YouTube oynatıcısı yedeğiyle çalan Yolda Müzik şarkısı ----

	/**
	 * Müzik doğrudan çalınamayınca şarkılar gizli YouTube oynatıcısında çalar; o zaman oynatıcı tüm şarkılar
	 * için aynı "YouTube" öğesini görür ve ad / resim değişmiyordu. Müzik ekranı çalan şarkıyı buraya bildirir;
	 * çubuk ve bu ekran YouTube motoru çalarken bu bilgiyi gösterir.
	 */
	public record External(YtMusic.Track track, List<YtMusic.Track> queue, int index) {
	}

	@Nullable
	private static volatile External external;

	public static void setExternal(@Nullable External e) {
		external = e;
	}

	/** YouTube motoru çalıyorsa bildirilen şarkı; değilse null. */
	@Nullable
	static External external(MediaSessionCallback cb) {
		External e = external;
		if (e == null) return null;
		var eng = cb.getEngine();
		// YouTube oynatıcısı henüz başlamadıysa (ilk şarkı) da göster; başka bir şey çalıyorsa gösterme
		if ((eng != null) && !eng.getClass().getName().contains("Youtube")) return null;
		return e;
	}

	// ---- oturumun güncel bilgisi: aynı öğede yayın / şarkı / program değişince de ad ve resim yenilenir ----

	@Nullable
	static String metaSig(@Nullable MediaMetadataCompat md) {
		if (md == null) return null;
		return metaTitle(md) + "|" + metaSub(md) + "|" + metaArtUri(md) + "|" + (metaBitmap(md) != null);
	}

	@Nullable
	static String metaTitle(@Nullable MediaMetadataCompat md) {
		if (md == null) return null;
		String s = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
		return TextUtils.isEmpty(s) ? md.getString(MediaMetadataCompat.METADATA_KEY_TITLE) : s;
	}

	@Nullable
	static String metaSub(@Nullable MediaMetadataCompat md) {
		if (md == null) return null;
		for (String k : new String[]{MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
				MediaMetadataCompat.METADATA_KEY_ARTIST, MediaMetadataCompat.METADATA_KEY_ALBUM}) {
			String s = md.getString(k);
			if (!TextUtils.isEmpty(s)) return s;
		}
		return null;
	}

	@Nullable
	static String metaArtUri(@Nullable MediaMetadataCompat md) {
		if (md == null) return null;
		for (String k : new String[]{MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
				MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, MediaMetadataCompat.METADATA_KEY_ART_URI}) {
			String s = md.getString(k);
			if (!TextUtils.isEmpty(s)) return s;
		}
		return null;
	}

	@Nullable
	static Bitmap metaBitmap(@Nullable MediaMetadataCompat md) {
		if (md == null) return null;
		for (String k : new String[]{MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
				MediaMetadataCompat.METADATA_KEY_ART, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON}) {
			Bitmap b = md.getBitmap(k);
			if (b != null) return b;
		}
		return null;
	}

	// ---- ortak eylemler (çubuk ve bu ekran) ----

	/** Yayın açılıyor mu (bağlanıyor / tamponluyor ya da dokunulan şarkı aranıyor). */
	static boolean isBusy(MediaSessionCallback cb) {
		if (pending(cb) != null) return true;
		PlaybackStateCompat st = cb.getPlaybackState();
		int s = (st != null) ? st.getState() : PlaybackStateCompat.STATE_NONE;
		if ((s == PlaybackStateCompat.STATE_BUFFERING) || (s == PlaybackStateCompat.STATE_CONNECTING)) return true;
		long since = MediaSessionCallback.yoldaOpeningSince();
		return (since > 0) && (SystemClock.elapsedRealtime() - since < 30_000) && (s != PlaybackStateCompat.STATE_ERROR);
	}

	static boolean isError(MediaSessionCallback cb) {
		PlaybackStateCompat st = cb.getPlaybackState();
		return (st != null) && (st.getState() == PlaybackStateCompat.STATE_ERROR) && (pending(cb) == null);
	}

	static boolean isStopped(MediaSessionCallback cb) {
		PlaybackStateCompat st = cb.getPlaybackState();
		int s = (st != null) ? st.getState() : PlaybackStateCompat.STATE_NONE;
		return (s == PlaybackStateCompat.STATE_STOPPED) || (s == PlaybackStateCompat.STATE_NONE)
				|| (s == PlaybackStateCompat.STATE_ERROR);
	}

	/**
	 * Oynat / duraklat. Hata ya da durdurulmuş durumda aynı içeriği yeniden açar (Fermata'nın varsayılanı
	 * "son çalınan" başka bir kanalı açabiliyordu); şarkının akış adresi süresi dolmuş olabilir, yeniden aranır.
	 */
	public static void togglePlay(MainActivityDelegate a) {
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (cb.isPlaying()) {
			cb.onPause();
			return;
		}
		MediaLib.PlayableItem pi = a.getCurrentPlayable();
		if (isStopped(cb) && (pi != null)) {
			if (pi instanceof YtMusicItem m) {
				YtMusic.Track tr = m.getTrack();
				if (isError(cb)) YtMusic.forget(tr); // akış adresinin süresi dolmuş olabilir: yeniden ara
				expect(tr.title(), tr.artist(), tr.thumb());
				YtMusicItem.create(a.getLib(), m.getQueue(), m.getIndex()).main().onCompletion((item, err) -> {
					if (item != null) a.getMediaServiceBinder().playItem(item);
					else clearExpected();
				});
			} else {
				a.getMediaServiceBinder().playItem(pi);
			}
			return;
		}
		cb.onPlay();
	}

	/**
	 * Çubuğa dokununca çalan içeriğe git: video oynuyorsa tam ekran video (YouTube ise YouTube sayfası),
	 * değilse bu ekran.
	 */
	public static void open(MainActivityDelegate a) {
		MediaLib.PlayableItem pi = a.getCurrentPlayable();
		boolean pend = pending(a.getMediaSessionCallback()) != null;
		var eng0 = a.getMediaSessionCallback().getEngine();
		boolean hasPicture = (eng0 != null) && ((eng0.getVideoWidth() > 0)
				|| eng0.getClass().getName().contains("Youtube"));
		if ((pi != null) && !pend && pi.isVideo() && (hasPicture || isBusy(a.getMediaSessionCallback()))) {
			var eng = eng0;
			if ((eng != null) && eng.getClass().getName().contains("Youtube")) {
				a.showFragment(R.id.youtube_fragment);
				return;
			}
			BodyLayout b = a.getBody();
			if (b != null) {
				b.setMode(BodyLayout.Mode.VIDEO);
				return;
			}
		}
		show(a);
	}

	public static void show(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl)) return;
		if (find(cl) != null) return;
		YoldaNowPlaying v = new YoldaNowPlaying(a.getContext());
		v.setTag(TAG_KEY, Boolean.TRUE);
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
		lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		v.setElevation(v.dp(30));
		cl.addView(v, lp);
		View nb = a.getNavBar();
		if ((nb != null) && !YoldaStyle.DECK) nb.setVisibility(INVISIBLE); // sesli düğme menüyle birlikte gizlenir
		View body = a.getBody();
		if (body != null) v.postDelayed(() -> {
			if (v.getTag(TAG_KEY) != null) body.setVisibility(INVISIBLE);
		}, 300);
		v.setAlpha(0f);
		v.setTranslationY(v.dp(48));
		v.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator(2f)).setDuration(260).start();
	}

	/** Açıksa kapatır; kapattıysa true (geri tuşu için). */
	public static boolean hide(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		if (!(host instanceof ViewGroup g)) return false;
		YoldaNowPlaying v = find(g);
		if (v == null) return false;
		v.setTag(TAG_KEY, null);
		View body = a.getBody();
		if (body != null) body.setVisibility(VISIBLE);
		v.animate().alpha(0f).translationY(v.dp(48)).setDuration(180).withEndAction(() -> g.removeView(v)).start();
		View nb = a.getNavBar();
		if ((nb != null) && !a.isBarsHidden() && !YoldaStyle.DECK) nb.setVisibility(VISIBLE);
		return true;
	}

	public static boolean isShown(MainActivityDelegate a) {
		View host = a.findViewById(R.id.main_activity);
		return (host instanceof ViewGroup g) && (find(g) != null);
	}

	@Nullable
	private static YoldaNowPlaying find(ViewGroup g) {
		for (int i = 0; i < g.getChildCount(); i++) {
			if ((g.getChildAt(i) instanceof YoldaNowPlaying v) && (v.getTag(TAG_KEY) != null)) return v;
		}
		return null;
	}

	// ---- görünüm ----

	private final YoldaStyle.Theme t;
	private final boolean wide;
	private final ImageView backdrop;
	private final FrameLayout art;
	private final ImageView cover;
	private final ImageView coverPh;
	private final TextView label;
	private final TextView title;
	private final TextView sub;
	private final TextView live;
	private final YoldaSeekLine seek;
	private final LinearLayout times;
	private final TextView pos;
	private final TextView dur;
	private final ImageView play;
	private final BusyRing ring;
	private final LinearLayout body;
	private final LinearLayout info;
	private final LinearLayout queueBox;
	private final LinearLayout queueRow;
	private final Runnable ticker = this::tick;
	private String itemKey;
	private final long shownAt = SystemClock.elapsedRealtime();
	@Nullable
	private String metaKey;
	private boolean dragging;
	private boolean logo;

	private YoldaNowPlaying(Context ctx) {
		super(ctx);
		t = YoldaStyle.current(ctx);
		wide = YoldaStyle.widthDp(ctx) > YoldaStyle.heightDp(ctx) * 1.15f;
		float ts = YoldaStyle.textScale(ctx);
		setClickable(true); // alttaki ekrana dokunuş geçmesin
		setBackgroundColor(t.base | 0xFF000000);

		// Kapaktan bulanık arka plan + karartma (cam hissi)
		backdrop = new ImageView(ctx);
		backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
		backdrop.setAlpha(0f);
		boolean car = false;
		try {
			car = MainActivityDelegate.get(ctx).isCarActivity();
		} catch (Throwable ignore) {
		}
		if ((Build.VERSION.SDK_INT >= 31) && !car) { // araçta bulanıklık yok: her karede pahalı
			backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(dp(60), dp(60),
					android.graphics.Shader.TileMode.CLAMP));
		}
		if (car || (Build.VERSION.SDK_INT < 31)) backdrop.setVisibility(GONE); // bulanıksız kapak arka planı çirkin
		addView(backdrop, new LayoutParams(-1, -1));
		View dim = new View(ctx);
		dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{(t.base & 0x00FFFFFF) | 0x99000000, (t.base & 0x00FFFFFF) | 0xE6000000}));
		addView(dim, new LayoutParams(-1, -1));

		int cut = YoldaStyle.cutoutTopPx;
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(wide ? 24 : 20), cut + dp(8), dp(wide ? 24 : 20), dp(wide ? 14 : 24));
		addView(col, new LayoutParams(-1, -1));

		// Üst satır: aşağı ok · etiket
		LinearLayout top = new LinearLayout(ctx);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		ImageView down = iconButton(ctx, R.drawable.yi_left, "Geri");
		down.setBackground(YoldaStyle.glass(ctx, t, 999));
		down.setOnClickListener(v -> {
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			hide(a);
		});
		top.addView(down, new LinearLayout.LayoutParams(dp(48), dp(48)));
		label = text(ctx, "ŞİMDİ ÇALIYOR", 12 * ts, true, t.muted);
		label.setLetterSpacing(0.14f);
		label.setGravity(Gravity.CENTER);
		top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
		top.addView(new View(ctx), new LinearLayout.LayoutParams(dp(48), dp(48)));
		col.addView(top, new LinearLayout.LayoutParams(-1, -2));

		// Gövde: kapak + bilgiler (yatayda yan yana, dikeyde alt alta)
		body = new LinearLayout(ctx);
		body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		body.setGravity(Gravity.CENTER);
		col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

		art = new FrameLayout(ctx);
		art.setClipToOutline(true);
		art.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(22));
			}
		});
		art.setElevation(dp(14));
		coverPh = new ImageView(ctx);
		coverPh.setImageResource(R.drawable.yi_music);
		coverPh.setColorFilter(0xE6FFFFFF);
		coverPh.setScaleType(ImageView.ScaleType.FIT_CENTER);
		art.addView(coverPh, new LayoutParams(-1, -1));
		cover = new ImageView(ctx);
		cover.setAlpha(0f);
		art.addView(cover, new LayoutParams(-1, -1));
		body.addView(art, new LinearLayout.LayoutParams(dp(180), dp(180)));

		info = new LinearLayout(ctx);
		info.setOrientation(LinearLayout.VERTICAL);
		info.setGravity(wide ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
		title = text(ctx, "", 24 * ts, true, t.ink);
		title.setMaxLines(2);
		title.setEllipsize(TextUtils.TruncateAt.END);
		title.setGravity(wide ? Gravity.START : Gravity.CENTER);
		info.addView(title, new LinearLayout.LayoutParams(-1, -2));

		LinearLayout subRow = new LinearLayout(ctx);
		subRow.setOrientation(LinearLayout.HORIZONTAL);
		subRow.setGravity((wide ? Gravity.START : Gravity.CENTER_HORIZONTAL) | Gravity.CENTER_VERTICAL);
		subRow.setPadding(0, dp(4), 0, dp(10));
		live = text(ctx, "CANLI", 11 * ts, true, 0xFFFFFFFF);
		live.setLetterSpacing(0.1f);
		live.setPadding(dp(8), dp(2), dp(8), dp(2));
		GradientDrawable lb = new GradientDrawable();
		lb.setColor(YoldaStyle.LIVE);
		lb.setCornerRadius(dp(5));
		live.setBackground(lb);
		live.setVisibility(GONE);
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
		llp.rightMargin = dp(8);
		subRow.addView(live, llp);
		sub = text(ctx, "", 16 * ts, false, t.muted);
		sub.setSingleLine(true);
		sub.setEllipsize(TextUtils.TruncateAt.END);
		subRow.addView(sub, new LinearLayout.LayoutParams(-2, -2));
		info.addView(subRow, new LinearLayout.LayoutParams(-1, -2));

		// 0:02 ━━━━●──── 3:54 — süreler çubuğun iki yanında, tek satır; geniş dokunma alanı
		times = new LinearLayout(ctx);
		times.setOrientation(LinearLayout.HORIZONTAL);
		times.setGravity(Gravity.CENTER_VERTICAL);
		pos = text(ctx, "0:00", 14 * ts, true, t.ink);
		pos.setGravity(Gravity.END);
		pos.setMinWidth(dp(48));
		dur = text(ctx, "", 14 * ts, false, t.muted);
		dur.setMinWidth(dp(48));
		seek = new YoldaSeekLine(ctx);
		times.addView(pos);
		LinearLayout.LayoutParams sk = new LinearLayout.LayoutParams(0, dp(48), 1f);
		sk.leftMargin = sk.rightMargin = dp(10);
		times.addView(seek, sk);
		times.addView(dur);
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
		tlp.topMargin = dp(6);
		info.addView(times, tlp);

		// Kontroller: önceki · oynat/duraklat (bağlanırken dönen halka) · sonraki
		LinearLayout ctl = new LinearLayout(ctx);
		ctl.setOrientation(LinearLayout.HORIZONTAL);
		ctl.setGravity(Gravity.CENTER);
		ctl.setPadding(0, dp(8), 0, 0);
		ImageView prev = iconButton(ctx, R.drawable.yi_prev, "Önceki");
		prev.setBackground(YoldaStyle.glass(ctx, t, 999));
		prev.setPadding(dp(18), dp(18), dp(18), dp(18));
		prev.setOnClickListener(v -> cb().onSkipToPrevious());
		ImageView next = iconButton(ctx, R.drawable.yi_next, "Sonraki");
		next.setBackground(YoldaStyle.glass(ctx, t, 999));
		next.setPadding(dp(18), dp(18), dp(18), dp(18));
		next.setOnClickListener(v -> cb().onSkipToNext());
		FrameLayout pf = new FrameLayout(ctx);
		ring = new BusyRing(ctx);
		pf.addView(ring, new LayoutParams(-1, -1));
		play = iconButton(ctx, R.drawable.yi_pause, "Oynat / duraklat");
		play.setColorFilter(YoldaStyle.ON_ACCENT);
		GradientDrawable pc = new GradientDrawable();
		pc.setShape(GradientDrawable.OVAL);
		pc.setColor(0xFFFFFFFF);
		play.setBackground(pc);
		play.setPadding(dp(24), dp(24), dp(24), dp(24));
		play.setOnClickListener(v -> {
			togglePlay(MainActivityDelegate.get(getContext()));
			play.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
					.withEndAction(() -> play.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
			post(ticker);
		});
		pf.addView(play, new LayoutParams(dp(80), dp(80), Gravity.CENTER));
		ctl.addView(prev, new LinearLayout.LayoutParams(dp(64), dp(64)));
		LinearLayout.LayoutParams pfl = new LinearLayout.LayoutParams(dp(96), dp(96));
		pfl.setMargins(dp(22), 0, dp(22), 0);
		ctl.addView(pf, pfl);
		ctl.addView(next, new LinearLayout.LayoutParams(dp(64), dp(64)));
		info.addView(ctl, new LinearLayout.LayoutParams(-1, -2));

		// Sıradakiler (Yolda Müzik listesi)
		queueBox = new LinearLayout(ctx);
		queueBox.setOrientation(LinearLayout.VERTICAL);
		queueBox.setPadding(0, dp(14), 0, 0);
		queueBox.setVisibility(GONE);
		TextView qt = text(ctx, "SIRADAKİ", 12 * ts, true, t.muted);
		qt.setLetterSpacing(0.14f);
		qt.setPadding(dp(4), 0, 0, dp(8));
		queueBox.addView(qt);
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		queueRow = new LinearLayout(ctx);
		queueRow.setOrientation(LinearLayout.HORIZONTAL);
		hs.addView(queueRow);
		queueBox.addView(hs, new LinearLayout.LayoutParams(-1, -2));
		info.addView(queueBox, new LinearLayout.LayoutParams(-1, -2));

		LinearLayout.LayoutParams ilp = wide ? new LinearLayout.LayoutParams(0, -2, 1f)
				: new LinearLayout.LayoutParams(-1, -2);
		if (wide) ilp.leftMargin = dp(32);
		else ilp.topMargin = dp(20);
		body.addView(info, ilp);

		seek.setListener(new YoldaSeekLine.Listener() {
			@Override
			public void onDrag(float f) {
				dragging = true;
				long d = durationNow();
				if (d > 0) pos.setText(MusicFragment.fmt((long) (d * f) / 1000)); // gidilecek yer
			}

			@Override
			public void onSeek(float f) {
				dragging = false;
				long d = durationNow();
				if (d > 0) cb().onSeekTo((long) (d * f));
			}
		});
		body.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> {
			if ((r - l != or - ol) || (b - tp != ob - ot)) post(this::fitCover);
		});
	}

	// ---- yaşam döngüsü ----

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		try {
			cb().addBroadcastListener(this);
		} catch (Throwable ignore) {
		}
		post(ticker);
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(ticker);
		ring.setBusy(false);
		try {
			cb().removeBroadcastListener(this);
		} catch (Throwable ignore) {
		}
		super.onDetachedFromWindow();
	}

	@Override
	public void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {
		post(ticker);
	}

	private void tick() {
		removeCallbacks(ticker);
		if (!isAttachedToWindow()) return;
		try {
			update();
		} catch (Throwable ignore) {
		}
		postDelayed(ticker, 300);
	}

	private void update() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		MediaSessionCallback cb = a.getMediaSessionCallback();
		boolean busy = isBusy(cb);
		ring.setBusy(busy);
		play.setImageResource(cb.isPlaying() ? R.drawable.yi_pause : R.drawable.yi_play);

		Pending pd = pending(cb);
		MediaLib.PlayableItem opening = MediaSessionCallback.yoldaOpeningItem();
		MediaLib.PlayableItem pi = (opening != null) ? opening : a.getCurrentPlayable();
		if (pd != null) {
			String key = "pending:" + pd.title + pd.at;
			if (!key.equals(itemKey)) {
				itemKey = key;
				showInfo(pd.title, pd.sub, false);
				loadCover(a, MusicFragment.bigThumb(pd.thumb), key, false);
				queueBox.setVisibility(GONE);
			}
			label.setText("BAĞLANIYOR…");
			setProgress(0, 0, false);
			return;
		}
		External ex = external(cb);
		if (ex != null) { // YouTube yedeğiyle çalan şarkı: kendi adı, kapağı, sıradakileri
			String k = "ext:" + ex.index() + ":" + ex.track().id();
			if (!k.equals(itemKey)) {
				itemKey = k;
				logo = false;
				YtMusic.Track tr = ex.track();
				showInfo(tr.title(), (tr.artist() != null) ? tr.artist() : "", false);
				loadCover(a, MusicFragment.bigThumb(tr.thumb()), k, false);
				fillQueue(a, ex.queue(), ex.index(), true);
			}
			live.setVisibility(GONE);
			label.setText(busy ? "BAĞLANIYOR…" : "ŞİMDİ ÇALIYOR");
			long d = Math.max(0, ex.track().durationSec()) * 1000;
			setProgress(position(cb), d, true);
			return;
		}
		if (pi == null) {
			// Yeni açıldıysa hemen kapanma: çalma birkaç saniye içinde başlar (ilk şarkıda ekran kapanıyordu)
			if (SystemClock.elapsedRealtime() - shownAt > 15_000) hide(a);
			else label.setText("BAĞLANIYOR…");
			return;
		}
		String key = pi.getId();
		if (!key.equals(itemKey)) {
			itemKey = key;
			metaKey = metaSig(cb.getYoldaMetadata());
			loadItem(a, pi, key);
		}
		if ((opening == null) && YoldaPlayerBar.isYoutube(cb)) applyMeta(a, cb.getYoldaMetadata(), pi, key);
		long d = duration();
		boolean isLive = (d <= 0) && pi.isStream();
		live.setVisibility(isLive ? VISIBLE : GONE);
		label.setText(isError(cb) ? "AÇILAMADI · TEKRAR DENEMEK İÇİN ▶" : busy ? "BAĞLANIYOR…"
				: isLive ? "CANLI YAYIN" : "ŞİMDİ ÇALIYOR");
		setProgress(position(cb), d, !isLive);
	}

	private long lastDur;

	/** Sürükleme sırasında kullanılan süre (YouTube yedeğindeki şarkıda öğe süresi yok). */
	private long durationNow() {
		long d = duration();
		return (d > 0) ? d : lastDur;
	}

	private void setProgress(long p, long d, boolean show) {
		int vis = (show && (d > 0)) ? VISIBLE : GONE;
		if (times.getVisibility() != vis) post(this::fitCover); // kapak kalan alana göre yeniden boyutlansın
		times.setVisibility(vis);
		if (d > 0) lastDur = d;
		if ((d <= 0) || dragging) return;
		seek.setProgress(Math.min(1f, p / (float) d));
		pos.setText(MusicFragment.fmt(p / 1000));
		dur.setText(MusicFragment.fmt(d / 1000));
	}

	private void loadItem(MainActivityDelegate a, MediaLib.PlayableItem pi, String key) {
		boolean music = pi instanceof YtMusicItem;
		logo = !music && pi.isStream(); // TV / radyo: kanal logosu beyaz plaka üzerinde
		showInfo(pi.getName(), "", logo);
		pi.getMediaDescription().main().onSuccess(md -> {
			if (!key.equals(itemKey) || (md == null)) return;
			title.setText(pi.getName()); // açıklama başlığı sıra numarası içerebiliyor ("1. TRT 1")
			CharSequence s = md.getSubtitle();
			if (TextUtils.isEmpty(s)) {
				MediaLib.BrowsableItem p = pi.getParent();
				s = (p != null) ? p.getName() : "";
			}
			sub.setText(s);
		});
		if (music) {
			YtMusicItem m = (YtMusicItem) pi;
			loadCover(a, MusicFragment.bigThumb(m.getTrack().thumb()), key, false);
			fillQueue(a, m.getQueue(), m.getIndex(), false);
		} else {
			queueBox.setVisibility(GONE);
			pi.getIconUri().main().onCompletion((u, err) -> {
				if (key.equals(itemKey)) loadCover(a, (u != null) ? u.toString() : null, key, logo);
			});
		}
	}

	/** Oturum bilgisi değiştiyse (YouTube'da yeni video, TV'de yeni program) adı, alt bilgiyi, resmi yenile. */
	private void applyMeta(MainActivityDelegate a, @Nullable MediaMetadataCompat md, MediaLib.PlayableItem pi,
												 String key) {
		String sig = metaSig(md);
		if ((sig == null) || sig.equals(metaKey)) return;
		metaKey = sig;
		String tt = metaTitle(md);
		if (!TextUtils.isEmpty(tt)) title.setText(tt);
		String st = metaSub(md);
		if (!TextUtils.isEmpty(st)) sub.setText(st);
		Bitmap bm = metaBitmap(md);
		if (bm != null) {
			setCover(bm, logo);
			return;
		}
		String u = metaArtUri(md);
		if (u == null) return;
		if (pi instanceof YtMusicItem) u = MusicFragment.bigThumb(u);
		loadCover(a, u, key, logo);
	}

	private void showInfo(String name, String subtitle, boolean logoMode) {
		title.setText(name);
		sub.setText(subtitle);
		cover.animate().alpha(0f).setDuration(120).start();
		backdrop.animate().alpha(0f).setDuration(200).start();
		GradientDrawable ab;
		if (logoMode) {
			ab = new GradientDrawable();
			ab.setColor(0xF0FFFFFF);
		} else {
			ab = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.blobs[0], t.blobs[2]});
		}
		art.setBackground(ab);
		coverPh.setImageResource(logoMode ? R.drawable.yi_tv : R.drawable.yi_music);
		coverPh.setColorFilter(logoMode ? 0x55000000 : 0xE6FFFFFF);
		cover.setScaleType(logoMode ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
		fitCover();
	}

	private void loadCover(MainActivityDelegate a, @Nullable String url, String key, boolean logoMode) {
		if (url == null) return;
		a.getLib().getBitmap(url, true, true).main().onCompletion((bmp, err) -> {
			if ((bmp == null) || !key.equals(itemKey)) return;
			setCover(bmp, logoMode);
		});
	}

	private void setCover(Bitmap bmp, boolean logoMode) {
		cover.setImageBitmap(bmp);
		cover.animate().alpha(1f).setDuration(260).start();
		if (!logoMode) {
			backdrop.setImageBitmap(bmp);
			backdrop.animate().alpha(0.55f).setDuration(400).start();
		}
	}

	/** Kapak: ad + ilerleme + düğmeler sığacak en büyük kare; logo ise iç boşluklu. */
	private void fitCover() {
		int bw = body.getWidth();
		int bh = body.getHeight();
		if ((bw <= 0) || (bh <= 0)) return;
		int c;
		if (wide) {
			c = Math.min(bh - dp(8), (int) (bw * 0.42f));
		} else {
			info.measure(MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY),
					MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			c = Math.min(bw - dp(24), bh - info.getMeasuredHeight() - dp(28));
		}
		c = Math.max(dp(96), Math.min(dp(420), c));
		ViewGroup.LayoutParams lp = art.getLayoutParams();
		if ((lp.width != c) || (lp.height != c)) {
			lp.width = c;
			lp.height = c;
			art.setLayoutParams(lp);
		}
		int pad = logo ? c / 6 : c / 4;
		coverPh.setPadding(pad, pad, pad, pad);
		int cp = logo ? c / 8 : 0;
		cover.setPadding(cp, cp, cp, cp);
	}

	private void fillQueue(MainActivityDelegate a, List<YtMusic.Track> q, int current, boolean viaYoutube) {
		queueRow.removeAllViews();
		int from = current + 1;
		int to = Math.min(q.size(), from + 12);
		if (from >= to) {
			queueBox.setVisibility(GONE);
			return;
		}
		Context ctx = getContext();
		for (int i = from; i < to; i++) {
			YtMusic.Track tr = q.get(i);
			int idx = i;
			LinearLayout card = new LinearLayout(ctx);
			card.setOrientation(LinearLayout.HORIZONTAL);
			card.setGravity(Gravity.CENTER_VERTICAL);
			card.setPadding(dp(6), dp(6), dp(12), dp(6));
			card.setBackground(YoldaStyle.glass(ctx, t, 16));
			card.setClickable(true);
			card.setOnClickListener(v -> {
				if (viaYoutube) { // YouTube yedeği: Müzik ekranı çalar (aynı yoldan)
					MusicFragment.playTracks(a, q, idx);
					return;
				}
				expect(tr.title(), tr.artist(), tr.thumb());
				post(ticker);
				YtMusicItem.create(a.getLib(), q, idx).main().onCompletion((item, err) -> {
					if (item != null) a.getMediaServiceBinder().playItem(item);
					else clearExpected();
				});
			});
			ImageView th = new ImageView(ctx);
			th.setScaleType(ImageView.ScaleType.CENTER_CROP);
			th.setClipToOutline(true);
			th.setOutlineProvider(new android.view.ViewOutlineProvider() {
				@Override
				public void getOutline(View v, android.graphics.Outline o) {
					o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
				}
			});
			GradientDrawable ph = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.blobs[0], t.blobs[2]});
			th.setBackground(ph);
			card.addView(th, new LinearLayout.LayoutParams(dp(44), dp(44)));
			if (tr.thumb() != null) {
				a.getLib().getBitmap(tr.thumb(), true, true).main().onCompletion((bmp, err) -> {
					if (bmp != null) th.setImageBitmap(bmp);
				});
			}
			LinearLayout tx = new LinearLayout(ctx);
			tx.setOrientation(LinearLayout.VERTICAL);
			tx.setPadding(dp(10), 0, 0, 0);
			TextView n = text(ctx, tr.title(), 14, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			n.setMaxWidth(dp(160));
			tx.addView(n);
			TextView s = text(ctx, (tr.artist() != null) ? tr.artist() : "", 12, false, t.muted);
			s.setSingleLine(true);
			s.setEllipsize(TextUtils.TruncateAt.END);
			s.setMaxWidth(dp(160));
			tx.addView(s);
			card.addView(tx);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(56));
			lp.rightMargin = dp(10);
			queueRow.addView(card, lp);
		}
		queueBox.setVisibility(VISIBLE);
		post(this::fitCover);
	}

	// ---- yardımcılar ----

	private MediaSessionCallback cb() {
		return MainActivityDelegate.get(getContext()).getMediaSessionCallback();
	}

	private long duration() {
		MediaLib.PlayableItem pi = MainActivityDelegate.get(getContext()).getCurrentPlayable();
		if (pi == null) return 0;
		return YoldaPlayerBar.durationOf(pi);
	}

	static long position(MediaSessionCallback cb) {
		PlaybackStateCompat st = cb.getPlaybackState();
		if (st == null) return 0;
		long p = st.getPosition();
		if (st.getState() == PlaybackStateCompat.STATE_PLAYING) {
			long dt = SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime();
			if (dt > 0) p += (long) (dt * Math.max(0.1f, st.getPlaybackSpeed()));
		}
		return Math.max(0, p);
	}

	private ImageView iconButton(Context ctx, int icon, String desc) {
		ImageView b = new ImageView(ctx);
		b.setImageResource(icon);
		b.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		b.setScaleType(ImageView.ScaleType.FIT_CENTER);
		b.setPadding(dp(12), dp(12), dp(12), dp(12));
		b.setContentDescription(desc);
		b.setClickable(true);
		b.setFocusable(true);
		return b;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size);
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}

	/**
	 * Oynat düğmesinin çevresinde dönen ince halka: yayın bağlanırken / tamponlarken. Animatör yerine kare
	 * kare çizilir; cihazda animasyonlar kapalıysa (erişilebilirlik) halka döndürülmeden sabit çizilir
	 * (sonsuz animatör animasyonlar kapalıyken ana iş parçacığını durmadan meşgul ediyordu).
	 */
	static final class BusyRing extends View {
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final RectF r = new RectF();
		private boolean busy;

		BusyRing(Context ctx) {
			super(ctx);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeCap(Paint.Cap.ROUND);
			p.setColor(0xFFFFFFFF);
			setVisibility(INVISIBLE);
		}

		void setBusy(boolean b) {
			if (busy == b) return;
			busy = b;
			setVisibility(b ? VISIBLE : INVISIBLE);
			invalidate();
		}

		private static boolean animate(Context ctx) {
			if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
			try {
				return android.provider.Settings.Global.getFloat(ctx.getContentResolver(),
						android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
			} catch (Throwable ex) {
				return true;
			}
		}

		@Override
		protected void onDraw(@NonNull Canvas c) {
			if (!busy) return;
			float w = Math.max(2f, getWidth() * 0.035f);
			p.setStrokeWidth(w);
			r.set(w, w, getWidth() - w, getHeight() - w);
			boolean anim = animate(getContext());
			float angle = anim ? (SystemClock.uptimeMillis() % 900) * 360f / 900f : -90f;
			c.drawArc(r, angle, anim ? 100 : 300, false, p);
			if (anim && isAttachedToWindow()) postInvalidateOnAnimation();
		}
	}
}
