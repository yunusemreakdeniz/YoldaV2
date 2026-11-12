package me.aap.fermata.ui.activity;

import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.Nullable;

import me.aap.utils.ui.activity.AppActivity;

/**
 * @author Andrey Pavlenko
 */
public interface FermataActivity extends AppActivity {


	boolean isCarActivity();

	void setRequestedOrientation(int requestedOrientation);

	@Nullable
	default EditText startInput(TextWatcher w) {
		return null;
	}

	default void stopInput() {
	}

	default boolean isInputActive() {
		return false;
	}

	default boolean setTextInput(String text) {
		return false;
	}

	/** Yolda: telefonun ekranını araç ekranında, Yolda'nın içinde göster (yalnız araçta). */
	default boolean yoldaShowMirror() {
		return false;
	}

	/** Yolda: telefon ekranı paneli açık mı. */
	default boolean yoldaMirrorShown() {
		return false;
	}

	/** Yolda: telefon ekranı panelini kapat; açık değilse false. */
	default boolean yoldaCloseMirror() {
		return false;
	}
}
