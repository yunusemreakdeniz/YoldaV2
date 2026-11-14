package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Hey Yolda kısa sohbet: selamlaşma, hal hatır, teşekkür, şaka, sürücüye göre öneriler. İnternet ve dil modeli
 * gerektirmez (ücretsiz, çevrimdışı); cümleler elle yazılmış kalıplardır. Bazı yanıtların ardından bir iş de
 * yapılır ("acıktım" → yakındaki restoranlar, "uykum geldi" → dinlenme tesisi).
 * Android'den bağımsızdır; birim testleriyle denetlenir.
 */
public final class YoldaChat {
	private YoldaChat() {
	}

	/** Yanıt ve (varsa) ardından yapılacak komut. */
	public record Reply(String text, @Nullable YoldaIntents.Intent then) {
	}

	private static String pick(Random r, String... s) {
		return s[(r == null) ? 0 : r.nextInt(s.length)];
	}

	private static String greet(int hour) {
		if ((hour >= 5) && (hour < 12)) return "Günaydın";
		if ((hour >= 12) && (hour < 18)) return "Tünaydın";
		if ((hour >= 18) && (hour < 23)) return "İyi akşamlar";
		return "İyi geceler";
	}

	/** Sohbet cümlesi mi (temizlenmiş metin). */
	public static boolean is(String c) {
		return answer(c, 12, null) != null;
	}

