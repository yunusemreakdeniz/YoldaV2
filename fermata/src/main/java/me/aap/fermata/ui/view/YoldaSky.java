package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import me.aap.fermata.ui.fragment.YoldaRadar;
import me.aap.fermata.ui.fragment.YoldaWeather;

/**
 * Hava durumuna göre zemin: tema ışıltısının üstüne hafif bir sahne çizer. Açık gündüz dönen soluk ışınlarıyla
 * güneş, açık gece parıldayan yıldızlar ve ara sıra kayan yıldız, bulutlu / kapalı süzülen bulutlar, sis yatay pus,
 * yağmur iki derinlikte (uzak ince, yakın kalın) parlak başlı damlalar, kar üç derinlikte salınan taneler, fırtına
 * yağmur ve yumuşak sönen şimşek. Sahne yazıyı bastırmasın diye düşük saydamlıkta.
 * <p>
 * Akıcılık: hareketli sahneler (damla, kar, yıldız) saniyede ~30 kare çizilir; ışık geçişleri her karede yeniden
 * kurulmaz (bir kez kurulur, matrisle taşınır), çizimde nesne üretilmez → çöp toplayıcı takılması yok. Araçta ve
 * sistem animasyonları kapalıyken sahne kımıldamadan çizilir.
 */
public final class YoldaSky {
	public static final int NONE = 0, SUN = 1, STARS = 2, CLOUDS = 3, FOG = 4, RAIN = 5, SNOW = 6, STORM = 7;
	public static final String PREF = "weather_bg";

	/** Kullanıcı ayarı (Tema seçicide "Hava efekti"): kapalı değilse true. */
	public static volatile boolean enabled = true;
	/** Efekt kipi: "auto" (havaya göre), "off" ya da sabit sahne ("rain", "snow"...). */
	public static volatile String mode = "auto";
	public static final String PREF_MODE = "weather_fx";
	/** Tema seçicideki seçenekler: {kip, ad}. */
	public static final String[][] MODES = {{"auto", "Otomatik"}, {"off", "Kapalı"}, {"sun", "Güneş"}, {"stars", "Yıldızlı gece"},
			{"clouds", "Bulut"}, {"fog", "Sis"}, {"rain", "Yağmur"}, {"snow", "Kar"}, {"storm", "Fırtına"}};

	/** Kipe göre sahne: otomatikte havanınki, kapalıda yok, sabitte seçilen. */
	public static int modeScene(String m, int weatherScene) {
		return switch ((m == null) ? "auto" : m) {
			case "off" -> NONE;
			case "sun" -> SUN;
			case "stars" -> STARS;
			case "clouds" -> CLOUDS;
			case "fog" -> FOG;
			case "rain" -> RAIN;
			case "snow" -> SNOW;
			case "storm" -> STORM;
			default -> weatherScene;
		};
	}

	public static String modeLabel(String m) {
		for (String[] x : MODES) if (x[0].equals(m)) return x[1];
		return "Otomatik";
	}

	public static void setMode(Context ctx, String m) {
		mode = (m == null) ? "auto" : m;
		enabled = !"off".equals(mode);
		ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
				.putString(PREF_MODE, mode).putBoolean(PREF, enabled).apply();
	}
	/** Testler ve önizleme için zorla sahne (-1: havadan). */
	public static volatile int forced = -1;

	private YoldaSky() {
	}

	/** WMO hava kodu ve gece / gündüz → sahne. Bilinmeyen kod (−1) sahnesiz. */
	public static int scene(int code, boolean night) {
		if (code < 0) return NONE;
		if (code <= 1) return night ? STARS : SUN;
		if (code <= 3) return CLOUDS;
		if (code <= 48) return FOG;
		if (code <= 67) return RAIN;
		if (code <= 77) return SNOW;
		if (code <= 82) return RAIN;
		if (code <= 86) return SNOW;
		return STORM; // 95-99 gök gürültülü
	}

