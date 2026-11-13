package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/**
 * Yolda Liquid Glass tasarım dili (Apple iOS 26 tarzı):
 * - Zemin: koyu taban üzerinde canlı, yavaşça süzülen dört renk ışığı (duvar kâğıdı).
 * - Cam: berrak (koyu değil); sol üstten sağ alta hafifleyen beyaz dolgu, üst yarıda ışık yansıması,
 *   ince parlak kenar. Yazılar beyaz.
 * Tüm Yolda ekranları ve Fermata'nın çerçevesi (alt menü, mini oynatıcı) bunu kullanır.
 */
public final class YoldaStyle {

	public static final String PREFS = "yolda_home";

	public static final class Theme {
		public final String name;
		public final boolean light = false;
		public final int base, accent, hero1, hero2;
		public final int ink = 0xFFF2F4F7, muted = 0xFFCBD1DC;
		/** Dört duvar kâğıdı ışığı. */
		public final int[] blobs;
		public final int glassTop = 0x38FFFFFF, glassBottom = 0x14FFFFFF, edge = 0x59FFFFFF;
		public final int surface = 0x24FFFFFF, stroke = 0x59FFFFFF;
		public final int bg1, bg2;
		/** Kart üslubu: CARDS_VIVID canlı renk geçişi, CARDS_SOFT yarı doygun, CARDS_NOBLE koyu cam. */
		public final int cards;

		Theme(String name, int base, int[] blobs, int accent) {
			this(name, base, blobs, accent, CARDS_VIVID);
		}

		Theme(String name, int base, int[] blobs, int accent, int cards) {
			this.cards = cards;
			this.name = name;
			this.base = base;
			this.blobs = blobs;
			this.accent = accent;
			this.hero1 = blobs[0];
			this.hero2 = blobs[1];
			this.bg1 = base;
			this.bg2 = base;
		}
	}

	public static final int CARDS_VIVID = 0, CARDS_SOFT = 1, CARDS_NOBLE = 2;

	public static final Theme[] THEMES = {
			new Theme("Gece", 0xFF0A0D18, new int[]{0xFF5B21B6, 0xFF9D174D, 0xFF0369A1, 0xFF0A0D18}, 0xFFF5F7FF),
			new Theme("Gece mavi", 0xFF0B0E2A, new int[]{0xFF6D5BFF, 0xFF00B8FF, 0xFFFF4FA0, 0xFF2E1A78}, 0xFF7AA2FF),
			new Theme("Gün batımı", 0xFF24110C, new int[]{0xFFFF8A3D, 0xFFFF4F6D, 0xFFFFC857, 0xFF6B1E3F}, 0xFFFFC857),
			new Theme("Okyanus", 0xFF061A24, new int[]{0xFF00C9A7, 0xFF1E90FF, 0xFF7BDFF2, 0xFF0B3B5C}, 0xFF7BDFF2),
			new Theme("Gül", 0xFF220C1F, new int[]{0xFFFF6FB5, 0xFFB26BFF, 0xFFFFB38A, 0xFF5A1446}, 0xFFFFB38A),
			// Sakin temalar: kartlar koyu cam (renk yalnız vurgu) ya da yarı doygun; zemin ışıkları soluk
			new Theme("Asil", 0xFF0B0D12, new int[]{0xFF2A2740, 0xFF1C2433, 0xFF262230, 0xFF0B0D12}, 0xFFE6E8EE, CARDS_NOBLE),
			new Theme("Yumuşak", 0xFF0E0F15, new int[]{0xFF3E3566, 0xFF4A3340, 0xFF2D3F4A, 0xFF0E0F15}, 0xFFD6D1EA, CARDS_SOFT),
	};

	/** Rengi sakinleştirir: doygunluğu sat, parlaklığı val katıyla çarpar (HSV). */
	public static int calm(int c, float sat, float val) {
		float[] h = new float[3];
		Color.colorToHSV(c | 0xFF000000, h);
		h[1] = Math.min(1f, h[1] * sat);
		h[2] = Math.min(1f, h[2] * val);
		return Color.HSVToColor(h);
	}

	/** Asil temada kartın vurgu rengi: açık, pastel (koyu zemin üzerinde okunur). */
	public static int nobleAccent(int c) {
		float[] h = new float[3];
		Color.colorToHSV(c | 0xFF000000, h);
		h[1] = Math.min(h[1], 0.38f);
		h[2] = Math.max(h[2], 0.84f);
		return Color.HSVToColor(h);
	}

	/** Rengin saydamlığını ayarlar (0-1). */
	public static int alpha(int c, float a) {
		return (Math.round(a * 255) << 24) | (c & 0xFFFFFF);
	}

	/** Fermata "Yolda Cam" teması etkinse true: Yolda ekranları kendi zeminini çizmez, kökteki ortak zemin görünür. */
	public static volatile boolean glassActive;

	/** Yolda V2: alt / yan menü yok; ana ekran yığılmış kart destesi, sesli asistan ve geri düğmesi sol altta. */
	public static final boolean DECK = true;

