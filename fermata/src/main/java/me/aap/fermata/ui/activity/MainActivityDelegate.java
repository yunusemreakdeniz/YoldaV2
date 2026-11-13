package me.aap.fermata.ui.activity;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.util.Base64.URL_SAFE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.fermata.action.KeyEventHandler.handleKeyEvent;
import static me.aap.fermata.ui.activity.MainActivityPrefs.BRIGHTNESS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CHANGE_BRIGHTNESS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CLOCK_POS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.action.Action;
import me.aap.fermata.action.Key;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataActivityAddon;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.AtvInterface;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExportedItem;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.IntentPlayable;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.SearchFolder;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.service.MediaSessionCallbackAssistant;
import me.aap.fermata.ui.fragment.AudioEffectsFragment;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.NavBarMediator;
import me.aap.fermata.ui.fragment.PlaylistsFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.fragment.SubtitlesFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends ActivityDelegate
		implements MediaSessionCallbackAssistant, PreferenceStore.Listener {
	public static final String INTENT_ACTION_OPEN = "open";
	public static final String INTENT_ACTION_PLAY = "play";
	public static final String INTENT_ACTION_UPDATE = "update";
	public static final String INTENT_ACTION_FINISH = "finish";
	private static final String INTENT_SCHEME = "fermata";
	private final HandlerExecutor handler = new HandlerExecutor(App.get().getHandler().getLooper());
	private final NavBarMediator navBarMediator = new NavBarMediator();
	private final FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private BodyLayout body;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private ContentLoadingProgressBar progressBar;
	private FutureSupplier<?> contentLoading;
	private boolean barsHidden;
	private boolean videoMode;
	private int brightness = 255;
	private SpeechListener speechListener;
	private VoiceCommandHandler voiceCommandHandler;

	public MainActivityDelegate(AppActivity activity, FermataServiceUiBinder binder) {
		super(activity);
		mediaServiceBinder = binder;
	}

	@NonNull
	public static MainActivityDelegate get(Context ctx) {
		return (MainActivityDelegate) ActivityDelegate.get(ctx);
	}

	@NonNull
	@SuppressWarnings("unchecked")
	public static FutureSupplier<MainActivityDelegate> getActivityDelegate(Context ctx) {
		return (FutureSupplier<MainActivityDelegate>) ActivityDelegate.getActivityDelegate(ctx);
	}

	public static Context attachBaseContext(Context ctx) {
		MainActivityPrefs prefs = Prefs.instance;
		if (!prefs.hasPref(LOCALE)) return ctx;

		var cfg = ctx.getResources().getConfiguration();
		var loc = prefs.getLocalePref();
		cfg.setLocale(loc);
		Locale.setDefault(loc);
		return ctx.createConfigurationContext(cfg);
	}

	public static Uri toIntentUri(String action, String itemId) {
		String id = Base64.encodeToString(itemId.getBytes(US_ASCII), URL_SAFE);
		return new Uri.Builder().scheme(INTENT_SCHEME).authority(action).path(id).build();
	}

	@Nullable
	public static String intentUriToId(Uri u) {
		if ((u == null) || !INTENT_SCHEME.equals(u.getScheme())) return null;
		String id = u.getPath();
		return (id == null) ? null : new String(Base64.decode(id.substring(1), URL_SAFE), US_ASCII);
	}

	@Nullable
	public static String intentUriToAction(Uri u) {
		return (u != null) && INTENT_SCHEME.equals(u.getScheme()) ? u.getHost() : null;
	}

	@Override
	public void onActivityCreate(@Nullable Bundle state) {
		super.onActivityCreate(state);
		Intent intent = getIntent();
		if ((intent != null) && INTENT_ACTION_FINISH.equals(intent.getAction())) {
			finish();
			return;
		}

		getPrefs().addBroadcastListener(this);
		int navId;
		int fragmentId;

		if (state != null) {
			navId = state.getInt("navId", ID_NULL);
			fragmentId = state.getInt("fragmentId", ID_NULL);
		} else {
			navId = ID_NULL;
			fragmentId = ID_NULL;
		}

		AppActivity a = getAppActivity();
		FermataServiceUiBinder b = getMediaServiceBinder();
		Context ctx = a.getContext();
		b.getMediaSessionCallback().getSession().setSessionActivity(
				PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()), FLAG_IMMUTABLE));
		b.getMediaSessionCallback().addAssistant(this, isCarActivityNotMirror() ? 0 : 1);
		if (b.getCurrentItem() == null) b.getMediaSessionCallback().onPrepare();
		init();

		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityCreate(this);
		}

		String[] perms = getRequiredPermissions();
		a.checkPermissions(perms).onCompletion((result, fail) -> {
			if (fail != null) {
				if (!isCarActivityNotMirror()) Log.e(fail);
			} else {
				Log.d("Requested permissions: ", Arrays.toString(perms),
						". Result: " + Arrays.toString(result));
			}

			if (fragmentId != ID_NULL) {
				setActiveNavItemId(navId);
				showFragment(fragmentId);
				return;
			}

			if ((intent != null) && !Intent.ACTION_MAIN.equals(intent.getAction())) {
				handleIntent(intent).onCompletion((r, err) -> {
					if (err != null) Log.e(err, "Failed to handle intent ", intent);
					if ((r == null) || !r) defaultIntent();
				});
			} else {
				defaultIntent();
			}
		});
	}

	@Override
	protected void onActivityNewIntent(Intent intent) {
		super.onActivityNewIntent(intent);
		handleIntent(intent);
	}

	private FutureSupplier<Boolean> handleIntent(Intent intent) {
		if (INTENT_ACTION_FINISH.equals(intent.getAction())) {
			finish();
			return completed(true);
		}

		for (FermataAddon a : AddonManager.get().getAddons()) {
			if (a.handleIntent(this, intent)) return completed(true);
		}

		Uri u = intent.getData();

		if (u != null) {
			if (INTENT_SCHEME.equals(u.getScheme())) {
				String action = u.getHost();
				if (action == null) return completed(false);
				String id = u.getPath();
				if (id == null) return completed(false);
				id = new String(Base64.decode(id.substring(1), URL_SAFE), US_ASCII);

				if (INTENT_ACTION_OPEN.equals(action)) {
					goToItem(id).map(MiscUtils::nonNull);
					return completed(true);
				} else if (INTENT_ACTION_PLAY.equals(action)) {
					goToItem(id).map(i -> {
						if (!(i instanceof PlayableItem)) return false;
						getMediaServiceBinder().playItem((PlayableItem) i);
						return true;
					});
					return completed(true);
				}
			} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				PlayableItem i = new IntentPlayable(this, u);
				getMediaServiceBinder().stop();
				post(() -> {
					if (!(getActiveFragment() instanceof MediaLibFragment))
						goToCurrent().onSuccess(v -> getMediaServiceBinder().playItem(i));
					else getMediaServiceBinder().playItem(i);
				});
			}
		}

		return completed(false);
	}

	private void defaultIntent() {
		if (getActiveFragment() != null) {
			checkUpdates();
			return;
		}

		String showAddon = getPrefs().getShowAddonOnStartPref();
		if (showAddon != null) {
			FermataAddon addon = AddonManager.get().getAddon(showAddon);

			if (addon instanceof FermataFragmentAddon) {
				showFragment(((FermataFragmentAddon) addon).getFragmentId());
				checkUpdates();
				return;
			}
		}

		// Yolda: telefonda araç modu hazır değilse önce kurulum ekranı; araçta ve hazırsa ana ekran
		// Yolda araç ekranında bir kez açıldıysa Android Auto tarafı tamam demektir
		if (isCarActivityNotMirror()) me.aap.fermata.ui.fragment.YoldaInstaller.markSeenInCar(getContext());
		if (!isCarActivity() && !skipSetupThisRun
				&& (!me.aap.fermata.ui.fragment.YoldaInstaller.isCarReady(getContext())
				|| !me.aap.fermata.ui.fragment.YoldaInstaller.seenInCar(getContext()))) {
			showFragment(R.id.yolda_setup_fragment);
		} else {
			// İlk açılışta alt menüde Ana sayfa etkin görünsün: menü düğmeleri sayfadan sonra
			// kurulduğunda da seçili gelmesi için etkin öğe önceden ayarlanır, kurulunca bir daha işaretlenir.
			if (getActiveNavItemId() == View.NO_ID || getActiveNavItemId() == 0)
				setActiveNavItemId(R.id.home_fragment);
			showFragment(R.id.home_fragment);
			App.get().getHandler().postDelayed(this::selectHomeNavButton, 300);
		}
		checkUpdates();
	}

	/**
	 * Oynatıcı: simgeler beyaz, oynat/duraklat ortada parlayan cam daire içinde; dokunma alanları büyük.
	 */
	private void styleYoldaPlayer() {
		ControlPanelView cp = getControlPanel();
		if (cp == null) return;
		Context ctx = getContext();
		float d = ctx.getResources().getDisplayMetrics().density;
		int[] ids = {R.id.control_prev, R.id.control_rw, R.id.control_play_pause, R.id.control_ff,
				R.id.control_next, R.id.show_hide_bars_icon, R.id.control_menu_button_icon, R.id.control_close_icon};
		for (int id : ids) {
			View v = cp.findViewById(id);
			if (v instanceof android.widget.ImageView iv) {
				iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
				iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
			}
		}
		// Yan düğmeler: simge yüksekliğin ~%55'i, ortada; oynat düğmesi ortada tam daire içinde
		for (int id : new int[]{R.id.control_prev, R.id.control_rw, R.id.control_ff, R.id.control_next,
				R.id.control_play_pause}) {
			View v = cp.findViewById(id);
			if (v == null) continue;
			boolean isPlay = (id == R.id.control_play_pause);
			v.setBackground(null);
			v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
				int h = b - t;
				if ((h <= 0) || (h == ob - ot)) return; // yalnız yükseklik değişince (ilk açılış, döndürme)
				int pv = Math.round(h * 0.22f); // hepsi aynı: oynat simgesinin çizimi ortalı değil, büyütünce kayıyordu
				view.setPadding(0, pv, 0, pv);
			});
		}
		int ip = Math.round(4 * d);
		View close = cp.findViewById(R.id.control_close_icon);
		if (close != null) close.setPadding(ip, ip, ip, ip);
		for (int id : new int[]{R.id.seek_time, R.id.seek_total}) {
			View v = cp.findViewById(id);
			if (v instanceof android.widget.TextView tv) tv.setTextColor(0xE6FFFFFF);
		}
		cp.useYoldaBar(); // Apple Music / Spotify tarzı tek satır oynatıcı
		int p = Math.round(4 * d);
		int oldV = cp.getPaddingTop() + cp.getPaddingBottom();
		cp.setPadding(Math.round(10 * d), p, Math.round(10 * d), p);
		android.view.ViewGroup.LayoutParams clp = cp.getLayoutParams();
		if ((clp != null) && (clp.height > 0)) {
			clp.height += 2 * p - oldV; // oynat dairesi alttan kesilmesin
			cp.setLayoutParams(clp);
		}
		// Yüzen geri düğmesi: soluk halka yerine cam daire, beyaz ok
		if (floatingButton != null) {
			floatingButton.setBorderWidth(0);
			floatingButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x40FFFFFF));
			floatingButton.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
			floatingButton.setAlpha(0.95f);
		}
	}

	/** Görünümün kısa kenarı kadar, ortalanmış daire (geniş düğmede yayvan hap olmasın). */
	private static final class CircleDrawable extends android.graphics.drawable.Drawable {
		private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private final android.graphics.Paint edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

		CircleDrawable(int fillColor, int edgeColor, int edgeWidth) {
			fill.setColor(fillColor);
			edge.setColor(edgeColor);
			edge.setStyle(android.graphics.Paint.Style.STROKE);
			edge.setStrokeWidth(edgeWidth);
		}

		@Override
		public void draw(@NonNull android.graphics.Canvas c) {
			android.graphics.Rect r = getBounds();
			float rad = Math.min(r.width(), r.height()) / 2f - edge.getStrokeWidth() - 2;
			if (rad <= 0) return;
			c.drawCircle(r.exactCenterX(), r.exactCenterY(), rad, fill);
			c.drawCircle(r.exactCenterX(), r.exactCenterY(), rad, edge);
		}

		@Override
		public void setAlpha(int a) {
			fill.setAlpha(a);
		}

		@Override
		public void setColorFilter(@Nullable android.graphics.ColorFilter cf) {
			fill.setColorFilter(cf);
		}

		@Override
		public int getOpacity() {
			return android.graphics.PixelFormat.TRANSLUCENT;
		}
	}

	private void selectHomeNavButton() {
		if (getActiveFragmentId() != R.id.home_fragment) return;
		NavBarView nb = getNavBar();
		if (nb == null) return;
		for (int i = 0; i < nb.getChildCount(); i++) {
			View c = nb.getChildAt(i);
			c.setSelected(c.getId() == R.id.home_fragment);
		}
		setActiveNavItemId(R.id.home_fragment);
	}

	/** Kurulum ekranı "Şimdi değil" ile atlandıysa bu açılışta bir daha gösterme. */
	public static boolean skipSetupThisRun;

	private void checkUpdates() {
		if (!AUTO) return;
		if (getAppActivity() instanceof MainActivity a) a.uninstallControl().thenRun(() -> {
			if (getPrefs().getCheckUpdatesPref()) a.checkUpdates();
		});
	}

	@Override
	protected void setUncaughtExceptionHandler() {
		if (!AUTO || getAppActivity().isCarActivity()) return;
		super.setUncaughtExceptionHandler();
	}

	@Override
	protected void onActivitySaveInstanceState(@NonNull Bundle outState) {
		super.onActivitySaveInstanceState(outState);
		if (isRecreating()) {
			outState.putInt("navId", getActiveNavItemId());
			outState.putInt("fragmentId", getActiveFragmentId());
		}
	}

	public void recreate() {
		if (AUTO && isCarActivityNotMirror()) showAlert(getContext(), R.string.please_restart_app);
		else getHandler().post(super::recreate);
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
		checkMirroringMode(true);
		me.aap.fermata.ui.fragment.YoldaWake.start(this); // "Hey Yolda" dinlemeye başla (açıksa)
		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityResume(this);
		}
	}

	@Override
	protected void onActivityPause() {
		super.onActivityPause();
		me.aap.fermata.ui.fragment.YoldaWake.pause(this); // yalnız bu ekran dinliyorsa durur
		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityPause(this);
		}
	}

	@Override
	public void onActivityDestroy() {
		super.onActivityDestroy();
		themeTickOn = false;
		App.get().getHandler().removeCallbacks(autoThemeTick);
		if (themeListener != null) {
			getContext().getApplicationContext().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS,
					Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(themeListener);
			themeListener = null;
		}
		if (memCallbacks != null) {
			try {
				getContext().unregisterComponentCallbacks(memCallbacks);
			} catch (Throwable ignore) {
			}
			memCallbacks = null;
		}
		handler.close();
		getMediaServiceBinder().getMediaSessionCallback().removeAssistant(this);
		getPrefs().removeBroadcastListener(this);
		if (speechListener != null) speechListener.destroy();

		for (FermataAddon addon : AddonManager.get().getAddons()) {
			if (addon instanceof FermataActivityAddon)
				((FermataActivityAddon) addon).onActivityDestroy(this);
		}

		if (me.aap.utils.BuildConfig.D) {
			boolean leaks = ListenerLeakDetector.hasLeaks((b, l) -> {
				if (l instanceof ExportedItem.ListenerWrapper)
					l = ((ExportedItem.ListenerWrapper) l).getListener();
				if (l instanceof Key.PrefsListener) return false;
				if (l instanceof FermataAddon) return false;
				if (l instanceof AtvInterface) return false;
				if ((l instanceof DefaultMediaLib) && (b instanceof DefaultMediaLib)) return false;
				if ((l instanceof MediaEngineManager) && (b instanceof DefaultMediaLib)) return false;
				return (!(l instanceof AddonManager)) ||
						(b != FermataApplication.get().getPreferenceStore());
			});
			if (leaks) Log.e(new IllegalStateException("Listener leaks detected!"));
		}
	}

	public void onActivityFinish() {
		super.onActivityFinish();
	}

	@NonNull
	@Override
	public FermataActivity getAppActivity() {
		return (FermataActivity) super.getAppActivity();
	}

	public boolean isCarActivity() {
		return AUTO && getAppActivity().isCarActivity() || FermataApplication.get().isMirroringMode();
	}

	public boolean isCarActivityNotMirror() {
		return isCarActivity();
	}

	@NonNull
	public MainActivityPrefs getPrefs() {
		return Prefs.instance;
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return getMediaServiceBinder().getMediaSessionCallback().getPlaybackControlPrefs();
	}

	public static void setTheme(Context ctx, boolean auto) {
		@StyleRes int theme = switch (Prefs.instance.getThemePref(auto)) {
			case MainActivityPrefs.THEME_LIGHT -> R.style.AppTheme_Light;
			case MainActivityPrefs.THEME_SYSTEM -> {
				if ((ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
						Configuration.UI_MODE_NIGHT_YES)
					yield R.style.AppTheme_Dark;
				else yield R.style.AppTheme_Light;
			}
			case MainActivityPrefs.THEME_BLACK -> R.style.AppTheme_Black;
			case MainActivityPrefs.THEME_STAR_WARS -> R.style.AppTheme_BlackStarWars;
			case MainActivityPrefs.THEME_PURPLE -> R.style.AppTheme_Purple;
			case MainActivityPrefs.THEME_CLASSIC -> R.style.AppTheme_Classic;
			case MainActivityPrefs.THEME_YOLDA -> R.style.AppTheme_YoldaGlass;
			default -> R.style.AppTheme_Dark;
		};
		me.aap.fermata.ui.view.YoldaStyle.glassActive = (theme == R.style.AppTheme_YoldaGlass);
		ctx.setTheme(theme);
		if ((VERSION.SDK_INT >= VERSION_CODES.S) && (ctx instanceof Activity a)) {
			a.getSplashScreen().setSplashScreenTheme(theme);
		}
	}

	@Override
	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if (AUTO && (e.getAction() == MotionEvent.ACTION_DOWN)) {
			FermataActivity a = getAppActivity();

			if (a.isInputActive()) {
				a.stopInput();
				return true;
			}
		}

		return super.interceptTouchEvent(e, view);
	}

	@Override
	public boolean isFullScreen() {
		if (videoMode || getPrefs().getFullscreenPref(this)) {
			if (isCarActivityNotMirror()) {
				FermataServiceUiBinder b = getMediaServiceBinder();
				return !b.getMediaSessionCallback().getPlaybackControlPrefs().getVideoAaShowStatusPref();
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	public boolean isGridView() {
		ActivityFragment f = getActiveFragment();

		if ((f instanceof MediaLibFragment) && ((MediaLibFragment) f).isGridSupported()) {
			return getPrefs().getGridViewPref(this);
		} else {
			return false;
		}
	}

	@NonNull
	public FermataServiceUiBinder getMediaServiceBinder() {
		return mediaServiceBinder;
	}

	public MediaSessionCallback getMediaSessionCallback() {
		return getMediaServiceBinder().getMediaSessionCallback();
	}

	@NonNull
	public MediaLib getLib() {
		return getMediaServiceBinder().getLib();
	}

	@Nullable
	public PlayableItem getCurrentPlayable() {
		return getMediaServiceBinder().getCurrentItem();
	}

	public ToolBarView getToolBar() {
		return toolBar;
	}

	@Override
	public float getToolBarSize() {
		return getPrefs().getToolBarSizePref(this);
	}

	public NavBarView getNavBar() {
		return navBar;
	}

	@Override
	public float getNavBarSize() {
		return getPrefs().getNavBarSizePref(this);
	}

	public BodyLayout getBody() {
		return body;
	}

	public NavBarMediator getNavBarMediator() {
		return navBarMediator;
	}


	public ControlPanelView getControlPanel() {
		return controlPanel;
	}

	public FloatingButton getFloatingButton() {
		return floatingButton;
	}

	@Override
	public float getTextIconSize() {
		return getPrefs().getTextIconSizePref(this);
	}

	public boolean isBarsHidden() {
		return barsHidden;
	}

	public void setBarsHidden(boolean barsHidden) {
		App.get().getHandler().post(() -> {
			this.barsHidden = barsHidden;
			int visibility = barsHidden ? GONE : VISIBLE;
			ToolBarView tb = getToolBar();
			if (tb.getMediator() != ToolBarView.Mediator.Invisible.instance) tb.setVisibility(visibility);
			getNavBar().setVisibility(me.aap.fermata.ui.view.YoldaStyle.DECK ? GONE : visibility);
		});
	}

	public void setVideoMode(boolean videoMode, @Nullable VideoView v) {
		if (videoMode == this.videoMode) return;
		ControlPanelView cp = getControlPanel();

		if (videoMode) {
			this.videoMode = true;
			setSystemUiVisibility();
			keepScreenOn(true);
			cp.enableVideoMode(v);
		} else {
			this.videoMode = false;
			setSystemUiVisibility();
			keepScreenOn(false);
			if (cp != null) cp.disableVideoMode();
		}
		showYoldaBack(videoMode);
		updateBodyTopPadding(); // tam ekran videoda soldaki pay kalkar
		if (cp != null) cp.yoldaRefreshVisibility();

		if (!checkMirroringMode(false)) {
			MainActivityPrefs p = getPrefs();

			if (p.getChangeBrightnessPref()) {
				if (videoMode) {
					brightness = getBrightness();
					setBrightness(p.getBrightnessPref());
				} else {
					setBrightness(brightness);
				}
			}
			if (p.getLandscapeVideoPref()) {
				if (videoMode) {
					getAppActivity().setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				} else {
					getAppActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				}
			}
		}

		fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	private boolean checkMirroringMode(boolean clearFlags) {
		if (!AUTO) return false;
		var screenOnFlags =
				FLAG_KEEP_SCREEN_ON | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED;
		var app = FermataApplication.get();
		if (!app.isMirroringMode()) {
			if (clearFlags) getWindow().clearFlags(screenOnFlags);
			return false;
		}
		setFullScreen(true);
		getWindow().addFlags(screenOnFlags);
		getAppActivity().setRequestedOrientation(
				app.isMirroringLandscape() ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
						SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		return true;
	}

	public void keepScreenOn(boolean on) {
		if (on) getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
		else getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
	}

	public int getBrightness() {
		return Settings.System.getInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, 255);
	}

	public void setBrightness(int br) {
		try {
			Settings.System.putInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, br);
		} catch (SecurityException ex) {
			Log.e(ex, "Failed to change brightness");
		}
	}

	public boolean isVideoMode() {
		return videoMode;
	}

	public void setContentLoading(FutureSupplier<?> contentLoading) {
		if (this.contentLoading != null) {
			this.contentLoading.cancel();
			this.contentLoading = null;
		}

		progressBar.hide();
		if (contentLoading.isDone()) return;
		progressBar.show();

		var cl = this.contentLoading = contentLoading.main();
		cl.onCompletion((r, f) -> {
			if ((f != null) && !isCancellation(f)) Log.d(f);
			if (this.contentLoading == cl) {
				this.contentLoading = null;
				progressBar.hide();
			}
		});
	}

	public void backToNavFragment() {
		int id = getActiveNavItemId();
		showFragment((id == ID_NULL) ? R.id.folders_fragment : id);
	}

	@Override
	protected int getFrameContainerId() {
		return R.id.frame_layout;
	}

	@Nullable
	@Override
	public ActivityFragment showFragment(int id, Object input) {
		if (isCarActivity()) {
			try {
				me.aap.fermata.ui.fragment.YoldaLog.add("araç ekranı: " + getContext().getResources().getResourceEntryName(id));
			} catch (Exception ignore) {
			}
		}
		// Yolda: TV'ye girince klasör menüsü yerine kart ekranı (sesli aramada ve "Gelişmiş" düğmesinde atlanır)
		if ((id == R.id.tv_fragment) && !me.aap.fermata.ui.fragment.ChannelsFragment.consumeBypass())
			id = R.id.yolda_channels_fragment;
		BodyLayout b = getBody();
		if (b.isVideoMode()) b.setMode(BodyLayout.Mode.BOTH);
		ActivityFragment f = super.showFragment(id, input);
		updateBodyTopPadding();
		ControlPanelView cpv = getControlPanel();
		if (cpv != null) cpv.yoldaRefreshVisibility();
		return f;
	}

	protected ActivityFragment createFragment(int id) {
		if (id == R.id.home_fragment) { // Yolda V2: yığılmış kart destesi
			return new me.aap.fermata.ui.fragment.YoldaDeckFragment();
		} else if (id == R.id.yolda_channels_fragment) {
			return new me.aap.fermata.ui.fragment.ChannelsFragment();
		} else if (id == R.id.yolda_nav_fragment) {
			return new me.aap.fermata.ui.fragment.NavFragment();
		} else if (id == R.id.yolda_pharmacy_fragment) {
			return new me.aap.fermata.ui.fragment.PharmacyFragment();
		} else if (id == R.id.yolda_menu_fragment) {
			return new me.aap.fermata.ui.fragment.YoldaMenuFragment();
		} else if (id == R.id.yolda_setup_fragment) {
			return new me.aap.fermata.ui.fragment.YoldaSetupFragment();
		} else if (id == R.id.yolda_music) {
			return new me.aap.fermata.ui.fragment.MusicFragment();
		} else if (id == R.id.yolda_youtube) {
			return new me.aap.fermata.ui.fragment.YoutubeHomeFragment();
		} else if (id == R.id.folders_fragment) {
			return new FoldersFragment();
		} else if (id == R.id.favorites_fragment) {
			return new FavoritesFragment();
		} else if (id == R.id.playlists_fragment) {
			return new PlaylistsFragment();
		} else if (id == R.id.settings_fragment) {
			return new SettingsFragment();
		} else if (id == R.id.audio_effects_fragment) {
			return new AudioEffectsFragment();
		} else if (id == R.id.subtitles_fragment) {
			return new SubtitlesFragment();
		}
		ActivityFragment f = FermataApplication.get().getAddonManager().createFragment(id);
		return (f != null) ? f : super.createFragment(id);
	}

	@Nullable
	public MediaLibFragment getActiveMediaLibFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MediaLibFragment) ? (MediaLibFragment) f : null;
	}

	@Nullable
	public MainActivityFragment getActiveMainActivityFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MainActivityFragment) ? (MainActivityFragment) f : null;
	}

	@Nullable
	public MediaLibFragment getMediaLibFragment(int id) {
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if (!(f instanceof MediaLibFragment m)) continue;
			if (m.getFragmentId() == id) return m;
		}

		return null;
	}

	public boolean hasCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return (pi != null) || (getLib().getPrefs().getLastPlayedItemPref() != null);
	}

	@Override
	public void onBackPressed() {
		if (me.aap.fermata.ui.view.YoldaNowPlaying.hide(this)) return; // önce "Şimdi çalıyor" kapanır
		BodyLayout b = getBody();
		if ((b != null) && (b.isVideoMode() || b.isBothMode())) { // tam ekran / bölünmüş videodan çık: yayın sürer
			BodyLayout.yoldaSplit = false;
			b.setMode(BodyLayout.Mode.FRAME);
			return;
		}
		super.onBackPressed();
	}

	/**
	 * Yolda: tam ekran video / YouTube tam ekranındayken menü gizli ve araçta geri tuşu yok; her zaman
	 * görünen geri düğmesi. Basınca tam ekrandan çıkılır, yayın sürer, oynatıcı çubuğu altta kalır
	 * (✕ ile kapatılabilir).
	 */
	@Nullable
	private View yoldaBack;

	private void showYoldaBack(boolean show) {
		View host = findViewById(R.id.main_activity);
		if (!(host instanceof ConstraintLayout cl)) return;
		if (!show) {
			if (yoldaBack != null) yoldaBack.setVisibility(GONE);
			if (yoldaSplit != null) yoldaSplit.setVisibility(GONE);
			return;
		}
		Context ctx = getContext();
		if (yoldaBack == null) {
			AppCompatImageView v = new AppCompatImageView(ctx);
			v.setImageResource(R.drawable.yi_left);
			v.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
			int p = toIntPx(ctx, 13);
			v.setPadding(p, p, p, p);
			v.setBackground(me.aap.fermata.ui.view.YoldaStyle.glass(ctx,
					me.aap.fermata.ui.view.YoldaStyle.current(ctx), 999));
			v.setContentDescription("Geri");
			v.setClickable(true);
			v.setFocusable(true);
			v.setElevation(toIntPx(ctx, 25));
			v.setOnClickListener(x -> yoldaBackPressed());
			int s = toIntPx(ctx, isCarActivity() ? 56 : 52);
			ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(s, s);
			lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.leftMargin = toIntPx(ctx, 16);
			lp.topMargin = me.aap.fermata.ui.view.YoldaStyle.cutoutTop(ctx) + toIntPx(ctx, 14);
			cl.addView(v, lp);
			yoldaBack = v;
		}
		yoldaBack.setVisibility(VISIBLE);
		yoldaBack.setAlpha(0f);
		yoldaBack.animate().alpha(0.92f).setDuration(200).start();
		showSplitButton(cl);
	}

	/** Video + navigasyon: video izlerken ekranı bölüp yanında haritayı açan düğme (geri düğmesinin sağında). */
	@Nullable
	private AppCompatImageView yoldaSplit;

	private void showSplitButton(ConstraintLayout cl) {
		Context ctx = getContext();
		if (yoldaSplit == null) {
			AppCompatImageView v = new AppCompatImageView(ctx);
			v.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
			int p = toIntPx(ctx, 13);
			v.setPadding(p, p, p, p);
			v.setBackground(me.aap.fermata.ui.view.YoldaStyle.glass(ctx,
					me.aap.fermata.ui.view.YoldaStyle.current(ctx), 999));
			v.setClickable(true);
			v.setFocusable(true);
			v.setElevation(toIntPx(ctx, 25));
			v.setOnClickListener(x -> toggleSplit());
			int s = toIntPx(ctx, isCarActivity() ? 56 : 52);
			ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(s, s);
			lp.startToEnd = yoldaBack.getId();
			lp.topToTop = yoldaBack.getId();
			lp.leftMargin = toIntPx(ctx, 12);
			if (yoldaBack.getId() == View.NO_ID) yoldaBack.setId(View.generateViewId());
			lp.startToEnd = yoldaBack.getId();
			lp.topToTop = yoldaBack.getId();
			cl.addView(v, lp);
			yoldaSplit = v;
		}
		updateSplitButton();
		yoldaSplit.setVisibility(VISIBLE);
	}

	private void updateSplitButton() {
		if (yoldaSplit == null) return;
		BodyLayout b = getBody();
		boolean split = (b != null) && b.isBothMode();
		yoldaSplit.setImageResource(split ? R.drawable.yi_tv : R.drawable.yi_nav);
		yoldaSplit.setContentDescription(split ? "Tam ekran video" : "Navigasyonla böl");
	}

	/** Tam ekran video ↔ yarısı video, yarısı harita (son gidilen yere rota, yoksa konumun). */
	private void toggleSplit() {
		BodyLayout b = getBody();
		if (b == null) return;
		if (b.isBothMode()) {
			BodyLayout.yoldaSplit = false;
			b.setMode(BodyLayout.Mode.VIDEO);
			updateSplitButton();
			return;
		}
		Context ctx = getContext();
		java.util.List<String> recent = me.aap.fermata.ui.fragment.NavFragment.recent(ctx);
		// Araçta: Android Auto'nun kendi navigasyonu (araç ekranı haritayı ve çalan medyayı birlikte gösterir);
		// Yolda arka planda çalmaya devam eder. Web haritası yalnız "Yolda içinde" seçildiyse.
		if (isCarActivityNotMirror() && !me.aap.fermata.ui.fragment.NavFragment.inAppMap(ctx)
				&& me.aap.fermata.ui.fragment.NavFragment.carNav(this, null)) {
			return;
		}
		// Telefonda: harita web sayfası değil, harita uygulaması; video küçük pencerede üstünde sürer
		if (!isCarActivity()) {
			pipWithMaps();
			return;
		}
		String url = recent.isEmpty() ? "https://www.google.com/maps"
				: "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" + Uri.encode(recent.get(0));
		me.aap.fermata.ui.view.YoldaStyle.webNoToolbar = true;
		BodyLayout.yoldaSplit = true;
		b.setMode(BodyLayout.Mode.BOTH);
		Object f = showFragment(R.id.web_browser_fragment);
		if (f != null) {
			try {
				f.getClass().getMethod("loadUrl", String.class).invoke(f, url);
			} catch (Exception ex) {
				Log.e(ex, "Harita açılamadı");
			}
		}
		updateSplitButton();
		me.aap.fermata.ui.fragment.YoldaLog.add("Bölünmüş ekran: video + harita");
	}

	private void yoldaBackPressed() {
		onBackPressed(); // video → çerçeve; web tam ekranı → normal sayfa
		App.get().getHandler().postDelayed(() -> {
			if (videoMode) setVideoMode(false, null); // hâlâ tam ekransa zorla çık
			setBarsHidden(false);
			showYoldaBack(false);
			var cb = getMediaSessionCallback();
			if (cb.isPlaying() || (getCurrentPlayable() != null)) getControlPanel().setVisibility(VISIBLE);
		}, 150);
	}

	/**
	 * V2: araç çubuğu (tarayıcının adres / arama çubuğu dahil) altta, oynatıcının hemen üstünde.
	 * Gövde ekranın tepesinden başlar; araç çubuğu gizliyken gövde oynatıcıya kadar uzanır.
	 */
	private void toolbarToBottom(FermataActivity a) {
		try {
			View tb = a.findViewById(R.id.tool_bar);
			View bd = a.findViewById(R.id.body_layout);
			View cp = a.findViewById(R.id.control_panel);
			if (!(tb.getLayoutParams() instanceof ConstraintLayout.LayoutParams tl)
					|| !(bd.getLayoutParams() instanceof ConstraintLayout.LayoutParams bl)
					|| !(cp.getLayoutParams() instanceof ConstraintLayout.LayoutParams cl)) return;
			int unset = ConstraintLayout.LayoutParams.UNSET;
			tl.topToTop = unset;
			tl.topToBottom = R.id.body_layout; // dikey zincir: gövde → araç çubuğu → oynatıcı → (menü şeridi)
			tl.bottomToBottom = unset;
			tl.bottomToTop = R.id.control_panel;
			tl.verticalBias = 1f;
			tl.leftMargin = toIntPx(getContext(), me.aap.fermata.ui.view.YoldaStyle.searchLeftDp()); // solunda sesli düğme
			tl.bottomMargin = toIntPx(getContext(), me.aap.fermata.ui.view.YoldaStyle.VOICE_BOTTOM_DP);
			tl.topMargin = 0;
			tb.setLayoutParams(tl);
			bl.topToBottom = unset;
			bl.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
			bl.bottomToTop = R.id.tool_bar;
			bd.setLayoutParams(bl);
			cl.topToBottom = R.id.tool_bar;
			cp.setLayoutParams(cl);
			View pr = a.findViewById(R.id.content_loading_progress);
			if ((pr != null) && (pr.getLayoutParams() instanceof ConstraintLayout.LayoutParams pl)) {
				pl.topToBottom = unset;
				pl.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
				pr.setLayoutParams(pl);
			}
			View tm = a.findViewById(R.id.tool_menu);
			if ((tm != null) && (tm.getLayoutParams() instanceof ConstraintLayout.LayoutParams ml)) {
				ml.topToBottom = unset;
				ml.bottomToTop = R.id.tool_bar;
				ml.verticalBias = 1f;
				tm.setLayoutParams(ml);
			}
		} catch (Throwable ex) {
			Log.e(ex, "Araç çubuğu alta alınamadı");
		}
	}

	private int yoldaTopTop;

	/** Kendi üst boşluğunu bırakmayan sayfalar (tarayıcı, YouTube sitesi, klasörler) çentiğin altında kalmasın. */
	private void updateBodyTopPadding() {
		if (!me.aap.fermata.ui.view.YoldaStyle.DECK || (body == null)) return;
		int id = getActiveFragmentId();
		boolean yolda = (id == R.id.home_fragment) || (id == R.id.yolda_channels_fragment) || (id == R.id.yolda_music)
				|| (id == R.id.yolda_youtube) || (id == R.id.yolda_nav_fragment) || (id == R.id.yolda_menu_fragment)
				|| (id == R.id.yolda_setup_fragment) || (id == R.id.yolda_pharmacy_fragment);
		int top = yolda ? 0 : yoldaTopTop;
		int left = (me.aap.fermata.ui.view.YoldaStyle.sideRail(getContext()) && yolda && (id != R.id.home_fragment)
				&& !videoMode)
				? toIntPx(getContext(), me.aap.fermata.ui.view.YoldaStyle.searchLeftDp() - 8) : 0;
		if ((body.getPaddingTop() != top) || (body.getPaddingLeft() != left))
			body.setPadding(left, top, body.getPaddingRight(), body.getPaddingBottom());
	}

	/** Yolda: işlemi kapanan web sayfasını at, sahibi olan ekranı yeniden kur (uygulama kapanmaz). */
	public void recoverDeadWebView(View dead) {
		Fragment owner = null;
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			View fv = f.getView();
			if (fv == null) continue;
			for (android.view.ViewParent p = dead.getParent(); p != null; p = p.getParent()) {
				if (p == fv) {
					owner = f;
					break;
				}
			}
			if (owner != null) break;
		}
		try { // bu sayfa üzerinden çalan YouTube varsa durdur
			var eng = getMediaSessionCallback().getEngine();
			if ((eng != null) && eng.getClass().getName().contains("Youtube")) getMediaSessionCallback().onStop();
		} catch (Throwable ignore) {
		}
		if (dead.getParent() instanceof android.view.ViewGroup g) g.removeView(dead);
		if (dead instanceof android.webkit.WebView w) {
			try {
				w.destroy();
			} catch (Throwable ignore) {
			}
		}
		Fragment o = owner;
		if (o != null) me.aap.utils.app.App.get().getHandler().post(() -> recreateFragment(o));
	}

	/**
	 * Yolda: bellek azalınca ekranda olmayan web sayfalarını bırak (YouTube çalmıyorsa). Gizli sayfalar
	 * bellekte kaldıkça Android sayfa işlemini, sonra da uygulamayı kapatıyordu.
	 */
	private void trimHiddenWebPages(int level) {
		int active = getActiveFragmentId();
		boolean ytPlaying = false;
		try {
			var eng = getMediaSessionCallback().getEngine();
			ytPlaying = (eng != null) && eng.getClass().getName().contains("Youtube");
		} catch (Throwable ignore) {
		}
		var tr = getSupportFragmentManager().beginTransaction();
		int n = 0;
		for (Fragment f : new ArrayList<>(getSupportFragmentManager().getFragments())) {
			if (!(f instanceof me.aap.utils.ui.fragment.ActivityFragment af) || (af.getFragmentId() == active)) continue;
			View fv = f.getView();
			if ((fv == null) || !hasWebView(fv)) continue;
			if (ytPlaying && f.getClass().getName().contains("Youtube")) continue;
			tr.remove(f);
			n++;
		}
		if (n == 0) return;
		try {
			tr.commitNowAllowingStateLoss();
			me.aap.fermata.ui.fragment.YoldaLog.add("Bellek azaldı (" + level + "): " + n + " gizli web sayfası bırakıldı");
		} catch (Exception ex) {
			Log.e(ex, "Gizli sayfalar bırakılamadı");
		}
	}

	@Nullable
	private android.content.ComponentCallbacks2 memCallbacks;

	private static boolean hasWebView(View v) {
		if (v instanceof android.webkit.WebView) return true;
		if (v instanceof android.view.ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) if (hasWebView(g.getChildAt(i))) return true;
		}
		return false;
	}

	public FutureSupplier<Boolean> goToCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return ((pi == null) || (pi.isExternal())) ?
				getLib().getLastPlayedItem().main().map(this::goToItem) : completed(goToItem(pi));
	}

	public FutureSupplier<Item> goToItem(String id) {
		return getLib().getItem(id).main(getHandler()).map(i -> goToItem(i) ? i : null);
	}

	public boolean goToItem(Item i) {
		if (i == null) return false;
		BrowsableItem root = i.getRoot();

		if (root instanceof MediaLib.Folders) {
			showFragment(R.id.folders_fragment);
		} else if (root instanceof MediaLib.Favorites) {
			showFragment(R.id.favorites_fragment);
		} else if (root instanceof MediaLib.Playlists) {
			showFragment(R.id.playlists_fragment);
		} else if (root instanceof ExtRoot) {
			if ("youtube".equals(root.getId())) {
				showFragment(R.id.youtube_fragment);
				return true;
			}
		} else {
			MediaLibAddon a = AddonManager.get().getMediaLibAddon(root);
			if (a != null) {
				showFragment(a.getFragmentId());
			} else {
				Log.d("Unsupported item: ", i);
				return false;
			}
		}

		FermataApplication.get().getHandler().post(() -> {
			ActivityFragment f = getActiveFragment();
			if (!(f instanceof MediaLibFragment)) return;
			if (i instanceof PlayableItem) ((MediaLibFragment) f).revealItem(i);
			else if (i instanceof BrowsableItem) ((MediaLibFragment) f).openItem((BrowsableItem) i);
		});

		return true;
	}

	@Override
	protected boolean exitOnBackPressed() {
		return !isCarActivityNotMirror();
	}

	@Override
	public OverlayMenu createMenu(View anchor) {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getContextMenu() {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getToolBarMenu() {
		return findViewById(R.id.tool_menu);
	}

	public void startVoiceAssistant() {
		ActivityFragment f = getActiveFragment();
		if (!(f instanceof MainActivityFragment) || !((MainActivityFragment) f).startVoiceAssistant())
			voiceSearch(getCurrentFocus());
	}

	private void voiceSearch(View focus) {
		startSpeechRecognizer().onSuccess(q -> {
			if (focus instanceof EditText) {
				((EditText) focus).setText(q.get(0));
				focus.requestFocus();
			} else if (getAppActivity().isInputActive()) {
				getAppActivity().setTextInput(q.get(0));
			} else {
				VoiceCommandHandler h = voiceCommandHandler;
				if (h == null) h = voiceCommandHandler = new VoiceCommandHandler(this);
				h.handle(q);
			}
		});
	}

	public FutureSupplier<List<String>> startSpeechRecognizer() {
		return startSpeechRecognizer(null, false);
	}

	public FutureSupplier<List<String>> startSpeechRecognizer(String locale, boolean textInput) {
		me.aap.fermata.ui.fragment.YoldaWake.pause(); // mikrofon sesli asistanda
		FutureSupplier<int[]> check =
				isCarActivityNotMirror() ? completed(new int[]{PERMISSION_GRANTED}) :
						getAppActivity().checkPermissions(Manifest.permission.RECORD_AUDIO);
		return check.then(r -> {
			if (r[0] == PERMISSION_GRANTED) return completedVoid();
			else return failed(new IllegalStateException("Audio recording permission is not granted"));
		}).onFailure(err -> {
			Log.e(err, "Failed to request RECORD_AUDIO permission");
			showAlert(getContext(), R.string.err_no_audio_record_perm);
		}).then(v -> {
			if (speechListener != null) speechListener.destroy();
			Promise<List<String>> p = new Promise<>();
			String lang = (locale == null) ? getPrefs().getVoiceControlLang(this) : locale;
			Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
			i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
			i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			speechListener = new SpeechListener(p, textInput);
			speechListener.start(i);
			return p;
		});
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable(Item i) {
		MediaLibFragment f = getActiveMediaLibFragment();
		if (f == null) return MediaSessionCallbackAssistant.super.getPrevPlayable(i);
		BrowsableItem p = f.getAdapter().getParent();
		return (p instanceof SearchFolder) ? ((SearchFolder) p).getPrevPlayable(i) :
				MediaSessionCallbackAssistant.super.getPrevPlayable(i);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable(Item i) {
		MediaLibFragment f = getActiveMediaLibFragment();
		if (f == null) return MediaSessionCallbackAssistant.super.getNextPlayable(i);
		BrowsableItem p = f.getAdapter().getParent();
		return (p instanceof SearchFolder) ? ((SearchFolder) p).getNextPlayable(i) :
				MediaSessionCallbackAssistant.super.getNextPlayable(i);
	}

	@Override
	public EditText createEditText(Context ctx) {
		EditText t = getAppActivity().createEditText(ctx);
		if (isCarActivity() && getPrefs().getVoiceControlEnabledPref()) {
			t.setOnLongClickListener(v -> {
				startSpeechRecognizer().onSuccess(q -> t.setText(q.get(0)));
				return true;
			});
		}
		return t;
	}

	@Override
	public DialogBuilder createDialogBuilder(Context ctx) {
		return DialogBuilder.create(getContextMenu());
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
															FutureSupplier<List<PlayableItem>> selection) {
		addPlaylistMenu(builder, () -> selection, () -> "");
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
															Supplier<FutureSupplier<List<PlayableItem>>> selection,
															Supplier<? extends CharSequence> initName) {
		builder.addItem(R.id.playlist_add, R.drawable.playlist_add, R.string.playlist_add)
				.setSubmenu(b -> createPlaylistMenu(b, selection, initName));
	}

	private void createPlaylistMenu(OverlayMenu.Builder b,
																	Supplier<FutureSupplier<List<PlayableItem>>> selection,
																	Supplier<? extends CharSequence> initName) {
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			b.addItem(R.id.playlist_create, R.drawable.playlist_add, R.string.playlist_create)
					.setHandler(i -> createPlaylist(selection.get(), initName));

			for (int i = 0; i < playlists.size(); i++) {
				Playlist pl = (Playlist) playlists.get(i);
				String name = pl.getName();
				b.addItem(UiUtils.getArrayItemId(i), R.drawable.playlist, name)
						.setHandler(item -> addToPlaylist(name, selection.get()));
			}
		});
	}

	private boolean createPlaylist(FutureSupplier<List<PlayableItem>> selection,
																 Supplier<? extends CharSequence> initName) {
		UiUtils.queryText(getContext(), R.string.playlist_name, R.drawable.playlist, initName.get())
				.onSuccess(name -> {
					discardSelection();
					if (name == null) return;

					getLib().getPlaylists().addItem(name)
							.onFailure(err -> showAlert(getContext(), err.getMessage())).then(
									pl -> selection.main().then(items -> pl.addItems(items)
											.onFailure(err -> showAlert(getContext(), err.getMessage())).thenRun(() -> {
												MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
												if (f != null) f.getAdapter().reload();
											})));
				});
		return true;
	}

	private boolean addToPlaylist(String name, FutureSupplier<List<PlayableItem>> selection) {
		discardSelection();
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			for (Item i : getLib().getPlaylists().getUnsortedChildren().getOrThrow()) {
				Playlist pl = (Playlist) i;

				if (name.equals(pl.getName())) {
					selection.main().onSuccess(items -> {
						pl.addItems(items);
						MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
						if (f != null) f.getAdapter().reload();
					});
					break;
				}
			}
		});
		return true;
	}

	public void removeFromPlaylist(Playlist pl, List<PlayableItem> selection) {
		discardSelection();
		pl.removeItems(selection).onFailure(err -> showAlert(getContext(), err.getMessage()))
				.thenRun(() -> {
					MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
					if (f != null) f.getAdapter().reload();
				});
	}

	private void discardSelection() {
		ActivityFragment f = getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).discardSelection();
	}

	@Override
	protected int getExitMsg() {
		return R.string.press_back_again;
	}

	@Nullable
	private android.widget.FrameLayout voiceButton;
	@Nullable
	private android.content.SharedPreferences.OnSharedPreferenceChangeListener themeListener;

	@Nullable
	public View getYoldaVoiceButton() {
		return voiceButton;
	}

	/**
	 * Sesli asistan düğmesi: temanın renkleriyle ışıklı "küre" — ortadan yayılan renk, üstte cam parıltısı,
	 * ince parlak kenar ve dışta yumuşak hale; gölgesi de temanın renginde.
	 */
	private void styleVoiceButton(android.widget.FrameLayout b) {
		Context ctx = b.getContext();
		me.aap.fermata.ui.view.YoldaStyle.Theme t = me.aap.fermata.ui.view.YoldaStyle.current(ctx);
		int size = (b.getLayoutParams() != null) ? b.getLayoutParams().width : toIntPx(ctx, 56);
		int in = toIntPx(ctx, 4);
		android.graphics.drawable.GradientDrawable halo = new android.graphics.drawable.GradientDrawable();
		halo.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		halo.setColor((t.blobs[1] & 0x00FFFFFF) | 0x33000000);
		android.graphics.drawable.GradientDrawable core = new android.graphics.drawable.GradientDrawable();
		core.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		core.setGradientType(android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT);
		core.setGradientCenter(0.32f, 0.28f);
		core.setGradientRadius(size * 0.85f);
		core.setColors(new int[]{lighten(t.blobs[2]), t.blobs[0], t.blobs[1]});
		android.graphics.drawable.GradientDrawable sheen = new android.graphics.drawable.GradientDrawable(
				android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x66FFFFFF, 0x00FFFFFF});
		sheen.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		android.graphics.drawable.GradientDrawable rim = new android.graphics.drawable.GradientDrawable();
		rim.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		rim.setStroke(Math.max(1, toIntPx(ctx, 1)), 0xB3FFFFFF);
		android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
				new android.graphics.drawable.Drawable[]{halo, core, sheen, rim});
		ld.setLayerInset(1, in, in, in, in);
		ld.setLayerInset(2, in + toIntPx(ctx, 3), in + toIntPx(ctx, 2), in + toIntPx(ctx, 3), size / 2);
		ld.setLayerInset(3, in, in, in, in);
		b.setBackground(ld);
		b.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setOval(in, in, v.getWidth() - in, v.getHeight() - in);
			}
		});
		if (VERSION.SDK_INT >= 28) {
			b.setOutlineSpotShadowColor(t.blobs[0]);
			b.setOutlineAmbientShadowColor(t.blobs[1]);
		}
	}

	private static int lighten(int c) {
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
		r += (255 - r) * 2 / 5;
		g += (255 - g) * 2 / 5;
		bl += (255 - bl) * 2 / 5;
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/**
	 * Tema değişti (telefonda ya da araçta): bu ekranın zemini, menüsü, oynatıcısı, sesli düğmesi ve sayfaları
	 * yeni renklerle yeniden kurulur. Önceden yalnız açık sayfa yenileniyor, gerisi eski renkte kalıyordu.
	 */
	/** Son uygulanan tema (otomatik temada dilim değişince yeniden kurmak için). */
	private int appliedTheme = -1;
	private final Runnable autoThemeTick = new Runnable() {
		@Override
		public void run() {
			App.get().getHandler().removeCallbacks(this);
			if (!themeTickOn) return;
			yoldaAutoThemeTick();
			App.get().getHandler().postDelayed(this, 30_000);
		}
	};

	private boolean themeTickOn;

	/** Otomatik tema: araç gece kipini bildirir; gündüz / gece dilimi değiştiyse ekranlar yeni temayla kurulur. */
	public void yoldaAutoThemeTick() {
		Context ctx = getContext();
		if (isCarActivity()) {
			int m = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
			me.aap.fermata.ui.view.YoldaStyle.setCarNight(m == Configuration.UI_MODE_NIGHT_YES);
		}
		yoldaRefreshThemeIfChanged();
	}

	private void yoldaRefreshThemeIfChanged() {
		int i = me.aap.fermata.ui.view.YoldaStyle.index(getContext());
		if (i == appliedTheme) return;
		boolean first = appliedTheme < 0;
		appliedTheme = i;
		if (!first) yoldaRefreshTheme();
	}

	private void yoldaRefreshTheme() {
		Context ctx = getContext();
		appliedTheme = me.aap.fermata.ui.view.YoldaStyle.index(ctx);
		me.aap.fermata.ui.view.YoldaStyle.refreshBackdrop(ctx);
		try {
			if (navBar instanceof me.aap.fermata.ui.view.FermataNavBarView nb) nb.applyYoldaPill();
		} catch (Throwable ignore) {
		}
		if (voiceButton != null) styleVoiceButton(voiceButton);
		if (yoldaBack != null) {
			yoldaBack.setBackground(me.aap.fermata.ui.view.YoldaStyle.glass(ctx,
					me.aap.fermata.ui.view.YoldaStyle.current(ctx), 999));
		}
		if (deckBack != null) {
			deckBack.setBackground(me.aap.fermata.ui.view.YoldaStyle.glass(ctx,
					me.aap.fermata.ui.view.YoldaStyle.current(ctx), 999));
		}
		ControlPanelView cp = getControlPanel();
		if (cp != null) cp.rebuildYoldaBar();
		// Sayfalar: gizlileri bırak (açılınca yeni renklerle kurulur), açık olanı yeniden kur.
		// Web sayfaları (YouTube, tarayıcı) bırakılmaz: içerik yeniden yüklenmesin.
		int active = getActiveFragmentId();
		Fragment activeF = null;
		var tr = getSupportFragmentManager().beginTransaction();
		int n = 0;
		for (Fragment f : new ArrayList<>(getSupportFragmentManager().getFragments())) {
			if (!(f instanceof me.aap.utils.ui.fragment.ActivityFragment af)) continue;
			View fv = f.getView();
			if ((fv != null) && hasWebView(fv)) continue;
			if (af.getFragmentId() == active) {
				activeF = f;
				continue;
			}
			tr.remove(f);
			n++;
		}
		try {
			if (n > 0) tr.commitNowAllowingStateLoss();
		} catch (Exception ex) {
			Log.e(ex, "Tema: sayfalar yenilenemedi");
		}
		if (activeF != null) recreateFragment(activeF);
		if (isCarActivityNotMirror()) { // Android Auto çubukları da yeni temanın renginde
			try {
				Object act = getAppActivity();
				act.getClass().getMethod("yoldaBarColors").invoke(act);
			} catch (Throwable ignore) {
			}
		}
		me.aap.fermata.ui.fragment.YoldaLog.add("Tema: " + me.aap.fermata.ui.view.YoldaStyle.current(ctx).name
				+ " (" + (isCarActivity() ? "araç" : "telefon") + ")");
	}

	/** Her sayfada sol altta, menünün hemen üstünde sesli asistan düğmesi (video tam ekranda gizlenir). */
	private void addVoiceButton(ConstraintLayout host) {
		Context ctx = host.getContext();
		android.widget.FrameLayout b = new android.widget.FrameLayout(ctx);
		voiceButton = b;
		b.setElevation(toIntPx(ctx, 12));
		b.setClickable(true);
		b.setFocusable(true);
		b.setContentDescription("Sesli komut");
		android.widget.ImageView mic = new android.widget.ImageView(ctx);
		mic.setImageResource(R.drawable.yi_mic);
		mic.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		int is = toIntPx(ctx, 30);
		b.addView(mic, new android.widget.FrameLayout.LayoutParams(is, is, Gravity.CENTER));
		b.setOnClickListener(v -> {
			v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
					.withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
			startVoiceAssistant();
		});
		boolean rail = (navBar != null) && navBar.isLeft();
		int s = toIntPx(ctx, me.aap.fermata.ui.view.YoldaStyle.DECK ? me.aap.fermata.ui.view.YoldaStyle.VOICE_DP : rail ? 56 : 60);
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(s, s);
		if (me.aap.fermata.ui.view.YoldaStyle.DECK) { // V2: şerit yok; içeriğin üstünde sol altta, oynatıcının üstünde
			lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.bottomToTop = R.id.control_panel;
			lp.leftMargin = toIntPx(ctx, 16);
			lp.bottomMargin = toIntPx(ctx, me.aap.fermata.ui.view.YoldaStyle.VOICE_BOTTOM_DP);
		} else if (rail) { // araç: dikey menünün en altında, ortalı
			lp.startToStart = R.id.nav_bar;
			lp.endToEnd = R.id.nav_bar;
			lp.bottomToBottom = R.id.nav_bar;
			lp.bottomMargin = toIntPx(ctx, 6);
			mic.getLayoutParams().width = mic.getLayoutParams().height = toIntPx(ctx, 24);
		} else { // telefon: alt menü kapsülünün solunda, aynı hizada
			lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.leftMargin = toIntPx(ctx, 16);
			lp.topToTop = R.id.nav_bar;
			lp.bottomToBottom = R.id.nav_bar;
		}
		b.setId(View.generateViewId());
		host.addView(b, lp);
		styleVoiceButton(b);
		// "Hey Yolda" dinlerken sağ üstte yeşil nokta
		View dot = new View(ctx);
		android.graphics.drawable.GradientDrawable dd = new android.graphics.drawable.GradientDrawable();
		dd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		dd.setColor(0xFF22C55E);
		dd.setStroke(Math.max(1, toIntPx(ctx, 2)), 0xFFFFFFFF);
		dot.setBackground(dd);
		dot.setVisibility(View.GONE);
		int ds = toIntPx(ctx, 14);
		android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(ds, ds,
				Gravity.TOP | Gravity.END);
		dlp.topMargin = dlp.rightMargin = toIntPx(ctx, 4);
		b.addView(dot, dlp);
		Runnable wakeDot = new Runnable() {
			@Override
			public void run() {
				if (!b.isAttachedToWindow()) return;
				boolean on = me.aap.fermata.ui.fragment.YoldaWake.isListening();
				dot.setVisibility(on ? View.VISIBLE : View.GONE);
				b.setContentDescription(on ? "Sesli komut (Hey Yolda dinliyor)" : "Sesli komut");
				b.postDelayed(this, 1500);
			}
		};
		b.post(wakeDot);
		// Tema değişince (bu ya da diğer ekrandan) bu ekran da yenilensin. Dinleyici zayıf tutulduğu için alanda saklanır.
		if (themeListener == null) {
			themeListener = (sp, key) -> {
				if (me.aap.fermata.ui.view.YoldaStyle.THEME_KEYS.contains(key))
					App.get().getHandler().post(this::yoldaRefreshThemeIfChanged);
			};
			ctx.getApplicationContext().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS,
					Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(themeListener);
			appliedTheme = me.aap.fermata.ui.view.YoldaStyle.index(ctx);
			themeTickOn = true;
			App.get().getHandler().postDelayed(autoThemeTick, 5_000);
		}
		if (me.aap.fermata.ui.view.YoldaStyle.DECK) {
			addDeckBackButton(host, b);
			// Tam ekran videoda ve Şimdi çalıyor açıkken gizli
			host.getViewTreeObserver().addOnGlobalLayoutListener(this::updateDeckButtons);
			return;
		}
		// Video tam ekrandayken alt menü gizlenir; düğme de onunla birlikte gizlensin
		navBar.getViewTreeObserver().addOnGlobalLayoutListener(() ->
				b.setVisibility(navBar.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE));
	}

	/** V2: sesli düğmenin üstünde, kartın içindeyken desteye dönüş düğmesi. */
	@Nullable
	private View deckBack;

	@Nullable
	public View getDeckBackButton() {
		return deckBack;
	}

	private void addDeckBackButton(ConstraintLayout host, View voice) {
		Context ctx = host.getContext();
		AppCompatImageView v = new AppCompatImageView(ctx);
		v.setImageResource(R.drawable.yi_home_fill);
		v.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
		int p = toIntPx(ctx, 14);
		v.setPadding(p, p, p, p);
		v.setBackground(me.aap.fermata.ui.view.YoldaStyle.glass(ctx, me.aap.fermata.ui.view.YoldaStyle.current(ctx), 999));
		v.setContentDescription("Ana sayfa");
		v.setClickable(true);
		v.setFocusable(true);
		v.setElevation(toIntPx(ctx, 12));
		v.setOnClickListener(x -> {
			x.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
					.withEndAction(() -> x.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
			me.aap.fermata.ui.view.YoldaNowPlaying.hide(this);
			showFragment(R.id.home_fragment); // Ana sayfa: her zaman kartlara
		});
		int s = toIntPx(ctx, 52);
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(s, s);
		// sesli düğmenin hemen üstünde, ortalı
		lp.startToStart = voice.getId();
		lp.endToEnd = voice.getId();
		lp.bottomToTop = voice.getId();
		lp.bottomMargin = toIntPx(ctx, 12);
		host.addView(v, lp);
		deckBack = v;
		updateDeckButtons();
	}

	/** Küçük pencere (resim içinde resim): yalnız video; düğmeler, çubuk, kanal şeridi gizli. */
	private boolean yoldaPip;

	public boolean isYoldaPip() {
		return yoldaPip;
	}

	public void yoldaPipChanged(boolean pip) {
		yoldaPip = pip;
		me.aap.fermata.ui.fragment.YoldaLog.add("Küçük pencere: " + (pip ? "açıldı" : "kapandı"));
		ControlPanelView cp = getControlPanel();
		if (pip) {
			if (cp != null) cp.setVisibility(GONE);
			if (voiceButton != null) voiceButton.setVisibility(GONE);
			if (deckBack != null) deckBack.setVisibility(GONE);
			showYoldaBack(false);
			me.aap.fermata.ui.view.VideoView vv = getBody().getVideoView();
			if (vv != null) me.aap.fermata.ui.view.YoldaChannelRail.of(vv).hide();
		} else {
			setBarsHidden(false);
			if ((cp != null) && (getMediaSessionCallback().isPlaying() || (getCurrentPlayable() != null)))
				cp.setVisibility(VISIBLE);
			if (videoMode) showYoldaBack(true);
			updateDeckButtons();
		}
	}

	/**
	 * Telefonda "videonun yanında harita": video küçük pencereye geçer, harita uygulaması (Google Haritalar /
	 * Waze) açılır; video haritanın üstünde oynamaya devam eder. Küçük pencere desteklenmezse yalnız harita açılır.
	 */
	private void pipWithMaps() {
		Context ctx = getContext();
		boolean pip = false;
		try {
			if (android.os.Build.VERSION.SDK_INT >= 26) {
				android.app.PictureInPictureParams p = new android.app.PictureInPictureParams.Builder()
						.setAspectRatio(new android.util.Rational(16, 9)).build();
				pip = ((android.app.Activity) getAppActivity()).enterPictureInPictureMode(p);
			}
		} catch (Throwable ex) {
			me.aap.fermata.ui.fragment.YoldaLog.add("Küçük pencere açılamadı: " + ex);
		}
		java.util.List<String> recent = me.aap.fermata.ui.fragment.NavFragment.recent(ctx);
		boolean waze = ctx.getApplicationContext().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS,
				Context.MODE_PRIVATE).getBoolean("nav_waze", false);
		Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(recent.isEmpty() ? "geo:0,0?q="
				: ("google.navigation:q=" + Uri.encode(recent.get(0)))))
				.setPackage(waze ? "com.waze" : "com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		App.get().getHandler().postDelayed(() -> {
			try {
				ctx.getApplicationContext().startActivity(i);
			} catch (Exception ex) {
				try {
					ctx.getApplicationContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} catch (Exception ex2) {
					me.aap.utils.ui.UiUtils.showAlert(ctx, "Harita uygulaması bulunamadı");
				}
			}
		}, pip ? 350 : 0);
		me.aap.fermata.ui.fragment.YoldaLog.add("Video + harita: " + (pip ? "küçük pencere ve " : "") + "harita uygulaması");
	}

	private void updateDeckButtons() {
		if (!me.aap.fermata.ui.view.YoldaStyle.DECK) return;
		if (yoldaPip) return;
		boolean np = me.aap.fermata.ui.view.YoldaNowPlaying.isShown(this);
		if ((navBar != null) && (navBar.getVisibility() != View.GONE)) navBar.setVisibility(View.GONE);
		boolean full = videoMode || barsHidden;
		updateSplitButton();
		int vv = (full || np) ? View.GONE : View.VISIBLE;
		if ((voiceButton != null) && (voiceButton.getVisibility() != vv)) voiceButton.setVisibility(vv);
		int bv = (full || np || (getActiveFragmentId() == R.id.home_fragment)) ? View.GONE : View.VISIBLE;
		if ((deckBack != null) && (deckBack.getVisibility() != bv)) deckBack.setVisibility(bv);
	}

	private static boolean yoldaSplashShown;

	/** Yolda açılışı (telefonda ve araçta): logo büyüyerek gelir, "Yolda" yazısı, sonra yumuşak geçiş. */
	private void showYoldaSplash(ConstraintLayout host) {
		if (yoldaSplashShown) return;
		yoldaSplashShown = true;
		Context ctx = host.getContext();
		android.widget.FrameLayout s = new android.widget.FrameLayout(ctx);
		s.setBackgroundColor(0xFF0B0E2A);
		s.setClickable(true);
		android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
		box.setOrientation(android.widget.LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		android.widget.ImageView logo = new android.widget.ImageView(ctx);
		logo.setImageResource(R.drawable.launcher);
		int ls = toIntPx(ctx, 132);
		logo.setOutlineProvider(new android.view.ViewOutlineProvider() {
			@Override
			public void getOutline(View v, android.graphics.Outline o) {
				o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getWidth() * 0.26f);
			}
		});
		logo.setClipToOutline(true);
		logo.setElevation(toIntPx(ctx, 12));
		box.addView(logo, new android.widget.LinearLayout.LayoutParams(ls, ls));
		android.widget.TextView name = new android.widget.TextView(ctx);
		name.setText("Yolda");
		name.setTextColor(0xFFFFFFFF);
		name.setTextSize(36);
		name.setLetterSpacing(-0.02f);
		name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
		name.setPadding(0, toIntPx(ctx, 22), 0, 0);
		name.setGravity(Gravity.CENTER);
		// Dikey LinearLayout varsayılanı tam genişlik verip yazıyı sola yaslıyordu; ortala
		android.widget.LinearLayout.LayoutParams nlp = new android.widget.LinearLayout.LayoutParams(-2, -2);
		nlp.gravity = Gravity.CENTER_HORIZONTAL;
		box.addView(name, nlp);
		s.addView(box, new android.widget.FrameLayout.LayoutParams(-1, -1));
		ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
		lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		s.setElevation(toIntPx(ctx, 50));
		host.addView(s, lp);
		logo.setScaleX(0.82f);
		logo.setScaleY(0.82f);
		logo.setAlpha(0f);
		name.setAlpha(0f);
		logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(520)
				.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
		name.animate().alpha(1f).setStartDelay(220).setDuration(420).start();
		s.animate().alpha(0f).setStartDelay(1250).setDuration(380).withEndAction(() -> host.removeView(s)).start();
	}

	private void init() {
		FermataActivity a = getAppActivity();
		// Yolda tanı günlüğü: çökmeleri ve açılışları kaydet (araçta yeniden başlama nedenini bulmak için)
		me.aap.fermata.ui.fragment.YoldaLog.install(getContext());
		me.aap.fermata.ui.fragment.YoldaLog.add("Açılış: " + (isCarActivity() ? "ARAÇ" : "telefon")
				+ " · " + me.aap.fermata.BuildConfig.VERSION_NAME);
		a.setContentView(getLayout());
		if (me.aap.fermata.ui.view.YoldaStyle.DECK) toolbarToBottom(a);
		getContext().registerComponentCallbacks(memCallbacks = new android.content.ComponentCallbacks2() {
			@Override
			public void onTrimMemory(int level) {
				if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trimHiddenWebPages(level);
			}

			@Override
			public void onConfigurationChanged(@NonNull Configuration c) {
			}

			@Override
			public void onLowMemory() {
				trimHiddenWebPages(80);
			}
		});
		// Oynatıcının ağ motorunu (Cronet) arka planda hazırla: ilk yayın açılırken ekran donmasın, daha hızlı açılsın
		me.aap.utils.app.App.get().getExecutor().submit(() -> {
			try {
				Class.forName("me.aap.fermata.engine.exoplayer.ExoPlayerEngine");
			} catch (Throwable ignore) {
			}
		});
		toolBar = a.findViewById(R.id.tool_bar);
		progressBar = a.findViewById(R.id.content_loading_progress);
		navBar = a.findViewById(R.id.nav_bar);
		body = a.findViewById(R.id.body_layout);
		controlPanel = a.findViewById(R.id.control_panel);
		floatingButton = a.findViewById(R.id.floating_button);
		floatingButton.setScale(getPrefs().getTextIconSizePref(this));
		controlPanel.bind(getMediaServiceBinder());
		// Yolda Cam: tüm uygulamanın arkasında süzülen pastel ışıklar; çubuklar ve listeler yarı saydam
		if (me.aap.fermata.ui.view.YoldaStyle.glassActive) {
			View root = a.findViewById(R.id.main_activity);
			if (root != null) me.aap.fermata.ui.view.YoldaStyle.applyBackdrop(root);
			// Ekranlar arasında yumuşak geçiş
			me.aap.utils.ui.activity.ActivityDelegate.fragmentEnterAnim = R.anim.yolda_enter;
			me.aap.utils.ui.activity.ActivityDelegate.fragmentExitAnim = R.anim.yolda_exit;
			// Alt menü: havada duran oval cam kapsül
			if (navBar instanceof me.aap.fermata.ui.view.FermataNavBarView fnb) {
				fnb.applyYoldaPill();
				fnb.post(fnb::applyYoldaPill);
			}
			// Mini oynatıcı: kenarlardan içeride, yuvarlak cam
			if (controlPanel.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams cp) {
				float d = getContext().getResources().getDisplayMetrics().density;
				cp.width = 0;
				cp.leftMargin = cp.rightMargin = Math.round(16 * d);
				cp.bottomMargin = Math.round(10 * d);
				controlPanel.setLayoutParams(cp);
				controlPanel.setBackground(me.aap.fermata.ui.view.YoldaStyle.glass(getContext(),
						me.aap.fermata.ui.view.YoldaStyle.current(getContext()), 28));
				controlPanel.setClipToOutline(true);
			}
			// Menüler ve diyaloglar: koyu cam panel, 56dp satırlar, büyük düğmeler
			me.aap.utils.ui.menu.OverlayMenuView.styler = me.aap.fermata.ui.view.YoldaStyle::styleMenu;
			styleYoldaPlayer();
			if (root instanceof ConstraintLayout cl) {
				addVoiceButton(cl);
				showYoldaSplash(cl);
			}
		}

		if (VERSION.SDK_INT >= VERSION_CODES.VANILLA_ICE_CREAM && !a.isCarActivity()) {
			// Yolda: dinleyici kök görünümde; üst çubuk gizli (tam ekran sayfalar) olsa da boşluk uygulanır
			ViewCompat.setOnApplyWindowInsetsListener(a.findViewById(R.id.main_activity), (v, insets) -> {
				// Yolda: tam ekranda sistem çubukları gizli olsa da kamera çentiği yerinde durur;
				// geri düğmesi, başlıklar çentiğin altında kalmasın
				var bars = insets.getInsets(
						WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
				);
				me.aap.fermata.ui.view.YoldaStyle.cutoutTopPx =
						insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top;
				if (me.aap.fermata.ui.view.YoldaStyle.glassActive && me.aap.fermata.ui.view.YoldaStyle.DECK) {
					// V2: araç çubuğu altta; üst boşluğu Yolda dışı sayfalar (tarayıcı, klasörler) kendisi alır
					v.setPadding(bars.left, 0, bars.right, bars.bottom);
					yoldaTopTop = bars.top;
					updateBodyTopPadding();
				} else if (me.aap.fermata.ui.view.YoldaStyle.glassActive) {
					// Üst boşluk tüm ekrana değil yalnız Fermata'nın üst çubuğuna: Yolda sayfaları kendi
					// boşluğunu bırakıyor, iki kez eklenince üstte büyük boşluk kalıyordu.
					v.setPadding(bars.left, 0, bars.right, bars.bottom);
					if (toolBar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams tlp
							&& (tlp.topMargin != bars.top)) {
						tlp.topMargin = bars.top;
						toolBar.setLayoutParams(tlp);
					}
				} else {
					v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
				}
				return WindowInsetsCompat.CONSUMED;
			});
		}
	}

	@LayoutRes
	private int getLayout() {
		MainActivityPrefs prefs = getPrefs();
		return switch (prefs.getNavBarPosPref(this)) {
			case NavBarView.POSITION_LEFT -> R.layout.main_activity_left;
			case NavBarView.POSITION_RIGHT -> R.layout.main_activity_right;
			default -> R.layout.main_activity;
		};
	}

	private static String[] getRequiredPermissions() {
		List<String> perms = new ArrayList<>();
		perms.add(permission.READ_EXTERNAL_STORAGE);
		if (VERSION.SDK_INT >= VERSION_CODES.P) {
			perms.add(permission.FOREGROUND_SERVICE);
		}
		if (VERSION.SDK_INT >= VERSION_CODES.Q) {
			perms.add(permission.ACCESS_MEDIA_LOCATION);
			perms.add(permission.USE_FULL_SCREEN_INTENT);
		}
		if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
			perms.add(permission.USE_FULL_SCREEN_INTENT);
			perms.add(permission.POST_NOTIFICATIONS);
		}
		if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
			perms.add(permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK);
		}
		return perms.toArray(new String[0]);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (MainActivityPrefs.hasThemePref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasNavBarPosPref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasTextIconSizePref(this, prefs)) {
			if (floatingButton != null) floatingButton.setScale(getPrefs().getTextIconSizePref(this));
		} else if (MainActivityPrefs.hasNavBarSizePref(this, prefs)) {
			if (navBar != null) navBar.setSize(getPrefs().getNavBarSizePref(this));
		} else if (MainActivityPrefs.hasToolBarSizePref(this, prefs)) {
			if (toolBar != null) toolBar.setSize(getPrefs().getToolBarSizePref(this));
		} else if (MainActivityPrefs.hasFullscreenPref(this, prefs)) {
			setSystemUiVisibility();
		} else if (prefs.contains(CHANGE_BRIGHTNESS)) {
			if (getPrefs().getChangeBrightnessPref()) {
				if (!Settings.System.canWrite(getContext())) {
					Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
					i.setData(Uri.parse("package:" + getContext().getPackageName()));
					startActivity(i);
				}
			}
		} else if (prefs.contains(BRIGHTNESS)) {
			if (isVideoMode()) setBrightness(getPrefs().getBrightnessPref());
		} else if (prefs.contains(VOICE_CONTROl_ENABLED)) {
			if (!getPrefs().getVoiceControlEnabledPref()) {
				getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
				return;
			}
			getAppActivity().checkPermissions(permission.RECORD_AUDIO).onCompletion((r, err) -> {
				if ((err == null) && (r[0] == PERMISSION_GRANTED)) return;
				if (err != null) Log.e(err, "Failed to request RECORD_AUDIO permission");
				showAlert(getContext(), R.string.err_no_audio_record_perm);
				getPrefs().applyBooleanPref(VOICE_CONTROl_FB, false);
			});
		} else if (prefs.contains(VOICE_CONTROL_SUBST)) {
			if (voiceCommandHandler != null) voiceCommandHandler.updateWordSubst();
		} else if (prefs.contains(CLOCK_POS)) {
			getBody().getVideoView().setClockPos(getPrefs().getClockPosPref());
		} else if (prefs.contains(LOCALE)) {
			recreate();
		}
	}

	@Override
	public boolean onKeyDown(int code, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	@Override
	public boolean onKeyUp(int code, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	@Override
	public boolean onKeyLongPress(int code, KeyEvent event,
																IntObjectFunction<KeyEvent, Boolean> next) {
		return handleKeyEvent(this, event, next);
	}

	public HandlerExecutor getHandler() {
		return handler;
	}

	public Cancellable post(Runnable task) {
		return getHandler().submit(task);
	}

	public Cancellable postDelayed(Runnable task, long delay) {
		return getHandler().schedule(task, delay);
	}

	public Cancellable interruptPlayback() {
		MediaSessionCallback cb = getMediaSessionCallback();
		if (!cb.isPlaying()) return Cancellable.CANCELED;
		PlaybackStateCompat playbackState = cb.getPlaybackState();
		cb.onPause();
		return () -> {
			PlaybackStateCompat state = cb.getPlaybackState();
			if ((state.getState() == PlaybackStateCompat.STATE_PAUSED) &&
					((state == playbackState) || (state.getPosition() != playbackState.getPosition()))) {
				cb.onPlay();
			}
			return true;
		};
	}

	@Override
	protected FutureSupplier<Void> sendCrashReport(Throwable err) {
		return completedVoid();
	}

	static final class Prefs implements MainActivityPrefs {
		static final Prefs instance = new Prefs();
		private final List<ListenerRef<PreferenceStore.Listener>> listeners = new LinkedList<>();
		private final SharedPreferences prefs = FermataApplication.get().getDefaultSharedPreferences();

		private Prefs() {
			App.get().getHandler().post(this::migratePrefs);
		}

		private void migratePrefs() {
			// Rename old prefs
			var oldTheme = Pref.i("THEME", THEME_DARK);
			var oldScale = Pref.f("MEDIA_ITEM_SCALE", 1f);
			var fbLongPress = Pref.i("FB_LONG_PRESS", 0);
			var fbLongPressAA = Pref.i("FB_LONG_PRESS_AA", 0);
			var showClock = Pref.b("SHOW_CLOCK", false);
			var voiceCtrlM = Pref.b("VOICE_CONTROl_M", false);
			var theme = getIntPref(oldTheme);
			var scale = getFloatPref(oldScale);

			if ((theme != THEME_DARK) || (scale != 1f)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					if (theme != THEME_DARK) {
						e.setIntPref(THEME_MAIN, theme);
						if (AUTO) e.setIntPref(THEME_AA, theme);
						e.removePref(oldTheme);
					}
					if (scale != 1f) {
						e.setFloatPref(TEXT_ICON_SIZE, scale);
						if (AUTO) e.setFloatPref(TEXT_ICON_SIZE_AA, scale);
						e.removePref(oldScale);
					}
				}
			}

			if ((getIntPref(fbLongPress) == 1) || (getIntPref(fbLongPressAA) == 1)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					e.setBooleanPref(VOICE_CONTROl_ENABLED, true);
					e.setBooleanPref(VOICE_CONTROl_FB, true);
				}
			}

			if (getBooleanPref(showClock)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					e.removePref(showClock);
					e.setIntPref(CLOCK_POS, CLOCK_POS_RIGHT);
				}
			}

			if (getBooleanPref(voiceCtrlM)) {
				removePref(voiceCtrlM);
				var kp = Key.getPrefs();
				var o = Action.ACTIVATE_VOICE_CTRL.ordinal();
				kp.applyIntPref(Key.M.getLongActionPref(), o);
				kp.applyIntPref(Key.MENU.getLongActionPref(), o);
			}
		}

		@NonNull
		@Override
		public SharedPreferences getSharedPreferences() {
			return prefs;
		}

		@Override
		public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
			return listeners;
		}
	}

	private final class SpeechListener implements RecognitionListener {
		private final Promise<List<String>> promise;
		private final boolean textInput;
		private SpeechRecognizer recognizer;
		private final MaterialTextView text;
		private PlaybackStateCompat playbackState;
		// Yolda: internetsiz (cihaz içi) tanıma, animasyonlu küre, durum yazısı
		private boolean onDevice;
		private boolean fallbackTried;
		private Intent intent;
		private me.aap.fermata.ui.view.VoiceOrbView orb;
		private android.widget.TextView statusText;

		private SpeechListener(Promise<List<String>> promise, boolean textInput) {
			this.promise = promise;
			this.textInput = textInput;
			recognizer = createRecognizer(!getContext().getApplicationContext().getSharedPreferences(
					me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).getBoolean("ondevice_tr_bad", false));
			text = new MaterialTextView(getContext());
		}

		/** Android 12+ ve cihazda tanıma varsa internetsiz motor; yoksa normal motor. */
		private SpeechRecognizer createRecognizer(boolean preferOnDevice) {
			Context ctx = getContext();
			SpeechRecognizer r = null;
			onDevice = false;
			if (preferOnDevice && (VERSION.SDK_INT >= VERSION_CODES.S)) {
				try {
					if (SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
						r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
						onDevice = true;
					}
				} catch (Exception ex) {
					Log.e(ex, "Cihaz içi tanıma açılamadı");
				}
			}
			if (r == null) r = SpeechRecognizer.createSpeechRecognizer(ctx);
			r.setRecognitionListener(this);
			return r;
		}

		/** Tanımayı kanal adları ve komutlarla yönlendir: "Habertürk", "TGRT", "A Spor" daha doğru anlaşılır. */
		private void addHints(Intent i) {
			i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
			i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
			if (VERSION.SDK_INT < 33) return;
			ArrayList<String> hints = new ArrayList<>();
			for (String s : new String[]{"aç", "oynat", "durdur", "devam et", "favoriler", "YouTube", "radyo",
					"ileri sar", "geri sar", "sonraki kanal", "önceki kanal"}) hints.add(s);
			try {
				java.io.File f = new java.io.File(getContext().getFilesDir(), "yolda.m3u");
				if (f.isFile()) {
					for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()),
							java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
						if (line.startsWith("#EXTINF") && line.contains(",") && (hints.size() < 120))
							hints.add(line.substring(line.lastIndexOf(',') + 1).trim());
					}
				}
			} catch (Exception ex) {
				Log.e(ex, "Kanal adları okunamadı");
			}
			i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, hints);
		}

		void start(Intent i) {
			addHints(i);
			intent = i;
			var cb = getMediaSessionCallback();
			if (cb.isPlaying()) {
				var eng = cb.getEngine();
				if ((eng != null) && eng.canPause()) {
					cb.onPause();
					playbackState = cb.getPlaybackState();
				} else {
					playbackState = null;
				}
			} else {
				playbackState = null;
			}
			recognizer.startListening(i);
		}

		void destroy() {
			MediaSessionCallback cb = getMediaSessionCallback();
			PlaybackStateCompat state = cb.getPlaybackState();
			if ((playbackState != null) && (state.getState() == PlaybackStateCompat.STATE_PAUSED)) {
				if ((state == playbackState) || (state.getPosition() != playbackState.getPosition())) {
					cb.onPlay();
				}
			}
			playbackState = null;
			recognizer.destroy();
			promise.cancel();
			removeGlow();
			if (speechListener == this) speechListener = null;
		}

		// Yolda: pencere yerine uygulamanın üstünde ışık efekti
		private me.aap.fermata.ui.view.VoiceGlowView glow;

		private boolean showGlow() {
			if (glow != null) return true;
			View host = findViewById(R.id.main_activity);
			if (!(host instanceof ConstraintLayout cl)) return false;
			Context ctx = getContext();
			glow = new me.aap.fermata.ui.view.VoiceGlowView(ctx, me.aap.fermata.ui.view.YoldaStyle.current(ctx), text);
			statusText = glow.status;
			statusText.setText(onDevice ? "DİNLİYORUM · İNTERNETSİZ" : "DİNLİYORUM");
			text.setText("“TRT Haber'i aç” · “eve git” · “müzik aç”");
			glow.setOnClickListener(v -> destroy()); // dokununca vazgeç
			ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, 0);
			lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
			lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
			glow.setElevation(toIntPx(ctx, 40));
			cl.addView(glow, lp);
			return true;
		}

		private void removeGlow() {
			me.aap.fermata.ui.fragment.YoldaWake.resumeSoon(); // asistan bitti: "Hey Yolda"yı yeniden dinle
			View v = glow;
			glow = null;
			if (v == null) return;
			v.animate().alpha(0f).setDuration(220).withEndAction(() -> {
				if (v.getParent() instanceof android.view.ViewGroup p) p.removeView(v);
			}).start();
		}

		@Override
		public void onReadyForSpeech(Bundle params) {
			if (!textInput && me.aap.fermata.ui.view.YoldaStyle.glassActive && showGlow()) return;
			getContextMenu().show(b -> {
				// Yolda: koyu kart, animasyonlu küre, "Dinliyorum…", canlı yazı ve örnek komutlar
				Context ctx = getContext();
				DisplayMetrics dm = getResources().getDisplayMetrics();
				int min = Math.min(dm.heightPixels, dm.widthPixels);
				int size = Math.max(min / 2, toIntPx(ctx, 260));
				int imgColor = me.aap.fermata.ui.view.YoldaStyle.current(ctx).ink;
				LinearLayoutCompat layout = new LinearLayoutCompat(ctx);
				layout.setOrientation(LinearLayoutCompat.VERTICAL);
				layout.setGravity(Gravity.CENTER_HORIZONTAL);
				int pad = toIntPx(ctx, 18);
				layout.setPadding(pad, pad, pad, pad);
				me.aap.fermata.ui.view.YoldaStyle.Theme yt = me.aap.fermata.ui.view.YoldaStyle.current(ctx);
				android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
						android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
						new int[]{(yt.base & 0x00FFFFFF) | 0x99000000, (yt.base & 0x00FFFFFF) | 0xB3000000});
				bg.setCornerRadius(toIntPx(ctx, 36));
				// Liquid Glass: koyu taban (okunurluk) + berrak cam, parlak kenar
				layout.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
						bg, me.aap.fermata.ui.view.YoldaStyle.glass(ctx, yt, 36)}));

				orb = new me.aap.fermata.ui.view.VoiceOrbView(ctx);
				orb.setColors(yt.blobs[1], yt.blobs[0], yt.blobs[2]);
				int orbSize = Math.min(size - 2 * pad, min / 3);
				layout.addView(orb, new LinearLayoutCompat.LayoutParams(orbSize, orbSize));

				statusText = new android.widget.TextView(ctx);
				statusText.setText(onDevice ? "Dinliyorum…  ·  internetsiz" : "Dinliyorum…");
				statusText.setTextColor(yt.accent);
				statusText.setTextSize(15);
				statusText.setLetterSpacing(0.06f);
				statusText.setGravity(Gravity.CENTER);
				statusText.setPadding(0, toIntPx(ctx, 8), 0, toIntPx(ctx, 4));
				layout.addView(statusText, new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

				text.setMaxLines(3);
				text.setGravity(Gravity.CENTER);
				text.setTextSize(22);
				text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
				text.setEllipsize(TextUtils.TruncateAt.END);
				text.setTextColor(ColorStateList.valueOf(yt.ink));
				text.setLayoutParams(new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

				android.widget.TextView hint = new android.widget.TextView(ctx);
				hint.setText("“TRT Haber'i aç”  ·  “YouTube'da Habertürk aç”  ·  “Kral FM'i aç”  ·  “durdur”");
				hint.setTextColor(yt.muted);
				hint.setTextSize(13);
				hint.setGravity(Gravity.CENTER);
				hint.setPadding(0, toIntPx(ctx, 10), 0, 0);

				layout.setLayoutParams(new ConstraintLayout.LayoutParams(size, WRAP_CONTENT));
				b.setView(layout);
				b.setCloseHandlerHandler(m -> destroy());
				layout.addView(text);
				layout.addView(hint, new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

				if (textInput) {
					int kbSize = size / 5;
					int margin = toIntPx(getContext(), 1);
					LinearLayoutCompat.LayoutParams lp = new LinearLayoutCompat.LayoutParams(kbSize, kbSize);
					AppCompatImageView kb = new AppCompatImageView(ctx);
					lp.gravity = Gravity.CENTER;
					lp.setMargins(0, margin, 0, margin);
					kb.setLayoutParams(lp);
					kb.setImageResource(R.drawable.keyboard);
					kb.setImageTintList(ColorStateList.valueOf(imgColor));
					layout.setOnClickListener(v -> {
						promise.completeExceptionally(new OperationCanceledException());
						hideActiveMenu();
					});
					layout.addView(kb);
				}
			});
		}

		@Override
		public void onResults(Bundle b) {
			List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			if ((r != null) && !r.isEmpty()) text.setText(r.get(0));
			if (glow != null) {
				if (statusText != null) statusText.setText("ANLAŞILDI");
				postDelayed(this::removeGlow, 700);
			} else postDelayed(MainActivityDelegate.this::hideActiveMenu, 1000);
			promise.complete(r);
		}

		@Override
		public void onPartialResults(Bundle b) {
			List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			if ((r != null) && !r.isEmpty()) text.setText(r.get(0));
		}

		@Override
		public void onError(int error) {
			// Yolda: cihaz içi motorda Türkçe paketi yoksa indirmesini iste ve bu sefer normal motorla dene
			if (onDevice && !fallbackTried && (intent != null) && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
					|| error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_CLIENT
					|| error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
				fallbackTried = true;
				android.content.SharedPreferences yp = getContext().getApplicationContext().getSharedPreferences(
						me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE);
				// Paket indirmesini yalnız bir kez iste; bundan sonra doğrudan normal (internetli) tanıma
				// (önceden her seferinde "indiriliyor" penceresi çıkıyor, paket hiç gelmiyordu)
				if ((error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) && (VERSION.SDK_INT >= 33)
						&& !yp.getBoolean("ondevice_tr_requested", false)) {
					yp.edit().putBoolean("ondevice_tr_requested", true).apply();
					try {
						recognizer.triggerModelDownload(intent);
					} catch (Exception ex) {
						Log.e(ex, "Türkçe tanıma paketi indirilemedi");
					}
				}
				yp.edit().putBoolean("ondevice_tr_bad", true).apply();
				me.aap.fermata.ui.fragment.YoldaLog.add("Cihaz içi Türkçe tanıma yok (hata " + error + "): normal tanıma kullanılacak");
				recognizer.destroy();
				recognizer = createRecognizer(false);
				if (statusText != null) statusText.setText("Dinliyorum…");
				recognizer.startListening(intent);
				return;
			}
			removeGlow();
			String msg = "Speech recognition failed with error code " + error;
			Log.e(msg);
			promise.completeExceptionally(new IOException(msg));
			hideActiveMenu();

			if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
				showAlert(getContext(), R.string.err_no_audio_record_perm);
			}
		}

		@Override
		public void onBeginningOfSpeech() {
			if (statusText != null) statusText.setText("Seni duyuyorum…");
		}

		@Override
		public void onRmsChanged(float rmsdB) {
			if (orb != null) orb.setLevel((rmsdB + 2f) / 12f);
			if (glow != null) glow.setLevel((rmsdB + 2f) / 12f);
		}

		@Override
		public void onBufferReceived(byte[] buffer) {
		}

		@Override
		public void onEndOfSpeech() {
			if (statusText != null) statusText.setText("Anlıyorum…");
			if (orb != null) orb.setThinking(true);
			if (glow != null) glow.setThinking(true);
		}

		@Override
		public void onEvent(int eventType, Bundle params) {
		}
	}
}
