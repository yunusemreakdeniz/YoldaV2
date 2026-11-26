package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Bütün yazı kutularında klavye + sesli giriş: kutunun sağ ucunda mikrofon. Mikrofona dokununca konuşma tanıma
 * açılır, söylenen kutuya yazılır ve kutunun eylemi (Ara / Git / Tamam) çalıştırılır; kutuya dokununca klavye
 * açılır (her zamanki gibi). Kendi mikrofon düğmesi olan kutular {@link #skip} ile hariç tutulur.
 */
public final class YoldaSearchMic {
	private static final Map<EditText, Boolean> SKIP = Collections.synchronizedMap(new WeakHashMap<>());

	private YoldaSearchMic() {
	}

	/** Bu kutuya mikrofon eklenmesin (yanında ayrı mikrofon düğmesi var). */
	public static void skip(EditText e) {
		SKIP.put(e, Boolean.TRUE);
		Drawable[] d = e.getCompoundDrawablesRelative();
		if (d[2] != null) e.setCompoundDrawablesRelative(d[0], d[1], null, d[3]);
	}

	/**
	 * Mikrofona dokunuldu mu: dokunuş kutunun sağ ucundaki en az 48dp'lik alanda (simge ve iç boşluğu).
	 * {@code endPad}: sağ iç boşluk, {@code icon}: simge genişliği.
	 */
	static boolean hitsMic(float x, int width, int endPad, int icon, float density) {
		int area = Math.max(Math.round(48 * density), endPad + icon + Math.round(12 * density));
		return x >= width - area;
	}

	/** Kutu ekrana gelince (çağıran başlangıç simgesini koyduktan sonra) mikrofonu sağ uca ekler. */
	@SuppressLint("ClickableViewAccessibility")
	public static void attach(MainActivityDelegate a, EditText e) {
		e.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			@Override
			public void onViewAttachedToWindow(View v) {
				if (SKIP.containsKey(e)) return;
				Drawable[] d = e.getCompoundDrawablesRelative();
				if (d[2] != null) return; // başka bir simge var
				Drawable mic = e.getContext().getDrawable(R.drawable.yi_mic);
				if (mic == null) return;
				int s = Math.round(22 * e.getResources().getDisplayMetrics().density);
				mic.mutate();
				mic.setBounds(0, 0, s, s);
				mic.setTintList(ColorStateList.valueOf(e.getCurrentHintTextColor() | 0xFF000000));
				e.setCompoundDrawablesRelative(d[0], d[1], mic, d[3]);
				if (e.getCompoundDrawablePadding() == 0) e.setCompoundDrawablePadding(s / 2);
				CharSequence cd = e.getContentDescription();
				if (cd == null) e.setContentDescription((e.getHint() != null ? e.getHint() + ". " : "") + "Sağdaki mikrofonla sesle yaz");
			}

			@Override
			public void onViewDetachedFromWindow(View v) {
			}
		});
		boolean car = a.isCarActivity();
		if (car) e.setShowSoftInputOnFocus(false); // araçta telefon klavyesi açılmaz: kendi klavyemiz
		e.setOnTouchListener((v, ev) -> {
			if (ev.getAction() != MotionEvent.ACTION_UP) return false;
			Drawable end = SKIP.containsKey(e) ? null : e.getCompoundDrawablesRelative()[2];
			if (end != null) {
				boolean rtl = e.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
				float x = rtl ? (e.getWidth() - ev.getX()) : ev.getX();
				if (hitsMic(x, e.getWidth(), e.getPaddingEnd(), end.getBounds().width(), e.getResources().getDisplayMetrics().density)) {
					dictate(a, e);
					return true;
				}
			}
			if (car) { // araç klavyesi (dururken); sürüşte sesle yazma
				YoldaCarKeyboard.onTap(a, e);
				return true;
			}
			return false;
		});
	}

	enum Edit {TEXT, CLEAR, CANCEL, SUBMIT}

	/** Sesle yazarken kısa düzenleme sözleri. */
	static Edit spokenEdit(String s) {
		String t = s.trim().toLowerCase(new java.util.Locale("tr")).replaceAll("[.!?]+$", "");
		return switch (t) {
			case "temizle", "sil", "hepsini sil" -> Edit.CLEAR;
			case "vazgeç", "iptal", "boş ver" -> Edit.CANCEL;
			case "ara", "aramayı başlat", "tamam" -> Edit.SUBMIT;
			default -> Edit.TEXT;
		};
	}

	/** Konuşma tanıma: söyleneni kutuya yaz, imleci sona al, kutunun eylemini çalıştır. */
	public static void dictate(MainActivityDelegate a, EditText e) {
		a.startSpeechRecognizer().onSuccess(q -> {
			if ((q == null) || q.isEmpty()) return;
			String s = q.get(0);
			// Hey Yolda ile aynı tanıma; yazarken kısa sözler: "temizle", "vazgeç", "ara"
			switch (spokenEdit(s)) {
				case CLEAR -> {
					e.setText("");
					return;
				}
				case CANCEL -> {
					return;
				}
				case SUBMIT -> {
					YoldaCarKeyboard.hide();
					e.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
					return;
				}
				default -> {
				}
			}
			e.setText(s);
			e.setSelection(s.length());
			int act = e.getImeOptions() & EditorInfo.IME_MASK_ACTION;
			if ((act == EditorInfo.IME_ACTION_SEARCH) || (act == EditorInfo.IME_ACTION_GO) || (act == EditorInfo.IME_ACTION_DONE)
					|| (act == EditorInfo.IME_ACTION_SEND))
				e.onEditorAction(act);
		});
	}
}
