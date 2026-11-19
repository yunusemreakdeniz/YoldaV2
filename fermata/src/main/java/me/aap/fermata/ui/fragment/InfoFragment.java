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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Bilgi kartlarının sayfası (Döviz ve altın, Namaz vakitleri, Haberler): hangisi açılacağı {@link #mode}.
 * Veri arka planda yenilenir; satırlar en az 56dp; haberlerde satıra dokununca başlık okunur, basılı tutunca
 * haber tarayıcıda açılır.
 */
public class InfoFragment extends MainActivityFragment {
	public static final String FX = "fx", PRAYER = "prayer", NEWS = "news";
	/** Açılacak bilgi: FX, PRAYER ya da NEWS (kart açmadan önce yazılır). */
	public static volatile String mode = FX;
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private boolean loading;
	private List<YoldaInfo.Rate> rates = new ArrayList<>();
	private List<YoldaInfo.Prayer> prayers = new ArrayList<>();
	private List<YoldaInfo.News> news = new ArrayList<>();

	@Override
	public int getFragmentId() {
		return R.id.yolda_info_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return title(mode);
	}

	static String title(String m) {
		return switch (m) {
			case PRAYER -> "Namaz vakitleri";
			case NEWS -> "Haberler";
			default -> "Döviz ve altın";
		};
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
		Context app = requireContext().getApplicationContext();
		String m = mode;
		// önce önbellek (anında), sonra ağ
		rates = YoldaInfo.rates(app, false);
		prayers = YoldaInfo.prayers(app, false);
		news = YoldaInfo.news(app, false);
		render();
		if (loading) return;
		loading = true;
		me.aap.utils.app.App.get().execute(() -> {
			if (force) app.getSharedPreferences("yolda_info", Context.MODE_PRIVATE).edit()
					.remove("fx_at").remove("news_at").remove("pr_day").apply();
			switch (m) {
				case PRAYER -> YoldaInfo.prayers(app, true);
				case NEWS -> YoldaInfo.news(app, true);
				default -> YoldaInfo.rates(app, true);
			}
		}).onCompletion((r, err) -> me.aap.utils.app.App.get().getHandler().post(() -> {
			loading = false;
			if (!isAdded()) return;
			rates = YoldaInfo.rates(app, false);
			prayers = YoldaInfo.prayers(app, false);
			news = YoldaInfo.news(app, false);
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

		col.addView(text(ctx, title(mode), YoldaStyle.titleSp(ctx), true, t.ink));
		switch (mode) {
			case PRAYER -> renderPrayer(ctx, col);
			case NEWS -> renderNews(ctx, col);
			default -> renderFx(ctx, col);
		}
		TextView rf = button(ctx, loading ? "Yenileniyor…" : "Yenile", v -> load(true));
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(52));
		rl.topMargin = dp(24);
		col.addView(rf, rl);
		TextView src = text(ctx, switch (mode) {
			case PRAYER -> "Kaynak: Aladhan (Diyanet hesap yöntemi), bulunduğun yere göre";
			case NEWS -> "Kaynak: TRT Haber son dakika, Anadolu Ajansı";
			default -> "Kaynak: TCMB günlük kurları (döviz satış), Truncgil (altın)";
		}, 12, false, t.muted);
		src.setPadding(0, dp(12), 0, 0);
		col.addView(src);
	}

	private void empty(Context ctx, LinearLayout col) {
		TextView m = text(ctx, loading ? "Yükleniyor…" : "Veri alınamadı. İnternet bağlantısını kontrol et.", 16, false, t.muted);
		m.setPadding(0, dp(16), 0, 0);
		col.addView(m);
	}

	private void renderFx(Context ctx, LinearLayout col) {
		if (rates.isEmpty()) {
			empty(ctx, col);
			return;
		}
		for (YoldaInfo.Rate r : rates) {
			LinearLayout row = row(ctx);
			LinearLayout tx = new LinearLayout(ctx);
			tx.setOrientation(LinearLayout.VERTICAL);
			tx.addView(text(ctx, r.name(), 18, true, t.ink));
			tx.addView(text(ctx, "Alış " + YoldaInfo.money(r.buy()), 13, false, t.muted));
			row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
			row.addView(text(ctx, YoldaInfo.money(r.sell()), 22, true, t.ink));
			row.setContentDescription(r.name() + " " + YoldaInfo.money(r.sell()));
			col.addView(row, rowLp());
		}
	}

	private void renderPrayer(Context ctx, LinearLayout col) {
		if (prayers.isEmpty()) {
			empty(ctx, col);
			return;
		}
		Calendar c = Calendar.getInstance();
		int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
		YoldaInfo.Prayer nx = YoldaInfo.next(prayers, now);
		if (nx != null) {
			TextView h = text(ctx, "Sıradaki: " + nx.name() + " " + nx.time() + " · " + YoldaInfo.left(nx, now) + " kaldı", 16, true,
					t.accent | 0xFF000000);
			h.setPadding(0, dp(8), 0, 0);
			col.addView(h);
		}
		for (YoldaInfo.Prayer p : prayers) {
			boolean sel = (nx != null) && p.name().equals(nx.name());
			LinearLayout row = row(ctx);
			if (sel) row.setBackground(YoldaStyle.selected(ctx, 16));
			row.addView(text(ctx, p.name(), 18, sel, t.ink), new LinearLayout.LayoutParams(0, -2, 1f));
			row.addView(text(ctx, p.time(), 22, true, t.ink));
			row.setContentDescription(p.name() + " " + p.time());
			col.addView(row, rowLp());
		}
	}

	private void renderNews(Context ctx, LinearLayout col) {
		if (news.isEmpty()) {
			empty(ctx, col);
			return;
		}
		TextView all = button(ctx, "🔊 İlk beş başlığı oku · ⏹ yeniden dokun: durdur", v -> {
			if (YoldaSpeak.isSpeaking()) YoldaSpeak.stop();
			else YoldaSpeak.sayAlways(ctx, YoldaInfo.newsSpoken(news, 5));
		});
		LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(52));
		al.topMargin = dp(16);
		col.addView(all, al);
		for (YoldaInfo.News n : news) {
			LinearLayout row = row(ctx);
			TextView tv = text(ctx, n.title(), 16, false, t.ink);
			tv.setMaxLines(3);
			tv.setEllipsize(TextUtils.TruncateAt.END);
			row.addView(tv, new LinearLayout.LayoutParams(-1, -2));
			row.setClickable(true);
			row.setContentDescription(n.title());
			row.setOnClickListener(v -> {
				if (YoldaSpeak.isSpeaking()) YoldaSpeak.stop();
				else YoldaSpeak.sayAlways(ctx, n.title());
			});
			row.setOnLongClickListener(v -> {
				if (n.link().isEmpty()) return false;
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(n.link())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} catch (Throwable ex) {
					YoldaStyle.toast(ctx, "Haber açılamadı");
				}
				return true;
			});
			col.addView(row, rowLp());
		}
	}

	private LinearLayout row(Context ctx) {
		LinearLayout r = new LinearLayout(ctx);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setBackground(YoldaStyle.glass(ctx, t, 16));
		r.setPadding(dp(16), dp(10), dp(16), dp(10));
		r.setMinimumHeight(dp(56));
		return r;
	}

	private LinearLayout.LayoutParams rowLp() {
		LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
		l.topMargin = dp(8);
		return l;
	}

	private TextView button(Context ctx, String label, View.OnClickListener click) {
		TextView b = text(ctx, label, 16, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription(label);
		return b;
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
