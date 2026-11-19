package me.aap.fermata.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * TV ve radyo kanallarının tek düzenleyicisi: kartın içinden ("Kanalları düzenle") ve Kanallar sayfasındaki
 * "Düzenle"den aynı pencere açılır. Her satırda ≡ sürükle, ▲ ▼ taşı, ★ favori, ada dokun gizle / göster.
 * Her değişiklik hemen kaydedilir; kart, Kanallar sayfası ve kanal atlama aynı sırayı izler.
 */
public final class YoldaChannelEditor {
	private YoldaChannelEditor() {
	}

	/**
	 * Yeni sıra: düzenlenen türün kanalları (TV ya da radyo) verilen sırayla, ardından eski sıradaki diğer
	 * kanallar (öbür tür) aynı sırayla. Böylece radyoyu sıralamak TV sırasını bozmaz.
	 */
	static List<String> merge(List<String> old, List<String> edited) {
		Set<String> ed = new HashSet<>(edited);
		List<String> out = new ArrayList<>(edited);
		for (String id : old) if (!ed.contains(id)) out.add(id);
		return out;
	}

	/** Düzenleyiciyi aç. items: o türün bütün kanalları; changed: her değişiklikten sonra (kart / sayfa yenilensin). */
	public static void show(MainActivityDelegate a, boolean radio, List<MediaLib.PlayableItem> items, Runnable changed) {
		Context ctx = a.getContext();
		if (items.isEmpty()) {
			YoldaStyle.toast(ctx, "Kanal listesi henüz yüklenmedi");
			return;
		}
		Activity act = (Activity) a.getAppActivity();
		ViewGroup host = act.findViewById(android.R.id.content);
		if (host == null) return;
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		float d = ctx.getResources().getDisplayMetrics().density;
		List<MediaLib.PlayableItem> work = new ArrayList<>(items);
		YoldaChannelOrder.sort(work, YoldaChannelOrder.order(ctx), MediaLib.PlayableItem::getId);

		FrameLayout scrim = new FrameLayout(ctx);
		scrim.setBackgroundColor(0xB3000000);
		scrim.setClickable(true);
		LinearLayout panel = new LinearLayout(ctx);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(dp(d, 16), dp(d, 12), dp(d, 12), dp(d, 12));
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(dp(d, 24));
		bg.setColor((t.base & 0x00FFFFFF) | 0xF7000000); // opak: satırlar okunsun
		bg.setStroke(Math.max(1, dp(d, 1)), 0x4DFFFFFF);
		panel.setBackground(bg);
		panel.setClickable(true);
		FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -1);
		int m = dp(d, 16);
		plp.setMargins(m, m + YoldaStyle.cutoutTop(ctx), m, m);
		scrim.addView(panel, plp);
		Runnable close = () -> host.removeView(scrim);
		scrim.setOnClickListener(v -> close.run());

