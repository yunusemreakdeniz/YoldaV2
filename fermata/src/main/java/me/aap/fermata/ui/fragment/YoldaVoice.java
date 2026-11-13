package me.aap.fermata.ui.fragment;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Yolda sesli komutları (Fermata'nın komutlarından önce denenir):
 * - Kanal/radyo adı: "TRT Haber'i aç", "Kral FM", "A Spor izle" → doğrudan oynatır
 * - Uygulamalar: "YouTube aç", "müzik aç", "navigasyon", "kanallar", "ana sayfa", "telefon"
 * - Navigasyon: "eve git", "Taksim'e git", "Ankara'ya yol tarifi"
 * Tanınan cümle Türkçe küçük harfe çevrilir; tanıma motoru büyük harfle döndürse de eşleşir.
 */
public final class YoldaVoice {
	private static final Locale TR = new Locale("tr");
	private static final Pattern ACTION_TAIL = Pattern.compile(
			"\\s+(aç|ac|açar mısın|izle|izlet|dinle|oynat|koy|başlat|baslat|getir|göster|goster|lütfen)$");
	private static final Pattern PLAY_SONG = Pattern.compile(
			"^(.+?)\\s+(?:şarkısını |şarkılarını |parçasını )?(?:çal|çalar mısın|çalsana|çalabilir misin)$|^(.+?)\\s+(?:şarkısını|şarkılarını|parçasını)\\s+(?:aç|dinle|oynat)$");
	private static final Pattern MUSIC_TAIL = Pattern.compile(
			"\\s+(şarkısı|şarkısını|şarkıları|şarkılarını|parçası|parçasını|albümü|albümünü)$");
	private static final Pattern OBJ_SUFFIX = Pattern.compile("['’]?(yı|yi|yu|yü|ı|i|u|ü|nı|ni|nu|nü)$");

	private static volatile List<MediaLib.PlayableItem> channels = new ArrayList<>();

	private YoldaVoice() {
	}

	/** Kanal listesini önceden yükle (ana ekran açılırken). */
	public static void preload(MainActivityDelegate a) {
		if (!AddonManager.get().hasAddon(R.id.tv_fragment)) return;
		a.getLib().getItem("TV").main().onCompletion((r, err) -> {
			if (!(r instanceof MediaLib.BrowsableItem)) return;
			((MediaLib.BrowsableItem) r).getPlayableChildren(true).main().onCompletion((list, e2) -> {
				if (list != null) channels = new ArrayList<>(list);
			});
		});
	}

	/** @return true komut burada işlendiyse. */
	public static boolean handle(MainActivityDelegate a, String raw) {
		if (raw == null) return false;
		String c = raw.trim().toLowerCase(TR).replaceAll("[.!?,]+$", "").trim();
		if (c.isEmpty()) return false;

		if (NavFragment.handleVoice(a, c)) return true;
		if (quick(a, c)) return true;

		// "<şarkı/sanatçı> çal", "… şarkısını aç": Yolda Müzik'te ara, ilk şarkıyı çal
		String song = songQuery(c);
		if (song != null) { // Yolda Müzik'te ara, ilk şarkıyı çal
			MusicFragment.playQuery(a, song);
			return true;
		}

		AddonManager am = AddonManager.get();
		String app = ACTION_TAIL.matcher(c).replaceAll("").trim();
		switch (app) {
			case "youtube music", "yutup müzik", "müzik", "muzik", "müzikler":
				a.showFragment(R.id.yolda_music);
				return true;
			case "youtube", "yutup", "yutub":
				if (am.hasAddon(R.id.youtube_fragment)) {
					a.showFragment(R.id.yolda_youtube);
					return true;
				}
				break;
			case "eczane", "eczaneler", "nöbetçi eczane", "nöbetçi eczaneler", "nöbetçi":
				a.showFragment(R.id.yolda_pharmacy_fragment);
				return true;
			case "navigasyon", "harita", "haritalar", "yol tarifi":
				a.showFragment(R.id.yolda_nav_fragment);
				return true;
			case "kanallar", "tv", "televizyon", "radyo", "radyolar":
				a.showFragment(R.id.yolda_channels_fragment);
				return true;
			case "ana sayfa", "anasayfa", "ana ekran":
				a.showFragment(R.id.home_fragment);
				return true;
			case "telefon", "telefonu kullan", "telefon ekranı":
				HomeFragment.phone(a.getContext());
				return true;
			case "menü", "menu":
				a.showFragment(R.id.yolda_menu_fragment);
				return true;
		}

		// Müzik ekranındayken söylenen her şey şarkı / sanatçı araması: "Tarkan", "Sezen Aksu dinle"
		if (a.getActiveFragment() instanceof MusicFragment) {
			String song2 = musicContextQuery(app);
			if (song2 != null) {
				MusicFragment.playQuery(a, song2);
				return true;
			}
		}

		// Kanal ya da radyo adı
		String q = norm(OBJ_SUFFIX.matcher(app).replaceAll(""));
		if (q.length() < 2) return false;
		if (channels.isEmpty() && AddonManager.get().hasAddon(R.id.tv_fragment)) {
			// Kanal listesi henüz yüklenmemiş (önceden yalnız eski ana sayfa yüklüyordu: "TRT Haber aç" boşa düşüyordu):
			// yükle, sonra aynı komutu yeniden dene
			YoldaLog.add("Sesli komut: kanal listesi yükleniyor, sonra “" + raw + "”");
			a.getLib().getItem("TV").main().onCompletion((r, err) -> {
				if (!(r instanceof MediaLib.BrowsableItem br)) {
					notUnderstood(a, raw);
					return;
				}
				br.getPlayableChildren(true).main().onCompletion((list, e2) -> {
					if ((list == null) || list.isEmpty()) {
						notUnderstood(a, raw);
						return;
					}
					channels = new ArrayList<>(list);
					if (!handle(a, raw)) notUnderstood(a, raw);
				});
			});
			return true;
		}
		MediaLib.PlayableItem best = null;
		int bestScore = 0;
		for (MediaLib.PlayableItem pi : channels) {
			String n = norm(pi.getName());
			int s;
			if (n.equals(q)) s = 100;
			else if (n.startsWith(q)) s = 80;
			else if (q.startsWith(n) && n.length() >= 3) s = 70; // "trt haber canlı" → TRT Haber
			else if (n.contains(q) && q.length() >= 3) s = 50;
			else s = 0;
			if (s > bestScore || (s == bestScore && s > 0 && best != null && n.length() < norm(best.getName()).length())) {
				best = pi;
				bestScore = s;
			}
		}
		if (best != null) {
			YoldaLog.add("Sesli komut → kanal: " + best.getName());
			a.getBody().playItem(best);
			if (!best.isVideo()) me.aap.fermata.ui.view.YoldaNowPlaying.show(a);
			return true;
		}
		// Listede olmayan radyo: internetteki radyo dizininde ara, ilkini çal ("Kral FM aç", "Joy Türk radyosu")
		String radio = radioQuery(c);
		if (radio != null) {
			me.aap.fermata.ui.view.YoldaStyle.toast(a.getContext(), "“" + radio + "” radyosu aranıyor…");
			me.aap.fermata.media.radio.RadioBrowser.search(radio).main().onCompletion((l, err) -> {
				if ((l == null) || l.isEmpty()) {
					me.aap.fermata.ui.view.YoldaStyle.toast(a.getContext(), "“" + radio + "” radyosu bulunamadı");
					return;
				}
				var s = l.get(0);
				a.getMediaServiceBinder().playItem(me.aap.fermata.media.radio.YoldaStreamItem.create(a.getLib(), s.url(),
						s.name(), s.logo(), "Radyo"));
				me.aap.fermata.ui.view.YoldaNowPlaying.show(a);
			});
			return true;
		}
		return false;
	}

	/** Komut anlaşılamadı: kullanıcı beklemede kalmasın, kısa yazıyla söylenir. */
	public static void notUnderstood(MainActivityDelegate a, String raw) {
		YoldaLog.add("Sesli komut anlaşılamadı: “" + raw + "”");
		me.aap.fermata.ui.view.YoldaStyle.toast(a.getContext(), "“" + raw + "” anlaşılamadı");
	}

	private static final Pattern RADIO_WORDS = Pattern.compile(
			// Android UNICODE_CHARACTER_CLASS'ı desteklemiyor (sınıf açılırken çöküyordu): harf sınırı elle
			"(?<![\\p{L}\\p{N}])(radyosunu|radyosu|radyo|aç|ac|çal|cal|dinle|dinlet|açar mısın|lütfen|istasyonu)(?![\\p{L}\\p{N}])");

	/** "Kral FM aç", "Joy Türk radyosu", "radyo Metro FM" → aranacak istasyon adı; radyo isteği değilse null. */
	static String radioQuery(String c) {
		if (c == null) return null;
		boolean radio = c.contains("radyo") || c.matches(".*\\bfm\\b.*");
		if (!radio) return null;
		String q = RADIO_WORDS.matcher(c).replaceAll(" ").replaceAll("\\s+", " ").trim();
		return (q.length() >= 2) ? q : null;
	}

	/** Kısa komutlar: ses, saat, tema, oynatma. */
	private static boolean quick(MainActivityDelegate a, String c) {
		android.content.Context ctx = a.getContext();
		android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(android.content.Context.AUDIO_SERVICE);
		if (c.matches("(sesi |ses )?(aç|yükselt|artır|arttır|yükseltir misin)") || c.matches("ses(i)? (aç|yükselt|artır)")
				|| c.equals("daha yüksek") || c.equals("sesi biraz aç")) {
			a.getMediaSessionCallback().yoldaUnmute("sesli komut");
			for (int i = 0; i < 2; i++)
				am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE,
						android.media.AudioManager.FLAG_SHOW_UI);
			return true;
		}
		if (c.matches("ses(i)? (kıs|azalt|düşür)") || c.equals("daha kısık") || c.equals("sesi biraz kıs")) {
			for (int i = 0; i < 2; i++)
				am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER,
						android.media.AudioManager.FLAG_SHOW_UI);
			return true;
		}
		if (c.matches("(sesi )?(kapat|sustur)|sessiz(e al)?")) {
			am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE,
					android.media.AudioManager.FLAG_SHOW_UI);
			return true;
		}
		if (c.matches("saat kaç.*|saat ne.*")) {
			me.aap.fermata.ui.view.YoldaStyle.toast(ctx, "Saat " + new java.text.SimpleDateFormat("HH:mm", TR).format(new java.util.Date()));
			return true;
		}
		if (c.matches("tema(yı)? değiştir|renkleri değiştir|başka tema")) {
			me.aap.fermata.ui.view.YoldaStyle.next(ctx);
			return true;
		}
		var cb = a.getMediaSessionCallback();
		if (c.matches("(duraklat|durdur|bekle|dur)")) {
			cb.onPause();
			return true;
		}
		if (c.matches("(devam et|devam|oynat|başlat)")) {
			cb.onPlay();
			return true;
		}
		if (c.matches("(sonraki|ileri|sıradaki|geç|sonraki şarkı|sonraki kanal)")) {
			cb.onSkipToNext();
			return true;
		}
		if (c.matches("(önceki|geri|önceki şarkı|önceki kanal)")) {
			cb.onSkipToPrevious();
			return true;
		}
		if (c.matches("(bunu |bu şarkıyı |bu kanalı |bu radyoyu )?favori(lere|ye|lerime)? ekle|favorile")) {
			String[] e = YoldaFavs.entryOf(a.getCurrentPlayable());
			if (e == null) {
				me.aap.fermata.ui.view.YoldaStyle.toast(ctx, "Çalan bir şey yok ya da favorilere eklenemiyor");
			} else {
				YoldaFavs.add(ctx, e[0], e[1], e[2], e[3]);
				me.aap.fermata.ui.view.YoldaStyle.toast(ctx, "★ Favorilere eklendi: " + e[1]);
			}
			return true;
		}
		if (c.matches("(ne çalıyor|şimdi ne çalıyor|bu ne şarkı|bu şarkı ne)")) {
			me.aap.fermata.ui.view.YoldaNowPlaying.show(a);
			return true;
		}
		return false;
	}

	/**
	 * "Tarkan çal", "Sezen Aksu şarkısını aç" → çalınacak arama; komut şarkı isteği değilse null.
	 * "müzik çal" gibi genel istek Türkçe pop'a döner.
	 */
	static String songQuery(String cmd) {
		if (cmd == null) return null;
		java.util.regex.Matcher m = PLAY_SONG.matcher(cmd.trim().toLowerCase(TR));
		if (!m.matches()) return null;
		String q = ((m.group(1) != null) ? m.group(1) : m.group(2)).trim();
		if (q.isEmpty()) return null;
		if (q.matches("(bir )?(müzik|muzik|şarkı|sarki|şarkılar)")) q = "Türkçe pop";
		return q;
	}

	/** Müzik ekranındayken: "Duman şarkılarını" → "duman"; boşsa null. */
	static String musicContextQuery(String app) {
		if (app == null) return null;
		String s = MUSIC_TAIL.matcher(app.trim()).replaceAll("").trim();
		return s.isEmpty() ? null : s;
	}

	static String norm(String s) {
		if (s == null) return "";
		String l = s.toLowerCase(TR).replace('ı', 'i');
		return Normalizer.normalize(l, Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "").replaceAll("[^a-z0-9]", "");
	}
}