	/**
	 * @param c    {@link YoldaIntents#clean} ile temizlenmiş cümle
	 * @param hour günün saati (selamlaşma için)
	 * @param r    yanıt çeşitliliği; null ise hep ilk yanıt (testler)
	 * @return yanıt; sohbet cümlesi değilse null
	 */
	@Nullable
	public static Reply answer(String c, int hour, @Nullable Random r) {
		if ((c == null) || c.isEmpty()) return null;
		String s = c.replaceAll("^(ya |peki |şey |hmm |eee |yani )+", "").trim();

		// selamlaşma
		if (s.matches("(merhaba|selam|selamlar|selamün aleyküm|selamun aleyküm|hey|alo|merhabalar|naber|ne haber|nabersin|hey yolda|yolda)"))
			return new Reply(pick(r, greet(hour) + "! Nereye gidiyoruz?", "Merhaba! Ne dinlemek istersin?",
					"Selam! Yol arkadaşın hazır."), null);
		if (s.matches("günaydın.*")) return new Reply(pick(r, "Günaydın! Güzel bir gün olsun.",
				"Günaydın! Kahveni aldıysan yola çıkabiliriz."), null);
		if (s.matches("(iyi akşamlar|tünaydın|iyi günler)")) return new Reply(greet(hour) + "! Nasıl yardımcı olabilirim?", null);
		if (s.matches("(iyi geceler|iyi uykular|hayırlı geceler)"))
			return new Reply("İyi geceler! Uykun geldiyse sürmeden önce dinlen.", null);
		if (s.matches("(görüşürüz|hoşça kal|güle güle|bay bay|bye|kendine iyi bak)"))
			return new Reply(pick(r, "Görüşürüz, iyi yolculuklar!", "Hoşça kal, dikkatli sür."), null);

		// hal hatır
		if (s.matches("(nasılsın|nasıl gidiyor|naptın|napıyorsun|ne yapıyorsun|ne var ne yok|iyi misin|keyifler nasıl|nasılsınız)"))
			return new Reply(pick(r, "İyiyim, teşekkürler! Sen nasılsın?", "Harikayım, yolu izliyorum. Sen nasılsın?",
					"Bomba gibiyim! Senin keyfin nasıl?"), null);
		if (s.matches("(iyiyim|ben de iyiyim|iyi|çok iyiyim|harikayım|süperim|fena değil|idare eder|bomba gibiyim)( teşekkürler| sağ ol| sağol)?"))
			return new Reply(pick(r, "Sevindim! Ne yapalım?", "Ne güzel! Müzik açayım mı?"), null);
		if (s.matches("(kötüyüm|moralim bozuk|canım sıkkın|üzgünüm|keyfim yok|hiç iyi değilim)"))
			return new Reply("Geçmiş olsun. Biraz müzik iyi gelebilir, sevdiğin bir şarkıyı açayım mı? \"Tarkan çal\" de yeter.", null);
		if (s.matches("(canım sıkıldı|sıkıldım|çok sıkıldım|sıkılıyorum)"))
			return new Reply("Hemen bir şey açıyorum, keyfin yerine gelsin.", YoldaIntents.Intent.of(YoldaIntents.Kind.SONG, "Türkçe pop"));

		// teşekkür, övgü
		if (s.matches("(teşekkürler|teşekkür ederim|sağ ol|sağol|sağolasın|eyvallah|çok teşekkürler|mersi|thanks|tşk|eline sağlık)"))
			return new Reply(pick(r, "Rica ederim!", "Ne demek, her zaman!", "Ben teşekkür ederim!"), null);
		if (s.matches("(aferin|harikasın|süpersin|çok iyisin|bravo|helal olsun|seni seviyorum|iyi ki varsın|çok akıllısın)"))
			return new Reply(pick(r, "Çok naziksin! Yolda hep yanındayım.", "Teşekkürler, sen de harika bir sürücüsün!"), null);
		if (s.matches("(salak|aptal|aptalsın|salaksın|işe yaramazsın|beceriksiz|berbatsın)"))
			return new Reply("Üzgünüm, daha iyi anlamaya çalışacağım. Komutu biraz farklı söyler misin?", null);

		// kimlik
		if (s.matches("(sen kimsin|adın ne|ismin ne|kimsin|sen nesin|kendini tanıt|kim konuşuyor)"))
			return new Reply("Ben Yolda, sürüş asistanınım. Müzik, radyo, TV, yol tarifi, radar ve arama işlerinde yardım ederim.", null);
		if (s.matches("(kaç yaşındasın|yaşın kaç)")) return new Reply("Yaşım yok ama her güncellemeyle biraz büyüyorum!", null);
		if (s.matches("(seni kim yaptı|kim yaptı seni|yaratıcın kim)")) return new Reply("Beni yol arkadaşın olayım diye yaptılar.", null);
		if (s.matches("(evli misin|sevgilin var mı)")) return new Reply("Benim tek aşkım yollar!", null);
		if (s.matches("(beni duyuyor musun|orada mısın|burada mısın|uyanık mısın|dinliyor musun)"))
			return new Reply("Buradayım, dinliyorum.", null);

		// eğlence
		if (s.matches("(şaka yap|bir şaka yap|fıkra anlat|bir fıkra anlat|beni güldür|komik bir şey söyle|espri yap)"))
			return new Reply(pick(r, JOKES), null);
		if (s.matches("(şarkı söyle|bir şarkı söyle|türkü söyle)"))
			return new Reply("Sesim pek güzel değil, ama en iyisini açabilirim!", YoldaIntents.Intent.of(YoldaIntents.Kind.SONG, "Türkçe pop"));
		if (s.matches("(bir şey söyle|bilgi ver|ilginç bir şey söyle|bilgi yarışması|biliyor muydun)"))
			return new Reply(pick(r, FACTS), null);
		if (s.matches("(yazı tura( at)?|para at)")) return new Reply((r == null) ? "Yazı!" : (r.nextBoolean() ? "Yazı!" : "Tura!"), null);
		if (s.matches("(zar at|bir zar at)")) return new Reply("Zar: " + ((r == null) ? 4 : (r.nextInt(6) + 1)), null);

		// sürücünün hali: öneri + iş
		if (s.matches("(uykum geldi|uykum var|yoruldum|çok yoruldum|gözlerim kapanıyor|yorgunum|uyuklıyorum)"))
			return new Reply("Lütfen dikkat! Yorgunken sürmek tehlikeli. Yakındaki dinlenme tesislerini gösteriyorum, 15 dakikalık mola ver.",
					YoldaIntents.Intent.of(YoldaIntents.Kind.NAV_NEARBY, YoldaIntents.nearbyQuery("dinlenme tesisi")));
		if (s.matches("(acıktım|çok acıktım|karnım aç|karnım acıktı|yemek yiyelim|bir şeyler yiyelim)"))
			return new Reply("Afiyet olsun! Yakındaki restoranları gösteriyorum.",
					YoldaIntents.Intent.of(YoldaIntents.Kind.NAV_NEARBY, YoldaIntents.nearbyQuery("restoran")));
		if (s.matches("(kahve içelim|kahve istiyorum|canım kahve çekti)"))
			return new Reply("Güzel fikir! Yakındaki kafeleri gösteriyorum.",
					YoldaIntents.Intent.of(YoldaIntents.Kind.NAV_NEARBY, YoldaIntents.nearbyQuery("kafe")));
		if (s.matches("(benzinim (bitiyor|azaldı|bitti)|yakıt (azaldı|bitiyor)|depo boş|benzin almam lazım|benzin alalım)"))
			return new Reply("Yakındaki benzinlikleri gösteriyorum.",
					YoldaIntents.Intent.of(YoldaIntents.Kind.NAV_NEARBY, YoldaIntents.nearbyQuery("benzinlik")));
		if (s.matches("(tuvaletim geldi|tuvalet lazım|lavabo lazım)"))
			return new Reply("Yakındaki tesisleri gösteriyorum.",
					YoldaIntents.Intent.of(YoldaIntents.Kind.NAV_NEARBY, YoldaIntents.nearbyQuery("tuvalet")));
		if (s.matches("(trafik çok|trafik var|trafikteyiz|sıkıştık|yol çok kalabalık)"))
			return new Reply("Sabırlı ol, birazdan açılır. Bu arada bir podcast ya da radyo açabilirim.", null);
		if (s.matches("(geç kaldım|geç kalıyorum|acelem var)"))
			return new Reply("Acele etme, güvenli sür. Geç kalmak kazadan iyidir.", null);

		// onay, ret (bağlamsız)
		if (s.matches("(tamam|olur|peki|anladım|tamamdır|ok|okey|evet)")) return new Reply("Tamam.", null);
		if (s.matches("(hayır|yok|istemiyorum|gerek yok|vazgeç|boş ver|boşver|iptal)")) return new Reply("Peki, vazgeçtim.", null);
		return null;
	}

