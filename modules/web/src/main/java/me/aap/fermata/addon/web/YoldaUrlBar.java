package me.aap.fermata.addon.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Yolda V2 tarayıcı kapsülü: sayfa tam ekran, kapsül sayfanın üstünde (sesli düğmenin yanında) yüzer.
 * Normalde kilit + sitenin adı ve yenile düğmesi; dokununca adres / arama yazılır (adres değilse Google'da
 * aranır). Sayfa yüklenirken alt kenarda ilerleme çizgisi akar. Aşağı kaydırınca gizlenir, yukarı
 * kaydırınca geri gelir. Sıvı cam görünüm (YoldaStyle.liquid).
 */
@SuppressLint("ViewConstructor")
public final class YoldaUrlBar extends FrameLayout {
	private final WebView web;
	private final Consumer<String> load;
	private final TextView host;
	private final ImageView lock;
	private final EditText edit;
	private final LinearLayout idle;
	private final View progress;
	private final Runnable ticker = this::tick;
	private boolean hidden;
	private String lastUrl;

	public static YoldaUrlBar attach(FrameLayout root, WebView web, Consumer<String> load) {
		YoldaUrlBar b = new YoldaUrlBar(root.getContext(), web, load);
		float d = root.getResources().getDisplayMetrics().density;
		LayoutParams lp = new LayoutParams(-1, Math.round(YoldaStyle.VOICE_DP * d), Gravity.BOTTOM);
		lp.setMargins(Math.round(YoldaStyle.searchLeft(root.getContext()) * d), 0, Math.round(22 * d),
				Math.round(YoldaStyle.VOICE_BOTTOM_DP * d));
		root.addView(b, lp);
		return b;
	}

