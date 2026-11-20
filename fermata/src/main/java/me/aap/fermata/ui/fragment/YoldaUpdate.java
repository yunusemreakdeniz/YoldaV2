package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Uygulama içi güncelleme (Mola'daki GitHub güncelleyicisinden uyarlandı): Yolda sürümleri GitHub'da
 * yunusemreakdeniz/YoldaV2 deposunda ön sürüm olarak "v2-N" etiketiyle yayınlanır; uygulamanın sürüm adı
 * "2.0.2-yolda.N". Daha büyük N'li ve APK'sı olan en yeni sürüm önerilir. Ağ isteği MainActivity'de.
 */
public final class YoldaUpdate {
	public static final String RELEASES = "https://api.github.com/repos/yunusemreakdeniz/YoldaV2/releases?per_page=10";

	private YoldaUpdate() {
	}

	/** "2.0.2-yolda.224" → 224; Yolda derlemesi değilse 0. */
	public static int buildNumber(@Nullable String versionName) {
		if (versionName == null) return 0;
		int i = versionName.lastIndexOf("-yolda.");
		if (i < 0) return 0;
		try {
			return Integer.parseInt(versionName.substring(i + 7).trim());
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** "v2-224" → 224; değilse -1. */
	static int tagNumber(String tag) {
		if ((tag == null) || !tag.startsWith("v2-")) return -1;
		try {
			return Integer.parseInt(tag.substring(3));
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	/**
	 * Sürüm listesinden önerilecek güncelleme: {etiket, APK adresi, boyut (MB)}; yoksa null. Taslaklar ve APK'sı
	 * olmayanlar atlanır.
	 */
	@Nullable
	public static String[] pick(String releasesJson, int current) throws Exception {
		JSONArray a = new JSONArray(releasesJson);
		String[] best = null;
		int bestN = current;
		for (int i = 0; i < a.length(); i++) {
			JSONObject r = a.getJSONObject(i);
			if (r.optBoolean("draft")) continue;
			int n = tagNumber(r.optString("tag_name"));
			if (n <= bestN) continue;
			JSONArray as = r.optJSONArray("assets");
			if (as == null) continue;
			for (int k = 0; k < as.length(); k++) {
				JSONObject x = as.getJSONObject(k);
				String url = x.optString("browser_download_url");
				if (!x.optString("name").endsWith(".apk") || !url.startsWith("https://")) continue;
				best = new String[]{r.optString("tag_name"), url, String.valueOf(Math.round(x.optLong("size") / 1048576.0))};
				bestN = n;
				break;
			}
		}
		return best;
	}
}
