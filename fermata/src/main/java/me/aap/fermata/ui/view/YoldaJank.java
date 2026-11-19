package me.aap.fermata.ui.view;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.function.Supplier;

/**
 * Yavaş kare ölçer: pencerenin her karesinin süresini dinler (Android 7+, ayrı iş parçacığında, ağırlıksız).
 * Dakikada bir, takılma varsa tanı günlüğüne yazar: "Akıcılık · <ekran>: 1800 kare, 42 yavaş (%2.3), 1 donma".
 * Yavaş: 32 ms üstü (iki kare kaçırdı); donma: 250 ms üstü. Gerçek telefonda takılan ekranı bulmak için.
 */
public final class YoldaJank {
	static final long SLOW_NS = 32_000_000L, FREEZE_NS = 250_000_000L;
	private static HandlerThread thread;
	/** Aynı pencereye iki kez bağlanmasın (ekran yeniden kurulunca). */
	private static final java.util.Map<Window, Boolean> attached = new java.util.WeakHashMap<>();

	private YoldaJank() {
	}

	/** Kare süresinden sınıf: 0 akıcı, 1 yavaş, 2 donma. */
	static int classify(long ns) {
		if (ns >= FREEZE_NS) return 2;
		return (ns >= SLOW_NS) ? 1 : 0;
	}

	/** Günlük satırı (yavaş yoksa null). */
	static String line(String screen, int frames, int slow, int freeze) {
		if ((frames <= 0) || ((slow == 0) && (freeze == 0))) return null;
		return String.format(java.util.Locale.forLanguageTag("tr"), "Akıcılık · %s: %d kare, %d yavaş (%%%.1f), %d donma",
				screen, frames, slow, 100f * slow / frames, freeze);
	}

	/** Etkinliğin penceresini dinlemeye başla; screen o an etkin ekranın adını verir. */
	public static void attach(Activity act, Supplier<String> screen) {
		if (Build.VERSION.SDK_INT < 24) return;
		try {
			Window win = act.getWindow();
			synchronized (attached) {
				if (attached.containsKey(win)) return;
				attached.put(win, true);
			}
			if (thread == null) {
				thread = new HandlerThread("YoldaJank");
				thread.start();
			}
			Handler h = new Handler(thread.getLooper());
			int[] n = new int[3];
			long[] since = {System.currentTimeMillis()};
			String[] last = {null};
			Window.OnFrameMetricsAvailableListener l = (w, fm, dropped) -> {
				long ns = fm.getMetric(FrameMetrics.TOTAL_DURATION);
				if (fm.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return; // ilk kare (açılış) sayılmaz
				n[0]++;
				int c = classify(ns);
				if (c == 1) n[1]++;
				else if (c == 2) {
					n[1]++;
					n[2]++;
				}
				long now = System.currentTimeMillis();
				String sc;
				try {
					sc = screen.get();
				} catch (Throwable ex) {
					sc = "?";
				}
				if ((now - since[0] >= 60_000L) || ((last[0] != null) && !last[0].equals(sc))) { // dakikada bir ya da ekran değişince
					String ln = line((last[0] != null) ? last[0] : sc, n[0], n[1], n[2]);
					if (ln != null) me.aap.fermata.ui.fragment.YoldaLog.add(ln);
					n[0] = n[1] = n[2] = 0;
					since[0] = now;
				}
				last[0] = sc;
			};
			win.addOnFrameMetricsAvailableListener(l, h);
		} catch (Throwable ignore) {
		}
	}
}
