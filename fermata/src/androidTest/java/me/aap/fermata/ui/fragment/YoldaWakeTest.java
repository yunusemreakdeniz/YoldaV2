package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/**
 * "Hey Yolda": APK'daki Türkçe ses modeli açılır, uyandırma sözcükleri modelin sözlüğünde vardır ve
 * sentetik "hey yolda" sesi (CI'da espeak-ng ile üretilir) uyandırma olarak tanınır.
 */
@RunWith(AndroidJUnit4.class)
public class YoldaWakeTest {
	private static final String TAG = "YoldaTest";
	private static Model model;

	@org.junit.Rule
	public final org.junit.rules.Timeout timeout = org.junit.rules.Timeout.seconds(240);

	private static Context ctx() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	private static synchronized Model model() throws Exception {
		Assume.assumeTrue("APK'da ses modeli yok", YoldaWake.hasBundledModel(ctx()));
		if (model == null) {
			File dir = YoldaWake.prepareModelSync(ctx());
			assertTrue("Model açılmalı", YoldaWake.isModelReady(ctx()));
			model = new Model(dir.getAbsolutePath());
		}
		return model;
	}

	@Test
	public void varsayilanAcik() {
		ctx().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
				.remove("wake_enabled").commit();
		assertTrue("Hey Yolda varsayılan açık olmalı", YoldaWake.isEnabled(ctx()));
	}

	@Test
	public void uyandirmaSozcukleriSozlukte() throws Exception {
		Model m = model();
		int found = 0;
		for (String w : new String[]{"yolda", "yola", "hey", "ey"}) {
			int id;
			try {
				id = (int) Model.class.getMethod("findWord", String.class).invoke(m, w);
			} catch (NoSuchMethodException ex) {
				Assume.assumeNoException(ex);
				return;
			}
			Log.i(TAG, "Vosk sözlüğü: " + w + " → " + id);
			if ((id >= 0) && (w.startsWith("yol"))) found++;
		}
		assertTrue("“yolda” ya da “yola” modelin sözlüğünde olmalı", found > 0);
	}

	@Test
	public void sentetikHeyYoldaTaninir() throws Exception {
		Model m = model();
		Context tctx = InstrumentationRegistry.getInstrumentation().getContext();
		int tried = 0, woke = 0;
		for (String f : new String[]{"hey_yolda.wav", "hey_yolda_2.wav", "yolda.wav"}) {
			byte[] wav;
			try (InputStream in = tctx.getAssets().open(f)) {
				wav = read(in);
			} catch (Exception ex) {
				continue;
			}
			tried++;
			try (Recognizer r = new Recognizer(m, 16000.0f, YoldaWake.GRAMMAR)) {
				StringBuilder all = new StringBuilder();
				int off = 44; // WAV başlığı
				while (off < wav.length) {
					int n = Math.min(3200, wav.length - off);
					byte[] chunk = new byte[n];
					System.arraycopy(wav, off, chunk, 0, n);
					if (r.acceptWaveForm(chunk, n)) all.append(r.getResult());
					else all.append(r.getPartialResult());
					off += n;
				}
				all.append(r.getFinalResult());
				boolean w = YoldaWake.isWake(all.toString());
				Log.i(TAG, "Vosk " + f + ": " + all.toString().replaceAll("\\s+", " ") + " → uyandırma=" + w);
				if (w) woke++;
			}
		}
		Assume.assumeTrue("Sentetik ses dosyası yok", tried > 0);
		assertTrue("Sentetik “hey yolda” en az bir kayıtta tanınmalı", woke > 0);
	}

	private static byte[] read(InputStream in) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] b = new byte[16384];
		for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
		return out.toByteArray();
	}
}
