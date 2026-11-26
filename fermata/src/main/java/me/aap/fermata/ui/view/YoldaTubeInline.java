package me.aap.fermata.ui.view;

import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * YouTube görüntüsünü Pano'daki video alanında göstermek için köprü. YouTube eklentisi (ayrı modül, bu modüle
 * bağlı) kendini {@link #host} olarak kaydeder: görüntünün çizildiği görünümü (tam ekranda video yüzeyi, yoksa
 * sayfa) geçici olarak Pano'daki alana taşır, YouTube ekranı açılınca yerine geri koyar.
 */
public final class YoldaTubeInline {
	public interface Host {
		/** Görüntüyü {@code target} içine taşı; olmazsa false. */
		boolean moveTo(ViewGroup target);

		/** Taşınan görünümü kendi yerine geri koy (taşınmamışsa bir şey yapma). */
		void restore();
	}

	@Nullable
	public static volatile Host host;

	private YoldaTubeInline() {
	}

	public static boolean show(ViewGroup target) {
		Host h = host;
		try {
			return (h != null) && h.moveTo(target);
		} catch (Throwable ex) {
			me.aap.utils.log.Log.e(ex, "YouTube görüntüsü Pano'ya taşınamadı");
			return false;
		}
	}

	public static void restore() {
		Host h = host;
		if (h == null) return;
		try {
			h.restore();
		} catch (Throwable ex) {
			me.aap.utils.log.Log.e(ex, "YouTube görüntüsü yerine konamadı");
		}
	}
}
