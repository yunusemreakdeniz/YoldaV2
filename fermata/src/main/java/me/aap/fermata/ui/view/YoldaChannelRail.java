package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaLog;

/**
 * Yayında videoya dokununca sağda, videonun üstünde açılan kanal şeridi: kaydırılır, dokunulan kanala geçilir.
 * Kontrollerle birlikte gizlenir. Yalnız TV kanalı çalarken görünür (YouTube videosu, film vb. değil).
 */
@SuppressLint("ViewConstructor")
public final class YoldaChannelRail extends FrameLayout {
	private final LinearLayout list;
	private final ScrollView scroll;
	@Nullable
	private String shownFor;

	private YoldaChannelRail(Context ctx) {
		super(ctx);
		setClickable(true);
		// Koyu, okunaklı panel: altındaki görüntü satırlara karışmaz
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(dp(22));
		bg.setColor(0xE6101218);
		bg.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
		setBackground(bg);
		setClipToOutline(true);
		setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		scroll = new ScrollView(ctx);
		scroll.setVerticalScrollBarEnabled(false);
		scroll.setClipToPadding(false);
		scroll.setPadding(dp(8), dp(8), dp(8), dp(8));
		list = new LinearLayout(ctx);
		list.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
		addView(scroll, new LayoutParams(-1, -1));
		setContentDescription("Kanal listesi");
	}

	/** Videonun içindeki şerit (yoksa kurulur). */
	public static YoldaChannelRail of(ViewGroup video) {
		for (int i = 0; i < video.getChildCount(); i++) {
			if (video.getChildAt(i) instanceof YoldaChannelRail r) return r;
		}
		YoldaChannelRail r = new YoldaChannelRail(video.getContext());
		r.setVisibility(GONE);
		r.setElevation(r.dp(6));
		// saat (varsa) son çocuk kalmalı: VideoView onu son sıradan bulur
		video.addView(r, Math.min(3, video.getChildCount()), railLp(video));
		return r;
	}

	private static FrameLayout.LayoutParams railLp(ViewGroup video) {
		float d = video.getResources().getDisplayMetrics().density;
		int w = video.getWidth();
		int rw = Math.round(Math.min(320 * d, Math.max(230 * d, w * 0.32f)));
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(rw, -1, Gravity.END);
		lp.topMargin = lp.bottomMargin = lp.rightMargin = Math.round(12 * d);
		return lp;
	}

	/** Testler için: şeritteki kanal sayısı (görünürse). */
	public int channelCount() {
		return (getVisibility() == VISIBLE) ? list.getChildCount() : 0;
	}

	/** Testler için: sıradaki kanala dokunur. */
	public boolean clickChannel(int i) {
		if ((i < 0) || (i >= list.getChildCount())) return false;
		return list.getChildAt(i).performClick();
	}

	/** Çalan bir TV kanalıysa şeridi doldurup gösterir; değilse gizli kalır. */
	public void showFor(MainActivityDelegate a) {
		MediaLib.PlayableItem cur = a.getCurrentPlayable();
		// kanal henüz açılıyorsa (dokunuldu, yayın bekleniyor) o kanal
		if (cur == null) cur = me.aap.fermata.media.service.MediaSessionCallback.yoldaOpeningItem();
		if ((cur == null) || !cur.isVideo() || !isTvChannel(cur)) {
			setVisibility(GONE);
			return;
		}
		ViewGroup p = (ViewGroup) getParent();
		if (p != null) setLayoutParams(railLp(p));
		setVisibility(VISIBLE);
		setAlpha(0f);
		animate().alpha(1f).setDuration(160).start(); // yerinde belirir (kayma yok)
		if (cur.getId().equals(shownFor) && (list.getChildCount() > 0)) {
			mark(cur.getId());
			return;
		}
		MediaLib.PlayableItem c = cur;
		shownFor = c.getId();
		channels(a, c).main().onSuccess(l -> {
			if (!c.getId().equals(shownFor)) return;
			fill(a, l, c.getId());
		});
	}

	public void hide() {
		if (getVisibility() != VISIBLE) return;
		animate().alpha(0f).setDuration(140).withEndAction(() -> setVisibility(GONE)).start();
	}

	/** Dokunuldukça kontroller açık kalsın (kaydırırken şerit kaybolmasın). */
	@Override
	public boolean dispatchTouchEvent(MotionEvent e) {
		try {
			MainActivityDelegate.get(getContext()).getControlPanel().keepVideoControls();
		} catch (Throwable ignore) {
		}
		return super.dispatchTouchEvent(e);
	}

