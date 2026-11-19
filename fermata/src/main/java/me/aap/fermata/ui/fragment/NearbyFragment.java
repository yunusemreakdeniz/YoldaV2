package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Typeface;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Yakın yerler sayfası: {@link #CHARGER} bulunduğun yerin çevresindeki şarj istasyonları (10 km),
 * {@link #PARK_DEST} son yol tarifi verilen yerin çevresindeki otoparklar (800 m). Her satırda uzaklık, bilgi
 * (ücret, kapasite, priz türü, güç) ve "Git".
 */
public class NearbyFragment extends MainActivityFragment {
	public static final String CHARGER = "charger", PARK_DEST = "park_dest";
	public static volatile String mode = CHARGER;
	/** Otopark aranacak yer (boşsa son gidilen yer). */
	@Nullable
	public static volatile String place;
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private boolean loading;
	private String status = "";
	private List<YoldaNearby.Spot> spots = new ArrayList<>();
	/** Şarj kartı için son bulunanlar (kart yüzünde gösterilir). */
	static volatile List<YoldaNearby.Spot> lastChargers = new ArrayList<>();
	static volatile long lastChargersAt;

	@Override
	public int getFragmentId() {
		return R.id.yolda_nearby_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return CHARGER.equals(mode) ? "Şarj istasyonları" : "Gideceğin yerdeki otoparklar";
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
		load();
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
		if (!hidden) load();
	}

	/** Gidilecek yer: verilen ya da son yol tarifi. */
	@Nullable
	static String destination(Context ctx) {
		String p = place;
		if ((p != null) && !p.isEmpty()) return p;
		List<String> r = NavFragment.recent(ctx);
		return r.isEmpty() ? null : r.get(0);
	}

	private void load() {
		if (loading) return;
		Context ctx = requireContext();
		boolean charger = CHARGER.equals(mode);
		String dest = charger ? null : destination(ctx);
		double la = YoldaRadar.lat, lo = YoldaRadar.lon;
		if (!charger && (dest == null)) {
			status = "Önce Navigasyon'dan bir yere yol tarifi al; varacağın yerin otoparkları burada görünür.";
			spots = new ArrayList<>();
			render();
			return;
		}
		if (charger && Double.isNaN(la)) {
			status = "Konum henüz bilinmiyor; birkaç saniye sonra Yenile.";
			render();
			return;
		}
		loading = true;
		status = charger ? "Yakındaki şarj istasyonları aranıyor…" : ("“" + dest + "” çevresinde otopark aranıyor…");
		render();
		me.aap.utils.function.CheckedSupplier<List<YoldaNearby.Spot>, Throwable> job = () -> {
			if (charger) return YoldaNearby.find(la, lo, true, 10_000, 30);
			double[] at = YoldaNearby.geocode(dest, la, lo);
			if (at == null) return null;
			return YoldaNearby.find(at[0], at[1], false, 800, 30);
		};
		me.aap.utils.app.App.get().execute(job).onCompletion((l, err) -> me.aap.utils.app.App.get().getHandler().post(() -> {
			loading = false;
			if (!isAdded()) return;
			if (err != null) {
				status = "Alınamadı (internet ya da harita sunucusu); Yenile ile tekrar dene.";
				spots = new ArrayList<>();
			} else if (l == null) {
				status = "“" + dest + "” haritada bulunamadı.";
				spots = new ArrayList<>();
			} else {
				spots = l;
				status = l.isEmpty() ? (charger ? "10 km içinde şarj istasyonu bulunamadı." : "800 m içinde otopark bulunamadı.")
						: (l.size() + (charger ? " şarj istasyonu" : (" otopark · " + dest)));
				if (charger) {
					lastChargers = l;
					lastChargersAt = System.currentTimeMillis();
				}
			}
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
		col.addView(text(ctx, getTitle().toString(), YoldaStyle.titleSp(ctx), true, t.ink));
		TextView st = text(ctx, status, 14, false, t.muted);
		st.setPadding(0, dp(4), 0, dp(8));
		col.addView(st);
		for (YoldaNearby.Spot s : spots) {
			LinearLayout row = new LinearLayout(ctx);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setBackground(YoldaStyle.glass(ctx, t, 16));
			row.setPadding(dp(16), dp(8), dp(8), dp(8));
			row.setMinimumHeight(dp(64));
			LinearLayout tx = new LinearLayout(ctx);
			tx.setOrientation(LinearLayout.VERTICAL);
			TextView n = text(ctx, s.name(), 16, true, t.ink);
			n.setSingleLine(true);
			n.setEllipsize(TextUtils.TruncateAt.END);
			tx.addView(n);
			String sub = YoldaPlaceSuggest.distance(s.meters()) + (s.info().isEmpty() ? "" : (" · " + s.info()));
			TextView sb = text(ctx, sub, 13, false, t.muted);
			sb.setMaxLines(2);
			tx.addView(sb);
			row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
			TextView go = text(ctx, "Git", 15, true, t.ink);
			go.setGravity(Gravity.CENTER);
			go.setPadding(dp(20), 0, dp(20), 0);
			go.setBackground(YoldaStyle.glass(ctx, t, 999));
			go.setClickable(true);
			go.setContentDescription("Git: " + s.name());
			go.setOnClickListener(v -> NavFragment.navigateTo(ctx, String.format(Locale.ROOT, "%.6f,%.6f", s.lat(), s.lon()), false));
			row.addView(go, new LinearLayout.LayoutParams(-2, dp(48)));
			row.setContentDescription(s.name() + ", " + sub);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
			rl.topMargin = dp(8);
			col.addView(row, rl);
		}
		TextView rf = text(ctx, loading ? "Aranıyor…" : "Yenile", 16, true, t.ink);
		rf.setGravity(Gravity.CENTER);
		rf.setBackground(YoldaStyle.glass(ctx, t, 999));
		rf.setClickable(true);
		rf.setOnClickListener(v -> load());
		LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, dp(52));
		fl.topMargin = dp(24);
		col.addView(rf, fl);
		TextView src = text(ctx, "Veri: OpenStreetMap katkıcıları (ODbL)", 12, false, t.muted);
		src.setPadding(0, dp(12), 0, 0);
		col.addView(src);
	}

	private static TextView text(Context ctx, String s, float size, boolean bold, int color) {
		TextView v = new TextView(ctx);
		v.setText(s);
		v.setTextSize(YoldaStyle.type(size) * YoldaStyle.textScale(ctx));
		v.setTextColor(color);
		if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
		return v;
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