	private YoldaUrlBar(Context ctx, WebView web, Consumer<String> load) {
		super(ctx);
		this.web = web;
		this.load = load;
		YoldaStyle.Theme t = YoldaStyle.current(ctx);
		setBackground(YoldaStyle.liquid(ctx, t));
		setElevation(dp(10));
		setClipToOutline(true);
		setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getHeight() / 2f);
			}
		});

		// Normal görünüm: kilit · site adı · yenile
		idle = new LinearLayout(ctx);
		idle.setOrientation(LinearLayout.HORIZONTAL);
		idle.setGravity(Gravity.CENTER_VERTICAL);
		idle.setPadding(dp(18), 0, dp(6), 0);
		idle.setClickable(true);
		idle.setOnClickListener(v -> startEdit());
		lock = new ImageView(ctx);
		lock.setImageResource(me.aap.fermata.R.drawable.yi_globe);
		lock.setImageTintList(ColorStateList.valueOf(0xCCFFFFFF));
		idle.addView(lock, new LinearLayout.LayoutParams(dp(20), dp(20)));
		host = new TextView(ctx);
		host.setTextColor(0xFFFFFFFF);
		host.setTextSize(16 * YoldaStyle.textScale(ctx));
		host.setTypeface(Typeface.DEFAULT_BOLD);
		host.setSingleLine(true);
		host.setEllipsize(TextUtils.TruncateAt.END);
		host.setPadding(dp(12), 0, dp(8), 0);
		host.setText("Ara ya da adres yaz");
		idle.addView(host, new LinearLayout.LayoutParams(0, -2, 1f));
		ImageView reload = new ImageView(ctx);
		reload.setImageResource(me.aap.fermata.R.drawable.yi_history);
		reload.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		reload.setPadding(dp(12), dp(12), dp(12), dp(12));
		reload.setContentDescription("Yenile");
		GradientDrawable rb = new GradientDrawable();
		rb.setShape(GradientDrawable.OVAL);
		rb.setColor(0x26FFFFFF);
		reload.setBackground(rb);
		reload.setOnClickListener(v -> web.reload());
		idle.addView(reload, new LinearLayout.LayoutParams(dp(44), dp(44)));
		addView(idle, new LayoutParams(-1, -1));

		// Yazma görünümü
		edit = MainActivityDelegate.get(ctx).createEditText(ctx);
		edit.setSingleLine();
		edit.setHint("Ara ya da adres yaz");
		edit.setTextColor(0xFFFFFFFF);
		edit.setHintTextColor(0x99FFFFFF);
		edit.setTextSize(16 * YoldaStyle.textScale(ctx));
		edit.setBackground(null);
		edit.setPadding(dp(20), 0, dp(20), 0);
		edit.setGravity(Gravity.CENTER_VERTICAL);
		edit.setImeOptions(EditorInfo.IME_ACTION_GO);
		edit.setSelectAllOnFocus(true);
		edit.setVisibility(GONE);
		edit.setOnEditorActionListener((v, id, ev) -> {
			go(edit.getText().toString());
			return true;
		});
		edit.setOnFocusChangeListener((v, has) -> {
			if (!has) endEdit();
		});
		addView(edit, new LayoutParams(-1, -1));

		// Yükleme çizgisi (alt kenarda)
		progress = new View(ctx);
		GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
				new int[]{0x00FFFFFF, 0xFFFFFFFF});
		pg.setCornerRadius(dp(2));
		progress.setBackground(pg);
		progress.setPivotX(0);
		progress.setVisibility(INVISIBLE);
		LayoutParams plp = new LayoutParams(-1, dp(3), Gravity.BOTTOM);
		plp.setMargins(dp(24), 0, dp(24), dp(3));
		addView(progress, plp);

		// Aşağı kaydırınca gizlen, yukarı kaydırınca gel
		web.setOnScrollChangeListener((v, x, y, ox, oy) -> {
			if (edit.getVisibility() == VISIBLE) return;
			if (y > oy + 6) setHidden(true);
			else if ((y < oy - 6) || (y <= 0)) setHidden(false);
		});
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		post(ticker);
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(ticker);
		super.onDetachedFromWindow();
	}

	/** Site adı ve yükleme çizgisi (yalnız değişince çizilir). */
	private void tick() {
		removeCallbacks(ticker);
		if (!isAttachedToWindow()) return;
		// YouTube Music tam sayfası: kapsül yok (kendi düzeni var)
		int vis = YoldaStyle.webNoToolbar ? GONE : VISIBLE;
		if (getVisibility() != vis) setVisibility(vis);
		String url = web.getUrl();
		if ((url != null) && !url.equals(lastUrl)) {
			lastUrl = url;
			Uri u = Uri.parse(url);
			String h = u.getHost();
			if (h != null && h.startsWith("www.")) h = h.substring(4);
			host.setText((h != null) ? h : url);
			setHidden(false);
		}
		int p = web.getProgress();
		boolean loading = (p > 0) && (p < 100);
		progress.setVisibility(loading ? VISIBLE : INVISIBLE);
		if (loading) progress.setScaleX(p / 100f);
		postDelayed(ticker, loading ? 150 : 600);
	}

	private void setHidden(boolean h) {
		if (hidden == h) return;
		hidden = h;
		animate().translationY(h ? getHeight() + dp(40) : 0).alpha(h ? 0f : 1f).setDuration(220).start();
	}

	private void startEdit() {
		idle.setVisibility(GONE);
		edit.setVisibility(VISIBLE);
		String u = web.getUrl();
		edit.setText((u != null) ? u : "");
		edit.requestFocus();
		edit.selectAll();
		InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (im != null) im.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
	}

	private void endEdit() {
		edit.setVisibility(GONE);
		idle.setVisibility(VISIBLE);
		InputMethodManager im = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (im != null) im.hideSoftInputFromWindow(edit.getWindowToken(), 0);
	}

	/** Adres gibi görünüyorsa aç, değilse Google'da ara. */
	private void go(String s) {
		s = s.trim();
		endEdit();
		if (s.isEmpty()) return;
		String url;
		if (s.contains("://")) url = s;
		else if (!s.contains(" ") && s.contains(".")) url = "https://" + s;
		else url = "https://www.google.com/search?q=" + Uri.encode(s);
		load.accept(url);
	}

	private int dp(int v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}
}
