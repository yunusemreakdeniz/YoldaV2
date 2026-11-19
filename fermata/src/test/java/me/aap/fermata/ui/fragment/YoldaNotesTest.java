package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Notlar ve hatırlatmalar: not metni temizleme, saklama biçimi, okunuş; sesli komutlar ve uyarı geçmişi komutu. */
public class YoldaNotesTest {
	@Test
	public void notMetni() {
		assertEquals("Süt al", YoldaNotes.cleanNote("not al: süt al"));
		assertEquals("Lastikleri kontrol et", YoldaNotes.cleanNote("şunu not et lastikleri kontrol et"));
		assertEquals("Yarın Ahmet'i ara", YoldaNotes.cleanNote("yarın Ahmet'i ara diye not al"));
		assertEquals("", YoldaNotes.cleanNote("not al"));
	}

	@Test
	public void saklamaVeOkunus() {
		List<YoldaNotes.Note> l = new ArrayList<>(List.of(new YoldaNotes.Note(2, "İki"), new YoldaNotes.Note(1, "Bir")));
		assertEquals(l, YoldaNotes.decodeNotes(YoldaNotes.encodeNotes(l)));
		assertEquals("2 not: İki. Bir", YoldaNotes.spoken(l, 5));
		assertEquals("Kayıtlı not yok", YoldaNotes.spoken(List.of(), 5));
		List<YoldaNotes.Reminder> r = List.of(new YoldaNotes.Reminder(5000, "Su iç"));
		assertEquals(r, YoldaNotes.decodeReminders(YoldaNotes.encodeReminders(r)));
		assertEquals("10 dk sonra", YoldaNotes.left(10 * 60_000L, 0));
		assertEquals("1 sa 30 dk sonra", YoldaNotes.left(90 * 60_000L, 0));
	}

	@Test
	public void sesliKomutlar() {
		assertEquals(YoldaIntents.Kind.NOTE_ADD, YoldaIntents.parse("not al süt al").kind());
		assertEquals(YoldaIntents.Kind.NOTE_ADD, YoldaIntents.parse("yarın ahmeti ara diye not al").kind());
		assertEquals(YoldaIntents.Kind.NOTE_READ, YoldaIntents.parse("notlarımı oku").kind());
		assertEquals(YoldaIntents.Kind.NOTE_CLEAR, YoldaIntents.parse("notları sil").kind());
		assertEquals(YoldaIntents.Kind.ALERTS, YoldaIntents.parse("son uyarılar").kind());
		assertEquals(1, YoldaIntents.parse("az önce ne dedin").num());
		assertEquals(YoldaIntents.Kind.TEST_ALERT, YoldaIntents.parse("uyarıyı dene").kind()); // eski komut bozulmasın
	}
}
