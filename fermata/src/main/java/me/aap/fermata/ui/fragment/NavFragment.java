package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

/**
 * Yolda navigasyon: hedef ara, Ev/İş, yakındakiler (benzinlik, otopark…), son gidilenler.
 * Yol tarifi Google Haritalar ya da Waze'de başlar; Android Auto aracın navigasyon ekranında onu açar
 * (harita çizimini Android Auto yalnız kendi navigasyon uygulamalarına bırakıyor).
 */
public class NavFragment extends MainActivityFragment {

	private static final String PREFS = YoldaStyle.PREFS;
	private static final String MAPS = "com.google.android.apps.maps";
	private static final String WAZE = "com.waze";

	private YoldaStyle.Theme t;
	private FrameLayout root;

	@Override
	public int getFragmentId() {
		return R.id.yolda_nav_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Navigasyon";
	}

	@Override
	public me.aap.utils.ui.view.ToolBarView.Mediator getToolBarMediator() {
		return YoldaFullScreen.instance; // tam ekran: başlık çubuğu yok
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
	}

	private SharedPreferences prefs() {
		return requireContext().getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	private boolean waze() {
		return prefs().getBoolean("nav_waze", false);
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

		// Başlık + uygulama seçimi
		LinearLayout head = new LinearLayout(ctx);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = text(ctx, "Navigasyon", 40, true);
		head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
		head.addView(choice(ctx, "Google Haritalar", !waze(), v -> {
			prefs().edit().putBoolean("nav_waze", false).apply();
			render();
		}));
		LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
		wlp.leftMargin = dp(10);
		head.addView(choice(ctx, "Waze", waze(), v -> {
			prefs().edit().putBoolean("nav_waze", true).apply();
			render();
		}), wlp);
		col.addView(head);

		// Arama
		LinearLayout search = new LinearLayout(ctx);
		search.setOrientation(LinearLayout.HORIZONTAL);
		search.setGravity(Gravity.CENTER_VERTICAL);
		search.setPadding(dp(22), 0, dp(8), 0);
		search.setBackground(YoldaStyle.searchField(ctx, t));
		search.addView(icon(ctx, R.drawable.yi_search, 0xC7FFFFFF), sq(24));
		EditText input = a.createEditText(ctx);
		input.setSingleLine();
		input.setHint("Nereye gidelim?");
		input.setTextSize(19);
		input.setTextColor(0xFFFFFFFF);
		input.setHintTextColor(0xB3FFFFFF);
		input.setBackground(null);
		input.setImeOptions(EditorInfo.IME_ACTION_GO);
		input.setPadding(dp(14), dp(18), dp(14), dp(18));
		input.setOnEditorActionListener((v, actionId, ev) -> {
			boolean go = (actionId == EditorInfo.IME_ACTION_GO) ||
					((ev != null) && (ev.getKeyCode() == KeyEvent.KEYCODE_ENTER));
			if (go) navigate(ctx, input.getText().toString(), true);
			return go;
		});
		search.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
		TextView goBtn = text(ctx, "Yol tarifi", 16, true);
		goBtn.setGravity(Gravity.CENTER);
		goBtn.setPadding(dp(22), dp(14), dp(22), dp(14));
		goBtn.setBackground(YoldaStyle.selected(ctx, 999));
		goBtn.setClickable(true);
		goBtn.setOnClickListener(v -> navigate(ctx, input.getText().toString(), true));
		search.addView(goBtn);
		if (YoldaStyle.DECK) { // V2: arama altta, sesli düğmenin sağında; içerik üstünde kayar
			search.setElevation(dp(8));
			FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM);
			flp.setMargins(dp(YoldaStyle.searchLeftDp()), 0, dp(22), dp(14));
			root.addView(search, flp);
			col.setPadding(col.getPaddingLeft(), col.getPaddingTop(), col.getPaddingRight(), dp(100));
			goBtn.setPadding(dp(18), dp(10), dp(18), dp(10));
			input.setPadding(dp(12), dp(8), dp(12), dp(8));
		} else {
			LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(72));
			slp.topMargin = dp(22);
			col.addView(search, slp);
		}

