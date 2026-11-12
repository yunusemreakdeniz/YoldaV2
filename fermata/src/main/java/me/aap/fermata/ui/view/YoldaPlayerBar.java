package me.aap.fermata.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Yolda oynatıcı çubuğu: Fermata'nın kontrol panelinin yerine, Apple Music / Spotify tarzı tek satır cam kapsül.
 * Solda kapak, ortada ad ve alt bilgi, sağda önceki · oynat/duraklat · sonraki · ⋯ · ✕. Üstte ince ilerleme
 * çizgisi (sürüklenerek ileri/geri sarılır); canlı yayında "CANLI" rozeti. Her yarım saniyede oynatıcı
 * durumundan güncellenir (TV, müzik, YouTube hangi motor çalıyorsa).
 */
public class YoldaPlayerBar extends FrameLayout implements MediaSessionCallback.Listener {
	public static final int HEIGHT_DP = 72;
	private final GradientDrawable tintBg;

	/** Çubukta görünen ad (testler için). */
	public CharSequence getTitleText() {
		return title.getText();
	}

	private final ImageView cover;
	private final ImageView coverPh;
	private final TextView title;
	private final TextView sub;
	private final ImageView play;
	private final YoldaSeekLine progress;
	private final TextView live;
	private final YoldaNowPlaying.BusyRing ring;
	private final Runnable ticker = this::tick;
	private String itemId;
	private boolean dragging;

