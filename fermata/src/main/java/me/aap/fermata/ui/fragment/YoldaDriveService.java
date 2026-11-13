package me.aap.fermata.ui.fragment;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.utils.app.App;

/**
 * Sürüş yardımcıları arka planda: Android Auto'da haritaya geçince, telefon kilitliyken ya da Yolda kapalıyken
 * de konum izlenir, radar / hız / bölge uyarıları sesle verilir. Bildirimde hız ve öndeki radar görünür;
 * "Kapat" ile durur. Araç bağlı değilken ve Yolda kapalıyken 15 dakika hareket olmazsa kendiliğinden kapanır.
 */
public class YoldaDriveService extends Service {
	private static final String CHANNEL = "yolda_drive";
	private static final int ID = 0x59_44;
	private static final String STOP = "me.aap.fermata.yolda.DRIVE_STOP";
	private static final long IDLE_STOP = 15 * 60_000L;
	public static volatile boolean running;
	private final Runnable listener = this::onRadar;
	private final Runnable tick = this::tick;
	private long notifiedAt;
	private String lastText;

	public static void start(Context ctx) {
		try {
			Context app = ctx.getApplicationContext();
			if (running || !YoldaRadar.hasPermission(app)) return; // izinsiz konum hizmeti Android 14'te çöker
			Intent i = new Intent(app, YoldaDriveService.class);
			if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
			else app.startService(i);
		} catch (Throwable ex) {
			YoldaLog.add("Sürüş hizmeti başlatılamadı: " + ex);
		}
	}

	public static void stop(Context ctx) {
		try {
			Context app = ctx.getApplicationContext();
			app.stopService(new Intent(app, YoldaDriveService.class));
		} catch (Throwable ignore) {
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ((intent != null) && STOP.equals(intent.getAction())) {
			YoldaLog.add("Sürüş hizmeti bildirimden kapatıldı");
			stopSelf();
			return START_NOT_STICKY;
		}
		try {
			NotificationManager nm = getSystemService(NotificationManager.class);
			if ((Build.VERSION.SDK_INT >= 26) && (nm.getNotificationChannel(CHANNEL) == null)) {
				NotificationChannel ch = new NotificationChannel(CHANNEL, "Radar ve sürüş yardımcıları", NotificationManager.IMPORTANCE_LOW);
				ch.setShowBadge(false);
				nm.createNotificationChannel(ch);
			}
			Notification n = build("Konum bekleniyor…");
			if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
			else startForeground(ID, n);
			if (!running) {
				running = true;
				YoldaRadar.attachService(this);
				YoldaRadar.addListener(listener);
				App.get().getHandler().postDelayed(tick, 60_000);
				YoldaLog.add("Sürüş hizmeti çalışıyor (harita açıkken de uyarır)");
			}
		} catch (Throwable ex) {
			YoldaLog.add("Sürüş hizmeti ön plana alınamadı: " + ex);
			stopSelf();
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		if (running) {
			running = false;
			App.get().getHandler().removeCallbacks(tick);
			YoldaRadar.removeListener(listener);
			YoldaRadar.detachService();
			YoldaLog.add("Sürüş hizmeti durdu");
		}
		super.onDestroy();
	}

	/** Dakikada bir: araç yok, Yolda kapalı ve uzun süre hareket yoksa kapan. */
	private void tick() {
		if (!running) return;
		boolean idle = SystemClock.elapsedRealtime() - YoldaRadar.lastMoveAt > IDLE_STOP;
		if (!YoldaRadar.isEnabled(this) || !YoldaRadar.feature(this, "bg")
				|| (!YoldaCar.isConnected() && !YoldaRadar.hasUi() && idle)) {
			YoldaLog.add("Sürüş hizmeti kendiliğinden kapanıyor (hareket yok / kapalı)");
			stopSelf();
			return;
		}
		App.get().getHandler().postDelayed(tick, 60_000);
	}

	private void onRadar() {
		long now = SystemClock.elapsedRealtime();
		if (now - notifiedAt < 5000) return;
		String t = text();
		if (t.equals(lastText)) return;
		notifiedAt = now;
		lastText = t;
		try {
			getSystemService(NotificationManager.class).notify(ID, build(t));
		} catch (Throwable ignore) {
		}
	}

	private String text() {
		if (!YoldaRadar.hasFix()) return "Konum bekleniyor…";
		StringBuilder b = new StringBuilder();
		if (YoldaRadar.speed >= 0) b.append(YoldaRadar.speed).append(" km/s");
		if (YoldaRadar.limit > 0) b.append(" · sınır ").append(YoldaRadar.limit);
		YoldaRadarCore.Ahead a = YoldaRadar.ahead;
		if (a != null) b.append(" · önde radar ").append(RadarFragment.meters(a.meters()));
		if (YoldaRadar.avg.isActive() && (YoldaRadar.avg.average() > 0)) b.append(" · ort. ").append(YoldaRadar.avg.average());
		return (b.length() == 0) ? "Açık" : b.toString();
	}

	private Notification build(String text) {
		Notification.Builder b = (Build.VERSION.SDK_INT >= 26) ? new Notification.Builder(this, CHANNEL)
				: new Notification.Builder(this);
		int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
		PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, YoldaDriveService.class).setAction(STOP), fl);
		Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
		if (open != null) b.setContentIntent(PendingIntent.getActivity(this, 2, open, fl));
		return b.setSmallIcon(R.drawable.notification).setContentTitle("Yolda radar ve sürüş yardımcıları")
				.setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
				.addAction(new Notification.Action.Builder(null, "Kapat", stop).build()).build();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
