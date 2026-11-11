package me.aap.fermata.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.view.GestureListener;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public class FermataNavBarView extends NavBarView implements GestureListener {
	private final GestureDetectorCompat gestureDetector;

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		gestureDetector = new GestureDetectorCompat(context, this);
	}

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		gestureDetector = new GestureDetectorCompat(context, this);
	}

	@Override
	public void setSize(float scale) {
		super.setSize(scale);
		applyYoldaPill();
	}

	/**
	 * Yolda Cam: alt menü ekranın kenarına yapışık blok değil, havada duran oval cam kapsül
	 * (en çok 640dp genişlikte, ortalı, alttan 14dp yukarıda).
	 */
	public void applyYoldaPill() {
		if (YoldaStyle.glassActive && isLeft()) {
			applyYoldaRail();
			return;
		}
		if (!YoldaStyle.glassActive || !isBottom()) return;
		if (!(getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams lp)) return;
		float d = getResources().getDisplayMetrics().density;
		int screenW = Math.round(getResources().getConfiguration().screenWidthDp * d);
		int maxW = Math.round(640 * d);
		int side = Math.max(Math.round(16 * d), (screenW - maxW) / 2);
		lp.width = 0; // ConstraintLayout: soldan sağa kısıtlara göre, kenar boşluklarıyla
		lp.height = pillHeight(); // kalın kapsül
		lp.leftMargin = side;
		lp.rightMargin = side;
		lp.bottomMargin = Math.round(14 * d);
		setLayoutParams(lp);
		setBackground(YoldaStyle.pill(getContext(), YoldaStyle.current(getContext())));
		setPadding(Math.round(12 * d), Math.round(8 * d), Math.round(12 * d), Math.round(8 * d));
		setClipToOutline(true);
		// Yalnız simge; seçili sekme içeride parlayan cam hap
		me.aap.utils.ui.view.NavButtonView.iconsOnly = true;
		me.aap.utils.ui.view.NavButtonView.selectedBackground = c -> YoldaStyle.centeredHighlight(c, 76, 52, 999);
		for (int i = 0; i < getChildCount(); i++) {
			android.view.View b = getChildAt(i);
			b.setSelected(b.isSelected());
		}
	}

	/**
	 * Döndürmede uygulama yeniden başlamıyor; Fermata düğmeleri baştan kuruyor. Kapsül kenar boşlukları
	 * yeni genişliğe göre, yeni düğmeler de tek tip simge boyuna göre yeniden ayarlanır; zemin yenilenir.
	 */
	@Override
	protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (!YoldaStyle.glassActive) return;
		post(() -> {
			applyYoldaPill();
			YoldaStyle.refreshBackdrop(getContext());
		});
	}

	/** Sonradan eklenen her düğme de hemen cam kapsül biçimini alsın (seçili olmasa bile aynı boy). */
	@Override
	public void onViewAdded(android.view.View child) {
		super.onViewAdded(child);
		if (YoldaStyle.glassActive) child.setSelected(child.isSelected());
	}

	/** Araç: solda dikey, yuvarlak cam şerit; yalnız simgeler. */
	private void applyYoldaRail() {
		if (!(getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams lp)) return;
		float d = getResources().getDisplayMetrics().density;
		YoldaStyle.railMode = true;
		lp.width = railWidth();
		lp.leftMargin = Math.round(8 * d);
		lp.topMargin = Math.round(8 * d);
		lp.bottomMargin = Math.round(8 * d);
		setLayoutParams(lp);
		setBackground(YoldaStyle.pill(getContext(), YoldaStyle.current(getContext())));
		// altta sesli asistan düğmesine (48dp) yer
		setPadding(Math.round(4 * d), Math.round(8 * d), Math.round(4 * d), Math.round(68 * d));
		setClipToOutline(true);
		me.aap.utils.ui.view.NavButtonView.iconsOnly = true;
		me.aap.utils.ui.view.NavButtonView.iconSizeDp = 26;
		me.aap.utils.ui.view.NavButtonView.selectedBackground = c -> YoldaStyle.centeredHighlight(c, 48, 48, 16);
		for (int i = 0; i < getChildCount(); i++) {
			android.view.View b = getChildAt(i);
			b.setSelected(b.isSelected());
		}
	}

	private int railWidth() {
		return Math.round(64 * getResources().getDisplayMetrics().density);
	}

	private int pillHeight() {
		return Math.round(72 * getResources().getDisplayMetrics().density);
	}

	/**
	 * Fermata yüksekliği bazen doğrudan eski (ince) değere çekiyor; o zaman simgelere yer kalmıyor ve
	 * kayboluyorlardı. Cam kapsülde yükseklik her ölçümde sabit.
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (YoldaStyle.glassActive && isBottom()) {
			android.view.ViewGroup.LayoutParams lp = getLayoutParams();
			if ((lp != null) && (lp.height != pillHeight())) lp.height = pillHeight();
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(pillHeight(), MeasureSpec.EXACTLY);
		} else if (YoldaStyle.glassActive && isLeft()) {
			android.view.ViewGroup.LayoutParams lp = getLayoutParams();
			if ((lp != null) && (lp.width != railWidth())) lp.width = railWidth();
			widthMeasureSpec = MeasureSpec.makeMeasureSpec(railWidth(), MeasureSpec.EXACTLY);
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	protected boolean interceptTouchEvent(MotionEvent e) {
		gestureDetector.onTouchEvent(e);
		return super.onTouchEvent(e);
	}

	@Override
	protected MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeLeft(e1, e2);
	}

	@Override
	public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeRight(e1, e2);
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		return getMainActivity().getControlPanel().onScroll(e1, e2, distanceX, distanceY);
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
