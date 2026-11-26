package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.ui.UiUtils;

/**
 * Xtream Codes kaynağı (Mola'dan): sunucu, kullanıcı adı ve şifreyle IPTV sağlayıcısının kanal listesi (M3U)
 * ve yayın akışı (XMLTV) adresleri kurulur, TV'ye kaynak olarak eklenir. Bilgiler yalnız telefonda, TV
 * kaynağının ayarında saklanır.
 */
public final class YoldaXtream {
	/** TV eklentisi kurulunca atanır (eklenti bu modüle bağlı olduğundan ters yönde doğrudan çağrı yok). */
	public interface Adder {
		void add(String name, String m3uUrl, String epgUrl, java.util.function.Consumer<Boolean> done);
	}

	@Nullable
	public static volatile Adder adder;

	private YoldaXtream() {
	}

	/** "iptv.ornek.com:8080/" → "http://iptv.ornek.com:8080" (şema yoksa http, sondaki / atılır). */
	static String server(String s) {
		String v = s.trim();
		while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
		if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://" + v;
		return v;
	}

	static String m3uUrl(String server, String user, String pass) {
		return server(server) + "/get.php?username=" + Uri.encode(user.trim()) + "&password=" + Uri.encode(pass.trim())
				+ "&type=m3u_plus&output=ts";
	}

	static String epgUrl(String server, String user, String pass) {
		return server(server) + "/xmltv.php?username=" + Uri.encode(user.trim()) + "&password=" + Uri.encode(pass.trim());
	}

	/** Menüden: sunucu → kullanıcı → şifre sorulur, kaynak eklenir. */
	public static void ask(MainActivityDelegate a) {
		Context ctx = a.getContext();
		Adder ad = adder;
		if (ad == null) {
			YoldaStyle.toast(ctx, "TV eklentisi hazır değil: önce TV ekranını bir kez aç");
			return;
		}
		UiUtils.queryText(ctx, R.string.yolda_xtream_server, R.drawable.yi_tv).onSuccess(srv -> {
			if ((srv == null) || srv.trim().isEmpty()) return;
			UiUtils.queryText(ctx, R.string.yolda_xtream_user, R.drawable.yi_edit).onSuccess(user -> {
				if ((user == null) || user.trim().isEmpty()) return;
				UiUtils.queryText(ctx, R.string.yolda_xtream_pass, R.drawable.yi_edit).onSuccess(pass -> {
					if ((pass == null) || pass.isEmpty()) return;
					String host = Uri.parse(server(srv)).getHost();
					String name = "Xtream · " + ((host != null) ? host : "IPTV");
					YoldaLog.add("Xtream kaynağı ekleniyor: " + host); // kullanıcı / şifre günlüğe yazılmaz
					ad.add(name, m3uUrl(srv, user, pass), epgUrl(srv, user, pass), ok -> YoldaStyle.toast(ctx,
							ok ? (name + " eklendi; kanallar TV'de") : "Xtream listesi alınamadı: sunucu, kullanıcı ya da şifre yanlış olabilir"));
				});
			});
		});
	}
}
