package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaPharmacy.Pharmacy;
import me.aap.fermata.ui.fragment.YoldaPharmacy.Place;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Nöbetçi eczaneler: üstte tarih (‹ Bugün ›), sonra il ve ilçe. Son seçim hatırlanır; yıldızlı il ve ilçeler
 * en üstte ve tek dokunuşla seçilir. Konum biliniyorsa her eczanenin uzaklığı yazar, en yakını önce gelir.
 * Her eczanede "Yol tarifi" ve "Ara".
 */
public class PharmacyFragment extends MainActivityFragment {
	private YoldaStyle.Theme t;
	private FrameLayout root;
	private LinearLayout listBox;
	private TextView status;
	private TextView validity;
	private int offset;
	private int seq;
	@Nullable
	private FrameLayout overlay;
	private List<Pharmacy> shown = new ArrayList<>();
	@Nullable
	private double[] me;

	@Override
	public int getFragmentId() {
		return R.id.yolda_pharmacy_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Nöbetçi eczaneler";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance;
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance;
	}

	@Override
	public boolean onBackPressed() {
		if (overlay != null) {
			closeOverlay();
			return true;
		}
		return super.onBackPressed();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		root = new FrameLayout(requireContext());
		render();
		return root;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		ViewGroup.LayoutParams lp = view.getLayoutParams();
		if (lp != null) {
			lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
			lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
			view.setLayoutParams(lp);
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (getView() != null) getView().post(this::render);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && (root != null)) render(); // nöbet günü 08:30'da değişir: dönünce güncel
	}

	// ---- ekran ----

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		overlay = null;
		YoldaStyle.applyScreenBackground(root, t);
		me = YoldaPharmacy.myLocation(ctx);
		Place il = YoldaPharmacy.selectedIl(ctx);
		Place ilce = YoldaPharmacy.selectedIlce(ctx);
		if (il != null) offset = Math.max(YoldaPharmacy.minOffset(il), Math.min(YoldaPharmacy.maxOffset(il), offset));

