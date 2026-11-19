package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.YoldaStyle;

/**
 * Notlar sayfası (Notlar kartına dokununca): sıradaki hatırlatmalar (ne zaman, iptal), notlar (dokun: oku, Sil),
 * yazarak ya da sesle not ekle ("Hey Yolda, not al: …"), "Notları oku", "Tümünü sil" (iki dokunuş).
 */
public class NotesFragment extends MainActivityFragment {
	private FrameLayout root;
	private YoldaStyle.Theme t;
	private long clearAskedAt;

	@Override
	public int getFragmentId() {
		return R.id.yolda_notes_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return "Notlar";
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

	private void render() {
		if ((root == null) || !isAdded()) return;
		Context ctx = requireContext();
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
		col.addView(text(ctx, "Notlar", YoldaStyle.titleSp(ctx), true, t.ink));
		TextView hint = text(ctx, "Sesle: “not al: lastikleri kontrol et”, “notlarımı oku”, “10 dakika sonra su içmeyi hatırlat”", 14,
				false, t.muted);
		hint.setPadding(0, dp(4), 0, dp(8));
		col.addView(hint);

		// Yazarak not ekle
		LinearLayout add = new LinearLayout(ctx);
		add.setGravity(Gravity.CENTER_VERTICAL);
		EditText in = getActivityDelegate().createEditText(ctx);
		in.setSingleLine();
		in.setHint("Not yaz…");
		in.setTextColor(t.ink);
		in.setHintTextColor(t.muted);
		in.setImeOptions(EditorInfo.IME_ACTION_DONE);
		in.setBackground(YoldaStyle.glass(ctx, t, 16));
		in.setPadding(dp(16), 0, dp(16), 0);
		Runnable save = () -> {
			String s = in.getText().toString().trim();
			if (s.isEmpty()) return;
			YoldaNotes.add(ctx, s);
			YoldaStyle.toast(ctx, "Not kaydedildi");
			render();
		};
		in.setOnEditorActionListener((v, id, ev) -> {
			save.run();
			return true;
		});
		add.addView(in, new LinearLayout.LayoutParams(0, dp(56), 1f));
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, dp(56));
		bl.leftMargin = dp(8);
		add.addView(button(ctx, "Ekle", v -> save.run()), bl);
		LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, dp(56));
		ml.leftMargin = dp(8);
		add.addView(button(ctx, "🎤", v -> getActivityDelegate().startVoiceAssistant()), ml);
		col.addView(add);

		// Hatırlatmalar
		List<YoldaNotes.Reminder> rl = YoldaNotes.reminders(ctx);
		long now = System.currentTimeMillis();
		if (!rl.isEmpty()) {
			TextView rh = text(ctx, "Hatırlatmalar", 18, true, t.ink);
			rh.setPadding(0, dp(24), 0, dp(4));
			col.addView(rh);
			SimpleDateFormat hf = new SimpleDateFormat("HH:mm", new Locale("tr"));
			for (YoldaNotes.Reminder r : rl) {
				col.addView(row(ctx, "⏰ " + hf.format(new Date(r.due())) + " · " + YoldaNotes.left(r.due(), now), r.text(), "İptal", v -> {
					YoldaNotes.removeReminder(ctx, r.due(), r.text());
					YoldaStyle.toast(ctx, "Hatırlatma silindi (bu oturumda yine de çalabilir)");
					render();
				}, () -> YoldaSpeak.sayAlways(ctx, r.text())), rowLp());
			}
		}

		// Notlar
		List<YoldaNotes.Note> nl = YoldaNotes.notes(ctx);
		TextView nh = text(ctx, nl.isEmpty() ? "Henüz not yok" : ("Notlar (" + nl.size() + ")"), 18, true, t.ink);
		nh.setPadding(0, dp(24), 0, dp(4));
		col.addView(nh);
		SimpleDateFormat df = new SimpleDateFormat("d MMM HH:mm", new Locale("tr"));
		for (YoldaNotes.Note n : nl) {
			col.addView(row(ctx, df.format(new Date(n.at())), n.text(), "Sil", v -> {
				YoldaNotes.remove(ctx, n.at());
				render();
			}, () -> YoldaSpeak.sayAlways(ctx, n.text())), rowLp());
		}
		if (!nl.isEmpty()) {
			LinearLayout acts = new LinearLayout(ctx);
			LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(52));
			al.topMargin = dp(16);
			acts.addView(button(ctx, "🔊 Notları oku", v -> {
				if (YoldaSpeak.isSpeaking()) YoldaSpeak.stop();
				else YoldaSpeak.sayAlways(ctx, YoldaNotes.spoken(nl, 10));
			}), new LinearLayout.LayoutParams(0, -1, 1f));
			boolean sure = System.currentTimeMillis() - clearAskedAt < 4000;
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
			cl.leftMargin = dp(8);
			acts.addView(button(ctx, sure ? "Emin misin? Tekrar dokun" : "Tümünü sil", v -> {
				if (System.currentTimeMillis() - clearAskedAt < 4000) {
					clearAskedAt = 0;
					YoldaNotes.clear(ctx);
				} else {
					clearAskedAt = System.currentTimeMillis();
				}
				render();
			}), cl);
			col.addView(acts, al);
		}
	}

	private View row(Context ctx, String top, String body, String act, View.OnClickListener onAct, Runnable onTap) {
		LinearLayout r = new LinearLayout(ctx);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setBackground(YoldaStyle.glass(ctx, t, 16));
		r.setPadding(dp(16), dp(8), dp(8), dp(8));
		r.setMinimumHeight(dp(64));
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.addView(text(ctx, top, 12, false, t.muted));
		TextView b = text(ctx, body, 16, true, t.ink);
		b.setMaxLines(3);
		b.setEllipsize(TextUtils.TruncateAt.END);
		tx.addView(b);
		r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
		r.addView(button(ctx, act, onAct), new LinearLayout.LayoutParams(-2, dp(48)));
		r.setClickable(true);
		r.setContentDescription(body);
		r.setOnClickListener(v -> onTap.run());
		return r;
	}

	private LinearLayout.LayoutParams rowLp() {
		LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
		l.topMargin = dp(8);
		return l;
	}

	private TextView button(Context ctx, String label, View.OnClickListener click) {
		TextView b = text(ctx, label, 15, true, t.ink);
		b.setGravity(Gravity.CENTER);
		b.setSingleLine(true);
		b.setMinWidth(dp(56));
		b.setBackground(YoldaStyle.glass(ctx, t, 999));
		b.setPadding(dp(16), 0, dp(16), 0);
		b.setClickable(true);
		b.setOnClickListener(click);
		b.setContentDescription("🎤".equals(label) ? "Sesle not al" : label);
		return b;
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