	/** Sahnenin Türkçe adı (ayarda gösterilir). */
	public static String label(int s) {
		return switch (s) {
			case SUN -> "güneşli";
			case STARS -> "yıldızlı gece";
			case CLOUDS -> "bulutlu";
			case FOG -> "sisli";
			case RAIN -> "yağmurlu";
			case SNOW -> "karlı";
			case STORM -> "fırtınalı";
			default -> "sade";
		};
	}

	/** Hızlı hareket eden sahne mi (saniyede ~30 kare gerekir); yavaş olanlar ~12 karede de akıcı. */
	public static boolean fast(int s) {
		return (s == RAIN) || (s == SNOW) || (s == STORM) || (s == STARS);
	}

	public static void load(Context ctx) {
		android.content.SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
		String m = p.getString(PREF_MODE, null);
		if (m == null) m = p.getBoolean(PREF, true) ? "auto" : "off"; // eski aç / kapa ayarı
		mode = m;
		enabled = !"off".equals(m);
	}

	public static void setEnabled(Context ctx, boolean on) {
		setMode(ctx, on ? "auto" : "off");
	}

	/** Gece mi: konum biliniyorsa gün batımına göre, yoksa saat 19-06. */
	static boolean night(long now) {
		double la = YoldaRadar.lat, lo = YoldaRadar.lon;
		if (!Double.isNaN(la) && !Double.isNaN(lo)) return YoldaStyle.sunDown(now, la, lo);
		int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
		return (h >= 19) || (h < 6);
	}

	/** Şu anki sahne (hava bilinmiyorsa sahnesiz). */
	public static int current() {
		if (forced >= 0) return forced;
		String m = mode;
		if ("off".equals(m)) return NONE;
		if (!"auto".equals(m)) return modeScene(m, NONE); // kullanıcının seçtiği efekt, hava ne olursa olsun
		YoldaWeather.Now n = YoldaWeather.cachedNow();
		return (n == null) ? NONE : scene(n.code(), night(System.currentTimeMillis()));
	}

	/** Parçacık sayısı: alanla ölçekli, üst sınırlı (küçük ekranda kalabalık, büyükte seyrek olmasın). */
	public static int particles(int scene, float w, float h) {
		float k = Math.min(1f, (w * h) / (1080f * 1920f));
		int max = switch (scene) {
			case RAIN, STORM -> 70;
			case SNOW -> 46;
			case STARS -> 38;
			default -> 0;
		};
		return Math.max((max > 0) ? 12 : 0, Math.round(max * Math.max(0.35f, k)));
	}

	/** 0..1 sözde rastgele (aynı i için hep aynı): parçacıkların yeri her karede sabit kalsın. */
	public static float rnd(int i, int salt) {
		int x = i * 374761393 + salt * 668265263;
		x = (x ^ (x >>> 13)) * 1274126177;
		x ^= (x >>> 16);
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}

