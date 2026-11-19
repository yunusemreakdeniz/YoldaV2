package me.aap.fermata.media.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;

import androidx.annotation.Nullable;

import me.aap.utils.log.Log;

/**
 * Yolda ekolayzer hazır ayarları (Ekolayzer kartı ve sesli komut): her ayar 5 başvuru frekansında dB kazanç
 * olarak tanımlı; cihazın kendi bantlarına (sayısı ve merkez frekansları cihaza göre değişir) aradeğerlemeyle
 * uyarlanır. Seçili ayar her yayın / parça açılışında oynatıcıya uygulanır; "Kapalı" Fermata'nın kendi ses
 * efektleri ayarına dokunmaz.
 */
public final class YoldaEq {
	private static final String PREFS = "yolda_eq";
	/** Başvuru frekansları (Hz). */
	static final int[] REF_HZ = {60, 230, 910, 3600, 14000};
	/** {ad, 60 Hz, 230 Hz, 910 Hz, 3,6 kHz, 14 kHz dB, bas güçlendirme 0-1000}. */
	static final Object[][] PRESETS = {
			{"Kapalı", 0, 0, 0, 0, 0, 0},
			{"Pop", -1, 2, 4, 2, -1, 0},
			{"Rock", 4, 2, -2, 2, 4, 200},
			{"Konuşma", -3, -1, 3, 4, 1, 0},
			{"Bas", 6, 4, 0, 0, 0, 500},
			{"Araç", 4, 1, 0, 2, 3, 300}, // yol uğultusu basları ve tizleri bastırır: ikisi de biraz öne
			{"Klasik", 3, 1, -1, 1, 3, 0},
			{"Caz", 2, 1, -1, 1, 2, 100},
	};

	private YoldaEq() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static String[] names() {
		String[] n = new String[PRESETS.length];
		for (int i = 0; i < n.length; i++) n[i] = (String) PRESETS[i][0];
		return n;
	}

	public static String current(Context ctx) {
		return prefs(ctx).getString("preset", "Kapalı");
	}

	public static void setCurrent(Context ctx, String name) {
		prefs(ctx).edit().putString("preset", (find(name) != null) ? name : "Kapalı").apply();
	}

	@Nullable
	static Object[] find(String name) {
		for (Object[] p : PRESETS) if (((String) p[0]).equalsIgnoreCase(name)) return p;
		return null;
	}

	/** Söylenen ada uyan ayar ("rock", "konuşma modu", "araç"), yoksa null. */
	@Nullable
	public static String spoken(String s) {
		String t = s.toLowerCase(new java.util.Locale("tr"));
		for (Object[] p : PRESETS) {
			String n = ((String) p[0]).toLowerCase(new java.util.Locale("tr"));
			if (t.contains(n)) return (String) p[0];
		}
		if (t.contains("haber") || t.contains("podcast")) return "Konuşma";
		if (t.contains("kapa") || t.contains("normal") || t.contains("düz")) return "Kapalı";
		return null;
	}

	/** Bir frekanstaki kazanç (dB): başvuru noktaları arasında logaritmik aradeğerleme. */
	static double gainAt(Object[] p, double hz) {
		if (hz <= REF_HZ[0]) return ((Number) p[1]).doubleValue();
		for (int i = 0; i + 1 < REF_HZ.length; i++) {
			if (hz <= REF_HZ[i + 1]) {
				double a = Math.log(REF_HZ[i]), b = Math.log(REF_HZ[i + 1]), x = (Math.log(hz) - a) / (b - a);
				double ga = ((Number) p[i + 1]).doubleValue(), gb = ((Number) p[i + 2]).doubleValue();
				return ga + (gb - ga) * x;
			}
		}
		return ((Number) p[REF_HZ.length]).doubleValue();
	}

	/** Cihaz bantlarının seviyeleri (millibel): merkez frekanslar (Hz) ve izin verilen aralığa göre. */
	static short[] levels(Object[] p, int[] centerHz, short min, short max) {
		short[] out = new short[centerHz.length];
		for (int i = 0; i < out.length; i++) {
			long mb = Math.round(gainAt(p, centerHz[i]) * 100);
			out[i] = (short) Math.max(min, Math.min(max, mb));
		}
		return out;
	}

	/** Seçili ayarı oynatıcıya uygula. @return uygulandıysa true (Kapalı ya da ekolayzer yoksa false). */
	public static boolean apply(Context ctx, @Nullable AudioEffects ae) {
		Object[] p = find(current(ctx));
		if ((ae == null) || (p == null) || "Kapalı".equals(p[0])) return false;
		Equalizer eq = ae.getEqualizer();
		if (eq == null) return false;
		try {
			short n = eq.getNumberOfBands();
			short[] range = eq.getBandLevelRange();
			int[] hz = new int[n];
			for (short b = 0; b < n; b++) hz[b] = eq.getCenterFreq(b) / 1000; // milliHz → Hz
			short[] lv = levels(p, hz, range[0], range[1]);
			eq.setEnabled(true);
			for (short b = 0; b < n; b++) eq.setBandLevel(b, lv[b]);
			BassBoost bass = ae.getBassBoost();
			int bs = ((Number) p[6]).intValue();
			if ((bass != null) && bass.getStrengthSupported()) {
				bass.setEnabled(bs > 0);
				if (bs > 0) bass.setStrength((short) bs);
			}
			return true;
		} catch (Exception ex) {
			Log.e(ex, "Yolda ekolayzer uygulanamadı");
			return false;
		}
	}

	/** Kapalıya geçince Yolda'nın açtığı efektleri kapat (Fermata kendi ayarını bir sonraki açılışta uygular). */
	public static void off(@Nullable AudioEffects ae) {
		if (ae == null) return;
		try {
			if (ae.getEqualizer() != null) ae.getEqualizer().setEnabled(false);
			if (ae.getBassBoost() != null) ae.getBassBoost().setEnabled(false);
		} catch (Exception ignore) {
		}
	}
}
