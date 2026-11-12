package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
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
	private Glow glow;

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
		if (!prefs(ctx).getBoolean("deck_has_nav", false)) {
			deck.restore(YoldaDeck.NAV);
			prefs(ctx).edit().putBoolean("deck_has_nav", true).putString(PREF_DECK, deck.encode()).apply();
		}
		boolean car = getActivityDelegate().isCarActivity();

		root = new FrameLayout(ctx);
		YoldaStyle.applyScreenBackground(root, t);
		// Ortadaki kartın renginde arkada süzülen ışıklar (kaydırdıkça kartın rengine akar)
		glow = new Glow(ctx);
		glow.drift = !car; // araçta ışıklar sabit (sürekli çizim araca giden görüntüyü ağırlaştırıyor)
		root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));

		// Üst satır: saat ve tarih, sağda düzenle
		LinearLayout top = new LinearLayout(ctx);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		top.setPadding(dp(22), YoldaStyle.cutoutTopPx + dp(car ? 8 : 12), dp(18), 0);
		TextClock clock = new TextClock(ctx);
		clock.setFormat24Hour("HH:mm");
		clock.setFormat12Hour("HH:mm");
		clock.setTextSize(car ? 24 : 28);
		clock.setTypeface(Typeface.DEFAULT_BOLD);
		clock.setTextColor(t.ink);
		top.addView(clock);
		TextClock date = new TextClock(ctx);
		date.setFormat24Hour("EEEE, d MMMM");
		date.setFormat12Hour("EEEE, d MMMM");
		date.setTextSize(14);
		date.setTextColor(t.muted);
		date.setPadding(dp(12), dp(4), 0, 0);
		top.addView(date, new LinearLayout.LayoutParams(0, -2, 1f));
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
		});
		LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1f);
		llp.topMargin = dp(car ? 4 : 10);
		col.addView(list, llp);

		dots = new LinearLayout(ctx);
		dots.setOrientation(LinearLayout.HORIZONTAL);
		dots.setGravity(Gravity.CENTER);
		dots.setPadding(0, dp(6), 0, dp(car ? 8 : 14));
		col.addView(dots, new LinearLayout.LayoutParams(-1, -2));

		loadChannels();
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
		boolean wide = w > h * 1.2f;
		int nw = Math.min(Math.round(w * (wide ? 0.6f : 0.8f)), dp(620));
		int nh = Math.min(h - dp(12), wide ? dp(460) : dp(620));
		boolean changed = (nw != cardW) || (nh != cardH);
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
	private void updateGlow() {
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
			add(a, lk.c1, w);
			add(b, lk.c2, w);
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

	private void setEditing(boolean on) {
		editing = on;
		editBtn.setImageResource(on ? R.drawable.yi_check : R.drawable.yi_edit);
		editBtn.setContentDescription(on ? "Bitti" : "Düzenle");
		adapter.notifyDataSetChanged();
		list.post(this::transform);
		if (on) UiUtils.showInfo(requireContext(), "Düzenle: ◀ ▶ sırala, 🎨 renk seç, ✕ kaldır");
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
			h.box.addView(card(h.box.getContext(), deck.get(pos), pos),
					new FrameLayout.LayoutParams(-1, cardH, Gravity.CENTER_VERTICAL));
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
		return (cc == null) ? lk : new Look(cc[0], cc[1], lk.icon(), lk.tint(), lk.tag(), lk.sub());
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
			case YoldaDeck.NAV -> new Look(0xFF2DD4BF, 0xFF0F766E, R.drawable.yi_nav, true, "NAVİGASYON",
					"Yol tarifi, ev, iş ve son gidilen yerler");
			case YoldaDeck.PHONE -> new Look(0xFF34D399, 0xFF047857, R.drawable.yi_phone, true, "TELEFON",
					"Telefonun ekranını araçta kullan");
			case YoldaDeck.WEB -> new Look(0xFF60A5FA, 0xFF1D4ED8, R.drawable.yi_globe, true, "TARAYICI",
					"İnternette gez, reklamsız");
			case YoldaDeck.SETTINGS -> new Look(0xFF94A3B8, 0xFF334155, R.drawable.yi_settings, true, "AYARLAR",
					"Tema, Hey Yolda, reklam engelleyici ve dahası");
			case YoldaDeck.ADD -> new Look(0xFFA78BFA, 0xFF5B21B6, R.drawable.yi_add, true, "EKLE",
					"TV, radyo, YouTube kanalı ya da web sayfası kartı ekle");
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
		FrameLayout f = new FrameLayout(ctx);
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{lk.c1, lk.c2});
		bg.setCornerRadius(dp(30));
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(dp(30));
		GradientDrawable rim = new GradientDrawable();
		rim.setCornerRadius(dp(30));
		rim.setStroke(Math.max(1, dp(1)), 0x66FFFFFF);
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
		if (lk.tint) mark.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		mark.setAlpha(0.13f);
		int ms = Math.round(cardH * 0.62f);
		FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ms, ms, Gravity.END | Gravity.BOTTOM);
		mlp.rightMargin = -ms / 6;
		mlp.bottomMargin = -ms / 8;
		f.addView(mark, mlp);

		boolean small = cardH < dp(330);
		LinearLayout v = new LinearLayout(ctx);
		v.setOrientation(LinearLayout.VERTICAL);
		int p = dp(small ? 18 : 24);
		v.setPadding(p, p, p, p);
		f.addView(v, new FrameLayout.LayoutParams(-1, -1));

		// Üst satır: simge, etiket + ad, "Aç"
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		FrameLayout chip = new FrameLayout(ctx);
		GradientDrawable cb = new GradientDrawable();
		cb.setCornerRadius(dp(16));
		cb.setColor(lk.tint ? 0x33FFFFFF : 0xF2FFFFFF);
		chip.setBackground(cb);
		ImageView ic = new ImageView(ctx);
		ic.setImageResource(lk.icon);
		if (lk.tint) ic.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		chip.addView(ic, new FrameLayout.LayoutParams(dp(small ? 26 : 30), dp(small ? 26 : 30), Gravity.CENTER));
		head.addView(chip, new LinearLayout.LayoutParams(dp(small ? 46 : 54), dp(small ? 46 : 54)));
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
		TextView tag = text(ctx, lk.tag, 12, true, 0xE6FFFFFF);
		tag.setLetterSpacing(0.14f);
		tagRow.addView(tag);
		names.addView(tagRow);
		TextView title = text(ctx, c.title(), small ? 26 : 32, true, 0xFFFFFFFF);
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		names.addView(title);
		head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
		v.addView(head);

		TextView sub = text(ctx, lk.sub, 15, false, 0xD9FFFFFF);
		sub.setMaxLines(2);
		sub.setEllipsize(TextUtils.TruncateAt.END);
		sub.setPadding(0, dp(10), 0, 0);
		v.addView(sub);
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
		TextView openBtn = text(ctx, "▶  Aç", 16, true, 0xFF111827);
		openBtn.setGravity(Gravity.CENTER);
		openBtn.setPadding(dp(18), 0, dp(20), 0);
		GradientDrawable ob = new GradientDrawable();
		ob.setCornerRadius(dp(999));
		ob.setColor(0xFFFFF4D6);
		openBtn.setBackground(ob);
		openBtn.setElevation(dp(4));
		openBtn.setContentDescription("Aç: " + c.title());
		openBtn.setOnClickListener(x -> open(c));
		LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, dp(52));
		olp.leftMargin = dp(10);
		bottom.addView(openBtn, olp);
		v.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
		return f;
	}

	/** Kartın alt kısmı: kanal logoları, son şarkılar, hızlı ayarlar, ekleme seçenekleri… */
	@Nullable
	private View content(Context ctx, YoldaDeck.Card c) {
		MainActivityDelegate a = getActivityDelegate();
		boolean small = cardH < dp(330);
		int tile = dp(small ? 70 : 84);
		switch (c.type()) {
			case YoldaDeck.TV -> {
				return channelRow(ctx, a, pickChannels(tvChannels), tile);
			}
			case YoldaDeck.RADIO -> {
				return channelRow(ctx, a, pickChannels(radioChannels), tile);
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
			case YoldaDeck.NAV -> {
				LinearLayout row = hrow(ctx);
				for (String d : NavFragment.recent(ctx)) {
					if (row.getChildCount() >= 5) break;
					row.addView(chipBtn(ctx, d, v -> NavFragment.navigateTo(ctx, d, true)), chipLp());
				}
				if (row.getChildCount() == 0) row.addView(chipBtn(ctx, "Nereye gidiyoruz?", v -> open(c)), chipLp());
				return scroll(ctx, row);
			}
			case YoldaDeck.PHONE -> {
				LinearLayout row = hrow(ctx);
				row.addView(chipBtn(ctx, "Telefon ekranını aç", v -> HomeFragment.phone(ctx)), chipLp());
				return row;
			}
			case YoldaDeck.SETTINGS -> {
				LinearLayout row = hrow(ctx);
				row.addView(chipBtn(ctx, "Tema: " + t.name, v -> YoldaStyle.next(ctx)), chipLp());
				boolean wake = YoldaWake.isEnabled(ctx);
				row.addView(chipBtn(ctx, "Hey Yolda: " + (wake ? "Açık" : "Kapalı"), v -> {
					YoldaWake.setEnabled(a, !wake);
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
						dp(small ? 84 : 104), true, null, v -> open(c));
				row.addView(logo, new LinearLayout.LayoutParams(dp(small ? 84 : 104), dp(small ? 84 : 104)));
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
		row.setGravity(Gravity.CENTER);
		row.addView(roundButton(ctx, R.drawable.yi_left, "Sola taşı", v -> {
			int j = deck.move(pos, -1);
			afterChange(j);
		}), new LinearLayout.LayoutParams(dp(56), dp(56)));
		LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(dp(56), dp(56));
		mid.leftMargin = mid.rightMargin = dp(14);
		row.addView(roundButton(ctx, R.drawable.yi_right, "Sağa taşı", v -> {
			int j = deck.move(pos, 1);
			afterChange(j);
		}), mid);
		LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(56), dp(56));
		clp.rightMargin = dp(14);
		row.addView(roundButton(ctx, R.drawable.yi_palette, "Rengi değiştir", v -> pickColor(c, pos)), clp);
		if (c.isRemovable()) {
			row.addView(roundButton(ctx, R.drawable.yi_close, "Kaldır", v -> {
				if (deck.remove(pos)) afterChange(Math.max(0, pos - 1));
			}), new LinearLayout.LayoutParams(dp(56), dp(56)));
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
		MainActivityDelegate a = getActivityDelegate();
		Context ctx = requireContext();
		switch (c.type()) {
			case YoldaDeck.YOUTUBE -> a.showFragment(R.id.yolda_youtube);
			case YoldaDeck.MUSIC -> a.showFragment(R.id.yolda_music);
			case YoldaDeck.TV -> ChannelsFragment.openSegment(a, false);
			case YoldaDeck.RADIO -> ChannelsFragment.openSegment(a, true);
			case YoldaDeck.NAV -> a.showFragment(R.id.yolda_nav_fragment);
			case YoldaDeck.PHONE -> HomeFragment.phone(ctx);
			case YoldaDeck.WEB -> {
				YoldaStyle.webNoToolbar = false; // genel tarayıcı: adres çubuğu görünsün
				a.showFragment(R.id.web_browser_fragment);
				a.postBroadcastEvent(me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED);
			}
			case YoldaDeck.SETTINGS -> a.showFragment(R.id.yolda_menu_fragment);
			case YoldaDeck.ADD -> showAddMenu();
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
			default -> {
			}
		}
	}

	// ---- ekleme ----

	private void showAddMenu() {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("TV kanalı", "Listeden bir kanal seç", null, R.drawable.yi_tv, this::addTvChannel));
		rows.add(new Row("Radyo kanalı", "Listeden seç ya da internette ara", null, R.drawable.yi_live, this::addRadio));
		rows.add(new Row("YouTube kanalı", "Kanal ya da konu adı", null, R.drawable.yi_youtube, this::addYoutube));
		rows.add(new Row("Web sayfası", "Bir adres", null, R.drawable.yi_globe, this::addWeb));
		for (String type : deck.missingBuiltIns()) {
			rows.add(new Row(YoldaDeck.titleOf(type) + " kartını geri getir", "Kaldırılmış kart", null, R.drawable.yi_history,
					() -> afterChange(deck.restore(type))));
		}
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
			rows.add(new Row(pi.getName(), groupOf(pi), null, R.drawable.yi_tv, () -> {
				int at = deck.add(YoldaDeck.TV_CHANNEL, pi.getName(), pi.getId(), null);
				afterChange(at);
			}, pi));
		}
		if (rows.isEmpty()) UiUtils.showInfo(requireContext(), "Kanal listesi henüz yüklenmedi");
		else showPicker("TV kanalı ekle", rows, null);
	}

	private void addRadio() {
		List<Row> rows = new ArrayList<>();
		for (MediaLib.PlayableItem pi : radioChannels) {
			rows.add(new Row(pi.getName(), "Yolda listesi", null, R.drawable.yi_live, () -> {
				int at = deck.add(YoldaDeck.RADIO_CHANNEL, pi.getName(), pi.getId(), null);
				afterChange(at);
			}, pi));
		}
		showPicker("Radyo ekle", rows, q -> RadioBrowser.search(q));
		// Türkiye'nin en çok dinlenenleri listenin altına
		RadioBrowser.topTurkey().main().onSuccess(l -> {
			if (overlay != null) appendStations(l);
		});
	}

	private void addYoutube() {
		askText("YouTube kanalı ekle", "Kanal ya da konu adı (ör. Habertürk)", s -> {
			int at = deck.add(YoldaDeck.YT_CHANNEL, s, s, null);
			afterChange(at);
		});
	}

	private void addWeb() {
		askText("Web sayfası ekle", "Adres (ör. sozcu.com.tr)", s -> {
			String url = s.contains("://") ? s : ("https://" + s.replace(" ", ""));
			String name = android.net.Uri.parse(url).getHost();
			if (name == null) name = s;
			if (name.startsWith("www.")) name = name.substring(4);
			int at = deck.add(YoldaDeck.URL, name, url, null);
			afterChange(at);
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
			pickerRows.addView(row(ctx, new Row(s.name(), sub, s.logo(), R.drawable.yi_live, () -> {
				int at = deck.add(YoldaDeck.RADIO_STREAM, s.name(), s.url(), s.logo());
				afterChange(at);
			})));
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
	private View channelRow(Context ctx, MainActivityDelegate a, List<MediaLib.PlayableItem> l, int tile) {
		if (l.isEmpty()) return null;
		LinearLayout row = hrow(ctx);
		for (MediaLib.PlayableItem pi : l) {
			FrameLayout plate = (FrameLayout) imageTile(ctx, null, null, tile, true, pi.getName(), v -> {
				a.getBody().playItem(pi);
				if (!pi.isVideo()) YoldaNowPlaying.show(a);
			});
			ImageView logo = (ImageView) plate.getChildAt(1);
			loadItemIcon(logo, (ImageView) plate.getChildAt(0), pi);
			row.addView(plate, tileLp(tile));
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
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
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
