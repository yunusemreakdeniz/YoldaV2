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

	/** Tanıma motoruna önceden bildirilecek adlar: kanallar, seçili kişiler, komutlar ("TRT Haber" daha doğru duyulsun). */
	public static ArrayList<String> hintNames(Context ctx) {
		ArrayList<String> l = new ArrayList<>();
		for (String s : new String[]{"Hey Yolda", "aç", "kapat", "duraklat", "devam et", "sonraki", "önceki", "sesi aç", "sesi kıs",
				"ara", "eve git", "işe git", "en yakın benzinlik", "en yakın eczane", "hava nasıl", "saat kaç", "ne çalıyor",
				"YouTube", "YouTube Music", "radyo", "favoriler", "radar", "park", "yolculuk özeti", "otuz dakika sonra kapat"}) l.add(s);
		for (YoldaCalls.Contact c : YoldaCalls.favorites(ctx)) l.add(c.name());
		for (MediaLib.PlayableItem pi : channels) {
			if (l.size() >= 140) break;
			l.add(pi.getName());
		}
		return l;
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
		lastFeedback = null;
		voiceSeq++; // yeni komut: daha önce sorulup yanıtı gelmemiş Gemini sorusu artık söylenmez

		// "Hangisi?" sorusunun cevabı ("birinci", "Ahmet Yılmaz")
		if (YoldaCalls.pending != null) {
			for (String r : raws) if (YoldaCalls.answer(a, r)) return true;
		}

		// Gemini bağlıysa: doğal cümle, sohbet ve bilgi soruları ona; kesin cihaz komutları yerelde (hızlı, internetsiz)
		if (!fromGemini && YoldaGemini.isEnabled(a.getContext()) && allForGemini(raws)) {
			YoldaLog.add("Sesli komut Gemini'ye (doğal cümle): “" + raws.get(0) + "”");
			askGemini(a, raws.get(0));
			return true;
		}

		// İki komut birden: "sesi aç ve TRT Haber aç", "duraklat sonra hava nasıl"
		String c0 = YoldaIntents.clean(raws.get(0));
		String[] parts = c0.split(" (?:ve|sonra|ardından|daha sonra) ");
		if (parts.length >= 2) {
			List<Intent> its = new ArrayList<>();
			boolean ok = true;
			for (int i = 0; i < parts.length; i++) {
				Intent it = YoldaIntents.parse(parts[i]);
				if ((it == null) || ((it.kind() == Kind.CHANNEL) && (i < parts.length - 1))) {
					ok = false;
					break;
				}
				its.add(it);
			}
			if (ok && (its.get(0).kind() != Kind.CHANNEL)) {
				YoldaLog.add("Sesli komut: " + its.size() + " komut birden");
				for (int i = 0; i < its.size(); i++) {
					Intent it = its.get(i);
					String part = parts[i];
					if (i == 0) run(a, it, part);
					else App.get().getHandler().postDelayed(() -> run(a, it, part), 700L * i);
				}
				return true;
			}
		}

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
		retries = 0;

		switch (it.kind()) {
			case HELP -> reply(a, "Örneğin: TRT Haber aç, Tarkan çal, eve git, en yakın benzinlik, sesi yüzde elli yap, "
					+ "yarın hava nasıl, on dakika sonra su içmeyi hatırlat, neredeyim, şarjım kaç, on beş çarpı dört, "
					+ "Atatürk kimdir, annemi ara, radyo kartına geç, sonraki kart, on saniye geri sar, okul uyarısını kapat, "
					+ "okyanus teması, gün batımı ne zaman. Sohbet de edebiliriz: nasılsın, şaka yap, acıktım.");
			case CHAT -> {
				YoldaChat.Reply cr = YoldaChat.answer(it.arg(), 12, null);
				// iş yapan sohbet ("acıktım" → restoranlar) yerelde; düz sohbet Gemini bağlıysa onunla
				if (YoldaGemini.isEnabled(ctx) && ((cr == null) || (cr.then() == null))) askGemini(a, raw);
				else reply(a, YoldaAnswers.chat(a, it.arg(), next -> run(a, next, raw)));
			}
			case FX -> {
				toast(a, "Kurlar alınıyor…");
				String want = it.arg();
				me.aap.utils.app.App.get().execute((me.aap.utils.function.CheckedSupplier<List<YoldaInfo.Rate>, Throwable>) () -> YoldaInfo.rates(ctx, true)).onSuccess(l -> App.get().getHandler().post(() -> {
					StringBuilder b = new StringBuilder();
					for (YoldaInfo.Rate r : l) {
						if ((want != null) && !r.name().equals(want)) continue;
						b.append((b.length() > 0) ? ", " : "").append(r.name()).append(' ').append(YoldaInfo.money(r.sell()).replace(" ₺", " lira"));
					}
					reply(a, (b.length() == 0) ? "Kurlar alınamadı" : b.toString());
				}));
			}
			case PRAYER -> {
				String want = it.arg();
				me.aap.utils.app.App.get().execute((me.aap.utils.function.CheckedSupplier<List<YoldaInfo.Prayer>, Throwable>) () -> YoldaInfo.prayers(ctx, true)).onSuccess(l -> App.get().getHandler().post(() -> {
					java.util.Calendar cal = java.util.Calendar.getInstance();
					int m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
					YoldaInfo.Prayer p = null;
					if (want != null) for (YoldaInfo.Prayer x : l) if (x.name().equals(want)) p = x;
					if (p == null) p = YoldaInfo.next(l, m);
					reply(a, (p == null) ? "Namaz vakitleri alınamadı"
							: (p.name() + " " + p.time() + ", " + YoldaInfo.left(p, m) + " sonra"));
				}));
			}
			case NEWS -> {
				toast(a, "Haberler alınıyor…");
				me.aap.utils.app.App.get().execute((me.aap.utils.function.CheckedSupplier<List<YoldaInfo.News>, Throwable>) () -> YoldaInfo.news(ctx, true)).onSuccess(l -> App.get().getHandler().post(() ->
						reply(a, l.isEmpty() ? "Haberler alınamadı" : ("Son dakika. " + YoldaInfo.newsSpoken(l, 5)))));
			}
			case VIEW_MODE -> {
				if (a.isCarActivity()) {
					reply(a, "Araçta her zaman kart destesi görünür");
					break;
				}
				boolean phone = it.num() == 1;
				ctx.getApplicationContext().getSharedPreferences(me.aap.fermata.ui.view.YoldaStyle.PREFS, Context.MODE_PRIVATE)
						.edit().putBoolean("phone_view", phone).apply();
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().postDelayed(() -> {
					if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.setViewMode(phone);
				}, 150);
				reply(a, phone ? "Telefon görünümü" : "Araç görünümü");
			}
			case VIDEO_Q -> {
				String cur = me.aap.fermata.ui.view.YoldaStyle.videoQuality(ctx);
				String q = me.aap.fermata.ui.view.YoldaStyle.spokenVideoQuality(it.arg(), cur);
				if (q == null) {
					reply(a, "Video kalitesi " + me.aap.fermata.ui.view.YoldaStyle.videoQualityLabel(cur)
							+ ". 360, 480, 720, 1080 ya da otomatik diyebilirsin");
				} else {
					me.aap.fermata.ui.view.YoldaStyle.setVideoQuality(ctx, q);
					if ((cb != null) && (cb.getEngine() != null)) cb.getEngine().yoldaVideoQualityChanged();
					reply(a, "Video kalitesi " + me.aap.fermata.ui.view.YoldaStyle.videoQualityLabel(q));
				}
			}
			case EQ -> {
				if (it.arg() == null) {
					run(a, Intent.of(Kind.CARD, "ekolayzer"), raw); // desteyi Ekolayzer kartına götür
					reply(a, "Ses ayarları: Pop, Rock, Konuşma, Bas, Araç, Klasik ya da Caz deyebilirsin");
				} else {
					me.aap.fermata.media.engine.YoldaEq.setCurrent(ctx, it.arg());
					if (cb != null) cb.yoldaEqChanged();
					reply(a, "Kapalı".equals(it.arg()) ? "Ekolayzer kapatıldı" : ("Ses ayarı " + it.arg()));
				}
			}
			case DATE -> reply(a, YoldaAnswers.date());
			case CALC -> reply(a, it.arg());
			case BATTERY -> reply(a, YoldaAnswers.battery(ctx));
			case SPEED -> reply(a, YoldaAnswers.speed());
			case WHERE_AM_I -> {
				toast(a, "Konum bulunuyor…");
				YoldaAnswers.whereAmI(ctx, t -> reply(a, t));
			}
			case FORECAST -> {
				toast(a, "Hava tahmini alınıyor…");
				YoldaAnswers.forecast(ctx, it.arg(), t -> reply(a, t));
			}
			case TIMER -> {
				YoldaAnswers.remind(ctx, it.num(), it.arg());
				reply(a, "Tamam, " + it.num() + " dakika sonra " + (((it.arg() == null) || it.arg().isEmpty())
						? "haber vereceğim" : "hatırlatacağım"));
			}
			case TIMER_CANCEL -> {
				int n = YoldaAnswers.cancelReminders();
				reply(a, (n > 0) ? "Hatırlatmalar silindi" : "Kurulu bir hatırlatma yok");
			}
			case WIKI -> {
				if (YoldaGemini.isEnabled(ctx)) {
					askGemini(a, raw);
					break;
				}
				toast(a, "“" + it.arg() + "” aranıyor…");
				YoldaAnswers.wiki(it.arg(), t -> reply(a, t));
			}
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
				boolean was = (sleepAt != 0) | (YoldaAnswers.cancelReminders() > 0);
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
			case RADAR_ON, RADAR_OFF -> {
				boolean on = it.kind() == Kind.RADAR_ON;
				YoldaRadar.setEnabled(ctx, on);
				if (on && !YoldaRadar.hasPermission(ctx)) {
					a.showFragment(R.id.yolda_radar_fragment);
					reply(a, "Radar uyarıları açık; konum izni gerekli");
				} else {
					reply(a, on ? "Radar uyarıları açık" : "Radar uyarıları kapatıldı");
				}
			}
			case RADAR_ADD -> reply(a, YoldaRadar.addHere(ctx) ? "Radar eklendi, buradan geçerken uyaracağım"
					: "Konum henüz bilinmiyor");
			case RADAR_INFO -> reply(a, radarInfo(ctx));
			case CALL -> YoldaCalls.voiceCall(a, it.arg());
			case PARK_WHERE -> {
				double[] pk = YoldaRadar.park(ctx);
				if (pk == null) reply(a, "Kayıtlı park yeri yok. Park edince buraya park ettim de");
				else {
					String d = Double.isNaN(YoldaRadar.lat) ? ""
							: (", " + YoldaDriveCore.distText(YoldaRadarCore.dist(YoldaRadar.lat, YoldaRadar.lon, pk[0], pk[1]))
							.replace(" km", " kilometre").replace(" m", " metre") + " uzakta");
					reply(a, "Aracın kayıtlı" + d + ". Yürüyerek yol tarifi açılıyor");
					YoldaRadar.walkToPark(ctx);
				}
			}
			case PARK_SET -> reply(a, YoldaRadar.savePark(ctx, false) ? "Park yeri kaydedildi" : "Konum henüz bilinmiyor");
			case TRIP_INFO -> {
				if (YoldaRadar.trip.isActive()) reply(a, "Bu yolculukta " + YoldaDriveCore.distText(YoldaRadar.trip.meters())
						.replace(" km", " kilometre").replace(" m", " metre") + " yol yaptın");
				else {
					java.util.List<YoldaDriveCore.TripRec> l = YoldaRadar.trips(ctx);
					if (l.isEmpty()) reply(a, "Henüz kayıtlı yolculuk yok");
					else reply(a, "Son yolculuk: " + l.get(0).summary().replace(" · ", ", ").replace("ort.", "ortalama"));
				}
			}
			case HOME -> {
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().postDelayed(() -> {
					if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.goHomeCard();
				}, 120);
			}
			case CARD -> {
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().postDelayed(() -> {
					if (!(a.getActiveFragment() instanceof YoldaDeckFragment d)) return;
					String title = d.goToCard(it.arg());
					if (title == null) reply(a, "“" + it.arg() + "” adında bir kart bulamadım");
					else toast(a, title + " kartı");
				}, 150);
			}
			case NEXT_CARD, PREV_CARD -> {
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().postDelayed(() -> {
					if (!(a.getActiveFragment() instanceof YoldaDeckFragment d)) return;
					String title = d.stepCard((it.kind() == Kind.NEXT_CARD) ? 1 : -1);
					if (title == null) toast(a, (it.kind() == Kind.NEXT_CARD) ? "Son karttasın" : "İlk karttasın");
					else toast(a, title + " kartı");
				}, 150);
			}
			case CARD_OPEN -> {
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().postDelayed(() -> {
					if (!(a.getActiveFragment() instanceof YoldaDeckFragment d)) return;
					String title = d.openCardByName(it.arg());
					if (title == null) reply(a, "“" + it.arg() + "” adında bir kart bulamadım");
					else toast(a, title + " açılıyor");
				}, 150);
			}
			case EDIT_CARDS -> {
				a.showFragment(R.id.home_fragment);
				App.get().getHandler().postDelayed(() -> {
					if (a.getActiveFragment() instanceof YoldaDeckFragment d) d.startEditing();
				}, 150);
			}
			case SEEK, SEEK_START -> {
				var eng = cb.getEngine();
				if (eng == null) {
					reply(a, "Şu an çalan bir şey yok");
					break;
				}
				long pos = Math.max(0, cb.getPlaybackState().getPosition());
				long to = (it.kind() == Kind.SEEK_START) ? 0 : Math.max(0, pos + it.num() * 1000L);
				cb.onSeekTo(to);
				toast(a, (it.kind() == Kind.SEEK_START) ? "Baştan" : ((it.num() > 0 ? "⏩ " : "⏪ ") + Math.abs(it.num()) + " sn"));
			}
			case FEATURE -> {
				boolean on = it.num() == 1;
				String k = it.arg();
				if ("overspeed".equals(k)) YoldaRadar.setOverspeedWarn(ctx, on);
				else if ("voice".equals(k)) YoldaRadar.setVoice(ctx, on);
				else YoldaRadar.setFeature(ctx, k, on);
				reply(a, featureName(k) + (on ? " açıldı" : " kapatıldı"));
			}
			case WAKE_OFF -> {
				YoldaWake.setEnabled(a, false);
				reply(a, "Hey Yolda kapatıldı. Mikrofon düğmesiyle konuşabilirsin, Ayarlar'dan yeniden açabilirsin");
			}
			case SUN -> YoldaWeather.detail(ctx, false).main().onCompletion((d, err) -> {
				if ((d == null) || (d.sunset() == null)) reply(a, "Bilgi alınamadı");
				else reply(a, "set".equals(it.arg()) ? ("Güneş " + d.sunset() + "'de batıyor") : ("Güneş " + d.sunrise() + "'de doğuyor"));
			});
			case THEME_SET -> {
				int i = YoldaStyle.setByName(ctx, it.arg());
				if (i < 0) reply(a, "“" + it.arg() + "” adında tema yok. Temalar: " + YoldaStyle.names());
				else toast(a, "Tema: " + YoldaStyle.THEMES[i].name);
			}
			case TEST_ALERT -> YoldaRadar.testAlert(ctx, false);
			case MSG_REPLY -> reply(a, YoldaMessages.reply(ctx, it.arg()));
			case MSG_READ -> {
				if (!YoldaMessages.readLast(ctx))
					reply(a, YoldaMessages.hasAccess(ctx) ? "Okunacak mesaj yok" : "Mesajları okumak için telefonda bildirim erişimi gerekiyor");
			}
			case NAV_APP -> {
				boolean waze = "waze".equals(it.arg());
				ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE).edit()
						.putBoolean("nav_waze", waze).apply();
				reply(a, waze ? "Yol tarifleri artık Waze ile" : "Yol tarifleri artık Google Haritalar ile");
			}
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
		if (musicFallback(a, chans.get(0))) return true;
		if (!fromGemini && YoldaGemini.isEnabled(a.getContext())) { // "biraz rahatlatıcı bir şey aç": kanal değil
			YoldaLog.add("Sesli komut Gemini'ye (kanal bulunamadı): “" + raw + "”");
			askGemini(a, raw);
			return true;
		}
		return false;
	}

	/**
	 * Gemini bağlıyken bu cümleyi yerel çözücü yerine Gemini mi anlamalı: anlaşılmayan, sohbet, bilgi sorusu ve
	 * uzun doğal cümleyle verilmiş gevşek komutlar (şarkı / video / radyo arama, yer tarifi) Gemini'ye; kesin cihaz
	 * komutları (ses, oynatma, kart, arama, tema, sürüş, saat…) yerelde kalır. Kanal adları {@link #channel}'da.
	 */
	/**
	 * Tanıyıcının bütün tahminleri Gemini'lik mi: tahminlerden biri kesin bir komutsa ya da cümle "ve / sonra"
	 * ile birden çok komutsa yerelde kalır.
	 */
	static boolean allForGemini(List<String> raws) {
		for (String r : raws) {
			String c = YoldaIntents.clean(r);
			if (c.matches(".* (?:ve|sonra|ardından|daha sonra) .*")) return false;
			if (!geminiFirst(YoldaIntents.parse(r), c)) return false;
		}
		return true;
	}

	static boolean geminiFirst(@androidx.annotation.Nullable Intent it, String cleaned) {
		if (it == null) return true;
		int words = cleaned.trim().isEmpty() ? 0 : cleaned.trim().split("\\s+").length;
		return switch (it.kind()) {
			case CHAT, WIKI -> true;
			case YT_SEARCH, SONG, RADIO_SEARCH, NAV_TO -> words >= 6; // "yol boyunca dinleyeceğim güzel bir şey çal"
			default -> false;
		};
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
				a.showFragment(R.id.yolda_nav_fragment); // her zaman Yolda'nın navigasyon ekranı
			}
			case "channels" -> a.showFragment(R.id.yolda_channels_fragment);
			case "phone" -> HomeFragment.phone(a.getContext());
			case "menu" -> a.showFragment(R.id.yolda_menu_fragment);
			case "web" -> {
				YoldaStyle.webNoToolbar = false;
				a.showFragment(R.id.web_browser_fragment);
				a.postBroadcastEvent(me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED);
			}
			case "radar" -> a.showFragment(R.id.yolda_radar_fragment);
			case "weather" -> a.showFragment(R.id.yolda_weather_fragment);
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

	static String radarInfo(Context ctx) {
		if (!YoldaRadar.isEnabled(ctx)) return "Radar uyarıları kapalı. Açmak için radarları aç de";
		if (!YoldaRadar.hasFix()) return "Konumun henüz bilinmiyor";
		YoldaRadarCore.Ahead ah = YoldaRadar.ahead;
		int lim = YoldaRadar.limit;
		String l = (lim > 0) ? ("Hız sınırı " + lim + (YoldaRadar.limitEstimated ? ", tahmini" : "")) : "Bu yolun hız sınırını bilmiyorum";
		if (ah == null) return "Önde radar yok. " + l;
		return ah.meters() + " metre sonra " + ah.cam().label().toLowerCase(TR) + ". " + l;
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

	/** Asistan penceresinde gösterilecek son yanıt ("TRT Haber açılıyor"); komut başında sıfırlanır. */
	public static volatile String lastFeedback;

	/** Son yanıtı al ve sıfırla. */
	public static String consumeFeedback() {
		String s = lastFeedback;
		lastFeedback = null;
		return s;
	}

	private static void toast(MainActivityDelegate a, String text) {
		lastFeedback = text;
		YoldaStyle.toast(a.getContext(), text);
	}

	static String featureName(String k) {
		return switch (k) {
			case "zones" -> "Okul ve hastane uyarısı";
			case "fatigue" -> "Mola hatırlatıcısı";
			case "avg" -> "Ortalama hız uyarısı";
			case "stops" -> "Yol üstü tesis bildirimi";
			case "overspeed" -> "Hız aşımı bipi";
			case "voice" -> "Sesli radar uyarısı";
			case "park" -> "Park yeri kaydı";
			case "trips" -> "Yolculuk kaydı";
			case "official" -> "Resmi radarlar";
			default -> k;
		};
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
		if (YoldaGemini.isEnabled(a.getContext()) && !fromGemini) { // komut değil: soru / sohbet → Gemini
			YoldaLog.add("Sesli komut Gemini'ye: “" + raw + "”");
			askGemini(a, raw);
			return;
		}
		YoldaLog.add("Sesli komut anlaşılamadı: “" + raw + "”");
		lastFeedback = "Anlayamadım";
		toast(a, "“" + raw + "” anlaşılamadı");
		listenAgain(a);
	}

	/** Soruyu Gemini'ye sor, yanıtı söyle (hata olursa nedenini). */
	static void askGemini(MainActivityDelegate a, String q) {
		toast(a, "Düşünüyorum…");
		int seq = voiceSeq;
		YoldaGemini.state = state(a);
		YoldaGemini.ask(a.getContext(), q, raw -> {
			if (seq != voiceSeq) { // bu arada yeni bir komut verildi: eski yanıt araya girmesin
				YoldaLog.add("Gemini yanıtı geç geldi, söylenmedi");
				return;
			}
			String ans = YoldaGemini.spoken(raw);
			List<String> cmds = YoldaActions.commands(ans);
			if (cmds == null) {
				reply(a, ans);
				// Gemini soru sorduysa ("Hangi şehir?") konuşma bitince mikrofonu bir kez aç: yeniden "Hey Yolda" deme
				if (ans.trim().endsWith("?")) YoldaSpeak.afterSpeech(() -> {
					if (seq == voiceSeq) a.startVoiceAssistant();
				});
				return;
			}
			// Gemini isteği katalogdaki komutlara çevirdi: yerel çözücü yürütür (geri Gemini'ye dönmez)
			YoldaLog.add("Gemini komutu: " + String.join(" ; ", cmds));
			for (int i = 0; i < cmds.size(); i++) {
				String c = cmds.get(i);
				Runnable go = () -> {
					fromGemini = true;
					try {
						if (!handle(a, c)) reply(a, "Bunu yapamadım: " + c);
					} finally {
						fromGemini = false;
					}
				};
				if (i == 0) go.run();
				else App.get().getHandler().postDelayed(go, 900L * i);
			}
		}, err -> reply(a, err));
	}

	/** Gemini'nin çevirdiği komut yürütülürken: anlaşılmazsa yeniden Gemini'ye sorulmaz (döngü olmasın). */
	private static boolean fromGemini;
	/** Her sesli komutta artar: geç gelen Gemini yanıtı yeni komutun üstüne konuşmasın. */
	private static volatile int voiceSeq;

	/** Gemini'ye verilen anlık durum: araç / telefon, ekran, çalan. */
	private static String state(MainActivityDelegate a) {
		StringBuilder b = new StringBuilder(a.isCarActivity() ? "Kullanıcı araçta (Android Auto). " : "Kullanıcı telefonda. ");
		try {
			var cb = a.getMediaSessionCallback();
			String t = YoldaNowPlaying.titleOf(cb);
			if (t != null) b.append(cb.isPlaying() ? "Şu an çalan: " : "Duraklatılmış: ").append(t).append(". ");
			if (YoldaRadar.speed > 5) b.append("Hız ").append(YoldaRadar.speed).append(" km/s. ");
		} catch (Throwable ignore) {
		}
		return b.toString();
	}

	/** Art arda yeniden dinleme sayısı; bir komut çalışınca sıfırlanır. */
	private static int retries;
	private static final int MAX_RETRIES = 2;

	/**
	 * Anlaşılmadı ya da hiçbir şey duyulmadı: "Anlayamadım, tekrar söyler misin?" deyip mikrofonu yeniden açar
	 * (yeniden "Hey Yolda" demek gerekmez). Art arda en çok iki kez; sonra vazgeçer, "Hey Yolda" dinlemesi sürer.
	 */
	public static void listenAgain(MainActivityDelegate a) {
		Context ctx = a.getContext();
		if (retries >= MAX_RETRIES) {
			retries = 0;
			YoldaSpeak.say(ctx, "Anlayamadım. Hazır olunca Hey Yolda de.");
			return;
		}
		retries++;
		YoldaWake.assistantBusy = true; // bekleme arasında "Hey Yolda" mikrofonu almasın
		YoldaSpeak.say(ctx, "Anlayamadım, tekrar söyler misin?");
		long wait = YoldaSpeak.isEnabled(ctx) ? 2400 : 600; // soru bitsin, mikrofon kendi sesini duymasın
		App.get().getHandler().postDelayed(() -> {
			try {
				YoldaLog.add("Sesli komut: yeniden dinleniyor (" + retries + ". deneme)");
				a.startVoiceAssistant();
			} catch (Throwable ex) {
				YoldaWake.assistantBusy = false;
				YoldaLog.add("Yeniden dinleme açılamadı: " + ex);
			}
		}, wait);
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
