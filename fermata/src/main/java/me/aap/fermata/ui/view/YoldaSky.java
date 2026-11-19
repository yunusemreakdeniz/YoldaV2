package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import me.aap.fermata.ui.fragment.YoldaRadar;
import me.aap.fermata.ui.fragment.YoldaWeather;

/**
 * Hava durumuna göre zemin: tema ışıltısının üstüne hafif bir sahne çizer. Açık gündüz güneş ışığı, açık
 * gece yıldızlar, bulutlu / kapalı gri bulut kümeleri, sis yatay pus, yağmur ve sağanak eğik damlalar, kar
 * süzülen taneler, fırtına seyrek şimşek. Sahne yazıyı bastırmasın diye düşük saydamlıkta; animasyon kapalıyken
 * ve araçta (YoldaGlow sabitken) aynı sahne kımıldamadan çizilir.
 */
public final class YoldaSky {
	public static final int NONE = 0, SUN = 1, STARS = 2, CLOUDS = 3, FOG = 4, RAIN = 5, SNOW = 6, STORM = 7;
	public static final String PREF = "weather_bg";

	/** Kullanıcı ayarı (Tema seçicide "Hava durumuna göre zemin"); varsayılan açık. */
	public static volatile boolean enabled = true;
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

	public static void load(Context ctx) {
		enabled = ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).getBoolean(PREF, true);
	}

	public static void setEnabled(Context ctx, boolean on) {
		enabled = on;
		ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply();
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
		if (!enabled) return NONE;
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

	/** Sahneyi çiz; s = saniye (sabitken 0). Animasyon gerekiyorsa true. */
	static boolean draw(Canvas c, Paint p, Rect bd, int scene, float s) {
		float w = bd.width(), h = bd.height();
		if ((scene == NONE) || (w <= 0) || (h <= 0)) return false;
		float u = Math.min(w, h) / 400f; // ekrana göre birim
		switch (scene) {
			case SUN -> {
				float r = Math.max(w, h) * 0.55f, pulse = 1f + 0.04f * (float) Math.sin(s * 0.5);
				glow(c, p, bd.left + w * 0.86f, bd.top + h * 0.04f, r * pulse, 0xFFFFD27A, 0x70);
				glow(c, p, bd.left + w * 0.86f, bd.top + h * 0.04f, r * 0.22f, 0xFFFFF4D6, 0x80);
				return true;
			}
			case STARS -> {
				p.setShader(null);
				p.setStyle(Paint.Style.FILL);
				int n = particles(scene, w, h);
				for (int i = 0; i < n; i++) {
					float tw = 0.55f + 0.45f * (float) Math.sin(s * (0.8f + rnd(i, 3)) + i);
					p.setColor(alpha(0xFFFFFFFF, (int) (40 + 120 * tw * rnd(i, 4))));
					c.drawCircle(bd.left + rnd(i, 1) * w, bd.top + rnd(i, 2) * h * 0.6f, u * (0.6f + 1.1f * rnd(i, 5)), p);
				}
				glow(c, p, bd.left + w * 0.14f, bd.top + h * 0.08f, Math.min(w, h) * 0.30f, 0xFFDDE6FF, 0x30); // ay ışığı
				return true;
			}
			case CLOUDS, FOG -> {
				boolean fog = scene == FOG;
				for (int i = 0; i < (fog ? 5 : 4); i++) {
					float x = ((rnd(i, 7) + s * (0.004f + 0.003f * rnd(i, 8))) % 1.3f) - 0.15f;
					float y = fog ? (0.18f + i * 0.17f) : (0.06f + 0.22f * rnd(i, 9));
					float r = Math.max(w, h) * (fog ? 0.45f : 0.30f + 0.12f * rnd(i, 10));
					glow(c, p, bd.left + x * w, bd.top + y * h, r, fog ? 0xFFE5E9F0 : 0xFFC9D2E0, fog ? 0x26 : 0x30);
				}
				return true;
			}
			case RAIN, STORM -> {
				p.setShader(null);
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeCap(Paint.Cap.ROUND);
				p.setStrokeWidth(Math.max(1f, u * 1.1f));
				int n = particles(scene, w, h);
				float len = u * 16f, dx = len * 0.28f;
				for (int i = 0; i < n; i++) {
					float sp = 0.55f + 0.45f * rnd(i, 12); // her damla farklı hızda
					float y = ((rnd(i, 11) + s * 0.45f * sp) % 1.1f) - 0.05f;
					float x = (rnd(i, 13) + y * 0.08f) % 1f;
					p.setColor(alpha(0xFFBFD7FF, (int) (45 + 50 * sp)));
					float px = bd.left + x * w, py = bd.top + y * h;
					c.drawLine(px, py, px - dx, py + len, p);
				}
				p.setStyle(Paint.Style.FILL);
				if (scene == STORM) { // ~7 sn'de bir kısa şimşek
					float ph = (s % 7f);
					if (ph < 0.12f) c.drawColor(alpha(0xFFFFFFFF, (int) (40 * (1f - ph / 0.12f))));
				}
				return true;
			}
			case SNOW -> {
				p.setShader(null);
				p.setStyle(Paint.Style.FILL);
				int n = particles(scene, w, h);
				for (int i = 0; i < n; i++) {
					float sp = 0.4f + 0.6f * rnd(i, 22);
					float y = ((rnd(i, 21) + s * 0.05f * sp) % 1.08f) - 0.04f;
					float x = (rnd(i, 23) + 0.02f * (float) Math.sin(s * 0.7f + i)) % 1f;
					p.setColor(alpha(0xFFFFFFFF, (int) (70 + 90 * sp)));
					c.drawCircle(bd.left + x * w, bd.top + y * h, u * (1.2f + 2.2f * rnd(i, 24)), p);
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private static void glow(Canvas c, Paint p, float x, float y, float r, int color, int a) {
		p.setStyle(Paint.Style.FILL);
		p.setShader(new RadialGradient(x, y, Math.max(1f, r), new int[]{(color & 0x00FFFFFF) | (a << 24),
				(color & 0x00FFFFFF) | ((a / 3) << 24), color & 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
		c.drawCircle(x, y, Math.max(1f, r), p);
		p.setShader(null);
	}

	private static int alpha(int c, int a) {
		return (c & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
	}
}