	/** 0..1 → yumuşak çan (şimşek ve kayan yıldızın sönmesi için). */
	public static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3 - 2 * t);
	}

	// ---- çizim (ana iş parçacığında; yeniden kullanılan nesneler) ----

	private static final Matrix gm = new Matrix();
	private static final Path ray = new Path();
	private static final java.util.Map<Long, RadialGradient> glows = new java.util.HashMap<>();

	/** Birim yarıçaplı ışık (renk ve saydamlık başına bir kez kurulur); matrisle yerine ve boyuna taşınır. */
	/** Renk önbellek anahtarı: kanal başına 32 basamak (renk geçişinde her ara renk için yeni ışık kurulmasın). */
	public static int quantize(int color) {
		return color & 0x00F8F8F8;
	}

	private static RadialGradient unitGlow(int color, int a) {
		color = quantize(color) | 0xFF000000;
		long k = ((long) (color & 0x00FFFFFF) << 8) | (a & 0xFF);
		RadialGradient g = glows.get(k);
		if (g == null) {
			if (glows.size() > 256) glows.clear();
			g = new RadialGradient(0, 0, 1, new int[]{(color & 0x00FFFFFF) | (a << 24), (color & 0x00FFFFFF) | ((a / 3) << 24),
					color & 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
			glows.put(k, g);
		}
		return g;
	}

	static void glow(Canvas c, Paint p, float x, float y, float r, int color, int a) {
		r = Math.max(1f, r);
		RadialGradient g = unitGlow(color, a);
		gm.setScale(r, r);
		gm.postTranslate(x, y);
		g.setLocalMatrix(gm);
		p.setStyle(Paint.Style.FILL);
		p.setShader(g);
		c.drawCircle(x, y, r, p);
		p.setShader(null);
	}

	private static final android.graphics.RectF oval = new android.graphics.RectF();

	/** Elips biçimli yumuşak ışık (sis bandı, bulut öbeği): aynı önbellekli ışık, yatay / dikey ayrı ölçek. */
	static void glowOval(Canvas c, Paint p, float x, float y, float rx, float ry, int color, int a) {
		rx = Math.max(1f, rx);
		ry = Math.max(1f, ry);
		RadialGradient g = unitGlow(color, a);
		gm.setScale(rx, ry);
		gm.postTranslate(x, y);
		g.setLocalMatrix(gm);
		p.setStyle(Paint.Style.FILL);
		p.setShader(g);
		oval.set(x - rx, y - ry, x + rx, y + ry);
		c.drawOval(oval, p);
		p.setShader(null);
	}

	/** Saniyede kaç kare (ms aralık): damla / kar / yıldız 30, bulut / sis 20, güneş ve sahnesiz 12. */
	public static long frameMs(int s) {
		if (fast(s)) return 33;
		return ((s == CLOUDS) || (s == FOG)) ? 50 : 83; // sakin güneş ~12 kare yeter
	}

	private static long nightAt;
	private static boolean nightNow;

	/** Gece mi (dakikada bir hesaplanır; her karede değil). */
	private static boolean nightCached() {
		long now = System.currentTimeMillis();
		if (now - nightAt > 60_000L) {
			nightAt = now;
			nightNow = night(now);
		}
		return nightNow;
	}

	/** Bir bulut: 7 yumuşak öbek (ortası kabarık, altı düz), üstte hafif ışık. */
	private static void cloud(Canvas c, Paint p, float x, float y, float size, int color, int a, boolean lit) {
		for (int k = 0; k < 7; k++) {
			float ox = (k - 3) * 0.32f * size, oy = -(float) Math.cos((k - 3) / 3.0 * Math.PI / 2) * 0.28f * size;
			float r = size * (0.42f + 0.18f * rnd(k, 51)) * (1f - Math.abs(k - 3) * 0.08f);
			glowOval(c, p, x + ox, y + oy, r * 1.15f, r * 0.85f, color, a);
		}
		if (lit) glowOval(c, p, x, y - size * 0.3f, size * 0.9f, size * 0.35f, 0xFFFFFFFF, a / 2); // üst kenarda ışık
	}

	/** Saydamlık 16'lık basamaklara (ışık önbelleği küçük kalsın). */
	static int qa(float a) {
		return Math.max(0, Math.min(255, Math.round(a / 16f) * 16));
	}

	private static final Path bolt = new Path();

	/** Dallanan şimşek: k. çakış için hep aynı biçim (yukarıdan aşağı, kırık çizgi ve iki dal). */
	private static void lightning(Canvas c, Paint p, Rect bd, int k, float strength, float u) {
		float w = bd.width(), h = bd.height();
		float x = bd.left + w * (0.18f + 0.64f * rnd(k, 91)), y = bd.top - u * 4;
		float end = bd.top + h * (0.42f + 0.18f * rnd(k, 92)), step = (end - y) / 11f;
		bolt.rewind();
		bolt.moveTo(x, y);
		float bx1 = 0, by1 = 0, bx2 = 0, by2 = 0;
		for (int j = 1; j <= 11; j++) {
			x += (rnd(k * 17 + j, 93) - 0.5f) * w * 0.07f;
			y += step;
			bolt.lineTo(x, y);
			if (j == 3) {
				bx1 = x;
				by1 = y;
			} else if (j == 6) {
				bx2 = x;
				by2 = y;
			}
		}
		for (int b = 0; b < 2; b++) { // iki dal: ana gövdeden yana inen kısa kırık çizgi
			float dx = ((b == 0) ? -1f : 1f) * w * 0.03f, xx = (b == 0) ? bx1 : bx2, yy = (b == 0) ? by1 : by2;
			bolt.moveTo(xx, yy);
			for (int j = 1; j <= 4; j++) {
				xx += dx + (rnd(k * 29 + j + b * 7, 94) - 0.5f) * w * 0.04f;
				yy += step * 0.8f;
				bolt.lineTo(xx, yy);
			}
		}
		p.setShader(null);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeJoin(Paint.Join.ROUND);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setColor(((int) (60 * strength) << 24) | 0xB8C8FF); // dış parıltı
		p.setStrokeWidth(u * 7f);
		c.drawPath(bolt, p);
		p.setColor(((int) (120 * strength) << 24) | 0xDDE6FF);
		p.setStrokeWidth(u * 3f);
		c.drawPath(bolt, p);
		p.setColor(((int) (250 * strength) << 24) | 0xFFFFFF); // parlak çekirdek
		p.setStrokeWidth(Math.max(1f, u * 1.1f));
		c.drawPath(bolt, p);
		p.setStyle(Paint.Style.FILL);
	}

	/** Camdaki su damlası: yumuşak gölge, saydam gövde, ince kenar, sol üstte parlak yansıma, altta ışık kırılması. */
	private static void droplet(Canvas c, Paint p, float x, float y, float r, float u) {
		glow(c, p, x + r * 0.15f, y + r * 0.25f, r * 1.5f, 0xFF050A14, 0x30);
		p.setShader(null);
		p.setStyle(Paint.Style.FILL);
		p.setColor(0x1AFFFFFF);
		c.drawCircle(x, y, r, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1f, u * 0.5f));
		p.setColor(0x47FFFFFF);
		c.drawCircle(x, y, r, p);
		p.setStyle(Paint.Style.FILL);
		p.setColor(0xC8FFFFFF);
		c.drawCircle(x - r * 0.35f, y - r * 0.38f, Math.max(u * 0.5f, r * 0.24f), p);
		p.setColor(0x40DCEBFF);
		c.drawCircle(x + r * 0.25f, y + r * 0.45f, r * 0.3f, p);
	}

	/** Sahneyi çiz; s = saniye (sabitken 0). Animasyon gerekiyorsa true. */
	static boolean draw(Canvas c, Paint p, Rect bd, int scene, float s) {
		float w = bd.width(), h = bd.height();
		if ((scene == NONE) || (w <= 0) || (h <= 0)) return false;
		float u = Math.min(w, h) / 400f; // ekrana göre birim
		switch (scene) {
			case SUN -> {
				float cx = bd.left + w * 0.84f, cy = bd.top + h * 0.07f, big = Math.max(w, h);
				// üstte ılık gökyüzü tonu (ekranın üst yarısı)
				glowOval(c, p, bd.left + w * 0.6f, bd.top, w * 1.1f, h * 0.55f, 0xFFFFB86B, 0x2E);
				// Sakin güneş (sık kullanılır, göz yormasın): dönme ve uçuşan zerre yok; hale çok yavaş nefes alır
				// (~12 sn), sabit ışınlar yalnız ışıkça ve çok yavaş (15-20 sn) belirip solar.
				float pulse = 1f + 0.02f * (float) Math.sin(s * 0.52);
				glow(c, p, cx, cy, big * 0.6f * pulse, 0xFFFFD27A, 0x50); // geniş hale
				p.setStyle(Paint.Style.FILL);
				for (int i = 0; i < 8; i++) { // sabit, geniş ve soluk ışınlar
					double a0 = Math.PI * 0.55 + i * (Math.PI * 2 / 8) + 0.15 * rnd(i, 31), da = 0.06 + 0.03 * rnd(i, 32);
					float len = big * (0.5f + 0.2f * rnd(i, 33));
					float br = 0.5f + 0.5f * (float) Math.sin(s * (0.31f + 0.1f * rnd(i, 34)) + i * 1.9f);
					p.setColor(((int) (8 + 9 * br) << 24) | 0xFFF1C2);
					ray.rewind();
					ray.moveTo(cx, cy);
					ray.lineTo(cx + (float) Math.cos(a0 - da) * len, cy + (float) Math.sin(a0 - da) * len);
					ray.lineTo(cx + (float) Math.cos(a0 + da) * len, cy + (float) Math.sin(a0 + da) * len);
					ray.close();
					c.drawPath(ray, p);
				}
				glow(c, p, cx, cy, big * 0.13f, 0xFFFFF4D6, 0x90); // parlak güneş diski
				glow(c, p, cx, cy, big * 0.05f, 0xFFFFFFFF, 0xB8);
				// iki sabit, çok soluk mercek parlaması
				float fx = bd.left + w * 0.5f - cx, fy = bd.top + h * 0.55f - cy;
				glow(c, p, cx + fx * 0.55f, cy + fy * 0.55f, big * 0.025f, 0xFFB8E1FF, 0x24);
				glow(c, p, cx + fx * 0.9f, cy + fy * 0.9f, big * 0.05f, 0xFFFFC7A0, 0x1E);
				return true;
			}
			case STARS -> {
				// Samanyolu: çapraz uzanan çok soluk, yumuşak bant
				c.save();
				c.rotate(-28f, bd.left + w * 0.5f, bd.top + h * 0.3f);
				for (int i = 0; i < 5; i++)
					glowOval(c, p, bd.left + w * (-0.1f + i * 0.3f), bd.top + h * (0.3f + 0.03f * (rnd(i, 101) - 0.5f)),
							w * 0.32f, h * 0.06f, i % 2 == 0 ? 0xFFB8C4FF : 0xFFE6D8FF, 0x14);
				c.restore();
				glow(c, p, bd.left + w * 0.14f, bd.top + h * 0.08f, Math.min(w, h) * 0.30f, 0xFFDDE6FF, 0x2C); // ay ışığı
				int[] tone = {0xFFFFFF, 0xCFE0FF, 0xFFF0D6, 0xE6ECFF}; // yıldızların renk sıcaklığı
				int n = particles(scene, w, h);
				for (int i = 0; i < n; i++) {
					float tw = 0.5f + 0.5f * (float) Math.sin(s * (0.5f + 1.2f * rnd(i, 3)) + i * 1.7f); // yumuşak parıldama
					float x = bd.left + rnd(i, 1) * w, y = bd.top + rnd(i, 2) * h * 0.65f;
					boolean bright = rnd(i, 6) > 0.85f;
					float rr = u * (bright ? 1.2f + 0.6f * rnd(i, 5) : 0.45f + 0.7f * rnd(i, 5));
					int col = tone[(int) (rnd(i, 7) * tone.length) % tone.length];
					float a = (bright ? 170 : 60) + (bright ? 80 : 120) * tw * (0.5f + 0.5f * rnd(i, 4));
					if (bright) { // yumuşak hale ve dört kollu ışık kırınımı
						glow(c, p, x, y, rr * 5f, 0xFF000000 | col, qa(a * 0.28f));
						p.setShader(null);
						p.setStyle(Paint.Style.STROKE);
						p.setStrokeCap(Paint.Cap.ROUND);
						p.setStrokeWidth(Math.max(1f, u * 0.35f));
						p.setColor(((int) (a * 0.45f) << 24) | col);
						float L = rr * (5f + 2f * tw);
						c.drawLine(x - L, y, x + L, y, p);
						c.drawLine(x, y - L, x, y + L, p);
					}
					p.setShader(null);
					p.setStyle(Paint.Style.FILL);
					p.setColor(((int) Math.min(255, a) << 24) | col);
					c.drawCircle(x, y, rr, p);
				}
				// kayan yıldız: ~11 sn'de bir, 0.9 sn; kuyruk sönerek uzanır
				float cyc = s % 11f;
				if (cyc < 0.9f) {
					int k = (int) (s / 11f);
					float t = cyc / 0.9f, sx = bd.left + w * (0.15f + 0.6f * rnd(k, 41)), sy = bd.top + h * (0.05f + 0.2f * rnd(k, 42));
					float hx = sx + w * 0.35f * t, hy = sy + h * 0.12f * t, tail = w * 0.16f;
					float life = (float) Math.sin(Math.PI * t);
					p.setStyle(Paint.Style.STROKE);
					p.setStrokeCap(Paint.Cap.ROUND);
					for (int j = 0; j < 6; j++) { // baştan kuyruğa incelen ve sönen parçalar
						float f0 = j / 6f, f1 = (j + 1) / 6f;
						p.setStrokeWidth(Math.max(1f, u * 1.6f * (1f - f0)));
						p.setColor(((int) (220 * life * (1f - f0)) << 24) | 0xEEF4FF);
						c.drawLine(hx - tail * f0, hy - tail * 0.34f * f0, hx - tail * f1, hy - tail * 0.34f * f1, p);
					}
					p.setStyle(Paint.Style.FILL);
					glow(c, p, hx, hy, u * 6f, 0xFFFFFFFF, qa(150 * life));
				}
				return true;
			}
			case CLOUDS -> {
				boolean night = nightCached();
				if (night) glow(c, p, bd.left + w * 0.2f, bd.top + h * 0.1f, Math.min(w, h) * 0.45f, 0xFFDDE6FF, 0x26); // bulut ardında ay
				int far = night ? 0xFF6B7891 : 0xFFAFBBCD, near = night ? 0xFF8C99B3 : 0xFFDCE3EE;
				// uzak katman: küçük, soluk, yavaş
				for (int i = 0; i < 4; i++) {
					float x = ((rnd(i, 71) + s * 0.0035f * (0.7f + 0.6f * rnd(i, 72))) % 1.4f) - 0.2f;
					cloud(c, p, bd.left + x * w, bd.top + h * (0.08f + 0.16f * rnd(i, 73)), w * (0.16f + 0.06f * rnd(i, 74)),
							far, night ? 0x22 : 0x2C, false);
				}
				// yakın katman: büyük, belirgin, biraz daha hızlı, üstü ışıklı
				for (int i = 0; i < 3; i++) {
					float x = ((rnd(i, 75) + s * 0.007f * (0.7f + 0.6f * rnd(i, 76))) % 1.5f) - 0.25f;
					cloud(c, p, bd.left + x * w, bd.top + h * (0.18f + 0.2f * rnd(i, 77)), w * (0.26f + 0.08f * rnd(i, 78)),
							near, night ? 0x2A : 0x3A, !night);
				}
				return true;
			}
			case FOG -> {
				boolean night = nightCached();
				int fc = night ? 0xFFB6C0D0 : 0xFFE8ECF2;
				// alta doğru koyulaşan pus
				glowOval(c, p, bd.left + w * 0.5f, bd.bottom, w * 1.2f, h * 0.6f, fc, night ? 0x26 : 0x3A);
				// farklı hız ve yönde süzülen geniş, yassı pus bantları
				for (int i = 0; i < 7; i++) {
					float dir = (i % 2 == 0) ? 1f : -1f, sp = 0.006f + 0.006f * rnd(i, 81);
					float x = (((rnd(i, 82) + dir * s * sp) % 1f) + 1f) % 1f;
					float y = 0.12f + i * 0.13f + 0.02f * (float) Math.sin(s * 0.15f + i);
					int a = (int) ((night ? 0x16 : 0x22) + 0x10 * rnd(i, 83));
					float rx = w * (0.55f + 0.3f * rnd(i, 84)), ry = h * (0.05f + 0.03f * rnd(i, 85));
					glowOval(c, p, bd.left + x * w, bd.top + y * h, rx, ry, fc, a);
					glowOval(c, p, bd.left + (x - 1f) * w, bd.top + y * h, rx, ry, fc, a); // kenardan taşan kısmı karşı yandan
				}
				return true;
			}
			case RAIN, STORM -> {
				boolean storm = scene == STORM;
				// gökyüzü: üstte koyu bulut tonu, alta doğru serin pus
				glowOval(c, p, bd.left + w * 0.5f, bd.top, w * 1.1f, h * 0.5f, storm ? 0xFF0A0F1C : 0xFF141C2C, storm ? 0x70 : 0x50);
				glowOval(c, p, bd.left + w * 0.5f, bd.bottom, w * 1.2f, h * 0.35f, 0xFF9FB4D6, 0x1A);
				// şimşek (fırtına): ~7 sn'de bir dallanan parlak çakış, ardından gökyüzü aydınlanıp söner; kısa ikinci çakış
				if (storm) {
					float ph = s % 7f;
					int k = (int) (s / 7f);
					float f = (ph < 0.35f) ? (1f - ease(ph / 0.35f)) : ((ph > 0.45f) && (ph < 0.7f) ? 0.55f * (1f - ease((ph - 0.45f) / 0.25f)) : 0f);
					if (f > 0f) {
						float bx = bd.left + w * (0.18f + 0.64f * rnd(k, 91));
						glowOval(c, p, bx, bd.top + h * 0.15f, w * 0.9f, h * 0.45f, 0xFFDCE4FF, qa(110 * f));
						c.drawColor(((int) (34 * f) << 24) | 0xEEF2FF);
						if ((ph < 0.22f) || ((ph > 0.45f) && (ph < 0.55f))) lightning(c, p, bd, k, f, u);
					}
				}
				// üç derinlikte yağmur: uzak ince ve soluk, yakın kalın, net ve hızlı (soluk iz + parlak baş)
				p.setShader(null);
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeCap(Paint.Cap.ROUND);
				int n = particles(scene, w, h);
				float wind = storm ? 0.42f : 0.24f; // eğim
				for (int i = 0; i < n; i++) {
					float d = rnd(i, 14); // 0 uzak, 1 yakın
					int layer = (d < 0.45f) ? 0 : ((d < 0.8f) ? 1 : 2);
					float sp = (layer == 0 ? 0.45f : layer == 1 ? 0.7f : 1.1f) * (0.85f + 0.3f * rnd(i, 12));
					float len = u * (layer == 0 ? 9f : layer == 1 ? 15f : 27f), dx = len * wind;
					float y = ((rnd(i, 11) + s * 0.6f * sp) % 1.15f) - 0.08f;
					float x = (rnd(i, 13) + y * 0.1f * wind) % 1f;
					float px = bd.left + x * w, py = bd.top + y * h;
					p.setStrokeWidth(Math.max(1f, u * (layer == 0 ? 0.55f : layer == 1 ? 0.9f : 1.6f)));
					p.setColor(((layer == 0 ? 26 : layer == 1 ? 44 : 64) << 24) | 0xBFD3F2); // soluk iz
					c.drawLine(px, py, px - dx, py - len, p);
					p.setColor(((layer == 0 ? 52 : layer == 1 ? 96 : 170) << 24) | 0xE6F0FF); // parlak baş
					c.drawLine(px, py, px - dx * 0.35f, py - len * 0.35f, p);
				}
				// zeminde sıçrama halkaları (ekranın alt kenarında açılıp sönen ince elipsler)
				p.setStrokeWidth(Math.max(1f, u * 0.7f));
				for (int i = 0; i < 12; i++) {
					float per = 0.9f + 0.6f * rnd(i, 111), t = ((s + rnd(i, 112) * per) % per) / per;
					float x = bd.left + w * rnd(i + (int) ((s + rnd(i, 112) * per) / per) * 13, 113);
					float y = bd.top + h * (0.92f + 0.07f * rnd(i, 114));
					float rx = u * (2f + 12f * t);
					p.setColor(((int) (110 * (1f - t)) << 24) | 0xD6E4FF);
					oval.set(x - rx, y - rx * 0.28f, x + rx, y + rx * 0.28f);
					c.drawOval(oval, p);
				}
				p.setStyle(Paint.Style.FILL);
				// camda damlalar: ışık yansıtan su damlaları; arada biri iz bırakarak aşağı kayar, sonra başka yerde belirir
				for (int i = 0; i < 18; i++) {
					float per = 9f + 8f * rnd(i, 121), cyc = (s + rnd(i, 122) * per) % per;
					int gen = (int) ((s + rnd(i, 122) * per) / per); // her dönemde yeni yer
					float x0 = bd.left + w * rnd(i * 31 + gen, 123), y0 = bd.top + h * (0.05f + 0.85f * rnd(i * 31 + gen, 124));
					float r = u * (2.2f + 4.5f * rnd(i * 31 + gen, 125));
					float slide = (cyc > per - 1.4f) ? ease((cyc - (per - 1.4f)) / 1.4f) : 0f; // son 1.4 sn kayar
					float y = y0 + slide * h * 0.22f;
					if (slide > 0f) { // kayarken arkasında ince ıslak iz
						p.setColor(0x14FFFFFF);
						oval.set(x0 - r * 0.35f, y0, x0 + r * 0.35f, y);
						c.drawRoundRect(oval, r * 0.35f, r * 0.35f, p);
					}
					float fade = (cyc < 0.6f) ? ease(cyc / 0.6f) : 1f; // belirirken yumuşak
					if (fade < 1f) {
						c.saveLayerAlpha(x0 - r * 2, y - r * 2, x0 + r * 2, y + r * 2, (int) (255 * fade));
						droplet(c, p, x0, y, r, u);
						c.restore();
					} else {
						droplet(c, p, x0, y, r, u);
					}
				}
				return true;
			}
			case SNOW -> {
				// soğuk, aydınlık gökyüzü ve altta yumuşak kar birikintisi parıltısı
				glowOval(c, p, bd.left + w * 0.5f, bd.top, w * 1.1f, h * 0.45f, 0xFFD6E2F5, 0x1C);
				glowOval(c, p, bd.left + w * 0.5f, bd.bottom + h * 0.02f, w * 1.0f, h * 0.13f, 0xFFFFFFFF, 0x30);
				int n = particles(scene, w, h);
				for (int i = 0; i < n; i++) {
					float depth = rnd(i, 25); // 0 uzak, 1 yakın
					float sp = 0.3f + 0.7f * depth;
					float y = ((rnd(i, 21) + s * 0.055f * sp) % 1.1f) - 0.05f;
					float sway = 0.02f * (0.4f + depth) * (float) Math.sin(s * (0.4f + 0.6f * rnd(i, 26)) + i * 2.3f);
					float x = (rnd(i, 23) + sway + 1f) % 1f;
					float px = bd.left + x * w, py = bd.top + y * h;
					float rr = u * (0.7f + 3.4f * depth * depth);
					if (depth > 0.78f) { // çok yakın: odak dışı, büyük ve flu ışık lekesi (bokeh)
						glow(c, p, px, py, rr * 2.6f, 0xFFFFFFFF, qa(60 + 50 * depth));
					} else { // yumuşak tane: flu hale ve küçük parlak çekirdek
						glow(c, p, px, py, rr * 2.4f, 0xFFF2F6FF, qa(40 + 70 * depth));
						p.setShader(null);
						p.setStyle(Paint.Style.FILL);
						p.setColor(((int) (120 + 110 * depth) << 24) | 0xFFFFFF);
						c.drawCircle(px, py, Math.max(u * 0.4f, rr * 0.55f), p);
					}
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
