package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
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
 * Park sayfası (Park kartına dokununca): park yerleri (en yenisi "aracın"), her birinde Git ve Sil; "Buraya park
 * ettim"; tümünü temizle (iki kez dokun); "Park yerimi hatırla" açık / kapalı. Önceden Sürüş ekranındaydı.
 */
public class ParkFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private long clearAskedAt;

	@Override
	public int getFragmentId() {
		return R.id.yolda_park_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Park";
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

		col.addView(text(ctx, "Park", YoldaStyle.titleSp(ctx), true, t.ink));

		// Park yerimi hatırla
		boolean on = YoldaRadar.feature(ctx, "park");
		LinearLayout sw = new LinearLayout(ctx);
		sw.setGravity(Gravity.CENTER_VERTICAL);
		sw.setPadding(dp(16), dp(12), dp(12), dp(12));
		sw.setBackground(YoldaStyle.glass(ctx, t, 20));
		LinearLayout swt = new LinearLayout(ctx);
		swt.setOrientation(LinearLayout.VERTICAL);
		swt.addView(text(ctx, "Park yerimi hatırla", 17, true, t.ink));
		swt.addView(text(ctx, "Araç bağlantısı (Android Auto) kesilince aracın yeri kendiliğinden kaydedilir", 13, false, t.muted));
		sw.addView(swt, new LinearLayout.LayoutParams(0, -2, 1f));
		sw.addView(YoldaStyle.toggleSwitch(ctx, on, t.accent), new LinearLayout.LayoutParams(-2, -2));
		sw.setClickable(true);
		sw.setContentDescription("Park yerimi hatırla: " + (on ? "açık" : "kapalı"));
		sw.setOnClickListener(v -> {
			YoldaRadar.setFeature(ctx, "park", !on);
			render();
		});
		LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(-1, -2);
		swl.topMargin = dp(16);
		col.addView(sw, swl);

		// Park yerleri
		List<double[]> pks = YoldaRadar.parks(ctx);
		TextView ph = text(ctx, pks.isEmpty() ? "Park yerleri" : "Park yerleri · en üstteki aracın", 18, true, t.ink);
		ph.setPadding(0, dp(24), 0, dp(4));
		col.addView(ph);
		if (pks.isEmpty()) col.addView(text(ctx, "Kayıtlı park yeri yok. Aşağıdaki düğmeyle ya da araçtan inince kendiliğinden eklenir.",
				14, false, t.muted));
		SimpleDateFormat pdf = new SimpleDateFormat("d MMM EEE HH:mm", new Locale("tr"));
		for (int i = 0; i < Math.min(20, pks.size()); i++) {
			double[] pk = pks.get(i);
			LinearLayout row = new LinearLayout(ctx);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setBackground(YoldaStyle.glass(ctx, t, 16));
			row.setPadding(dp(16), dp(8), dp(8), dp(8));
			LinearLayout tx = new LinearLayout(ctx);
			tx.setOrientation(LinearLayout.VERTICAL);
			String when = pdf.format(new Date((long) pk[2])) + ((pk[3] != 0) ? " · otomatik" : " · elle");
			tx.addView(text(ctx, (i == 0) ? ("🅿 Aracın · " + when) : when, 15, i == 0, (i == 0) ? t.ink : t.muted));
			String far = Double.isNaN(YoldaRadar.lat) ? "Uzaklık bilinmiyor"
					: (YoldaDriveCore.distText(YoldaRadarCore.dist(YoldaRadar.lat, YoldaRadar.lon, pk[0], pk[1])) + " uzakta");
			tx.addView(text(ctx, far, 13, false, t.muted));
			row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
			row.addView(button(ctx, "Git", v -> YoldaRadar.walkTo(ctx, pk[0], pk[1])), new LinearLayout.LayoutParams(-2, dp(48)));
			LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, dp(48));
			dl.leftMargin = dp(8);
			row.addView(button(ctx, "Sil", v -> {
				YoldaRadar.deletePark(ctx, (long) pk[2]);
				YoldaStyle.toast(ctx, "Park yeri silindi");
				render();
			}), dl);
			row.setContentDescription("Park yeri " + when + ", " + far);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
			rl.topMargin = dp(8);
			col.addView(row, rl);
		}

		LinearLayout acts = new LinearLayout(ctx);
		LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(52));
		al.topMargin = dp(16);
		acts.addView(button(ctx, "🅿 Buraya park ettim", v -> {
			YoldaStyle.toast(ctx, YoldaRadar.savePark(ctx, false) ? "Park yeri kaydedildi" : "Konum henüz bilinmiyor");
			render();
		}), new LinearLayout.LayoutParams(0, -1, 1f));
		if (!pks.isEmpty()) {
			boolean sure = System.currentTimeMillis() - clearAskedAt < 4000;
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
			cl.leftMargin = dp(8);
			acts.addView(button(ctx, sure ? "Emin misin? Tekrar dokun" : "Tümünü temizle", v -> {
				if (System.currentTimeMillis() - clearAskedAt < 4000) {
					clearAskedAt = 0;
					YoldaRadar.clearPark(ctx);
					YoldaStyle.toast(ctx, "Park yerleri temizlendi");
				} else {
					clearAskedAt = System.currentTimeMillis();
				}
				render();
			}), cl);
		}
		col.addView(acts, al);
	}

	private TextView button(Context ctx, String label, View.OnClickListener click) {
		TextView b = text(ctx, label, 15, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setSingleLine(true);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setPadding(dp(16), 0, dp(16), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription(label);
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