	static final String[] JOKES = {
			"Temel'e sormuşlar: Araban neden hep sağda? Demiş ki: Solda hep başkaları var!",
			"Navigasyon bana 200 metre sonra sağa dön dedi. Ben de döndüm, o da şaşırdı.",
			"Trafikte o kadar yavaş gidiyorduk ki yanımızdaki salyangoz sollama yaptı.",
			"Nasreddin Hoca'ya sormuşlar: Dünyanın merkezi neresi? Eşeğinin ayağını gösterip: Tam burası, inanmazsan ölç!",
			"Arabam o kadar eski ki radyosu sadece eski şarkıları çalıyor."};

	static final String[] FACTS = {
			"Biliyor muydun? Türkiye'nin en uzun karayolu tüneli Zigana Tüneli, yaklaşık 14,5 kilometre.",
			"Biliyor muydun? Saatte 90 kilometre hızla giden bir araç, bir saniyede 25 metre yol alır.",
			"Biliyor muydun? Kısa bir mola, uzun yolda dikkat süresini belirgin biçimde artırır. İki saatte bir mola önerilir.",
			"Biliyor muydun? Lastik basıncı düşük olursa yakıt tüketimi artar.",
			"Biliyor muydun? İstanbul Boğazı'nın üzerinde üç köprü var."};
}
