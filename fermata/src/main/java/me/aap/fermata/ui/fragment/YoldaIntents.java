package me.aap.fermata.ui.fragment;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hey Yolda: söylenen cümleyi niyete çevirir (Android'den bağımsız; birim testleriyle denetlenir).
 * Önce temizlik (uyandırma sözcüğü, nezaket sözcükleri, noktalama), sonra sırayla kalıplar: daha özel olan önce.
 * Kanal adı eşleşmesi kanal listesi gerektirdiği için burada değil; {@link #channelScore} puanlar.
 */
public final class YoldaIntents {
	static final Locale TR = new Locale("tr");

	private YoldaIntents() {
	}

	public enum Kind {
		PAUSE, PLAY, NEXT, PREV, STOP, VOL_UP, VOL_DOWN, MUTE, VOL_SET, SLEEP, SLEEP_CANCEL, TIME, WEATHER,
		WHAT_PLAYING, FAV_ADD, FAV_OPEN, THEME_NEXT, HOME, BACK, OPEN_APP, NAV_HOME, NAV_WORK, NAV_TO, NAV_NEARBY,
		YT_SEARCH, SONG, RADIO_SEARCH, RESUME, HELP, REPEAT, THEME_AUTO, SPEAK_ON, SPEAK_OFF, RADAR_ON, RADAR_OFF, RADAR_INFO, RADAR_ADD, PARK_WHERE, PARK_SET, TRIP_INFO, CALL, CHANNEL,
		/** Kısa sohbet (arg: temizlenmiş cümle; yanıtı {@link YoldaChat} verir). */
		CHAT,
		/** Destede o karta geç ("radyo kartına geç"); arg: söylenen kart adı. */
		CARD,
		/** Kartlar: sonraki / önceki kart, kartı aç ("radyo kartını aç"), kartları düzenle. */
		NEXT_CARD, PREV_CARD, CARD_OPEN, EDIT_CARDS,
		/** Kart yönetimi: gizle / geri getir (arg: kart adı); taşı (arg: "ad|first", "ad|last", "ad|before|öbür", "ad|after|öbür"). */
		CARD_HIDE, CARD_SHOW, CARD_MOVE,
		/** Sarma: num saniye (eksi: geri); SEEK_START başa. */
		SEEK, SEEK_START,
		/** Sürüş yardımcısını aç / kapat: arg anahtar (zones, fatigue, avg, stops, overspeed, voice, park, trips), num 1 / 0. */
		FEATURE,
		WAKE_OFF, SUN, THEME_SET, TEST_ALERT, NAV_APP,
		/** Mesaj: son mesaja sesle yanıt (arg: metin), son mesajı tekrar oku. */
		MSG_REPLY, MSG_READ,
		DATE, CALC, TIMER, TIMER_CANCEL, BATTERY, SPEED, FORECAST, WHERE_AM_I, WIKI,
		/** Ekolayzer hazır ayarı (arg: ayar adı; null: kartı aç). */
		EQ,
		/** Video kalitesi (arg: söylenen cümle; kaliteyi YoldaStyle.spokenVideoQuality çıkarır). */
		VIDEO_Q,
		/** Telefonda görünüm: num 1 telefon (ızgara), 0 araç (deste). */
		VIEW_MODE,
		/** Bilgi: döviz / altın (arg: dolar, euro, sterlin, gram, çeyrek ya da null), namaz vakti, haberleri oku. */
		FX, PRAYER, NEWS,
		/** Telefon durumu: pil, bağlantı, bu ayki mobil veri. */
		STATUS,
		/** Varılacak yerdeki otoparklar (arg: yer; null = son yol tarifi). */
		PARK_DEST,
		/** Notlar: ekle (arg: metin), oku, sil. */
		NOTE_ADD, NOTE_READ, NOTE_CLEAR,
		/** Uyarı geçmişi: son uyarılar; az önce söylenen (num 1). */
		ALERTS,
		/** Takvim: bugünkü / yarınki etkinlikler; araç bakımı: muayene, sigorta, kasko, yağ değişimi. */
		CALENDAR, CARCARE,
		/** Podcast: arg = podcast adı ("X podcastini aç"). */
		PODCAST,
		/** Yol süresi: arg home / work. Spor: arg takım (null: son sonuçlar). */
		TRAFFIC, SPORTS
	}

	/** Niyet (arg ve sayı birlikte): "10 dakika sonra su içmeyi hatırlat" → TIMER, 10, "su içmeyi". */
	static Intent of(Kind k, String arg, int n) {
		return new Intent(k, arg, n);
	}

	/** Niyet: tür, metin bağımsız değişkeni (uygulama anahtarı, hedef, arama…) ve sayı (dakika, yüzde). */
	public record Intent(Kind kind, String arg, int num) {
		static Intent of(Kind k) {
			return new Intent(k, null, 0);
		}

		static Intent of(Kind k, String arg) {
			return new Intent(k, arg, 0);
		}

		static Intent of(Kind k, int n) {
			return new Intent(k, null, n);
		}
	}

	// ---- temizlik ----

	private static final Pattern WAKE = Pattern.compile("^(hey |hay |ey |hi )?(yolda|yola|yoldaş)[, ]+");
	private static final Pattern POLITE_HEAD = Pattern.compile("^((lütfen|acaba|bana|beni|şimdi|hadi|haydi) )+");
	private static final Pattern POLITE_TAIL = Pattern.compile("( (lütfen|bakalım|hadi|haydi|misin|mısın|musun|müsün))+$");

	/** Küçük harf (Türkçe), uyandırma sözcüğü ve nezaket sözcükleri atılmış cümle. */
	public static String clean(String raw) {
		if (raw == null) return "";
		String c = raw.trim().toLowerCase(TR).replace('’', '\'').replaceAll("[.!?,;:]+", " ").replaceAll("\\s+", " ").trim();
		c = fixMisheard(c);
		c = WAKE.matcher(c).replaceFirst("");
		c = POLITE_HEAD.matcher(c).replaceFirst("");
		c = POLITE_TAIL.matcher(c).replaceFirst("");
		c = c.replaceAll(" (açar|açabilir|açsana|açın)( mı| mi)?$", " aç").replaceAll(" (çalar|çalsana|çalabilir)( mı| mi)?$", " çal");
		return c.trim();
	}

	/** Tanıma motorunun sık yanlış yazdıkları (Türkçe telaffuz): "tirete" → trt, "yutup" → youtube, "ef em" → fm. */
	private static final String[][] MISHEARD = {{"tirete", "trt"}, {"te re te", "trt"}, {"te ar te", "trt"},
			{"yutup", "youtube"}, {"yutub", "youtube"}, {"yu tüp", "youtube"}, {"you tube", "youtube"}, {"yu tub", "youtube"},
			{"ef em", "fm"}, {"efem", "fm"}, {"en tivi", "ntv"}, {"en te ve", "ntv"}, {"a tivi", "atv"}, {"a te ve", "atv"},
			{"haber türk", "habertürk"}, {"teve", "tv"}, {"tivi", "tv"}, {"hey yoldaş", "hey yolda"}, {"hey yollar", "hey yolda"}};

	static String fixMisheard(String c) {
		String t = " " + c + " ";
		for (String[] m : MISHEARD) t = t.replace(" " + m[0] + " ", " " + m[1] + " ");
		return t.trim();
	}

	/**
	 * Söylenen kişi adının denenecek biçimleri: "annemi" → annemi, annem, anne; "Ahmet'e" → ahmet'e, ahmet;
	 * "babamı" → babam, baba. Kesme işareti atılır.
	 */
	public static List<String> nameForms(String spoken) {
		java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
		String s = (spoken == null) ? "" : spoken.trim().toLowerCase(TR);
		if (s.isEmpty()) return new ArrayList<>(out);
		out.add(s.replace("'", "").replace("’", ""));
		String base = s.replaceAll("['’]?(yı|yi|yu|yü|nı|ni|nu|nü|ya|ye|na|ne|ı|i|u|ü|a|e)$", "").replace("'", "").replace("’", "");
		if (base.length() >= 2) out.add(base);
		if ((base.length() >= 4) && base.endsWith("m")) out.add(base.substring(0, base.length() - 1)); // annem → anne
		return new ArrayList<>(out);
	}

	// ---- sayılar ----

	private static final String[][] NUMS = {{"yarım saat", "30"}, {"bir saat", "60"}, {"çeyrek saat", "15"},
			{"doksan", "90"}, {"seksen", "80"}, {"yetmiş", "70"}, {"altmış", "60"}, {"elli", "50"}, {"kırk beş", "45"}, {"kırk", "40"}, {"otuz", "30"},
			{"yirmi beş", "25"}, {"yirmi", "20"}, {"on beş", "15"}, {"on", "10"}, {"dokuz", "9"}, {"sekiz", "8"},
			{"yedi", "7"}, {"altı", "6"}, {"beş", "5"}, {"dört", "4"}, {"üç", "3"}, {"iki", "2"}, {"bir", "1"},
			{"yüz", "100"}};

	/** Sayı sözcükleri rakama: "otuz beş" → "35", "yarım saat" → "30". */
	private static String words(String s) {
		String t = " " + s + " ";
		for (String[] n : NUMS) t = t.replace(" " + n[0] + " ", " " + n[1] + " ");
		Matcher m = Pattern.compile(" ([1-9]0) ([1-9]) ").matcher(t);
		StringBuffer b = new StringBuffer(); // Android'de appendReplacement(StringBuilder) yok (çöküyordu)
		while (m.find()) m.appendReplacement(b, " " + (Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2))) + " ");
		m.appendTail(b);
		return b.toString();
	}

	/** "otuz dakika", "45 dk", "yarım saat", "1 saat" → dakika; sayı yoksa -1. */
	static int minutes(String s) {
		String t = words(s);
		Matcher m = Pattern.compile("(\\d+) ?(dakika|dk|saat)?").matcher(t);
		if (!m.find()) return -1;
		int v = Integer.parseInt(m.group(1));
		if ("saat".equals(m.group(2)) && !s.contains("yarım") && !s.contains("çeyrek")) v *= 60;
		return v;
	}

	static int number(String s) {
		String t = words(s);
		Matcher m = Pattern.compile("(\\d+)").matcher(t);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	// ---- kalıplar ----

	private static final Pattern SLEEP = Pattern.compile(
			"^(?:uyku zamanlayıcısı(?:nı)? (?:kur )?)?(.+?) (?:sonra|içinde) (?:kapat|kapansın|dur|dursun|sustur|sus|kapat beni)$"
					+ "|^uyku zamanlayıcısı(?:nı)? (.+?)(?: kur| ayarla)?$");
	private static final Pattern VOL_SET = Pattern.compile("^ses(?:i| seviyesini| seviyesi)? (?:yüzde )?(.+?)(?: yap| ayarla| olsun)?$");
	private static final Pattern NEARBY = Pattern.compile(
			"^(?:en yakın|yakındaki|yakınımdaki|buralardaki|yakında bir|yakınlarda) (.+?)(?: git| götür| nerede| var mı| bul| göster| ara)?$");
	private static final Pattern NAV_DEST = Pattern.compile(
			"^(.+?)(?:['’]?(?:ye|ya|e|a|ne|na))? (git|götür|gidelim|yol tarifi(?: ver)?|navigasyon(?: başlat)?|rota(?: oluştur)?)$");
	private static final Pattern YT = Pattern.compile(
			"^(?:youtube'?(?:da|ta|dan|tan)|yutup'?(?:ta|da)) (.+?)(?: ara| aç| bul| izle| göster| izlet)?$"
					+ "|^(.+?) videosu(?:nu)? (?:aç|izle|göster|bul)$");
	private static final Pattern FAV_OPEN = Pattern.compile(
			"^favori(?:lerimden|lerden|mden|den) (.+?)(?:['’]?(?:yi|yı|yu|yü|i|ı|u|ü))? (?:aç|çal|başlat)$|^(.+?) favorimi (?:aç|çal)$");
	private static final Pattern CALL = Pattern.compile(
			"^(.+?) (?:ara|arar|arar mısın|arasana|telefonla ara|çaldır|bir ara)$|^(.+?) (?:telefon et|ile görüş|ile konuş|ile görüşmek istiyorum)$");
	private static final Pattern CARD_HIDE_P = Pattern.compile(
			"^(.+?) kart(?:ı|ını)? (?:desteden |ana sayfadan |ekrandan )?(?:gizle|kaldır|sil|çıkar)$");
	private static final Pattern CARD_SHOW_P = Pattern.compile(
			"^(.+?) kart(?:ı|ını)? (?:geri getir|geri ekle|geri al|tekrar ekle|tekrar göster|ekle)$");
	private static final Pattern CARD_MOVE_END = Pattern.compile(
			"^(.+?) kart(?:ı|ını) (?:en )?(başa|öne|sona|arkaya) (?:al|taşı|koy|getir|götür)$");
	private static final Pattern CARD_MOVE_REL = Pattern.compile(
			"^(.+?) kart(?:ı|ını) (.+?) kart(?:ı|ının) (önüne|soluna|ardına|arkasına|sağına|yanına) (?:al|taşı|koy|getir)$");
	private static final Pattern CARD = Pattern.compile(
			"^(.+?) kart(?:ı|ına|ını|a|ına doğru)?(?: (geç|git|gel|dön|göster|getir|aç|kaydır|bul|başlat))?$");
	private static final Pattern SEEK_P = Pattern.compile(
			"^(?:(.+?) (saniye|sn|dakika|dk) )?(ileri|geri) (?:sar|al|atla)$|^(.+?) (saniye|sn|dakika|dk) (ileri|geri)$");
	private static final Pattern FEATURE_P = Pattern.compile(
			"^(.+?) (?:uyarı(?:sı|sını|larını|ları|yı)?|hatırlatıcı(?:sı|sını|yı)?|hatırlatma(?:sı|sını|yı)?|bildirim(?:i|ini|leri)?|bip(?:i|ini)?|kaydı(?:nı)?)"
					+ " (aç|kapat|etkinleştir|devre dışı bırak|sustur|başlat|durdur)$");
	private static final Pattern THEME_P = Pattern.compile(
			"^(.+?) tema(?:sı|sına|sını|yı)?(?: (?:geç|yap|uygula|aç|seç|olsun))?$|^tema(?:yı)? (.+?) (?:yap|olsun|seç)$");
	private static final Pattern MSG_REPLY_P = Pattern.compile(
			"^(?:yanıtla|yanıt ver|cevapla|cevap ver|cevap yaz|yanıt yaz|mesajı yanıtla|mesaja cevap ver)[:,]? (.+)$"
					+ "|^(.+?) (?:diye|olarak) (?:yanıtla|cevap ver|cevapla|yaz|yanıt ver)$");
	private static final Pattern REMIND = Pattern.compile(
			"^(.+? (?:dakika|dk|saat)) (?:sonra|içinde) (?:(.+?) )?(?:hatırlat|uyar|haber ver|söyle)$");
	private static final Pattern TIMER = Pattern.compile(
			"^(.+? (?:dakika|dk|saat))(?:lık|lik|luk|lük) (?:bir )?(?:zamanlayıcı|sayaç|alarm) (?:kur|başlat|ayarla)$"
					+ "|^(?:zamanlayıcı|sayaç|alarm)(?:ı|yı)? (.+? (?:dakika|dk|saat))(?:ya| için)? (?:kur|ayarla|başlat)$");
	private static final Pattern WIKI = Pattern.compile(
			"^(.+?) (?:nedir|ne demek|ne demektir|kimdir|kim|neresidir|nerededir|nerede bulunur)$"
					+ "|^(.+?) hakkında (?:bilgi ver|bilgi|ne biliyorsun|bir şey söyle|anlat)$");
	private static final Pattern CALC_P = Pattern.compile(
			"^(\\d+(?:[.,]\\d+)?) (artı|eksi|çarpı|kere|kez|bölü) (\\d+(?:[.,]\\d+)?)(?: kaç| kaç eder| eder| nedir| kaçtır| ediyor)?$");

	/** "15 çarpı 4 kaç" → "15 çarpı 4 = 60"; hesap değilse null. */
	static String calc(String c) {
		String t = words(c.replaceAll("^(hesapla|hesap yap|söyle) ", "")).trim().replaceAll("\\s+", " ");
		Matcher m = CALC_P.matcher(t);
		if (!m.matches()) return null;
		double a = Double.parseDouble(m.group(1).replace(',', '.')), b = Double.parseDouble(m.group(3).replace(',', '.'));
		String op = m.group(2);
		double r;
		switch (op) {
			case "artı" -> r = a + b;
			case "eksi" -> r = a - b;
			case "bölü" -> {
				if (b == 0) return "Sıfıra bölünmez";
				r = a / b;
			}
			default -> r = a * b;
		}
		String rs = (Math.abs(r - Math.rint(r)) < 1e-9) ? String.valueOf((long) Math.rint(r))
				: String.format(TR, "%.2f", r).replaceAll("0+$", "");
		return fmt(a) + " " + op + " " + fmt(b) + " = " + rs;
	}

	private static String fmt(double v) {
		return (Math.abs(v - Math.rint(v)) < 1e-9) ? String.valueOf((long) Math.rint(v)) : String.valueOf(v).replace('.', ',');
	}

	private static final Pattern ACTION_TAIL = Pattern.compile(
			" (aç|ac|izle|izlet|dinle|oynat|koy|başlat|getir|göster|goster|bul|aç bakalım)$");
	private static final Pattern PLAY_SONG = Pattern.compile(
			"^(.+?) (?:şarkısını |şarkılarını |parçasını |albümünü )?(?:çal|çalar mısın|çalsana|çalabilir misin)$"
					+ "|^(.+?) (?:şarkısını|şarkılarını|parçasını|albümünü) (?:aç|dinle|oynat|dinlet)$");

	/** Söylenen cümlenin niyeti; kanal adı olabilecek bir şeyse CHANNEL (arg = aranacak ad), anlaşılmazsa null. */
	public static Intent parse(String raw) {
		String c = clean(raw);
		if (c.isEmpty()) return null;
		Matcher m;

		// yardım, tekrar
		if (c.matches("(yardım|ne yapabilirsin|neler yapabilirsin|komutlar|ne diyebilirim|nasıl kullanılır)")) return Intent.of(Kind.HELP);
		if (c.matches("(tekrar et|tekrarla|bir daha|aynısını yap)")) return Intent.of(Kind.REPEAT);

		// notlar: "not al: süt al", "şunu not et lastikleri kontrol et", "notlarımı oku", "notları sil"
		if (c.matches("(notlarımı|notları|notlar) (oku|söyle|neler)|notlarım( neler)?|not(larım)? var mı")) return Intent.of(Kind.NOTE_READ);
		if (c.matches("(bütün |tüm )?notları (sil|temizle)")) return Intent.of(Kind.NOTE_CLEAR);
		if (c.matches("(şunu |bunu )?(not al|not et|not düş)\\b.+|.+ (diye )?(not al|not et|not düş)"))
			return Intent.of(Kind.NOTE_ADD, raw.trim());

		// yol süresi: "eve kaç dakika", "işe ne kadar sürer", "eve ne zaman varırım"
		if (c.matches("(eve|evime) (kaç dakika|ne kadar( sürer)?|ne zaman varırım|kaç saat)( var| kaldı)?"))
			return Intent.of(Kind.TRAFFIC, "home");
		if (c.matches("(işe|iş yerine|ofise) (kaç dakika|ne kadar( sürer)?|ne zaman varırım|kaç saat)( var| kaldı)?"))
			return Intent.of(Kind.TRAFFIC, "work");
		// spor: "maç sonuçları", "galatasaray maçı ne zaman", "fenerbahçe kaç kaç bitti"
		if (c.matches("(maç sonuçları|skorlar|süper lig( sonuçları)?|maçlar|bugünkü maçlar)"))
			return Intent.of(Kind.SPORTS, (String) null);
		m = Pattern.compile("^(.+?) (?:maçı|maçları|maçının sonucu) ?(?:ne zaman|kaçta|kaç kaç|ne oldu|bitti|sonucu)?.*$").matcher(c);
		if (m.matches() && !m.group(1).matches("(bugün|yarın|son)")) return Intent.of(Kind.SPORTS, m.group(1));

		// podcast: "fularsız entellik podcastini aç", "podcast dinle"
		m = Pattern.compile("^(.+?) (?:podcastini|podcasti|podkastını|podcast'ini) (?:aç|çal|dinle|oynat)$").matcher(c);
		if (m.matches()) return Intent.of(Kind.PODCAST, m.group(1));
		if (c.matches("podcast (aç|dinle|çal)|podcast dinlemek istiyorum")) return Intent.of(Kind.PODCAST, (String) null);

		// takvim: "bugün ne var", "takvimimde ne var", "toplantım ne zaman", "yarın ne var"
		if (c.matches("(bugün|yarın|bugün ve yarın) (ne var|neler var|programım ne|programımda ne var)|takvim(im|imde)?( ne var| neler var)?|"
				+ "(sıradaki |bir sonraki )?(toplantım|randevum|etkinliğim) (ne zaman|saat kaçta|kaçta)|ajandam(da ne var)?"))
			return Intent.of(Kind.CALENDAR);
		// araç bakımı: "muayene ne zaman", "sigortam ne zaman bitiyor", "kasko"
		if (c.matches(".*(muayene|sigorta|kasko|yağ değişimi|araç bakımı).*(ne zaman|kaç gün|bitiyor|kaldı|var mı).*|araç bakımı"))
			return Intent.of(Kind.CARCARE);

		// uyarı geçmişi: "son uyarılar", "hangi uyarıları verdin", "az önce ne dedin"
		if (c.matches("(son |hangi )?uyarı(lar|ları)?( neler| ne| verdin| oku| söyle| geçmişi)?|son uyarılar(ı oku)?|hangi uyarıları verdin"))
			return Intent.of(Kind.ALERTS);
		if (c.matches("(az önce|demin|biraz önce) ne (dedin|söyledin)|ne dedin|anlamadım tekrar söyle"))
			return Intent.of(Kind.ALERTS, 1);

		// varılacak yerin otoparkları: "gideceğim yerde otopark var mı", "kızılayda otopark", "varınca nereye park ederim"
		if (c.matches("(gideceğim|varacağım|gittiğim) yer(de|in yakınında)? (otopark|park yeri)( var mı| nerede)?|varınca (nereye park ederim|otopark)"))
			return Intent.of(Kind.PARK_DEST);
		Matcher pm = Pattern.compile("^(.+?)(?:da|de|ta|te)(?:ki)? (?:otopark|park yeri)(?: var mı| nerede| bul)?$").matcher(c);
		if (pm.matches() && !pm.group(1).matches("(burası|bura|yakın|yakını|en yakın)")) return Intent.of(Kind.PARK_DEST, pm.group(1));

		// telefon durumu: "ne kadar veri kullandım", "internetim ne kadar kaldı", "telefon durumu"
		if (c.matches(".*(veri|internet|kota)(m|im|imi|imiz|yi|i|ı)? (ne kadar|kaç)( kaldı| kullandım| gitti| harcadım)?.*")
				|| c.matches("(ne kadar|kaç) (veri|internet|mb|gb) (kullandım|harcadım|gitti)")
				|| c.matches("telefon(un)? durumu( nasıl)?"))
			return Intent.of(Kind.STATUS);

		// bilgi: "dolar kaç", "altın ne kadar", "akşam ezanı ne zaman", "haberleri oku"
		if (c.matches(".*(dolar|euro|avro|sterlin|döviz|kur(lar)?|gram altın|çeyrek altın|altın) (kaç|ne kadar|kaçtan|fiyatı|olmuş|kuru).*")
				|| c.matches("(dolar|euro|döviz|altın|kurlar|döviz kurları|altın fiyatları)")) {
			String w = c.contains("çeyrek") ? "Çeyrek altın" : c.contains("altın") ? "Gram altın" : c.contains("euro") || c.contains("avro")
					? "Euro" : c.contains("sterlin") ? "Sterlin" : c.contains("dolar") ? "Dolar" : null;
			return Intent.of(Kind.FX, w);
		}
		if (c.matches(".*(namaz|ezan|imsak|iftar|sahur|öğle|ikindi|akşam|yatsı).*(vakti|vakitleri|ne zaman|kaçta|saat kaç).*")
				|| c.matches("(namaz vakitleri|ezan vakitleri|ezan ne zaman|namaz ne zaman)")) {
			String w = c.contains("imsak") || c.contains("sahur") ? "İmsak" : c.contains("öğle") ? "Öğle"
					: c.contains("ikindi") ? "İkindi" : (c.contains("akşam") || c.contains("iftar")) ? "Akşam" : c.contains("yatsı") ? "Yatsı" : null;
			return Intent.of(Kind.PRAYER, w);
		}
		if (c.matches("(haberleri|haber|son dakika|son dakika haberlerini|manşetleri|gündemi) ?(oku|anlat|neler|ne var|söyle)?")
				|| c.matches("(bugün )?(neler oluyor|ne haber var|haberlerde ne var)"))
			return Intent.of(Kind.NEWS);

		// görünüm: "telefon görünümüne geç", "araç görünümü", "ızgara görünümü"
		if (c.matches("(telefon|ızgara) (görünümü|görünümüne geç|görünümünü aç|modu|moduna geç)"))
			return Intent.of(Kind.VIEW_MODE, 1);
		if (c.matches("(araç|deste|kart) (görünümü|görünümüne geç|görünümünü aç|modu|moduna geç)"))
			return Intent.of(Kind.VIEW_MODE, 0);

		// video kalitesi: "video kalitesini 720 yap", "kaliteyi düşür", "görüntü kalitesi otomatik"
		if (c.matches(".*(video kalite|kaliteyi|kalitesini|görüntü kalite|çözünürlü).*")) return Intent.of(Kind.VIDEO_Q, c);

		// ekolayzer: "rock ekolayzer", "ekolayzeri kapat", "ses modunu konuşma yap", "bas ses ayarı"
		if (c.matches(".*(ekolayz|ekolaz|ekualayz|ses modu|ses ayarı).*"))
			return Intent.of(Kind.EQ, me.aap.fermata.media.engine.YoldaEq.spoken(c));

		// kart: "radyo kartına geç", "hava durumu kartı", "kişiler kartını göster"
		if (c.matches("kart(ları)?(mı)? düzenle|kartları sırala|kartları düzenleyelim")) return Intent.of(Kind.EDIT_CARDS);
		// kart yönetimi: "hava durumu kartını gizle", "podcast kartını geri getir", "radyo kartını başa al",
		// "spor kartını trafik kartının ardına taşı"
		if ((m = CARD_MOVE_REL.matcher(c)).matches()) {
			String w = m.group(3).matches("önüne|soluna") ? "before" : "after";
			return Intent.of(Kind.CARD_MOVE, m.group(1).trim() + "|" + w + "|" + m.group(2).trim());
		}
		if ((m = CARD_MOVE_END.matcher(c)).matches())
			return Intent.of(Kind.CARD_MOVE, m.group(1).trim() + "|" + (m.group(2).matches("başa|öne") ? "first" : "last"));
		if ((m = CARD_HIDE_P.matcher(c)).matches()) return Intent.of(Kind.CARD_HIDE, m.group(1).trim());
		if ((m = CARD_SHOW_P.matcher(c)).matches()) return Intent.of(Kind.CARD_SHOW, m.group(1).trim());
		if ((m = CARD.matcher(c)).matches()) {
			String n = m.group(1).replaceAll("^(ana sayfada|destede|kartlarda) ", "").trim();
			if (n.matches("ana|ana sayfa|ev")) return Intent.of(Kind.HOME);
			if (n.matches("(bir )?(sonraki|sağdaki|diğer|öbür)")) return Intent.of(Kind.NEXT_CARD);
			if (n.matches("(bir )?(önceki|soldaki)")) return Intent.of(Kind.PREV_CARD);
			String verb = m.group(2);
			if (!n.isEmpty()) return Intent.of((("aç".equals(verb)) || ("başlat".equals(verb))) ? Kind.CARD_OPEN : Kind.CARD, n);
		}
		if (c.matches("(sağa|sola) kaydır|(sağdaki|soldaki) karta geç")) return Intent.of(c.startsWith("sağ") ? Kind.NEXT_CARD : Kind.PREV_CARD);

		// sarma: "10 saniye ileri sar", "geri sar", "başa sar"
		if (c.matches("başa sar|baştan (başlat|başla|çal|oynat)|en başa (dön|sar|git)|başından (başlat|çal)|şarkıyı baştan çal"))
			return Intent.of(Kind.SEEK_START);
		if ((m = SEEK_P.matcher(c)).matches()) {
			String amount = (m.group(1) != null) ? m.group(1) : m.group(4);
			String unit = (m.group(2) != null) ? m.group(2) : m.group(5);
			String dir = (m.group(3) != null) ? m.group(3) : m.group(6);
			int n = (amount == null) ? 15 : number(amount);
			if (n > 0) {
				if ((unit != null) && unit.startsWith("d")) n *= 60;
				return Intent.of(Kind.SEEK, "geri".equals(dir) ? -n : n);
			}
		}

		// uyarı denemesi, Hey Yolda'yı kapat, harita uygulaması
		if (c.matches("(radar )?uyarı(yı|sını)? (dene|test et)|deneme uyarısı( ver)?|uyarıyı göster"))
			return Intent.of(Kind.TEST_ALERT);
		if (c.matches("(hey )?yolda'?(yı|yi)? (kapat|sustur|devre dışı bırak)|uyandırma(yı)? kapat|beni dinleme(yi)? (bırak|kapat)|dinlemeyi kapat"))
			return Intent.of(Kind.WAKE_OFF);
		if (c.matches("waze'?(ı|i|yi)? (kullan|seç)|waze ile (git|gidelim)|haritada waze kullan")) return Intent.of(Kind.NAV_APP, "waze");
		if (c.matches("(google )?haritalar'?(ı)? (kullan|seç)|google maps'?(i)? (kullan|seç)|google haritalar ile (git|gidelim)"))
			return Intent.of(Kind.NAV_APP, "google");

		// gün doğumu / batımı
		if (c.matches("güneş (ne zaman|saat kaçta|kaçta) (batıyor|batacak|batar)|gün batımı( ne zaman| saat kaçta| kaçta)?|hava ne zaman kararıyor|akşam ezanı.*"))
			return Intent.of(Kind.SUN, "set");
		if (c.matches("güneş (ne zaman|saat kaçta|kaçta) (doğuyor|doğacak|doğar)|gün doğumu( ne zaman| saat kaçta| kaçta)?|hava ne zaman aydınlanıyor"))
			return Intent.of(Kind.SUN, "rise");

		// sürüş yardımcıları: "okul uyarısını kapat", "mola hatırlatıcısını aç"
		if ((m = FEATURE_P.matcher(c)).matches()) {
			String key = featureKey(m.group(1).trim());
			if (key != null) return of(Kind.FEATURE, key, m.group(2).matches("aç|etkinleştir|başlat") ? 1 : 0);
		}

		// mesaj: "yanıtla geliyorum", "geliyorum diye cevap ver", "mesajı oku"
		if ((m = MSG_REPLY_P.matcher(c)).matches()) {
			String txt = ((m.group(1) != null) ? m.group(1) : m.group(2)).trim();
			if (!txt.isEmpty()) return Intent.of(Kind.MSG_REPLY, txt);
		}
		if (c.matches("(son )?mesaj(ı|ları)? (tekrar )?oku|mesajı tekrar oku|ne yazmış|kim yazdı|son mesaj ne")) return Intent.of(Kind.MSG_READ);

		// hatırlatıcı / zamanlayıcı ("10 dakika sonra hatırlat", "5 dakikalık zamanlayıcı kur")
		if (c.matches("(hatırlatma(yı|ları)?|hatırlatıcı(yı)?) (iptal et|sil|kapat)|zamanlayıcı(yı)? iptal et ve hatırlatmayı sil"))
			return Intent.of(Kind.TIMER_CANCEL);
		if ((m = REMIND.matcher(c)).matches()) {
			int min = minutes(m.group(1));
			String what = (m.group(2) == null) ? "" : m.group(2).replaceAll("^(bana|beni)( |$)", "").replaceAll(" (diye|olarak)$", "").trim();
			if (min > 0) return of(Kind.TIMER, what, min);
		}
		if ((m = TIMER.matcher(c)).matches()) {
			int min = minutes((m.group(1) != null) ? m.group(1) : m.group(2));
			if (min > 0) return of(Kind.TIMER, "", min);
		}

		// kısa sohbet ("merhaba", "nasılsın", "teşekkürler", "acıktım")
		if (YoldaChat.is(c)) return Intent.of(Kind.CHAT, c);

		// hesap ("15 çarpı 4 kaç", "100 bölü 8")
		String calc = calc(c);
		if (calc != null) return Intent.of(Kind.CALC, calc);

		// uyku zamanlayıcısı
		if (c.matches("(uyku )?zamanlayıcı(yı|sını)? (iptal et|kapat|durdur|sil)|kapatmayı iptal et")) return Intent.of(Kind.SLEEP_CANCEL);
		if ((m = SLEEP.matcher(c)).matches()) {
			int min = minutes((m.group(1) != null) ? m.group(1) : m.group(2));
			if ((min <= 0) && c.startsWith("uyku zamanlayıcı")) min = 30; // "uyku zamanlayıcısını kur": varsayılan yarım saat
			if (min > 0) return Intent.of(Kind.SLEEP, min);
		}

		// ses
		if (c.matches("ses(i)? (en sona|sonuna kadar|tamamen|en yükseğe) (aç|çıkar|yükselt)|ses(i)? (son|tam) ses|sonuna kadar aç"))
			return Intent.of(Kind.VOL_SET, 100);
		if (c.matches("ses(i)? (yarıya|yarım) (indir|getir|yap|al)|ses(i)? yarı")) return Intent.of(Kind.VOL_SET, 50);
		if ((m = VOL_SET.matcher(c)).matches() && (number(m.group(1)) >= 0) && m.group(1).matches("[^a-z]*\\d.*|.*(bir|iki|üç|dört|beş|altı|yedi|sekiz|dokuz|on|yirmi|otuz|kırk|elli|altmış|yetmiş|seksen|doksan|yüz).*")
				&& !m.group(1).matches(".*(aç|kıs|azalt|artır|yükselt|düşür).*")) {
			int n = number(m.group(1));
			// "ses 7" (0-15 aralığı telefon düzeyi gibi) ya da yüzde
			return Intent.of(Kind.VOL_SET, (c.contains("yüzde") || (n > 15)) ? Math.min(100, n) : Math.min(100, n * 100 / 15));
		}
		if (c.matches("(sesi |ses )?(aç|yükselt|artır|arttır)|ses(i)? (biraz )?(aç|yükselt|artır|arttır)|daha yüksek|biraz daha yüksek|sesi biraz aç"))
			return Intent.of(Kind.VOL_UP);
		if (c.matches("ses(i)? (biraz )?(kıs|azalt|düşür|indir)|daha kısık|biraz kıs|sesi biraz kıs")) return Intent.of(Kind.VOL_DOWN);
		if (c.matches("(sesi )?(kapat|sustur)|sessiz(e al)?|sesi sıfırla")) {
			if (c.equals("kapat")) return Intent.of(Kind.STOP); // "kapat" tek başına: çalan şeyi kapat
			return Intent.of(Kind.MUTE);
		}

		// bilgi
		if (c.matches("saat kaç.*|saat ne.*|saati söyle")) return Intent.of(Kind.TIME);
		if (c.matches("(bugün )?(ayın kaçı|günlerden ne|hangi gün|tarih(i)? (ne|kaç|söyle)|bugün ne günü|bugünün tarihi( ne)?|bugün tarih ne)|tarih"))
			return Intent.of(Kind.DATE);
		if (c.matches("(şarj(ım)?|pil(im)?|batarya(m)?|telefonun şarjı|şarj durumu|pil durumu)( kaç| ne durumda| ne kadar| yüzde kaç)?|kaç şarjım var"))
			return Intent.of(Kind.BATTERY);
		if (c.matches("(kaçla|kaç km ile|kaç kilometreyle|ne hızla|kaç hızla|hangi hızla) gidiyorum|hızım (kaç|ne)|hızımı söyle|ne kadar hızlı gidiyorum"))
			return Intent.of(Kind.SPEED);
		if (c.matches("(şu an |şimdi )?(neredeyim|nerdeyim|konumum ne|konumum nerede|neresi burası|burası neresi|hangi ilçedeyiz|neredeyiz|hangi şehirdeyiz)"))
			return Intent.of(Kind.WHERE_AM_I);
		if (c.matches("yarın (hava )?(nasıl|nasıl olacak|kaç derece|yağmur var mı|yağmurlu mu|yağacak mı)( olacak)?|yarınki hava( durumu)?|yarın için hava( durumu)?|yarın hava durumu"))
			return Intent.of(Kind.FORECAST, "yarın");
		if (c.matches("(bugün )?(yağmur|kar) (yağacak mı|var mı|yağar mı|yağıyor mu)|şemsiye (alayım mı|lazım mı)|yağış var mı|haftalık hava( durumu)?|hafta(lık)? hava nasıl|bu hafta hava( nasıl)?"))
			return Intent.of(Kind.FORECAST, c.contains("hafta") ? "hafta" : "yağış");
		if (c.matches("(bugün |yarın )?hava (nasıl|durumu|ne durumda|kaç derece).*|hava durumu.*|dışarısı kaç derece.*|kaç derece"))
			return Intent.of(Kind.WEATHER);
		if (c.matches("(şimdi )?ne çalıyor|bu ne şarkı|bu şarkı ne|bu hangi şarkı|hangi kanal(dayız)?|ne dinliyoruz"))
			return Intent.of(Kind.WHAT_PLAYING);

		// favoriler
		if (c.matches("(bunu |bu şarkıyı |bu kanalı |bu radyoyu )?favori(lere|ye|lerime)? (ekle|kaydet)|favorile|favorilere al"))
			return Intent.of(Kind.FAV_ADD);
		if ((m = FAV_OPEN.matcher(c)).matches()) return Intent.of(Kind.FAV_OPEN, ((m.group(1) != null) ? m.group(1) : m.group(2)).trim());

		// tema
		if (c.matches("tema(yı)? değiştir|renkleri değiştir|başka tema|temayı değiştir")) return Intent.of(Kind.THEME_NEXT);
		if (c.matches("(otomatik tema|tema(yı)? otomatik( yap)?|gece gündüz tema(sı)?)")) return Intent.of(Kind.THEME_AUTO);
		if (c.matches("gece modu(na geç| aç)?|karanlık mod(a geç)?")) return Intent.of(Kind.THEME_SET, "gece");
		if ((m = THEME_P.matcher(c)).matches()) {
			String n = ((m.group(1) != null) ? m.group(1) : m.group(2)).trim();
			if (!n.matches("(otomatik|başka|yeni|bir)")) return Intent.of(Kind.THEME_SET, n);
		}

		// radar
		if (c.matches("radar(lar)?(ı|ları)? (uyarı(sı|ları)?(nı)? )?(kapat|durdur|sustur|devre dışı bırak)|radar uyarısını kapat"))
			return Intent.of(Kind.RADAR_OFF);
		if (c.matches("radar(lar)?(ı|ları)? (uyarı(sı|ları)?(nı)? )?(aç|başlat|etkinleştir)|radar uyarısını aç"))
			return Intent.of(Kind.RADAR_ON);
		if (c.matches("(burada|burda|şurada) radar var|buraya radar ekle|radar ekle|bunu radar olarak (ekle|kaydet)"))
			return Intent.of(Kind.RADAR_ADD);
		if (c.matches("((önde|yakında|ileride|yolda) )?radar var mı|radar nerede|en yakın radar( nerede)?|(burada |bu yolda )?hız sınırı (kaç|ne)"
				+ "|kaçla gidebilirim|sınır kaç")) return Intent.of(Kind.RADAR_INFO);

		// park yeri, yolculuk
		if (c.matches("(arabam|aracım|arabamı|aracımı) nerede|(arabamı|aracımı) (nereye|nerede) (park ettim|bıraktım)|arabama (git|götür)|aracıma (git|götür)|park yerim nerede"))
			return Intent.of(Kind.PARK_WHERE);
		if (c.matches("(buraya|burada) park ettim|park yerini kaydet|park ettim")) return Intent.of(Kind.PARK_SET);
		if (c.matches("(son )?yolculuk (özeti|nasıldı)|ne kadar yol (yaptım|gittim)|kaç (km|kilometre) (yaptım|gittim)|yolculuğum nasıldı"))
			return Intent.of(Kind.TRIP_INFO);

		// sesli yanıt ayarı
		if (c.matches("(sesli )?yanıt(ları)?(nı)? kapat|konuşma(yı)? kapat|sessiz yanıt|sesli yanıtı kapat|sus artık")) return Intent.of(Kind.SPEAK_OFF);
		if (c.matches("(sesli )?yanıt(ları)?(nı)? aç|konuşma(yı)? aç|sesli yanıtı aç|benimle konuş")) return Intent.of(Kind.SPEAK_ON);

		// oynatma
		if (c.matches("(radyoyu|müziği|şarkıyı|tv'yi|tvyi|televizyonu|videoyu|yayını|kanalı|çalanı) (kapat|durdur|sustur)|oynatıcıyı kapat|kapat"))
			return Intent.of(Kind.STOP);
		if (c.matches("(duraklat|durdur|bekle|dur|pause|biraz dur)")) return Intent.of(Kind.PAUSE);
		if (c.matches("(devam et|devam|oynat|başlat|çal|sürdür)")) return Intent.of(Kind.PLAY);
		if (c.matches("kaldığım yerden( devam( et)?)?|kaldığımız yerden( devam( et)?)?|son çalanı (aç|çal)")) return Intent.of(Kind.RESUME);
		if (c.matches("(sonraki|ileri|sıradaki|geç|atla|sonrakine geç|sonraki şarkı|sonraki kanal|sonraki video|diğer kanal|başka kanal|kanalı değiştir)"))
			return Intent.of(Kind.NEXT);
		if (c.matches("(önceki|geri|önceki şarkı|önceki kanal|önceki video|bir önceki)")) return Intent.of(Kind.PREV);

		// ekran
		if (c.matches("ana ?sayfa(ya|yı)?( dön| git| aç)?|ana ekran(a|ı)?( dön| git| aç)?|başa dön")) return Intent.of(Kind.HOME);
		if (c.matches("geri (dön|git|gel)|bir önceki ekran")) return Intent.of(Kind.BACK);

		// navigasyon
		if (c.matches("(eve|evime) (git|götür|gidelim|yol tarifi( ver)?|dönelim)")) return Intent.of(Kind.NAV_HOME);
		if (c.matches("(işe|işyerine|iş yerine|ofise) (git|götür|gidelim|yol tarifi( ver)?)")) return Intent.of(Kind.NAV_WORK);
		if ((m = NEARBY.matcher(c)).matches()) return Intent.of(Kind.NAV_NEARBY, nearbyQuery(m.group(1)));
		if ((m = WIKI.matcher(c)).matches()) {
			String q = ((m.group(1) != null) ? m.group(1) : m.group(2)).replaceAll("['’](nin|nın|nun|nün|in|ın|un|ün)$", "").trim();
			if ((q.length() >= 2) && !q.matches("(bu|şu|o|bu şarkı|bu kanal|saat|hava|tarih)")) return Intent.of(Kind.WIKI, q);
		}
		if ((m = NAV_DEST.matcher(c)).matches()) {
			String d = m.group(1).replaceAll("['’]$", "").trim();
			String k = appKey(d.replaceAll("['’]?(y?[ae]|n[ae])$", ""));
			if (k == null) k = appKey(d);
			if (k != null) return Intent.of(Kind.OPEN_APP, k); // "YouTube'a git", "müziğe git"
			if (!d.isEmpty() && !d.matches("(geri|ileri|sonraki|önceki)")) return Intent.of(Kind.NAV_TO, d);
		}

		// YouTube araması
		if ((m = YT.matcher(c)).matches()) {
			String q = ((m.group(1) != null) ? m.group(1) : m.group(2)).trim();
			if (!q.isEmpty()) return Intent.of(Kind.YT_SEARCH, q);
		}

		// telefonla arama: "annemi ara", "Ahmet'e telefon et", "Ahmet Yılmaz'ı ara"
		if ((m = CALL.matcher(c)).matches()) {
			String who = ((m.group(1) != null) ? m.group(1) : m.group(2)).trim();
			if (!who.isEmpty() && !who.matches("(kanal|kanalı|radyo|radyoyu|şarkı|şarkıyı|video|videoyu|müzik|müziği|youtube|"
					+ "internet|google|haber|haberleri|eczane|benzinlik|yer|adres|bir şey|birşey|.* kanal|.* radyo)"))
				return Intent.of(Kind.CALL, who);
		}

		// radyo araması ("Kral FM", "Joy Türk radyosunu çal")
		String radio = radioQuery(c);
		if (radio != null) return Intent.of(Kind.RADIO_SEARCH, radio);

		// şarkı
		if ((m = PLAY_SONG.matcher(c)).matches()) {
			String q = ((m.group(1) != null) ? m.group(1) : m.group(2)).trim();
			if (q.matches("(bir )?(müzik|muzik|şarkı|sarki|şarkılar)")) q = "Türkçe pop";
			if (!q.isEmpty() && !q.matches("(radyo|tv|kanal)")) return Intent.of(Kind.SONG, q);
		}

		// uygulamalar
		String app = ACTION_TAIL.matcher(c).replaceAll("").replaceAll(" (ekranı|ekranını|sayfası|sayfasını|uygulaması)$", "").trim();
		String key = appKey(app);
		if (key != null) return Intent.of(Kind.OPEN_APP, key);

		// kanal / radyo adı olabilir
		String q = OBJ_SUFFIX.matcher(app).replaceAll("").trim();
		return (q.length() >= 2) ? Intent.of(Kind.CHANNEL, q) : null;
	}

	/** Sürüş yardımcısının adı → ayar anahtarı; bilinmiyorsa null. */
	static String featureKey(String s) {
		String t = s.replaceAll("^(sesli )?", "");
		if (t.matches("okul|okul bölgesi|hastane|okul ve hastane|bölge|okul ve hastane bölgesi")) return "zones";
		if (t.matches("mola|yorgunluk|dinlenme|mola zamanı")) return "fatigue";
		if (t.matches("ortalama hız|koridor|hız koridoru|ortalama hız koridoru")) return "avg";
		if (t.matches("tesis|dinlenme tesisi|mola yeri|yol üstü|yol üstü mola")) return "stops";
		if (t.matches("hız aşımı|hız|hız sınırı|aşım")) return "overspeed";
		if (s.matches("sesli|sesli radar|radar sesi|ses")) return "voice";
		if (t.matches("park|park yeri")) return "park";
		if (t.matches("yolculuk|yolculuk geçmişi|yolculuk özeti")) return "trips";
		if (t.matches("resmi radar|bakanlık|bakanlık radar")) return "official";
		return null;
	}

	/** Ekran adı → uygulama anahtarı (music, youtube, pharmacy, nav, channels, phone, menu, web, favs); değilse null. */
	static String appKey(String app) {
		return switch (app) {
			case "youtube music", "yutup müzik", "müzik", "muzik", "müzikler", "müzik çalar", "müziğ", "müzikleri" -> "music";
			case "youtube", "yutup", "yutub", "youtube'u", "yutubu" -> "youtube";
			case "eczane", "eczaneler", "nöbetçi eczane", "nöbetçi eczaneler", "nöbetçi", "nöbetçi eczaneyi" -> "pharmacy";
			case "navigasyon", "harita", "haritalar", "yol tarifi", "haritayı", "navigasyonu" -> "nav";
			case "kanallar", "tv", "televizyon", "radyo", "radyolar", "canlı yayın", "kanal listesi" -> "channels";
			case "telefon", "telefonu kullan", "telefon ekranı", "telefonu" -> "phone";
			case "menü", "menu", "ayarlar", "ayarları" -> "menu";
			case "tarayıcı", "internet", "web", "google" -> "web";
			case "favoriler", "favorilerim", "favorileri" -> "favs";
			case "radar", "radarlar", "radarları", "radar ekranı", "hız sınırı", "sürüş", "sürüş ekranı", "park yerlerim",
					 "park yerleri", "yolculuklarım", "yolculuk geçmişi" -> "radar";
			case "hava durumu ekranı", "hava ekranı", "hava durumu sayfası", "hava tahmini" -> "weather";
			default -> null;
		};
	}

	private static final Pattern OBJ_SUFFIX = Pattern.compile("['’]?(yı|yi|yu|yü|ı|i|u|ü|nı|ni|nu|nü)$");

	private static final Pattern RADIO_WORDS = Pattern.compile(
			// Android UNICODE_CHARACTER_CLASS'ı desteklemiyor (sınıf açılırken çöküyordu): harf sınırı elle
			"(?<![\\p{L}\\p{N}])(radyosunu|radyosu|radyo|aç|ac|çal|cal|dinle|dinlet|açar mısın|lütfen|istasyonu)(?![\\p{L}\\p{N}])");

	/** "Kral FM aç", "Joy Türk radyosu", "radyo Metro FM" → aranacak istasyon adı; radyo isteği değilse null. */
	static String radioQuery(String c) {
		if (c == null) return null;
		boolean radio = c.contains("radyo") || c.matches(".*\\bfm\\b.*");
		if (!radio) return null;
		String q = RADIO_WORDS.matcher(c).replaceAll(" ").replaceAll("\\s+", " ").trim();
		return ((q.length() >= 2) && !q.matches("(radyo\\p{L}*|fm|kanallar)")) ? q : null;
	}

	/** "benzinliğe", "eczaneye", "marketi" → aranacak yer: harita uygulamasının anlayacağı ad. */
	static String nearbyQuery(String s) {
		String t = s.trim().replace("'", "");
		if (t.startsWith("nöbetçi eczane")) return "nöbetçi eczane";
		String[] tries = {t, t.replaceAll("(y[aeıi]|n[ae])$", ""), t.replaceAll("[aeıi]$", "")};
		for (String c : tries) {
			String k = place(c.replaceAll("ğ$", "k")); // benzinliğ → benzinlik
			if (k != null) return k;
		}
		return t.replaceAll("(y[aeıi]|n[ae])$", "");
	}

	private static String place(String t) {
		return switch (t) {
			case "benzinlik", "benzin istasyonu", "akaryakıt", "akaryakıt istasyonu", "petrol ofisi", "istasyon",
					 "benzinci" -> "benzin istasyonu";
			case "eczane" -> "eczane";
			case "hastane", "acil", "sağlık ocağı" -> "hastane";
			case "otopark", "park yeri" -> "otopark";
			case "market", "bakkal", "süpermarket" -> "market";
			case "tuvalet", "wc", "lavabo" -> "tuvalet";
			case "dinlenme tesisi", "mola yeri", "tesis" -> "dinlenme tesisi";
			case "şarj istasyonu", "şarj" -> "elektrikli araç şarj istasyonu";
			case "restoran", "lokanta", "yemek yeri", "restaurant", "yemek" -> "restoran";
			case "kafe", "kahveci" -> "kafe";
			case "atm", "bankamatik" -> "atm";
			case "cami" -> "cami";
			default -> null;
		};
	}

	// ---- kanal adı eşleşmesi (telaffuza dayanıklı) ----

	/** Karşılaştırma biçimi: küçük harf, aksansız, yalnız harf / rakam; "show tv" ≈ "şov tivi". */
	static String phon(String s) {
		if (s == null) return "";
		String l = s.toLowerCase(TR).replace('ı', 'i');
		l = Normalizer.normalize(l, Normalizer.Form.NFD).replaceAll("\\p{Mn}+", "").replaceAll("[^a-z0-9 ]", "");
		l = (" " + l + " ").replace("tivi", "tv").replace(" te ve ", " tv ").replace("sh", "s").replace("w", "v")
				.replace("x", "ks").replace("q", "k").replace("ph", "f").replace(" di ", " d ").replace(" dee ", " d ");
		return l.replaceAll("\\s+", "");
	}

	/** Söylenen ad ile kanal adı ne kadar tutuyor (0 = hiç; 100 = aynı). */
	public static int channelScore(String name, String spoken) {
		String n = phon(name), q = phon(spoken);
		if (n.isEmpty() || q.length() < 2) return 0;
		if (n.equals(q)) return 100;
		if (n.startsWith(q) && (q.length() >= 3)) return 80;
		if (q.startsWith(n) && (n.length() >= 3)) return 70; // "trt haber canlı" → TRT Haber
		if (n.contains(q) && (q.length() >= 4)) return 50;
		int d = lev(n, q);
		int tol = Math.max(1, Math.min(n.length(), q.length()) / 5);
		// Rakamlar aynı olmalı: "TRT 3" denince TRT 1 açılmasın
		boolean sameDigits = n.replaceAll("[^0-9]", "").equals(q.replaceAll("[^0-9]", ""));
		if ((n.length() >= 4) && (d <= tol) && sameDigits) return 60 - 5 * d; // küçük telaffuz farkı ("kanal di", "tirete haber")
		return 0;
	}

	static int lev(String a, String b) {
		int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) prev[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int c = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
}
