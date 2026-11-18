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

import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Akaryakıt sayfası (Akaryakıt kartına dokununca): benzin, motorin, LPG Türkiye ortalaması ve haftalık değişim;
 * yakıt türü ve tüketim seçimi; 100 km ve son yolculuğun yakıt maliyeti; en yakın benzinliğe yol tarifi.
 */
public class FuelFragment extends MainActivityFragment {
	private static final String[] TYPES = {"gasoline", "diesel", "lpg"};
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private boolean loading;

	@Override
	public int getFragmentId() {
		return R.id.yolda_fuel_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Akaryakıt";
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
		load(false);
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
			render();
			load(false);
		}
	}

	private void load(boolean force) {
		if (loading) return;
		loading = true;
		Context app = requireContext().getApplicationContext();
		me.aap.utils.function.CheckedSupplier<YoldaFuel.Prices, Throwable> job = () -> YoldaFuel.refresh(app, force);
		me.aap.utils.app.App.get().execute(job).onCompletion((p, err) -> me.aap.utils.app.App.get().getHandler().post(() -> {
			loading = false;
			render();
		}));
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

		col.addView(text(ctx, "Akaryakıt", YoldaStyle.titleSp(ctx), true, t.ink));
		YoldaFuel.Prices p = YoldaFuel.cached(ctx);
		String type = YoldaFuel.type(ctx);
		if (p == null) {
			TextView m = text(ctx, loading ? "Fiyatlar alınıyor…" : "Fiyatlar alınamadı. İnternet bağlantısını kontrol et.", 16, false, t.muted);
			m.setPadding(0, dp(16), 0, 0);
			col.addView(m);
		} else {
			// Üç fiyat kutusu: seçili yakıt vurgulu, dokununca o yakıt seçilir
			LinearLayout rw = new LinearLayout(ctx);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(120));
			rl.topMargin = dp(16);
			for (int i = 0; i < TYPES.length; i++) {
				String ty = TYPES[i];
				boolean sel = ty.equals(type);
				LinearLayout b = new LinearLayout(ctx);
				b.setOrientation(LinearLayout.VERTICAL);
				b.setGravity(Gravity.CENTER_VERTICAL);
				b.setPadding(dp(16), dp(12), dp(16), dp(12));
				b.setBackground(sel ? YoldaStyle.selected(ctx, 20) : YoldaStyle.glass(ctx, t, 20));
				b.addView(text(ctx, YoldaFuel.label(ty) + (sel ? " · seçili" : ""), 14, sel, sel ? t.ink : t.muted));
				TextView pr = text(ctx, YoldaFuel.money(p.of(ty)), 26, true, t.ink);
				pr.setSingleLine(true);
				b.addView(pr);
				String ch = YoldaFuel.change(p.changeOf(ty));
				TextView cv = text(ctx, ch.isEmpty() ? "değişim yok" : (ch + " ₺ bu hafta"), 13, true,
						ch.startsWith("▲") ? 0xFFF87171 : ch.startsWith("▼") ? 0xFF4ADE80 : t.muted);
				b.addView(cv);
				b.setClickable(true);
				b.setContentDescription(YoldaFuel.label(ty) + " " + YoldaFuel.money(p.of(ty)) + (sel ? ", seçili" : ", seç"));
				b.setOnClickListener(v -> {
					YoldaFuel.setType(ctx, ty);
					render();
				});
				LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -1, 1f);
				if (i > 0) bl.leftMargin = dp(8);
				rw.addView(b, bl);
			}
			col.addView(rw, rl);

			// Maliyet: 100 km ve son yolculuk
			double per = YoldaFuel.consumption(ctx), price = p.of(type);
			LinearLayout cost = card(ctx);
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
			cl.topMargin = dp(16);
			cost.addView(text(ctx, "Yakıt maliyeti · " + YoldaFuel.label(type) + ", " + fmt(per) + " L / 100 km", 14, false, t.muted));
			cost.addView(text(ctx, "100 km ≈ " + YoldaFuel.money(YoldaFuel.cost(100_000, per, price)), 24, true, t.ink));
			List<YoldaRadar.TripItem> trips = YoldaRadar.tripItems(ctx);
			if (!trips.isEmpty()) {
				YoldaRadar.TripItem last = trips.get(0);
				String rt = last.route().isEmpty() ? "Son yolculuk" : last.route();
				cost.addView(text(ctx, rt + " · " + String.format(new Locale("tr"), "%.1f km", last.rec().meters() / 1000f)
						+ " ≈ " + YoldaFuel.money(YoldaFuel.cost(last.rec().meters(), per, price)), 15, false, t.ink));
			}
			col.addView(cost, cl);

			// Tüketim ayarı: − / +
			LinearLayout adj = new LinearLayout(ctx);
			adj.setGravity(Gravity.CENTER_VERTICAL);
			LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(56));
			al.topMargin = dp(8);
			TextView lab = text(ctx, "Aracının tüketimi: " + fmt(per) + " L / 100 km", 16, true, t.ink);
			adj.addView(lab, new LinearLayout.LayoutParams(0, -2, 1f));
			adj.addView(button(ctx, "−", v -> {
				YoldaFuel.setConsumption(ctx, per - 0.5);
				render();
			}), new LinearLayout.LayoutParams(dp(56), dp(56)));
			LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(56), dp(56));
			pl.leftMargin = dp(8);
			adj.addView(button(ctx, "+", v -> {
				YoldaFuel.setConsumption(ctx, per + 0.5);
				render();
			}), pl);
			col.addView(adj, al);
		}

		// Eylemler
		LinearLayout acts = new LinearLayout(ctx);
		LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(52));
		al.topMargin = dp(16);
		acts.addView(button(ctx, "⛽ En yakın benzinlik", v -> NavFragment.navigateTo(ctx, "benzin istasyonu", false)),
				new LinearLayout.LayoutParams(0, -1, 1f));
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -1, 1f);
		rl.leftMargin = dp(8);
		acts.addView(button(ctx, loading ? "Yenileniyor…" : "Yenile", v -> load(true)), rl);
		col.addView(acts, al);

		// Kaynak (lisans gereği görünür) ve tarih
		String when = "";
		if ((p != null) && (p.fetchedAt().length() >= 10)) {
			String d = p.fetchedAt().substring(0, 10); // yyyy-MM-dd
			when = " · " + d.substring(8, 10) + "." + d.substring(5, 7) + "." + d.substring(0, 4);
		}
		TextView src = text(ctx, YoldaFuel.ATTRIBUTION + when + ". İstasyon fiyatları farklı olabilir.", 12, false, t.muted);
		src.setPadding(0, dp(16), 0, 0);
		col.addView(src);
	}

	private static String fmt(double v) {
		return String.format(new Locale("tr"), "%.1f", v);
	}

	private LinearLayout card(Context ctx) {
		LinearLayout l = new LinearLayout(ctx);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBackground(YoldaStyle.glass(ctx, t, 20));
		l.setPadding(dp(16), dp(16), dp(16), dp(16));
		return l;
	}

	private TextView button(Context ctx, String label, View.OnClickListener click) {
		TextView b = text(ctx, label, 16, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setSingleLine(true);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setPadding(dp(12), 0, dp(12), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription(label.equals("−") ? "Tüketimi azalt" : label.equals("+") ? "Tüketimi artır" : label);
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
