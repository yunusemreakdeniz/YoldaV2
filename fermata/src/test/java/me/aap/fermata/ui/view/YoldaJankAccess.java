package me.aap.fermata.ui.view;

/** Testler için YoldaJank'ın paket içi yöntemlerine erişim. */
public final class YoldaJankAccess {
	private YoldaJankAccess() {
	}

	public static int classify(long ns) {
		return YoldaJank.classify(ns);
	}

	public static String line(String screen, int frames, int slow, int freeze) {
		return YoldaJank.line(screen, frames, slow, freeze);
	}

	public static String buildLine(String what, long ms, int items) {
		return YoldaJank.buildLine(what, ms, items);
	}
}
