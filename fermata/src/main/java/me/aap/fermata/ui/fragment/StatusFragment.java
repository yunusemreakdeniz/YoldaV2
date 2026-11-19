package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Telefon durumu sayfası (kartına dokununca): pil ve şarj, bağlantı (Wi-Fi / mobil) ve sinyal, bu ayki mobil
 * veri ve sınırı (Kapalı, 1, 2, 5, 10, 20 GB; aşıldıysa "Devam et"), hızlı ayarlar (Wi-Fi, İnternet, Bluetooth,
 * Ses, Rahatsız etmeyin, Pil tasarrufu). Android sistem ayarlarını yalnız kendi panelleriyle açar.
 */
public class StatusFragment extends MainActivityFragment {
	static final int[] LIMITS_MB = {0, 1024, 2048, 5120, 10240, 20480};
	private FrameLayout root;
	private YoldaStyle.Theme t;

	@Override
	public int getFragmentId() {
		return R.id.yolda_status_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Telefon durumu";
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
		if (!hidden) render();
	}

	/** Pil yüzdesi (bilinmiyorsa -1) ve şarj oluyor mu. */
	static int[] battery(Context ctx) {
		try {
			Intent b = ctx.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			if (b == null) return new int[]{-1, 0};
			int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			boolean ch = (st == BatteryManager.BATTERY_STATUS_CHARGING) || (st == BatteryManager.BATTERY_STATUS_FULL);
			return new int[]{(lv < 0) ? -1 : (lv * 100 / Math.max(1, sc)), ch ? 1 : 0};
		} catch (Throwable ex) {
			return new int[]{-1, 0};
		}
	}

	/** "Wi-Fi", "Mobil veri", "Bağlantı yok". */
	static String network(Context ctx) {
		try {
			android.net.ConnectivityManager cm = ctx.getSystemService(android.net.ConnectivityManager.class);
			android.net.Network n = cm.getActiveNetwork();
			android.net.NetworkCapabilities c = (n != null) ? cm.getNetworkCapabilities(n) : null;
			if (c == null) return "Bağlantı yok";
			if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
			if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobil veri";
			return "Bağlı";
		} catch (Throwable ex) {
			return "Bilinmiyor";
		}
	}

	/** Sinyal 0-4 (bilinmiyorsa -1). */
	static int signal(Context ctx) {
		try {
			TelephonyManager tm = ctx.getSystemService(TelephonyManager.class);
			SignalStrength s = (android.os.Build.VERSION.SDK_INT >= 28) ? tm.getSignalStrength() : null;
			return (s != null) ? s.getLevel() : -1;
		} catch (Throwable ex) {
			return -1;
		}
	}