		LinearLayout head = new LinearLayout(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.addView(text(ctx, radio ? "Radyoları düzenle" : "Kanalları düzenle", YoldaStyle.titleSp(ctx), true, t.ink),
				new LinearLayout.LayoutParams(0, -2, 1f));
		head.addView(button(ctx, d, R.drawable.yi_close, "Kapat", v -> close.run()), new LinearLayout.LayoutParams(dp(d, 48), dp(d, 48)));
		panel.addView(head);
		TextView hint = text(ctx, "≡ tutup sürükle ya da ▲ ▼ ile taşı · ★ favori · ada dokun: gizle / göster", 14, false, YoldaStyle.TEXT_2);
		hint.setPadding(0, dp(d, 4), 0, dp(d, 8));
		panel.addView(hint);

		RecyclerView rv = new RecyclerView(ctx);
		rv.setLayoutManager(new LinearLayoutManager(ctx));
		rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
		panel.addView(rv, new LinearLayout.LayoutParams(-1, 0, 1f));
		ItemTouchHelper[] th = new ItemTouchHelper[1];
		Runnable save = () -> {
			List<String> ids = new ArrayList<>();
			for (MediaLib.PlayableItem pi : work) ids.add(pi.getId());
			YoldaChannelOrder.save(ctx, merge(YoldaChannelOrder.order(ctx), ids));
			changed.run();
		};
		MediaLib.Favorites favs = a.getLib().getFavorites();
		RecyclerView.Adapter<RecyclerView.ViewHolder> ad = new RecyclerView.Adapter<>() {
			@NonNull
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				LinearLayout r = new LinearLayout(ctx);
				r.setGravity(Gravity.CENTER_VERTICAL);
				r.setPadding(0, dp(d, 4), 0, dp(d, 4));
				r.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
				return new RecyclerView.ViewHolder(r) {
				};
			}

			@android.annotation.SuppressLint("ClickableViewAccessibility")
			@Override
			public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int i) {
				LinearLayout r = (LinearLayout) h.itemView;
				r.removeAllViews();
				MediaLib.PlayableItem pi = work.get(i);
				boolean hidden = YoldaChannelOrder.isHidden(ctx, pi.getName());
				TextView grip = text(ctx, "≡", 26, true, YoldaStyle.TEXT_2);
				grip.setGravity(Gravity.CENTER);
				grip.setContentDescription("Sürükle: " + pi.getName());
				grip.setOnTouchListener((v, e) -> {
					if (e.getActionMasked() == MotionEvent.ACTION_DOWN) th[0].startDrag(h);
					return false;
				});
				r.addView(grip, new LinearLayout.LayoutParams(dp(d, 44), dp(d, 52)));
				TextView n = text(ctx, (i + 1) + ".  " + pi.getName() + (hidden ? "  · gizli" : ""), 17, !hidden, hidden ? YoldaStyle.TEXT_3 : t.ink);
				n.setSingleLine(true);
				n.setEllipsize(TextUtils.TruncateAt.END);
				n.setMinHeight(dp(d, 52));
				n.setGravity(Gravity.CENTER_VERTICAL);
				n.setContentDescription((hidden ? "Göster: " : "Gizle: ") + pi.getName());
				n.setOnClickListener(v -> {
					YoldaChannelOrder.setHidden(ctx, pi.getName(), !hidden);
					YoldaStyle.toast(ctx, pi.getName() + (hidden ? " gösteriliyor" : " gizlendi"));
					changed.run();
					notifyItemChanged(h.getBindingAdapterPosition());
				});
				r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
				boolean fav = favs.isFavoriteItem(pi);
				ImageView st = button(ctx, d, fav ? R.drawable.yi_star : R.drawable.yi_star_border, (fav ? "Favoriden çıkar: " : "Favori yap: ") + pi.getName(), v -> {
					String[] e = YoldaFavs.entryOf(pi); // Favoriler kartı da güncellensin
					if (favs.isFavoriteItem(pi)) {
						favs.removeItem(pi);
						if (e != null) YoldaFavs.remove(ctx, e[0], e[2]);
					} else {
						favs.addItem(pi);
						if (e != null) YoldaFavs.add(ctx, e[0], e[1], e[2], e[3]);
					}
					changed.run();
					notifyItemChanged(h.getBindingAdapterPosition());
				});
				if (fav) st.setImageTintList(ColorStateList.valueOf(0xFFFFD27A));
				LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(d, 48), dp(d, 48));
				bl.leftMargin = dp(d, 4);
				r.addView(st, bl);
				ImageView up = button(ctx, d, R.drawable.yi_up, "Yukarı: " + pi.getName(), v -> {
					int idx = h.getBindingAdapterPosition();
					if (idx <= 0) return;
					YoldaChannelOrder.move(work, idx, -1);
					save.run();
					notifyItemMoved(idx, idx - 1);
					notifyItemRangeChanged(idx - 1, 2);
				});
				up.setAlpha((i == 0) ? 0.3f : 1f);
				r.addView(up, new LinearLayout.LayoutParams(bl));
				ImageView dn = button(ctx, d, R.drawable.yi_down, "Aşağı: " + pi.getName(), v -> {
					int idx = h.getBindingAdapterPosition();
					if ((idx < 0) || (idx >= work.size() - 1)) return;
					YoldaChannelOrder.move(work, idx, 1);
					save.run();
					notifyItemMoved(idx, idx + 1);
					notifyItemRangeChanged(idx, 2);
				});
				dn.setAlpha((i == work.size() - 1) ? 0.3f : 1f);
				r.addView(dn, new LinearLayout.LayoutParams(bl));
			}

			@Override
			public int getItemCount() {
				return work.size();
			}
		};
		rv.setAdapter(ad);
		th[0] = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
			boolean moved;

			@Override
			public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder x, @NonNull RecyclerView.ViewHolder y) {
				int from = x.getBindingAdapterPosition(), to = y.getBindingAdapterPosition();
				if ((from < 0) || (to < 0)) return false;
				work.add(to, work.remove(from));
				ad.notifyItemMoved(from, to);
				moved = true;
				return true;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
			}

			@Override
			public boolean isLongPressDragEnabled() {
				return true; // satıra basılı tutmak da sürükler
			}

			@Override
			public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int state) {
				super.onSelectedChanged(vh, state);
				if ((vh != null) && (state == ItemTouchHelper.ACTION_STATE_DRAG)) {
					vh.itemView.setBackground(YoldaStyle.glass(ctx, t, 14));
					YoldaStyle.haptic(vh.itemView);
				}
			}

			@Override
			public void clearView(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder vh) {
				super.clearView(r, vh);
				vh.itemView.setBackground(null);
				if (moved) {
					moved = false;
					save.run();
					r.post(ad::notifyDataSetChanged); // sıra numaraları yenilensin
				}
			}
		});
		th[0].attachToRecyclerView(rv);
		host.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
	}

	private static int dp(float d, int v) {
		return Math.round(v * d);
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(YoldaStyle.type(size) * YoldaStyle.textScale(ctx));
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private static ImageView button(Context ctx, float d, int icon, String desc, View.OnClickListener click) {
		ImageView b = new ImageView(ctx);
		b.setImageResource(icon);
		b.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		int p = dp(d, 12);
		b.setPadding(p, p, p, p);
		b.setBackground(YoldaStyle.glass(ctx, YoldaStyle.current(ctx), 999));
		b.setContentDescription(desc);
		b.setClickable(true);
		b.setOnClickListener(click);
		return b;
	}
}
