package me.aap.fermata.ui.fragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Yolda V2 kart destesi: ana ekrandaki kartların sırası ve kullanıcının eklediği kartlar.
 * Sabit kartlar: YouTube, Müzik, TV, Radyo, Telefon, Tarayıcı, Ayarlar, Ekle. Kullanıcı TV kanalı,
 * radyo kanalı, YouTube kanalı ve web sayfası kartları ekleyebilir; kartlar sıralanabilir, gizlenebilir.
 * Saf Java (Android'e bağlı değil): birim testleriyle denetlenir. Kayıt biçimi düz metindir.
 */
public final class YoldaDeck {
	public static final String YOUTUBE = "youtube";
	public static final String MUSIC = "music";
	public static final String TV = "tv";
	public static final String RADIO = "radio";
	public static final String NAV = "nav";
	public static final String RESUME = "resume";
	public static final String FAVS = "favs";
	public static final String WEATHER = "weather";
	public static final String PHARMACY = "pharmacy";
	/** Akaryakıt fiyatları ve yakıt maliyeti. */
	public static final String FUEL = "fuel";
	/** Ekolayzer hazır ayarları. */
	public static final String EQ = "eq";
	/** Bilgi kartları: döviz ve altın, namaz vakitleri, haberler. */
	public static final String FX = "fx", PRAYER = "prayer", NEWS = "news";
	/** Telefon durumu: pil, bağlantı, mobil veri ve sınırı, hızlı ayarlar. */
	public static final String STATUS = "status";
	/** Elektrikli araç şarj istasyonları. */
	public static final String CHARGER = "charger";
	/** Sesli notlar ve hatırlatmalar. */
	public static final String NOTES = "notes";
	/** Hızlı mesaj: hazır cevaplar, varınca haber ver. */
	public static final String QUICKMSG = "quickmsg";
	/** Takvim (bugün / yarın) ve araç bakım defteri. */
	public static final String CALENDAR = "calendar", CARCARE = "carcare";
	/** Podcast abonelikleri. */
	public static final String PODCAST = "podcast";
	/** Telefondaki videolar ve fotoğraflar. */
	public static final String MEDIA = "media";
	/** Eve / işe yol süresi; Süper Lig sonuçları. */
	public static final String TRAFFIC = "traffic", SPORTS = "sports";
	/** Son depremler (EMSC, 300 km). */
	public static final String QUAKE = "quake";
	public static final String RADAR = "radar";
	/** Park yerleri (aracım). */
	public static final String PARK = "park";
	/** Sürüş geçmişi: bütün yolculuklar ve ayrıntıları. */
	public static final String TRIPS = "trips";
	public static final String CALLS = "calls";
	public static final String PHONE = "phone";
	public static final String MIRROR = "mirror";
	public static final String WEB = "web";
	public static final String SETTINGS = "settings";
	public static final String ADD = "add";
	/** Kullanıcı kartları. */
	public static final String TV_CHANNEL = "tvch";
	public static final String RADIO_CHANNEL = "radioch";
	public static final String RADIO_STREAM = "stream";
	public static final String YT_CHANNEL = "ytch";
	public static final String URL = "url";

	static final String[] DEFAULT = {RESUME, YOUTUBE, MUSIC, PODCAST, MEDIA, TV, RADIO, FAVS, NAV, RADAR, PARK, TRIPS, WEATHER, PHARMACY, FUEL, FX, PRAYER, NEWS, STATUS, CHARGER, NOTES, QUICKMSG, CALENDAR, CARCARE, TRAFFIC, SPORTS, QUAKE, PHONE, CALLS, MIRROR, WEB, EQ, SETTINGS,
			ADD};
	private static final char FIELD = '\u001F';
	private static final char RECORD = '\u001E';

	/**
	 * Bir kart. {@code key} destede tektir. Sabit kartlarda {@code key == type}. Kullanıcı kartlarında
	 * {@code data}: kanal kimliği (tvch, radioch), akış adresi (stream), arama sözcüğü (ytch) ya da adres (url).
	 */
	public record Card(String key, String type, String title, String data, @Nullable String logo) {
		public boolean isBuiltIn() {
			return key.equals(type);
		}

		public boolean isRemovable() {
			return !ADD.equals(type);
		}
	}

	private final List<Card> cards = new ArrayList<>();

	public YoldaDeck() {
		for (String t : DEFAULT) cards.add(builtIn(t));
	}

	public List<Card> cards() {
		return new ArrayList<>(cards);
	}

	/** Kartları bu sırayla koy (listede olmayan kaldırılır; "Ekle" her zaman sonda kalır). */
	public void setOrder(List<Card> order) {
		Card add = null;
		for (Card c : cards) if (ADD.equals(c.type())) add = c;
		cards.clear();
		for (Card c : order) if (!ADD.equals(c.type())) cards.add(c);
		cards.add((add != null) ? add : builtIn(ADD));
	}

	public int size() {
		return cards.size();
	}

	public Card get(int i) {
		return cards.get(i);
	}

	public int indexOf(String key) {
		for (int i = 0; i < cards.size(); i++) if (cards.get(i).key().equals(key)) return i;
		return -1;
	}

	/** Sabit kartın varsayılan adı. */
	/**
	 * Telefon görünümünün kartları: kart seti destedeki (araç) ile aynı, sırası ve gizlenenleri ayrı. Önce
	 * {@code order}'daki sırayla, sonra sırada olmayan (yeni eklenen) kartlar destedeki sırayla; gizlenenler ve
	 * "Ekle" kartı yok.
	 */
	public static List<Card> phoneCards(List<Card> deckCards, List<String> order, java.util.Set<String> hidden) {
		List<Card> out = new ArrayList<>();
		java.util.Map<String, Card> byKey = new java.util.LinkedHashMap<>();
		for (Card c : deckCards) if (!ADD.equals(c.type())) byKey.put(c.key(), c);
		for (String k : order) {
			Card c = byKey.remove(k);
			if ((c != null) && !hidden.contains(k)) out.add(c);
		}
		for (Card c : byKey.values()) if (!hidden.contains(c.key())) out.add(c);
		return out;
	}

	/** Söylenen kart adı → kart türü ("radyo", "hava", "harita", "kişiler"…); bilinmiyorsa null. */
	@Nullable
	static String typeOfSpoken(String s) {
		return switch (s) {
			case "radyo", "radyolar" -> RADIO;
			case "tv", "televizyon", "canlı yayın", "kanallar", "tv kanalları" -> TV;
			case "müzik", "müzikler", "şarkılar" -> MUSIC;
			case "youtube", "yutup", "video" -> YOUTUBE;
			case "navigasyon", "harita", "haritalar", "yol tarifi", "nav" -> NAV;
			case "hava", "hava durumu" -> WEATHER;
			case "radar", "sürüş", "hız", "radar ve hız" -> RADAR;
			case "yolculuk", "yolculuklar", "yolculuklarım", "sürüş geçmişi", "geçmiş" -> TRIPS;
			case "park", "park yeri", "park yerleri", "aracım", "otopark" -> PARK;
			case "kişiler", "kişi", "arama", "rehber", "aramalar" -> CALLS;
			case "eczane", "nöbetçi eczane", "eczaneler" -> PHARMACY;
			case "akaryakıt", "yakıt", "benzin", "akaryakıt fiyatları", "yakıt fiyatları", "benzin fiyatları", "mazot", "motorin" -> FUEL;
			case "favori", "favoriler", "favorilerim" -> FAVS;
			case "telefon", "uygulamalar" -> PHONE;
			case "yansıtma", "ekran yansıtma", "ayna" -> MIRROR;
			case "tarayıcı", "internet", "web" -> WEB;
			case "ayar", "ayarlar" -> SETTINGS;
			case "ekolayzer", "ekolayzır", "ekolayzeri", "ses ayarı", "ses ayarları", "ses modu", "eq" -> EQ;
			case "döviz", "kur", "kurlar", "altın", "dolar", "euro", "döviz ve altın" -> FX;
			case "namaz", "namaz vakitleri", "ezan", "ezan vakitleri", "vakitler" -> PRAYER;
			case "haber", "haberler", "son dakika", "gündem" -> NEWS;
			case "telefon durumu", "durum", "pil", "veri", "mobil veri", "hızlı ayarlar" -> STATUS;
			case "şarj istasyonu", "şarj istasyonları", "şarj", "elektrikli şarj", "ev şarj" -> CHARGER;
			case "not", "notlar", "notlarım", "hatırlatma", "hatırlatmalar", "hatırlatıcı" -> NOTES;
			case "hızlı mesaj", "mesaj", "mesajlar", "hazır mesaj", "hazır cevap" -> QUICKMSG;
			case "podcast", "podcastler", "podkast", "sesli kitap" -> PODCAST;
			case "medya", "videolarım", "videolar", "fotoğraflar", "fotoğraflarım", "galeri" -> MEDIA;
			case "trafik", "yol durumu", "eve ne kadar", "işe ne kadar" -> TRAFFIC;
			case "spor", "maç", "maçlar", "skor", "skorlar", "futbol", "süper lig" -> SPORTS;
			case "deprem", "depremler", "son depremler", "deprem listesi" -> QUAKE;
			case "takvim", "ajanda", "etkinlik", "etkinlikler", "toplantı", "toplantılar" -> CALENDAR;
			case "bakım", "araç bakımı", "muayene", "sigorta", "kasko" -> CARCARE;
			case "ekle", "ekleme" -> ADD;
			case "kaldığım yer", "kaldığın yer", "kaldığın yerden", "kaldığım yerden", "devam" -> RESUME;
			default -> null;
		};
	}

	/**
	 * "Radyo kartına geç": söylenen ada en uygun kartın sırası; yoksa -1. Önce bilinen adlar (radyo, hava,
	 * harita…), sonra kart başlıkları (kendi eklediğin "TRT Haber" kartı gibi) telaffuza dayanıklı karşılaştırılır.
	 */
	public int find(String spoken) {
		if (spoken == null) return -1;
		String s = spoken.trim().toLowerCase(new java.util.Locale("tr")).replaceAll("['’]", "");
		if (s.isEmpty()) return -1;
		String type = typeOfSpoken(s);
		if (type == null) type = typeOfSpoken(s.replaceAll("(y?[ae]|n[ae]|y?[ıiuü]|n[ıiuü])$", ""));
		if (type != null) {
			for (int i = 0; i < cards.size(); i++) if (cards.get(i).type().equals(type)) return i;
		}
		int best = -1, score = 0;
		for (int i = 0; i < cards.size(); i++) {
			int sc = YoldaIntents.channelScore(cards.get(i).title(), s);
			if (sc > score) {
				score = sc;
				best = i;
			}
		}
		return (score >= 50) ? best : -1;
	}

	public static String titleOf(String type) {
		return switch (type) {
			case YOUTUBE -> "YouTube";
			case MUSIC -> "Müzik";
			case TV -> "TV";
			case RADIO -> "Radyo";
			case NAV -> "Navigasyon";
			case RESUME -> "Kaldığın yerden";
			case FAVS -> "Favoriler";
			case WEATHER -> "Hava durumu";
			case PHARMACY -> "Nöbetçi eczane";
			case FUEL -> "Akaryakıt";
			case RADAR -> "Sürüş";
			case PHONE -> "Telefon";
			case CALLS -> "Kişiler";
			case TRIPS -> "Yolculuklar";
			case PARK -> "Park";
			case MIRROR -> "Ekran yansıtma";
			case WEB -> "Tarayıcı";
			case SETTINGS -> "Ayarlar";
			case EQ -> "Ekolayzer";
			case FX -> "Döviz ve altın";
			case PRAYER -> "Namaz vakitleri";
			case NEWS -> "Haberler";
			case STATUS -> "Telefon durumu";
			case CHARGER -> "Şarj istasyonları";
			case NOTES -> "Notlar";
			case QUICKMSG -> "Hızlı mesaj";
			case CALENDAR -> "Takvim";
			case TRAFFIC -> "Trafik";
			case SPORTS -> "Spor";
			case QUAKE -> "Son depremler";
			case PODCAST -> "Podcast";
			case MEDIA -> "Medya";
			case CARCARE -> "Araç bakımı";
			case ADD -> "Ekle";
			default -> type;
		};
	}

	static Card builtIn(String type) {
		return new Card(type, type, titleOf(type), "", null);
	}

	/** Sabit kartın eski varsayılan adını yenisiyle değiştirir (kayıtlı destelerde). @return değişti mi. */
	public boolean retitle(String type, String from, String to) {
		for (int i = 0; i < cards.size(); i++) {
			Card c = cards.get(i);
			if (c.isBuiltIn() && c.type().equals(type) && c.title().equals(from)) {
				cards.set(i, new Card(c.key(), c.type(), to, c.data(), c.logo()));
				return true;
			}
		}
		return false;
	}

	/** Kartı bir sola (dir = -1) ya da sağa (dir = 1) taşır; yeni konumu döner. */
	public int move(int i, int dir) {
		int j = i + dir;
		if ((i < 0) || (i >= cards.size()) || (j < 0) || (j >= cards.size())) return i;
		Card c = cards.remove(i);
		cards.add(j, c);
		return j;
	}

	/** Kartı kaldırır ("Ekle" kaldırılamaz). Sabit kartlar sonra {@link #restore} ile geri gelir. */
	public boolean remove(int i) {
		if ((i < 0) || (i >= cards.size()) || !cards.get(i).isRemovable()) return false;
		cards.remove(i);
		return true;
	}

	/** Kullanıcı kartı ekler: "Ekle" kartının hemen önüne. Aynı tür ve veri zaten varsa eklenmez. */
	public int add(String type, String title, String data, @Nullable String logo) {
		for (int i = 0; i < cards.size(); i++) {
			Card c = cards.get(i);
			if (c.type().equals(type) && c.data().equals(data)) return i;
		}
		String key = type + ":" + Integer.toHexString((type + data).hashCode()) + ":" + cards.size();
		int at = indexOf(ADD);
		if (at < 0) at = cards.size();
		cards.add(at, new Card(key, type, clean(title), clean(data), (logo != null) ? clean(logo) : null));
		return at;
	}

	/** Kaldırılmış sabit kartlar (geri getirilebilir). */
	public List<String> missingBuiltIns() {
		List<String> out = new ArrayList<>();
		for (String t : DEFAULT) if (indexOf(t) < 0) out.add(t);
		return out;
	}

	/** Kaldırılmış sabit kartı varsayılan sırasına en yakın yere geri koyar. */
	public int restore(String type) {
		int have = indexOf(type);
		if (have >= 0) return have;
		int at = cards.size();
		boolean seen = false;
		for (String t : DEFAULT) {
			if (t.equals(type)) {
				seen = true;
				continue;
			}
			if (!seen) continue;
			int i = indexOf(t);
			if (i >= 0) {
				at = i;
				break;
			}
		}
		cards.add(at, builtIn(type));
		return at;
	}

	public void reset() {
		cards.clear();
		for (String t : DEFAULT) cards.add(builtIn(t));
	}

	// ---- kayıt ----

	/** Kart listesi kaydı (kaldırılan kullanıcı kartları için; varsayılan deste eklenmez). */
	public static String encodeCards(List<Card> l) {
		StringBuilder b = new StringBuilder();
		for (Card c : l) {
			if (b.length() > 0) b.append(RECORD);
			b.append(c.key()).append(FIELD).append(c.type()).append(FIELD).append(c.title()).append(FIELD)
					.append(c.data()).append(FIELD).append((c.logo() != null) ? c.logo() : "");
		}
		return b.toString();
	}

	public static List<Card> decodeCards(@Nullable String s) {
		List<Card> l = new ArrayList<>();
		if ((s == null) || s.isEmpty()) return l;
		for (String rec : s.split(String.valueOf(RECORD), -1)) {
			String[] f = rec.split(String.valueOf(FIELD), -1);
			if ((f.length < 5) || f[0].isEmpty() || f[1].isEmpty()) continue;
			l.add(new Card(f[0], f[1], f[2], f[3], f[4].isEmpty() ? null : f[4]));
		}
		return l;
	}

	@NonNull
	public String encode() {
		StringBuilder b = new StringBuilder();
		for (Card c : cards) {
			if (b.length() > 0) b.append(RECORD);
			b.append(c.key()).append(FIELD).append(c.type()).append(FIELD).append(c.title()).append(FIELD)
					.append(c.data()).append(FIELD).append((c.logo() != null) ? c.logo() : "");
		}
		return b.toString();
	}

	/** Kayıttan kurar; bozuk ya da boş kayıtta varsayılan deste. "Ekle" kartı hep vardır. */
	public static YoldaDeck decode(@Nullable String s) {
		YoldaDeck d = new YoldaDeck();
		if ((s == null) || s.isEmpty()) return d;
		List<Card> l = new ArrayList<>();
		for (String rec : s.split(String.valueOf(RECORD), -1)) {
			String[] f = rec.split(String.valueOf(FIELD), -1);
			if ((f.length < 5) || f[0].isEmpty() || f[1].isEmpty()) continue;
			boolean dup = false;
			for (Card c : l) dup |= c.key().equals(f[0]);
			if (dup) continue;
			l.add(new Card(f[0], f[1], f[2], f[3], f[4].isEmpty() ? null : f[4]));
		}
		if (l.isEmpty()) return d;
		d.cards.clear();
		d.cards.addAll(l);
		if (d.indexOf(ADD) < 0) d.cards.add(builtIn(ADD));
		return d;
	}

	private static String clean(String s) {
		return s.replace(FIELD, ' ').replace(RECORD, ' ').trim();
	}

	// ---- Android Auto sadeleştirme (sürüş dikkat yönergeleri: kısa liste, kısa yazı) ----

	/** Araçta bir kart yüzündeki şeritte en çok kaç öğe (Android Auto sürüşte liste sınırı 6). */
	public static final int CAR_MAX_ITEMS = 6;
	/** Araçta çip yazısının en çok uzunluğu (göz kısa bakışta okusun). */
	public static final int CAR_MAX_CHARS = 22;

	/** Araçta şeritte kalacak öğe sayısı. */
	public static int carItems(int n) {
		return Math.max(0, Math.min(n, CAR_MAX_ITEMS));
	}

	/** Araç için kısaltılmış çip yazısı: tek satır, sözcük ortasında kesmeden, sonunda "…". */
	public static String carLabel(@androidx.annotation.Nullable String s) {
		if (s == null) return "";
		String t = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
		if (t.length() <= CAR_MAX_CHARS) return t;
		String cut = t.substring(0, CAR_MAX_CHARS - 1);
		int sp = cut.lastIndexOf(' ');
		if (sp >= CAR_MAX_CHARS / 2) cut = cut.substring(0, sp);
		cut = cut.replaceAll("[\\s·,.:;-]+$", "");
		return cut + "…";
	}

	// ---- sesle / Gemini ile kart yönetimi ----

	/**
	 * Taşınan kartın yeni sırası: where = first / last / before / after (other: yanına konacak kartın sırası).
	 * size: taşınabilir kart sayısı ("Ekle" hariç). Kart kendi yanına taşınırsa yeri değişmez.
	 */
	public static int targetIndex(int from, String where, int other, int size) {
		if ((size <= 0) || (from < 0) || (from >= size)) return from;
		int t = switch (where) {
			case "first" -> 0;
			case "last" -> size - 1;
			case "before" -> (other < 0) ? from : ((other < from) ? other : other - 1);
			case "after" -> (other < 0) ? from : ((other < from) ? other + 1 : other);
			default -> from;
		};
		return Math.max(0, Math.min(size - 1, t));
	}

	/** Söylenen ada uyan sabit kart türü (kaldırılmış olsa da; geri getirmek için), yoksa null. */
	@Nullable
	public static String builtInOfSpoken(@Nullable String spoken) {
		if (spoken == null) return null;
		String s = spoken.trim().toLowerCase(new java.util.Locale("tr")).replaceAll("['’]", "");
		String t = typeOfSpoken(s);
		if (t == null) t = typeOfSpoken(s.replaceAll("(y?[ae]|n[ae]|y?[ıiuü]|n[ıiuü])$", ""));
		if (t == null) return null;
		for (String d : DEFAULT) if (d.equals(t)) return t;
		return null;
	}
}