		// Ev, İş ve yakındakiler
		LinearLayout quick = new LinearLayout(ctx);
		quick.setOrientation(LinearLayout.HORIZONTAL);
		quick.addView(place(ctx, R.drawable.yi_home_fill, "Ev", savedLabel("nav_home"), 0xFF60A5FA, 0xFF2563EB, "nav_home"));
		quick.addView(place(ctx, R.drawable.yi_work, "İş", savedLabel("nav_work"), 0xFFA78BFA, 0xFF7C3AED, "nav_work"));
		quick.addView(nearby(ctx, R.drawable.yi_fuel, "Benzinlik", "benzin istasyonu", 0xFFFBBF24, 0xFFD97706));
		quick.addView(nearby(ctx, R.drawable.yi_parking, "Otopark", "otopark", 0xFF38BDF8, 0xFF0284C7));
		quick.addView(nearby(ctx, R.drawable.yi_food, "Yemek", "restoran", 0xFFFB7185, 0xFFE11D48));
		quick.addView(nearby(ctx, R.drawable.yi_pharmacy, "Eczane", "nöbetçi eczane", 0xFF4ADE80, 0xFF16A34A));
		quick.addView(nearby(ctx, R.drawable.yi_ev, "Şarj", "elektrikli araç şarj istasyonu", 0xFF2DD4BF, 0xFF0D9488));
		quick.addView(nearby(ctx, R.drawable.yi_coffee, "Kafe", "kafe", 0xFFD6A77A, 0xFF92643B));
		HorizontalScrollView hs = new HorizontalScrollView(ctx);
		hs.setHorizontalScrollBarEnabled(false);
		hs.addView(quick);
		LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-1, -2);
		qlp.topMargin = dp(22);
		col.addView(hs, qlp);

		// Son gidilenler
		List<String> recent = recent();
		if (!recent.isEmpty()) {
			TextView rt = text(ctx, "Son gidilenler", 22, true);
			rt.setPadding(dp(4), dp(28), 0, dp(12));
			col.addView(rt);
			for (String r : recent) col.addView(recentRow(ctx, r));
		}

		TextView info = text(ctx, (waze() ? "Waze" : "Google Haritalar")
				+ " yol tarifini başlatır; araç ekranında navigasyon açılır. Müzik ve yayın Yolda'da devam eder.", 14, false);
		info.setTextColor(0xB3FFFFFF);
		info.setPadding(dp(4), dp(24), 0, 0);
		col.addView(info);
	}

	// ---- parçalar ----

	private View place(Context ctx, @DrawableRes int ic, String title, String sub, int c1, int c2, String key) {
		View card = card(ctx, ic, title, sub, c1, c2, v -> {
			String addr = prefs().getString(key, null);
			if (addr == null) setPlace(ctx, key, title);
			else navigate(ctx, addr, false);
		});
		card.setOnLongClickListener(v -> {
			setPlace(ctx, key, title);
			return true;
		});
		return card;
	}

	private void setPlace(Context ctx, String key, String title) {
		UiUtils.queryText(ctx, R.string.yolda_nav_dest, R.drawable.yi_nav).onSuccess(addr -> {
			if (addr == null || addr.trim().isEmpty()) return;
			prefs().edit().putString(key, addr.trim()).apply();
			UiUtils.showInfo(ctx, title + " kaydedildi: " + addr.trim());
			render();
		});
	}

	private String savedLabel(String key) {
		String s = prefs().getString(key, null);
		return (s == null) ? "Dokun: adres kaydet" : s;
	}

	private View nearby(Context ctx, @DrawableRes int ic, String title, String query, int c1, int c2) {
		return card(ctx, ic, title, "Yakındakiler", c1, c2, v -> showNearby(ctx, query));
	}

	@SuppressLint("ClickableViewAccessibility")
	private View card(Context ctx, @DrawableRes int ic, String title, String sub, int c1, int c2,
										View.OnClickListener click) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		c.setPadding(dp(14), dp(18), dp(14), dp(16));
		c.setBackground(YoldaStyle.glass(ctx, t, 30));
		c.setClickable(true);
		c.setOnClickListener(click);
		c.setOnTouchListener((v, e) -> {
			int act = e.getActionMasked();
			if (act == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)
				v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
			return false;
		});
		FrameLayout ib = new FrameLayout(ctx);
		ib.setBackground(YoldaStyle.appIcon(ctx, c1, c2, 22));
		ib.addView(icon(ctx, ic, 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
		c.addView(ib, sq(80));
		TextView tt = text(ctx, title, 17, true);
		tt.setGravity(Gravity.CENTER);
		tt.setPadding(0, dp(12), 0, 0);
		c.addView(tt, new LinearLayout.LayoutParams(-1, -2));
		TextView st = text(ctx, sub, 12, false);
		st.setTextColor(0xB3FFFFFF);
		st.setGravity(Gravity.CENTER);
		st.setMaxLines(1);
		st.setEllipsize(android.text.TextUtils.TruncateAt.END);
		c.addView(st, new LinearLayout.LayoutParams(-1, -2));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(176));
		lp.rightMargin = dp(14);
		c.setLayoutParams(lp);
		return c;
	}

	private View recentRow(Context ctx, String dest) {
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(dp(16), dp(10), dp(16), dp(10));
		r.setBackground(YoldaStyle.glass(ctx, t, 22));
		r.setClickable(true);
		r.setOnClickListener(v -> navigate(ctx, dest, true));
		FrameLayout ib = new FrameLayout(ctx);
		ib.setBackground(YoldaStyle.appIcon(ctx, 0xFF94A3B8, 0xFF475569, 14));
		ib.addView(icon(ctx, R.drawable.yi_history, 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
		r.addView(ib, sq(44));
		TextView tv = text(ctx, dest, 17, true);
		tv.setPadding(dp(16), 0, 0, 0);
		tv.setMaxLines(1);
		tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
		r.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
		r.addView(icon(ctx, R.drawable.yi_nav, 0xFFFFFFFF), sq(24));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
		lp.bottomMargin = dp(10);
		r.setLayoutParams(lp);
		return r;
	}

	private View choice(Context ctx, String label, boolean on, View.OnClickListener click) {
		TextView c = text(ctx, label, 15, true);
		c.setPadding(dp(18), dp(11), dp(18), dp(11));
		c.setBackground(on ? YoldaStyle.selected(ctx, 999) : YoldaStyle.glass(ctx, t, 999));
		c.setClickable(true);
		c.setOnClickListener(click);
		return c;
	}

	// ---- işler ----

	/** Yol tarifini başlat. */
	private void navigate(Context ctx, String dest, boolean remember) {
		String d = (dest == null) ? "" : dest.trim();
		if (d.isEmpty()) {
			UiUtils.showInfo(ctx, "Önce nereye gideceğini yaz");
			return;
		}
		if (remember) remember(d);
		Intent i = waze()
				? new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + Uri.encode(d) + "&navigate=yes")).setPackage(WAZE)
				: new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d))).setPackage(MAPS);
		if (!start(ctx, i)) {
			// Seçili uygulama yoksa telefondaki herhangi bir harita uygulaması
			start(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(d))));
		}
	}

	/** Yakındakileri haritada göster (en yakını seçmek kullanıcıda). */
	private void showNearby(Context ctx, String query) {
		Intent i = waze()
				? new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + Uri.encode(query))).setPackage(WAZE)
				: new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))).setPackage(MAPS);
		if (!start(ctx, i)) start(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))));
	}

	private boolean start(Context ctx, Intent i) {
		return launch(ctx, i);
	}

	/**
	 * Araçtaysa yol tarifini Android Auto'nun kendi harita uygulamasında açar (telefonda açmaya çalışmak
	 * araçta bir şey göstermiyordu: uygulama telefonda arka planda, Android engelliyor). Telefonda olağan yol.
	 */
	static boolean launch(Context ctx, Intent i) {
		try {
			MainActivityDelegate a = MainActivityDelegate.get(ctx);
			if (a.isCarActivityNotMirror()) {
				Object act = a.getAppActivity();
				java.lang.reflect.Method m = act.getClass().getMethod("startCarActivity", Intent.class);
				try {
					m.invoke(act, new Intent(i));
				} catch (Exception first) { // paket belirtilmeden (araçtaki varsayılan harita)
					m.invoke(act, new Intent(i).setPackage(null));
				}
				YoldaLog.add("Navigasyon araçta açıldı: " + i.getData());
				return true;
			}
		} catch (Throwable ex) {
			YoldaLog.add("Navigasyon araçta açılamadı: " + ex);
		}
		try {
			ctx.getApplicationContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			return true;
		} catch (Exception ex) {
			Log.d("Navigasyon açılamadı: ", i);
			return false;
		}
	}

	/** Son gidilen yerler (en yenisi başta). */
	public static List<String> recent(Context ctx) {
		List<String> l = new ArrayList<>();
		try {
			JSONArray arr = new JSONArray(ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.getString("nav_recent", "[]"));
			for (int i = 0; i < arr.length(); i++) l.add(arr.getString(i));
		} catch (Exception ignore) {
		}
		return l;
	}

	private List<String> recent() {
		List<String> l = new ArrayList<>();
		try {
			JSONArray a = new JSONArray(prefs().getString("nav_recent", "[]"));
			for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
		} catch (Exception ignore) {
		}
		return l;
	}

	private void remember(String d) {
		List<String> l = recent();
		l.remove(d);
		l.add(0, d);
		JSONArray a = new JSONArray();
		for (int i = 0; i < l.size() && i < 6; i++) a.put(l.get(i));
		prefs().edit().putString("nav_recent", a.toString()).apply();
	}

	// ---- sesli komut: "eve git", "işe git", "Taksim'e git", "Ankara'ya yol tarifi" ----

	private static final java.util.regex.Pattern V_HOME = java.util.regex.Pattern.compile("^(eve|evime) (git|götür|gidelim|yol tarifi)$");
	private static final java.util.regex.Pattern V_WORK = java.util.regex.Pattern.compile("^(işe|işyerine|iş yerine|ofise) (git|götür|gidelim|yol tarifi)$");
	private static final java.util.regex.Pattern V_DEST = java.util.regex.Pattern.compile(
			"^(.+?)(?:['’]?(?:ye|ya|e|a|ne|na))? (git|götür|gidelim|yol tarifi( ver)?|navigasyon( başlat)?)$");

	/** @return true komut navigasyon komutuysa (yol tarifi başlatıldı ya da adres istendi). */
	public static boolean handleVoice(MainActivityDelegate a, String cmd) {
		if (cmd == null) return false;
		String c = cmd.trim().toLowerCase(new java.util.Locale("tr"));
		Context ctx = a.getContext();
		SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String dest;
		if (V_HOME.matcher(c).matches()) dest = p.getString("nav_home", null);
		else if (V_WORK.matcher(c).matches()) dest = p.getString("nav_work", null);
		else {
			java.util.regex.Matcher m = V_DEST.matcher(c);
			if (!m.matches()) return false;
			dest = m.group(1).replaceAll("['’]$", "").trim();
		}
		if ((dest == null) || dest.isEmpty()) {
			a.showFragment(R.id.yolda_nav_fragment);
			UiUtils.showInfo(ctx, "Önce Navigasyon ekranında Ev/İş adresini kaydet");
			return true;
		}
		navigateTo(ctx, dest, true);
		return true;
	}

	/** Yol tarifini başlat (ekrandan ve sesli komuttan). */
	public static void navigateTo(Context ctx, String dest, boolean remember) {
		SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String d = dest.trim();
		if (remember) {
			List<String> l = new ArrayList<>();
			try {
				JSONArray arr = new JSONArray(p.getString("nav_recent", "[]"));
				for (int i = 0; i < arr.length(); i++) l.add(arr.getString(i));
			} catch (Exception ignore) {
			}
			l.remove(d);
			l.add(0, d);
			JSONArray arr = new JSONArray();
			for (int i = 0; i < l.size() && i < 6; i++) arr.put(l.get(i));
			p.edit().putString("nav_recent", arr.toString()).apply();
		}
		boolean waze = p.getBoolean("nav_waze", false);
		Intent i = waze
				? new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + Uri.encode(d) + "&navigate=yes")).setPackage(WAZE)
				: new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d))).setPackage(MAPS);
		if (!startStatic(ctx, i)) startStatic(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(d))));
	}

	private static boolean startStatic(Context ctx, Intent i) {
		return launch(ctx, i);
	}

	// ---- yardımcılar ----

	private ImageView icon(Context ctx, @DrawableRes int res, int tint) {
		ImageView iv = new ImageView(ctx);
		iv.setImageResource(res);
		iv.setImageTintList(ColorStateList.valueOf(tint));
		return iv;
	}

	private TextView text(Context ctx, String s, float size, boolean bold) {
		TextView tv = new TextView(ctx);
		tv.setText(s);
		tv.setTextSize(size * me.aap.fermata.ui.view.YoldaStyle.textScale(ctx)); // araçta küçük
		tv.setTextColor(0xFFFFFFFF);
		tv.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
		if (bold && size >= 28) tv.setLetterSpacing(-0.02f);
		tv.setShadowLayer(dp(6), 0, dp(1), 0x40000000);
		return tv;
	}

	private LinearLayout.LayoutParams sq(int v) {
		return new LinearLayout.LayoutParams(dp(v), dp(v));
	}

	private int dp(int v) {
		return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
	}
}
