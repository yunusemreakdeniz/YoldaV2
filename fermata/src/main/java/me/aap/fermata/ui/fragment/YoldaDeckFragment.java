package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.radio.RadioBrowser;
import me.aap.fermata.media.radio.YoldaStreamItem;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaGlow;
import me.aap.fermata.ui.view.YoldaNowPlaying;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * Yolda V2 ana ekranı: yığılmış kart destesi. Ortadaki kart büyük ve öndedir, yanındakiler küçülüp
 * arkasına dizilir; kaydırınca kartlar yerine oturur. Karta dokununca içine girilir, sol alttaki geri
 * düğmesiyle desteye dönülür. Kartlar: YouTube, Müzik, TV, Radyo, Telefon, Tarayıcı, Ayarlar, Ekle ve
 * kullanıcının eklediği TV / radyo / YouTube kanalı ve web sayfası kartları. Düzenle kipinde kartlar
 * sıralanır ve kaldırılır.
 */
public class YoldaDeckFragment extends MainActivityFragment {
	static final String PREF_DECK = "deck_v2";
	static final String PREF_POS = "deck_pos";
	private static final String RADIO_GROUP = "Radyo";

	private YoldaStyle.Theme t;
	private YoldaDeck deck;
	private FrameLayout root;
	private RecyclerView list;
	private LinearLayout dots;
	private ImageView editBtn;
	private LinearLayout topRow;
	@Nullable
	private TextView nowPill;
	private boolean pillIconOnly;
	private TextClock clockView;
	/** Alçak ekran (yatay telefon): üst satır ve noktalar sıkışık, kartlara daha çok yer kalır. */
	private boolean shortScreen;

	/**
	 * Kartın içi kart yüksekliğine göre ölçülür: başlık, alt yazı, kutucuklar ve "Aç" düğmesi her zaman kartın içine
	 * sığar (yatay telefonda kart alçakken alt kısım kesiliyordu). Tüm ölçüler dp, yazılar sp.
	 */
	private record Fit(boolean small, boolean compact, int pad, int chip, int title, int subLines, int tile, int logo,
										 int btn, int chipH, int big, boolean row) {
	}

	/**
	 * Ölçüm payı (dp): kart çizildikten sonra içerik yine de taşarsa (yazı tipi, ekran yoğunluğu, tahmin hatası)
	 * taşan kadar artar ve kartlar daha sıkı düzenle yeniden kurulur. Kart boyu değişince sıfırlanır.
	 */
	private int fitSlackDp;

	/** Kartın içeriği kartın içine sığıyor mu: sığmıyorsa payı artırıp desteyi yeniden kur. */
	private void watchOverflow(LinearLayout v) {
		int base = fitSlackDp; // bu kart kurulurkenki pay: birden çok kart aynı anda taşarsa paylar toplanmasın
		v.addOnLayoutChangeListener((x, l, tp, r, b, ol, ot, or, ob) -> {
			if ((v.getChildCount() == 0) || (v.getHeight() <= 0)) return;
			View last = v.getChildAt(v.getChildCount() - 1);
			int over = last.getBottom() - (v.getHeight() - v.getPaddingBottom());
			// satır sığsa da içindeki öğe satırdan uzun olabilir (ör. "Aç" düğmesi): o da taşmadır
			for (int i = 0; i < v.getChildCount(); i++) {
				if (!(v.getChildAt(i) instanceof ViewGroup g) || (g.getVisibility() != View.VISIBLE)) continue;
				for (int k = 0; k < g.getChildCount(); k++) {
					View cc = g.getChildAt(k);
					if (cc.getVisibility() != View.VISIBLE) continue;
					over = Math.max(over, Math.max(cc.getBottom() - g.getHeight(), -cc.getTop()));
				}
			}
			if (over <= dp(1)) return;
			int add = Math.round(over / getResources().getDisplayMetrics().density) + 2;
			int want = base + add;
			if ((want <= fitSlackDp) || (want > 240)) return;
			float dn = getResources().getDisplayMetrics().density;
			YoldaLog.add("Deste: kart içeriği " + add + " dp taştı (kart " + Math.round(cardH / dn) + " dp, iç "
					+ Math.round(v.getHeight() / dn) + " dp, pay " + base + "→" + want + ")");
			fitSlackDp = want;
			if (list != null) list.post(() -> {
				if (adapter != null) adapter.notifyDataSetChanged();
			});
		});
	}

