package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import java.util.function.Supplier;

/**
 * Ana iş parçacığını bekletmeyen kısa süreli önbellek: değer istenince bellekteki son sonuç hemen döner; eskiyse
 * (ya da hiç yoksa) yükleme arka planda yapılır, bitince {@link #onLoaded} çağrılır (kart yenilensin). Takvim ve
 * telefondaki medya gibi veritabanı sorguları kart kurulurken ekranı bekletmesin diye.
 */
public final class YoldaBgCache<T> {
	private final long ttlMs;
	private final Supplier<T> loader;
	private volatile T value;
	private volatile long at;
	private volatile boolean loading;
	/** Yükleme bitince (ana iş parçacığında). */
	@Nullable
	public volatile Runnable onLoaded;

	public YoldaBgCache(long ttlMs, Supplier<T> loader) {
		this.ttlMs = ttlMs;
		this.loader = loader;
	}

	/** Yeniden yüklensin mi: hiç yoksa ya da süresi geçtiyse (yükleme sürmüyorsa). */
	static boolean stale(long now, long at, long ttl, boolean has, boolean loading) {
		return !loading && (!has || (now - at > ttl));
	}

	/** Bellekteki değer (yoksa fallback); gerekiyorsa arka planda yeniler. */
	public T get(T fallback) {
		long now = System.currentTimeMillis();
		T v = value;
		if (stale(now, at, ttlMs, v != null, loading)) {
			loading = true;
			me.aap.utils.function.CheckedSupplier<T, Throwable> job = loader::get;
			me.aap.utils.app.App.get().execute(job).onCompletion((r, err) -> {
				if (r != null) {
					value = r;
					at = System.currentTimeMillis();
				}
				loading = false;
				Runnable cb = onLoaded;
				if (cb != null) me.aap.utils.app.App.get().getHandler().post(cb);
			});
		}
		return (v != null) ? v : fallback;
	}

	/** Bir kez yüklendi mi (ilk açılışta "yükleniyor" göstermek için). */
	public boolean loaded() {
		return value != null;
	}

	/** Değişiklik oldu (izin verildi, yeni fotoğraf): sonraki istekte yeniden yükle. */
	public void invalidate() {
		at = 0;
	}
}