	/**
	 * Üstte bırakılacak kamera çentiği payı. Telefondan ölçülür; araç ekranında çentik yoktur (telefonun piksel
	 * değeri araçta büyük bir boşluk açıyordu).
	 */
	public static int cutoutTop(Context ctx) {
		try {
			if (me.aap.fermata.ui.activity.MainActivityDelegate.get(ctx).isCarActivity()) return 0;
		} catch (Throwable ignore) {
		}
		return cutoutTopPx;
	}
	/** V2: sesli düğme ve yanındaki arama kutuları aynı boy ve aynı alt boşluk (tam aynı hizada). */
	public static final int VOICE_DP = 56;
	public static final int VOICE_BOTTOM_DP = 14;

	/** Ortak zemin çizilen kökler (telefon ve araç ekranı ayrı ayrı): tema değişince hepsi yenilenir. */
	private static final java.util.List<WeakReference<View>> backdropHosts = new java.util.ArrayList<>();

	private YoldaStyle() {
	}

	/** Otomatik tema: gündüz ve gece için ayrı tema, duruma göre kendiliğinden değişir. */
	public static final String PREF_AUTO = "theme_auto", PREF_DAY = "theme_day", PREF_NIGHT = "theme_night";
	/** Tema seçimini etkileyen ayarlar (değişince ekranlar yenilenir). */
	public static final java.util.Set<String> THEME_KEYS = java.util.Collections.unmodifiableSet(
			new java.util.HashSet<>(java.util.Arrays.asList("theme", PREF_AUTO, PREF_DAY, PREF_NIGHT)));
	public static final int DEFAULT_DAY = 1, DEFAULT_NIGHT = 5; // Gece mavi / Asil
	/** Araç gece kipi (farlar / araç ışık algılayıcısı): araç ekranı açıkken o belirler. */
	private static volatile boolean carNight;
	private static volatile long carNightAt;

	private static android.content.SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static int index(Context ctx) {
		android.content.SharedPreferences p = prefs(ctx);
		if (p.getBoolean(PREF_AUTO, false)) {
			boolean night = isNight(ctx);
			return Math.floorMod(p.getInt(night ? PREF_NIGHT : PREF_DAY, night ? DEFAULT_NIGHT : DEFAULT_DAY), THEMES.length);
		}
		return Math.floorMod(p.getInt("theme", 0), THEMES.length);
	}

	public static boolean isAuto(Context ctx) {
		return prefs(ctx).getBoolean(PREF_AUTO, false);
	}

	public static void setAuto(Context ctx, boolean on) {
		android.content.SharedPreferences p = prefs(ctx);
		android.content.SharedPreferences.Editor e = p.edit().putBoolean(PREF_AUTO, on);
		if (on && !p.contains(PREF_DAY)) e.putInt(PREF_DAY, DEFAULT_DAY);
		if (on && !p.contains(PREF_NIGHT)) e.putInt(PREF_NIGHT, DEFAULT_NIGHT);
		e.apply();
	}

	public static int dayTheme(Context ctx) {
		return Math.floorMod(prefs(ctx).getInt(PREF_DAY, DEFAULT_DAY), THEMES.length);
	}

	public static int nightTheme(Context ctx) {
		return Math.floorMod(prefs(ctx).getInt(PREF_NIGHT, DEFAULT_NIGHT), THEMES.length);
	}

	public static void setDayTheme(Context ctx, int i) {
		prefs(ctx).edit().putInt(PREF_DAY, Math.floorMod(i, THEMES.length)).apply();
	}

	public static void setNightTheme(Context ctx, int i) {
		prefs(ctx).edit().putInt(PREF_NIGHT, Math.floorMod(i, THEMES.length)).apply();
	}

	/** Araç ekranı gece / gündüz kipini bildirir (Android Auto farlara göre değiştirir). */
	public static void setCarNight(boolean night) {
		carNight = night;
		carNightAt = android.os.SystemClock.elapsedRealtime();
	}

	/** Şu an gece mi: araç bağlıysa aracın kipi, değilse konuma göre güneşin batıp batmadığı. */
	public static boolean isNight(Context ctx) {
		if ((carNightAt != 0) && (android.os.SystemClock.elapsedRealtime() - carNightAt < 120_000)) return carNight;
		android.content.SharedPreferences p = prefs(ctx);
		return sunDown(System.currentTimeMillis(), p.getFloat("loc_lat", 39.9f), p.getFloat("loc_lon", 32.85f));
	}

