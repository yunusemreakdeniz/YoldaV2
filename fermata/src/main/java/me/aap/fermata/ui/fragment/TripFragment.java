package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Yolculuk özeti: araçtan inince gelen bildirime dokununca açılır. Nereden nereye, büyük mesafe, süre, ortalama
 * ve en yüksek hız, geçilen radarlar, beklenen süre; haritada göster; önceki yolculuklar (dokun: onu göster).
 * Boşluklar 8dp ızgarasında: kenar 16, aralık 8, bölüm 24.
 */
public class TripFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	/** Gösterilen yolculuğun sırası (0 = en son). */
	private int shown;
	private long clearAskedAt;
	/** Açılırken gösterilecek yolculuk (kart / bildirim); açılınca sıfırlanır. */
	public static volatile int showIndex;

	/** Bu ayın özeti: "Bu ay 12 yolculuk · 486 km · 9 sa 20 dk" (yoksa kısa açıklama). */
	public static String monthLine(Context ctx) {
		List<YoldaRadar.TripItem> l = YoldaRadar.tripItems(ctx);
		if (l.isEmpty()) return "Henüz yolculuk yok · araçla 1 km'den uzun yolculuklar burada";
		java.util.Calendar c = java.util.Calendar.getInstance();
		int m = c.get(java.util.Calendar.MONTH), y = c.get(java.util.Calendar.YEAR);
		int n = 0, meters = 0;
		long ms = 0;
		for (YoldaRadar.TripItem it : l) {
			c.setTimeInMillis(it.rec().startWall());
			if ((c.get(java.util.Calendar.MONTH) != m) || (c.get(java.util.Calendar.YEAR) != y)) continue;
			n++;
			meters += it.rec().meters();
			ms += it.rec().durationMs();
		}
		if (n == 0) return "Bu ay yolculuk yok · toplam " + l.size() + " kayıt";
		return "Bu ay " + n + " yolculuk · " + String.format(new Locale("tr"), "%.0f km", meters / 1000f) + " · "
				+ YoldaDriveCore.duration(ms);
	}

	@Override
	public int getFragmentId() {
		return R.id.yolda_trip_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Yolculuk özeti";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance;
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
		root = new FrameLayout(requireContext());
		shown = showIndex;
		showIndex = 0;
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
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) {
			shown = showIndex;
			showIndex = 0;
			render();
		}
	}

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		YoldaStyle.applyScreenBackground(root, t);
		ScrollView sv = new ScrollView(ctx);
		sv.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(YoldaStyle.side(ctx)), dp(YoldaStyle.top(ctx)), dp(YoldaStyle.side(ctx)), dp(160));
		sv.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

		col.addView(text(ctx, "Yolculuk özeti", YoldaStyle.titleSp(ctx), true, t.ink));
		TextView month = text(ctx, monthLine(ctx), 14, true, t.accent | 0xFF000000);
		month.setPadding(0, dp(4), 0, 0);
		col.addView(month);
		List<YoldaRadar.TripItem> trips = YoldaRadar.tripItems(ctx);
		if (trips.isEmpty()) {
			TextView m = text(ctx, "Henüz kayıtlı yolculuk yok. Araçla 1 km'den uzun bir yolculuktan sonra burada özeti görünür.",
					16, false, t.muted);
			m.setPadding(0, dp(24), 0, 0);
			col.addView(m);
			return;
		}
		int idx = Math.max(0, Math.min(shown, trips.size() - 1));
		YoldaRadar.TripItem it = trips.get(idx);
		YoldaDriveCore.TripRec r = it.rec();
		Locale tr = new Locale("tr");

		// Nereden nereye ve ne zaman
		String route = it.route().isEmpty() ? "Yolculuk" : it.route();
		TextView rt = text(ctx, route, 22, true, t.ink);
		rt.setPadding(0, dp(16), 0, 0);
		col.addView(rt);
		String when = new SimpleDateFormat("d MMMM EEEE, HH:mm", tr).format(new Date(r.startWall())) + " – "
				+ new SimpleDateFormat("HH:mm", tr).format(new Date(r.endWall()));
		col.addView(text(ctx, when, 14, false, t.muted));

		// Büyük mesafe
		LinearLayout hero = card(ctx);
		LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
		hl.topMargin = dp(16);
		LinearLayout big = new LinearLayout(ctx);
		big.setGravity(Gravity.BOTTOM);
		big.addView(text(ctx, String.format(tr, "%.1f", r.meters() / 1000f), 56, true, t.ink));
		TextView km = text(ctx, " km", 22, true, t.muted);
		km.setPadding(0, 0, 0, dp(10));
		big.addView(km);
		hero.addView(big);
		hero.addView(text(ctx, r.isLong() ? "Uzun yolculuk · geçmişte kalıcı tutulur" : "Kısa yolculuk", 14, false, t.muted));
		col.addView(hero, hl);

		// Bilgi kutuları (2 × 2)
		long wait = Math.max(0, r.durationMs() - r.movingMs());
		String[][] stats = {
				{"Süre", YoldaDriveCore.duration(r.durationMs())},
				{"Ortalama", r.avgKmh() + " km/s"},
				{"En yüksek", r.maxKmh() + " km/s"},
				{(r.radars() > 0) ? "Radar" : "Duraklama", (r.radars() > 0) ? (r.radars() + " radar geçildi")
						: ((wait >= 60_000) ? YoldaDriveCore.duration(wait) : "Yok")}};
		for (int row = 0; row < 2; row++) {
			LinearLayout rw = new LinearLayout(ctx);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(88));
			rl.topMargin = dp(8);
			for (int c = 0; c < 2; c++) {
				String[] s = stats[row * 2 + c];
				LinearLayout b = card(ctx);
				b.setGravity(Gravity.CENTER_VERTICAL);
				b.addView(text(ctx, s[0], 13, false, t.muted));
				TextView v = text(ctx, s[1], 22, true, t.ink);
				v.setSingleLine(true);
				v.setEllipsize(TextUtils.TruncateAt.END);
				b.addView(v);
				b.setContentDescription(s[0] + ": " + s[1]);
				LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -1, 1f);
				if (c == 1) bl.leftMargin = dp(8);
				rw.addView(b, bl);
			}
			col.addView(rw, rl);
		}

		// Haritada göster
		if (!Double.isNaN(r.startLat()) && !Double.isNaN(r.endLat())) {
			TextView map = pill(ctx, "Rotayı haritada göster");
			map.setOnClickListener(v -> {
				try {
					Uri u = Uri.parse(String.format(Locale.ROOT,
							"https://www.google.com/maps/dir/?api=1&origin=%.6f,%.6f&destination=%.6f,%.6f",
							r.startLat(), r.startLon(), r.endLat(), r.endLon()));
					startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} catch (Throwable ex) {
					YoldaStyle.toast(ctx, "Harita açılamadı");
				}
			});
			LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, dp(52));
			ml.topMargin = dp(16);
			col.addView(map, ml);
		}

		// Bu yolculukta verilen uyarılar
		List<YoldaAlertLog.Entry> al = YoldaAlertLog.between(ctx, r.startWall(), r.endWall() + 60_000);
		TextView ah = text(ctx, al.isEmpty() ? "Bu yolculukta uyarı yok" : ("Uyarılar · " + YoldaAlertLog.summary(al)), 18, true, t.ink);
		ah.setPadding(0, dp(24), 0, dp(8));
		col.addView(ah);
		for (int i = 0; i < Math.min(20, al.size()); i++) col.addView(RadarFragment.alertRow(ctx, t, al.get(i)));

		addClear(ctx, col, r);

		// Önceki yolculuklar
		if (trips.size() > 1) {
			TextView sec = text(ctx, "Bütün yolculuklar (" + trips.size() + ")", 18, true, t.ink);
			sec.setPadding(0, dp(24), 0, dp(8));
			col.addView(sec);
			SimpleDateFormat df = new SimpleDateFormat("d MMM HH:mm", tr);
			for (int i = 0; i < Math.min(60, trips.size()); i++) {
				if (i == idx) continue;
				YoldaRadar.TripItem p = trips.get(i);
				LinearLayout row = card(ctx);
				row.setPadding(dp(16), dp(12), dp(16), dp(12));
				String title = p.route().isEmpty() ? df.format(new Date(p.rec().startWall())) : p.route();
				TextView ti = text(ctx, title, 15, true, t.ink);
				ti.setSingleLine(true);
				ti.setEllipsize(TextUtils.TruncateAt.END);
				row.addView(ti);
				row.addView(text(ctx, df.format(new Date(p.rec().startWall())) + " · " + p.rec().summary(), 13, false, t.muted));
				row.setClickable(true);
				int pi = i;
				row.setOnClickListener(v -> {
					shown = pi;
					render();
				});
				LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
				pl.topMargin = dp(8);
				col.addView(row, pl);
			}
		}
	}

	/** Bu yolculuğu sil ve tümünü temizle (iki kez dokun). */
	private void addClear(Context ctx, LinearLayout col, YoldaDriveCore.TripRec r) {
		LinearLayout acts = new LinearLayout(ctx);
		LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(52));
		al.topMargin = dp(24);
		TextView one = pill(ctx, "Bu yolculuğu sil");
		one.setOnClickListener(v -> {
			YoldaRadar.deleteTrip(ctx, r.startWall());
			YoldaStyle.toast(ctx, "Yolculuk silindi");
			shown = 0;
			render();
		});
		acts.addView(one, new LinearLayout.LayoutParams(0, -1, 1f));
		boolean sure = System.currentTimeMillis() - clearAskedAt < 4000;
		TextView all = pill(ctx, sure ? "Emin misin? Tekrar dokun" : "Tümünü temizle");
		all.setOnClickListener(v -> {
			if (System.currentTimeMillis() - clearAskedAt < 4000) {
				clearAskedAt = 0;
				YoldaRadar.clearTrips(ctx);
				YoldaStyle.toast(ctx, "Yolculuklar temizlendi");
			} else {
				clearAskedAt = System.currentTimeMillis();
			}
			render();
		});
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
		cl.leftMargin = dp(8);
		acts.addView(all, cl);
		col.addView(acts, al);
	}

	private LinearLayout card(Context ctx) {
		LinearLayout l = new LinearLayout(ctx);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBackground(YoldaStyle.glass(ctx, t, 20));
		l.setPadding(dp(16), dp(16), dp(16), dp(16));
		return l;
	}

	private TextView pill(Context ctx, String s) {
		TextView b = text(ctx, s, 16, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setClickable(true);
		b.setContentDescription(s);
		return b;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size);
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
