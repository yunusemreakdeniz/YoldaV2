package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Medya kartı: telefondaki videolar (en yeni üstte, klasör adıyla) ve fotoğraflar. Yalnız okuma izni; hiçbir
 * şey dışarı gönderilmez. Videolar Yolda'nın oynatıcısında açılır; fotoğraflar yalnız telefonda (araçta
 * gösterilmez) galeride açılır.
 */
public final class YoldaMedia {
	/** Bir dosya: adres, ad, klasör, süre (ms; fotoğrafta 0), eklenme (sn). */
	public record Item(Uri uri, String name, String folder, long durMs, long added) {
	}

	private YoldaMedia() {
	}

	/** İstenecek izinler (Android 13+ ayrı video / fotoğraf izni). */
	public static String[] permissions() {
		return (Build.VERSION.SDK_INT >= 33) ? new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES}
				: new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
	}

	public static boolean hasPermission(Context ctx) {
		for (String p : permissions()) if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
		return true;
	}

	/** En yeni videolar (en çok n). */
	public static List<Item> videos(Context ctx, int n) {
		return query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, n);
	}

	/** En yeni fotoğraflar (en çok n). */
	public static List<Item> photos(Context ctx, int n) {
		return query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, n);
	}

	private static List<Item> query(Context ctx, Uri base, boolean video, int n) {
		List<Item> out = new ArrayList<>();
		if (!hasPermission(ctx)) return out;
		String[] cols = video ? new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
				MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.Video.VideoColumns.DURATION, MediaStore.MediaColumns.DATE_ADDED}
				: new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
				MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED};
		try (Cursor c = ctx.getContentResolver().query(base, cols, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
			if (c == null) return out;
			while (c.moveToNext() && (out.size() < n)) {
				long id = c.getLong(0);
				String name = c.getString(1);
				String folder = c.getString(2);
				long dur = video ? c.getLong(3) : 0;
				long added = c.getLong(video ? 4 : 3);
				out.add(new Item(ContentUris.withAppendedId(base, id), (name == null) ? "Dosya" : stripExt(name),
						(folder == null) ? "" : folder, dur, added));
			}
		} catch (Throwable ex) {
			YoldaLog.add("Medya okunamadı: " + ex.getClass().getSimpleName());
		}
		return out;
	}

	static String stripExt(String n) {
		int i = n.lastIndexOf('.');
		return (i > 0) ? n.substring(0, i) : n;
	}

	/** "1:02:03" / "4:05". */
	public static String dur(long ms) {
		long s = ms / 1000;
		long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
		return (h > 0) ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
				: String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
	}

	/** Küçük resim (arka planda çağır; Android 10+), alınamazsa null. */
	@Nullable
	public static Bitmap thumbnail(Context ctx, Uri uri) {
		try {
			if (Build.VERSION.SDK_INT >= 29) return ctx.getContentResolver().loadThumbnail(uri, new Size(320, 320), null);
		} catch (Throwable ignore) {
		}
		return null;
	}
}
