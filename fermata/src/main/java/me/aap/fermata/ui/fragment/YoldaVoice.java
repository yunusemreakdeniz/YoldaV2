package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.yt.YtMusic;
import me.aap.fermata.media.yt.YtMusicItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.YoldaIntents.Intent;
import me.aap.fermata.ui.fragment.YoldaIntents.Kind;
import me.aap.fermata.ui.view.YoldaNowPlaying;
import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;

/**
 * Hey Yolda sesli komutları (Fermata'nın komutlarından önce denenir). Cümle {@link YoldaIntents} ile niyete
 * çevrilir, burada yürütülür; sonuç kısa bir yazıyla gösterilir ve {@link YoldaSpeak} ile söylenir.
 * Tanıma motorunun bütün tahminleri denenir: ilki anlaşılmasa da ikincisi ("tirete haber" / "TRT Haber") tutabilir.
 */
public final class YoldaVoice {
	private static final Locale TR = new Locale("tr");
	private static final Pattern PLAY_SONG = Pattern.compile(
			"^(.+?)\\s+(?:şarkısını |şarkılarını |parçasını )?(?:çal|çalar mısın|çalsana|çalabilir misin)$|^(.+?)\\s+(?:şarkısını|şarkılarını|parçasını)\\s+(?:aç|dinle|oynat)$");
	private static final Pattern MUSIC_TAIL = Pattern.compile(
			"\\s+(şarkısı|şarkısını|şarkıları|şarkılarını|parçası|parçasını|albümü|albümünü)$");
	/** Kanal adı en az bu kadar tutmalı (50: ad içinde geçiyor; 55-60: küçük telaffuz farkı). */
	static final int MIN_CHANNEL_SCORE = 50;

	private static volatile List<MediaLib.PlayableItem> channels = new ArrayList<>();
	private static Intent lastIntent;
	private static Runnable sleepTask;
	/** Uyku zamanlayıcısının biteceği an (elapsedRealtime); kapalıysa 0. */
	public static volatile long sleepAt;

	private YoldaVoice() {
	}

	/** Kanal listesini önceden yükle (ana ekran açılırken). */
	public static void preload(MainActivityDelegate a) {
		YoldaSpeak.warmUp(a.getContext());
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
		return (raw != null) && handle(a, Collections.singletonList(raw));
	}

	/**
	 * Tanıma motorunun tahminleri (en olası önce): önce kanal olmayan bir komut aranır (ilk tutan), sonra
	 * bütün tahminler arasında en iyi tutan kanal adı.
	 *
	 * @return true komut burada işlendiyse.
	 */
	public static boolean handle(MainActivityDelegate a, List<String> alts) {
		if ((alts == null) || alts.isEmpty()) return false;
		List<String> raws = new ArrayList<>(alts.size());
		for (String s : alts) if ((s != null) && !s.trim().isEmpty() && (raws.size() < 5)) raws.add(s.trim());
		if (raws.isEmpty()) return false;

		// En olası tahmin bir komutsa ("eve git", "sesi kıs") doğrudan
		Intent first = YoldaIntents.parse(raws.get(0));
		if ((first != null) && (first.kind() != Kind.CHANNEL)) return run(a, first, raws.get(0));

		List<Intent> chans = new ArrayList<>();
		Intent other = null;
		String otherRaw = null;
		for (String r : raws) {
			Intent it = YoldaIntents.parse(r);
			if (it == null) continue;
			if (it.kind() == Kind.CHANNEL) chans.add(it);
			else if (other == null) {
				other = it;
				otherRaw = r;
			}
		}
		// Kanal adı tutuyorsa o; tutmuyorsa sonraki tahminlerdeki komut
		if (!chans.isEmpty() && !channels.isEmpty()) {
			List<String> names = new ArrayList<>(chans.size());
			for (Intent i : chans) names.add(i.arg());
			if ((bestChannel(names) == null) && (other != null)) return run(a, other, otherRaw);
		} else if (chans.isEmpty()) {
			return (other != null) && run(a, other, otherRaw);
		}
		return channel(a, chans, raws.get(0));
	}

