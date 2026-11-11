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
		if (!YoldaStyle.glassActive || !isBottom()) return;
		if (!(getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams lp)) return;
		float d = getResources().getDisplayMetrics().density;
		int screenW = Math.round(getResources().getConfiguration().screenWidthDp * d);
		int maxW = Math.round(640 * d);
		int side = Math.max(Math.round(16 * d), (screenW - maxW) / 2);
		lp.width = 0; // ConstraintLayout: soldan sağa kısıtlara göre, kenar boşluklarıyla
		lp.leftMargin = side;
		lp.rightMargin = side;
		lp.bottomMargin = Math.round(14 * d);
		setLayoutParams(lp);
		setBackground(YoldaStyle.pill(getContext(), YoldaStyle.current(getContext())));
		setPadding(Math.round(8 * d), 0, Math.round(8 * d), 0);
		setClipToOutline(true);
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
