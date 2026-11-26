package me.aap.fermata.ui.fragment;

/**
 * Panel (Kokpit) kart renkleri temadan: vurgu temanın en canlı rengi, kartlar temanın ışık rengine bürünmüş koyu
 * yüzey. Canlı temalarda kart hafif renk geçişli, yumuşakta yarı tonlu, asil (koyu cam) temalarda nötr. Kartlar her
 * temada koyu kalır: beyaz yazı okunur (karşıtlık en az 7:1).
 */
record YoldaKokpitPalette(int accent, int well, int wellEnd, int deep, int line) {
	static final int GOLD = 0xFFD6B46A;
	static final int WELL = 0xFF1F2126, DEEP = 0xFF131518, LINE = 0xFF2A2D33;
	/** Stil: YoldaStyle.CARDS_VIVID / CARDS_SOFT / CARDS_NOBLE. */
	static final int VIVID = 0, SOFT = 1, NOBLE = 2;

	static YoldaKokpitPalette of(int themeAccent, int[] blobs, int cards) {
		int accent = pickAccent(themeAccent, blobs);
		int b0 = (blobs.length > 0) ? blobs[0] : accent;
		int b1 = (blobs.length > 1) ? blobs[1] : b0;
		float mix = (cards == NOBLE) ? 0.07f : (cards == SOFT) ? 0.16f : 0.22f;
		int well = darkEnough(blend(WELL, b0, mix));
		int wellEnd = (cards == VIVID) ? darkEnough(blend(WELL, b1, mix)) : well;
		int deep = darkEnough(blend(DEEP, b1, mix * 0.8f));
		int line = blend(LINE, accent, (cards == NOBLE) ? 0.12f : 0.28f);
		return new YoldaKokpitPalette(accent, well, wellEnd, deep, line);
	}

	/** Temanın vurgusu yeterince renkliyse o; değilse en renkli ışık rengi açılarak; hiçbiri renkli değilse temanın açık vurgusu (yoksa altın). */
	static int pickAccent(int themeAccent, int[] blobs) {
		if (saturation(themeAccent) >= 0.30f && lightness(themeAccent) >= 0.55f) return themeAccent | 0xFF000000;
		int best = 0;
		float bs = 0.25f; // en az bu kadar renk yoğunluğu (koyu gri-mor ışıklar renkli sayılmaz)
		for (int i = 0; i < Math.min(3, blobs.length); i++) {
			float s = chroma(blobs[i]);
			if (s > bs) {
				bs = s;
				best = blobs[i];
			}
		}
		if (best == 0) return (lightness(themeAccent) >= 0.6f) ? (themeAccent | 0xFF000000) : GOLD; // gümüş / platin
		// koyu ışık rengini okunur aydınlığa çek (koyu kartta ikon / etiket rengi)
		while (lightness(best) < 0.66f) best = blend(best, 0xFFFFFFFF, 0.18f);
		return best | 0xFF000000;
	}

	/** Kart beyaz yazıyla okunur kalsın: göreli parlaklık en çok 0.045 (gri alt yazı da ≥ 4.5:1). */
	static int darkEnough(int c) {
		while (luminance(c) > 0.045) c = blend(c, 0xFF000000, 0.12f);
		return c | 0xFF000000;
	}

	static int blend(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	static float saturation(int c) {
		float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		return (max == 0) ? 0 : (max - min) / max; // HSV doygunluğu (HSL beyaza yakın renklerde yanıltıyor)
	}

	/** Renk yoğunluğu (en büyük - en küçük kanal, 0-1). */
	static float chroma(int c) {
		float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
		return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
	}

	static float lightness(int c) {
		float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
		return (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 2f;
	}

	static double luminance(int c) {
		return 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF) + 0.0722 * lin(c & 0xFF);
	}

	static double contrast(int a, int b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double lin(int v) {
		double s = v / 255.0;
		return (s <= 0.03928) ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}
}