	public YoldaPlayerBar(@NonNull Context ctx) {
		super(ctx);
		setClipChildren(false);
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor((t.base & 0x00FFFFFF) | 0xF2000000);
		bg.setCornerRadius(dp(22));
		// Kapağın renginde ışıklı cam: soldan kapak renginde, sağa doğru koyu zemine erir
		tintBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00000000, 0x00000000});
		tintBg.setCornerRadius(dp(26));
		bg.setCornerRadius(dp(26));
		setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, tintBg,
				YoldaStyle.glass(ctx, t, 26)}));
		setElevation(dp(8));
		setClickable(true); // boş alana dokunuş alttaki eski düğmelere gitmesin
		boolean compact = YoldaStyle.compact(ctx);

		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(10), dp(6), dp(6), dp(12)); // altta ilerleme çizgisine yer
		addView(row, new LayoutParams(-1, -1));

		// Kapak
		FrameLayout art = new FrameLayout(ctx);
		GradientDrawable ab = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.blobs[0], t.blobs[2]});
		ab.setCornerRadius(dp(14));
		art.setBackground(ab);
		art.setClipToOutline(true);
		art.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
			}
		});
		coverPh = new ImageView(ctx);
		coverPh.setImageResource(R.drawable.yi_music);
		coverPh.setColorFilter(0xE6FFFFFF);
		coverPh.setPadding(dp(12), dp(12), dp(12), dp(12));
		art.addView(coverPh, new LayoutParams(-1, -1));
		cover = new ImageView(ctx);
		cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
		cover.setAlpha(0f);
		art.addView(cover, new LayoutParams(-1, -1));
		art.setOnClickListener(v -> openCurrent());
		art.setElevation(dp(4));
		row.addView(art, new LinearLayout.LayoutParams(dp(compact ? 50 : 56), dp(compact ? 50 : 56)));

		// Ad + alt bilgi
		LinearLayout txt = new LinearLayout(ctx);
		txt.setOrientation(LinearLayout.VERTICAL);
		txt.setPadding(dp(12), 0, dp(6), 0);
		txt.setGravity(Gravity.CENTER_VERTICAL);
		txt.setOnClickListener(v -> openCurrent());
		title = new TextView(ctx);
		title.setTextColor(0xFFFFFFFF);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setTextSize(15 * YoldaStyle.textScale(ctx));
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(title);
		LinearLayout subRow = new LinearLayout(ctx);
		subRow.setOrientation(LinearLayout.HORIZONTAL);
		subRow.setGravity(Gravity.CENTER_VERTICAL);
		live = new TextView(ctx);
		live.setText("● CANLI");
		live.setTextColor(0xFFFFFFFF);
		live.setTextSize(10 * YoldaStyle.textScale(ctx));
		live.setTypeface(Typeface.DEFAULT_BOLD);
		live.setLetterSpacing(0.08f);
		live.setPadding(dp(6), dp(1), dp(6), dp(1));
		GradientDrawable lb = new GradientDrawable();
		lb.setColor(0xFFE11D48);
		lb.setCornerRadius(dp(4));
		live.setBackground(lb);
		live.setVisibility(GONE);
		live.setSingleLine(true);
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
		llp.rightMargin = dp(6);
		subRow.addView(live, llp);
		sub = new TextView(ctx);
		sub.setTextColor(0xB3FFFFFF);
		sub.setTextSize(13 * YoldaStyle.textScale(ctx));
		sub.setSingleLine(true);
		sub.setEllipsize(TextUtils.TruncateAt.END);
		subRow.addView(sub);
		txt.addView(subRow);
		row.addView(txt, new LinearLayout.LayoutParams(0, -1, 1f)); // tüm yükseklik dokunulabilir

		// Düğmeler (her biri en az 44dp dokunma alanı)
		if (!compact) row.addView(button(ctx, R.drawable.yi_prev, "Önceki", v -> cb().onSkipToPrevious()), btnLp(44));
		play = button(ctx, R.drawable.yi_pause, "Oynat / duraklat", v -> togglePlay());
		play.setColorFilter(0xFF0B0E2A);
		GradientDrawable pc = new GradientDrawable();
		pc.setShape(GradientDrawable.OVAL);
		pc.setColor(0xFFFFFFFF);
		play.setBackground(pc);
		play.setPadding(dp(12), dp(12), dp(12), dp(12));
		// bağlanırken / tamponlarken oynat düğmesinin çevresinde dönen halka
		FrameLayout pf = new FrameLayout(ctx);
		ring = new YoldaNowPlaying.BusyRing(ctx);
		pf.addView(ring, new LayoutParams(-1, -1));
		pf.addView(play, new LayoutParams(dp(46), dp(46), Gravity.CENTER));
		LinearLayout.LayoutParams plp = btnLp(56);
		plp.leftMargin = dp(2);
		plp.rightMargin = dp(2);
		row.addView(pf, plp);
		row.addView(button(ctx, R.drawable.yi_next, "Sonraki", v -> cb().onSkipToNext()), btnLp(44));
		if (!compact) row.addView(button(ctx, R.drawable.more, "Diğer", v -> clickOriginal(R.id.control_menu_button)),
				btnLp(40));
		row.addView(button(ctx, R.drawable.yi_close, "Kapat", v -> {
			if (getParent() instanceof ControlPanelView cp) cp.closePlayer();
		}), btnLp(40));

		// Üstte ilerleme çizgisi
		// İlerleme: çubuğun içinde, alt kenara oturan ince çizgi; sürüklenerek sarılır
		progress = new YoldaSeekLine(ctx);
		progress.setLine(0.7f, dp(26), dp(3));
		progress.setListener(new YoldaSeekLine.Listener() {
			@Override
			public void onDrag(float f) {
				dragging = true;
			}

			@Override
			public void onSeek(float f) {
				dragging = false;
				seekTo(f);
			}
		});
		addView(progress, new LayoutParams(-1, dp(16), Gravity.BOTTOM));
	}

	// ---- yaşam döngüsü ----

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		try { // durum değişince hemen güncelle (yalnız yarım saniyelik tura bağlı kalma)
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
		boolean fast = (MediaSessionCallback.yoldaOpeningSince() > 0) || dragging;
		postDelayed(ticker, (getVisibility() != VISIBLE) ? 1500 : fast ? 200 : 400);
	}

	private void hideOriginals() {
		if (!(getParent() instanceof android.view.ViewGroup p)) return;
		for (int i = 0; i < p.getChildCount(); i++) {
			View c = p.getChildAt(i);
			if (c == this) continue;
			if (c.getAlpha() != 0f) c.setAlpha(0f);
			if (c.getVisibility() == VISIBLE) c.setVisibility(INVISIBLE);
		}
	}

	private void update() {
		hideOriginals();
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		MediaSessionCallback cb = a.getMediaSessionCallback();
		boolean playing = cb.isPlaying();
		play.setImageResource(playing ? R.drawable.yi_pause : R.drawable.yi_play);
		boolean busy = YoldaNowPlaying.isBusy(cb);
		ring.setBusy(busy);

		// Dokunulan şarkı henüz aranıyor: hemen onun adını göster
		String pt = YoldaNowPlaying.pendingTitle(cb);
		if (pt != null) {
			String key = "pending:" + pt;
			if (!key.equals(itemId)) {
				itemId = key;
				title.setText(pt);
				cover.animate().alpha(0f).setDuration(120).start();
				String th = YoldaNowPlaying.pendingThumb(cb);
				if (th != null) {
					a.getLib().getBitmap(th, true, true).main().onCompletion((bmp, e2) -> {
						if ((bmp == null) || !key.equals(itemId)) return;
						showCover(bmp);
						cover.animate().alpha(1f).setDuration(220).start();
					});
				}
			}
			sub.setText("Bağlanıyor…");
			live.setVisibility(GONE);
			setProg(0f, false);
			return;
		}

		// YouTube yedeğiyle çalan Yolda Müzik şarkısı: çalan şarkının kendi adı ve kapağı
		YoldaNowPlaying.External ex = YoldaNowPlaying.external(cb);
		if (ex != null) {
			showExternal(a, ex);
			long d = Math.max(0, ex.track().durationSec()) * 1000;
			long p = YoldaNowPlaying.position(cb);
			live.setVisibility(GONE);
			sub.setText(busy ? "Bağlanıyor…" : subText);
			if (!dragging) setProg((d > 0) ? Math.min(1f, p / (float) d) : 0f, d > 0);
			return;
		}

		// Dokunulan kanal / şarkı: çalmaya başlamasını beklemeden adı ve resmi hemen (önceden eski ad kalıyordu)
		MediaLib.PlayableItem opening = MediaSessionCallback.yoldaOpeningItem();
		MediaLib.PlayableItem pi = (opening != null) ? opening : a.getCurrentPlayable();
		if (pi == null) return;
		String id = pi.getId();
		if (!id.equals(itemId)) {
			itemId = id;
			metaKey = YoldaNowPlaying.metaSig(cb.getYoldaMetadata()); // eski öğenin bilgisi üstüne yazmasın
			loadInfo(a, pi);
		}
		if ((opening == null) && isYoutube(cb)) applyMeta(a, cb.getYoldaMetadata());
		else if ((opening == null) && (SystemClock.elapsedRealtime() - descAt > 20_000)) refreshDesc(pi);
		if (YoldaNowPlaying.isError(cb)) {
			sub.setText("Açılamadı · ▶ ile tekrar dene");
			subOverride = true;
		} else if (busy) {
			sub.setText("Bağlanıyor…");
			subOverride = true;
		} else if (subOverride) {
			subOverride = false;
			sub.setText(subText);
		}

		PlaybackStateCompat st = cb.getPlaybackState();
		long pos = (st != null) ? st.getPosition() : 0;
		if ((st != null) && (st.getState() == PlaybackStateCompat.STATE_PLAYING)) {
			long dt = SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime();
			if (dt > 0) pos += (long) (dt * Math.max(0.1f, st.getPlaybackSpeed()));
		}
		long dur = durationOf(pi);
		boolean isLive = (dur <= 0) && pi.isStream();
		live.setVisibility(isLive ? VISIBLE : GONE);
		progress.setVisibility(isLive ? INVISIBLE : VISIBLE); // canlıda ilerleme yok: yerine CANLI nabzı
		if (isLive && (live.getAnimation() == null) && ((android.os.Build.VERSION.SDK_INT < 26) || android.animation.ValueAnimator.areAnimatorsEnabled())) {
			android.view.animation.AlphaAnimation an = new android.view.animation.AlphaAnimation(1f, 0.55f);
			an.setDuration(900);
			an.setRepeatMode(android.view.animation.Animation.REVERSE);
			an.setRepeatCount(android.view.animation.Animation.INFINITE);
			live.startAnimation(an);
		} else if (!isLive && (live.getAnimation() != null)) live.clearAnimation();
		if (!dragging) setProg((dur > 0) ? Math.min(1f, pos / (float) dur) : (isLive ? 1f : 0f), dur > 0);
	}

	private void setProg(float f, boolean seekable) {
		progress.setSeekable(seekable);
		progress.setProgress(f);
	}

	/** Süre: öğe bilgisinden; henüz yüklenmediyse (şarkıda) şarkının kendi süresi. Bilgi yüklemesi tetiklenir. */
	static long durationOf(MediaLib.PlayableItem pi) {
		long d = 0;
		try {
			var f = pi.getMediaData();
			MediaMetadataCompat md = f.peek();
			if (md != null) d = md.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
		} catch (Throwable ignore) {
		}
		if ((d <= 0) && (pi instanceof me.aap.fermata.media.yt.YtMusicItem m))
			d = Math.max(0, m.getTrack().durationSec()) * 1000;
		return d;
	}

	@Nullable
	private String metaKey;
	private long descAt;

	static boolean isYoutube(MediaSessionCallback cb) {
		var eng = cb.getEngine();
		return (eng != null) && eng.getClass().getName().contains("Youtube");
	}

	/** Ad ve alt bilgi öğenin kendi açıklamasından (TV'de güncel program). */
	private void refreshDesc(MediaLib.PlayableItem pi) {
		descAt = SystemClock.elapsedRealtime();
		String key = pi.getId();
		pi.getMediaDescription().main().onSuccess(md -> {
			if ((md == null) || !key.equals(itemId)) return;
			title.setText(pi.getName()); // açıklama başlığı sıra numarası içerebiliyor ("1. TRT 1")
			CharSequence s = md.getSubtitle();
			if (TextUtils.isEmpty(s)) {
				MediaLib.BrowsableItem p = pi.getParent();
				s = (p != null) ? p.getName() : "";
			}
			subText = s;
			if (!subOverride) sub.setText(s);
		});
	}

	/** YouTube yedeğindeki şarkı değiştiyse adı, sanatçıyı ve kapağı yenile. */
	private void showExternal(MainActivityDelegate a, YoldaNowPlaying.External ex) {
		me.aap.fermata.media.yt.YtMusic.Track tr = ex.track();
		String key = "ext:" + ex.index() + ":" + tr.id();
		if (key.equals(itemId)) return;
		itemId = key;
		title.setText(tr.title());
		subText = (tr.artist() != null) ? tr.artist() : "";
		sub.setText(subText);
		subOverride = false;
		cover.animate().alpha(0f).setDuration(120).start();
		if (tr.thumb() == null) return;
		a.getLib().getBitmap(tr.thumb(), true, true).main().onCompletion((bmp, e2) -> {
			if ((bmp == null) || !key.equals(itemId)) return;
			showCover(bmp);
			cover.animate().alpha(1f).setDuration(220).start();
		});
	}

	/**
	 * Oturumun güncel bilgisiyle adı, alt bilgiyi ve resmi yenile: çalınan öğe aynı kalsa da (YouTube'da
	 * sonraki video, TV'de yeni program, şarkının kapağı geç gelince) çubuk güncel kalır.
	 */
	private void applyMeta(MainActivityDelegate a, @Nullable MediaMetadataCompat md) {
		String sig = YoldaNowPlaying.metaSig(md);
		if ((sig == null) || sig.equals(metaKey)) return;
		metaKey = sig;
		String tt = YoldaNowPlaying.metaTitle(md);
		if (!TextUtils.isEmpty(tt)) title.setText(tt);
		String st = YoldaNowPlaying.metaSub(md);
		if (!TextUtils.isEmpty(st)) {
			subText = st;
			if (!subOverride) sub.setText(st);
		}
		String key = itemId;
		android.graphics.Bitmap bm = YoldaNowPlaying.metaBitmap(md);
		if (bm != null) {
			showCover(bm);
			cover.animate().alpha(1f).setDuration(220).start();
			return;
		}
		String u = YoldaNowPlaying.metaArtUri(md);
		if (u == null) return;
		a.getLib().getBitmap(u, true, true).main().onCompletion((bmp, e2) -> {
			if ((bmp == null) || !java.util.Objects.equals(key, itemId)) return;
			showCover(bmp);
			cover.animate().alpha(1f).setDuration(220).start();
		});
	}

	/** Alt satır geçici olarak "Bağlanıyor…" / hata gösteriyorsa asıl metin burada bekler. */
	private CharSequence subText = "";
	private boolean subOverride;

	/** Kapağı göster ve çubuğu kapağın rengine boya. */
	private void showCover(android.graphics.Bitmap bmp) {
		cover.setImageBitmap(bmp);
		cover.animate().alpha(1f).setDuration(220).start();
		try {
			int c = android.graphics.Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0);
			float[] hsv = new float[3];
			android.graphics.Color.colorToHSV(c, hsv);
			hsv[1] = Math.min(1f, hsv[1] * 1.3f + 0.1f);
			hsv[2] = Math.max(0.35f, Math.min(0.7f, hsv[2]));
			int v = android.graphics.Color.HSVToColor(hsv);
			tintBg.setColors(new int[]{(v & 0x00FFFFFF) | 0xCC000000, (v & 0x00FFFFFF) | 0x33000000});
		} catch (Throwable ignore) {
		}
	}

	private void loadInfo(MainActivityDelegate a, MediaLib.PlayableItem pi) {
		descAt = SystemClock.elapsedRealtime();
		tintBg.setColors(new int[]{0x00000000, 0x00000000});
		coverPh.setImageResource(pi.isStream() ? (pi.isVideo() ? R.drawable.yi_tv : R.drawable.yi_live) : R.drawable.yi_music);
		title.setText(pi.getName());
		subText = "";
		if (!subOverride) sub.setText("");
		cover.animate().alpha(0f).setDuration(120).start();
		pi.getMediaDescription().main().onSuccess(md -> {
			if (!pi.getId().equals(itemId) || (md == null)) return;
			title.setText(pi.getName()); // açıklama başlığı sıra numarası içerebiliyor ("1. TRT 1")
			CharSequence s = md.getSubtitle();
			if (TextUtils.isEmpty(s)) {
				MediaLib.BrowsableItem p = pi.getParent();
				s = (p != null) ? p.getName() : "";
			}
			subText = s;
			if (!subOverride) sub.setText(s);
		});
		pi.getIconUri().main().onCompletion((u, err) -> {
			if ((u == null) || !pi.getId().equals(itemId)) return;
			a.getLib().getBitmap(u.toString(), true, true).main().onCompletion((bmp, e2) -> {
				if ((bmp == null) || !pi.getId().equals(itemId)) return;
				showCover(bmp);
				cover.animate().alpha(1f).setDuration(220).start(); // kapak yumuşakça belirir
			});
		});
	}

	// ---- eylemler ----

	private MediaSessionCallback cb() {
		return MainActivityDelegate.get(getContext()).getMediaSessionCallback();
	}

	private void togglePlay() {
		YoldaNowPlaying.togglePlay(MainActivityDelegate.get(getContext()));
		post(ticker);
		play.animate().scaleX(0.86f).scaleY(0.86f).setDuration(80)
				.withEndAction(() -> play.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
	}

	/** Kapağa ya da ada dokununca: video ise tam ekran video, değilse "Şimdi çalıyor" ekranı (açıksa kapanır). */
	private void openCurrent() {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if (!YoldaNowPlaying.hide(a)) YoldaNowPlaying.open(a);
		} catch (Throwable ignore) {
		}
	}

	/** Fermata'nın gizli özgün düğmesine tıkla (⋯ menüsü: hız, altyazı, ses parçası…). */
	private void clickOriginal(int id) {
		if (getParent() instanceof View p) {
			View v = p.findViewById(id);
			if (v != null) v.performClick();
		}
	}

	private void seekTo(float frac) {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		MediaLib.PlayableItem pi = a.getCurrentPlayable();
		if (pi == null) return;
		long dur = durationOf(pi);
		if (dur > 0) a.getMediaSessionCallback().onSeekTo((long) (dur * frac));
	}

	private ImageView button(Context ctx, int icon, String desc, OnClickListener click) {
		ImageView b = new ImageView(ctx);
		b.setImageResource(icon);
		b.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		b.setScaleType(ImageView.ScaleType.FIT_CENTER);
		b.setPadding(dp(11), dp(11), dp(11), dp(11));
		b.setContentDescription(desc);
		b.setClickable(true);
		b.setFocusable(true);
		android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
		GradientDrawable pressed = new GradientDrawable();
		pressed.setShape(GradientDrawable.OVAL);
		pressed.setColor(0x33FFFFFF);
		sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
		sl.addState(new int[]{android.R.attr.state_focused}, pressed);
		b.setBackground(sl);
		b.setOnClickListener(click);
		return b;
	}

	private LinearLayout.LayoutParams btnLp(int sizeDp) {
		return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}

	/** İnce ilerleme çizgisi; dokunup sürükleyince ileri/geri sarar (canlı yayında kapalı). */
	private final class ProgressLine extends View {
		private final android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.Paint fg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private float frac;
		private boolean seekable;

		ProgressLine(Context ctx) {
			super(ctx);
			bg.setColor(0x40FFFFFF);
			fg.setColor(0xFFFFFFFF);
		}

		void set(float f, boolean canSeek) {
			seekable = canSeek;
			if (Math.abs(f - frac) > 0.0005f) {
				frac = f;
				invalidate();
			}
		}

		@Override
		protected void onDraw(@NonNull android.graphics.Canvas c) {
			float h = dragging ? dp(7) : dp(5);
			float y = getHeight() / 2f;
			float l = dp(22), r = getWidth() - dp(22);
			if (r <= l) return;
			c.drawRoundRect(l, y - h / 2, r, y + h / 2, h, h, bg);
			float x = l + (r - l) * frac;
			c.drawRoundRect(l, y - h / 2, x, y + h / 2, h, h, fg);
			if (seekable) c.drawCircle(x, y, dragging ? dp(11) : dp(7), fg); // tutamak: sürüklenebildiği belli olsun
		}

		@Override
		public boolean onTouchEvent(MotionEvent e) {
			if (!seekable) return false;
			float l = dp(22), r = getWidth() - dp(22);
			float f = Math.max(0f, Math.min(1f, (e.getX() - l) / Math.max(1f, r - l)));
			switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
					dragging = true;
					getParent().requestDisallowInterceptTouchEvent(true);
					frac = f;
					invalidate();
					return true;
				}
				case MotionEvent.ACTION_UP -> {
					dragging = false;
					frac = f;
					invalidate();
					seekTo(f);
					return true;
				}
				case MotionEvent.ACTION_CANCEL -> {
					dragging = false;
					invalidate();
					return true;
				}
			}
			return false;
		}
	}
}
