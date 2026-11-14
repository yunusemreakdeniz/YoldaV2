package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.ui.fragment.YoldaCalls.Contact;
import me.aap.fermata.ui.fragment.YoldaIntents.Intent;
import me.aap.fermata.ui.fragment.YoldaIntents.Kind;

/** Sesle arama ve güçlendirilmiş komut motoru: "Annemi ara", ad biçimleri, kişi eşleşmesi, yanlış duyulanlar. */
public class YoldaCallsTest {
	private static final List<Contact> BOOK = List.of(new Contact("Annem", "05001", null), new Contact("Babam", "05002", null),
			new Contact("Ahmet Yılmaz", "05003", null), new Contact("Ahmet Kaya", "05004", null),
			new Contact("Mehmet Öztürk", "05005", null), new Contact("Şule", "05006", null),
			new Contact("Eşim", "05007", null), new Contact("Ayşe Demir", "05008", null));

	private static void call(String spoken, String who) {
		Intent i = YoldaIntents.parse(spoken);
		assertTrue("“" + spoken + "” → " + i, (i != null) && (i.kind() == Kind.CALL));
		assertEquals("“" + spoken + "”", who, i.arg());
	}

	private static List<String> names(List<Contact> l) {
		List<String> n = new ArrayList<>();
		for (Contact c : l) n.add(c.name());
		return n;
	}

	@Test
	public void aramaKomutu() {
		call("annemi ara", "annemi");
		call("Hey Yolda babamı ara lütfen", "babamı");
		call("Ahmet Yılmaz'ı ara", "ahmet yılmaz'ı");
		call("Mehmet'e telefon et", "mehmet'e");
		call("ayşe ile görüş", "ayşe");
		call("eşimi arar mısın", "eşimi");
		// arama değil
		assertEquals(Kind.YT_SEARCH, YoldaIntents.parse("YouTube'da haber ara").kind());
		assertEquals(Kind.NAV_NEARBY, YoldaIntents.parse("en yakın eczane ara").kind());
		Intent k = YoldaIntents.parse("kanal ara");
		assertTrue(String.valueOf(k), (k == null) || (k.kind() != Kind.CALL));
	}

	@Test
	public void adBicimleri() {
		assertTrue(YoldaIntents.nameForms("annemi").containsAll(List.of("annemi", "annem", "anne")));
		assertTrue(YoldaIntents.nameForms("Ahmet'e").contains("ahmet"));
		assertTrue(YoldaIntents.nameForms("babamı").containsAll(List.of("babam", "baba")));
		assertTrue(YoldaIntents.nameForms("").isEmpty());
	}

	@Test
	public void kisiEslesmesi() {
		assertEquals(List.of("Annem"), names(YoldaCalls.match(BOOK, "annemi")));
		assertEquals(List.of("Babam"), names(YoldaCalls.match(BOOK, "babamı")));
		assertEquals(List.of("Ahmet Yılmaz"), names(YoldaCalls.match(BOOK, "ahmet yılmaz'ı")));
		assertEquals(List.of("Mehmet Öztürk"), names(YoldaCalls.match(BOOK, "mehmet'e")));
		assertEquals(List.of("Şule"), names(YoldaCalls.match(BOOK, "şuleyi")));
		assertEquals(List.of("Eşim"), names(YoldaCalls.match(BOOK, "eşimi")));
		// iki Ahmet: ikisi de döner ("Hangisi?")
		List<String> two = names(YoldaCalls.match(BOOK, "ahmeti"));
		assertEquals(2, two.size());
		assertTrue(two.containsAll(List.of("Ahmet Yılmaz", "Ahmet Kaya")));
		// telaffuz farkı (Türkçe karakter yok)
		assertEquals(List.of("Mehmet Öztürk"), names(YoldaCalls.match(BOOK, "mehmet ozturk")));
		// yok
		assertTrue(YoldaCalls.match(BOOK, "zeynep").isEmpty());
	}

	@Test
	public void basHarfler() {
		assertEquals("AY", new Contact("Ahmet Yılmaz", "1", null).initials());
		assertEquals("A", new Contact("annem", "1", null).initials());
	}

	@Test
	public void yanlisDuyulanlar() {
		assertEquals("trt haber aç", YoldaIntents.clean("tirete haber aç"));
		assertEquals("youtube aç", YoldaIntents.clean("yutup aç"));
		assertEquals("kral fm aç", YoldaIntents.clean("kral ef em aç"));
		assertEquals("ntv aç", YoldaIntents.clean("en tivi aç"));
		Intent i = YoldaIntents.parse("Kral ef em aç");
		assertEquals(Kind.RADIO_SEARCH, i.kind());
		assertEquals("kral fm", i.arg());
		assertEquals(Kind.OPEN_APP, YoldaIntents.parse("yutup aç").kind());
	}

	@Test
	public void ikiKomutBirden() {
		String c = YoldaIntents.clean("sesi aç ve TRT Haber aç");
		String[] parts = c.split(" (?:ve|sonra|ardından|daha sonra) ");
		assertEquals(2, parts.length);
		assertEquals(Kind.VOL_UP, YoldaIntents.parse(parts[0]).kind());
		assertEquals(Kind.CHANNEL, YoldaIntents.parse(parts[1]).kind());
		assertNull(YoldaIntents.parse(""));
	}
}