	/** Kartın ve sesli yanıtın kısa satırı. */
	static String line(Context ctx) {
		int[] b = battery(ctx);
		long used = YoldaData.usedThisMonth(ctx);
		int lim = YoldaData.limitMb(ctx);
		return ((b[0] >= 0) ? ("Pil %" + b[0] + ((b[1] == 1) ? " şarjda" : "")) : "Pil ?") + " · " + network(ctx) + " · Bu ay "
				+ YoldaData.size(used) + ((lim > 0) ? (" / " + YoldaData.size(lim * 1024L * 1024L)) : "");
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
		col.addView(text(ctx, "Telefon durumu", YoldaStyle.titleSp(ctx), true, t.ink));

		// Üç kutu: pil, bağlantı, sinyal
		int[] b = battery(ctx);
		int sg = signal(ctx);
		String[][] boxes = {{"Pil", (b[0] >= 0) ? ("%" + b[0]) : "?", (b[1] == 1) ? "Şarj oluyor" : "Şarjda değil"},
				{"Bağlantı", network(ctx), YoldaData.onMobile(ctx) ? "Veri sayılıyor" : "Veri sayılmıyor"},
				{"Sinyal", (sg >= 0) ? ("▂▄▆█".substring(0, Math.max(1, sg)) + " " + sg + "/4") : "?", "Mobil şebeke"}};
		LinearLayout rw = new LinearLayout(ctx);
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(104));
		rl.topMargin = dp(16);
		for (int i = 0; i < boxes.length; i++) {
			LinearLayout bx = card(ctx);
			bx.setGravity(Gravity.CENTER_VERTICAL);
			bx.addView(text(ctx, boxes[i][0], 13, false, t.muted));
			bx.addView(text(ctx, boxes[i][1], 22, true, t.ink));
			bx.addView(text(ctx, boxes[i][2], 12, false, t.muted));
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -1, 1f);
			if (i > 0) bl.leftMargin = dp(8);
			rw.addView(bx, bl);
		}
		col.addView(rw, rl);

		// Mobil veri
		long used = YoldaData.usedThisMonth(ctx);
		int lim = YoldaData.limitMb(ctx);
		int pc = YoldaData.percent(used, lim);
		LinearLayout dc = card(ctx);
		LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
		dl.topMargin = dp(16);
		dc.addView(text(ctx, "Bu ay mobil veri (Yolda)", 14, false, t.muted));
		dc.addView(text(ctx, YoldaData.size(used) + ((lim > 0) ? (" / " + YoldaData.size(lim * 1024L * 1024L) + " · %" + pc) : ""), 26, true,
				(pc >= 100) ? 0xFFF87171 : t.ink));
		if (lim > 0) dc.addView(bar(ctx, Math.min(100, pc)));
		col.addView(dc, dl);
		TextView lh = text(ctx, "Aylık sınır ve uyarı (%80'de ve sınırda sesli uyarır, sınırda video 360p'ye iner)", 14, false, t.muted);
		lh.setPadding(0, dp(16), 0, dp(4));
		col.addView(lh);
		LinearLayout chips = new LinearLayout(ctx);
		for (int mb : LIMITS_MB) {
			boolean sel = mb == lim;
			TextView c = pill(ctx, (mb == 0) ? "Kapalı" : YoldaData.size(mb * 1024L * 1024L), sel);
			c.setOnClickListener(v -> {
				YoldaData.setLimitMb(ctx, mb);
				YoldaStyle.toast(ctx, (mb == 0) ? "Veri sınırı kapalı" : ("Veri sınırı " + YoldaData.size(mb * 1024L * 1024L)));
				render();
			});
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, dp(48));
			cl.rightMargin = dp(8);
			chips.addView(c, cl);
		}
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(chips);
		col.addView(hs);
		if ((pc >= 100) && !YoldaData.continued(ctx)) {
			TextView cont = pill(ctx, "Devam et (bu ay yeniden uyarma)", false);
			cont.setOnClickListener(v -> {
				YoldaData.continueAnyway(ctx);
				YoldaStyle.toast(ctx, "Tamam, ay sonuna kadar uyarmayacağım");
				render();
			});
			LinearLayout.LayoutParams ccl = new LinearLayout.LayoutParams(-1, dp(52));
			ccl.topMargin = dp(8);
			col.addView(cont, ccl);
		}

		// Hızlı ayarlar
		TextView qh = text(ctx, "Hızlı ayarlar", 18, true, t.ink);
		qh.setPadding(0, dp(24), 0, dp(4));
		col.addView(qh);
		String[][] qs = {{"Wi-Fi", Settings.Panel.ACTION_WIFI}, {"İnternet", Settings.Panel.ACTION_INTERNET_CONNECTIVITY},
				{"Ses", Settings.Panel.ACTION_VOLUME}, {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
				{"Rahatsız etmeyin", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS}, {"Pil tasarrufu", Settings.ACTION_BATTERY_SAVER_SETTINGS}};
		for (int i = 0; i < qs.length; i += 2) {
			LinearLayout r = new LinearLayout(ctx);
			LinearLayout.LayoutParams qrl = new LinearLayout.LayoutParams(-1, dp(56));
			qrl.topMargin = dp(8);
			for (int k = 0; k < 2; k++) {
				String[] q = qs[i + k];
				TextView c = pill(ctx, q[0], false);
				c.setOnClickListener(v -> {
					try {
						String act = q[1];
						if ((android.os.Build.VERSION.SDK_INT < 29) && act.startsWith("android.settings.panel")) // Android 9: panel yok
							act = act.contains("VOLUME") ? Settings.ACTION_SOUND_SETTINGS : Settings.ACTION_WIRELESS_SETTINGS;
						startActivity(new Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
					} catch (Throwable ex) {
						YoldaStyle.toast(ctx, q[0] + " ayarı açılamadı");
					}
				});
				LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(0, -1, 1f);
				if (k == 1) ql.leftMargin = dp(8);
				r.addView(c, ql);
			}
			col.addView(r, qrl);
		}
	}

	private View bar(Context ctx, int percent) {
		FrameLayout bar = new FrameLayout(ctx);
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(dp(4));
		bg.setColor(0x33FFFFFF);
		bar.setBackground(bg);
		View fill = new View(ctx);
		GradientDrawable fg = new GradientDrawable();
		fg.setCornerRadius(dp(4));
		fg.setColor((percent >= 80) ? 0xFFF87171 : (t.accent | 0xFF000000));
		fill.setBackground(fg);
		bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
		bar.post(() -> {
			ViewGroup.LayoutParams lp = fill.getLayoutParams();
			lp.width = bar.getWidth() * percent / 100;
			fill.setLayoutParams(lp);
		});
		LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, dp(8));
		l.topMargin = dp(8);
		bar.setLayoutParams(l);
		return bar;
	}

	private LinearLayout card(Context ctx) {
		LinearLayout l = new LinearLayout(ctx);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBackground(YoldaStyle.glass(ctx, t, 20));
		l.setPadding(dp(16), dp(12), dp(16), dp(12));
		return l;
	}

	private TextView pill(Context ctx, String s, boolean sel) {
		TextView b = text(ctx, s, 15, true, sel ? 0xFF111827 : t.ink);
		b.setGravity(Gravity.CENTER);
		b.setIncludeFontPadding(false);
		b.setPadding(dp(16), 0, dp(16), 0);
		if (sel) {
			GradientDrawable g = new GradientDrawable();
			g.setCornerRadius(dp(999));
			g.setColor(0xFFFFF4D6);
			b.setBackground(g);
		} else {
			b.setBackground(YoldaStyle.glass(ctx, t, 999));
		}
		b.setClickable(true);
		b.setContentDescription(s);
		return b;
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(size * YoldaStyle.textScale(ctx));
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
