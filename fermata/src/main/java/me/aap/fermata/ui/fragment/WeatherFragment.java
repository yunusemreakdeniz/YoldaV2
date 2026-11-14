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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Hava durumu (uygulama içi, tarayıcı değil): şimdiki hava, hissedilen, nem, rüzgâr, gün doğumu / batımı;
 * 24 saatlik kaydırılabilir şerit; 7 günlük liste. Open-Meteo; 15 dakika saklanır, "Yenile" ile hemen.
 */
public class WeatherFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	@Nullable
	private YoldaWeather.Detail data;
	private boolean loading;
	private String error;

	@Override
	public int getFragmentId() {
		return R.id.yolda_weather_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Hava durumu";
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
		if (!hidden) load(false);
	}

	private void load(boolean force) {
		loading = true;
		error = null;
		render();
		YoldaWeather.detail(requireContext(), force).main().onCompletion((d, err) -> {
			loading = false;
			if (d != null) data = d;
			else error = "Hava durumu alınamadı (internet bağlantısı?)";
			if (isAdded()) render();
		});
	}

	/** Testler için. */
	@Nullable
	public YoldaWeather.Detail getData() {
		return data;
	}

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		YoldaStyle.applyScreenBackground(root, t);
		ScrollView scroll = new ScrollView(ctx);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(YoldaStyle.side(ctx)), dp(YoldaStyle.top(ctx)), dp(YoldaStyle.side(ctx)), dp(190));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		// Başlık: yer + yenile
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = text(ctx, (data != null) ? data.place() : "Hava durumu", YoldaStyle.titleSp(ctx), true, t.ink);
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
		TextView refresh = pill(ctx, loading ? "Yükleniyor…" : "↻ Yenile");
		refresh.setOnClickListener(v -> {
			if (!loading) load(true);
		});
		head.addView(refresh, new LinearLayout.LayoutParams(-2, dp(48)));
		col.addView(head);

		YoldaWeather.Detail d = data;
		if (d == null) {
			TextView msg = text(ctx, (error != null) ? error : "Hava durumu alınıyor…", 16, false, t.muted);
			msg.setPadding(dp(4), dp(24), dp(4), 0);
			col.addView(msg);
			return;
		}

		// Şimdi
		LinearLayout now = card(ctx);
		LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
		nlp.topMargin = dp(14);
		col.addView(now, nlp);
		LinearLayout big = new LinearLayout(ctx);
		big.setOrientation(LinearLayout.HORIZONTAL);
		big.setGravity(Gravity.CENTER_VERTICAL);
		big.addView(text(ctx, YoldaWeather.emoji(d.code()), 54, false, t.ink));
		LinearLayout bt = new LinearLayout(ctx);
		bt.setOrientation(LinearLayout.VERTICAL);
		bt.setPadding(dp(16), 0, 0, 0);
		bt.addView(text(ctx, d.temp() + "°", 52, true, t.ink));
		bt.addView(text(ctx, YoldaWeather.text(d.code()), 18, true, t.ink));
		big.addView(bt);
		now.addView(big);
		TextView facts = text(ctx, "Hissedilen " + d.feels() + "° · Nem %" + d.humidity() + " · Rüzgâr " + d.wind() + " km/s"
				+ (d.sunrise().isEmpty() ? "" : ("\nGün doğumu " + d.sunrise() + " · Gün batımı " + d.sunset())), 15, false, t.muted);
		facts.setPadding(0, dp(10), 0, 0);
		now.addView(facts);
		now.setContentDescription("Şimdi " + d.temp() + " derece, " + YoldaWeather.text(d.code()) + ", hissedilen " + d.feels());

		// Saatlik
		col.addView(section(ctx, "Saatlik"));
		LinearLayout hours = new LinearLayout(ctx);
		hours.setOrientation(LinearLayout.HORIZONTAL);
		for (YoldaWeather.HourD h : d.hours()) {
			LinearLayout c = card(ctx);
			c.setGravity(Gravity.CENTER_HORIZONTAL);
			c.setPadding(dp(14), dp(12), dp(14), dp(12));
			c.addView(text(ctx, h.time(), 13, false, t.muted));
			c.addView(text(ctx, YoldaWeather.emoji(h.code()), 24, false, t.ink));
			c.addView(text(ctx, h.temp() + "°", 17, true, t.ink));
			c.addView(text(ctx, (h.pop() > 0) ? ("💧" + h.pop() + "%") : " ", 12, false, 0xFF7DD3FC));
			c.setContentDescription("Saat " + h.time() + ", " + h.temp() + " derece" + ((h.pop() > 0) ? (", yağış yüzde " + h.pop()) : ""));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
			lp.rightMargin = dp(8);
			hours.addView(c, lp);
		}
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(hours);
		col.addView(hs);

		// 7 gün
		col.addView(section(ctx, "7 gün"));
		SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
		SimpleDateFormat out = new SimpleDateFormat("EEEE", new Locale("tr"));
		for (int i = 0; i < d.days().size(); i++) {
			YoldaWeather.Day day = d.days().get(i);
			String name;
			if (i == 0) name = "Bugün";
			else if (i == 1) name = "Yarın";
			else {
				try {
					name = out.format(in.parse(day.date()));
					name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
				} catch (Exception ex) {
					name = day.date();
				}
			}
			LinearLayout r = card(ctx);
			r.setOrientation(LinearLayout.HORIZONTAL);
			r.setGravity(Gravity.CENTER_VERTICAL);
			r.setPadding(dp(16), dp(10), dp(16), dp(10));
			TextView dn = text(ctx, name, 16, true, t.ink);
			r.addView(dn, new LinearLayout.LayoutParams(0, -2, 1f));
			r.addView(text(ctx, YoldaWeather.emoji(day.code()), 22, false, t.ink));
			TextView pop = text(ctx, (day.pop() > 0) ? ("💧" + day.pop() + "%") : "", 13, false, 0xFF7DD3FC);
			pop.setGravity(Gravity.END);
			r.addView(pop, new LinearLayout.LayoutParams(dp(64), -2));
			TextView mm = text(ctx, day.max() + "° / " + day.min() + "°", 16, true, t.ink);
			mm.setGravity(Gravity.END);
			r.addView(mm, new LinearLayout.LayoutParams(dp(96), -2));
			r.setContentDescription(name + ", " + YoldaWeather.text(day.code()) + ", en yüksek " + day.max() + ", en düşük "
					+ day.min() + ((day.pop() > 0) ? (", yağış yüzde " + day.pop()) : ""));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.topMargin = dp(8);
			col.addView(r, lp);
		}
		TextView src = text(ctx, "Kaynak: Open-Meteo", 12, false, t.muted);
		src.setPadding(dp(4), dp(16), dp(4), 0);
		col.addView(src);
	}

	private LinearLayout card(Context ctx) {
		LinearLayout l = new LinearLayout(ctx);
		l.setOrientation(LinearLayout.VERTICAL);
		l.setBackground(YoldaStyle.glass(ctx, t, 20));
		l.setPadding(dp(18), dp(16), dp(18), dp(16));
		return l;
	}

	private TextView section(Context ctx, String s) {
		TextView v = text(ctx, s, 18, true, t.ink);
		v.setPadding(dp(4), dp(20), dp(4), dp(8));
		return v;
	}

	private TextView pill(Context ctx, String s) {
		TextView b = text(ctx, s, 15, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(18), 0, dp(18), 0);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setClickable(true);
		b.setContentDescription(s);
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
