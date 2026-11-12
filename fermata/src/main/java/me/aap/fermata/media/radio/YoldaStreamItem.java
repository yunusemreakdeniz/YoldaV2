package me.aap.fermata.media.radio;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * Adresi bilinen canlı ses akışı (internet radyosu): radyo dizininden bulunan ya da kullanıcının eklediği
 * istasyon. Video değildir; "Şimdi çalıyor" ekranında istasyon logosuyla görünür.
 */
public class YoldaStreamItem extends PlayableItemBase {
	private final String name;
	@Nullable
	private final String logo;
	@Nullable
	private final String tags;

	private YoldaStreamItem(MediaLib lib, String url, String name, @Nullable String logo, @Nullable String tags) {
		super("yolda-stream://" + Integer.toHexString(url.hashCode()), root(lib),
				GenericFileSystem.getInstance().create(Rid.create(Uri.parse(url))));
		this.name = name;
		this.logo = logo;
		this.tags = tags;
	}

	private static ExtRoot root;

	private static synchronized ExtRoot root(MediaLib lib) {
		if (root == null) root = new ExtRoot("radio_root", lib);
		return root;
	}

	public static YoldaStreamItem create(MediaLib lib, String url, String name, @Nullable String logo,
																			 @Nullable String tags) {
		return new YoldaStreamItem(lib, url, name, logo, tags);
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
		meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, (tags != null) ? tags : "Radyo");
		if (logo != null) meta.setImageUri(logo);
		return completed(meta.build());
	}

	@NonNull
	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed((tags != null) && !tags.isEmpty() ? tags : "İnternet radyosu");
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		return completedNull();
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		return completedNull();
	}

	@Override
	public boolean isVideo() {
		return false;
	}

	@Override
	public boolean isStream() {
		return true;
	}

	@Override
	public boolean isExternal() {
		return true;
	}

	@Override
	public boolean isSeekable() {
		return false;
	}

	@Override
	public String getOrigId() {
		return getId();
	}
}
