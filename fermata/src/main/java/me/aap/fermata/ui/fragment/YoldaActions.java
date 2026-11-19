package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Uygulamanın eylem kataloğu: sesli asistanın yapabildiği her iş, yerel komut çözücünün ({@link YoldaIntents})
 * anladığı kalıp cümleyle. Gemini bağlıysa bu katalog ona verilir; doğal bir istek ("biraz rahatlatıcı bir şey
 * aç", "en yakın eczaneye götür") gelince Gemini isteği katalogdaki komut cümlesine çevirir ("KOMUT: ..."),
 * komutu yine yerel çözücü yürütür. Böylece Gemini bütün uygulamayı yönetir, yerel motor da aynı kataloğu kullanır.
 */
public final class YoldaActions {
	/** {komut kalıbı, ne yapar}. Köşeli parantez: yerine konacak değer. */
	static final String[][] CATALOG = {
			// oynatma
			{"[kanal adı] aç", "TV ya da radyo kanalını açar (TRT 1 aç, Kral FM aç)"},
			{"[şarkı / sanatçı / tür] çal", "YouTube Music'ten çalar (Tarkan çal, rahatlatıcı müzik çal, 90'lar pop çal)"},
			{"youtube'da [konu] ara", "YouTube'da arar"},
			{"[konu] radyosu aç", "internet radyolarında arayıp açar (caz radyosu aç)"},
			{"[ad] podcastini aç", "podcast'in son bölümünü çalar (abone değilse dizinde arar)"},
			{"duraklat / devam et / sonraki / önceki / durdur", "oynatma denetimi"},
			{"sus / dur / yeter", "sesli okumayı (haberler, uzun yanıt) keser"},
			{"on saniye ileri sar / otuz saniye geri sar / başa sar", "sarma"},
			{"sesi aç / sesi kıs / sesi yüzde [sayı] yap / sessize al", "ses düzeyi"},
			{"[sayı] dakika sonra kapat / uyku zamanlayıcısını iptal et", "uyku zamanlayıcısı"},
			{"ne çalıyor", "çalan parçayı söyler"},
			{"video kalitesini [360 / 480 / 720 / 1080 / otomatik] yap / kaliteyi düşür / kaliteyi artır", "video kalitesi (veri kullanımı)"},
			{"[pop / rock / konuşma / bas / araç / klasik / caz] ekolayzer / ekolayzeri kapat", "ses ayarı (ekolayzer hazır ayarı)"},
			{"kaldığım yerden devam et", "son dinleneni sürdürür"},
			{"favorilere ekle / favorileri aç", "favoriler"},
			// navigasyon
			{"eve git / işe git", "kayıtlı eve / işe yol tarifi"},
			{"[yer] git", "yol tarifi (Kızılay'a git, Anıtkabir'e git)"},
			{"en yakın [benzinlik / eczane / nöbetçi eczane / otopark / restoran / kafe / şarj istasyonu / hastane]", "yakındakiler"},
			{"park yerim nerede / buraya park ettim", "park yeri"},
			{"gideceğim yerde otopark var mı / [yer]de otopark", "varılacak yerin çevresindeki otoparklar"},
			{"şarj istasyonları kartına geç", "yakındaki elektrikli araç şarj istasyonları"},
			{"neredeyim", "bulunduğun yer"},
			{"eve kaç dakika / işe ne kadar sürer", "yol süresi (trafik hariç tahmini)"},
			{"yolculuğu başlat / yolculuğu bitir", "telefonda yolculuk kaydı (Android Auto'da kendiliğinden)"},
			{"maç sonuçları / [takım] maçı ne zaman", "Süper Lig sonuçları ve fikstür"},
			// arama ve mesaj
			{"[kişi] ara", "telefonla arar (annemi ara)"},
			{"yanıtla [metin] / mesajı tekrar oku", "son mesaja sesle yanıt, son mesajı okuma"},
			{"hızlı mesaj kartına geç", "hazır cevaplar, varınca haber ver"},
			// destede ve ekranlar
			{"[kart adı] kartına geç / [kart adı] kartını aç", "desteyi o karta götürür / açar (radyo, hava, akaryakıt, park, yolculuklar, eczane, sürüş, kişiler, telefon, favoriler)"},
			{"sonraki kart / önceki kart / kartları düzenle", "deste"},
			{"[kart adı] kartını gizle / [kart adı] kartını geri getir", "kartı gizler (telefon görünümünde yalnız telefonda) / geri getirir"},
			{"[kart adı] kartını başa al / sona al", "kartın sırasını değiştirir"},
			{"[kart adı] kartını [öbür kart] kartının önüne / ardına taşı", "kartı başka bir kartın yanına taşır"},
			{"ana sayfa / geri", "gezinme"},
			{"telefon görünümüne geç / araç görünümüne geç", "telefonda ızgara ya da kart destesi"},
			{"navigasyonu aç / kanalları aç / menüyü aç / ayarları aç", "ekran açar"},
			// sürüş
			{"radarı aç / radarı kapat / radar var mı", "radar uyarısı"},
			{"okul uyarısını kapat / mola hatırlatıcısını aç / hız uyarısını kapat", "sürüş yardımcıları"},
			{"uyarıyı dene", "deneme radar uyarısı"},
			{"bu yolculuk nasıldı", "yolculuk özeti"},
			// bilgi
			{"saat kaç / bugün günlerden ne", "saat ve tarih"},
			{"dolar kaç / euro ne kadar / gram altın ne kadar / çeyrek altın kaç", "döviz ve altın"},
			{"akşam ezanı ne zaman / namaz vakitleri / imsak kaçta", "namaz vakitleri"},
			{"haberleri oku / son dakika", "son dakika haberleri sesli"},
			{"ne kadar veri kullandım / telefon durumu", "pil, bağlantı ve bu ayki mobil veri"},
			{"hava nasıl / yarın hava nasıl / [şehir] hava durumu", "hava durumu ve tahmin"},
			{"şarjım kaç / hızım kaç", "pil, hız"},
			{"[sayı] dakika sonra [iş] hatırlat / hatırlatmaları sil", "hatırlatıcı"},
			{"not al: [metin] / notlarımı oku / notları sil", "sesli notlar"},
			{"bugün ne var / toplantım ne zaman", "takvimdeki bugünkü ve yarınki etkinlikler"},
			{"muayene ne zaman / sigortam ne zaman bitiyor", "araç bakım tarihleri"},
			{"son uyarılar / az önce ne dedin", "uyarı geçmişi ve son söylenen"},
			{"on beş çarpı dört", "hesap"},
			{"gün batımı ne zaman", "güneş saatleri"},
			// görünüm
			{"[okyanus / gece / gün batımı / orman ...] teması / temayı değiştir / temayı otomatik yap", "tema"},
			{"sesli yanıtı aç / sesli yanıtı kapat / hey yoldayı kapat", "asistan ayarları"},
	};

	private YoldaActions() {
	}

	/** Gemini'ye verilen katalog bölümü. */
	static String forPrompt() {
		StringBuilder b = new StringBuilder();
		for (String[] r : CATALOG) b.append("- ").append(r[0]).append(" → ").append(r[1]).append('\n');
		return b.toString();
	}

	/**
	 * Gemini yanıtı komut mu: "KOMUT: radyo kartına geç ; Kral FM aç" → [radyo kartına geç, Kral FM aç]
	 * (en fazla 3 komut). Komut değilse null.
	 */
	@Nullable
	static List<String> commands(@Nullable String answer) {
		if (answer == null) return null;
		String s = answer.trim();
		int i = s.toUpperCase(new java.util.Locale("tr")).indexOf("KOMUT:");
		if (i < 0 || i > 3) return null; // yalnız yanıtın başındaysa
		String body = s.substring(i + 6).trim();
		int nl = body.indexOf('\n');
		if (nl >= 0) body = body.substring(0, nl);
		List<String> out = new ArrayList<>();
		for (String p : body.split("[;|]")) {
			String c = p.trim().replaceAll("^[\"'“”]+|[\"'“”.!]+$", "").trim();
			if (!c.isEmpty() && (out.size() < 3)) out.add(c);
		}
		return out.isEmpty() ? null : out;
	}
}
