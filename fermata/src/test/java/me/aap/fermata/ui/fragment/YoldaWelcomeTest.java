package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Sesli karşılama: günün saati, isim temizliği, günde bir kuralı ve metin. */
public class YoldaWelcomeTest {
	@Test
	public void selam() {
		assertEquals("Günaydın", YoldaWelcome.hello(7));
		assertEquals("İyi günler", YoldaWelcome.hello(14));
		assertEquals("İyi akşamlar", YoldaWelcome.hello(20));
		assertEquals("İyi geceler", YoldaWelcome.hello(2));
	}

	@Test
	public void isim() {
		assertEquals("Emre", YoldaWelcome.cleanName("  Emre<script> ".replace("<script>", "")));
		assertEquals("Ali Can", YoldaWelcome.cleanName("Ali\n  Can"));
		assertEquals("Ahmet", YoldaWelcome.cleanName("Ahmet!!!"));
		assertEquals(24, YoldaWelcome.cleanName("abcdefghijklmnopqrstuvwxyzabc").length());
	}

	@Test
	public void kural() {
		assertFalse(YoldaWelcome.shouldGreet(false, false, 1, 2));
		assertTrue(YoldaWelcome.shouldGreet(true, false, 2, 2)); // her bağlanışta
		assertFalse(YoldaWelcome.shouldGreet(true, true, 2, 2)); // günde bir: bugün selam verildi
		assertTrue(YoldaWelcome.shouldGreet(true, true, 1, 2));
	}

	@Test
	public void metin() {
		assertEquals("Günaydın Emre. İyi yolculuklar!", YoldaWelcome.text(8, "Emre", null));
		assertEquals("İyi akşamlar. Hava 14 derece, parçalı bulutlu. İyi yolculuklar!",
				YoldaWelcome.text(19, "", new YoldaWeather.Now(14, 2, 10, "X", java.util.List.of())));
	}

	@Test
	public void ozelGun() {
		int sal = java.util.Calendar.TUESDAY;
		assertEquals("Cumhuriyet Bayramı kutlu olsun!", YoldaWelcome.specialDay(10, 29, sal, ""));
		assertEquals("Doğum günün kutlu olsun! Bugün yol senin.", YoldaWelcome.specialDay(11, 21, sal, "11-21"));
		assertEquals("Cuma geldi, hafta sonu kapıda.", YoldaWelcome.specialDay(3, 6, java.util.Calendar.FRIDAY, ""));
		assertEquals("", YoldaWelcome.specialDay(3, 3, sal, null));
		assertEquals("11-21", YoldaWelcome.parseBirthday("21.11"));
		assertEquals("01-05", YoldaWelcome.parseBirthday(" 5/1 "));
		assertEquals("", YoldaWelcome.parseBirthday("32.13"));
		assertEquals("", YoldaWelcome.parseBirthday("dün"));
		assertEquals("İyi akşamlar Emre. Cuma geldi, hafta sonu kapıda. İyi yolculuklar!",
				YoldaWelcome.text(19, "Emre", null, "Cuma geldi, hafta sonu kapıda."));
	}
}
