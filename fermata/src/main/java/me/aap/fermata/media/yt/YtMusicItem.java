package me.aap.fermata.media.yt;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * Yolda Müzik'te çalan şarkı. Sıradaki / önceki şarkı ihtiyaç anında çözülür, böylece liste kendiliğinden
 * devam eder; akış adresleri birkaç saat geçerli olduğundan önceden hepsi alınmaz.
 */
public class YtMusicItem extends PlayableItemBase {
	private final List<YtMusic.Track> queue;
	private final int index;
	private final YtMusic.Track track;
	private final YtMusic.Stream stream;

	private YtMusicItem(MediaLib lib, List<YtMusic.Track> queue, int index, YtMusic.Stream stream) {
		super("ytm://" + queue.get(index).id(), root(lib),
				GenericFileSystem.getInstance().create(Rid.create(Uri.parse(stream.url()))));
		this.queue = queue;
		this.index = index;
		this.track = queue.get(index);
		this.stream = stream;
	}

	private static ExtRoot root;

	private static synchronized ExtRoot root(MediaLib lib) {
		if (root == null) root = new ExtRoot("ytm_root", lib);
		return root;
	}

	/** Listedeki şarkının akışını bulup çalınabilir öğe yapar. */
	public static FutureSupplier<PlayableItem> create(MediaLib lib, List<YtMusic.Track> queue, int index) {
		if ((index < 0) || (index >= queue.size())) return completedNull();
		return YtMusic.resolve(queue.get(index)).main().map(s -> new YtMusicItem(lib, queue, index, s));
	}

	public YtMusic.Track getTrack() {
		return track;
	}

	@NonNull
	@Override
	public String getName() {
		return track.title();
	}

	@Nullable
	@Override
	public String getUserAgent() {
		return stream.userAgent();
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title());
		if (track.artist() != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist());
		long d = (stream.durationSec() > 0) ? stream.durationSec() : track.durationSec();
		if (d > 0) meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, d * 1000);
		String img = (stream.thumb() != null) ? stream.thumb() : track.thumb();
		if (img != null) meta.setImageUri(img);
		return completed(meta.build());
	}

	@NonNull
	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed((track.artist() != null) ? track.artist() : "");
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		return create(getLib(), queue, index + 1);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		return create(getLib(), queue, index - 1);
	}

	@Override
	public boolean isVideo() {
		return false;
	}

	@Override
	public boolean isExternal() {
		return true;
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public String getOrigId() {
		return getId();
	}
}
