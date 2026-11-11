package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * Araç modu kurulum ekranı (yalnız telefonda). Yolda'nın araçtaki kendi tasarımının açılması için
 * uygulamayı Play Store kaynağıyla yeniden kurar (gömülü Shizuku). Android Auto "Bilinmeyen kaynaklar"
 * adımını da buradan açtırır. Hazır olunca ya da kullanıcı atlayınca ana ekrana geçilir.
 */
public class YoldaSetupFragment extends MainActivityFragment {
	private Object shizukuListener;
	private FrameLayout root;
	private YoldaStyle.Theme t;

	@Override
	public int getFragmentId() {
		return R.id.yolda_setup_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Araç modu kurulumu";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance;
	}

	@Override
	public me.aap.utils.ui.view.FloatingButton.Mediator getFloatingButtonMediator() {
		return YoldaNoFab.instance; // yüzen ≡ düğmesi yok
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
		addShizukuListener();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && root != null) render();
	}

	@Override
	public void onDestroyView() {
		removeShizukuListener();
		super.onDestroyView();
	}

	@SuppressWarnings("unchecked")
	private void addShizukuListener() {
		try {
			rikka.shizuku.Shizuku.OnRequestPermissionResultListener l = (code, result) -> {
				if (isAdded()) render();
			};
			rikka.shizuku.Shizuku.addRequestPermissionResultListener(l);
			shizukuListener = l;
		} catch (Throwable ignore) {
		}
	}

	private void removeShizukuListener() {
		try {
			if (shizukuListener != null)
				rikka.shizuku.Shizuku.removeRequestPermissionResultListener(
						(rikka.shizuku.Shizuku.OnRequestPermissionResultListener) shizukuListener);
		} catch (Throwable ignore) {
		}
		shizukuListener = null;
	}

	/** Döndürmede (dikey ↔ yatay) ölçüler yeni ekran genişliğine göre baştan hesaplansın. */
	@Override
	public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (getView() != null) getView().post(this::render);
	}

	private void render() {
		Context ctx = requireContext();
		t = YoldaStyle.current(ctx);
		root.removeAllViews();
		YoldaStyle.applyScreenBackground(root, t);
		MainActivityDelegate a = getActivityDelegate();

		ScrollView scroll = new ScrollView(ctx);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(me.aap.fermata.ui.view.YoldaStyle.side(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.top(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.side(ctx)), dp(me.aap.fermata.ui.view.YoldaStyle.bottom(ctx)));
		scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
		root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

		boolean ready = YoldaInstaller.isCarReady(ctx);
		boolean seen = YoldaInstaller.seenInCar(ctx);
		col.addView(text(ctx, (ready && seen) ? "Araç modu hazır ✓" : "Android Auto kurulumu", 34, true));
		TextView intro = text(ctx, (ready && seen)
				? "Her şey tamam. Telefonu arabaya bağla; araç ekranında “Yolda”ya dokun, bu tasarım açılır."
				: "Araç ekranında Yolda'nın bu tasarımının açılması için aşağıdaki adımlar bir kez gerekiyor. "
				+ "Yolda araçta ilk açıldığında bu liste kendiliğinden kapanır.", 15, false);
		intro.setTextColor(0xD9FFFFFF);
		intro.setPadding(0, dp(8), 0, dp(20));
		col.addView(intro);

		if (ready && seen) {
			col.addView(bigButton(ctx, "Ana ekrana geç", true, v -> a.showFragment(R.id.home_fragment)));
			return;
		}

		// 1) Kurulum kaynağı: KingInstaller / Play Store olarak görünmeli
		if (ready) {
			col.addView(step(ctx, "✓", "Kurulum tamam",
					"Yolda “" + YoldaInstaller.installerLabel(ctx) + "” kaynağından kurulmuş görünüyor "
							+ "(KingInstaller böyle kurar). Android Auto bunu kabul eder.", null, null, true));
		} else {
			boolean installed = YoldaInstaller.shizukuInstalled(ctx);
			boolean running = YoldaInstaller.shizukuRunning();
			boolean granted = YoldaInstaller.shizukuGranted();
			String desc = "Şu an “" + YoldaInstaller.installerLabel(ctx) + "” ile kurulu; Android Auto bunu gizler. "
					+ "En kolayı: APK'yı KingInstaller ile yeniden kur. KingInstaller yoksa Shizuku ile Yolda kendini hazırlar:\n";
			String btn;
			View.OnClickListener act;
			if (!installed) {
				desc += "Önce Shizuku'yu kur.";
				btn = "Shizuku'yu indir";
				act = v -> openUrl(ctx, "https://shizuku.rikka.app/download/");
			} else if (!running) {
				desc += "Shizuku'yu aç ve “kablosuz hata ayıklama” ile başlat, sonra buraya dön.";
				btn = "Shizuku'yu aç";
				act = v -> openApp(ctx, "moe.shizuku.privileged.api");
			} else if (!granted) {
				desc += "Shizuku çalışıyor; Yolda'ya izin ver.";
				btn = "Shizuku iznini ver";
				act = v -> YoldaInstaller.requestShizuku();
			} else {
				desc += "Shizuku hazır. Yolda kendini yeniden kurup yeniden başlar.";
				btn = "Aracı hazırla";
				act = v -> prepare(ctx, a);
			}
			col.addView(step(ctx, "1", "Kurulum kaynağı", desc, btn, act, false));
		}

		// 2) Android Auto: bilinmeyen kaynaklar
		col.addView(step(ctx, "2", "Bilinmeyen kaynakları aç",
				"Android Auto ayarları → en alttaki “Sürüm” satırına 10 kez dokun → sağ üst ⋮ → "
						+ "Geliştirici ayarları → “Bilinmeyen kaynaklar”ı aç.",
				"Android Auto ayarlarını aç", v -> openAndroidAuto(ctx), false));

		// 3) Başlatıcı
		col.addView(step(ctx, "3", "Başlatıcıda Yolda açık olsun",
				"Android Auto ayarları → “Başlatıcıyı özelleştir” → “Yolda” işaretli olsun. "
						+ "Yeni kurulan uygulamalar bazen kapalı gelir.",
				"Android Auto ayarlarını aç", v -> openAndroidAuto(ctx), false));

		// 4) Araçta aç
		col.addView(step(ctx, "4", "Araçta “Yolda”ya dokun",
				"Telefonu arabaya bağla (bağlıysa çıkarıp yeniden tak). Araç ekranında “Yolda” simgesine dokun; "
						+ "“Yolda Liste” Android Auto'nun düz listesidir, o değil. Yolda açılınca bu liste kendiliğinden kapanır.",
				"Araçta gördüm ✓", v -> {
					YoldaInstaller.markSeenInCar(ctx);
					a.showFragment(R.id.home_fragment);
				}, false));

		TextView skip = text(ctx, "Şimdi değil — telefonda kullan", 15, true);
		skip.setGravity(Gravity.CENTER);
		skip.setPadding(dp(16), dp(16), dp(16), dp(16));
		skip.setClickable(true);
		skip.setOnClickListener(v -> {
			MainActivityDelegate.skipSetupThisRun = true;
			a.showFragment(R.id.home_fragment);
		});
		LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
		slp.topMargin = dp(10);
		col.addView(skip, slp);
	}

	private void prepare(Context ctx, MainActivityDelegate a) {
		UiUtils.showInfo(ctx, "Araç modu hazırlanıyor… Uygulama birazdan yeniden başlayabilir.");
		YoldaInstaller.makeCarReady(ctx).main().onSuccess(err -> {
			if (err == null) UiUtils.showInfo(ctx, "Tamam! Yolda yeniden başlıyor.");
			else UiUtils.showAlert(ctx, err);
			if (isAdded()) render();
		});
	}

	// ---- parçalar ----

	private View step(Context ctx, String no, String title, String desc, @Nullable String btn,
										@Nullable View.OnClickListener click, boolean done) {
		LinearLayout card = new LinearLayout(ctx);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(20), dp(18), dp(20), dp(18));
		card.setBackground(YoldaStyle.glass(ctx, t, 26));
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		TextView badge = text(ctx, no, 18, true);
		badge.setGravity(Gravity.CENTER);
		badge.setBackground(YoldaStyle.selected(ctx, 999));
		head.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));
		TextView tt = text(ctx, title, 20, true);
		tt.setPadding(dp(14), 0, 0, 0);
		head.addView(tt);
		card.addView(head);
		TextView d = text(ctx, desc, 14, false);
		d.setTextColor(0xCCFFFFFF);
		d.setPadding(0, dp(10), 0, 0);
		card.addView(d);
		if (btn != null) {
			card.addView(bigButton(ctx, btn, click != null, click), topMargin(dp(14)));
		}
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = dp(16);
		card.setLayoutParams(lp);
		if (done) card.setAlpha(0.7f);
		return card;
	}

	@SuppressLint("ClickableViewAccessibility")
	private View bigButton(Context ctx, String label, boolean enabled, @Nullable View.OnClickListener click) {
		TextView b = text(ctx, label, 16, true);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(22), dp(14), dp(22), dp(14));
		b.setBackground(enabled ? YoldaStyle.selected(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
		b.setClickable(enabled);
		if (enabled && (click != null)) {
			b.setOnClickListener(click);
			b.setOnTouchListener((v, e) -> {
				int act = e.getActionMasked();
				if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
				else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
					v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
				return false;
			});
		}
		return b;
	}

	/**
	 * Android Auto'nun ayar ekranı: yeni telefonlarda Android Auto'nun simgesi yok; ayar ekranını
	 * paketin dışa açık etkinlikleri arasından bulur, bulamazsa uygulama bilgisine gider.
	 */
	private void openAndroidAuto(Context ctx) {
		String aa = "com.google.android.projection.gearhead";
		android.content.pm.PackageManager pm = ctx.getPackageManager();
		Intent i = pm.getLaunchIntentForPackage(aa);
		if (i == null) {
			Intent pref = new Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(aa);
			if (!pm.queryIntentActivities(pref, 0).isEmpty()) i = pref;
		}
		if (i == null) {
			try {
				android.content.pm.PackageInfo pi = pm.getPackageInfo(aa, android.content.pm.PackageManager.GET_ACTIVITIES);
				android.content.pm.ActivityInfo best = null;
				if (pi.activities != null) {
					for (android.content.pm.ActivityInfo ai : pi.activities) {
						if (!ai.exported) continue;
						String n = ai.name.toLowerCase(java.util.Locale.ROOT);
						if (n.contains("settings") && (best == null || n.contains("companion"))) best = ai;
					}
				}
				if (best != null) i = new Intent().setClassName(aa, best.name);
			} catch (Exception ignore) {
			}
		}
		if (i == null) i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + aa));
		try {
			ctx.getApplicationContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} catch (Exception ex) {
			start(ctx, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + aa)));
		}
	}

	private void openApp(Context ctx, String pkg) {
		Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
		if (i != null) start(ctx, i);
		else openUrl(ctx, "https://shizuku.rikka.app/");
	}

	private void openUrl(Context ctx, String url) {
		start(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
	}

	private void start(Context ctx, Intent i) {
		try {
			ctx.getApplicationContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		} catch (Exception ex) {
			Log.e(ex, "Açılamadı: ", i);
		}
	}

	private TextView text(Context ctx, String s, float size, boolean bold) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size * me.aap.fermata.ui.view.YoldaStyle.textScale(ctx)); // araçta küçük
		tv.setTextColor(0xFFFFFFFF);
		tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
		tv.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
		return tv;
	}

	private LinearLayout.LayoutParams topMargin(int px) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
		lp.topMargin = px;
		return lp;
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}
}