		ScrollView scroll = new ScrollView(ctx);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(YoldaStyle.side(ctx)), dp(YoldaStyle.top(ctx)), dp(YoldaStyle.side(ctx)), dp(110));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		TextView title = text(ctx, "Nöbetçi eczaneler", YoldaStyle.titleSp(ctx), true, t.ink);
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		col.addView(title);

		// Tarih: ‹ Bugün · 13 Kasım Cuma ›
		LinearLayout dateRow = hrow(ctx);
		LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(-1, -2);
		drp.topMargin = dp(14);
		col.addView(dateRow, drp);
		boolean canPrev = (il != null) && (offset > YoldaPharmacy.minOffset(il));
		boolean canNext = (il != null) && (offset < YoldaPharmacy.maxOffset(il));
		dateRow.addView(round(ctx, R.drawable.yi_left, "Önceki gün", canPrev, v -> {
			offset--;
			render();
		}), sq(52));
		TextView date = text(ctx, YoldaPharmacy.dayLabel(offset), 18, true, t.ink);
		date.setGravity(Gravity.CENTER);
		date.setSingleLine(true);
		date.setEllipsize(TextUtils.TruncateAt.END);
		date.setBackground(YoldaStyle.glass(ctx, t, 999));
		date.setPadding(dp(16), 0, dp(16), 0);
		date.setContentDescription("Tarih: " + YoldaPharmacy.dayLabel(offset) + ". Bugüne dönmek için dokun");
		date.setOnClickListener(v -> {
			offset = 0;
			render();
		});
		LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(52), 1f);
		dlp.leftMargin = dlp.rightMargin = dp(10);
		dateRow.addView(date, dlp);
		dateRow.addView(round(ctx, R.drawable.yi_right, "Sonraki gün", canNext, v -> {
			offset++;
			render();
		}), sq(52));
		validity = text(ctx, "", 13, false, t.muted);
		validity.setPadding(dp(4), dp(8), dp(4), 0);
		col.addView(validity);

		// İl ve ilçe
		LinearLayout filters = hrow(ctx);
		LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
		flp.topMargin = dp(14);
		col.addView(filters, flp);
		filters.addView(selector(ctx, "İl", (il != null) ? il.name() : "Seç", v -> pickIl()),
				new LinearLayout.LayoutParams(0, dp(56), 1f));
		LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(56), 1f);
		ilp.leftMargin = dp(10);
		filters.addView(selector(ctx, "İlçe", (ilce != null) ? ilce.name() : "Tüm ilçeler", v -> {
			if (il != null) pickIlce(il);
			else pickIl();
		}), ilp);

		// Yıldızlılar: tek dokunuşla seçilir
		View stars = starChips(ctx, il, ilce);
		if (stars != null) {
			LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
			slp.topMargin = dp(12);
			col.addView(stars, slp);
		}

		status = text(ctx, "", 14, false, t.muted);
		status.setPadding(dp(4), dp(14), dp(4), dp(6));
		status.setSingleLine(true);
		status.setEllipsize(TextUtils.TruncateAt.END);
		col.addView(status);
		listBox = new LinearLayout(ctx);
		listBox.setOrientation(LinearLayout.VERTICAL);
		col.addView(listBox, new LinearLayout.LayoutParams(-1, -2));

		if (il == null) {
			status.setText("İl seç (konum izni varsa bulunduğun il kendiliğinden seçilir)");
			autoSelectIl();
			return;
		}
		load(il, ilce);
	}

	/** İlk açılış: konumdaki il seçilir; konum yoksa il listesi açılır. */
	private void autoSelectIl() {
		Context ctx = requireContext();
		double[] ll = YoldaPharmacy.myLocation(ctx);
		YoldaPharmacy.provinces(ctx).main().onCompletion((list, err) -> {
			if (!isAdded()) return;
			if (list == null) {
				status.setText("İller yüklenemedi: " + reason(err));
				return;
			}
			if (ll == null) {
				pickIl();
				return;
			}
			me.aap.utils.app.App.get().getExecutor().submit(() -> {
				String name = YoldaPharmacy.provinceAt(ctx, ll);
				me.aap.utils.app.App.get().getHandler().post(() -> {
					if (!isAdded()) return;
					Place found = null;
					if (name != null) for (Place p : list) if (YoldaPharmacy.fold(p.name()).equals(YoldaPharmacy.fold(name))) found = p;
					if (found == null) {
						pickIl();
						return;
					}
					YoldaPharmacy.select(ctx, found, null);
					render();
				});
			});
		});
	}

	private void load(Place il, @Nullable Place ilce) {
		Context ctx = requireContext();
		int s = ++seq;
		status.setText("Nöbetçi eczaneler yükleniyor…");
		listBox.removeAllViews();
		YoldaPharmacy.duty(ctx, il, ilce, offset).main().onCompletion((d, err) -> {
			if (!isAdded() || (s != seq)) return;
			if (d == null) {
				status.setText("Yüklenemedi: " + reason(err));
				TextView retry = text(ctx, "Yeniden dene", 16, true, t.ink);
				retry.setGravity(Gravity.CENTER);
				retry.setBackground(YoldaStyle.glass(ctx, t, 999));
				retry.setOnClickListener(v -> load(il, ilce));
				listBox.addView(retry, new LinearLayout.LayoutParams(-2, dp(52)));
				return;
			}
			validity.setText(d.validity());
			YoldaLog.add("Eczane: " + il.name() + ((ilce != null) ? (" / " + ilce.name()) : "") + " · "
					+ YoldaPharmacy.dayLabel(offset) + " · " + d.list().size() + " eczane");
			shown = new ArrayList<>(d.list());
			showList();
			if (me != null) {
				YoldaPharmacy.resolveLocations(ctx, shown, (i, ph) -> {
					if (!isAdded() || (s != seq)) return;
					for (int k = 0; k < shown.size(); k++) {
						Pharmacy x = shown.get(k);
						if (x.name().equals(ph.name()) && x.address().equals(ph.address())) shown.set(k, ph);
					}
					listBox.removeCallbacks(reshow);
					listBox.postDelayed(reshow, 400); // konumlar geldikçe tek seferde yeniden sırala
				});
			}
		});
	}

	private final Runnable reshow = this::showList;

	private void showList() {
		if (!isAdded() || (listBox == null)) return;
		Context ctx = requireContext();
		List<Pharmacy> l = YoldaPharmacy.sortByDistance(shown, me);
		listBox.removeAllViews();
		if (l.isEmpty()) {
			status.setText("Bu seçim için nöbetçi eczane bulunamadı");
			return;
		}
		String near = "";
		if ((me != null) && l.get(0).hasLocation()) {
			near = " · en yakını " + YoldaPharmacy.fmtKm(YoldaPharmacy.km(me[0], me[1], l.get(0).lat(), l.get(0).lon()));
		} else if (me == null) {
			near = " · uzaklık için konum izni gerekli";
		}
		status.setText(l.size() + " nöbetçi eczane" + near);
		for (Pharmacy p : l) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.bottomMargin = dp(12);
			listBox.addView(card(ctx, p), lp);
		}
	}

	private View card(Context ctx, Pharmacy p) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setBackground(YoldaStyle.glass(ctx, t, 22));
		c.setPadding(dp(18), dp(16), dp(18), dp(16));
		c.setContentDescription(p.name());

		LinearLayout top = hrow(ctx);
		ImageView ic = new ImageView(ctx);
		ic.setImageResource(R.drawable.yi_pharmacy);
		ic.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		ic.setPadding(dp(8), dp(8), dp(8), dp(8));
		GradientDrawable ib = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFEF4444, 0xFFB91C1C});
		ib.setCornerRadius(dp(12));
		ic.setBackground(ib);
		top.addView(ic, sq(40));
		LinearLayout names = new LinearLayout(ctx);
		names.setOrientation(LinearLayout.VERTICAL);
		names.setPadding(dp(12), 0, dp(8), 0);
		TextView n = text(ctx, p.name(), 19, true, t.ink);
		n.setMaxLines(2);
		n.setEllipsize(TextUtils.TruncateAt.END);
		names.addView(n);
		if (!p.district().isEmpty()) {
			TextView d = text(ctx, p.district(), 13, true, t.muted);
			d.setSingleLine(true);
			names.addView(d);
		}
		top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
		if ((me != null) && p.hasLocation()) {
			TextView km = text(ctx, YoldaPharmacy.fmtKm(YoldaPharmacy.km(me[0], me[1], p.lat(), p.lon())), 15, true,
					YoldaStyle.ON_ACCENT);
			km.setSingleLine(true);
			km.setGravity(Gravity.CENTER);
			km.setPadding(dp(12), dp(6), dp(12), dp(6));
			km.setBackground(YoldaStyle.accentFill(ctx, 999));
			top.addView(km);
		}
		c.addView(top);

		if (!p.address().isEmpty()) {
			TextView a = text(ctx, p.address(), 15, false, t.ink);
			a.setMaxLines(3);
			a.setEllipsize(TextUtils.TruncateAt.END);
			a.setPadding(0, dp(10), 0, 0);
			c.addView(a);
		}
		if (!p.note().isEmpty()) {
			TextView no = text(ctx, "→ " + p.note(), 14, false, t.muted);
			no.setMaxLines(2);
			no.setEllipsize(TextUtils.TruncateAt.END);
			no.setPadding(0, dp(4), 0, 0);
			c.addView(no);
		}

		LinearLayout btns = hrow(ctx);
		LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
		blp.topMargin = dp(12);
		c.addView(btns, blp);
		TextView go = button(ctx, R.drawable.yi_nav, "Yol tarifi", true, v -> navigate(p));
		btns.addView(go, new LinearLayout.LayoutParams(0, dp(48), 1f));
		if (!p.phone().isEmpty()) {
			TextView call = button(ctx, R.drawable.yi_phone, "Ara", false, v -> call(p));
			call.setContentDescription("Ara: " + p.phone());
			LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(48), 1f);
			clp.leftMargin = dp(10);
			btns.addView(call, clp);
		}
		return c;
	}

	private void navigate(Pharmacy p) {
		Context ctx = requireContext();
		YoldaStyle.haptic(root);
		String dest = p.hasLocation() ? (p.lat() + "," + p.lon()) : (p.name() + ", " + p.address());
		YoldaLog.add("Eczane: yol tarifi " + p.name());
		NavFragment.navigateTo(ctx, dest, false);
	}

	private void call(Pharmacy p) {
		Context ctx = requireContext();
		YoldaStyle.haptic(root);
		String num = p.phone().replaceAll("[^0-9+]", "");
		try {
			ctx.getApplicationContext().startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} catch (Exception ex) {
			me.aap.utils.ui.UiUtils.showAlert(ctx, "Arama açılamadı: " + p.phone());
		}
	}

	// ---- il / ilçe seçimi ----

	private void pickIl() {
		Context ctx = requireContext();
		YoldaPharmacy.provinces(ctx).main().onCompletion((list, err) -> {
			if (!isAdded()) return;
			if (list == null) {
				if (status != null) status.setText("İller yüklenemedi: " + reason(err));
				return;
			}
			showPicker("İl seç", "il", list, null, p -> {
				Place old = YoldaPharmacy.selectedIl(ctx);
				boolean same = (old != null) && old.slug().equals(p.slug());
				YoldaPharmacy.select(ctx, p, same ? YoldaPharmacy.selectedIlce(ctx) : null);
				render();
			});
		});
	}

	private void pickIlce(Place il) {
		Context ctx = requireContext();
		status.setText("İlçeler yükleniyor…");
		YoldaPharmacy.districts(ctx, il).main().onCompletion((list, err) -> {
			if (!isAdded()) return;
			if (list == null) {
				status.setText("İlçeler yüklenemedi: " + reason(err));
				return;
			}
			status.setText("");
			showPicker(il.name() + " · ilçe seç", "ilce", list, il, p -> {
				YoldaPharmacy.select(ctx, il, (p.slug().isEmpty()) ? null : p);
				render();
			});
		});
	}

	/** İlçe yıldızının anahtarı "ankara/cankaya". */
	private static String starKey(String kind, @Nullable Place il, Place p) {
		return "ilce".equals(kind) && (il != null) ? (il.slug() + "/" + p.slug()) : p.slug();
	}

	private void showPicker(String title, String kind, List<Place> items, @Nullable Place il,
													java.util.function.Consumer<Place> pick) {
		Context ctx = requireContext();
		closeOverlay();
		FrameLayout ov = new FrameLayout(ctx);
		ov.setBackgroundColor(0xCC000000);
		ov.setClickable(true);
		ov.setOnClickListener(v -> closeOverlay());
		LinearLayout panel = new LinearLayout(ctx);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setClickable(true);
		GradientDrawable pb = new GradientDrawable();
		pb.setCornerRadius(dp(26));
		pb.setColor(YoldaStyle.alpha(t.bg1 | 0xFF000000, 0.97f));
		pb.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
		panel.setBackground(pb);
		panel.setPadding(dp(18), dp(16), dp(10), dp(12));
		int w = Math.min(dp(560), root.getWidth() - dp(32));
		FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((w > 0) ? w : -1, -1, Gravity.CENTER);
		plp.topMargin = plp.bottomMargin = dp(20);
		ov.addView(panel, plp);

		LinearLayout head = hrow(ctx);
		TextView tt = text(ctx, title, 22, true, t.ink);
		tt.setSingleLine(true);
		tt.setEllipsize(TextUtils.TruncateAt.END);
		head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
		head.addView(round(ctx, R.drawable.yi_close, "Kapat", true, v -> closeOverlay()), sq(48));
		panel.addView(head);

		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(true);
		LinearLayout lst = new LinearLayout(ctx);
		lst.setOrientation(LinearLayout.VERTICAL);
		sv.addView(lst, new ScrollView.LayoutParams(-1, -2));
		LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(-1, 0, 1f);
		svp.topMargin = dp(8);
		panel.addView(sv, svp);

		List<Place> starred = new ArrayList<>();
		for (Place p : items) if (YoldaPharmacy.isStar(ctx, kind, starKey(kind, il, p))) starred.add(p);
		if ("ilce".equals(kind)) lst.addView(pickRow(ctx, kind, il, new Place("", "Tüm ilçeler"), false, pick));
		if (!starred.isEmpty()) {
			lst.addView(section(ctx, "★ Yıldızlılar"));
			for (Place p : starred) lst.addView(pickRow(ctx, kind, il, p, true, pick));
			lst.addView(section(ctx, "Tümü"));
		}
		for (Place p : items) lst.addView(pickRow(ctx, kind, il, p, true, pick));
		overlay = ov;
		root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
		ov.setAlpha(0f);
		ov.animate().alpha(1f).setDuration(140).start();
	}

	private View pickRow(Context ctx, String kind, @Nullable Place il, Place p, boolean starable,
											 java.util.function.Consumer<Place> pick) {
		LinearLayout r = hrow(ctx);
		r.setMinimumHeight(dp(56));
		r.setPadding(dp(8), 0, 0, 0);
		Place sel = "il".equals(kind) ? YoldaPharmacy.selectedIl(ctx) : YoldaPharmacy.selectedIlce(ctx);
		boolean on = ((sel == null) && p.slug().isEmpty() && "ilce".equals(kind))
				|| ((sel != null) && sel.slug().equals(p.slug()));
		TextView name = text(ctx, p.name(), 18, on, on ? t.ink : t.ink);
		name.setSingleLine(true);
		name.setEllipsize(TextUtils.TruncateAt.END);
		name.setGravity(Gravity.CENTER_VERTICAL);
		name.setMinHeight(dp(56));
		name.setClickable(true);
		name.setOnClickListener(v -> {
			closeOverlay();
			pick.accept(p);
		});
		if (on) r.setBackground(YoldaStyle.selected(ctx, 14));
		r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
		if (starable) {
			String key = starKey(kind, il, p);
			boolean star = YoldaPharmacy.isStar(ctx, kind, key);
			ImageView s = new ImageView(ctx);
			s.setImageResource(star ? R.drawable.yi_star : R.drawable.yi_star_border);
			s.setImageTintList(ColorStateList.valueOf(star ? 0xFFFFC857 : 0x99FFFFFF));
			s.setPadding(dp(12), dp(12), dp(12), dp(12));
			s.setContentDescription((star ? "Yıldızı kaldır: " : "Yıldızla: ") + p.name());
			s.setClickable(true);
			s.setOnClickListener(v -> {
				YoldaStyle.haptic(v);
				boolean nowOn = YoldaPharmacy.toggleStar(ctx, kind, key, p.name());
				s.setImageResource(nowOn ? R.drawable.yi_star : R.drawable.yi_star_border);
				s.setImageTintList(ColorStateList.valueOf(nowOn ? 0xFFFFC857 : 0x99FFFFFF));
				s.setContentDescription((nowOn ? "Yıldızı kaldır: " : "Yıldızla: ") + p.name());
			});
			r.addView(s, sq(52));
		}
		return r;
	}

	/** Yıldızlı il ve ilçeler: üstte kısayol çipleri (seçili olan vurgulu). */
	@Nullable
	private View starChips(Context ctx, @Nullable Place il, @Nullable Place ilce) {
		List<String> ils = YoldaPharmacy.stars(ctx, "il");
		List<String> ilces = YoldaPharmacy.stars(ctx, "ilce");
		if (ils.isEmpty() && ilces.isEmpty()) return null;
		LinearLayout row = hrow(ctx);
		for (String s : ilces) { // "ankara/cankaya|Çankaya"
			int bar = s.indexOf('|'), sl = s.indexOf('/');
			if ((bar < 0) || (sl < 0) || (sl > bar)) continue;
			String ilSlug = s.substring(0, sl);
			Place pIlce = new Place(s.substring(sl + 1, bar), s.substring(bar + 1));
			Place pIl = ilPlace(ctx, ilSlug);
			boolean on = (il != null) && il.slug().equals(ilSlug) && (ilce != null) && ilce.slug().equals(pIlce.slug());
			row.addView(chip(ctx, "★ " + pIlce.name(), on, v -> {
				YoldaPharmacy.select(ctx, pIl, pIlce);
				render();
			}), chipLp());
		}
		for (String s : ils) {
			Place p = Place.decode(s);
			if (p == null) continue;
			boolean on = (il != null) && il.slug().equals(p.slug()) && (ilce == null);
			row.addView(chip(ctx, "★ " + p.name(), on, v -> {
				YoldaPharmacy.select(ctx, p, null);
				render();
			}), chipLp());
		}
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(row);
		return hs;
	}

	/** İl yıldızlı değilse adını önbellekteki il listesinden bulur. */
	private Place ilPlace(Context ctx, String slug) {
		for (String s : YoldaPharmacy.stars(ctx, "il")) {
			Place p = Place.decode(s);
			if ((p != null) && p.slug().equals(slug)) return p;
		}
		try {
			List<Place> l = YoldaPharmacy.provinces(ctx).peek();
			if (l != null) for (Place p : l) if (p.slug().equals(slug)) return p;
		} catch (Throwable ignore) {
		}
		return new Place(slug, YoldaPharmacy.title(slug));
	}

	private void closeOverlay() {
		View v = overlay;
		overlay = null;
		if ((v != null) && (root != null)) root.removeView(v);
	}

	// ---- yapı taşları ----

	private View selector(Context ctx, String label, String value, View.OnClickListener click) {
		LinearLayout b = hrow(ctx);
		b.setBackground(YoldaStyle.glass(ctx, t, 18));
		b.setPadding(dp(16), 0, dp(12), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription(label + ": " + value);
		LinearLayout txt = new LinearLayout(ctx);
		txt.setOrientation(LinearLayout.VERTICAL);
		TextView l = text(ctx, label.toUpperCase(new java.util.Locale("tr", "TR")), 11, true, t.muted);
		l.setLetterSpacing(0.1f);
		txt.addView(l);
		TextView v = text(ctx, value, 17, true, t.ink);
		v.setSingleLine(true);
		v.setEllipsize(TextUtils.TruncateAt.END);
		txt.addView(v);
		b.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
		ImageView ch = new ImageView(ctx);
		ch.setImageResource(R.drawable.yi_chevron);
		ch.setImageTintList(ColorStateList.valueOf(t.muted));
		ch.setRotation(90);
		b.addView(ch, sq(22));
		return b;
	}

	private TextView chip(Context ctx, String s, boolean on, View.OnClickListener click) {
		TextView c = text(ctx, s, 15, true, on ? YoldaStyle.ON_ACCENT : t.ink);
		c.setSingleLine(true);
		c.setGravity(Gravity.CENTER);
		c.setPadding(dp(16), 0, dp(16), 0);
		c.setBackground(on ? YoldaStyle.accentFill(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
		c.setClickable(true);
		c.setOnClickListener(click);
		return c;
	}

	private LinearLayout.LayoutParams chipLp() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(46));
		lp.rightMargin = dp(8);
		return lp;
	}

	private TextView button(Context ctx, @DrawableRes int icon, String s, boolean primary, View.OnClickListener click) {
		TextView b = text(ctx, s, 16, true, primary ? YoldaStyle.ON_ACCENT : t.ink);
		b.setSingleLine(true);
		b.setGravity(Gravity.CENTER);
		b.setBackground(primary ? YoldaStyle.accentFill(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
		android.graphics.drawable.Drawable d = ctx.getDrawable(icon);
		if (d != null) {
			d = d.mutate();
			d.setTint(primary ? YoldaStyle.ON_ACCENT : t.ink);
			d.setBounds(0, 0, dp(20), dp(20));
			b.setCompoundDrawablesRelative(d, null, null, null);
			b.setCompoundDrawablePadding(dp(8));
		}
		b.setPadding(dp(16), 0, dp(16), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		return b;
	}

	private ImageView round(Context ctx, @DrawableRes int icon, String desc, boolean enabled, View.OnClickListener click) {
		ImageView b = new ImageView(ctx);
		b.setImageResource(icon);
		b.setImageTintList(ColorStateList.valueOf(t.ink));
		b.setPadding(dp(13), dp(13), dp(13), dp(13));
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setContentDescription(desc);
		b.setEnabled(enabled);
		b.setAlpha(enabled ? 1f : 0.35f);
		b.setClickable(true);
		b.setOnClickListener(v -> {
			if (v.isEnabled()) click.onClick(v);
		});
		return b;
	}

	private TextView section(Context ctx, String s) {
		TextView h = text(ctx, s, 13, true, t.muted);
		h.setPadding(dp(8), dp(14), dp(8), dp(6));
		return h;
	}

	private LinearLayout hrow(Context ctx) {
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);
		return r;
	}

	private LinearLayout.LayoutParams sq(int s) {
		return new LinearLayout.LayoutParams(dp(s), dp(s));
	}

	private static String reason(@Nullable Throwable err) {
		if (err == null) return "bilinmeyen hata";
		if ((err instanceof java.net.UnknownHostException) || (err instanceof java.net.SocketTimeoutException))
			return "internet bağlantısı yok";
		String m = err.getMessage();
		return (m != null) ? m : err.getClass().getSimpleName();
	}

	private TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size);
		v.setTextColor(color);
		if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}

	// ---- testler için ----

	public int shownCount() {
		return shown.size();
	}

	@Nullable
	public CharSequence statusText() {
		return (status != null) ? status.getText() : null;
	}
}
