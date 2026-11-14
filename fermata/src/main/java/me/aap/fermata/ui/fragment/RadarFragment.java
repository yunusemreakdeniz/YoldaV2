package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaRadarCore.Ahead;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Radar ve hız: açık / kapalı, sesli uyarı, hız aşımı uyarısı; anlık hız ve yolun sınırı; öndeki ve yakındaki
 * radarlar (uzaklık, tür, sınır); bulunduğun yere radar ekleme; listeyi güncelleme.
 */
public class RadarFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private final Runnable listener = this::onRadar;
	private long renderedAt;

	@Override
	public int getFragmentId() {
		return R.id.yolda_radar_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Sürüş";
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
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		root = new FrameLayout(requireContext());
		render();
		YoldaRadar.addListener(listener);
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
	public void onDestroyView() {
		YoldaRadar.removeListener(listener);
		super.onDestroyView();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden) render();
	}

	/**
	 * Konum her saniye gelir: ekran yeniden kurulmaz (kurulunca içindeyken sürekli kıpırdıyordu); yalnız üstteki
	 * hız / sınır / önde radar satırları yerinde güncellenir. Listeler ekran açılınca ve bir ayar değişince yenilenir.
	 */
	private void onRadar() {
		if (isHidden() || (root == null) || (liveSpeed == null)) return;
		Context ctx = getContext();
		if (ctx == null) return;
		setIf(liveSign, (YoldaRadar.limit > 0) ? String.valueOf(YoldaRadar.limit) : "–");
		setIf(liveSpeed, speedLine(ctx));
		setIf(liveLimit, limLine());
	}

	@Nullable
	private TextView liveSign, liveSpeed, liveLimit;

	private static void setIf(@Nullable TextView t, String s) {
		if ((t != null) && !android.text.TextUtils.equals(t.getText(), s)) t.setText(s);
	}

	private String speedLine(Context ctx) {
		if (!YoldaRadar.isEnabled(ctx)) return "Radar uyarıları kapalı";
		if (!YoldaRadar.hasPermission(ctx)) return "Konum izni gerekli";
		if (!YoldaRadar.hasFix()) return "Konum bekleniyor (GPS)…";
		return (YoldaRadar.speed >= 0) ? (YoldaRadar.speed + " km/s") : "Hız bilinmiyor";
	}

	private String limLine() {
		int lim = YoldaRadar.limit;
		String l = (lim > 0) ? ("Hız sınırı " + lim + (YoldaRadar.limitEstimated ? " (yol türüne göre tahmini)" : ""))
				: "Bu yolun hız sınırı bilinmiyor";
		Ahead ah = YoldaRadar.ahead;
		if (ah != null) l += "\nÖnde " + ah.cam().label().toLowerCase(new Locale("tr")) + ": " + meters(ah.meters());
		return l;
	}

	private void render() {
		if ((root == null) || !isAdded()) return;
		renderedAt = System.currentTimeMillis();
		Context ctx = requireContext();
		MainActivityDelegate a = getActivityDelegate();
		t = YoldaStyle.current(ctx);
		int scrollY = 0;
		if ((root.getChildCount() > 0) && (root.getChildAt(0) instanceof ScrollView sv)) scrollY = sv.getScrollY();
		root.removeAllViews();
		YoldaStyle.applyScreenBackground(root, t);
		ScrollView scroll = new ScrollView(ctx);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(YoldaStyle.side(ctx)), dp(YoldaStyle.top(ctx)), dp(YoldaStyle.side(ctx)), dp(190));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
		int sy = scrollY;
		scroll.post(() -> scroll.scrollTo(0, sy));

		TextView title = text(ctx, "Sürüş", YoldaStyle.titleSp(ctx), true, t.ink);
		title.setSingleLine(true);
		col.addView(title);

		boolean on = YoldaRadar.isEnabled(ctx);
		boolean perm = YoldaRadar.hasPermission(ctx);

		// Anlık durum
		LinearLayout now = new LinearLayout(ctx);
		now.setOrientation(LinearLayout.HORIZONTAL);
		now.setGravity(Gravity.CENTER_VERTICAL);
		now.setBackground(YoldaStyle.glass(ctx, t, 22));
		now.setPadding(dp(16), dp(14), dp(16), dp(14));
		LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
		nlp.topMargin = dp(14);
		col.addView(now, nlp);
		int lim = YoldaRadar.limit;
		TextView sign = text(ctx, (lim > 0) ? String.valueOf(lim) : "–", 22, true, 0xFF111111);
		sign.setGravity(Gravity.CENTER);
		GradientDrawable sd = new GradientDrawable();
		sd.setShape(GradientDrawable.OVAL);
		sd.setColor(0xFFFFFFFF);
		sd.setStroke(dp(6), 0xFFDC2626);
		sign.setBackground(sd);
		now.addView(sign, new LinearLayout.LayoutParams(dp(64), dp(64)));
		LinearLayout info = new LinearLayout(ctx);
		info.setOrientation(LinearLayout.VERTICAL);
		info.setPadding(dp(16), 0, 0, 0);
		now.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
		liveSign = sign;
		liveSpeed = text(ctx, speedLine(ctx), 22, true, t.ink);
		info.addView(liveSpeed);
		liveLimit = text(ctx, limLine(), 14, false, t.muted);
		liveLimit.setMinLines(2); // önde radar satırı gelip gidince ekran kaymasın
		info.addView(liveLimit);

		// Ayarlar
		col.addView(toggle(ctx, "Radar ve sürüş yardımcıları", on ? "Açık · aşağıdakiler tek tek kapatılabilir"
				: "Hepsi kapalı · konum izlenmiyor", on, v -> {
			if (!on && !YoldaRadar.hasPermission(ctx)) askPermission(a);
			YoldaRadar.setEnabled(ctx, !on);
			YoldaStyle.toast(ctx, on ? "Radar uyarıları kapatıldı" : "Radar uyarıları açıldı");
			render();
		}));
		if (on && !perm) col.addView(button(ctx, "Konum iznini ver", v -> askPermission(a)));
		col.addView(feature(ctx, "cams", "Radar uyarısı", "Radara 1000 / 500 / 150 m kala bip ve uyarı"));
		boolean voice = YoldaRadar.voice(ctx);
		col.addView(toggle(ctx, "Sesli uyarı", voice ? "“500 metre sonra radar, hız sınırı 70”" : "Yalnız bip ve yazı", voice,
				v -> {
					YoldaRadar.setVoice(ctx, !voice);
					render();
				}));
		boolean over = YoldaRadar.overspeedWarn(ctx);
		col.addView(toggle(ctx, "Hız aşımı uyarısı", over ? "Sınırın %10'u aşılınca çift bip (konuşmaz), levha yanıp söner" : "Kapalı", over, v -> {
			YoldaRadar.setOverspeedWarn(ctx, !over);
			render();
		}));
		col.addView(feature(ctx, "avg", "Ortalama hız koridoru", YoldaRadar.avg.isActive()
				? ("Koridordasın · ortalaman " + Math.max(0, YoldaRadar.avg.average()) + " km/s · " + meters(YoldaRadar.avg.meters()))
				: "Denetim noktasından geçince ortalamanı gösterir, aşarsan uyarır"));
		col.addView(feature(ctx, "zones", "Okul ve hastane bölgesi", "Yaklaşınca uyarır (okullar hafta içi 07:00-18:30)"));
		col.addView(feature(ctx, "stops", "Yol üstü mola yerleri", "Önündeki akaryakıt ve dinlenme tesisleri"));
		col.addView(feature(ctx, "fatigue", "Mola hatırlatıcısı", "2 saat ara vermeden sürünce hatırlatır · şu an "
				+ YoldaDriveCore.duration(YoldaRadar.fatigue.drivingMs())));
		col.addView(feature(ctx, "bg", "Arka planda da çalış", "Android Auto'da haritaya geçince, telefon kilitliyken de sesli uyarır"
				+ (YoldaDriveService.running ? " · çalışıyor" : "")));
		col.addView(feature(ctx, "park", "Park yerimi hatırla", "Araç bağlantısı kesilince aracın yeri kaydedilir"));
		col.addView(feature(ctx, "trips", "Yolculuk özeti", "Her yolculuğun km, süre, ortalama ve en yüksek hızı"));

		// Park yerleri: en yenisi "aracım"; her biri "Git" ve "Sil" (araç ayrılınca kendiliğinden eklenir)
		if (YoldaRadar.feature(ctx, "park")) {
			List<double[]> pks = YoldaRadar.parks(ctx);
			TextView ph = text(ctx, pks.isEmpty() ? "Park yerleri" : "Park yerleri · en üstteki aracın", 18, true, t.ink);
			ph.setPadding(dp(4), dp(20), dp(4), dp(4));
			col.addView(ph);
			TextView pt = text(ctx, "Araç bağlantısı (Android Auto) kesilince kendiliğinden kaydedilir; 🅿 ile elle de eklenir",
					13, false, t.muted);
			pt.setPadding(dp(4), 0, dp(4), dp(6));
			col.addView(pt);
			java.text.SimpleDateFormat pdf = new java.text.SimpleDateFormat("d MMM EEE HH:mm", new Locale("tr"));
			for (int i = 0; i < Math.min(10, pks.size()); i++) {
				double[] pk = pks.get(i);
				LinearLayout row = new LinearLayout(ctx);
				row.setOrientation(LinearLayout.HORIZONTAL);
				row.setGravity(Gravity.CENTER_VERTICAL);
				row.setBackground(YoldaStyle.glass(ctx, t, 16));
				row.setPadding(dp(14), dp(8), dp(8), dp(8));
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
				rlp.topMargin = dp(8);
				LinearLayout tx = new LinearLayout(ctx);
				tx.setOrientation(LinearLayout.VERTICAL);
				String when = pdf.format(new java.util.Date((long) pk[2])) + ((pk[3] != 0) ? " · otomatik" : " · elle");
				tx.addView(text(ctx, (i == 0) ? ("🅿 Aracın · " + when) : when, 14, i == 0, (i == 0) ? t.ink : t.muted));
				String far = Double.isNaN(YoldaRadar.lat) ? "Uzaklık bilinmiyor"
						: (YoldaDriveCore.distText(YoldaRadarCore.dist(YoldaRadar.lat, YoldaRadar.lon, pk[0], pk[1])) + " uzakta");
				tx.addView(text(ctx, far, 13, false, t.muted));
				row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
				row.addView(button(ctx, "Git", v -> YoldaRadar.walkTo(ctx, pk[0], pk[1])), new LinearLayout.LayoutParams(-2, dp(42)));
				LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, dp(42));
				dl.leftMargin = dp(8);
				row.addView(button(ctx, "Sil", v -> {
					YoldaRadar.deletePark(ctx, (long) pk[2]);
					YoldaStyle.toast(ctx, "Park yeri silindi");
					render();
				}), dl);
				row.setContentDescription("Park yeri " + when + ", " + far);
				col.addView(row, rlp);
			}
			LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, dp(46));
			hp.topMargin = dp(10);
			col.addView(button(ctx, "🅿 Buraya park ettim", v -> {
				YoldaStyle.toast(ctx, YoldaRadar.savePark(ctx, false) ? "Park yeri kaydedildi" : "Konum henüz bilinmiyor");
				render();
			}), hp);
		}


		LinearLayout acts = new LinearLayout(ctx);
		acts.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
		alp.topMargin = dp(12);
		col.addView(acts, alp);
		acts.addView(button(ctx, "＋ Buraya radar ekle", v -> {
			if (YoldaRadar.addHere(ctx)) YoldaStyle.toast(ctx, "Radar eklendi; buradan geçerken uyaracak");
			else YoldaStyle.toast(ctx, "Konum henüz bilinmiyor");
			render();
		}), new LinearLayout.LayoutParams(0, dp(52), 1f));
		LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(52), 1f);
		ulp.leftMargin = dp(10);
		acts.addView(button(ctx, "↻ Listeyi güncelle", v -> {
			YoldaStyle.toast(ctx, "Radar listesi güncelleniyor…");
			YoldaRadar.update(ctx, r -> {
				YoldaStyle.toast(ctx, r);
				render();
			});
		}), ulp);

		// Önündeki mola yerleri
		if (on && YoldaRadar.feature(ctx, "stops")) {
			List<YoldaDriveCore.Stop> st = YoldaRadar.stops;
			TextView sh = text(ctx, st.isEmpty() ? "Önünde mola yeri bulunamadı (30 km)" : "Önündeki mola yerleri", 18, true, t.ink);
			sh.setPadding(dp(4), dp(20), dp(4), dp(8));
			col.addView(sh);
			for (YoldaDriveCore.Stop s : st) col.addView(stopRow(ctx, a, s));
		}

		// Yolculuklar: kısalar (son 5) ve 20 km üstü geçmiş (silinene kadar)
		if (YoldaRadar.feature(ctx, "trips")) {
			List<YoldaRadar.TripItem> items = YoldaRadar.tripItems(ctx);
			List<YoldaRadar.TripItem> shorts = new java.util.ArrayList<>(), longs = new java.util.ArrayList<>();
			for (YoldaRadar.TripItem it : items) (it.rec().isLong() ? longs : shorts).add(it);
			String nowTrip = YoldaRadar.trip.isActive() ? (" · şu an yoldasın, " + YoldaDriveCore.distText(YoldaRadar.trip.meters())) : "";
			TextView th = text(ctx, (shorts.isEmpty() ? "Kısa yolculuk yok" : "Son yolculuklar") + nowTrip, 18, true, t.ink);
			th.setPadding(dp(4), dp(20), dp(4), dp(8));
			col.addView(th);
			for (int i = 0; i < Math.min(5, shorts.size()); i++) col.addView(tripRow(ctx, shorts.get(i)));

			TextView lh = text(ctx, longs.isEmpty() ? "Yolculuk geçmişi: 20 km üstü yolculukların burada saklanır"
					: ("Yolculuk geçmişi · 20 km üstü · " + longs.size() + " yolculuk, " + totalKm(longs) + " km"), 18, true, t.ink);
			lh.setPadding(dp(4), dp(20), dp(4), dp(8));
			col.addView(lh);
			int show = allTrips ? longs.size() : Math.min(10, longs.size());
			for (int i = 0; i < show; i++) col.addView(tripRow(ctx, longs.get(i)));
			LinearLayout tr = new LinearLayout(ctx);
			tr.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, -2);
			trp.topMargin = dp(10);
			col.addView(tr, trp);
			if (longs.size() > 10) {
				tr.addView(button(ctx, allTrips ? "Daha az göster" : ("Tümünü göster (" + longs.size() + ")"), v -> {
					allTrips = !allTrips;
					render();
				}), new LinearLayout.LayoutParams(-2, dp(46)));
			}
			if (!items.isEmpty()) {
				LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, dp(46));
				if (tr.getChildCount() > 0) dl.leftMargin = dp(10);
				boolean sure = System.currentTimeMillis() - clearAskedAt < 4000;
				tr.addView(button(ctx, sure ? "Emin misin? Tekrar dokun" : "Tüm yolculukları sil", v -> {
					if (System.currentTimeMillis() - clearAskedAt < 4000) {
						clearAskedAt = 0;
						YoldaRadar.clearTrips(ctx);
						YoldaStyle.toast(ctx, "Yolculuklar silindi");
					} else {
						clearAskedAt = System.currentTimeMillis();
					}
					render();
				}), dl);
			}
		}

		// Yakındaki radarlar
		List<Ahead> near = Double.isNaN(YoldaRadar.lat) ? java.util.Collections.emptyList()
				: YoldaRadarCore.nearby(YoldaRadar.lat, YoldaRadar.lon, YoldaRadar.cams(), 30_000, 20);
		TextView h = text(ctx, near.isEmpty() ? "Yakında radar yok (30 km)" : "Yakındaki radarlar", 18, true, t.ink);
		h.setPadding(dp(4), dp(20), dp(4), dp(8));
		col.addView(h);
		for (Ahead n : near) col.addView(camRow(ctx, n));

		long up = YoldaRadar.updatedAt(ctx);
		String src = "Türkiye'de " + YoldaRadar.cams().size() + " radar · kaynak: OpenStreetMap katkıcıları"
				+ ((up > 0) ? (" · güncelleme " + new java.text.SimpleDateFormat("d MMM", new Locale("tr")).format(new java.util.Date(up)))
				: " · uygulamayla gelen liste")
				+ ". Radar yerleri eksik ya da eski olabilir; her zaman trafik işaretlerine uy.";
		TextView foot = text(ctx, src, 12, false, t.muted);
		foot.setPadding(dp(4), dp(18), dp(4), 0);
		col.addView(foot);
	}

	private void askPermission(MainActivityDelegate a) {
		a.getAppActivity().checkPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,
				android.Manifest.permission.ACCESS_FINE_LOCATION).onCompletion((r, err) -> {
			YoldaRadar.permissionChanged(a.getContext());
			if (isAdded()) render();
		});
	}

	private View camRow(Context ctx, Ahead n) {
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(YoldaStyle.glass(ctx, t, 16));
		row.setPadding(dp(14), dp(10), dp(10), dp(10));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = dp(8);
		row.setLayoutParams(lp);
		TextView d = text(ctx, meters(n.meters()), 16, true, t.ink);
		d.setMinWidth(dp(72));
		row.addView(d);
		String what = n.cam().label() + ((n.cam().limit() > 0) ? (" · sınır " + n.cam().limit()) : "");
		TextView w = text(ctx, what, 15, false, t.ink);
		w.setSingleLine(true);
		w.setEllipsize(TextUtils.TruncateAt.END);
		row.addView(w, new LinearLayout.LayoutParams(0, -2, 1f));
		if (n.cam().kind() == 'u') {
			row.addView(button(ctx, "Sil", v -> {
				YoldaRadar.removeUser(ctx, n.cam().id());
				render();
			}), new LinearLayout.LayoutParams(-2, dp(42)));
		}
		row.setContentDescription(what + ", " + meters(n.meters()) + ". Haritada göstermek için dokun");
		row.setOnClickListener(v -> {
			try {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.ROOT, "geo:%.5f,%.5f?q=%.5f,%.5f(Radar)",
						n.cam().lat(), n.cam().lon(), n.cam().lat(), n.cam().lon()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			} catch (Exception ex) {
				YoldaStyle.toast(ctx, "Harita uygulaması bulunamadı");
			}
		});
		return row;
	}

	private boolean allTrips;
	private long clearAskedAt;

	private static int totalKm(List<YoldaRadar.TripItem> l) {
		long m = 0;
		for (YoldaRadar.TripItem t : l) m += t.rec().meters();
		return (int) Math.round(m / 1000d);
	}

	/** Yolculuk satırı: tarih · nereden → nereye, özet; "Sil". */
	private View tripRow(Context ctx, YoldaRadar.TripItem it) {
		YoldaDriveCore.TripRec tr = it.rec();
		java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("d MMM yyyy EEE HH:mm", new Locale("tr"));
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(YoldaStyle.glass(ctx, t, 16));
		row.setPadding(dp(14), dp(10), dp(10), dp(10));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = dp(8);
		row.setLayoutParams(lp);
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		String head = df.format(new java.util.Date(tr.startWall())) + (it.route().isEmpty() ? "" : (" · " + it.route()));
		TextView h = text(ctx, head, 13, false, t.muted);
		h.setSingleLine(true);
		h.setEllipsize(TextUtils.TruncateAt.END);
		tx.addView(h);
		tx.addView(text(ctx, tr.summary(), 15, true, t.ink));
		row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
		row.addView(button(ctx, "Sil", v -> {
			YoldaRadar.deleteTrip(ctx, tr.startWall());
			YoldaStyle.toast(ctx, "Yolculuk silindi");
			render();
		}), new LinearLayout.LayoutParams(-2, dp(42)));
		row.setContentDescription("Yolculuk " + head + ": " + tr.summary());
		return row;
	}

	private View feature(Context ctx, String key, String label, String sub) {
		boolean f = YoldaRadar.feature(ctx, key);
		return toggle(ctx, label, f ? sub : "Kapalı", f, v -> {
			YoldaRadar.setFeature(ctx, key, !f);
			YoldaStyle.toast(ctx, label + (f ? " kapatıldı" : " açıldı"));
			render();
		});
	}

	private View stopRow(Context ctx, MainActivityDelegate a, YoldaDriveCore.Stop s) {
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(YoldaStyle.glass(ctx, t, 16));
		row.setPadding(dp(14), dp(10), dp(10), dp(10));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = dp(8);
		row.setLayoutParams(lp);
		TextView d = text(ctx, meters(s.meters()), 16, true, t.ink);
		d.setMinWidth(dp(72));
		row.addView(d);
		String name = s.place().name().isEmpty() ? s.place().label() : (s.place().name() + " · " + s.place().label());
		TextView w = text(ctx, name, 15, false, t.ink);
		w.setSingleLine(true);
		w.setEllipsize(TextUtils.TruncateAt.END);
		row.addView(w, new LinearLayout.LayoutParams(0, -2, 1f));
		row.addView(button(ctx, "Git", v -> NavFragment.voiceGo(a, null, String.format(Locale.ROOT, "%.6f,%.6f",
				s.place().lat(), s.place().lon()))), new LinearLayout.LayoutParams(-2, dp(42)));
		row.setContentDescription(name + ", " + meters(s.meters()) + " ileride");
		return row;
	}

	static String meters(int m) {
		return (m >= 1000) ? String.format(new Locale("tr"), "%.1f km", m / 1000f) : (m + " m");
	}

	private View toggle(Context ctx, String label, String sub, boolean on, View.OnClickListener click) {
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(YoldaStyle.glass(ctx, t, 18));
		row.setPadding(dp(16), dp(12), dp(14), dp(12));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = dp(10);
		row.setLayoutParams(lp);
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.addView(text(ctx, label, 17, true, t.ink));
		TextView s = text(ctx, sub, 13, false, t.muted);
		s.setMaxLines(2);
		tx.addView(s);
		row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
		row.addView(YoldaStyle.toggleSwitch(ctx, on, t.accent), new LinearLayout.LayoutParams(dp(56), dp(32)));
		row.setContentDescription(label + ": " + (on ? "açık" : "kapalı"));
		row.setClickable(true);
		row.setOnClickListener(v -> {
			YoldaStyle.haptic(v);
			click.onClick(v);
		});
		return row;
	}

	private TextView button(Context ctx, String label, View.OnClickListener click) {
		TextView b = text(ctx, label, 15, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setSingleLine(true);
		b.setEllipsize(TextUtils.TruncateAt.END);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setPadding(dp(14), 0, dp(14), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription(label);
		return b;
	}

	private TextView text(Context ctx, String s, float size, boolean bold, int color) {
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
