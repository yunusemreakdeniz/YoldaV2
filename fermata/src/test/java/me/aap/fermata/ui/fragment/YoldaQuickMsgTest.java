package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Hızlı mesaj: hangi mesaja yanıt gider (30 dakika, yanıt yolu olan). */
public class YoldaQuickMsgTest {
	@Test
	public void yanitHedefi() {
		long now = 100L * 60_000L;
		YoldaMessages.Msg noReply = new YoldaMessages.Msg("WhatsApp", "Ahmet", "Neredesin", null, now);
		assertNull(YoldaQuickMsg.replyTarget(noReply, now)); // yanıt yolu yok: paylaşım
		assertNull(YoldaQuickMsg.replyTarget(null, now));
		assertEquals(5, YoldaQuickMsg.DEFAULTS.length);
	}
}
