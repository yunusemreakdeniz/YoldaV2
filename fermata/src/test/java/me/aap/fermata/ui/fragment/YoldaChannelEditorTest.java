package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** Tek kanal düzenleyici: bir türü (radyo) sıralamak öbürünün (TV) sırasını bozmaz. */
public class YoldaChannelEditorTest {
	@Test
	public void siraBirlesir() {
		List<String> old = List.of("tv1", "tv2", "r1", "tv3", "r2");
		// radyolar yeniden sıralandı: önce düzenlenenler, sonra eski sıradaki diğerleri (TV) aynı sırayla
		assertEquals(List.of("r2", "r1", "tv1", "tv2", "tv3"), YoldaChannelEditor.merge(old, List.of("r2", "r1")));
		// TV sırası korunarak sort edildiğinde TV'ler kendi aralarında aynı
		List<String> tv = new java.util.ArrayList<>(List.of("tv3", "tv1", "tv2"));
		YoldaChannelOrder.sort(tv, YoldaChannelEditor.merge(old, List.of("r2", "r1")), s -> s);
		assertEquals(List.of("tv1", "tv2", "tv3"), tv);
		// yeni kanal (eski sırada yok) da kaydedilir
		assertEquals(List.of("tv4", "tv1", "tv2", "r1", "tv3", "r2"), YoldaChannelEditor.merge(old, List.of("tv4", "tv1")));
		assertEquals(List.of(), YoldaChannelEditor.merge(List.of(), List.of()));
	}
}
