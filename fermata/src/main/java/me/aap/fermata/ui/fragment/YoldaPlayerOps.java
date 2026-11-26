package me.aap.fermata.ui.fragment;

import android.os.SystemClock;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Şimdi çalıyor ekranının işlem satırı (Mola'daki tam oynatıcıdan uyarlandı): oynatma hızı, sarma, uyku
 * zamanlayıcısı. Seçim kuralları burada (birim testli), ekran yalnız düğmeleri çizer.
 */
public final class YoldaPlayerOps {
	/** Hız sırası: 1 → 1,25 → 1,5 → 2 → 0,75 → 1. */
	static final float[] SPEEDS = {1f, 1.25f, 1.5f, 2f, 0.75f};
	private static volatile float speed = 1f;
	/** Hızın ayarlandığı içerik: başka içerik çalınca hız yine 1 (motor yeni içerikte 1 ile başlar). */
	private static volatile String speedItem;

	private YoldaPlayerOps() {
	}

	static float nextSpeed(float cur) {
		for (int i = 0; i < SPEEDS.length; i++)
			if (Math.abs(SPEEDS[i] - cur) < 0.01f) return SPEEDS[(i + 1) % SPEEDS.length];
		return 1f;
	}

	/** "1×", "1,25×", "0,75×". */
	public static String speedLabel(float s) {
		String v = (Math.abs(s - Math.round(s)) < 0.01f) ? String.valueOf(Math.round(s))
				: String.valueOf(s).replace('.', ',');
		return v + "×";
	}

	public static float speed(String itemId) {
		return java.util.Objects.equals(itemId, speedItem) ? speed : 1f;
	}

	/** Sıradaki hıza geç ve uygula; yeni hız. */
	public static float cycleSpeed(MainActivityDelegate a) {
		var pi = a.getCurrentPlayable();
		String id = (pi != null) ? pi.getId() : null;
		float n = nextSpeed(speed(id));
		speed = n;
		speedItem = id;
		a.getMediaSessionCallback().onSetPlaybackSpeed(n);
		YoldaLog.add("Oynatma hızı: " + speedLabel(n));
		return n;
	}

	/** Uyku zamanlayıcısında kalan dakika (yoksa 0). */
	public static int sleepLeftMin() {
		long at = YoldaVoice.sleepAt;
		if (at == 0) return 0;
		long left = Math.max(0, at - SystemClock.elapsedRealtime());
		return (int) ((left + 59_999) / 60_000);
	}

	/** Uyku düğmesi: Kapalı → 15 → 30 → 60 → Kapalı; yeni durumun dakikası (0 kapalı). */
	public static int cycleSleep(MainActivityDelegate a) {
		int n = YoldaCardStats.nextSleep(sleepLeftMin());
		if (n == 0) YoldaVoice.cancelSleep();
		else YoldaVoice.sleep(a, n);
		return n;
	}

	public static String sleepLabel(int leftMin) {
		return (leftMin > 0) ? (leftMin + " dk") : "Uyku";
	}

	/** Sarma: ileri (true) 30 sn, geri 15 sn; süre bilinmiyorsa / canlıysa false. */
	public static boolean skip(MainActivityDelegate a, boolean fwd) {
		var cb = a.getMediaSessionCallback();
		var eng = cb.getEngine();
		if ((eng == null) || !eng.canSeek()) return false;
		long pos = me.aap.fermata.ui.view.YoldaNowPlaying.positionOf(cb);
		cb.onSeekTo(skipTarget(pos, fwd));
		return true;
	}

	static long skipTarget(long pos, boolean fwd) {
		return Math.max(0, pos + (fwd ? 30_000L : -15_000L));
	}
}