	/** Niyeti yürüt. */
	static boolean run(MainActivityDelegate a, Intent it, String raw) {
		YoldaLog.add("Sesli komut “" + raw + "” → " + it.kind() + ((it.arg() != null) ? (" " + it.arg()) : "")
				+ ((it.num() != 0) ? (" " + it.num()) : ""));
		Context ctx = a.getContext();
		var cb = a.getMediaSessionCallback();
		AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
		if ((it.kind() != Kind.REPEAT) && (it.kind() != Kind.HELP)) lastIntent = it;

		switch (it.kind()) {
			case HELP -> reply(a, "Örneğin: TRT Haber aç, Tarkan çal, eve git, en yakın benzinlik, sesi yüzde elli yap, "
					+ "otuz dakika sonra kapat, hava nasıl, ne çalıyor.");
			case REPEAT -> {
				Intent l = lastIntent;
				if (l == null) reply(a, "Tekrarlanacak bir komut yok");
				else return run(a, l, raw);
			}
			case PAUSE -> cb.onPause();
			case PLAY, RESUME -> resume(a);
			case STOP -> {
				var cp = a.getControlPanel();
				if (cp != null) cp.closePlayer();
				else cb.onStop();
			}
			case NEXT -> cb.onSkipToNext();
			case PREV -> cb.onSkipToPrevious();
			case VOL_UP -> {
				cb.yoldaUnmute("sesli komut");
				am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
				for (int i = 0; i < 2; i++)
					am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
			}
			case VOL_DOWN -> {
				for (int i = 0; i < 2; i++)
					am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
			}
			case MUTE -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
			case VOL_SET -> {
				int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
				int v = Math.max(0, Math.min(max, Math.round(max * it.num() / 100f)));
				cb.yoldaUnmute("sesli komut");
				am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
				am.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI);
				toast(a, "Ses %" + it.num());
			}
			case SLEEP -> {
				sleep(a, it.num());
				reply(a, "Tamam, " + it.num() + " dakika sonra kapatacağım");
			}
			case SLEEP_CANCEL -> {
				boolean was = sleepAt != 0;
				cancelSleep();
				reply(a, was ? "Uyku zamanlayıcısı iptal edildi" : "Kurulu bir zamanlayıcı yok");
			}
			case TIME -> reply(a, "Saat " + new java.text.SimpleDateFormat("HH:mm", TR).format(new java.util.Date()));
			case WEATHER -> {
				toast(a, "Hava durumu alınıyor…");
				YoldaWeather.get(ctx).main().onCompletion((n, err) -> {
					if (n == null) reply(a, "Hava durumu alınamadı");
					else reply(a, ((n.place() != null) && !n.place().isEmpty() ? (n.place() + ": ") : "") + n.temp()
							+ " derece, " + YoldaWeather.text(n.code()).toLowerCase(TR));
				});
			}
			case WHAT_PLAYING -> {
				String t = YoldaNowPlaying.titleOf(cb);
				MediaLib.PlayableItem pi = a.getCurrentPlayable();
				if ((t == null) && (pi != null)) t = pi.getName();
				if ((pi instanceof YtMusicItem m) && (m.getTrack().artist() != null) && !m.getTrack().artist().isEmpty())
					t = m.getTrack().artist() + ", " + m.getTrack().title();
				if (t == null) reply(a, "Şu an bir şey çalmıyor");
				else {
					reply(a, "Şu an " + t);
					YoldaNowPlaying.show(a);
				}
			}
			case FAV_ADD -> {
				String[] e = YoldaFavs.entryOf(a.getCurrentPlayable());
				if (e == null) {
					reply(a, "Çalan bir şey yok ya da favorilere eklenemiyor");
				} else {
					YoldaFavs.add(ctx, e[0], e[1], e[2], e[3]);
					reply(a, "Favorilere eklendi: " + e[1]);
				}
			}
			case FAV_OPEN -> {
				YoldaDeck.Card best = null;
				int bs = 0;
				for (YoldaDeck.Card c : YoldaFavs.load(ctx)) {
					int s = YoldaIntents.channelScore(c.title(), it.arg());
					if (s > bs) {
						bs = s;
						best = c;
					}
				}
				if ((best == null) || (bs < MIN_CHANNEL_SCORE)) reply(a, "Favorilerde “" + it.arg() + "” yok");
				else {
					reply(a, best.title() + " açılıyor");
					YoldaDeckFragment.openFavorite(a, best);
				}
			}
			case THEME_NEXT -> YoldaStyle.next(ctx);
			case THEME_AUTO -> {
				YoldaStyle.setAuto(ctx, true);
				reply(a, "Tema artık gündüz ve geceye göre değişecek");
			}
			case SPEAK_OFF -> {
				YoldaSpeak.setEnabled(ctx, false);
				toast(a, "Sesli yanıt kapatıldı");
			}
			case SPEAK_ON -> {
				YoldaSpeak.setEnabled(ctx, true);
				reply(a, "Sesli yanıt açık");
			}
			case HOME -> a.showFragment(R.id.home_fragment);
			case BACK -> a.yoldaBack();
			case OPEN_APP -> {
				if (!openApp(a, it.arg())) return false;
			}
			case NAV_HOME, NAV_WORK -> {
				boolean home = it.kind() == Kind.NAV_HOME;
				String d = NavFragment.voiceGo(a, home ? "home" : "work", null);
				if (d == null) reply(a, "Önce Navigasyon ekranında " + (home ? "ev" : "iş") + " adresini kaydet");
				else reply(a, home ? "Eve rota başlatılıyor" : "İşe rota başlatılıyor");
			}
			case NAV_TO -> {
				NavFragment.voiceGo(a, null, it.arg());
				reply(a, cap(it.arg()) + " için rota başlatılıyor");
			}
			case NAV_NEARBY -> {
				if ("nöbetçi eczane".equals(it.arg())) {
					a.showFragment(R.id.yolda_pharmacy_fragment);
					reply(a, "Nöbetçi eczaneler açılıyor");
				} else {
					NavFragment.voiceGo(a, null, it.arg());
					reply(a, "En yakın " + it.arg() + " aranıyor");
				}
			}
			case YT_SEARCH -> {
				if (!AddonManager.get().hasAddon(R.id.youtube_fragment)) return false;
				reply(a, "YouTube'da " + it.arg() + " aranıyor");
				YoutubeHomeFragment.searchFor(a, it.arg());
			}
			case SONG -> {
				toast(a, "“" + it.arg() + "” çalınıyor");
				MusicFragment.playQuery(a, it.arg());
			}
			case RADIO_SEARCH -> {
				MediaLib.PlayableItem ch = bestChannel(Collections.singletonList(it.arg()));
				if (ch != null) {
					playChannel(a, ch);
					break;
				}
				String radio = it.arg();
				toast(a, "“" + radio + "” radyosu aranıyor…");
				me.aap.fermata.media.radio.RadioBrowser.search(radio).main().onCompletion((l, err) -> {
					if ((l == null) || l.isEmpty()) {
						reply(a, cap(radio) + " radyosu bulunamadı");
						return;
					}
					var s = l.get(0);
					a.getMediaServiceBinder().playItem(me.aap.fermata.media.radio.YoldaStreamItem.create(a.getLib(), s.url(),
							s.name(), s.logo(), "Radyo"));
					YoldaNowPlaying.show(a);
					reply(a, s.name() + " açılıyor");
				});
			}
			case CHANNEL -> {
				return channel(a, Collections.singletonList(it), raw);
			}
		}
		return true;
	}

	/** Kanal adı (bütün tahminler); tutmazsa müzik ekranındaysa şarkı araması. */
	private static boolean channel(MainActivityDelegate a, List<Intent> chans, String raw) {
		if (channels.isEmpty() && AddonManager.get().hasAddon(R.id.tv_fragment)) {
			// Kanal listesi henüz yüklenmemiş: yükle, sonra aynı komutu yeniden dene
			YoldaLog.add("Sesli komut: kanal listesi yükleniyor, sonra “" + raw + "”");
			a.getLib().getItem("TV").main().onCompletion((r, err) -> {
				if (!(r instanceof MediaLib.BrowsableItem br)) {
					fallback(a, chans, raw);
					return;
				}
				br.getPlayableChildren(true).main().onCompletion((list, e2) -> {
					if ((list != null) && !list.isEmpty()) channels = new ArrayList<>(list);
					if (channels.isEmpty()) fallback(a, chans, raw);
					else if (!channel(a, chans, raw)) notUnderstood(a, raw);
				});
			});
			return true;
		}
		List<String> names = new ArrayList<>(chans.size());
		for (Intent i : chans) names.add(i.arg());
		MediaLib.PlayableItem best = bestChannel(names);
		if (best != null) {
			lastIntent = Intent.of(Kind.CHANNEL, best.getName());
			YoldaLog.add("Sesli komut “" + raw + "” → kanal: " + best.getName());
			playChannel(a, best);
			return true;
		}
		return musicFallback(a, chans.get(0));
	}

	private static void fallback(MainActivityDelegate a, List<Intent> chans, String raw) {
		if (!musicFallback(a, chans.get(0))) notUnderstood(a, raw);
	}

	/** Müzik ekranındayken söylenen her şey şarkı / sanatçı araması: "Tarkan", "Sezen Aksu dinle". */
	private static boolean musicFallback(MainActivityDelegate a, Intent it) {
		if (!(a.getActiveFragment() instanceof MusicFragment)) return false;
		String q = musicContextQuery(it.arg());
		if (q == null) return false;
		toast(a, "“" + q + "” çalınıyor");
		MusicFragment.playQuery(a, q);
		return true;
	}

	/** Söylenen adlar içinde kanal listesinde en iyi tutan (eşitlikte kısa ad: "TRT 1" ≠ "TRT 1 HD"). */
	static MediaLib.PlayableItem bestChannel(List<String> spoken) {
		MediaLib.PlayableItem best = null;
		int bestScore = 0;
		for (String q : spoken) {
			for (MediaLib.PlayableItem pi : channels) {
				int s = YoldaIntents.channelScore(pi.getName(), q);
				if ((s > bestScore) || ((s == bestScore) && (s > 0) && (best != null)
						&& (pi.getName().length() < best.getName().length()))) {
					best = pi;
					bestScore = s;
				}
			}
		}
		return (bestScore >= MIN_CHANNEL_SCORE) ? best : null;
	}

	private static void playChannel(MainActivityDelegate a, MediaLib.PlayableItem ch) {
		a.getBody().playItem(ch);
		if (!ch.isVideo()) YoldaNowPlaying.show(a);
		reply(a, ch.getName() + " açılıyor");
	}

	private static boolean openApp(MainActivityDelegate a, String key) {
		AddonManager am = AddonManager.get();
		switch (key) {
			case "music" -> a.showFragment(R.id.yolda_music);
			case "youtube" -> {
				if (!am.hasAddon(R.id.youtube_fragment)) return false;
				a.showFragment(R.id.yolda_youtube);
			}
			case "pharmacy" -> a.showFragment(R.id.yolda_pharmacy_fragment);
			case "nav" -> {
				Context ctx = a.getContext();
				if (!a.isCarActivityNotMirror() || NavFragment.inAppMap(ctx) || !NavFragment.carNav(a, null))
					a.showFragment(R.id.yolda_nav_fragment);
			}
			case "channels" -> a.showFragment(R.id.yolda_channels_fragment);
			case "phone" -> HomeFragment.phone(a.getContext());
			case "menu" -> a.showFragment(R.id.yolda_menu_fragment);
			case "web" -> {
				YoldaStyle.webNoToolbar = false;
				a.showFragment(R.id.web_browser_fragment);
				a.postBroadcastEvent(me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED);
			}
			case "favs" -> {
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().post(() -> {
					if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.openFavorites();
				});
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Kaldığın yerden: kapatılan / son çalan; yoksa son dinlenen şarkılar. */
	private static void resume(MainActivityDelegate a) {
		if (YoldaNowPlaying.togglePlayIfStopped(a)) return;
		List<YtMusic.Track> rec = MusicFragment.recent(a.getContext());
		if (!rec.isEmpty()) MusicFragment.playTracks(a, rec, 0);
		else reply(a, "Henüz bir şey dinlemedin");
	}

	// ---- uyku zamanlayıcısı ----

	static void sleep(MainActivityDelegate a, int min) {
		cancelSleep();
		Runnable r = new Runnable() {
			@Override
			public void run() {
				if (sleepTask != this) return;
				sleepTask = null;
				sleepAt = 0;
				YoldaLog.add("Uyku zamanlayıcısı: oynatma durduruldu");
				a.getMediaSessionCallback().onPause();
				toast(a, "Uyku zamanlayıcısı: oynatma durduruldu");
			}
		};
		sleepTask = r;
		long ms = min * 60_000L;
		sleepAt = SystemClock.elapsedRealtime() + ms;
		App.get().getHandler().postDelayed(r, ms);
	}

	static void cancelSleep() {
		Runnable r = sleepTask;
		sleepTask = null;
		sleepAt = 0;
		if (r != null) App.get().getHandler().removeCallbacks(r);
	}

	// ---- yanıt ----

	private static void toast(MainActivityDelegate a, String text) {
		YoldaStyle.toast(a.getContext(), text);
	}

	/** Kısa yazı + sesli yanıt. */
	static void reply(MainActivityDelegate a, String text) {
		toast(a, text);
		YoldaSpeak.say(a.getContext(), text);
	}

	private static String cap(String s) {
		return ((s == null) || s.isEmpty()) ? "" : (s.substring(0, 1).toUpperCase(TR) + s.substring(1));
	}

	/** Komut anlaşılamadı: kullanıcı beklemede kalmasın, kısa yazıyla (ve sesle) söylenir. */
	public static void notUnderstood(MainActivityDelegate a, String raw) {
		YoldaLog.add("Sesli komut anlaşılamadı: “" + raw + "”");
		toast(a, "“" + raw + "” anlaşılamadı");
		YoldaSpeak.say(a.getContext(), "Anlayamadım, tekrar söyler misin?");
	}

	// ---- eski yardımcılar (birim testleri kullanıyor) ----

	/** "Kral FM aç", "Joy Türk radyosu", "radyo Metro FM" → aranacak istasyon adı; radyo isteği değilse null. */
	static String radioQuery(String c) {
		return YoldaIntents.radioQuery(c);
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
