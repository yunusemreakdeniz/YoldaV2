package me.aap.fermata.ui.fragment;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import me.aap.fermata.R;

/**
 * "Hey Yolda" için ön plan hizmeti (mikrofon). Araç bağlıyken Yolda telefonda arka plandadır; Android
 * arka plandaki uygulamaya mikrofonu sessiz verir, bu yüzden "Hey Yolda" araçta hiç duyulmuyordu. Hizmet
 * telefonda uygulama açıkken başlatılır ve "Hey Yolda" açık kaldıkça sürer; bildirimde görünür.
 */
public class YoldaWakeService extends Service {
	private static final String CHANNEL = "yolda_wake";
	private static final int ID = 0x59_4B;

	public static void start(Context ctx) {
		try {
			Context app = ctx.getApplicationContext();
			if (app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
					!= android.content.pm.PackageManager.PERMISSION_GRANTED) return; // izinsiz mikrofon hizmeti çöker
			Intent i = new Intent(app, YoldaWakeService.class);
			if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
			else app.startService(i);
		} catch (Throwable ex) {
			YoldaLog.add("Hey Yolda hizmeti başlatılamadı: " + ex);
		}
	}

	public static void stop(Context ctx) {
		try {
			Context app = ctx.getApplicationContext();
			app.stopService(new Intent(app, YoldaWakeService.class));
		} catch (Throwable ignore) {
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			NotificationManager nm = getSystemService(NotificationManager.class);
			if ((Build.VERSION.SDK_INT >= 26) && (nm.getNotificationChannel(CHANNEL) == null)) {
				NotificationChannel ch = new NotificationChannel(CHANNEL, "Hey Yolda", NotificationManager.IMPORTANCE_LOW);
				ch.setShowBadge(false);
				nm.createNotificationChannel(ch);
			}
			Notification.Builder b = (Build.VERSION.SDK_INT >= 26) ? new Notification.Builder(this, CHANNEL)
					: new Notification.Builder(this);
			Notification n = b.setSmallIcon(R.drawable.notification).setContentTitle("Hey Yolda dinliyor")
					.setContentText("“Hey Yolda” deyince sesli asistan açılır").setOngoing(true).build();
			if (Build.VERSION.SDK_INT >= 30) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
			else startForeground(ID, n);
			YoldaLog.add("Hey Yolda hizmeti çalışıyor (mikrofon arka planda da açık)");
		} catch (Throwable ex) {
			YoldaLog.add("Hey Yolda hizmeti ön plana alınamadı: " + ex);
			stopSelf();
		}
		// Sistem kendiliğinden yeniden başlatmasın: arka planda mikrofon hizmeti açmak Android 14'te çöker
		return START_NOT_STICKY;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
