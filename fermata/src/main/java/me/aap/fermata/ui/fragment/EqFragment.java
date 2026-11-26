package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import me.aap.fermata.R;
import me.aap.fermata.media.engine.YoldaEq;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Ekolayzer sayfası (Ekolayzer kartına dokununca): hazır ayarlar iki sütunlu büyük kutucuklar (seçili vurgulu,
 * dokununca çalan yayına hemen uygulanır), her birinin kısa açıklaması; bir şey çalarken "Bant ayarları".
 */
public class EqFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;

	@Override
	public int getFragmentId() {
		return R.id.yolda_eq_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Ekolayzer";
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

	static String hint(String preset) {
		return switch (preset) {
			case "Kapalı" -> "Ses olduğu gibi";
			case "Pop" -> "Vokal ve orta sesler önde";
			case "Rock" -> "Bas ve tiz güçlü";
			case "Konuşma" -> "Haber, podcast: sesler net";
			case "Bas" -> "Derin bas";
			case "Araç" -> "Yol uğultusuna karşı";
			case "Klasik" -> "Geniş, dengeli";
			case "Caz" -> "Sıcak, yumuşak";
			case "Özel" -> "Aşağıdaki bantlarla kendi ayarın";
			default -> "";
		};
	}

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
		MainActivityDelegate a = getActivityDelegate();
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

		col.addView(text(ctx, "Ekolayzer", YoldaStyle.titleSp(ctx), true, t.ink));
		TextView sub = text(ctx, "Sesle: “rock ekolayzer”, “ses modunu konuşma yap”, “ekolayzeri kapat”", 14, false, t.muted);
		sub.setPadding(0, dp(4), 0, dp(8));
		col.addView(sub);

		String cur = YoldaEq.current(ctx);
		String[] names = YoldaEq.names();
		for (int i = 0; i < names.length; i += 2) {
			LinearLayout rw = new LinearLayout(ctx);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(88));
			rl.topMargin = dp(8);
			for (int c = 0; c < 2; c++) {
				LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -1, 1f);
				if (c == 1) bl.leftMargin = dp(8);
				if (i + c >= names.length) {
					rw.addView(new View(ctx), bl);
					continue;
				}
				String n = names[i + c];
				boolean sel = n.equals(cur);
				LinearLayout b = new LinearLayout(ctx);
				b.setOrientation(LinearLayout.VERTICAL);
				b.setGravity(Gravity.CENTER_VERTICAL);
				b.setPadding(dp(16), dp(8), dp(16), dp(8));
				if (sel) {
					GradientDrawable g = new GradientDrawable();
					g.setCornerRadius(dp(20));
					g.setColor(0xFFFFF4D6);
					b.setBackground(g);
				} else {
					b.setBackground(YoldaStyle.glass(ctx, t, 20));
				}
				b.addView(text(ctx, (sel ? "✓ " : "") + n, 20, true, sel ? 0xFF111827 : t.ink));
				b.addView(text(ctx, hint(n), 13, false, sel ? 0xFF374151 : t.muted));
				b.setClickable(true);
				b.setContentDescription("Ses ayarı " + n + (sel ? ", seçili" : ""));
				b.setOnClickListener(v -> {
					YoldaEq.setCurrent(ctx, n);
					a.getMediaSessionCallback().yoldaEqChanged();
					YoldaStyle.toast(ctx, "Ses ayarı: " + n);
					render();
				});
				rw.addView(b, bl);
			}
			col.addView(rw, rl);
		}

		col.addView(bandsPanel(ctx, a, cur));

		var eng = a.getMediaSessionCallback().getEngine();
		boolean can = (eng != null) && (eng.getAudioEffects() != null);
		TextView bands = text(ctx, can ? "Gelişmiş ayarlar (Fermata)" : "Gelişmiş ayarlar (önce bir şey çal)", 16, true, can ? t.ink : t.muted);
		bands.setGravity(Gravity.CENTER);
		bands.setBackground(YoldaStyle.glass(ctx, t, 999));
		bands.setClickable(true);
		bands.setContentDescription("Gelişmiş ekolayzer ayarları");
		bands.setOnClickListener(v -> {
			var e = a.getMediaSessionCallback().getEngine();
			if ((e != null) && (e.getAudioEffects() != null)) a.showFragment(R.id.audio_effects_fragment);
			else YoldaStyle.toast(ctx, "Bant ayarları için önce bir şey çal");
		});
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(56));
		bl.topMargin = dp(24);
		col.addView(bands, bl);
	}

	/**
	 * Bantlar: beş büyük dikey sürgü (60 Hz - 14 kHz, -10..+10 dB). Seçili hazır ayarın değerlerini gösterir;
	 * bir sürgü oynatılınca "Özel" seçilir, ses kısa aralıklarla ve bırakınca hemen uygulanır.
	 */
	private View bandsPanel(Context ctx, MainActivityDelegate a, String cur) {
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(dp(16), dp(16), dp(16), dp(12));
		box.setBackground(YoldaStyle.glass(ctx, t, 24));
		LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
		blp.topMargin = dp(16);
		box.setLayoutParams(blp);
		boolean custom = YoldaEq.CUSTOM.equals(cur);
		box.addView(text(ctx, custom ? "Bantlar · Özel" : ("Bantlar · " + cur + " (oynatınca Özel olur)"), 17, true, t.ink));
		int[] g = YoldaEq.gains(ctx, cur);
		// eğri (Mola'dan): bantların birlikte nasıl bir ses verdiği bir bakışta
		YoldaEqCurve curve = new YoldaEqCurve(ctx, 0xFFFFD27A);
		curve.setGains(g);
		LinearLayout.LayoutParams cvl = new LinearLayout.LayoutParams(-1, dp(a.isCarActivity() ? 64 : 88));
		cvl.topMargin = dp(10);
		box.addView(curve, cvl);
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setPadding(0, dp(12), 0, 0);
		boolean car = a.isCarActivity();
		int h = dp(car ? 200 : 260);
		TextView[] vals = new TextView[g.length];
		long[] lastApply = {0};
		for (int i = 0; i < g.length; i++) {
			int bi = i;
			LinearLayout c = new LinearLayout(ctx);
			c.setOrientation(LinearLayout.VERTICAL);
			c.setGravity(Gravity.CENTER_HORIZONTAL);
			TextView v = text(ctx, YoldaEqBand.fmt(g[i]), 16, true, t.ink);
			v.setGravity(Gravity.CENTER);
			vals[i] = v;
			c.addView(v, new LinearLayout.LayoutParams(-1, -2));
			YoldaEqBand band = new YoldaEqBand(ctx, g[i], 0xFFFFD27A);
			band.setContentDescription(YoldaEq.bandLabel(i) + " bandı, " + YoldaEqBand.fmt(g[i]) + " desibel");
			band.listener = (db, done) -> {
				g[bi] = db;
				curve.setGains(g);
				vals[bi].setText(YoldaEqBand.fmt(db));
				band.setContentDescription(YoldaEq.bandLabel(bi) + " bandı, " + YoldaEqBand.fmt(db) + " desibel");
				long now = System.currentTimeMillis();
				if (done || (now - lastApply[0] > 300)) { // ses kısa aralıklarla: sürüklerken de duyulsun
					lastApply[0] = now;
					YoldaEq.setCustom(ctx, g);
					a.getMediaSessionCallback().yoldaEqChanged();
				}
				if (done && !YoldaEq.CUSTOM.equals(cur)) render(); // başlık "Özel", kutucuk seçimi
			};
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, h);
			bl.topMargin = dp(8);
			c.addView(band, bl);
			TextView f = text(ctx, YoldaEq.bandLabel(i), 13, false, t.muted);
			f.setGravity(Gravity.CENTER);
			f.setPadding(0, dp(8), 0, 0);
			c.addView(f, new LinearLayout.LayoutParams(-1, -2));
			row.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
		}
		box.addView(row);
		TextView reset = text(ctx, "Sıfırla", 15, true, t.ink);
		reset.setGravity(Gravity.CENTER);
		reset.setBackground(YoldaStyle.glass(ctx, t, 999));
		reset.setClickable(true);
		reset.setOnClickListener(v -> {
			YoldaEq.setCustom(ctx, new int[g.length]);
			a.getMediaSessionCallback().yoldaEqChanged();
			render();
		});
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(48));
		rl.topMargin = dp(12);
		box.addView(reset, rl);
		return box;
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
