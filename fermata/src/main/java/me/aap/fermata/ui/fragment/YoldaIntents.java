package me.aap.fermata.ui.fragment;

import java.text.Normalizer;
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
		YT_SEARCH, SONG, RADIO_SEARCH, RESUME, HELP, REPEAT, THEME_AUTO, SPEAK_ON, SPEAK_OFF, RADAR_ON, RADAR_OFF, RADAR_INFO, RADAR_ADD, CHANNEL
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
		c = WAKE.matcher(c).replaceFirst("");
		c = POLITE_HEAD.matcher(c).replaceFirst("");
		c = POLITE_TAIL.matcher(c).replaceFirst("");
		c = c.replaceAll(" (açar|açabilir|açsana|açın)( mı| mi)?$", " aç").replaceAll(" (çalar|çalsana|çalabilir)( mı| mi)?$", " çal");
		return c.trim();
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
		StringBuilder b = new StringBuilder();
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

		// radar
		if (c.matches("radar(lar)?(ı|ları)? (uyarı(sı|ları)?(nı)? )?(kapat|durdur|sustur|devre dışı bırak)|radar uyarısını kapat"))
			return Intent.of(Kind.RADAR_OFF);
		if (c.matches("radar(lar)?(ı|ları)? (uyarı(sı|ları)?(nı)? )?(aç|başlat|etkinleştir)|radar uyarısını aç"))
			return Intent.of(Kind.RADAR_ON);
		if (c.matches("(burada|burda|şurada) radar var|buraya radar ekle|radar ekle|bunu radar olarak (ekle|kaydet)"))
			return Intent.of(Kind.RADAR_ADD);
		if (c.matches("((önde|yakında|ileride|yolda) )?radar var mı|radar nerede|en yakın radar( nerede)?|(burada |bu yolda )?hız sınırı (kaç|ne)"
				+ "|kaçla gidebilirim|sınır kaç")) return Intent.of(Kind.RADAR_INFO);

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
			case "radar", "radarlar", "radarları", "radar ekranı", "hız sınırı" -> "radar";
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
			case "restoran", "lokanta", "yemek yeri" -> "restoran";
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
