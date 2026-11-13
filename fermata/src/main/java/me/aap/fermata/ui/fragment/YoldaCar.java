package me.aap.fermata.ui.fragment;

import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Android Auto bağlantısı (Car App Library'nin CarConnection'ı gibi): bağlanınca / kesilince dinleyicilere
 * haber verir. Park yeri ve yolculuk sonu buna bağlı.
 */
public final class YoldaCar {
	private static final List<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();
	private static boolean registered;
	private static volatile boolean connected;
	private static volatile boolean known;
	private static AsyncQueryHandler query;

	private YoldaCar() {
	}

	public static boolean isConnected() {
		return connected;
	}

	public static void addListener(Consumer<Boolean> l) {
		listeners.add(l);
	}

	public static void init(Context ctx) {
		if (registered) return;
		registered = true;
		Context app = ctx.getApplicationContext();
		try {
			query = new AsyncQueryHandler(app.getContentResolver()) {
				@Override
				protected void onQueryComplete(int token, Object cookie, Cursor c) {
					boolean now = false;
					if (c != null) {
						try (c) {
							now = c.moveToNext() && (c.getInt(0) != 0);
						} catch (Exception ignore) {
						}
					}
					set(now);
				}
			};
			ContextCompat.registerReceiver(app, new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					refresh();
				}
			}, new IntentFilter("androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"), ContextCompat.RECEIVER_EXPORTED);
			refresh();
		} catch (Throwable ex) {
			YoldaLog.add("Araç bağlantısı izlenemiyor: " + ex);
		}
	}

	private static void refresh() {
		try {
			if (query != null) query.startQuery(0, null, Uri.parse("content://androidx.car.app.connection"),
					new String[]{"CarConnectionState"}, null, null, null);
		} catch (Throwable ignore) {
		}
	}

	/** Bağlantı durumu (testler doğrudan verir). */
	public static void set(boolean now) {
		boolean was = connected;
		boolean first = !known;
		known = true;
		connected = now;
		if (first || (was == now)) return; // ilk okuma değişiklik sayılmaz
		YoldaLog.add("Araç bağlantısı: " + (now ? "bağlandı" : "kesildi"));
		for (Consumer<Boolean> l : listeners) {
			try {
				l.accept(now);
			} catch (Throwable ex) {
				YoldaLog.add("Araç bağlantısı dinleyicisi: " + ex);
			}
		}
	}
}
