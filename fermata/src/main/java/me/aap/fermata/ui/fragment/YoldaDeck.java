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

	static final String[] DEFAULT = {YOUTUBE, MUSIC, TV, RADIO, NAV, PHONE, MIRROR, WEB, SETTINGS, ADD};
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
	public static String titleOf(String type) {
		return switch (type) {
			case YOUTUBE -> "YouTube";
			case MUSIC -> "Müzik";
			case TV -> "TV";
			case RADIO -> "Radyo";
			case NAV -> "Navigasyon";
			case PHONE -> "Telefon";
			case MIRROR -> "Ekran yansıtma";
			case WEB -> "Tarayıcı";
			case SETTINGS -> "Ayarlar";
			case ADD -> "Ekle";
			default -> type;
		};
	}

	static Card builtIn(String type) {
		return new Card(type, type, titleOf(type), "", null);
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
}