	private Fit fit() {
		android.content.res.Resources r = getResources();
		float dens = r.getDisplayMetrics().density;
		float fs = Math.max(1f, r.getConfiguration().fontScale);
		int h = Math.round(cardH / dens) - fitSlackDp;
		boolean small = h < 330;
		boolean compact = h < 260;
		int pad = compact ? 14 : small ? 18 : 24;
		int chip = compact ? 40 : small ? 46 : 54;
		int title = compact ? 22 : small ? 26 : 32;
		int head = Math.max(chip, Math.round((12 * 1.35f + title * 1.22f) * fs) + (noble() ? 9 : 0));
		int avail = h - 2 * pad - head;
		int line = Math.round(15 * 1.33f * fs);
		int subLines = (avail - 10 - 2 * line >= 84) ? 2 : (avail - 10 - line >= 52) ? 1 : 0;
		int bottom = avail - ((subLines == 0) ? 0 : (10 + subLines * line)) - 2;
		int tile = Math.max(40, Math.min(small ? 70 : 84, bottom));
		int logo = Math.max(44, Math.min(small ? 84 : 104, bottom));
		int btn = Math.max(40, Math.min(52, bottom));
		int chipH = Math.max(40, Math.min(48, bottom));
		int big = compact ? 32 : small ? 40 : 52;
		// Çok alçak kart (yatay telefon, çubuk açık): tek satır — simge, ad, "Aç" yan yana; içerik şeridi yok
		if (h < 150) {
			int rp = (h < 90) ? 8 : 12;
			int inner = Math.max(36, h - 2 * rp);
			return new Fit(true, true, rp, Math.min(44, inner), (h < 90) ? 18 : 22, 0, 0, 0, Math.min(48, inner),
					Math.min(44, inner), 28, true);
		}
		return new Fit(small, compact, pad, chip, title, subLines, tile, logo, btn, chipH, big, false);
	}
	private DeckAdapter adapter;
	private PagerSnapHelper snap;
	private boolean editing;
	/** Araç ekranı: gölge ve sürekli animasyon yok. */
	private boolean lowFx;
	private int cardW;
	private int cardH;
	private final List<MediaLib.PlayableItem> tvChannels = new ArrayList<>();
	private final List<MediaLib.PlayableItem> radioChannels = new ArrayList<>();
	@Nullable
	private View overlay;
	@Nullable
	private MediaLib.PlayableItem lastItem;
	private final List<MediaLib.PlayableItem> favItems = new ArrayList<>();
	@Nullable
	private YoldaWeather.Now weather;


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
		return YoldaFullScreen.instance;
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance;
	}

	@Override
	public boolean isRootPage() {
		return true;
	}

	@Override
	public boolean onBackPressed() {
		if (overlay != null) {
			closeOverlay();
			return true;
		}
		if (editing) {
			setEditing(false);
			return true;
		}
		return super.onBackPressed();
	}

	/** Testler için: destedeki kartlar. */
	public YoldaDeck getDeck() {
		return deck;
	}

	/** Testler için: üstteki "çalan" düğmesi görünüyor mu. */
	public boolean nowPlayingShown() {
		updateNowPill();
		return (nowPill != null) && (nowPill.getVisibility() == View.VISIBLE);
	}

	/** Testler için: kartı ortaya getirir. */
	public void scrollToCard(int pos) {
		if ((list == null) || (pos < 0) || (pos >= deck.size())) return;
		((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(pos, 0);
		list.post(() -> {
			transform();
			updateDots();
		});
	}

	/** Testler için: kartı açar (dokunmakla aynı). */
	public void openCard(String key) {
		int i = deck.indexOf(key);
		if (i >= 0) open(deck.get(i));
	}

	// ---- kurulum ----

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		deck = YoldaDeck.decode(prefs(ctx).getString(PREF_DECK, null));
		// Sonradan gelen sabit kartlar (Navigasyon) önceki kurulumların destesine bir kez eklenir
		for (String nt : new String[]{YoldaDeck.RESUME, YoldaDeck.FAVS, YoldaDeck.WEATHER, YoldaDeck.PHARMACY,
				YoldaDeck.RADAR}) {
			if (prefs(ctx).getBoolean("deck_has_" + nt, false)) continue;
			deck.restore(nt);
			prefs(ctx).edit().putBoolean("deck_has_" + nt, true).putString(PREF_DECK, deck.encode()).apply();
		}
		if (!prefs(ctx).getBoolean("deck_has_mirror", false)) {
			deck.restore(YoldaDeck.MIRROR);
			prefs(ctx).edit().putBoolean("deck_has_mirror", true).putString(PREF_DECK, deck.encode()).apply();
		}
		if (!prefs(ctx).getBoolean("deck_has_nav", false)) {
			deck.restore(YoldaDeck.NAV);
			prefs(ctx).edit().putBoolean("deck_has_nav", true).putString(PREF_DECK, deck.encode()).apply();
		}
		boolean car = getActivityDelegate().isCarActivity();

		root = new FrameLayout(ctx);
		YoldaStyle.applyScreenBackground(root, t);
		// Ortadaki kartın renginde ışıltılar: uygulamanın en dış zemininde (tüm ekran, kenarlar dahil)
		YoldaGlow g = rootGlow();
		if (g != null) g.setDrift(!car); // araçta ışıklar sabit (sürekli çizim araca giden görüntüyü ağırlaştırıyor)
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Üst satır: saat ve tarih, sağda düzenle
		LinearLayout top = new LinearLayout(ctx);
		topRow = top;
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(22), YoldaStyle.cutoutTop(ctx) + dp(car ? 8 : 12), dp(18), 0);
		TextClock clock = new TextClock(ctx);
		clockView = clock;
		clock.setFormat24Hour("HH:mm");
		clock.setFormat12Hour("HH:mm");
		clock.setTextSize(car ? 24 : 28);
		clock.setTypeface(Typeface.DEFAULT_BOLD);
		clock.setTextColor(t.ink);
		TextView date = YoldaStyle.turkishDate(ctx, "EEEE, d MMMM");
		date.setTextColor(t.muted);
		date.setSingleLine(true);
		date.setEllipsize(TextUtils.TruncateAt.END);
		// Dar telefon (dikey): tarih ve hava saatin altında ikinci satırda; üst satırda düğmelere yer kalsın
		narrowHeader = !car && (YoldaStyle.widthDp(ctx) < 600);
		if (narrowHeader) {
			LinearLayout clockCol = new LinearLayout(ctx);
			clockCol.setOrientation(LinearLayout.VERTICAL);
			clockCol.addView(clock);
			dateLine = new LinearLayout(ctx);
			dateLine.setOrientation(LinearLayout.HORIZONTAL);
			dateLine.setGravity(Gravity.CENTER_VERTICAL);
			date.setTextSize(12);
			dateLine.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
			clockCol.addView(dateLine, new LinearLayout.LayoutParams(-1, -2));
			top.addView(clockCol, new LinearLayout.LayoutParams(0, -2, 1f));
			// Dokunma alanı: saat + tarih + hava bloğunun tamamı (küçük hava yazısı tek başına zor basılır)
			clockCol.setOnClickListener(v -> {
				if (weather == null) return;
				YoldaStyle.haptic(v);
				HomeFragment.openWeb(getActivityDelegate(), "https://www.google.com/search?q=hava+durumu");
			});
		} else {
			top.addView(clock);
			date.setTextSize(14);
			date.setPadding(dp(12), dp(4), 0, 0);
			top.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
		}
		// Çalan: destede oynatıcı çubuğu yok; çalan şeyin adı burada, dokununca Şimdi çalıyor / video
		// Hava: saatin yanında küçük rozet (☀ 18°); dokununca hava durumu
		wxChip = text(ctx, "", 15, true, t.ink);
		wxChip.setSingleLine(true);
		wxChip.setGravity(Gravity.CENTER);
		wxChip.setPadding(dp(12), 0, dp(12), 0);
		wxChip.setBackground(YoldaStyle.glass(ctx, t, 999));
		wxChip.setVisibility(View.GONE);
		wxChip.setOnClickListener(v -> {
			YoldaStyle.haptic(v);
			HomeFragment.openWeb(getActivityDelegate(), "https://www.google.com/search?q=hava+durumu");
		});
		if (narrowHeader && (dateLine != null)) { // tarihin yanında sade yazı (dokunma: tüm saat bloğu)
			wxChip.setOnClickListener(null);
			wxChip.setClickable(false);
			wxChip.setBackground(null);
			wxChip.setTextSize(12);
			wxChip.setTextColor(t.muted);
			wxChip.setPadding(dp(6), 0, 0, 0);
			dateLine.addView(wxChip, 0, new LinearLayout.LayoutParams(-2, -2));
		} else {
			LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, dp(44));
			wlp.rightMargin = dp(10);
			top.addView(wxChip, wlp);
		}
		updateWeatherChip();
		TextView playing = text(ctx, "", 15, true, 0xFFFFFFFF);
		playing.setSingleLine(true);
		playing.setEllipsize(TextUtils.TruncateAt.END);
		playing.setGravity(Gravity.CENTER_VERTICAL);
		playing.setPadding(dp(16), 0, dp(18), 0);
		pillIconOnly = !car && (YoldaStyle.widthDp(ctx) < 600); // dar ekranda yalnız simge (üst satır taşmasın)
		playing.setMaxWidth(dp(car ? 340 : 260));
		if (pillIconOnly) {
			playing.setPadding(0, 0, 0, 0);
			playing.setGravity(Gravity.CENTER);
			playing.setTextSize(18);
		}
		playing.setBackground(YoldaStyle.glass(ctx, t, 999));
		playing.setVisibility(View.GONE);
		playing.setOnClickListener(v -> {
			YoldaStyle.haptic(v);
			YoldaNowPlaying.open(getActivityDelegate());
		});
		LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(pillIconOnly ? dp(44) : -2, dp(44));
		plp.rightMargin = dp(10);
		top.addView(playing, plp);
		nowPill = playing;
		Runnable tick = new Runnable() {
			@Override
			public void run() {
				if (!isAdded() || (nowPill == null)) return;
				updateNowPill();
				nowPill.postDelayed(this, 1500);
			}
		};
		playing.post(tick);
		// Tema: dokun → sıradaki tema; basılı tut → otomatik (gündüz / gece) aç / kapat
		ImageView themeBtn = themeBtnView = roundButton(ctx, R.drawable.yi_palette, "Tema: " + YoldaStyle.label(ctx), v -> {
			YoldaStyle.haptic(v);
			YoldaStyle.next(ctx); // tüm ekranlar dinleyiciyle yenilenir
			YoldaStyle.toast(ctx, "Tema: " + YoldaStyle.label(ctx));
		});
		themeBtn.setOnLongClickListener(v -> {
			YoldaStyle.haptic(v);
			boolean on = !YoldaStyle.isAuto(ctx);
			YoldaStyle.setAuto(ctx, on);
			YoldaStyle.toast(ctx, on ? "Otomatik tema açık: gündüz ve gece kendiliğinden değişir"
					: "Otomatik tema kapalı");
			return true;
		});
		LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(44), dp(44));
		tlp.rightMargin = dp(10);
		top.addView(themeBtn, tlp);
		// Ayarlar: tema düğmesinin yanında (menü / ayarlar ekranı)
		ImageView setBtn = setBtnView = roundButton(ctx, R.drawable.yi_settings, "Ayarlar", v -> {
			YoldaStyle.haptic(v);
			getActivityDelegate().showFragment(R.id.yolda_menu_fragment);
		});
		LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(dp(44), dp(44));
		slp2.rightMargin = dp(10);
		top.addView(setBtn, slp2);
		restoreBtn = roundButton(ctx, R.drawable.yi_history, "Kaldırılan kartlar", v -> {
			YoldaStyle.haptic(v);
			showRestoreMenu();
		});
		LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(dp(44), dp(44));
		rlp2.rightMargin = dp(10);
		top.addView(restoreBtn, rlp2);
		restoreBtn.setVisibility(View.GONE);
		editBtn = roundButton(ctx, R.drawable.yi_edit, "Düzenle", v -> setEditing(!editing));
		top.addView(editBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
		col.addView(top);

		// Deste
		list = new RecyclerView(ctx);
		list.setClipToPadding(false);
		list.setClipChildren(false);
		list.setOverScrollMode(View.OVER_SCROLL_NEVER);
		list.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
		list.setHasFixedSize(true);
		list.setItemViewCacheSize(16); // kaydırırken kartlar yeniden kurulmasın
		lowFx = car;
		adapter = new DeckAdapter();
		list.setAdapter(adapter);
		snap = new PagerSnapHelper();
		snap.attachToRecyclerView(list);
		list.setChildDrawingOrderCallback(this::drawingOrder);
		list.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
				transform();
			}

			@Override
			public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
				if (state == RecyclerView.SCROLL_STATE_IDLE) {
					int p = centerPosition();
					if (p >= 0) prefs(rv.getContext()).edit().putInt(PREF_POS, p).apply();
					updateDots();
				}
			}
		});
		list.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> {
			if ((r - l != or - ol) || (b - tp != ob - ot)) v.post(this::resize);
			else v.post(this::transform); // her yerleşimden sonra ortadaki kart tam boy (eski küçültme kalmasın)
		});
		list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
			@Override
			public void onChildViewAttachedToWindow(@NonNull View view) {
				list.post(YoldaDeckFragment.this::transform);
			}

			@Override
			public void onChildViewDetachedFromWindow(@NonNull View view) {
			}
		});
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1f);
		llp.topMargin = dp(car ? 4 : 10);
		col.addView(list, llp);

		dots = new LinearLayout(ctx);
		dots.setOrientation(LinearLayout.HORIZONTAL);
		dots.setGravity(Gravity.CENTER);
		dots.setPadding(0, dp(6), 0, dp(car ? 8 : 14));
		dots.setMinimumHeight(dp(36)); // parmakla kolay basılsın
		dots.setContentDescription("Kartlar arasında hızlı geçiş: basılı tutup kaydır");
		dots.setOnTouchListener((v, e) -> onJumpTouch(e));
		col.addView(dots, new LinearLayout.LayoutParams(-1, -2));

		loadChannels();
		loadExtras(car);
		YoldaVoice.preload(getActivityDelegate()); // sesli komutta kanal adları hazır olsun ("TRT Haber aç")
		YoldaRadar.addListener(radarListener);
		return root;
	}

	private SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	private void save() {
		if (getContext() != null) prefs(requireContext()).edit().putString(PREF_DECK, deck.encode()).apply();
	}

	/** Kart boyu destenin alanına göre: yatayda geniş, dikeyde ekranın çoğu; yanlarda komşular görünür. */
	private void resize() {
		if ((list == null) || !isAdded() || (getContext() == null)) return; // geç gelen çağrı: sayfa kapanmışsa çökmesin
		int w = list.getWidth();
		int h = list.getHeight();
		if ((w <= 0) || (h <= 0)) return;
		boolean car = getActivityDelegate().isCarActivity();
		compactChrome(car);
		// Kart boyu oynatıcı çubuğundan bağımsız: çubuğun yeri her zaman ayrılır; çubuk açılıp kapanınca kartlar
		// küçülüp büyümez (önceden bir şey çalınca kartlar küçülüyordu)
		h = stableHeight(h, car);
		if (h <= 0) return;
		boolean wide = w > h * 1.2f;
		// Araçta kartlar büyük: yüksekliğin neredeyse tamamı, genişliğin üçte ikisi
		// Yatayda kartlar geniş (yanlarda komşu kartların ucu görünür kalır)
		int nw = car ? Math.min(Math.round(w * 0.76f), dp(1100))
				: Math.min(Math.round(w * (wide ? 0.74f : 0.8f)), dp(wide ? 820 : 620));
		int nh = car ? (h - dp(8)) : Math.min(h - dp(12), wide ? dp(460) : dp(620));
		if (car && (nw != cardW)) {
			View rv = root;
			me.aap.fermata.ui.fragment.YoldaLog.add("Araç destesi: liste " + Math.round(w / getResources().getDisplayMetrics().density)
					+ "×" + Math.round(h / getResources().getDisplayMetrics().density) + " dp, kök "
					+ ((rv != null) ? Math.round(rv.getHeight() / getResources().getDisplayMetrics().density) : 0)
					+ " dp, üst pay " + YoldaStyle.cutoutTop(requireContext()) + " px, yoğunluk "
					+ getResources().getDisplayMetrics().density);
		}
		boolean changed = (nw != cardW) || (nh != cardH);
		if (changed) fitSlackDp = 0;
		cardW = nw;
		cardH = nh;
		int pad = (w - cardW) / 2;
		list.setPadding(pad, 0, pad, 0);
		if (changed) {
			adapter.notifyDataSetChanged();
			int pos = Math.max(0, Math.min(deck.size() - 1, prefs(requireContext()).getInt(PREF_POS, 0)));
			((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(pos, 0);
			list.post(() -> {
				transform();
				updateDots();
			});
		}
	}

	/** Üstteki "♪ çalan" düğmesi: çalan / açılan bir şey varsa adı, yoksa gizli. */
	private void updateNowPill() {
		TextView p = nowPill;
		if (p == null) return;
		MainActivityDelegate a = getActivityDelegate();
		var cb = a.getMediaSessionCallback();
		MediaLib.PlayableItem pi = me.aap.fermata.media.service.MediaSessionCallback.yoldaOpeningItem();
		if (pi == null) pi = a.getCurrentPlayable();
		boolean on = (pi != null) && (cb.isPlaying() || (me.aap.fermata.media.service.MediaSessionCallback.yoldaOpeningItem() != null));
		String name = null;
		if (on) {
			// YouTube'da sayfanın başlığı (şarkı / video adı), TV ve radyoda kanalın adı
			String mt = me.aap.fermata.ui.view.YoldaPlayerBar.isYoutube(cb) ? YoldaNowPlaying.titleOf(cb) : null;
			name = ((mt != null) && !mt.isEmpty()) ? mt : pi.getName();
		}
		String txt = on ? (pillIconOnly ? (pi.isVideo() ? "▶" : "♪") : ((pi.isVideo() ? "▶  " : "♪  ") + name)) : "";
		if (!txt.contentEquals(p.getText())) {
			p.setText(txt);
			p.setContentDescription(on ? ("Çalıyor: " + name) : null);
		}
		int v = on ? View.VISIBLE : View.GONE;
		if (p.getVisibility() != v) p.setVisibility(v);
	}

	/** Yatay telefonda (alçak ekran) saat satırı ve sayfa noktaları daha az yer kaplar; kartlar büyür. */
	private void compactChrome(boolean car) {
		if ((root == null) || (topRow == null)) return;
		int hdp = Math.round(root.getHeight() / getResources().getDisplayMetrics().density);
		boolean sh = !car && (hdp > 0) && (hdp < 520);
		if (sh == shortScreen) return;
		shortScreen = sh;
		Context ctx = requireContext();
		topRow.setPadding(dp(22), YoldaStyle.cutoutTop(ctx) + dp(sh ? 2 : 12), dp(18), 0);
		if (clockView != null) clockView.setTextSize(sh ? 22 : 28);
		if (dots != null) dots.setPadding(0, dp(sh ? 2 : 6), 0, dp(sh ? 4 : 14));
		if (list != null) {
			ViewGroup.LayoutParams lp = list.getLayoutParams();
			if (lp instanceof LinearLayout.LayoutParams llp) {
				llp.topMargin = dp(sh ? 2 : 10);
				list.setLayoutParams(llp);
			}
		}
	}

	/** Eczane kartının alt yazısı: seçili il / ilçe. */
	private String radarSubtitle() {
		Context ctx = getContext();
		if (ctx == null) return "";
		if (!YoldaRadar.isEnabled(ctx)) return "Kapalı · radar, hız sınırı, ortalama hız, okul bölgesi ve mola uyarıları";
		if (!YoldaRadar.hasPermission(ctx)) return "Açık · konum izni gerekli";
		YoldaRadarCore.Ahead a = YoldaRadar.ahead;
		if (a != null) return "Önde " + a.cam().label().toLowerCase(new java.util.Locale("tr")) + ": "
				+ RadarFragment.meters(a.meters()) + ((YoldaRadar.limit > 0) ? (" · sınır " + YoldaRadar.limit) : "");
		if (!YoldaRadar.hasFix()) return "Açık · konum bekleniyor";
		int n = Double.isNaN(YoldaRadar.lat) ? 0
				: YoldaRadarCore.nearby(YoldaRadar.lat, YoldaRadar.lon, YoldaRadar.cams(), 10_000, 99).size();
		return "Açık · 10 km içinde " + n + " radar" + ((YoldaRadar.limit > 0) ? (" · sınır " + YoldaRadar.limit) : "");
	}

	private String lastRadarSub;
	private long lastRadarRefresh;
	/** Radar durumu değişince kart yazısı (en çok 3 sn'de bir; yazı aynıysa hiç) yenilenir. */
	private final Runnable radarListener = () -> {
		if (!isAdded() || isHidden()) return;
		String s = radarSubtitle();
		long now = android.os.SystemClock.elapsedRealtime();
		if (s.equals(lastRadarSub) || (now - lastRadarRefresh < 3000)) return;
		lastRadarSub = s;
		lastRadarRefresh = now;
		refreshCard(YoldaDeck.RADAR);
	};

	/** Kartın Açık / Kapalı düğmesi. */
	private TextView radarToggle(Context ctx) {
		boolean on = YoldaRadar.isEnabled(ctx);
		TextView sw = chipBtn(ctx, on ? "Açık  ⬤" : "◯  Kapalı", v -> toggleRadar(ctx));
		sw.setContentDescription("Radar uyarıları: " + (on ? "açık, kapatmak için dokun" : "kapalı, açmak için dokun"));
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(999));
		g.setColor(on ? 0x59FFFFFF : 0x1AFFFFFF);
		g.setStroke(Math.max(1, dp(1)), on ? 0xB3FFFFFF : 0x40FFFFFF);
		sw.setBackground(g);
		sw.setTextColor(on ? 0xFFFFFFFF : 0xB3FFFFFF);
		return sw;
	}

	private boolean radarUpdating;

	/** Kartın "↻ Güncelle" düğmesi: radar listesini hemen OpenStreetMap'ten yeniler. */
	private TextView radarUpdateBtn(Context ctx, boolean compact) {
		TextView b = chipBtn(ctx, radarUpdating ? "↻ Güncelleniyor…" : (compact ? "↻" : "↻ Güncelle"), v -> {
			if (radarUpdating) return;
			radarUpdating = true;
			refreshCard(YoldaDeck.RADAR);
			YoldaStyle.toast(ctx, "Radar listesi güncelleniyor…");
			YoldaRadar.update(ctx, r -> {
				radarUpdating = false;
				YoldaStyle.toast(ctx, r);
				lastRadarSub = null;
				refreshCard(YoldaDeck.RADAR);
			});
		});
		long up = YoldaRadar.updatedAt(ctx);
		b.setContentDescription("Radar listesini güncelle" + ((up > 0) ? (", son güncelleme "
				+ new java.text.SimpleDateFormat("d MMMM", new java.util.Locale("tr")).format(new java.util.Date(up))) : ""));
		if (compact && !radarUpdating) b.setPadding(dp(14), 0, dp(14), 0);
		return b;
	}

	/** Karttan aç / kapat (izin yoksa önce sorulur). */
	private void toggleRadar(Context ctx) {
		boolean on = !YoldaRadar.isEnabled(ctx);
		if (on && !YoldaRadar.hasPermission(ctx)) {
			getActivityDelegate().getAppActivity().checkPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,
					android.Manifest.permission.ACCESS_FINE_LOCATION).onCompletion((r, err) -> YoldaRadar.permissionChanged(ctx));
		}
		YoldaRadar.setEnabled(ctx, on);
		YoldaStyle.toast(ctx, on ? "Radar uyarıları açıldı" : "Radar uyarıları kapatıldı");
		lastRadarSub = null;
		refreshCard(YoldaDeck.RADAR);
	}

	private String pharmacySubtitle() {
		Context ctx = getContext();
		if (ctx == null) return "";
		YoldaPharmacy.Place il = YoldaPharmacy.selectedIl(ctx);
		YoldaPharmacy.Place ilce = YoldaPharmacy.selectedIlce(ctx);
		if (il == null) return "Bugünün nöbetçi eczaneleri, en yakını önce";
		return il.name() + ((ilce != null) ? (" · " + ilce.name()) : " · tüm ilçeler") + " · bugünün nöbetçileri";
	}

	/** Son görülen oynatıcı çubuğu yüksekliği (çubuk gizliyken de bu kadar yer ayrılır). */
	private int barReserve = -1;

	private int stableHeight(int h, boolean car) {
		if (YoldaStyle.DECK) return h; // destede çubuk hiç gösterilmez: kartlar tüm yüksekliği kullanır
		View cp = getActivityDelegate().getControlPanel();
		boolean shown = (cp != null) && (cp.getVisibility() == View.VISIBLE);
		if (shown && (cp.getHeight() > 0)) barReserve = cp.getHeight();
		if (barReserve < 0) barReserve = dp(me.aap.fermata.ui.view.YoldaPlayerBar.heightDp(requireContext()) + 12);
		return shown ? h : (h - barReserve);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (list != null) list.post(this::resize);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && (list != null)) list.post(() -> {
			resize();
			transform();
		});
	}

	// ---- yığılmış görünüm ----

	/** Ortadaki kart tam boy ve önde; uzaklaştıkça küçülür, solar, merkeze doğru çekilip arkasına yığılır. */
	private void transform() {
		if ((list == null) || !isAdded() || (getContext() == null)) return;
		float center = list.getWidth() / 2f;
		for (int i = 0; i < list.getChildCount(); i++) {
			View c = list.getChildAt(i);
			float cc = (c.getLeft() + c.getRight()) / 2f;
			float d = (cc - center) / Math.max(1f, cardW);
			float ad = Math.min(Math.abs(d), 2f);
			float s = 1f - 0.14f * Math.min(ad, 1.5f);
			c.setScaleX(s);
			c.setScaleY(s);
			c.setTranslationX(-d * cardW * 0.30f); // komşular merkeze çekilip ortadakinin arkasında kalır
			c.setAlpha(1f - 0.45f * Math.min(ad, 1f));
			c.setRotationY(-Math.max(-1f, Math.min(1f, d)) * 6f);
			c.setCameraDistance(cardW * 12f);
			if (!lowFx) c.setTranslationZ(dp(12) * (1f - Math.min(ad, 1f)));
		}
		list.invalidate();
		updateGlow();
	}

	/** Görünen kartların renklerini ortaya yakınlıklarına göre karıştırıp arka plan ışıklarına verir. */
	@Nullable
	private YoldaGlow rootGlow() {
		try {
			return YoldaGlow.of(getActivityDelegate().findViewById(R.id.main_activity));
		} catch (Throwable ex) {
			return null;
		}
	}

	private void updateGlow() {
		YoldaGlow glow = rootGlow();
		if ((glow == null) || (list == null) || (cardW <= 0)) return;
		float center = list.getWidth() / 2f;
		float[] a = new float[4], b = new float[4];
		float sum = 0;
		for (int i = 0; i < list.getChildCount(); i++) {
			View c = list.getChildAt(i);
			int pos = list.getChildAdapterPosition(c);
			if ((pos < 0) || (pos >= deck.size())) continue;
			float d = Math.abs((c.getLeft() + c.getRight()) / 2f - center) / cardW;
			float w = Math.max(0f, 1f - d);
			if (w <= 0) continue;
			Look lk = look(deck.get(pos));
			if (noble()) { // asil: zemin yalnız hafifçe kartın tonunu alır
				int ac = YoldaStyle.nobleAccent(lk.c1);
				add(a, YoldaStyle.calm(ac, 0.6f, 0.30f), w);
				add(b, YoldaStyle.calm(ac, 0.4f, 0.20f), w);
			} else {
				add(a, lk.c1, w);
				add(b, lk.c2, w);
			}
			sum += w;
		}
		if (sum <= 0) return;
		glow.setColors(mix(a, sum), mix(b, sum));
	}

	private static void add(float[] acc, int c, float w) {
		acc[0] += ((c >> 16) & 0xFF) * w;
		acc[1] += ((c >> 8) & 0xFF) * w;
		acc[2] += (c & 0xFF) * w;
	}

	private static int mix(float[] acc, float sum) {
		return 0xFF000000 | (Math.round(acc[0] / sum) << 16) | (Math.round(acc[1] / sum) << 8) | Math.round(acc[2] / sum);
	}

	/**
	 * Arka plan ışıltıları: kartın iki renginde büyük, yumuşak ışık lekeleri; yavaşça süzülür (animasyonlar
	 * kapalıysa sabit). Renk değişince yumuşakça geçer.
	 */
	private static final class Glow extends View {
		private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private int c1 = 0xFF3B82F6, c2 = 0xFF7C3AED;
		private int t1 = c1, t2 = c2;
		private final long start = android.os.SystemClock.uptimeMillis();
		boolean drift = true;

		Glow(Context ctx) {
			super(ctx);
		}

		void setColors(int a, int b) {
			if ((a == t1) && (b == t2)) return;
			t1 = a;
			t2 = b;
			invalidate();
		}

		private static int towards(int from, int to, float k) {
			int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
			int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
			int bl = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
			return 0xFF000000 | (r << 16) | (g << 8) | bl;
		}

		private boolean motionOn() {
			if (android.os.Build.VERSION.SDK_INT < 26) return true;
			return android.animation.ValueAnimator.areAnimatorsEnabled();
		}

		@Override
		protected void onDraw(@NonNull android.graphics.Canvas c) {
			float w = getWidth(), h = getHeight();
			if ((w <= 0) || (h <= 0)) return;
			boolean anim = drift && motionOn();
			// renk geçişi: her karede hedefe biraz yaklaş (animasyon kapalıysa hemen)
			boolean fade = motionOn(); // renk geçişi araçta da yumuşak (yalnız geçiş süresince çizilir)
			c1 = fade ? towards(c1, t1, 0.12f) : t1;
			c2 = fade ? towards(c2, t2, 0.12f) : t2;
			float s = anim ? (android.os.SystemClock.uptimeMillis() - start) / 1000f : 0f;
			float r = Math.max(w, h) * 0.62f;
			blob(c, c1, w * (0.25f + 0.08f * (float) Math.sin(s * 0.21)), h * (0.30f + 0.10f * (float) Math.cos(s * 0.17)), r, 0x88);
			blob(c, c2, w * (0.78f + 0.07f * (float) Math.cos(s * 0.19)), h * (0.72f + 0.09f * (float) Math.sin(s * 0.23)), r, 0x77);
			blob(c, towards(c1, 0xFFFFFFFF, 0.25f), w * (0.55f + 0.12f * (float) Math.sin(s * 0.13)), h * 0.10f, r * 0.55f, 0x33);
			boolean settling = fade && ((c1 != t1) || (c2 != t2));
			if (anim || settling) postInvalidateOnAnimation();
		}

		private void blob(android.graphics.Canvas c, int color, float x, float y, float r, int alpha) {
			p.setShader(new android.graphics.RadialGradient(x, y, r, new int[]{(color & 0x00FFFFFF) | (alpha << 24),
					(color & 0x00FFFFFF) | ((alpha / 3) << 24), color & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f},
					android.graphics.Shader.TileMode.CLAMP));
			c.drawCircle(x, y, r, p);
		}
	}

	/** Ortaya en yakın kart en son (en üstte) çizilir. */
	private int drawingOrder(int count, int i) {
		float center = list.getWidth() / 2f;
		Integer[] idx = new Integer[count];
		for (int k = 0; k < count; k++) idx[k] = k;
		java.util.Arrays.sort(idx, (a, b) -> {
			View va = list.getChildAt(a), vb = list.getChildAt(b);
			float da = Math.abs((va.getLeft() + va.getRight()) / 2f - center);
			float db = Math.abs((vb.getLeft() + vb.getRight()) / 2f - center);
			return Float.compare(db, da);
		});
		return idx[i];
	}

	private int centerPosition() {
		View v = snap.findSnapView(list.getLayoutManager());
		return (v != null) ? list.getChildAdapterPosition(v) : -1;
	}

	private void updateDots() {
		if ((dots == null) || !isAdded() || (getContext() == null)) return;
		Context ctx = requireContext();
		dots.removeAllViews();
		int cur = centerPosition();
		for (int i = 0; i < deck.size(); i++) {
			View d = new View(ctx);
			GradientDrawable g = new GradientDrawable();
			g.setCornerRadius(dp(4));
			g.setColor((i == cur) ? 0xFFFFFFFF : 0x55FFFFFF);
			d.setBackground(g);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp((i == cur) ? 22 : 7), dp(7));
			lp.leftMargin = lp.rightMargin = dp(3);
			dots.addView(d, lp);
		}
	}

	// ---- hızlı geçiş: sayfa noktalarına basınca mini kart şeridi ----

	@Nullable
	private LinearLayout jumpStrip;
	private int jumpSel = -1;
	private final Runnable hideJump = this::hideJumpStrip;

	/** Noktalara / şeride dokunuş: basınca şerit açılır, parmak gezdikçe deste o karta geçer, bırakınca kalır. */
	private boolean onJumpTouch(MotionEvent e) {
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN -> {
				if (editing) return false;
				showJumpStrip();
				jumpTo(e.getRawX(), false);
			}
			case MotionEvent.ACTION_MOVE -> jumpTo(e.getRawX(), false);
			case MotionEvent.ACTION_UP -> {
				jumpTo(e.getRawX(), true);
				if (root != null) {
					root.removeCallbacks(hideJump);
					root.postDelayed(hideJump, 1400);
				}
			}
			case MotionEvent.ACTION_CANCEL -> {
				if (root != null) root.postDelayed(hideJump, 600);
			}
			default -> {
			}
		}
		return true;
	}

	/** Şeridi kurar (her açılışta güncel deste ve tema) ve gösterir. */
	private void showJumpStrip() {
		if ((root == null) || (getContext() == null)) return;
		Context ctx = requireContext();
		root.removeCallbacks(hideJump);
		if (jumpStrip != null) root.removeView(jumpStrip);
		LinearLayout s = new LinearLayout(ctx);
		s.setOrientation(LinearLayout.HORIZONTAL);
		s.setGravity(Gravity.CENTER_VERTICAL);
		s.setPadding(dp(8), dp(6), dp(8), dp(6));
		s.setBackground(YoldaStyle.glass(ctx, t, 16));
		s.setElevation(dp(24));
		s.setOnTouchListener((v, e) -> onJumpTouch(e)); // şerit üzerinde de kaydırılır / dokunulur
		int n = Math.max(1, deck.size());
		int gap = dp(4);
		int avail = root.getWidth() - dp(40) - n * gap;
		int w = Math.max(dp(20), Math.min(dp(48), avail / n));
		int h = dp(36);
		boolean noble = noble();
		for (int i = 0; i < deck.size(); i++) {
			YoldaDeck.Card c = deck.get(i);
			Look lk = look(c);
			FrameLayout m = new FrameLayout(ctx);
			GradientDrawable g = noble
					? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF22252F, 0xFF16181F})
					: new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{lk.c1(), lk.c2()});
			g.setCornerRadius(dp(9));
			if (noble) g.setStroke(Math.max(1, dp(1)), YoldaStyle.alpha(YoldaStyle.nobleAccent(lk.c1()), 0.6f));
			m.setBackground(g);
			m.setContentDescription(c.title());
			ImageView ic = new ImageView(ctx);
			ic.setImageResource(lk.icon());
			if (lk.tint()) ic.setImageTintList(ColorStateList.valueOf(noble ? YoldaStyle.nobleAccent(lk.c1()) : 0xFFFFFFFF));
			int is = Math.min(dp(18), w - dp(6));
			m.addView(ic, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
			if (i > 0) lp.leftMargin = gap;
			s.addView(m, lp);
		}
		FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		flp.bottomMargin = ((dots != null) ? dots.getHeight() : dp(30)) + dp(2);
		root.addView(s, flp);
		jumpStrip = s;
		jumpSel = -1;
		s.setAlpha(0f);
		s.setTranslationY(dp(10));
		s.animate().alpha(1f).translationY(0).setDuration(140).start();
		highlightJump(centerPosition());
	}

	/** Parmağın altındaki mini kart (yoksa en yakını); değiştiyse deste hemen o karta geçer. */
	private void jumpTo(float rawX, boolean done) {
		LinearLayout s = jumpStrip;
		if ((s == null) || (s.getChildCount() == 0) || !isAdded()) return;
		int best = 0;
		float bd = Float.MAX_VALUE;
		int[] l = new int[2];
		for (int i = 0; i < s.getChildCount(); i++) {
			View c = s.getChildAt(i);
			c.getLocationOnScreen(l);
			float cx = l[0] + c.getWidth() / 2f;
			float d = Math.abs(rawX - cx);
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		if (best != jumpSel) {
			highlightJump(best);
			YoldaStyle.haptic(s);
			scrollToCard(best);
			prefs(requireContext()).edit().putInt(PREF_POS, best).apply();
		} else if (done) {
			scrollToCard(best);
		}
	}

	private void highlightJump(int sel) {
		LinearLayout s = jumpStrip;
		if (s == null) return;
		jumpSel = sel;
		for (int i = 0; i < s.getChildCount(); i++) {
			View c = s.getChildAt(i);
			boolean on = (i == sel);
			c.animate().scaleX(on ? 1.22f : 1f).scaleY(on ? 1.22f : 1f).alpha(on ? 1f : 0.7f).setDuration(90).start();
			c.setElevation(on ? dp(6) : 0);
		}
	}

	private void hideJumpStrip() {
		LinearLayout s = jumpStrip;
		jumpStrip = null;
		if (s == null) return;
		// Gecikmeli çağrı: bu arada sayfa kapanmış olabilir (önceden burada çöküyordu)
		android.view.ViewParent par = s.getParent();
		if (!isAdded() || (getContext() == null) || (root == null)) {
			if (par instanceof ViewGroup g) g.removeView(s);
			return;
		}
		float d = s.getResources().getDisplayMetrics().density;
		s.animate().alpha(0f).translationY(10 * d).setDuration(160).withEndAction(() -> {
			if (s.getParent() instanceof ViewGroup g) g.removeView(s);
		}).start();
	}

	@Override
	public void onDestroyView() {
		if (root != null) root.removeCallbacks(hideJump); // sayfa kapanırken bekleyen şerit kapatma çalışmasın
		YoldaRadar.removeListener(radarListener);
		jumpStrip = null;
		nowPill = null;
		super.onDestroyView();
	}

	/** Testler için: ortadaki kartın sırası. */
	public int currentCard() {
		return centerPosition();
	}

	/** Testler için: hızlı geçiş şeridi açık mı ve kaç mini kart var. */
	public int jumpStripSize() {
		return (jumpStrip != null) ? jumpStrip.getChildCount() : 0;
	}

	@androidx.annotation.Nullable
	private ImageView restoreBtn;
	@androidx.annotation.Nullable
	private TextView wxChip;

	private void updateWeatherChip() {
		TextView c = wxChip;
		if (c == null) return;
		if (weather == null) {
			c.setVisibility(View.GONE);
			return;
		}
		c.setText(YoldaWeather.emoji(weather.code()) + " " + weather.temp() + "°");
		c.setContentDescription("Hava: " + weather.temp() + " derece, " + YoldaWeather.text(weather.code()) + ". Ayrıntı için dokun");
		c.setVisibility(View.VISIBLE);
	}

	/** Testler için: destedeki kartın görünümü (bağlıysa). */
	@androidx.annotation.Nullable
	public View cardView(int pos) {
		RecyclerView.ViewHolder h = (list != null) ? list.findViewHolderForAdapterPosition(pos) : null;
		return (h != null) ? h.itemView : null;
	}

	public boolean isEditing() {
		return editing;
	}

	/** Testler için. */
	public String weatherChipText() {
		return ((wxChip != null) && (wxChip.getVisibility() == View.VISIBLE)) ? wxChip.getText().toString() : null;
	}

	private List<YoldaDeck.Card> removedCards() {
		return YoldaDeck.decodeCards(prefs(requireContext()).getString("deck_removed", ""));
	}

	private void saveRemoved(List<YoldaDeck.Card> l) {
		prefs(requireContext()).edit().putString("deck_removed", YoldaDeck.encodeCards(l)).apply();
	}

	private void stashRemoved(YoldaDeck.Card c) {
		List<YoldaDeck.Card> l = removedCards();
		l.removeIf(x -> x.type().equals(c.type()) && x.data().equals(c.data()));
		l.add(0, c);
		while (l.size() > 30) l.remove(l.size() - 1);
		saveRemoved(l);
	}

	/** Kaldırılan sabit ve kullanıcı kartı sayısı. */
	public int removedCount() {
		return deck.missingBuiltIns().size() + removedCards().size();
	}

	private boolean narrowHeader;
	@androidx.annotation.Nullable
	private LinearLayout dateLine;
	@androidx.annotation.Nullable
	private ImageView themeBtnView, setBtnView;

	private void updateRestoreBtn() {
		boolean show = editing && (removedCount() > 0);
		if (restoreBtn != null) restoreBtn.setVisibility(show ? View.VISIBLE : View.GONE);
		// Dar ekranda düzenlerken tema / ayarlar yerine ↺ (üst satır taşmasın)
		int tv = (narrowHeader && editing) ? View.GONE : View.VISIBLE;
		if (themeBtnView != null) themeBtnView.setVisibility(tv);
		if (setBtnView != null) setBtnView.setVisibility(tv);
	}

	/** Kaldırılan kartlar: dokununca destedeki eski yerine (sabit kart) ya da "Ekle"nin önüne geri gelir. */
	private void showRestoreMenu() {
		List<Row> rows = new ArrayList<>();
		addRestoreRows(rows);
		if (rows.isEmpty()) {
			YoldaStyle.toast(requireContext(), "Kaldırılmış kart yok");
			return;
		}
		showPicker("Kaldırılan kartlar", rows, null);
	}

	private void addRestoreRows(List<Row> rows) {
		for (String type : deck.missingBuiltIns()) {
			rows.add(new Row(YoldaDeck.titleOf(type) + " kartı", "Kaldırılmış kart · dokun, geri gelsin", null, R.drawable.yi_history,
					() -> {
						afterChange(deck.restore(type));
						YoldaStyle.toast(requireContext(), "Geri geldi: " + YoldaDeck.titleOf(type));
						updateRestoreBtn();
					}));
		}
		for (YoldaDeck.Card c : removedCards()) {
			rows.add(new Row(c.title(), "Kaldırdığın kart · dokun, geri gelsin", c.logo(), R.drawable.yi_history, () -> {
				int at = deck.add(c.type(), c.title(), c.data(), c.logo());
				List<YoldaDeck.Card> l = removedCards();
				l.removeIf(x -> x.type().equals(c.type()) && x.data().equals(c.data()));
				saveRemoved(l);
				afterChange(at);
				YoldaStyle.toast(requireContext(), "Geri geldi: " + c.title());
				updateRestoreBtn();
			}));
		}
	}

	private void setEditing(boolean on) {
		editing = on;
		updateRestoreBtn();
		editBtn.setImageResource(on ? R.drawable.yi_check : R.drawable.yi_edit);
		editBtn.setContentDescription(on ? "Bitti" : "Düzenle");
		adapter.notifyDataSetChanged();
		list.post(this::transform);
		if (on) YoldaStyle.toast(requireContext(), "Düzenle: ◀ ▶ sırala, 🎨 renk, ✕ kaldır, ↺ kaldırılanlar");
	}

	// ---- kart görünümü ----

	private final class Holder extends RecyclerView.ViewHolder {
		final FrameLayout box;

		Holder(FrameLayout box) {
			super(box);
			this.box = box;
		}
	}

	private final class DeckAdapter extends RecyclerView.Adapter<Holder> {
		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			FrameLayout box = new FrameLayout(parent.getContext());
			box.setClipChildren(false);
			box.setLayoutParams(new RecyclerView.LayoutParams(Math.max(1, cardW), -1));
			return new Holder(box);
		}

		@Override
		public void onBindViewHolder(@NonNull Holder h, int pos) {
			ViewGroup.LayoutParams lp = h.box.getLayoutParams();
			lp.width = Math.max(1, cardW);
			h.box.setLayoutParams(lp);
			h.box.removeAllViews();
			if (cardW <= 0) return;
			// Yatay telefonda kart üstte: oynatıcı çubuğu için ayrılan yer kartın altında kalır (üstte boşluk olmaz)
			boolean top = (getContext() != null) && !getActivityDelegate().isCarActivity()
					&& (list != null) && (list.getWidth() > list.getHeight() * 1.2f);
			h.box.addView(card(h.box.getContext(), deck.get(pos), pos),
					new FrameLayout.LayoutParams(-1, cardH, top ? Gravity.TOP : Gravity.CENTER_VERTICAL));
		}

		@Override
		public int getItemCount() {
			return deck.size();
		}
	}

	/** Kartın renkleri, simgesi, etiketi ve açıklaması türüne göre. */
	private record Look(int c1, int c2, @DrawableRes int icon, boolean tint, String tag, String sub) {
	}

	/** Kartın görünümü: kullanıcı renk seçtiyse o renkler, yoksa türünün renkleri. */
	private Look look(YoldaDeck.Card c) {
		Look lk = baseLook(c);
		int[] cc = customColors(c.key());
		if (cc != null) lk = new Look(cc[0], cc[1], lk.icon(), lk.tint(), lk.tag(), lk.sub());
		// Yumuşak tema: kartın renkleri yarı doygun ve biraz koyu
		if ((t != null) && (t.cards == YoldaStyle.CARDS_SOFT)) {
			lk = new Look(YoldaStyle.calm(lk.c1, 0.5f, 0.62f), YoldaStyle.calm(lk.c2, 0.5f, 0.55f), lk.icon(), lk.tint(),
					lk.tag(), lk.sub());
		}
		return lk;
	}

	private boolean noble() {
		return (t != null) && (t.cards == YoldaStyle.CARDS_NOBLE);
	}

	// ---- kart renkleri (kullanıcı seçer) ----

	/** Hazır renk geçişleri: ad, üst renk, alt renk. */
	private static final Object[][] PALETTE = {
			{"Kırmızı", 0xFFFF4D4D, 0xFF9F0019}, {"Mercan", 0xFFFF7A59, 0xFFC2410C}, {"Turuncu", 0xFFFBBF24, 0xFFC2410C},
			{"Altın", 0xFFFDE68A, 0xFFB45309}, {"Yeşil", 0xFF34D399, 0xFF047857}, {"Nane", 0xFF5EEAD4, 0xFF0F766E},
			{"Turkuaz", 0xFF22D3EE, 0xFF0E7490}, {"Mavi", 0xFF3B82F6, 0xFF1E3A8A}, {"Gece", 0xFF6366F1, 0xFF1E1B4B},
			{"Mor", 0xFFA78BFA, 0xFF5B21B6}, {"Pembe", 0xFFFF5F9E, 0xFF6D28D9}, {"Gül", 0xFFF472B6, 0xFF9D174D},
			{"Gri", 0xFF94A3B8, 0xFF334155}, {"Grafit", 0xFF64748B, 0xFF0F172A}, {"Gün batımı", 0xFFFB923C, 0xFF7C3AED},
			{"Okyanus", 0xFF38BDF8, 0xFF4338CA}};

	@Nullable
	private int[] customColors(String key) {
		if (getContext() == null) return null;
		String s = prefs(requireContext()).getString("card_color_" + key, null);
		if (s == null) return null;
		try {
			String[] p = s.split(",");
			return new int[]{(int) Long.parseLong(p[0], 16), (int) Long.parseLong(p[1], 16)};
		} catch (Exception ex) {
			return null;
		}
	}

	private void setCustomColors(String key, @Nullable int[] c) {
		SharedPreferences.Editor e = prefs(requireContext()).edit();
		if (c == null) e.remove("card_color_" + key);
		else e.putString("card_color_" + key, Integer.toHexString(c[0]) + "," + Integer.toHexString(c[1]));
		e.apply();
	}

	/** Renk seçici: hazır geçişler ve "Varsayılan". */
	private void pickColor(YoldaDeck.Card c, int pos) {
		Context ctx = requireContext();
		FrameLayout panel = openOverlay(ctx, "Kart rengi: " + c.title());
		LinearLayout box = (LinearLayout) panel.getChildAt(0);
		ScrollView sv = new ScrollView(ctx);
		LinearLayout grid = new LinearLayout(ctx);
		grid.setOrientation(LinearLayout.VERTICAL);
		grid.setPadding(0, dp(8), 0, dp(8));
		sv.addView(grid);
		box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
		int cols = Math.max(3, Math.min(6, (Math.min(root.getWidth() - dp(32), dp(560)) - dp(36)) / dp(84)));
		LinearLayout row = null;
		int[] cur = customColors(c.key());
		for (int i = 0; i < PALETTE.length; i++) {
			if (i % cols == 0) {
				row = hrow(ctx);
				row.setPadding(0, 0, 0, dp(14));
				grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
			}
			Object[] p = PALETTE[i];
			int a = (int) p[1], b = (int) p[2];
			LinearLayout cell = new LinearLayout(ctx);
			cell.setOrientation(LinearLayout.VERTICAL);
			cell.setGravity(Gravity.CENTER_HORIZONTAL);
			View sw = new View(ctx);
			GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
			g.setShape(GradientDrawable.OVAL);
			boolean on = (cur != null) && (cur[0] == a) && (cur[1] == b);
			g.setStroke(dp(on ? 4 : 1), on ? 0xFFFFFFFF : 0x66FFFFFF);
			sw.setBackground(g);
			sw.setContentDescription((String) p[0]);
			sw.setClickable(true);
			sw.setOnClickListener(v -> {
				setCustomColors(c.key(), new int[]{a, b});
				closeOverlay();
				afterChange(pos);
			});
			pressEffect(sw);
			cell.addView(sw, new LinearLayout.LayoutParams(dp(60), dp(60)));
			TextView n = text(ctx, (String) p[0], 12, false, t.muted);
			n.setPadding(0, dp(4), 0, 0);
			cell.addView(n);
			row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
		}
		if (row != null) for (int i = PALETTE.length % cols; (i > 0) && (i < cols); i++)
			row.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
		LinearLayout btns = hrow(ctx);
		btns.setGravity(Gravity.END);
		btns.addView(chipBtn(ctx, "Varsayılan renk", v -> {
			setCustomColors(c.key(), null);
			closeOverlay();
			afterChange(pos);
		}), chipLp());
		box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
	}

	private Look baseLook(YoldaDeck.Card c) {
		return switch (c.type()) {
			case YoldaDeck.YOUTUBE -> new Look(0xFFFF4D4D, 0xFF9F0019, R.drawable.yi_brand_youtube, false, "VİDEO",
					"Videolar, canlı yayınlar ve kanallar — reklamsız");
			case YoldaDeck.MUSIC -> new Look(0xFFFF5F9E, 0xFF6D28D9, R.drawable.yi_music, true, "MÜZİK",
					"YouTube Music şarkıları, reklamsız");
			case YoldaDeck.TV -> new Look(0xFF3B82F6, 0xFF1E3A8A, R.drawable.yi_tv, true, "CANLI YAYIN",
					tvChannels.isEmpty() ? "Ulusal, haber, spor kanalları" : tvChannels.size() + " kanal · HD");
			case YoldaDeck.RADIO -> new Look(0xFFFBBF24, 0xFFC2410C, R.drawable.yi_live, true, "RADYO",
					"Türkiye'nin radyoları ve dünyadan istasyonlar");
			case YoldaDeck.RESUME -> new Look(0xFFFB923C, 0xFFBE123C, R.drawable.yi_history, true, "DEVAM ET",
					resumeText());
			case YoldaDeck.FAVS -> new Look(0xFFFACC15, 0xFFB45309, R.drawable.yi_star, true, "FAVORİLER",
					favSubtitle());
			case YoldaDeck.WEATHER -> new Look(0xFF38BDF8, 0xFF1E40AF, R.drawable.yi_globe, true, "HAVA DURUMU",
					(weather == null) ? "Hava durumu alınıyor…"
							: weather.place() + " · " + YoldaWeather.text(weather.code()) + " · rüzgâr " + weather.wind() + " km/s");
			case YoldaDeck.PHARMACY -> new Look(0xFFF87171, 0xFFB91C1C, R.drawable.yi_pharmacy, true, "NÖBETÇİ ECZANE",
					pharmacySubtitle());
			case YoldaDeck.RADAR -> new Look(0xFFFB923C, 0xFFC2410C, R.drawable.yi_radar, true, "RADAR VE HIZ",
					radarSubtitle());
			case YoldaDeck.NAV -> new Look(0xFF2DD4BF, 0xFF0F766E, R.drawable.yi_nav, true, "NAVİGASYON",
					"Yol tarifi, ev, iş ve son gidilen yerler");
			case YoldaDeck.PHONE -> new Look(0xFF34D399, 0xFF047857, R.drawable.yi_phone, true, "TELEFON",
					"Telefondaki uygulamaları araçta kullan");
			case YoldaDeck.MIRROR -> new Look(0xFF818CF8, 0xFF3730A3, R.drawable.yi_tv, true, "EKRAN YANSITMA",
					"Telefonun ekranını araca olduğu gibi yansıt");
			case YoldaDeck.WEB -> new Look(0xFF60A5FA, 0xFF1D4ED8, R.drawable.yi_globe, true, "TARAYICI",
					"İnternette gez, reklamsız");
			case YoldaDeck.SETTINGS -> new Look(0xFF94A3B8, 0xFF334155, R.drawable.yi_settings, true, "AYARLAR",
					"Tema, Hey Yolda, reklam engelleyici ve dahası");
			case YoldaDeck.ADD -> new Look(0xFFA78BFA, 0xFF5B21B6, R.drawable.yi_add, true, "EKLE",
					"TV, radyo, YouTube kanalı ya da web sayfası kartı ekle" + ((removedCount() > 0)
							? (" · kaldırılan " + removedCount() + " kart geri getirilebilir") : ""));
			case YoldaDeck.TV_CHANNEL -> new Look(0xFF2563EB, 0xFF172554, R.drawable.yi_tv, true, "TV KANALI",
					"Dokun, hemen açılsın");
			case YoldaDeck.RADIO_CHANNEL, YoldaDeck.RADIO_STREAM -> new Look(0xFFF59E0B, 0xFF9A3412, R.drawable.yi_live,
					true, "RADYO", "Dokun, hemen çalsın");
			case YoldaDeck.YT_CHANNEL -> new Look(0xFFEF4444, 0xFF7F1D1D, R.drawable.yi_brand_youtube, false,
					"YOUTUBE", "Videoları göster");
			default -> new Look(0xFF38BDF8, 0xFF0C4A6E, R.drawable.yi_globe, true, "WEB", c.data());
		};
	}

	@SuppressLint("ClickableViewAccessibility")
	private View card(Context ctx, YoldaDeck.Card c, int pos) {
		Look lk = look(c);
		boolean noble = noble();
		// Asil: kart koyu cam, renk yalnız vurguda (simge, ince çizgi, kenar, etiket, Aç düğmesi)
		int accent = noble ? YoldaStyle.nobleAccent(lk.c1) : 0xFFFFFFFF;
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable bg = noble
				? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1B1E28, 0xFF13151C})
				: new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{lk.c1, lk.c2});
		bg.setCornerRadius(dp(30));
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				noble ? new int[]{0x0FFFFFFF, 0x00FFFFFF, 0x00FFFFFF} : new int[]{0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(dp(30));
		GradientDrawable rim = new GradientDrawable();
		rim.setCornerRadius(dp(30));
		rim.setStroke(Math.max(1, dp(1)), noble ? YoldaStyle.alpha(accent, 0.30f) : 0x66FFFFFF);
		f.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, sheen, rim}));
		f.setClipToOutline(true);
		f.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(30));
			}
		});
		f.setElevation(lowFx ? 0 : dp(10));
		f.setClickable(true);
		f.setContentDescription(c.title());
		f.setOnClickListener(v -> {
			if (editing) return;
			if (centerPosition() != pos) { // yandaki karta dokununca önce ortaya gelir
				list.smoothScrollToPosition(pos);
				return;
			}
			open(c);
		});
		f.setOnLongClickListener(v -> {
			setEditing(true);
			return true;
		});

		// Arkada büyük, soluk simge
		ImageView mark = new ImageView(ctx);
		mark.setImageResource(lk.icon);
		if (lk.tint) mark.setImageTintList(ColorStateList.valueOf(accent));
		mark.setAlpha(noble ? 0.06f : 0.13f);
		int ms = Math.round(cardH * 0.62f);
		FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ms, ms, Gravity.END | Gravity.BOTTOM);
		mlp.rightMargin = -ms / 6;
		mlp.bottomMargin = -ms / 8;
		f.addView(mark, mlp);

		Fit fz = fit();
		LinearLayout v = new LinearLayout(ctx);
		v.setOrientation(LinearLayout.VERTICAL);
		int p = dp(fz.pad());
		v.setPadding(p, p, p, p);
		if (fz.row()) v.setPadding(dp(18), p, dp(18), p);
		watchOverflow(v);
		f.addView(v, new FrameLayout.LayoutParams(-1, -1));

		// Üst satır: simge, etiket + ad, "Aç"
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		FrameLayout chip = new FrameLayout(ctx);
		GradientDrawable cb = new GradientDrawable();
		cb.setCornerRadius(dp(16));
		cb.setColor(noble ? YoldaStyle.alpha(accent, 0.16f) : (lk.tint ? 0x33FFFFFF : 0xF2FFFFFF));
		chip.setBackground(cb);
		ImageView ic = new ImageView(ctx);
		ic.setImageResource(lk.icon);
		if (lk.tint) ic.setImageTintList(ColorStateList.valueOf(accent));
		int icn = Math.round(fz.chip() * 0.56f);
		chip.addView(ic, new FrameLayout.LayoutParams(dp(icn), dp(icn), Gravity.CENTER));
		head.addView(chip, new LinearLayout.LayoutParams(dp(fz.chip()), dp(fz.chip())));
		LinearLayout names = new LinearLayout(ctx);
		names.setOrientation(LinearLayout.VERTICAL);
		names.setPadding(dp(14), 0, dp(8), 0);
		LinearLayout tagRow = new LinearLayout(ctx);
		tagRow.setOrientation(LinearLayout.HORIZONTAL);
		tagRow.setGravity(Gravity.CENTER_VERTICAL);
		if (YoldaDeck.TV.equals(c.type()) || YoldaDeck.TV_CHANNEL.equals(c.type())) {
			View dot = new View(ctx);
			GradientDrawable dd = new GradientDrawable();
			dd.setShape(GradientDrawable.OVAL);
			dd.setColor(0xFFFF4D4D);
			dot.setBackground(dd);
			LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
			dlp.rightMargin = dp(6);
			tagRow.addView(dot, dlp);
		}
		TextView tag = text(ctx, lk.tag, 12, true, noble ? accent : 0xE6FFFFFF);
		tag.setLetterSpacing(0.14f);
		tagRow.addView(tag);
		names.addView(tagRow);
		TextView title = text(ctx, c.title(), fz.title(), true, 0xFFFFFFFF);
		title.setMaxLines(1);
		title.setEllipsize(TextUtils.TruncateAt.END);
		androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(title, 16, fz.title(), 1,
				android.util.TypedValue.COMPLEX_UNIT_SP);
		names.addView(title, new LinearLayout.LayoutParams(-1, -2));
		if (noble) { // başlığın altında ince vurgu çizgisi
			View line = new View(ctx);
			GradientDrawable ld = new GradientDrawable();
			ld.setCornerRadius(dp(2));
			ld.setColor(accent);
			line.setBackground(ld);
			LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(40), dp(3));
			llp.topMargin = dp(6);
			names.addView(line, llp);
		}
		head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
		v.addView(head);

		TextView sub = text(ctx, lk.sub, 15, false, noble ? 0xFFA3A8B5 : 0xD9FFFFFF);
		sub.setMaxLines(Math.max(1, fz.subLines()));
		sub.setEllipsize(TextUtils.TruncateAt.END);
		sub.setPadding(0, dp(10), 0, 0);
		if (fz.subLines() == 0) sub.setVisibility(View.GONE); // çok alçak kartta alt yazı yerine içerik
		v.addView(sub);
		if (fz.row()) { // tek satır: düğme(ler) başlığın sağında, dikeyde ortada
			v.setGravity(Gravity.CENTER_VERTICAL);
			if (editing) {
				head.addView(editBar(ctx, c, pos), new LinearLayout.LayoutParams(-2, -2));
				return f;
			}
			// Radar: alçak kartta da açıp kapatılabilsin (içerik satırı yok)
			if (YoldaDeck.RADAR.equals(c.type())) { // alçak kart: başlığa yer kalsın, küçük anahtar
				boolean on = YoldaRadar.isEnabled(ctx);
				View sw = YoldaStyle.toggleSwitch(ctx, on, 0xFFFFFFFF);
				sw.setContentDescription("Radar uyarıları: " + (on ? "açık, kapatmak için dokun" : "kapalı, açmak için dokun"));
				sw.setClickable(true);
				sw.setOnClickListener(x -> toggleRadar(ctx));
				TextView up = radarUpdateBtn(ctx, true);
				up.setPadding(0, 0, 0, 0);
				LinearLayout.LayoutParams upl = new LinearLayout.LayoutParams(dp(44), dp(44));
				upl.leftMargin = dp(8);
				head.addView(up, upl);
				LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(dp(56), dp(32));
				swp.leftMargin = dp(10);
				head.addView(sw, swp);
			}
			head.addView(openButton(ctx, c, noble, accent, fz), openLp(fz));
			return f;
		}
		v.addView(new View(ctx), new LinearLayout.LayoutParams(-1, 0, 1f));

		if (editing) {
			v.addView(editBar(ctx, c, pos), new LinearLayout.LayoutParams(-1, -2));
			return f;
		}
		LinearLayout bottom = hrow(ctx);
		bottom.setGravity(Gravity.BOTTOM);
		View content = content(ctx, c);
		if (content != null) bottom.addView(content, new LinearLayout.LayoutParams(0, -2, 1f));
		else bottom.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
		bottom.addView(openButton(ctx, c, noble, accent, fz), openLp(fz));
		v.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
		return f;
	}

	private LinearLayout.LayoutParams openLp(Fit fz) {
		LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, dp(fz.btn()));
		olp.leftMargin = dp(10);
		return olp;
	}

	/** Kartın "▶ Aç" düğmesi. */
	private TextView openButton(Context ctx, YoldaDeck.Card c, boolean noble, int accent, Fit fz) {
		TextView openBtn = text(ctx, "▶  Aç", (fz.btn() < 48) ? 15 : 16, true, noble ? 0xFFF2F3F7 : 0xFF111827);
		openBtn.setSingleLine(true);
		openBtn.setGravity(Gravity.CENTER);
		openBtn.setPadding(dp(18), 0, dp(20), 0);
		GradientDrawable ob = new GradientDrawable();
		ob.setCornerRadius(dp(999));
		if (noble) {
			ob.setColor(YoldaStyle.alpha(accent, 0.16f));
			ob.setStroke(Math.max(1, dp(1)), YoldaStyle.alpha(accent, 0.7f));
		} else {
			ob.setColor(0xFFFFF4D6);
		}
		openBtn.setBackground(ob);
		openBtn.setElevation(dp(4));
		openBtn.setContentDescription("Aç: " + c.title());
		openBtn.setOnClickListener(x -> open(c));
		return openBtn;
	}

	/** Kartın alt kısmı: kanal logoları, son şarkılar, hızlı ayarlar, ekleme seçenekleri… */
	@Nullable
	private View content(Context ctx, YoldaDeck.Card c) {
		MainActivityDelegate a = getActivityDelegate();
		Fit fz = fit();
		int tile = dp(fz.tile());
		switch (c.type()) {
			case YoldaDeck.TV -> { // kutucuğun altında kanal adı (logosu olmayan kanal da tanınsın)
				return channelRow(ctx, a, pickChannels(tvChannels), labeledTile(tile));
			}
			case YoldaDeck.RADIO -> {
				return channelRow(ctx, a, pickChannels(radioChannels), labeledTile(tile));
			}
			case YoldaDeck.MUSIC -> {
				List<YtMusic.Track> rec = MusicFragment.recent(ctx);
				if (rec.isEmpty()) return null;
				LinearLayout row = hrow(ctx);
				for (int i = 0; i < Math.min(8, rec.size()); i++) {
					YtMusic.Track tr = rec.get(i);
					int idx = i;
					View cover = imageTile(ctx, tr.thumb(), null, tile, false, tr.title(),
							v -> MusicFragment.playTracks(a, rec, idx));
					row.addView(cover, tileLp(tile));
				}
				return scroll(ctx, row);
			}
			case YoldaDeck.YOUTUBE -> {
				LinearLayout row = hrow(ctx);
				for (String[] q : new String[][]{{"Haber", "haber canlı"}, {"Canlı yayın", "canlı yayın türkiye"},
						{"Müzik", "türkçe pop"}, {"Spor", "spor özet"}, {"Belgesel", "belgesel türkçe"}}) {
					row.addView(chipBtn(ctx, q[0], v -> YoutubeHomeFragment.searchFor(a, q[1])), chipLp());
				}
				return scroll(ctx, row);
			}
			case YoldaDeck.WEB -> {
				LinearLayout row = hrow(ctx);
				for (String[] s : new String[][]{{"Google", "https://www.google.com"}, {"Hava durumu", "https://www.mgm.gov.tr"},
						{"Haberler", "https://news.google.com/?hl=tr&gl=TR&ceid=TR:tr"}, {"Vikipedi", "https://tr.m.wikipedia.org"}}) {
					row.addView(chipBtn(ctx, s[0], v -> HomeFragment.openWeb(a, s[1])), chipLp());
				}
				return scroll(ctx, row);
			}
			case YoldaDeck.RESUME -> {
				LinearLayout row = hrow(ctx);
				if (lastItem != null) {
					MediaLib.PlayableItem li = lastItem;
					FrameLayout plate = (FrameLayout) imageTile(ctx, null, null, tile, true, li.getName(), v -> playItem(a, li));
					loadItemIcon((ImageView) plate.getChildAt(1), (ImageView) plate.getChildAt(0), li);
					row.addView(plate, tileLp(tile));
				}
				List<YtMusic.Track> rec = MusicFragment.recent(ctx);
				if (!rec.isEmpty()) {
					row.addView(imageTile(ctx, rec.get(0).thumb(), null, tile, false, rec.get(0).title(),
							v -> MusicFragment.playTracks(a, rec, 0)), tileLp(tile));
				}
				return (row.getChildCount() == 0) ? null : row;
			}
			case YoldaDeck.FAVS -> { // kutucuğun altında ad satırı var
				return favRow(ctx, a, Math.max(dp(40), tile - dp(Math.round(17 * Math.max(1f,
						getResources().getConfiguration().fontScale)))));
			}
			case YoldaDeck.WEATHER -> {
				if (weather == null) return null;
				LinearLayout row = hrow(ctx);
				TextView big = text(ctx, weather.temp() + "°", fz.big(), true, 0xFFFFFFFF);
				row.addView(big);
				TextView em = text(ctx, YoldaWeather.emoji(weather.code()), Math.round(fz.big() * 0.72f), false, 0xFFFFFFFF);
				em.setPadding(dp(8), 0, dp(14), 0);
				row.addView(em);
				LinearLayout hours = hrow(ctx);
				for (int i = 1; i < Math.min(weather.hours().size(), 7); i++) {
					YoldaWeather.Hour h = weather.hours().get(i);
					TextView hc = chipBtn(ctx, h.time() + "  " + YoldaWeather.emoji(h.code()) + " " + h.temp() + "°", v -> open(c));
					hours.addView(hc, chipLp());
				}
				row.addView(scroll(ctx, hours), new LinearLayout.LayoutParams(0, -2, 1f));
				return row;
			}
			case YoldaDeck.RADAR -> {
				LinearLayout row = hrow(ctx);
				boolean on = YoldaRadar.isEnabled(ctx);
				row.addView(radarToggle(ctx), chipLp());
				row.addView(radarUpdateBtn(ctx, false), chipLp());
				if (on) row.addView(chipBtn(ctx, "＋ Buraya radar ekle", v -> YoldaStyle.toast(ctx,
						YoldaRadar.addHere(ctx) ? "Radar eklendi" : "Konum henüz bilinmiyor")), chipLp());
				row.addView(chipBtn(ctx, "Ayrıntılar", v -> open(c)), chipLp());
				return scroll(ctx, row);
			}
			case YoldaDeck.PHARMACY -> { // yıldızlı ilçe / iller: tek dokunuşla o yerin nöbetçileri
				LinearLayout row = hrow(ctx);
				for (String s : YoldaPharmacy.stars(ctx, "ilce")) {
					int bar = s.indexOf('|'), sl = s.indexOf('/');
					if ((bar < 0) || (sl < 0) || (sl > bar) || (row.getChildCount() >= 5)) continue;
					String ilSlug = s.substring(0, sl);
					YoldaPharmacy.Place ilce = new YoldaPharmacy.Place(s.substring(sl + 1, bar), s.substring(bar + 1));
					row.addView(chipBtn(ctx, "★ " + ilce.name(), v -> {
						YoldaPharmacy.Place il = YoldaPharmacy.selectedIl(ctx);
						if ((il == null) || !il.slug().equals(ilSlug)) il = new YoldaPharmacy.Place(ilSlug,
								YoldaPharmacy.title(ilSlug));
						for (String x : YoldaPharmacy.stars(ctx, "il")) {
							YoldaPharmacy.Place p = YoldaPharmacy.Place.decode(x);
							if ((p != null) && p.slug().equals(ilSlug)) il = p;
						}
						YoldaPharmacy.select(ctx, il, ilce);
						open(c);
					}), chipLp());
				}
				for (String s : YoldaPharmacy.stars(ctx, "il")) {
					YoldaPharmacy.Place il = YoldaPharmacy.Place.decode(s);
					if ((il == null) || (row.getChildCount() >= 6)) continue;
					row.addView(chipBtn(ctx, "★ " + il.name(), v -> {
						YoldaPharmacy.select(ctx, il, null);
						open(c);
					}), chipLp());
				}
				if (row.getChildCount() == 0) row.addView(chipBtn(ctx, "Yakınımdaki nöbetçiler", v -> open(c)), chipLp());
				return scroll(ctx, row);
			}
			case YoldaDeck.NAV -> {
				LinearLayout row = hrow(ctx);
				android.content.SharedPreferences np = prefs(ctx);
				String home = np.getString("nav_home", null), work = np.getString("nav_work", null);
				if (home != null) row.addView(chipBtn(ctx, "⌂ Ev", v -> NavFragment.navigateTo(ctx, home, false)), chipLp());
				if (work != null) row.addView(chipBtn(ctx, "İş", v -> NavFragment.navigateTo(ctx, work, false)), chipLp());
				row.addView(chipBtn(ctx, "Hedef ara", v -> a.showFragment(R.id.yolda_nav_fragment)), chipLp());
				if (YoldaRadar.feature(ctx, "park") && (YoldaRadar.park(ctx) != null))
					row.addView(chipBtn(ctx, "🅿 Aracım", v -> YoldaRadar.walkToPark(ctx)), chipLp());
				for (String d : NavFragment.recent(ctx)) {
					if (row.getChildCount() >= 7) break;
					row.addView(chipBtn(ctx, d, v -> NavFragment.navigateTo(ctx, d, true)), chipLp());
				}
				return scroll(ctx, row);
			}
			case YoldaDeck.PHONE, YoldaDeck.MIRROR -> {
				LinearLayout row = hrow(ctx);
				java.util.List<String> miss = YoldaPhonePerms.missing(ctx);
				if (!miss.isEmpty()) {
					row.addView(chipBtn(ctx, "⚠ " + miss.size() + " izin eksik — kurulum", v ->
							a.showFragment(R.id.yolda_setup_fragment)), chipLp());
				}
				if (YoldaDeck.PHONE.equals(c.type())) { // telefondaki uygulamalar: ekle, düzenle, aç
					int at = labeledTile(tile);
					row.setGravity(Gravity.TOP);
					android.content.pm.PackageManager pm = ctx.getPackageManager();
					for (String pkg : phoneApps(ctx)) {
						CharSequence label;
						android.graphics.drawable.Drawable icon;
						try {
							android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
							label = pm.getApplicationLabel(ai);
							icon = pm.getApplicationIcon(ai);
						} catch (Exception ex) {
							continue; // kaldırılmış uygulama
						}
						LinearLayout cell = new LinearLayout(ctx);
						cell.setOrientation(LinearLayout.VERTICAL);
						cell.setGravity(Gravity.CENTER_HORIZONTAL);
						ImageView iv = new ImageView(ctx);
						iv.setImageDrawable(icon);
						iv.setContentDescription(label);
						iv.setClickable(true);
						iv.setOnClickListener(v -> openPhoneApp(a, ctx, pkg, label));
						pressEffect(iv);
						cell.addView(iv, new LinearLayout.LayoutParams(at, at));
						TextView name = text(ctx, String.valueOf(label), 12, false, 0xE6FFFFFF);
						name.setSingleLine(true);
						name.setEllipsize(TextUtils.TruncateAt.END);
						name.setGravity(Gravity.CENTER);
						name.setMaxWidth(at + dp(12));
						cell.addView(name, new LinearLayout.LayoutParams(-2, -2));
						LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
						clp.rightMargin = dp(10);
						row.addView(cell, clp);
					}
					row.addView(chipBtn(ctx, "＋ Uygulama", v -> pickPhoneApp()), chipLp());
					if (!phoneApps(ctx).isEmpty()) row.addView(chipBtn(ctx, "✎ Düzenle", v -> editPhoneApps()), chipLp());
					return scroll(ctx, row);
				}
				return row;
			}
			case YoldaDeck.SETTINGS -> {
				LinearLayout row = hrow(ctx);
				row.addView(chipBtn(ctx, "Tema: " + YoldaStyle.label(ctx), v -> YoldaStyle.next(ctx)), chipLp());
				boolean autoTheme = YoldaStyle.isAuto(ctx);
				row.addView(chipBtn(ctx, "Otomatik tema: " + (autoTheme ? "Açık" : "Kapalı"), v -> {
					YoldaStyle.setAuto(ctx, !autoTheme);
					adapter.notifyDataSetChanged();
				}), chipLp());
				boolean wake = YoldaWake.isEnabled(ctx);
				row.addView(chipBtn(ctx, "Hey Yolda: " + (wake ? "Açık" : "Kapalı"), v -> {
					YoldaWake.setEnabled(a, !wake);
					adapter.notifyDataSetChanged();
				}), chipLp());
				row.addView(chipBtn(ctx, "Telefon izinleri", v -> a.showFragment(R.id.yolda_setup_fragment)), chipLp());
				boolean saver = prefs(ctx).getBoolean("data_saver", true);
				row.addView(chipBtn(ctx, "Veri tasarrufu: " + (saver ? "Açık (720p)" : "Kapalı"), v -> {
					prefs(ctx).edit().putBoolean("data_saver", !saver).apply();
					adapter.notifyDataSetChanged();
				}), chipLp());
				boolean auto = prefs(ctx).getBoolean("car_autoplay", true);
				row.addView(chipBtn(ctx, "Araçta otomatik çal: " + (auto ? "Açık" : "Kapalı"), v -> {
					prefs(ctx).edit().putBoolean("car_autoplay", !auto).apply();
					adapter.notifyDataSetChanged();
				}), chipLp());
				boolean ads = prefs(ctx).getBoolean("adblock", true);
				row.addView(chipBtn(ctx, "Reklam engel: " + (ads ? "Açık" : "Kapalı"), v -> {
					prefs(ctx).edit().putBoolean("adblock", !ads).apply();
					adapter.notifyDataSetChanged();
				}), chipLp());
				return scroll(ctx, row);
			}
			case YoldaDeck.ADD -> {
				LinearLayout row = hrow(ctx);
				row.addView(chipBtn(ctx, "+ TV kanalı", v -> addTvChannel()), chipLp());
				row.addView(chipBtn(ctx, "+ Radyo", v -> addRadio()), chipLp());
				row.addView(chipBtn(ctx, "+ YouTube kanalı", v -> addYoutube()), chipLp());
				row.addView(chipBtn(ctx, "+ Web sayfası", v -> addWeb()), chipLp());
				row.addView(chipBtn(ctx, "Düzenle", v -> setEditing(true)), chipLp());
				return scroll(ctx, row);
			}
			case YoldaDeck.TV_CHANNEL, YoldaDeck.RADIO_CHANNEL, YoldaDeck.RADIO_STREAM -> {
				LinearLayout row = hrow(ctx);
				row.setGravity(Gravity.START | Gravity.BOTTOM);
				View logo = imageTile(ctx, c.logo(), YoldaDeck.RADIO_STREAM.equals(c.type()) ? null : c.data(),
						dp(fz.logo()), true, null, v -> open(c));
				row.addView(logo, new LinearLayout.LayoutParams(dp(fz.logo()), dp(fz.logo())));
				return row;
			}
			default -> {
				return null;
			}
		}
	}

	/** Düzenle kipinde kartın altı: ◀ ▶ sırala, ✕ kaldır. */
	private View editBar(Context ctx, YoldaDeck.Card c, int pos) {
		LinearLayout row = hrow(ctx);
		int eb = (cardH > 0) && fit().row() ? Math.min(44, fit().btn()) : 56;
		row.setGravity(Gravity.CENTER);
		row.addView(roundButton(ctx, R.drawable.yi_left, "Sola taşı", v -> {
			int j = deck.move(pos, -1);
			afterChange(j);
		}), new LinearLayout.LayoutParams(dp(eb), dp(eb)));
		LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(dp(eb), dp(eb));
		mid.leftMargin = mid.rightMargin = dp((eb < 56) ? 8 : 14);
		row.addView(roundButton(ctx, R.drawable.yi_right, "Sağa taşı", v -> {
			int j = deck.move(pos, 1);
			afterChange(j);
		}), mid);
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(eb), dp(eb));
		clp.rightMargin = dp((eb < 56) ? 8 : 14);
		row.addView(roundButton(ctx, R.drawable.yi_palette, "Rengi değiştir", v -> pickColor(c, pos)), clp);
		if (c.isRemovable()) {
			row.addView(roundButton(ctx, R.drawable.yi_close, "Kaldır", v -> {
				if (!deck.remove(pos)) return;
				if (!c.isBuiltIn()) stashRemoved(c); // kendi eklediğin kart da geri getirilebilsin
				YoldaStyle.toast(ctx, "Kaldırıldı: " + c.title() + " · ↺ ile geri getirebilirsin");
				afterChange(Math.max(0, pos - 1));
				updateRestoreBtn();
			}), new LinearLayout.LayoutParams(dp(eb), dp(eb)));
		}
		return row;
	}

	private void afterChange(int focus) {
		if (!isAdded() || (getContext() == null)) return;
		save();
		adapter.notifyDataSetChanged();
		list.post(() -> {
			if (!isAdded()) return;
			((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(focus, 0);
			prefs(requireContext()).edit().putInt(PREF_POS, focus).apply();
			list.post(() -> {
				transform();
				updateDots();
			});
		});
	}

	// ---- kartı açma ----

	private void open(YoldaDeck.Card c) {
		YoldaStyle.haptic(list);
		MainActivityDelegate a = getActivityDelegate();
		Context ctx = requireContext();
		switch (c.type()) {
			case YoldaDeck.YOUTUBE -> a.showFragment(R.id.yolda_youtube);
			case YoldaDeck.MUSIC -> a.showFragment(R.id.yolda_music);
			case YoldaDeck.TV -> ChannelsFragment.openSegment(a, false);
			case YoldaDeck.RADIO -> ChannelsFragment.openSegment(a, true);
			case YoldaDeck.NAV -> {
				// Araçta: doğrudan Android Auto'nun haritası (Yolda içinde seçildiyse ya da açılamazsa navigasyon ekranı)
				if (!a.isCarActivityNotMirror() || NavFragment.inAppMap(ctx) || !NavFragment.carNav(a, null))
					a.showFragment(R.id.yolda_nav_fragment);
			}
			case YoldaDeck.RESUME -> {
				if (lastItem != null) playItem(a, lastItem);
				else {
					List<YtMusic.Track> rec = MusicFragment.recent(ctx);
					if (!rec.isEmpty()) MusicFragment.playTracks(a, rec, 0);
					else UiUtils.showInfo(ctx, "Henüz bir şey dinlemedin");
				}
			}
			case YoldaDeck.FAVS -> showFavorites();
			case YoldaDeck.PHARMACY -> a.showFragment(R.id.yolda_pharmacy_fragment);
			case YoldaDeck.RADAR -> a.showFragment(R.id.yolda_radar_fragment);
			case YoldaDeck.WEATHER -> HomeFragment.openWeb(a, "https://www.google.com/search?q=hava+durumu");
			case YoldaDeck.PHONE, YoldaDeck.MIRROR -> openPhoneScreen(a, ctx);
			case YoldaDeck.WEB -> {
				YoldaStyle.webNoToolbar = false; // genel tarayıcı: adres çubuğu görünsün
				a.showFragment(R.id.web_browser_fragment);
				a.postBroadcastEvent(me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED);
			}
			case YoldaDeck.SETTINGS -> a.showFragment(R.id.yolda_menu_fragment);
			case YoldaDeck.ADD -> showAddMenu();
			default -> openFavorite(a, c);
		}
	}

	/** Favori / kanal kartını aç (Hey Yolda "favorilerimden … aç" da kullanır). @return açılabilen türdeyse true. */
	public static boolean openFavorite(MainActivityDelegate a, YoldaDeck.Card c) {
		Context ctx = a.getContext();
		switch (c.type()) {
			case YoldaDeck.TV_CHANNEL, YoldaDeck.RADIO_CHANNEL -> a.getLib().getItem(c.data()).main().onCompletion((i, err) -> {
				if (i instanceof MediaLib.PlayableItem pi) {
					a.getBody().playItem(pi);
					if (!pi.isVideo()) YoldaNowPlaying.show(a);
				} else {
					UiUtils.showInfo(ctx, "Kanal bulunamadı: " + c.title());
				}
			});
			case YoldaDeck.RADIO_STREAM -> {
				a.getMediaServiceBinder().playItem(YoldaStreamItem.create(a.getLib(), c.data(), c.title(), c.logo(), "Radyo"));
				YoldaNowPlaying.show(a);
			}
			case YoldaDeck.YT_CHANNEL -> YoutubeHomeFragment.searchFor(a, c.data());
			case YoldaDeck.URL -> HomeFragment.openWeb(a, c.data());
			case YoldaFavs.SONG -> {
				YtMusic.Track tr = YoldaFavs.songOf(c.data());
				if (tr != null) MusicFragment.playTracks(a, List.of(tr), 0);
				else UiUtils.showInfo(ctx, "Şarkı açılamadı");
			}
			case YoldaFavs.MUSIC_QUERY -> MusicFragment.playQuery(a, c.data());
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---- favoriler: kanal, radyo, YouTube kanalı, şarkı, web sayfası bir arada ----

	private String favSubtitle() {
		int n = (getContext() != null) ? YoldaFavs.load(requireContext()).size() : 0;
		return (n == 0) ? "Kanal, radyo, YouTube kanalı, şarkı ya da web sayfası ekle"
				: n + " favori · basılı tutarak çıkar";
	}

	/** Kart ekleme akışları favorilere mi ekliyor (yoksa desteye kart mı). */
	private boolean addToFavs;

	/** Ekleme akışlarının ortak sonu: desteye kart ya da favori. */
	private void addEntry(String type, String title, String data, @Nullable String logo) {
		if (addToFavs) {
			addToFavs = false;
			boolean fresh = YoldaFavs.add(requireContext(), type, title, data, logo);
			UiUtils.showInfo(requireContext(), fresh ? "★ Favorilere eklendi: " + title : "Zaten favorilerde: " + title);
			refreshCard(YoldaDeck.FAVS);
		} else {
			afterChange(deck.add(type, title, data, logo));
		}
	}

	@DrawableRes
	private static int favIcon(String type) {
		return switch (type) {
			case YoldaDeck.TV_CHANNEL -> R.drawable.yi_tv;
			case YoldaDeck.RADIO_CHANNEL, YoldaDeck.RADIO_STREAM -> R.drawable.yi_live;
			case YoldaDeck.YT_CHANNEL -> R.drawable.yi_youtube;
			case YoldaFavs.SONG, YoldaFavs.MUSIC_QUERY -> R.drawable.yi_music;
			default -> R.drawable.yi_globe;
		};
	}

	private static String favKind(String type) {
		return switch (type) {
			case YoldaDeck.TV_CHANNEL -> "TV kanalı";
			case YoldaDeck.RADIO_CHANNEL, YoldaDeck.RADIO_STREAM -> "Radyo";
			case YoldaDeck.YT_CHANNEL -> "YouTube kanalı";
			case YoldaFavs.SONG -> "Şarkı";
			case YoldaFavs.MUSIC_QUERY -> "Müzik";
			default -> "Web sayfası";
		};
	}

	/** Kartın altı: favori kutucukları (dokun: çal / aç, basılı tut: çıkar) ve sonda "Ekle". */
	private View favRow(Context ctx, MainActivityDelegate a, int tile) {
		LinearLayout row = hrow(ctx);
		for (YoldaDeck.Card f : YoldaFavs.load(ctx)) {
			boolean ch = YoldaDeck.TV_CHANNEL.equals(f.type()) || YoldaDeck.RADIO_CHANNEL.equals(f.type());
			String pic = ((f.logo() != null) && !f.logo().isEmpty()) ? f.logo() : null;
			FrameLayout plate = (FrameLayout) imageTile(ctx, pic, ch ? f.data() : null, tile, ch,
					favKind(f.type()) + ": " + f.title(), v -> open(f));
			ImageView ph = (ImageView) plate.getChildAt(0);
			ph.setImageResource(favIcon(f.type())); // logo gelene kadar türünün simgesi
			plate.setOnLongClickListener(v -> {
				YoldaFavs.remove(ctx, f.key());
				UiUtils.showInfo(ctx, "Favorilerden çıkarıldı: " + f.title());
				refreshCard(YoldaDeck.FAVS);
				return true;
			});
			LinearLayout cell = new LinearLayout(ctx);
			cell.setOrientation(LinearLayout.VERTICAL);
			cell.setGravity(Gravity.CENTER_HORIZONTAL);
			cell.addView(plate, new LinearLayout.LayoutParams(tile, tile));
			TextView name = text(ctx, f.title(), 12, false, 0xE6FFFFFF);
			name.setSingleLine(true);
			name.setEllipsize(TextUtils.TruncateAt.END);
			name.setGravity(Gravity.CENTER);
			name.setMaxWidth(tile + dp(8));
			cell.addView(name, new LinearLayout.LayoutParams(-2, -2));
			LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
			clp.rightMargin = dp(10);
			row.addView(cell, clp);
		}
		row.addView(chipBtn(ctx, "＋ Favori ekle", v -> showFavAddMenu()), chipLp());
		if (!YoldaFavs.load(ctx).isEmpty()) row.addView(chipBtn(ctx, "✎ Düzenle", v -> editFavorites()), chipLp());
		return scroll(ctx, row);
	}

	/** Favorileri düzenle: ▲ ▼ ile sırala, ada dokunup değiştir, ✕ ile kaldır. Değişiklik hemen kaydedilir. */
	private void editFavorites() {
		Context ctx = requireContext();
		FrameLayout panel = openOverlay(ctx, "Favorileri düzenle");
		LinearLayout box = (LinearLayout) panel.getChildAt(0);
		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(true);
		LinearLayout lst = new LinearLayout(ctx);
		lst.setOrientation(LinearLayout.VERTICAL);
		sv.addView(lst, new ScrollView.LayoutParams(-1, -2));
		LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
		slp.topMargin = dp(8);
		box.addView(sv, slp);
		fillFavEdit(ctx, lst);
	}

	private void fillFavEdit(Context ctx, LinearLayout lst) {
		lst.removeAllViews();
		List<YoldaDeck.Card> l = YoldaFavs.load(ctx);
		if (l.isEmpty()) {
			lst.addView(text(ctx, "Favori yok. Kartta \"＋ Favori ekle\" ile ekleyebilirsin.", 16, false, t.muted));
		}
		for (int i = 0; i < l.size(); i++) {
			YoldaDeck.Card f = l.get(i);
			LinearLayout r = hrow(ctx);
			r.setPadding(dp(4), dp(6), 0, dp(6));
			ImageView ic = new ImageView(ctx);
			ic.setImageResource(favIcon(f.type()));
			ic.setImageTintList(ColorStateList.valueOf(t.ink));
			r.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
			LinearLayout names = new LinearLayout(ctx);
			names.setOrientation(LinearLayout.VERTICAL);
			names.setPadding(dp(12), 0, dp(8), 0);
			TextView n = text(ctx, f.title(), 17, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			names.addView(n);
			names.addView(text(ctx, favKind(f.type()) + " · adı değiştirmek için dokun", 12, false, t.muted));
			names.setMinimumHeight(dp(48));
			names.setGravity(Gravity.CENTER_VERTICAL);
			names.setClickable(true);
			names.setContentDescription("Adını değiştir: " + f.title());
			names.setOnClickListener(v -> UiUtils.queryText(ctx, R.string.yolda_fav_name, R.drawable.yi_edit, f.title())
					.onSuccess(s -> {
						if (s == null) return;
						YoldaFavs.rename(ctx, f.key(), s);
						fillFavEdit(ctx, lst);
						refreshCard(YoldaDeck.FAVS);
					}));
			r.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
			int idx = i;
			ImageView up = roundButton(ctx, R.drawable.yi_up, "Yukarı taşı: " + f.title(), v -> {
				YoldaFavs.move(ctx, f.key(), -1);
				fillFavEdit(ctx, lst);
				refreshCard(YoldaDeck.FAVS);
			});
			up.setAlpha((idx > 0) ? 1f : 0.3f);
			r.addView(up, new LinearLayout.LayoutParams(dp(44), dp(44)));
			ImageView down = roundButton(ctx, R.drawable.yi_down, "Aşağı taşı: " + f.title(), v -> {
				YoldaFavs.move(ctx, f.key(), 1);
				fillFavEdit(ctx, lst);
				refreshCard(YoldaDeck.FAVS);
			});
			down.setAlpha((idx < l.size() - 1) ? 1f : 0.3f);
			LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(44), dp(44));
			dlp.leftMargin = dlp.rightMargin = dp(8);
			r.addView(down, dlp);
			r.addView(roundButton(ctx, R.drawable.yi_close, "Kaldır: " + f.title(), v -> {
				YoldaFavs.remove(ctx, f.key());
				YoldaStyle.toast(ctx, "Favorilerden çıkarıldı: " + f.title());
				fillFavEdit(ctx, lst);
				refreshCard(YoldaDeck.FAVS);
			}), new LinearLayout.LayoutParams(dp(44), dp(44)));
			lst.addView(r, new LinearLayout.LayoutParams(-1, -2));
		}
	}

	/** Favorilerin tam listesi (dokun: çal / aç); başta ekleme. */
	/** Hey Yolda "favorileri aç". */
	public void openFavorites() {
		if (isAdded()) showFavorites();
	}

	private void showFavorites() {
		List<YoldaDeck.Card> l = YoldaFavs.load(requireContext());
		if (l.isEmpty()) {
			showFavAddMenu();
			return;
		}
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("＋ Favori ekle", "Kanal, radyo, YouTube kanalı, şarkı, web sayfası", null, R.drawable.yi_add,
				this::showFavAddMenu));
		rows.add(new Row("✎ Favorileri düzenle", "Sırala, adını değiştir, kaldır", null, R.drawable.yi_edit,
				this::editFavorites));
		for (YoldaDeck.Card f : l) rows.add(new Row(f.title(), favKind(f.type()), f.logo(), favIcon(f.type()), () -> open(f)));
		showPicker("Favoriler", rows, null);
	}

	/** Favoriye ne eklenecek: çalan, TV, radyo, YouTube kanalı, şarkı / sanatçı, web sayfası. */
	private void showFavAddMenu() {
		MainActivityDelegate a = getActivityDelegate();
		List<Row> rows = new ArrayList<>();
		MediaLib.PlayableItem cur = a.getCurrentPlayable();
		String[] e = YoldaFavs.entryOf(cur);
		if (e != null) {
			rows.add(new Row("Şu an çalanı ekle", e[1], e[3], favIcon(e[0]), () -> {
				addToFavs = true;
				addEntry(e[0], e[1], e[2], e[3]);
			}));
		}
		rows.add(new Row("TV kanalı", "Listeden seç", null, R.drawable.yi_tv, () -> {
			addToFavs = true;
			addTvChannel();
		}));
		rows.add(new Row("Radyo", "Listeden seç ya da internette ara", null, R.drawable.yi_live, () -> {
			addToFavs = true;
			addRadio();
		}));
		rows.add(new Row("YouTube kanalı", "Kanal ya da konu adı", null, R.drawable.yi_youtube, () -> {
			addToFavs = true;
			addYoutube();
		}));
		rows.add(new Row("Şarkı ya da sanatçı", "Adını yaz ya da söyle", null, R.drawable.yi_music, () -> {
			addToFavs = true;
			askText("Favori müzik", "Şarkı ya da sanatçı (ör. Sezen Aksu)", s -> addEntry(YoldaFavs.MUSIC_QUERY, s, s, null));
		}));
		rows.add(new Row("Web sayfası", "Bir adres", null, R.drawable.yi_globe, () -> {
			addToFavs = true;
			addWeb();
		}));
		showPicker("Favori ekle", rows, null);
	}

	/**
	 * Telefon ekranını araçta aç. İzin eksikse sessizce takılmasın: telefondaysa kurulum ekranı, araçtaysa
	 * hangi iznin nereden verileceği söylenir.
	 */
	private void openPhoneScreen(MainActivityDelegate a, Context ctx) {
		java.util.List<String> miss = YoldaPhonePerms.missing(ctx);
		if (!miss.isEmpty()) {
			YoldaLog.add("Telefon ekranı: eksik izinler " + miss);
			if (a.isCarActivity()) {
				UiUtils.showAlert(ctx, "Telefon ekranı için telefonda şu izinler gerekiyor:\n• " + String.join("\n• ", miss)
						+ "\n\nTelefonda Yolda V2'yi aç → Ayarlar kartı → Telefon izinleri. Araç bağlıyken bu pencereler açılamıyor.");
			} else {
				a.showFragment(R.id.yolda_setup_fragment);
			}
			return;
		}
		HomeFragment.phone(ctx);
	}

	// ---- telefon kartı: telefondaki uygulamalar ----

	private static final String PREF_PHONE_APPS = "phone_apps";

	static List<String> phoneApps(Context ctx) {
		String s = ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE)
				.getString(PREF_PHONE_APPS, "");
		List<String> l = new ArrayList<>();
		for (String p : s.split(",")) if (!p.trim().isEmpty()) l.add(p.trim());
		return l;
	}

	static void savePhoneApps(Context ctx, List<String> l) {
		ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
				.putString(PREF_PHONE_APPS, String.join(",", l)).apply();
	}

	/** Uygulamayı telefonda açar; araçtaysa telefon ekranı da Yolda'da açılır (uygulama araçta kullanılır). */
	private void openPhoneApp(MainActivityDelegate a, Context ctx, String pkg, CharSequence label) {
		YoldaStyle.haptic(list);
		try {
			Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
			if (i == null) {
				YoldaStyle.toast(ctx, label + " açılamadı");
				return;
			}
			ctx.getApplicationContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			YoldaLog.add("Telefon uygulaması açıldı: " + label);
		} catch (Exception ex) {
			YoldaLog.add("Telefon uygulaması açılamadı: " + label + " · " + ex);
			YoldaStyle.toast(ctx, label + " açılamadı");
			return;
		}
		if (a.isCarActivity()) openPhoneScreen(a, ctx); // telefon ekranı araçta: uygulama görünür ve dokunulur
	}

	/** Telefondaki uygulamalardan seç (eklenmiş olanlar listede yok). */
	private void pickPhoneApp() {
		Context ctx = requireContext();
		FrameLayout panel = openOverlay(ctx, "Uygulama ekle");
		LinearLayout box = (LinearLayout) panel.getChildAt(0);
		ScrollView sv = new ScrollView(ctx);
		LinearLayout lst = new LinearLayout(ctx);
		lst.setOrientation(LinearLayout.VERTICAL);
		sv.addView(lst, new ScrollView.LayoutParams(-1, -2));
		LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
		slp.topMargin = dp(8);
		box.addView(sv, slp);
		android.content.pm.PackageManager pm = ctx.getPackageManager();
		Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
		List<android.content.pm.ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(main, 0));
		apps.sort((x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
		List<String> have = phoneApps(ctx);
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (android.content.pm.ResolveInfo ri : apps) {
			String pkg = ri.activityInfo.packageName;
			if (pkg.equals(ctx.getPackageName()) || have.contains(pkg) || !seen.add(pkg)) continue;
			LinearLayout r = hrow(ctx);
			r.setPadding(dp(6), dp(6), dp(6), dp(6));
			r.setMinimumHeight(dp(56));
			r.setClickable(true);
			ImageView iv = new ImageView(ctx);
			iv.setImageDrawable(ri.loadIcon(pm));
			r.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
			TextView n = text(ctx, String.valueOf(ri.loadLabel(pm)), 17, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			n.setPadding(dp(14), 0, 0, 0);
			r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
			r.setContentDescription("Ekle: " + ri.loadLabel(pm));
			r.setOnClickListener(v -> {
				List<String> l = phoneApps(ctx);
				if (!l.contains(pkg)) l.add(pkg);
				savePhoneApps(ctx, l);
				closeOverlay();
				refreshCard(YoldaDeck.PHONE);
				YoldaStyle.toast(ctx, "Telefon kartına eklendi: " + ri.loadLabel(pm));
			});
			lst.addView(r, new LinearLayout.LayoutParams(-1, -2));
		}
	}

	/** Telefon kartındaki uygulamaları düzenle: ▲ ▼ sırala, ✕ kaldır. */
	private void editPhoneApps() {
		Context ctx = requireContext();
		FrameLayout panel = openOverlay(ctx, "Uygulamaları düzenle");
		LinearLayout box = (LinearLayout) panel.getChildAt(0);
		ScrollView sv = new ScrollView(ctx);
		LinearLayout lst = new LinearLayout(ctx);
		lst.setOrientation(LinearLayout.VERTICAL);
		sv.addView(lst, new ScrollView.LayoutParams(-1, -2));
		LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
		slp.topMargin = dp(8);
		box.addView(sv, slp);
		fillPhoneAppsEdit(ctx, lst);
	}

	private void fillPhoneAppsEdit(Context ctx, LinearLayout lst) {
		lst.removeAllViews();
		android.content.pm.PackageManager pm = ctx.getPackageManager();
		List<String> l = phoneApps(ctx);
		for (int i = 0; i < l.size(); i++) {
			String pkg = l.get(i);
			CharSequence label = pkg;
			android.graphics.drawable.Drawable icon = null;
			try {
				android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
				label = pm.getApplicationLabel(ai);
				icon = pm.getApplicationIcon(ai);
			} catch (Exception ignore) {
			}
			LinearLayout r = hrow(ctx);
			r.setPadding(dp(4), dp(6), 0, dp(6));
			ImageView iv = new ImageView(ctx);
			if (icon != null) iv.setImageDrawable(icon);
			r.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
			TextView n = text(ctx, String.valueOf(label), 17, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			n.setPadding(dp(12), 0, dp(8), 0);
			r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
			int idx = i;
			CharSequence lb = label;
			ImageView up = roundButton(ctx, R.drawable.yi_up, "Yukarı taşı: " + label, v -> {
				List<String> m = phoneApps(ctx);
				if (idx > 0) java.util.Collections.swap(m, idx, idx - 1);
				savePhoneApps(ctx, m);
				fillPhoneAppsEdit(ctx, lst);
				refreshCard(YoldaDeck.PHONE);
			});
			up.setAlpha((idx > 0) ? 1f : 0.3f);
			r.addView(up, new LinearLayout.LayoutParams(dp(44), dp(44)));
			ImageView down = roundButton(ctx, R.drawable.yi_down, "Aşağı taşı: " + label, v -> {
				List<String> m = phoneApps(ctx);
				if (idx < m.size() - 1) java.util.Collections.swap(m, idx, idx + 1);
				savePhoneApps(ctx, m);
				fillPhoneAppsEdit(ctx, lst);
				refreshCard(YoldaDeck.PHONE);
			});
			down.setAlpha((idx < l.size() - 1) ? 1f : 0.3f);
			LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(44), dp(44));
			dlp.leftMargin = dlp.rightMargin = dp(8);
			r.addView(down, dlp);
			r.addView(roundButton(ctx, R.drawable.yi_close, "Kaldır: " + label, v -> {
				List<String> m = phoneApps(ctx);
				m.remove(pkg);
				savePhoneApps(ctx, m);
				YoldaStyle.toast(ctx, "Kaldırıldı: " + lb);
				fillPhoneAppsEdit(ctx, lst);
				refreshCard(YoldaDeck.PHONE);
			}), new LinearLayout.LayoutParams(dp(44), dp(44)));
			lst.addView(r, new LinearLayout.LayoutParams(-1, -2));
		}
	}

	// ---- ekleme ----

	private void showAddMenu() {
		addToFavs = false;
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("TV kanalı", "Listeden bir kanal seç", null, R.drawable.yi_tv, this::addTvChannel));
		rows.add(new Row("Radyo kanalı", "Listeden seç ya da internette ara", null, R.drawable.yi_live, this::addRadio));
		rows.add(new Row("YouTube kanalı", "Kanal ya da konu adı", null, R.drawable.yi_youtube, this::addYoutube));
		rows.add(new Row("Web sayfası", "Bir adres", null, R.drawable.yi_globe, this::addWeb));
		addRestoreRows(rows);
		rows.add(new Row("Kartları düzenle", "Sırala ya da kaldır", null, R.drawable.yi_edit, () -> setEditing(true)));
		rows.add(new Row("Desteyi varsayılana döndür", "Eklenen kartlar silinir", null, R.drawable.yi_close, () -> {
			deck.reset();
			afterChange(0);
		}));
		showPicker("Kart ekle", rows, null);
	}

	private void addTvChannel() {
		List<Row> rows = new ArrayList<>();
		for (MediaLib.PlayableItem pi : tvChannels) {
			rows.add(new Row(pi.getName(), groupOf(pi), null, R.drawable.yi_tv,
					() -> addEntry(YoldaDeck.TV_CHANNEL, pi.getName(), pi.getId(), null), pi));
		}
		if (rows.isEmpty()) UiUtils.showInfo(requireContext(), "Kanal listesi henüz yüklenmedi");
		else showPicker("TV kanalı ekle", rows, null);
	}

	private void addRadio() {
		List<Row> rows = new ArrayList<>();
		for (MediaLib.PlayableItem pi : radioChannels) {
			rows.add(new Row(pi.getName(), "Yolda listesi", null, R.drawable.yi_live,
					() -> addEntry(YoldaDeck.RADIO_CHANNEL, pi.getName(), pi.getId(), null), pi));
		}
		showPicker("Radyo ekle", rows, q -> RadioBrowser.search(q));
		// Türkiye'nin en çok dinlenenleri listenin altına
		RadioBrowser.topTurkey().main().onSuccess(l -> {
			if (overlay != null) appendStations(l);
		});
	}

	private void addYoutube() {
		askText("YouTube kanalı ekle", "Kanal ya da konu adı (ör. Habertürk)",
				s -> addEntry(YoldaDeck.YT_CHANNEL, s, s, null));
	}

	private void addWeb() {
		askText("Web sayfası ekle", "Adres (ör. sozcu.com.tr)", s -> {
			String url = s.contains("://") ? s : ("https://" + s.replace(" ", ""));
			String name = android.net.Uri.parse(url).getHost();
			if (name == null) name = s;
			if (name.startsWith("www.")) name = name.substring(4);
			addEntry(YoldaDeck.URL, name, url, null);
		});
	}

	// ---- seçim ve metin pencereleri (kartın içinde, cam panel) ----

	private record Row(String name, String sub, @Nullable String logo, @DrawableRes int icon, Runnable action,
										 @Nullable MediaLib.PlayableItem item) {
		Row(String name, String sub, @Nullable String logo, @DrawableRes int icon, Runnable action) {
			this(name, sub, logo, icon, action, null);
		}
	}

	@Nullable
	private LinearLayout pickerRows;

	private void showPicker(String title, List<Row> rows,
													@Nullable java.util.function.Function<String, me.aap.utils.async.FutureSupplier<List<RadioBrowser.Station>>> search) {
		Context ctx = requireContext();
		FrameLayout panel = openOverlay(ctx, title);
		LinearLayout box = (LinearLayout) panel.getChildAt(0);
		if (search != null) {
			LinearLayout sr = new LinearLayout(ctx);
			sr.setOrientation(LinearLayout.HORIZONTAL);
			sr.setGravity(Gravity.CENTER_VERTICAL);
			EditText q = input(ctx, "İstasyon adı (internette ara)");
			sr.addView(q, new LinearLayout.LayoutParams(0, dp(52), 1f));
			Runnable go = () -> {
				String s = q.getText().toString().trim();
				if (s.isEmpty()) return;
				setStatus("Aranıyor…");
				search.apply(s).main().onCompletion((l, err) -> {
					if (overlay == null) return;
					if (pickerRows != null) pickerRows.removeAllViews();
					if ((l == null) || l.isEmpty()) {
						setStatus((err != null) ? "Radyo dizinine ulaşılamadı" : "Sonuç yok");
						return;
					}
					setStatus("");
					appendStations(l);
				});
			};
			q.setOnEditorActionListener((v, id, ev) -> {
				go.run();
				return true;
			});
			LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(52), dp(52));
			mlp.leftMargin = dp(8);
			sr.addView(roundButton(ctx, R.drawable.yi_mic, "Sesle söyle", v -> dictate(q, go)), mlp);
			sr.addView(roundButton(ctx, R.drawable.yi_search, "Ara", v -> go.run()), mlp);
			box.addView(sr, new LinearLayout.LayoutParams(-1, -2));
		}
		TextView st = text(ctx, "", 14, false, t.muted);
		st.setTag("status");
		st.setPadding(dp(4), dp(6), 0, dp(2));
		box.addView(st);
		ScrollView sv = new ScrollView(ctx);
		pickerRows = new LinearLayout(ctx);
		pickerRows.setOrientation(LinearLayout.VERTICAL);
		sv.addView(pickerRows);
		box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
		for (Row r : rows) pickerRows.addView(row(ctx, r));
	}

	private void setStatus(String s) {
		if (overlay == null) return;
		View v = overlay.findViewWithTag("status");
		if (v instanceof TextView tv) tv.setText(s);
	}

	private void appendStations(List<RadioBrowser.Station> l) {
		if ((pickerRows == null) || (overlay == null)) return;
		Context ctx = requireContext();
		for (RadioBrowser.Station s : l) {
			String sub = s.tags().isEmpty() ? s.country() : s.tags();
			if (sub.length() > 60) sub = sub.substring(0, 60) + "…";
			pickerRows.addView(row(ctx, new Row(s.name(), sub, s.logo(), R.drawable.yi_live,
					() -> addEntry(YoldaDeck.RADIO_STREAM, s.name(), s.url(), s.logo()))));
		}
	}

	private View row(Context ctx, Row r) {
		LinearLayout v = new LinearLayout(ctx);
		v.setOrientation(LinearLayout.HORIZONTAL);
		v.setGravity(Gravity.CENTER_VERTICAL);
		v.setPadding(dp(10), dp(8), dp(10), dp(8));
		v.setMinimumHeight(dp(64));
		v.setBackground(YoldaStyle.glass(ctx, t, 16));
		v.setClickable(true);
		v.setOnClickListener(x -> {
			closeOverlay();
			r.action().run();
		});
		FrameLayout plate = new FrameLayout(ctx);
		GradientDrawable pb = new GradientDrawable();
		pb.setCornerRadius(dp(12));
		pb.setColor(((r.logo() != null) || (r.item() != null)) ? 0xF2FFFFFF : 0x33FFFFFF);
		plate.setBackground(pb);
		ImageView ic = new ImageView(ctx);
		ic.setImageResource(r.icon());
		ic.setImageTintList(ColorStateList.valueOf(((r.logo() != null) || (r.item() != null)) ? 0x55000000 : 0xFFFFFFFF));
		int ip = dp(9);
		ic.setPadding(ip, ip, ip, ip);
		plate.addView(ic, new FrameLayout.LayoutParams(-1, -1));
		ImageView logo = new ImageView(ctx);
		logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
		logo.setPadding(dp(4), dp(4), dp(4), dp(4));
		plate.addView(logo, new FrameLayout.LayoutParams(-1, -1));
		v.addView(plate, new LinearLayout.LayoutParams(dp(48), dp(48)));
		if (r.logo() != null) loadUrl(logo, ic, r.logo());
		else if (r.item() != null) loadItemIcon(logo, ic, r.item());
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.setPadding(dp(14), 0, 0, 0);
		TextView n = text(ctx, r.name(), 17, true, t.ink);
		n.setSingleLine(true);
		n.setEllipsize(TextUtils.TruncateAt.END);
		tx.addView(n);
		if (!r.sub().isEmpty()) {
			TextView s = text(ctx, r.sub(), 13, false, t.muted);
			s.setSingleLine(true);
			s.setEllipsize(TextUtils.TruncateAt.END);
			tx.addView(s);
		}
		v.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
		LinearLayout wrap = new LinearLayout(ctx);
		wrap.setPadding(0, 0, 0, dp(8));
		wrap.addView(v, new LinearLayout.LayoutParams(-1, -2));
		return wrap;
	}

	private void askText(String title, String hint, Consumer<String> done) {
		Context ctx = requireContext();
		FrameLayout panel = openOverlay(ctx, title);
		LinearLayout box = (LinearLayout) panel.getChildAt(0);
		EditText q = input(ctx, hint);
		box.addView(q, new LinearLayout.LayoutParams(-1, dp(56)));
		Runnable ok = () -> {
			String s = q.getText().toString().trim();
			if (s.isEmpty()) return;
			closeOverlay();
			done.accept(s);
		};
		q.setOnEditorActionListener((v, id, ev) -> {
			ok.run();
			return true;
		});
		LinearLayout btns = hrow(ctx);
		btns.setGravity(Gravity.END);
		btns.setPadding(0, dp(14), 0, 0);
		TextView mic = chipBtn(ctx, "🎤  Sesle söyle", v -> dictate(q, ok));
		btns.addView(mic, chipLp());
		TextView add = chipBtn(ctx, "Ekle", v -> ok.run());
		GradientDrawable ab = new GradientDrawable();
		ab.setCornerRadius(dp(999));
		ab.setColor(0xFFFFF4D6);
		add.setBackground(ab);
		add.setTextColor(0xFF111827);
		btns.addView(add, chipLp());
		box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
		box.addView(new View(ctx), new LinearLayout.LayoutParams(-1, 0, 1f));
		q.requestFocus();
	}

	/** Sesle yazdırma (araçta klavye yerine). */
	private void dictate(EditText q, Runnable then) {
		getActivityDelegate().startSpeechRecognizer(null, true).main().onCompletion((l, err) -> {
			if ((l == null) || l.isEmpty()) return;
			q.setText(l.get(0));
			then.run();
		});
	}

	private EditText input(Context ctx, String hint) {
		EditText e = getActivityDelegate().createEditText(ctx);
		e.setSingleLine();
		e.setHint(hint);
		e.setImeOptions(EditorInfo.IME_ACTION_DONE);
		e.setTextSize(17 * YoldaStyle.textScale(ctx));
		e.setTextColor(t.ink);
		e.setHintTextColor(t.muted);
		e.setPadding(dp(18), 0, dp(18), 0);
		e.setGravity(Gravity.CENTER_VERTICAL);
		e.setBackground(YoldaStyle.searchField(ctx, t));
		return e;
	}

	/** Karartılmış zemin üzerinde ortada cam panel; panelin ilk çocuğu içerik sütunu. */
	private FrameLayout openOverlay(Context ctx, String title) {
		closeOverlay();
		FrameLayout ov = new FrameLayout(ctx);
		ov.setBackgroundColor(0xB3000000);
		ov.setClickable(true);
		ov.setOnClickListener(v -> closeOverlay());
		ov.setElevation(dp(28));
		FrameLayout panel = new FrameLayout(ctx);
		panel.setBackground(YoldaStyle.menuPanel(ctx));
		panel.setClickable(true);
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(dp(18), dp(14), dp(18), dp(14));
		panel.addView(box, new FrameLayout.LayoutParams(-1, -1));
		LinearLayout head = hrow(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		TextView tt = text(ctx, title, 20, true, t.ink);
		head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
		head.addView(roundButton(ctx, R.drawable.yi_close, "Kapat", v -> closeOverlay()),
				new LinearLayout.LayoutParams(dp(48), dp(48)));
		box.addView(head);
		int w = Math.min(root.getWidth() - dp(32), dp(560));
		int h = Math.round(root.getHeight() * 0.86f);
		ov.addView(panel, new FrameLayout.LayoutParams((w > 0) ? w : -1, (h > 0) ? h : -1, Gravity.CENTER));
		root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
		ov.setAlpha(0f);
		ov.animate().alpha(1f).setDuration(180).start();
		panel.setScaleX(0.96f);
		panel.setScaleY(0.96f);
		panel.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
		overlay = ov;
		return panel;
	}

	private void closeOverlay() {
		View v = overlay;
		overlay = null;
		pickerRows = null;
		if (v != null) v.animate().alpha(0f).setDuration(140).withEndAction(() -> root.removeView(v)).start();
	}

	// ---- kaldığın yer, favoriler, hava ----

	private void loadExtras(boolean car) {
		MainActivityDelegate a = getActivityDelegate();
		a.getLib().getLastPlayedItem().main().onSuccess(i -> {
			lastItem = i;
			refreshCard(YoldaDeck.RESUME);
		});
		try {
			a.getLib().getFavorites().getChildren().main().onSuccess(l -> {
				favItems.clear();
				for (Object o : l) if (o instanceof MediaLib.PlayableItem pi) favItems.add(pi);
				if (!YoldaFavs.imported(a.getContext())) { // eski yıldızlı kanallar bir kez Yolda favorilerine
					YoldaFavs.importLib(a.getContext(), favItems);
					refreshCard(YoldaDeck.FAVS);
				}
			});
		} catch (Throwable ex) {
			Log.e(ex, "Favoriler okunamadı");
		}
		Runnable wx = () -> YoldaWeather.get(requireContext()).main().onSuccess(w -> {
			weather = w;
			refreshCard(YoldaDeck.WEATHER);
			updateWeatherChip();
		});
		// Telefonda ilk açılışta konum izni sorulur (araçta pencere açılamaz; son bilinen konum ya da İstanbul)
		Context pc = requireContext();
		boolean hasLoc = (pc.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
				== android.content.pm.PackageManager.PERMISSION_GRANTED) || (pc.checkSelfPermission(
				android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED);
		// bir kez sorulur (reddedilirse her açılışta yeniden çıkmasın)
		if (!car && !hasLoc && !prefs(pc).getBoolean("loc_asked", false)) {
			prefs(pc).edit().putBoolean("loc_asked", true).apply();
			a.getAppActivity().checkPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,
					android.Manifest.permission.ACCESS_FINE_LOCATION).onCompletion((r, err) -> {
				if (isAdded()) wx.run();
			});
		} else {
			wx.run();
		}
	}

	private void refreshCard(String type) {
		if ((adapter == null) || !isAdded()) return;
		int i = deck.indexOf(type);
		if (i >= 0) adapter.notifyItemChanged(i);
		if (list != null) list.post(this::transform);
	}

	// ---- kanallar ----

	private void loadChannels() {
		MainActivityDelegate a = getActivityDelegate();
		a.getLib().getItem("TV").main().onCompletion((r, err) -> {
			if (!(r instanceof MediaLib.BrowsableItem tvRoot) || !isAdded()) return;
			tvRoot.getPlayableChildren(true).main().onCompletion((l, e2) -> {
				if ((l == null) || !isAdded()) return;
				tvChannels.clear();
				radioChannels.clear();
				for (MediaLib.PlayableItem pi : l) {
					if (RADIO_GROUP.equals(groupOf(pi))) radioChannels.add(pi);
					else tvChannels.add(pi);
				}
				if (adapter != null) {
					for (String k : new String[]{YoldaDeck.TV, YoldaDeck.RADIO}) {
						int i = deck.indexOf(k);
						if (i >= 0) adapter.notifyItemChanged(i);
					}
				}
				if (list != null) list.post(this::transform);
			});
		});
	}

	private static String groupOf(MediaLib.PlayableItem pi) {
		MediaLib.BrowsableItem p = pi.getParent();
		return (p != null) ? p.getName() : "";
	}

	private void playItem(MainActivityDelegate a, MediaLib.PlayableItem pi) {
		a.getBody().playItem(pi);
		if (!pi.isVideo()) YoldaNowPlaying.show(a);
	}

	/** "Kaldığın yerden" kartının alt yazısı: son kanal / radyo ve son şarkı. */
	private String resumeText() {
		StringBuilder b = new StringBuilder();
		if (lastItem != null) b.append(lastItem.getName());
		List<YtMusic.Track> rec = (getContext() != null) ? MusicFragment.recent(requireContext()) : List.of();
		if (!rec.isEmpty()) {
			if (b.length() > 0) b.append(" · ");
			b.append(rec.get(0).title());
		}
		return (b.length() == 0) ? "Dinlediğin son kanal ve şarkı burada" : b.toString();
	}

	/** Önce favoriler, sonra listedeki ilk kanallar (en çok 8). */
	private List<MediaLib.PlayableItem> pickChannels(List<MediaLib.PlayableItem> all) {
		List<MediaLib.PlayableItem> out = new ArrayList<>();
		try {
			MediaLib.Favorites favs = getActivityDelegate().getLib().getFavorites();
			for (MediaLib.PlayableItem pi : all) if ((out.size() < 8) && favs.isFavoriteItem(pi)) out.add(pi);
		} catch (Throwable ignore) {
		}
		for (MediaLib.PlayableItem pi : all) {
			if (out.size() >= 8) break;
			if (!out.contains(pi)) out.add(pi);
		}
		return out;
	}

	@Nullable
	/** Altında ad satırı olan kutucuğun boyu: ad satırına yer kalsın (kart yüksekliği aşılmasın). */
	private int labeledTile(int tile) {
		return Math.max(dp(40), tile - dp(Math.round(17 * Math.max(1f, getResources().getConfiguration().fontScale))));
	}

	private View channelRow(Context ctx, MainActivityDelegate a, List<MediaLib.PlayableItem> l, int tile) {
		if (l.isEmpty()) return null;
		LinearLayout row = hrow(ctx);
		row.setGravity(Gravity.TOP);
		for (MediaLib.PlayableItem pi : l) {
			FrameLayout plate = (FrameLayout) imageTile(ctx, null, null, tile, true, pi.getName(), v -> {
				a.getBody().playItem(pi);
				if (!pi.isVideo()) YoldaNowPlaying.show(a);
			});
			ImageView logo = (ImageView) plate.getChildAt(1);
			ImageView ph = (ImageView) plate.getChildAt(0);
			if (!pi.isVideo()) ph.setImageResource(R.drawable.yi_live); // radyo: logo gelene kadar radyo simgesi
			loadItemIcon(logo, ph, pi);
			LinearLayout cell = new LinearLayout(ctx);
			cell.setOrientation(LinearLayout.VERTICAL);
			cell.setGravity(Gravity.CENTER_HORIZONTAL);
			cell.addView(plate, new LinearLayout.LayoutParams(tile, tile));
			TextView name = text(ctx, pi.getName(), 12, false, 0xE6FFFFFF);
			name.setSingleLine(true);
			name.setEllipsize(TextUtils.TruncateAt.END);
			name.setGravity(Gravity.CENTER);
			name.setMaxWidth(tile + dp(12));
			cell.addView(name, new LinearLayout.LayoutParams(-2, -2));
			LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
			clp.rightMargin = dp(10);
			row.addView(cell, clp);
		}
		return scroll(ctx, row);
	}

	/**
	 * Kare kutucuk: {@code white} ise beyaz plaka üzerinde logo (kanallar), değilse kapak (şarkılar).
	 * {@code itemId} verilirse logo o öğeden yüklenir. Çocuklar: 0 yer tutucu simge, 1 resim.
	 */
	private View imageTile(Context ctx, @Nullable String url, @Nullable String itemId, int size, boolean white,
												 @Nullable String desc, View.OnClickListener click) {
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(18));
		g.setColor(white ? 0xF2FFFFFF : 0x40FFFFFF);
		f.setBackground(g);
		f.setClipToOutline(true);
		f.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(18));
			}
		});
		f.setElevation(dp(3));
		f.setClickable(true);
		f.setOnClickListener(click);
		if (desc != null) f.setContentDescription(desc);
		pressEffect(f);
		ImageView ph = new ImageView(ctx);
		ph.setImageResource(white ? R.drawable.yi_tv : R.drawable.yi_music);
		ph.setImageTintList(ColorStateList.valueOf(white ? 0x44000000 : 0xCCFFFFFF));
		int pp = size / 4;
		ph.setPadding(pp, pp, pp, pp);
		f.addView(ph, new FrameLayout.LayoutParams(-1, -1));
		ImageView img = new ImageView(ctx);
		img.setScaleType(white ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
		int ip = white ? size / 9 : 0;
		img.setPadding(ip, ip, ip, ip);
		f.addView(img, new FrameLayout.LayoutParams(-1, -1));
		if (url != null) loadUrl(img, ph, url);
		else if (itemId != null) {
			getActivityDelegate().getLib().getItem(itemId).main().onSuccess(i -> {
				if (i instanceof MediaLib.PlayableItem pi) loadItemIcon(img, ph, pi);
			});
		}
		return f;
	}

	private void loadItemIcon(ImageView img, @Nullable ImageView ph, MediaLib.PlayableItem pi) {
		pi.getIconUri().main().onSuccess(u -> {
			if (u != null) loadUrl(img, ph, u.toString());
		});
	}

	private void loadUrl(ImageView img, @Nullable ImageView ph, String url) {
		try {
			getActivityDelegate().getLib().getBitmap(url, true, true).main().onSuccess(b -> {
				if (b == null) return;
				img.setImageBitmap(b);
				if (ph != null) ph.setVisibility(View.INVISIBLE);
			});
		} catch (Throwable ex) {
			Log.e(ex, "Resim yüklenemedi: ", url);
		}
	}

	// ---- küçük yapı taşları ----

	private LinearLayout hrow(Context ctx) {
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);
		return r;
	}

	private View scroll(Context ctx, View row) {
		HorizontalScrollView hs = new ChipScroll(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.setHorizontalFadingEdgeEnabled(true);
		hs.setFadingEdgeLength(dp(28));
		hs.addView(row);
		return hs;
	}

	/**
	 * Kart içindeki yatay kısayol şeridi: kaydırılabildiği sürece desteye (dış yatay liste) dokunuşu
	 * kaptırmaz; şeridin sonuna gelince deste kaymaya devam eder.
	 */
	private static final class ChipScroll extends HorizontalScrollView {
		private float downX;

		ChipScroll(Context ctx) {
			super(ctx);
		}

		@Override
		public boolean dispatchTouchEvent(MotionEvent e) {
			switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN -> {
					downX = e.getX();
					if (canScrollHorizontally(1) || canScrollHorizontally(-1))
						getParent().requestDisallowInterceptTouchEvent(true);
				}
				case MotionEvent.ACTION_MOVE -> {
					float dx = e.getX() - downX;
					boolean can = (dx < 0) ? canScrollHorizontally(1) : canScrollHorizontally(-1);
					getParent().requestDisallowInterceptTouchEvent(can);
				}
				default -> {
				}
			}
			return super.dispatchTouchEvent(e);
		}
	}

	private LinearLayout.LayoutParams tileLp(int tile) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tile, tile);
		lp.rightMargin = dp(12);
		return lp;
	}

	private LinearLayout.LayoutParams chipLp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp((cardH > 0) ? fit().chipH() : 48));
		lp.rightMargin = dp(10);
		return lp;
	}

	private TextView chipBtn(Context ctx, String s, View.OnClickListener click) {
		TextView c = text(ctx, s, 15, true, 0xFFFFFFFF);
		c.setGravity(Gravity.CENTER);
		c.setPadding(dp(18), 0, dp(18), 0);
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(999));
		g.setColor(0x33FFFFFF);
		g.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
		c.setBackground(g);
		c.setClickable(true);
		c.setOnClickListener(click);
		pressEffect(c);
		return c;
	}

	private ImageView roundButton(Context ctx, @DrawableRes int icon, String desc, View.OnClickListener click) {
		ImageView b = new ImageView(ctx);
		b.setImageResource(icon);
		b.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		int p = dp(11);
		b.setPadding(p, p, p, p);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setContentDescription(desc);
		b.setClickable(true);
		b.setFocusable(true);
		b.setOnClickListener(click);
		pressEffect(b);
		return b;
	}

	@SuppressLint("ClickableViewAccessibility")
	private static void pressEffect(View v) {
		v.setOnTouchListener((x, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start();
			else if ((act == MotionEvent.ACTION_UP) || (act == MotionEvent.ACTION_CANCEL))
				x.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
			return false;
		});
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size * YoldaStyle.textScale(ctx));
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