	private static boolean isTvChannel(MediaLib.PlayableItem pi) {
		for (MediaLib.Item i = pi.getParent(); i != null; i = i.getParent()) {
			if ("TV".equals(i.getId())) return true; // TV eklentisinin kökü (TvRootItem.ID)
		}
		return false;
	}

	/** TV kanalları (radyo hariç): çalan kanalın kökündeki tüm video kanallar. */
	private static me.aap.utils.async.FutureSupplier<List<MediaLib.PlayableItem>> channels(MainActivityDelegate a,
																																														MediaLib.PlayableItem cur) {
		MediaLib.Item root = cur.getParent();
		while ((root != null) && !"TV".equals(root.getId())) root = root.getParent();
		if (!(root instanceof MediaLib.BrowsableItem br)) {
			return me.aap.utils.async.Completed.completed(new ArrayList<>());
		}
		return br.getPlayableChildren(true).map(l -> {
			List<MediaLib.PlayableItem> out = new ArrayList<>();
			for (MediaLib.PlayableItem pi : l) if (pi.isVideo()) out.add(pi);
			return out;
		});
	}

	private void fill(MainActivityDelegate a, List<MediaLib.PlayableItem> l, String curId) {
		list.removeAllViews();
		Context ctx = getContext();
		for (MediaLib.PlayableItem pi : l) {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setPadding(dp(8), dp(6), dp(10), dp(6));
			row.setMinimumHeight(dp(60));
			row.setClipToOutline(true);
			row.setClickable(true);
			row.setFocusable(true);
			row.setTag(pi.getId());
			row.setContentDescription(pi.getName());
			FrameLayout plate = new FrameLayout(ctx);
			GradientDrawable pb = new GradientDrawable();
			pb.setCornerRadius(dp(10));
			pb.setColor(0xF2FFFFFF);
			plate.setBackground(pb);
			plate.setClipToOutline(true);
			ImageView ph = new ImageView(ctx);
			ph.setImageResource(R.drawable.yi_tv);
			ph.setImageTintList(ColorStateList.valueOf(0x44000000));
			ph.setPadding(dp(10), dp(10), dp(10), dp(10));
			plate.addView(ph, new FrameLayout.LayoutParams(-1, -1));
			ImageView logo = new ImageView(ctx);
			logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
			logo.setPadding(dp(5), dp(5), dp(5), dp(5));
			plate.addView(logo, new FrameLayout.LayoutParams(-1, -1));
			row.addView(plate, new LinearLayout.LayoutParams(dp(64), dp(44)));
			TextView name = new TextView(ctx);
			name.setText(pi.getName());
			name.setTextColor(0xFFFFFFFF);
			name.setTextSize(15);
			name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			name.setSingleLine(true);
			name.setEllipsize(TextUtils.TruncateAt.END);
			name.setPadding(dp(12), 0, 0, 0);
			row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
			row.setOnClickListener(v -> {
				YoldaStyle.haptic(v);
				YoldaLog.add("Kanal şeridi: " + pi.getName());
				shownFor = pi.getId();
				mark(pi.getId());
				a.getBody().playItem(pi);
				a.getControlPanel().keepVideoControls();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.bottomMargin = dp(6);
			list.addView(row, lp);
			pi.getIconUri().main().onSuccess(u -> {
				if (u == null) return;
				a.getLib().getBitmap(u.toString(), true, true).main().onSuccess(b -> {
					if (b == null) return;
					logo.setImageBitmap(b);
					ph.setVisibility(INVISIBLE);
				});
			});
		}
		mark(curId);
	}

	/** Çalan kanal vurgulu ve görünür yerde. */
	private void mark(String id) {
		View sel = null;
		for (int i = 0; i < list.getChildCount(); i++) {
			View r = list.getChildAt(i);
			boolean on = id.equals(r.getTag());
			GradientDrawable g = new GradientDrawable();
			g.setCornerRadius(dp(14));
			g.setColor(on ? 0x33FFFFFF : 0x0FFFFFFF);
			if (on) g.setStroke(dp(2), 0xE6FFFFFF);
			r.setBackground(g);
			if (on) sel = r;
		}
		View s = sel;
		if (s != null) scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, s.getTop() - scroll.getHeight() / 3)));
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