	/** Güneş batmış mı (NOAA yaklaşık hesabı; dakika duyarlılığında yeterli). Varsayılan konum Ankara. */
	public static boolean sunDown(long utcMs, double lat, double lon) {
		java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
		c.setTimeInMillis(utcMs);
		double g = 2 * Math.PI / 365 * (c.get(java.util.Calendar.DAY_OF_YEAR) - 1);
		double eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g)
				- 0.040849 * Math.sin(2 * g));
		double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
				+ 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
		double la = Math.toRadians(lat);
		double x = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
		if (x >= 1) return true; // kutup gecesi
		if (x <= -1) return false; // kutup gündüzü
		double ha = Math.toDegrees(Math.acos(x));
		double rise = 720 - 4 * (lon + ha) - eq;
		double set = 720 - 4 * (lon - ha) - eq;
		double now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
				+ c.get(java.util.Calendar.SECOND) / 60.0;
		// UTC dakikaları gün sınırını aşabilir: gün doğumundan itibaren geçen süreye göre karşılaştır
		double sinceRise = ((now - rise) % 1440 + 1440) % 1440;
		double dayLen = ((set - rise) % 1440 + 1440) % 1440;
		return sinceRise >= dayLen;
	}

	public static Theme current(Context ctx) {
		return THEMES[index(ctx)];
	}

	/** Sıradaki tema. Otomatikteyse şu anki dilimin (gündüz / gece) teması değişir. */
	public static void next(Context ctx) {
		int n = Math.floorMod(index(ctx) + 1, THEMES.length);
		if (isAuto(ctx)) {
			if (isNight(ctx)) setNightTheme(ctx, n);
			else setDayTheme(ctx, n);
			return;
		}
		prefs(ctx).edit().putInt("theme", n).apply();
	}

	/** Tema adı ve otomatikse hangi dilimde olduğu: "Asil (gece, otomatik)". */
	public static String label(Context ctx) {
		String n = current(ctx).name;
		return isAuto(ctx) ? (n + " · otomatik " + (isNight(ctx) ? "gece" : "gündüz")) : n;
	}

	static float dp(Context ctx, float v) {
		return v * ctx.getResources().getDisplayMetrics().density;
	}

	// ---- cam yüzeyler ----

	/** Liquid Glass yüzey: berrak dolgu + üst ışık yansıması + ince parlak kenar. */
	public static Drawable glass(Context ctx, Theme t, float radiusDp) {
		return glass(ctx, radiusDp, 0x24FFFFFF, 0x12FFFFFF, 0x17FFFFFF, 0x33FFFFFF);
	}

	/** Vurgulu kutu (şartname: beyaz %12). */
	public static Drawable glassStrong(Context ctx, float radiusDp) {
		return glass(ctx, radiusDp, 0x2EFFFFFF, 0x1AFFFFFF, 0x1FFFFFFF, 0x40FFFFFF);
	}

	/** Birincil düğme: açık vurgu dolgu (#F5F7FF), üzerindeki ikon koyu (#0A0D18). */
	public static Drawable accentFill(Context ctx, float radiusDp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(ACCENT);
		g.setCornerRadius(dp(ctx, radiusDp));
		return g;
	}

	public static final int ACCENT = 0xFFF5F7FF;
	public static final int ON_ACCENT = 0xFF0A0D18;
	public static final int LIVE = 0xFFD93A3F;

	/** Alt menü ve mini oynatıcı için biraz daha belirgin cam. */
	public static Drawable pill(Context ctx, Theme t) {
		return glass(ctx, 999, 0x2EFFFFFF, 0x14FFFFFF, 0x1CFFFFFF, 0x40FFFFFF);
	}

	/** Dikey menü kipinde mi (araç): arama kutuları sesli düğme için sola boşluk bırakmaz. */
	public static volatile boolean railMode;

	/** Arama kutusunun soldan boşluğu (dp): telefonda sesli asistan düğmesinin sağında başlar. */
	/** Arama kutusunun sol boşluğu: araçta gövde zaten sol sütunun sağından başlıyor. */
	public static int searchLeft(Context ctx) {
		return sideRail(ctx) ? 16 : searchLeftDp();
	}

	/**
	 * Sesli ve ⌂ düğmeleri içeriğin solunda ayrı bir sütun mu: araçta ve yatay ekranda evet (gövde sağa kayar,
	 * düğmeler kanal kartlarının, menü satırlarının üstüne binmez). Dikey telefonda düğmeler içeriğin üstünde yüzer.
	 */
	public static boolean sideRail(Context ctx) {
		if (!DECK) return false;
		try {
			if (me.aap.fermata.ui.activity.MainActivityDelegate.get(ctx).isCarActivity()) return true;
		} catch (Throwable ignore) {
		}
		android.content.res.Configuration c = ctx.getResources().getConfiguration();
		return c.screenWidthDp > c.screenHeightDp;
	}

	/** Tüm sayfa başlıkları aynı ölçek (sp): araçta biraz küçük. */
	/**
	 * Açık / kapalı anahtarı: yuvarlak iz ve beyaz düğme; açıkken iz temanın vurgu renginde ve düğme sağda,
	 * kapalıyken soluk cam ve düğme solda. Yazı yok (yanındaki satır söyler); erişilebilirlik için açıklamalı.
	 */
	public static View toggleSwitch(Context ctx, boolean on, int accent) {
		float d = ctx.getResources().getDisplayMetrics().density;
		android.widget.FrameLayout track = new android.widget.FrameLayout(ctx);
		android.graphics.drawable.GradientDrawable tg = new android.graphics.drawable.GradientDrawable();
		tg.setCornerRadius(999 * d);
		tg.setColor(on ? accent : 0x26FFFFFF);
		tg.setStroke(Math.max(1, Math.round(d)), on ? 0x40FFFFFF : 0x59FFFFFF);
		track.setBackground(tg);
		View knob = new View(ctx);
		android.graphics.drawable.GradientDrawable kg = new android.graphics.drawable.GradientDrawable();
		kg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		// Açık renkli vurguda (beyaza yakın tema) beyaz düğme kaybolmasın: koyu düğme
		boolean lightAccent = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.6;
		kg.setColor(on ? (lightAccent ? 0xFF1F2937 : 0xFFFFFFFF) : 0xFFCBD1DC);
		knob.setBackground(kg);
		knob.setElevation(2 * d);
		int k = Math.round(24 * d);
		android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(k, k,
				android.view.Gravity.CENTER_VERTICAL | (on ? android.view.Gravity.END : android.view.Gravity.START));
		lp.leftMargin = lp.rightMargin = Math.round(4 * d);
		track.addView(knob, lp);
		track.setContentDescription(on ? "açık" : "kapalı");
		return track;
	}

	public static int titleSp(Context ctx) {
		try {
			if (me.aap.fermata.ui.activity.MainActivityDelegate.get(ctx).isCarActivity()) return 28;
		} catch (Throwable ignore) {
		}
		return 34;
	}

	/** Hafif dokunma titreşimi (sistemde dokunma geri bildirimi açıksa). */
	public static void haptic(@androidx.annotation.Nullable View v) {
		if (v == null) return;
		try {
			v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
		} catch (Throwable ignore) {
		}
	}

	/**
	 * Türkçe tarih ("Perşembe, 12 Kasım"): telefonun dili ne olursa olsun; dakikada bir yenilenir.
	 */
	public static android.widget.TextView turkishDate(Context ctx, String pattern) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(pattern, new java.util.Locale("tr", "TR"));
		android.widget.TextView tv = new android.widget.TextView(ctx) {
			private final Runnable tick = new Runnable() {
				@Override
				public void run() {
					String s = f.format(new java.util.Date());
					if (!s.isEmpty()) s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
					setText(s);
					postDelayed(this, 30_000);
				}
			};

			@Override
			protected void onAttachedToWindow() {
				super.onAttachedToWindow();
				removeCallbacks(tick);
				tick.run();
			}

			@Override
			protected void onDetachedFromWindow() {
				removeCallbacks(tick);
				super.onDetachedFromWindow();
			}
		};
		tv.setSingleLine(true);
		tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
		return tv;
	}

	/**
	 * Alttaki yüzen arama kutusu, sesli ve ⌂ düğmelerinin arkasında: içerik buraya doğru zemin rengine
	 * yumuşakça kaybolur (kanal adları kutunun altında karışık görünmesin).
	 */
	public static View bottomScrim(Context ctx, Theme t) {
		View v = new View(ctx);
		int c = t.bg1 | 0xFF000000;
		v.setBackground(new android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{c & 0x00FFFFFF, (c & 0x00FFFFFF) | 0xCC000000, c}));
		v.setClickable(false);
		v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		return v;
	}

	public static android.widget.FrameLayout.LayoutParams bottomScrimLp(Context ctx) {
		return new android.widget.FrameLayout.LayoutParams(-1, Math.round(dp(ctx, VOICE_DP + VOICE_BOTTOM_DP + 46)),
				android.view.Gravity.BOTTOM);
	}

	/**
	 * Kısa bilgi: ekranın altında beliren yazı, ~1 sn sonra kendiliğinden kaybolur (onay kutusu yok).
	 * Tema değişince ekran yeniden kurulsa da görünsün diye etkinliğin kök görünümüne eklenir.
	 */
	/** Testler için: son gösterilen bilgi yazısı ve zamanı. */
	public static volatile String lastToast;
	public static volatile long lastToastAt;

	public static void toast(Context ctx, String text) {
		// tema değişince ekran yeniden kuruluyor: yazı o bittikten sonra eklensin
		me.aap.utils.app.App.get().getHandler().postDelayed(() -> toastNow(ctx, text), 120);
	}

	private static void toastNow(Context ctx, String text) {
		try {
			me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(ctx);
			View host = a.findViewById(me.aap.fermata.R.id.main_activity);
			if (!(host instanceof androidx.constraintlayout.widget.ConstraintLayout cl)) return;
			android.widget.TextView t = new android.widget.TextView(ctx);
			t.setText(text);
			t.setTextSize(16);
			t.setTextColor(0xFFFFFFFF);
			t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			t.setGravity(android.view.Gravity.CENTER);
			t.setMaxLines(2);
			float d = ctx.getResources().getDisplayMetrics().density;
			t.setPadding(Math.round(22 * d), Math.round(12 * d), Math.round(22 * d), Math.round(12 * d));
			android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
			g.setCornerRadius(999 * d);
			g.setColor(0xE6181B24);
			g.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
			t.setBackground(g);
			t.setElevation(60 * d);
			androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
					new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(-2, -2);
			lp.startToStart = lp.endToEnd = lp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
			lp.bottomMargin = Math.round((VOICE_DP + VOICE_BOTTOM_DP + 16) * d);
			t.setTag("yolda_toast");
			for (int i = cl.getChildCount() - 1; i >= 0; i--) { // önceki yazı varsa hemen kalksın
				View c = cl.getChildAt(i);
				if ("yolda_toast".equals(c.getTag())) cl.removeView(c);
			}
			cl.addView(t, lp);
			t.setAlpha(0f);
			t.setTranslationY(12 * d);
			t.animate().alpha(1f).translationY(0).setDuration(150).withEndAction(() ->
					t.animate().alpha(0f).setStartDelay(1000).setDuration(220).withEndAction(() -> cl.removeView(t)).start()).start();
			android.util.Log.i("Yolda", "Bilgi yazısı: " + text);
			lastToast = text;
			lastToastAt = android.os.SystemClock.elapsedRealtime();
		} catch (Throwable ex) {
			android.util.Log.i("Yolda", "Bilgi yazısı gösterilemedi: " + ex);
		}
	}

	public static int searchLeftDp() {
		if (DECK) return 16 + VOICE_DP + 12; // V2: sesli düğmenin sağında, aynı hizada (araçta gövde zaten kaydırılmış)
		return railMode ? 18 : 22; // sesli asistan artık alt menünün yanında
	}

	/** Menüde seçili düğme: düğmenin ortasında sabit boyutlu, yumuşak parlak cam (genişleyip uzamaz). */
	public static Drawable centeredHighlight(Context ctx, float wDp, float hDp, float radiusDp) {
		float d = ctx.getResources().getDisplayMetrics().density;
		final float w = wDp * d, h = hDp * d, r = radiusDp * d;
		return new Drawable() {
			private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
			private final android.graphics.Paint edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
			private final android.graphics.RectF rect = new android.graphics.RectF();

			{
				edge.setStyle(android.graphics.Paint.Style.STROKE);
				edge.setStrokeWidth(Math.max(1f, d));
				edge.setColor(0x8CFFFFFF);
			}

			@Override
			public void draw(@androidx.annotation.NonNull android.graphics.Canvas c) {
				android.graphics.Rect b = getBounds();
				float cw = Math.min(w, b.width()), ch = Math.min(h, b.height());
				rect.set(b.exactCenterX() - cw / 2, b.exactCenterY() - ch / 2, b.exactCenterX() + cw / 2,
						b.exactCenterY() + ch / 2);
				fill.setShader(new android.graphics.LinearGradient(0, rect.top, 0, rect.bottom,
						0x40FFFFFF, 0x30FFFFFF, android.graphics.Shader.TileMode.CLAMP));
				edge.setShader(new android.graphics.LinearGradient(0, rect.top, 0, rect.bottom,
						0x80FFFFFF, 0x0FFFFFFF, android.graphics.Shader.TileMode.CLAMP));
				float rr = Math.min(r, ch / 2);
				c.drawRoundRect(rect, rr, rr, fill);
				c.drawRoundRect(rect, rr, rr, edge);
			}

			@Override
			public void setAlpha(int alpha) {
				fill.setAlpha(alpha);
			}

			@Override
			public void setColorFilter(@androidx.annotation.Nullable android.graphics.ColorFilter cf) {
				fill.setColorFilter(cf);
			}

			@Override
			public int getOpacity() {
				return android.graphics.PixelFormat.TRANSLUCENT;
			}
		};
	}

	/** Arka plan ışıltısının o anki rengi (sıvı cam kutular bu tona bürünür). */
	public static volatile int glowTint = 0xFF7C3AED;

	/**
	 * V2 sıvı cam kapsül (arama kutuları, tarayıcı adres kapsülü): koyu ama yarı saydam taban, ışıltının
	 * renginde ton, üstte sönen parıltı, ince parlak kenar; odaklanınca kenar vurgu renginde parlar.
	 * Gerçek bulanıklık yerine renk ve ışıkla taklit edilir (araçta akıcı kalsın).
	 */
	public static Drawable liquid(Context ctx, Theme t) {
		android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
		s.addState(new int[]{android.R.attr.state_focused}, liquidLayer(ctx, t, true));
		s.addState(new int[]{}, liquidLayer(ctx, t, false));
		return s;
	}

	private static Drawable liquidLayer(Context ctx, Theme t, boolean focused) {
		float r = dp(ctx, 999);
		int tint = glowTint;
		GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{(t.base & 0x00FFFFFF) | 0xA6000000, (t.base & 0x00FFFFFF) | 0xC7000000});
		base.setCornerRadius(r);
		GradientDrawable tone = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{(tint & 0x00FFFFFF) | 0x59000000, (tint & 0x00FFFFFF) | 0x1A000000});
		tone.setCornerRadius(r);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x3DFFFFFF, 0x0DFFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		GradientDrawable rim = new GradientDrawable();
		rim.setCornerRadius(r);
		rim.setStroke(Math.max(1, Math.round(dp(ctx, focused ? 2 : 1))), focused ? (ACCENT & 0x00FFFFFF) | 0xE6000000 : 0x8CFFFFFF);
		LayerDrawable l = new LayerDrawable(new Drawable[]{base, tone, sheen, rim});
		l.setLayerInset(2, 0, 0, 0, Math.round(dp(ctx, 22)));
		return l;
	}

	/** Arama kutusu: fazla saydam olmasın; koyu tema rengi dolgu, üstte cam parlaması, belirgin kenar. */
	public static Drawable searchField(Context ctx, Theme t) {
		if (DECK) return liquid(ctx, t);
		float r = dp(ctx, 999);
		GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{(t.base & 0x00FFFFFF) | 0xEB000000, (t.base & 0x00FFFFFF) | 0xF5000000});
		fill.setCornerRadius(r);
		fill.setStroke(Math.max(1, Math.round(dp(ctx, 1))), 0x8CFFFFFF);
		return new android.graphics.drawable.LayerDrawable(new Drawable[]{fill,
				glass(ctx, 999, 0x2EFFFFFF, 0x0DFFFFFF, 0x14FFFFFF, 0x00FFFFFF)});
	}

	/** Seçili öğe (menüdeki aktif sekme, seçili çip): dolgun cam. */
	public static Drawable selected(Context ctx, float radiusDp) {
		return glass(ctx, radiusDp, 0x5CFFFFFF, 0x33FFFFFF, 0x40FFFFFF, 0x99FFFFFF);
	}

	private static Drawable glass(Context ctx, float radiusDp, int c1, int c2, int c3, int edge) {
		float r = dp(ctx, radiusDp);
		GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2, c3});
		fill.setCornerRadius(r);
		// Üst yarıda yumuşak ışık (camın üst kenarından düşen yansıma)
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		GradientDrawable border = new GradientDrawable();
		border.setColor(0x00000000);
		border.setCornerRadius(r);
		border.setStroke(Math.max(1, Math.round(dp(ctx, 1))), edge);
		return new LayerDrawable(new Drawable[]{fill, sheen, border});
	}

	/** Büyük vurgulu kart: temanın ilk iki ışığının geçişi + cam parıltısı. */
	public static Drawable hero(Context ctx, Theme t, float radiusDp) {
		float r = dp(ctx, radiusDp);
		GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.hero1, t.hero2});
		body.setCornerRadius(r);
		body.setStroke(Math.max(1, Math.round(dp(ctx, 1))), 0x73FFFFFF);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x4DFFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		return new LayerDrawable(new Drawable[]{body, sheen});
	}

	/** Uygulama simgesi zemini (iOS gibi yuvarlatılmış kare): renk geçişi + üst parlama + ince kenar. */
	public static Drawable appIcon(Context ctx, int c1, int c2, float radiusDp) {
		float r = dp(ctx, radiusDp);
		GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
		body.setCornerRadius(r);
		GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[]{0x40FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
		sheen.setCornerRadius(r);
		GradientDrawable border = new GradientDrawable();
		border.setColor(0x00000000);
		border.setCornerRadius(r);
		border.setStroke(Math.max(1, Math.round(dp(ctx, 1))), 0x66FFFFFF);
		return new LayerDrawable(new Drawable[]{body, sheen, border});
	}

	public static int onHero(Theme t) {
		return 0xFFFFFFFF;
	}

	public static int pastel(int i) {
		int[] c = {0xFFF4A7A7, 0xFFB8A9F5, 0xFF9ED9C5, 0xFF9CC9F5, 0xFFF6CFA0, 0xFFE9B3D6, 0xFFAFC0D9};
		return c[Math.floorMod(i, c.length)];
	}

	// ---- zemin ----

	/** Kök görünüme süzülen ışıklı zemini çizer (tüm uygulamanın arkası). */
	public static synchronized void applyBackdrop(View root) {
		backdropHosts.removeIf(r -> (r.get() == null) || (r.get() == root));
		backdropHosts.add(new WeakReference<>(root));
		// V2: kart renginde ışıltılı zemin tüm ekranda (kenar payları dahil)
		root.setBackground(DECK ? new YoldaGlow(current(root.getContext())) : new BackdropDrawable(current(root.getContext())));
	}

	/** Tema değişince ortak zemini yenile (önceden yalnız en son açılan ekranınki yenileniyordu). */
	public static synchronized void refreshBackdrop(Context ctx) {
		backdropHosts.removeIf(r -> r.get() == null);
		for (WeakReference<View> r : backdropHosts) {
			View v = r.get();
			if (v == null) continue;
			if (DECK) {
				YoldaGlow old = YoldaGlow.of(v);
				YoldaGlow g = new YoldaGlow(current(ctx));
				if (old != null) g.setColors(old.colors()[0], old.colors()[1]);
				v.setBackground(g);
			} else {
				v.setBackground(new BackdropDrawable(current(ctx)));
			}
		}
	}

	/** Yolda ekranının zemini: ortak zemin varsa saydam, yoksa kendi ışıklı zemini. */
	public static void applyScreenBackground(FrameLayout root, Theme t) {
		if (glassActive) root.setBackground(null);
		else root.addView(new Backdrop(root.getContext(), t), 0, new FrameLayout.LayoutParams(-1, -1));
	}

	/** Duvar kâğıdı: taban + dört ışık; ışıklar çok yavaş süzülür. */
	static void drawLights(Canvas c, Theme t, Paint p, float w, float h, float s) {
		if (w <= 0 || h <= 0) return;
		c.drawColor(t.base);
		// Konum (genişlik/yükseklik oranı), yarıçap (genişliğe oranla), saydamlık — tasarım taslağıyla aynı
		float[][] pos = {{0.10f, 0.12f, 0.42f, 0.80f}, {0.88f, 0.18f, 0.44f, 0.73f},
				{0.62f, 0.92f, 0.50f, 0.67f}, {0.20f, 0.95f, 0.55f, 0.93f}};
		for (int i = 0; i < t.blobs.length && i < pos.length; i++) {
			float dx = (float) Math.sin(s * 0.06 + i * 2.1) * 0.05f * w;
			float dy = (float) Math.cos(s * 0.045 + i * 1.7) * 0.05f * h;
			float cx = pos[i][0] * w + dx, cy = pos[i][1] * h + dy, r = pos[i][2] * Math.max(w, h);
			int col = t.blobs[i];
			int a = Math.round(pos[i][3] * 255);
			p.setShader(new RadialGradient(cx, cy, r,
					new int[]{Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)),
							Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))},
					null, Shader.TileMode.CLAMP));
			c.drawCircle(cx, cy, r, p);
		}
	}

	/**
	 * Zemini bir kez yarım çözünürlükte bitmap'e çizer, sonra yalnız onu kopyalar. Işıklar zaten yumuşak
	 * geçişli olduğu için yarım çözünürlük fark edilmez; her karede gradyan hesaplanmaz → kasma yok.
	 */
	private static android.graphics.Bitmap render(Theme t, int w, int h) {
		int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
		android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
		drawLights(new Canvas(bmp), t, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), bw, bh, 0f);
		return bmp;
	}

	/** Zemin (View olarak): Fermata teması Yolda Cam değilse Yolda ekranları bunu kendi arkasına koyar. */
	public static final class Backdrop extends View {
		private final Theme t;
		private final Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
		private android.graphics.Bitmap cache;
		private final Rect dst = new Rect();

		public Backdrop(Context ctx, Theme t) {
			super(ctx);
			this.t = t;
		}

		@Override
		protected void onDraw(Canvas c) {
			int w = getWidth(), h = getHeight();
			if (w <= 0 || h <= 0) return;
			if ((cache == null) || (cache.getWidth() != Math.max(1, w / 2)) || (cache.getHeight() != Math.max(1, h / 2)))
				cache = render(t, w, h);
			dst.set(0, 0, w, h);
			c.drawBitmap(cache, null, dst, fp);
		}
	}

	/** Zemin (arka plan olarak): boyut değişmedikçe yeniden çizilmez. */
	public static final class BackdropDrawable extends Drawable implements Runnable {
		private final Theme t;
		private final Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
		private android.graphics.Bitmap cache;

		BackdropDrawable(Theme t) {
			this.t = t;
		}

		@Override
		public void draw(Canvas c) {
			Rect b = getBounds();
			int w = b.width(), h = b.height();
			if (w <= 0 || h <= 0) return;
			if ((cache == null) || (cache.getWidth() != Math.max(1, w / 2)) || (cache.getHeight() != Math.max(1, h / 2)))
				cache = render(t, w, h);
			c.drawBitmap(cache, null, b, fp);
		}

		@Override
		public void run() {
			invalidateSelf();
		}

		@Override
		public void setAlpha(int alpha) {
		}

		@Override
		public void setColorFilter(android.graphics.ColorFilter cf) {
		}

		@Override
		public int getOpacity() {
			return PixelFormat.OPAQUE;
		}
	}

	// ---- duyarlı ölçüler: dar telefon / orta / geniş (araç) ekran ----
	public static int widthDp(android.content.Context c) {
		return c.getResources().getConfiguration().screenWidthDp;
	}

	public static int heightDp(android.content.Context c) {
		return c.getResources().getConfiguration().screenHeightDp;
	}

	/** Dikey telefon gibi dar ekran. */
	public static boolean compact(android.content.Context c) {
		return widthDp(c) < 600;
	}

	/** Araç ekranı gibi geniş ama alçak ekran. */
	public static boolean shortScreen(android.content.Context c) {
		return heightDp(c) < 520;
	}

	/** Sayfa yan boşluğu (dp). */
	public static int side(android.content.Context c) {
		int w = widthDp(c);
		return (w < 600) ? 18 : (w < 900) ? 28 : 44;
	}

	/** Sayfa üst boşluğu (dp). */
	/** Tarayıcı üst çubuğu gizli mi (YouTube Music tam ekran açılır; "Tarayıcı" kartında çubuk görünür). */
	public static volatile boolean webNoToolbar;

	/** Yazı ölçeği: alçak geniş ekranda (araç, yatay telefon) yazılar daha küçük; yer kaplamasın. */
	public static float textScale(android.content.Context c) {
		if (shortScreen(c)) return 0.82f; // şartname: araçta yazı en az ~15 sp
		return 1f;
	}

	/** Kamera çentiğinin üstten yüksekliği (px); tam ekran katmanlar içeriği bunun altına koyar. */
	public static volatile int cutoutTopPx;

	public static int top(android.content.Context c) {
		return (compact(c) || shortScreen(c)) ? 16 : 28;
	}

	/** Alt menü + sesli asistan düğmesinin altında içerik kalmasın diye alt boşluk (dp). */
	public static int bottom(android.content.Context c) {
		return 100;
	}

	// ---- menüler ve diyaloglar: koyu cam panel, büyük dokunma alanları ----

	/** Açılan menü / diyalog paneli: okunaklı olsun diye koyu, üstünde cam parlaması. */
	public static Drawable menuPanel(Context ctx) {
		Theme t = current(ctx);
		float d = ctx.getResources().getDisplayMetrics().density;
		android.graphics.drawable.GradientDrawable solid = new android.graphics.drawable.GradientDrawable();
		solid.setColor((t.base & 0x00FFFFFF) | 0xF2000000);
		solid.setCornerRadius(28 * d);
		solid.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
		return new android.graphics.drawable.LayerDrawable(new Drawable[]{solid, glass(ctx, t, 28)});
	}

	/** Basılı / seçili / odaklı satırda yumuşak cam hap, normalde saydam. */
	private static Drawable rowBackground(Context ctx) {
		float d = ctx.getResources().getDisplayMetrics().density;
		android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
		int in = Math.round(2 * d);
		Drawable on = new android.graphics.drawable.InsetDrawable(selected(ctx, 18), in, in, in, in);
		sl.addState(new int[]{android.R.attr.state_pressed}, on);
		sl.addState(new int[]{android.R.attr.state_focused}, on);
		sl.addState(new int[]{android.R.attr.state_selected}, on);
		sl.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
		return sl;
	}

	public static void styleMenu(me.aap.utils.ui.menu.OverlayMenuView m) {
		if (!glassActive) return;
		Context ctx = m.getContext();
		float d = ctx.getResources().getDisplayMetrics().density;
		m.setBackground(menuPanel(ctx));
		m.setClipToOutline(true);
		m.setElevation(24 * d);
		int p = Math.round(10 * d);
		m.setPadding(p, p, p, p);
		int screenW = Math.round(widthDp(ctx) * d);
		m.setMinimumWidth(Math.min(Math.round(320 * d), screenW - Math.round(32 * d)));
		if (m.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams lp) {
			int mg = Math.round(16 * d);
			if (lp.leftMargin != mg) {
				lp.setMargins(mg, mg, mg, mg);
				m.setLayoutParams(lp);
			}
		}
		if (m.getChildCount() > 0) styleTree(ctx, m.getChildAt(0), d);
	}

	private static void styleTree(Context ctx, android.view.View v, float d) {
		if (v instanceof me.aap.utils.ui.menu.OverlayMenuItemView item) {
			item.setMinHeight(Math.round(56 * d));
			item.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
			item.setPadding(Math.round(18 * d), Math.round(6 * d), Math.round(18 * d), Math.round(6 * d));
			item.setCompoundDrawablePadding(Math.round(16 * d));
			item.setTextColor(0xFFFFFFFF);
			item.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
			androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(item,
					android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
			item.setBackground(rowBackground(ctx));
			return;
		}
		if (v instanceof android.widget.EditText e) {
			e.setTextColor(0xFFFFFFFF);
			e.setHintTextColor(0x99FFFFFF);
			return;
		}
		if (v instanceof android.widget.Button b) {
			// Diyalog düğmeleri: büyük, yuvarlak cam
			b.setTextColor(0xFFFFFFFF);
			b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
			b.setAllCaps(false);
			b.setMinHeight(Math.round(52 * d));
			b.setMinWidth(Math.round(96 * d));
			b.setPadding(Math.round(20 * d), 0, Math.round(20 * d), 0);
			b.setBackground(new android.graphics.drawable.InsetDrawable(glass(ctx, current(ctx), 999),
					Math.round(4 * d)));
			return;
		}
		if (v instanceof android.widget.TextView t) {
			if (t.getId() == me.aap.utils.R.id.overlay_menu_title) {
				t.setTextColor(0xFFFFFFFF);
				t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 19);
				t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			} else if (!(t instanceof android.widget.CompoundButton)) {
				t.setTextColor(0xF2FFFFFF);
			} else {
				t.setTextColor(0xFFFFFFFF);
				t.setMinHeight(Math.round(52 * d));
			}
			return;
		}
		if (v instanceof android.view.ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) styleTree(ctx, g.getChildAt(i), d);
		}
	}
}
