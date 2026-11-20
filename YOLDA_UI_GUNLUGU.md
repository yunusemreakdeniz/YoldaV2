# Yolda arayüz günlüğü

Her arayüz turunda yapılan iyileştirmeler. En yenisi en üstte.

## 2026-11-20 · v2-204 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-204 · Hızlı açılış, son açılanlar

- Kanallar sayfası ve telefon görünümü kademeli kurulur: ekranı dolduran ilk satırlar hemen, kalanı her karede birkaç tane (açılışta donma azaldı).
- Kanallar sayfasında 'Son açılanlar' şeridi (TV / radyo son 6 kanal, tek dokunuşla).
- 40 ms'yi aşan ekran kurulumları tanı günlüğüne ('Kurulum · ...').

## 2026-11-20 · v2-202 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-202 · Çökme düzeltmesi

- Telefon görünümünde Kart ekle → Kartları düzenle / Desteyi varsayılana döndür uygulamayı kapatıyordu: artık telefonun kart düzenleyicisi açılır, ızgara yeniden kurulur.

## 2026-11-19 · v2-201 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-201 · Güneş, bulut, sis yeniden

- Güneş: ılık gök geçişi, katmanlı sıcak güneş, aşağı inen sönen hüzmeler (sakin, dönmez). Bulut: mavi gök, hacimli gölgeli bulutlar, üç katman. Sis: renkleri bastıran örtü, içinden dağılan ışık, zıt yönlerde süzülen tabakalar.

## 2026-11-19 · v2-200 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-200 · Gerçekçi hava sahneleri

- Yağmur: üç derinlik, sıçrama halkaları, camda yansıyan ve kayan su damlaları. Fırtına: dallanan parlak şimşek. Kar: yumuşak flu taneler, odak dışı yakın taneler, birikinti parıltısı. Yıldızlı gece: Samanyolu, renkli yıldızlar ve ışık kırınımı, sönen kuyruklu kayan yıldız.

## 2026-11-19 · v2-199 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-199 · Akıcılık

- Kaydırırken / dokunurken zemin animasyonu bekler; renk geçişi en çok ~30 kare ve önbellekli; kaydırmada kartlar donanım katmanında.
- Yavaş kare ölçer: takılma varsa tanı günlüğüne 'Akıcılık · ekran: kare, yavaş, donma'.

## 2026-11-19 · v2-198 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-198 · Yolculuk mesafesi

- Android Auto bağlanınca başlayan yolculuk eski konumdan mesafe saymıyor (ilk taze konum başlangıç).

## 2026-11-19 · v2-197 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-197 · Daha çok kanal, az veri radyo

- TV 50 → 95 kanal (denenmiş resmi yayınlar; yeni gruplar Yerel, Dini, Eğitim), radyo 21 → 85 istasyon (FM istasyonları önde).
- Radyolarda az veri: aynı istasyonun 48 kbps ve üstü en düşük yayını; Radyo ekle'de saatte ~MB.

## 2026-11-19 · v2-195 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-195 · Düzenle kipi düzeltmesi

- Dar telefonda Düzenle kipine girince deste başka karta kayıyordu (kartın ✎ düğmesi görünmüyordu): saat yalnız küçülür, başlık boyu sabit, ortadaki kart korunur.

## 2026-11-19 · v2-194 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-194 · Yolculuk kaydı

- Android Auto bağlanınca yolculuk kendiliğinden başlar, kesilince biter. Telefonda kendiliğinden başlamaz: Yolculuklar kartında ve sayfasında Yolculuğu başlat / bitir; sesle de.

## 2026-11-19 · v2-193 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-193 · Sakin güneş

- Güneşli sahne sakin: ışınlar dönmez (sabit, yalnız ışıkları çok yavaş belirip solar), uçuşan zerreler yok, iki soluk mercek parlaması.
- Kart düzenlemede içerik kurulamazsa nedeni tanı günlüğüne yazılır.

## 2026-11-19 · v2-191 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-191 · Güneş, bulut, sis

- Güneş: ılık gök tonu, hale, nefes alan ince ışınlar, mercek parlamaları, ışıkta süzülen zerreler. Bulut: gerçek bulut biçimi, iki katman, gündüz ışıklı / gece mavi-gri ve ay. Sis: alta koyulaşan pus, süzülen bantlar. Üçü de ~20 kare.

## 2026-11-19 · v2-190 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-190 · Tek kanal düzenleyici

- TV ve radyo: Kanallar sayfasındaki Düzenle ile kartın içindeki düzenleme aynı pencere: sürükle / ▲ ▼ sırala, ★ favori, ada dokun gizle; her değişiklik hemen kaydedilir.

## 2026-11-19 · v2-189 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-189 · Ekolayzer, yer önerileri, hava efekti

- Ekolayzer sayfasında beş büyük dikey bant (-10..+10 dB), oynatınca Özel ayar, Sıfırla.
- Navigasyon önerileri opak zeminde okunur; yakına ağırlık, Türkiye kutusu, telefonun adres araması yedeği, Aranıyor / Sonuç yok.
- Tema: hava efekti Otomatik, Kapalı ya da her zaman seçilen (Güneş, Yıldız, Bulut, Sis, Yağmur, Kar, Fırtına).

## 2026-11-19 · v2-188 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-188 · Akıcı hava sahnesi

- Yağmur / kar / yıldız / fırtına saniyede ~30 kare ve takılmasız (ışıklar önbellekte); yağmur iki derinlikte parlak başlı damla, kar salınan haleli taneler, kayan yıldız, güneş ışınları, yumuşak şimşek.
- Dar telefon başlığında tema düğmesi artık kaybolmuyor (aralıklar daraldı, saat sığacak kadar küçülür).

## 2026-11-19 · v2-187 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-187 · TomTom hız sınırı

- İsteğe bağlı TomTom hız sınırı: Menü → "TomTom hız sınırı" ile kullanıcının ücretsiz anahtarı; OSM'de sınırı yazılı olmayan yollarda TomTom'un sınırı (sıra: OSM yazılı, TomTom, tahmin); günde en çok 2.000 istek.
- Alanı çizilmemiş köy / kasabalarda yakın yerleşim noktasıyla şehir içi tahmini; yol değişince tanı günlüğüne sınırın kaynağı.

## 2026-11-19 · v2-186 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-186 · Hey Yolda düzeltmesi

- "Hey Yolda" denmeden açılıyordu: sıkı kip (varsayılan) yalnız kesin sonuçta, yüksek güvenle (>= 0.88), doğal sürede ve sohbetin ortasında değilken uyanır; tek "yola" uyandırmaz. Menü'de "Hey Yolda hassasiyeti: Sıkı / Normal".

## 2026-11-19 · v2-185 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-185 · Paket B

- Zemin hava durumuna göre: yağmurda damla, karda tane, açık gündüz güneş, açık gece yıldız, sis, bulut, fırtına; araçta ve animasyon kapalıyken sabit; Tema seçicide aç / kapa.
- Ölçülü animasyonlar: ekran açılışında kısa soluklaşma ve yükselme (araçta yalnız soluklaşma), basınca hafif küçülme.
- Pro tasarım dili: tek yazı ölçeği, köşe basamakları, yazı rengi tokenları (her temada okunurluk testi).
- Android Auto: kart yüzünde en çok 6 öğe, kısa tek satır çip yazıları.
- Hız sınırı: yerleşim alanı içindeyken adsız ana yolda 50 (dışında 90), bölünmüş yolda 70 / 110.
- Reklam engelleyici: daha çok reklam ağı, her sitede reklam betiği dosyaları, boş reklam kutuları gizlenir, sayfa dokunulmadan pencere açamaz.
- Sesle ve Gemini ile kart yönetimi: "X kartını gizle / geri getir / başa al / Y kartının ardına taşı"; Gemini kart listesini bilir.
- Müzik kartı ilk açılışta boş kalmaz; hızlı geçiş şeridi 33 kartta ekrana sığar (son karta varılır).
- Yeni cihaz testi: her ekranda her tıklama (çökme / boş ekran yok).

## 2026-11-19 · v2-184 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-184 · Gemini düzeltmesi

- Gemini 503 ("model aşırı yüklü") ve uzun bekleme: önce hızlı Lite modeller (gemini-3.5-flash-lite, flash-lite-latest), sonra Flash; yoğunlukta kısa bekleyip tekrar dener, 12 sn yanıtsız model atlanır. Yeni anahtarlara kapalı 2.5 modelleri çıkarıldı.
- Zaman aşımı artık "İnternet bağlantısı yok" diye gösterilmiyor; 400 yalnız gerçek anahtar hatasında "anahtar geçersiz".

## 2026-11-19 · v2-182 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-182 · Paket A (tamamı)

- Yeni kartlar: Podcast (arama, abonelik, son bölümü çal), Medya (telefondaki videolar kaldığı yerden, fotoğraflar), Trafik (eve / işe süre ve mesafe, trafik hariç tahmini; canlı trafik Google Haritalar'da), Spor (Süper Lig son sonuçlar ve fikstür, tuttuğun takım öne). Deste 33 kart.
- Sesli: "eve kaç dakika", "işe ne kadar sürer", "maç sonuçları", "galatasaray maçı ne zaman", "X podcastini aç".
- TV'de çok kanal değiştirince görüntü gidiyordu: görüntü gelmezse yüzey kendiliğinden yeniden bağlanır (en çok iki kez), tanı günlüğüne yazılır.

## 2026-11-19 · v2-181 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-181 · Paket A (1. bölüm)

- Yeni kartlar: Telefon durumu (pil, bağlantı, sinyal, aylık mobil veri ve sınırı, hızlı ayarlar), Şarj istasyonları, Notlar (sesli not, kalıcı hatırlatmalar), Hızlı mesaj (hazır cevaplar, varınca haber ver), Takvim (bugün / yarın), Araç bakımı (muayene, sigorta, kasko, yağ değişimi; 7 gün kala hatırlatma). Deste 29 kart.
- Mobil veri sınırı: %80'de ve sınırda sesli uyarı, sınırda video 360p; "Devam et" ile ay sonuna kadar susar.
- Akaryakıt fiyatı bulunduğun il / ilçeye göre (Opet servisi; alınamazsa Türkiye ortalaması), resmi fiyat sayfaları ve pompadaki fiyatı elle girme.
- Gideceğin yerdeki otoparklar (Park sayfası, sesle "gideceğim yerde otopark var mı").
- Sesli okumayı durdurma (ekranda düğme, duraklat tuşu, "dur / sus"); Gemini anahtarı çalışıyorsa bütün sesli konuşma ve yönetim Gemini'den (ulaşılamazsa yerel); "son uyarılar", "az önce ne dedin".
- Telefon ve Android Auto kart sıraları ayrı; telefon görünümünden araca araba simgeli düğme; dar ekranda başlık, düğme ve sayfa noktası taşmaları düzeltildi; küçük pencereden (PiP) dönünce tam ekran.

## 2026-11-19 · v2-179 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-179

- Video izlerken kalite: sağ üstte "HD 480p ▾" düğmesi; dokununca 360p · 480p · 720p · 1080p · Otomatik, seçilen kalite çalan videoya (TV ve YouTube) hemen uygulanır; bir süre sonra soluklaşır.
- Yeni Döviz ve altın kartı ve sayfası: dolar, euro, sterlin (TCMB), gram ve çeyrek altın; sesle "dolar kaç", "gram altın ne kadar".
- Yeni Namaz vakitleri kartı ve sayfası: bulunduğun yere göre altı vakit (Diyanet hesap yöntemi), sıradaki vurgulu ve kalan süre; sesle "akşam ezanı ne zaman".
- Yeni Haberler kartı ve sayfası: son dakika başlıkları (TRT Haber, AA); dokununca okunur, "Başlıkları oku", sesle "haberleri oku". Deste 23 kart; tasarım taslağı güncellendi.

## 2026-11-19 · v2-178 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-178

- Gemini ile yerel sesli asistan artık karışmıyor: kesin cihaz komutları (ses, oynatma, kanal adı, kart, arama, tema, sürüş, saat) yerelde ve hızlı; sohbet, bilgi soruları, anlaşılmayan ve uzun doğal cümleler ("yol boyunca dinleyebileceğim güzel şarkılar çal") doğrudan Gemini'ye.
- Kanal adı tutmazsa "anlayamadım" yerine Gemini'ye sorulur ("biraz rahatlatıcı bir şey aç").
- Geç gelen Gemini yanıtı yeni komutun üstüne konuşmaz; Gemini soru sorarsa ("Hangi şehir?") konuşma bitince mikrofon kendiliğinden açılır.
- Gemini neyin çaldığını, araçta mı telefonda mı olduğunu ve hızı bilir; yanıtlardaki işaretler (*, #, emoji) okunmaz; 10 dakika sessizlikten sonra eski konu unutulur.

## 2026-11-19 · v2-177 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-177

- Araç görünümünden telefon görünümüne dönülemiyordu (dikey telefonda düğme gizliydi): artık Araç görünümünün üst çubuğunda her zaman "Telefon görünümü" düğmesi var; sesle "telefon görünümüne geç" / "araç görünümüne geç".
- Telefon görünümünde düğme yazıları hizalandı: Ev, İş, Rota, Hey Yolda yazıları tek satır ve tam ortada (İ noktası satırı kaydırmıyor, büyük yazıda iki satıra kırılmıyor); durum kutularındaki yazılar da.
- Telefon | Araç anahtarı 48dp dokunma alanına çıktı, yazısı ortada.
- Emülatör testi: Araç görünümüne geçip düğmeyle telefona geri dönmeyi de deniyor.

## 2026-11-19 · v2-176 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-176

- Telefon görünümü tasarım taslağına göre yenilendi: şimdi çalıyor şeridinde türün renkli kutusu (radyo turuncu, TV mavi, müzik mor, video kırmızı) ve canlı yayında kırmızı "● CANLI · RADYO / TV".
- Hava kutusu iki satır (büyük sıcaklık, altında yer), hız sınırı kırmızı halkalı levha olarak, hızlı erişim düğmeleri kendi renkleriyle (Ev, İş, Rota, Hey Yolda).
- Tasarım taslağı da bugünkü sürüme göre güncellendi (19 kart, tam sayfa): https://claude.ai/artifact/Wb9zRssqERUwyso4xH64YW
- Yeni emülatör testi: telefon görünümünde hava, hızlı erişim, bütün kartlar ve "Kart ekle" var, hepsi en az 48dp.

## 2026-11-19 · v2-175 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-175

- "Video + harita": telefonda harita uygulaması yoksa video artık küçük pencereye geçmiyor, "Harita uygulaması yok" deniyor (önceden küçük pencerede kalıp ekran yanıt vermiyor gibi görünüyordu).
- Dayanıklılık testindeki donmanın nedeni bulundu ve listeye yazıldı (küçük pencerede odak).

## 2026-11-19 · v2-174 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-174

- Yeni "Kaldığın yerden" sayfası (karttaki "Tümünü gör ›"): son çalınanların tamamı, türü, ne zaman açıldığı ve ilerleme çubuğu; her satırda "Devam et", en üstte büyük "Sürdür", "Geçmişi temizle". Karta dokunmak eskisi gibi kaldığı yerden çalar.
- Yeni Ekolayzer sayfası (karta dokununca): sekiz hazır ayar iki sütunlu büyük kutucuklarda, kısa açıklamalarıyla ("Yol uğultusuna karşı"); "Bant ayarları" bir şey çalarken.
- Artık her sabit kartın kendi sayfası var; yeni sayfalar ekran denetim testlerine eklendi.

## 2026-11-19 · v2-173 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-173

- 48dp'nin altında kalan son dokunma alanları büyüdü: destenin üst çubuğundaki tema, telefon görünümü, ayarlar, kaldırılan kartlar ve düzenle düğmeleri; düzenlerken kartlardaki ✕ ve ✎ (44 → 48dp).
- Kanallar ekranındaki gizle / göster düğmeleri (42 → 48dp), ana ekrandaki küçük oynatma düğmeleri (40 → 48dp) ve radar uyarısındaki ✕ (44 → 48dp).
- ✎ düğmesi büyüyen ✕'in altında 8dp boşlukla; kart yazıları ✕'in altına girmez.

## 2026-11-19 · v2-172 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-172

- Video kalitesi oynatıcının menüsünden: "Video kalitesi: 480p" → 360p, 480p (veri dostu), 720p, 1080p, Otomatik; seçim çalan videoda hemen geçerli (TV ve YouTube).
- Sesle: "video kalitesini 720 yap", "full hd yap", "kaliteyi düşür / artır", "video kalitesi ne"; Gemini de aynı komutu kullanır.

## 2026-11-19 · v2-171 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-171

- Hava ekranında uzun yer adları artık gerçekten sığana kadar küçülüyor (önceki düzeltme tek satır ayarı yüzünden işlemiyordu).
- Emülatör testleri takılmadan baştan sona çalışıyor: kalan tek UI sorunu buydu; dayanıklılık testindeki donmalar incelendi (uygulama boştayken pencere odak alamıyor), listede R3.

## 2026-11-19 · v2-170 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-170

- Yeni Ekolayzer kartı: Kapalı, Pop, Rock, Konuşma, Bas, Araç, Klasik, Caz; dokununca çalan yayına hemen uygulanır, seçili olan vurgulu.
- Ayarlar her cihazın kendi bantlarına uyarlanır (bant sayısı ve frekansları cihaza göre değişir); "Araç" yol uğultusuna karşı basları ve tizleri öne çıkarır.
- Karta dokununca bant bant ayar ekranı (bir şey çalarken); sesle: "rock ekolayzer", "ses modunu konuşma yap", "ekolayzeri kapat".

## 2026-11-19 · v2-169 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-169

- "Kaldığın yerden" kartı artık son çalınanların geçmişini tutar (en çok 12): TV, radyo, müzik ve videolar, ne zaman açıldıkları ("5 dk önce", "dün") ve ne kadarı dinlendiği (%42).
- Kartta son kanal ve şarkının yanında geçmişten 5 çip; dokununca o öğe yeniden açılır (radyo dizininden eklenen istasyonlar ve yedek TV adresleri dahil).
- Alt satırda son iki öğe, ilerleme ve zaman.

## 2026-11-19 · v2-168 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-168

- Gemini bağlıysa uygulamayı o yönetir: doğal istekleri ("biraz rahatlatıcı bir şey aç", "beni en yakın eczaneye götür", "çok gürültülü") anlayıp uygulamanın komutuna çevirir, komut hemen yürür.
- Tek eylem kataloğu: oynatma, kanallar, navigasyon, arama / mesaj, kartlar, sürüş uyarıları, hatırlatma, tema; Gemini de yerel komut çözücü de aynı listeyi kullanır.
- Bir cümlede en çok 3 iş ("radyo kartına geç ve Kral FM aç"); Gemini'nin çevirdiği komut anlaşılmazsa döngüye girmez, "Bunu yapamadım" der.

## 2026-11-19 · v2-166 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-166

- TV ve Radyo kartında "Kanalları düzenle": tüm liste tek sırayla; sürükle ya da ▲ ▼ ile taşı, ada dokununca kanalı gizle / göster.
- Aynı sıra ve gizleme her yerde: kartın yüzü, Kanallar ekranı ve direksiyondan / sesle "sonraki kanal" (gizlenenler atlanır).
- Emülatör testlerindeki gerçek hatalar düzeltildi: hava ekranında uzun yer adı başlıkta kesilmiyor (sığana kadar küçülür); takılan test sınıfı artık tüm testi kilitlemiyor.

## 2026-11-19 · v2-163 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-163

- Kartlardaki erişim düğmeleri büyüdü: TV / radyo kanal kutucukları, kısayol çipleri ve Aç düğmesi telefonda en az 56dp, araçta 64dp.
- Kartta yer yetmezse düğmeler küçülmez, önce alt yazı satırı kısalır.
- Adı altında yazan kutucukların (favoriler, kanallar) görsel kutusu en az 48dp; dokunma alanı adıyla birlikte 56dp ve üstü.

## 2026-11-19 · v2-162 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-162

- Kart simgeleri başlıkla aynı hizada: koyu (asil) temada başlığın altındaki vurgu çizgisi artık simgeyi aşağı itmiyor.
- Simge kutusu 8dp büyüdü (en az 56dp); simge kutuyu daha çok dolduruyor (renkli simgeler %72, marka logoları %66).
- Telefon görünümü kutucuklarındaki simgeler de büyüdü (24 → 28dp).

## 2026-11-19 · v2-161 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-161

- Yeni Akaryakıt kartı ve sayfası: benzin, motorin ve LPG Türkiye ortalaması (TL / litre) ve bu haftaki değişim (▲ / ▼).
- Yakıt türünü seç, aracının tüketimini (L / 100 km) ayarla: 100 km ve son yolculuğun yakıt maliyeti hesaplanır.
- "En yakın benzinlik" tek dokunuşla yol tarifi; sesle "akaryakıt kartına geç". Veri: OpenVan.camp (CC BY 4.0), 6 saatte bir yenilenir.

## 2026-11-19 · v2-160 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-160

- Navigasyon aramasında yazdıkça yer önerileri: Ev, İş ve son gidilenler anında; 3 harften sonra OpenStreetMap önerileri (bulunduğun yere yakın olanlar önce, uzaklığıyla).
- Öneriye dokununca yol tarifi doğrudan başlar; araçta öneriler arama kutusunun hemen üstünde, en az 56dp satırlarla.
- Deste testi 18 kartlık desteye göre düzeltildi (birim testleri yeniden yeşil).

## 2026-11-19 · v2-158 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-158

- Uyarı geçmişi: radar, hız aşımı, ortalama hız, okul / hastane, mola, yol tehlikesi ve hava uyarıları zaman ve yerle kaydedilir (son 300).
- Sürüş ekranında "Son uyarılar" listesi: "Tümünü göster" ve "Geçmişi temizle" (iki dokunuş); yeri bilinen uyarıya dokununca haritada açılır.
- Yolculuklar özetinde o yolculukta verilen uyarılar ve sayımı ("3 radar · 1 hız aşımı").

## 2026-11-19 · v2-157 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-157

- Yeni sesli sürüş uyarıları: hemzemin geçit, kasis / hız tümseği, okul yanındaki yaya geçidi, keskin viraj, tünel ("farlarını aç"), gişe ve soğukta köprü buzlanması.
- Uyarı mesafesi hıza göre ayarlanır (hızlıyken daha erken); yalnız önündekiler, her biri bir kez söylenir.
- Keskin viraj yolun şeklinden hesaplanır (ana yollarda, 60 km/s üstünde); köprü uyarısı hava 3°C ve altındayken.
- Her uyarı Sürüş ekranından ayrı ayrı açılıp kapatılır.

## 2026-11-19 · v2-156 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-156

- Radyo ekle listesinde artık Türkiye'deki tüm çalışan istasyonlar var (açık radyo dizini; en çok dinlenen önce).
- Tür çipleri (Haber, Pop, Türk sanat / halk, Arabesk, Rock, Slow, Klasik / Caz, Spor, Dini, Çocuk) ve en çok istasyonu olan şehirlerin çipleri.
- Son denetimde çalışmayan istasyonlar listeye gelmez; alt satırda şehir, tür ve yayın kalitesi (kbps) yazar.

## 2026-11-19 · v2-155 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-155

- TV kanalı açılmazsa artık hemen hata yok: aynı kanalın yedek yayın adresleri (iptv-org Türkiye listesi) sırayla otomatik denenir.
- Çalışan yedek adres telefonda hatırlanır; o kanal bir dahaki sefere önce o adresle denenir. Liste haftada bir telefonda yenilenir.
- Her açılışın süresi ve kullanılan adres değişikliği tanı günlüğüne yazılır; haftalık "Kanal denetimi" iş akışı ölü / yavaş adresleri raporlar.

## 2026-11-18 · v2-153 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-153

- Araçtayken gelen WhatsApp, WhatsApp Business, Telegram ve SMS mesajları sesli okunur ("WhatsApp mesajı, Ahmet yazdı: …").
- Sesle yanıt: "Hey Yolda, yanıtla geliyorum" ya da "on dakikaya oradayım diye cevap ver"; "mesajı tekrar oku", "kim yazdı".
- Bir kez izin: Kurulum ekranında "Mesajları sesli oku" → Bildirimlere erişim → Yolda V2. Mesaj metni günlüğe yazılmaz.

## 2026-11-18 · v2-152 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-152

- Sürüş kartı ve ekranı artık yalnız radar ve hız: park ve yolculuk bölümleri kalktı.
- Yeni Park kartı ve sayfası: aracının uzaklığı ve bırakıldığı saat; "Buraya park ettim", "Aracıma git"; park yerleri listesi (Git / Sil), "Tümünü temizle" ve "Park yerimi hatırla" anahtarı.
- Yolculuklar sayfasında "Bu yolculuğu sil" ve "Tümünü temizle" (iki kez dokunarak).
- Ekran testlerine Park, Yolculuklar, Kişiler ve Hava ekranları eklendi.

## 2026-11-18 · v2-151 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-151

- Videolar artık varsayılan 480p açılır (YouTube ve TV): veri kullanımı belirgin düşer.
- Kalite seçimi: Ayarlar kartındaki "Video kalitesi" ve Menü → Ayarlar; dokundukça 360p → 480p → 720p → 1080p → Otomatik.
- Otomatik: Wi-Fi'de en iyi kalite, mobil veride en çok 720p. Görüntü ekranda değilken (yalnız ses) en düşük kalite.

## 2026-11-18 · v2-150 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-150

- Telefon kartından WhatsApp vb. açmak araçta çalışmıyordu: Yolda telefonda arka planda olduğundan Android uygulamayı açtırmıyordu. Artık önce izinler denetlenir (eksikse hangisinin gerektiği söylenir), sonra telefon ekranı araçta açılır, en son uygulama öne gelir.
- Tanı günlüğüne uygulamanın açılıp açılmadığı ve izin durumu yazılır.

## 2026-11-18 · v2-149 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-149

- Düzeltme: telefon görünümü (dikey ızgara, Telefon | Araç anahtarı) gerçek telefonda hiç açılmıyordu; uygulama kendini test ortamı sanıp her zaman desteyi gösteriyordu. Artık telefonda varsayılan telefon görünümü.

## 2026-11-18 · v2-148 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-148

- Yeni "Yolculuklar" kartı (Sürüş kartının yanında): bu ayın özeti ("Bu ay 12 yolculuk · 486 km · 9 sa 20 dk") ve son yolculuklar; birine dokununca ayrıntısı açılır.
- Yolculuk özeti ekranında ayın toplamı ve bütün yolculuklar (60'a kadar) listelenir; dokununca o yolculuğun ayrıntısı.
- Kart mevcut destelere de bir kez eklenir; sesle: "yolculuklar kartına geç".

## 2026-11-18 · v2-147 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-147

- Kartlardaki simge kutuları büyüdü (48–60dp), simgeler kutunun içinde daha büyük.
- Kart düğmeleri (Aç, ＋, ✎ ve yuvarlak düğmeler) artık en az 48dp; araçta 64dp'ye kadar.
- Telefon görünümündeki kart simgeleri 40dp kutuda, 24dp simge.

## 2026-11-18 · v2-146 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-146

- Kişiler kartına dokununca artık kendi sayfası açılır: eklediğin kişiler büyük satırlarda, renkli baş harfleriyle; dokununca arar.
- Listenin sonunda "＋ Kişi ekle"; sağ üstte "Düzenle" ile ▲ ▼ sıralama ve ✕ kaldırma.
- Satırlar 72dp, düğmeler 48dp: araç ekranında da rahat dokunulur.
- Düzeltme: radyo / kanal açılırken (arabellek) önceki / sonraki tuşları yanlışlıkla kart geçişine gidiyordu; artık yalnız hiçbir şey yüklü değilken kartlarda gezinir.

## 2026-11-18 · v2-144 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-144

- Hiçbir şey çalmıyorken aracın önceki / sonraki tuşları (TRACK, direksiyon) kartlar arasında geçer.
- İki kez hızlı "sonraki": ortadaki kartı açar; iki kez hızlı "önceki": ana sayfaya (ana karta) döner; basılı tutunca iki kart birden geçer.
- Kartın içindeyken "önceki" geri gider. Döner düğme / yön tuşları da destede sola / sağa geçer, ortası kartı açar.
- Bir şey çalarken tuşlar eskisi gibi şarkı / kanal değiştirir.

## 2026-11-18 · v2-143 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-143

- Araçtan inince (yolculuk bitince) bildirim gelir: "Yolculuk bitti · 42,3 km" ve kısa özet.
- Bildirime dokununca yeni "Yolculuk özeti" ekranı açılır: nereden nereye, saatler, büyük mesafe; süre, ortalama, en yüksek hız ve radar / duraklama kutuları; rotayı haritada göster.
- Aynı ekranda önceki yolculuklar listesi; dokununca o yolculuğun özeti gösterilir.

## 2026-11-18 · v2-142 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-142

- Hız sınırı artık çok daha fazla yolda görünür: OpenStreetMap'te sınırı girilmemiş ana cadde, il ve devlet yollarında yönetmelikteki genel sınır tahmin edilir (yerleşim yerinde 50, şehirlerarası 90, bölünmüş yolda 110, otoyolda 120).
- Şehir içi mi dışı mı: yol adındaki Cadde / Bulvar / Sokak, kaldırım ve "D / O" yol numarasından anlaşılır.
- Tahmini sınırlar yanında "~" ile gösterilir; girilmiş gerçek sınır her zaman önce gelir.

## 2026-11-18 · v2-140 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-140

- Araçtayken (ya da yolculuk sürerken) bulunduğun yerin havası 15 dakikada ya da 10 km'de bir kontrol edilir.
- Yağmur, kar, sis, fırtına başlayınca, hava düzelince, sıcaklık 3 derecenin altına inince ve bir saat içinde yağış beklenince bildirim gelir, sesli söylenir (haritadayken de).
- Sürüş ekranında "Hava uyarıları" ile açılıp kapatılır.

## 2026-11-18 · v2-141 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-141

- Düzeltme: video / TV açıkken ana sayfaya dönünce kartların küçülmesi (testlerin yakaladığı hata) giderildi.
- Düzeltme: deste açıkken bir kart yenilenince ekranın telefon ızgarasına dönmesi giderildi.
- Dar telefonda görünüm değiştirme: Menü → Ayarlar → "Görünüm" (başlık taşmasın diye).

## 2026-11-18 · v2-139 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-139

- Radar kaynağı değişince sesli ve yazılı uyarı: hedef girilip Bakanlık listesi yüklenince "Radar kaynağı İçişleri Bakanlığı. Rotada N radar var"; alınamazsa ya da hedefe varınca "Radar kaynağı OpenStreetMap".
- Uyarı haritadayken de duyulur (radar sesli uyarısı açıksa); aynı hedefte "alınamadı" bir kez söylenir.
- Sürüş bildiriminde artık kaynak yazıyor: "· kaynak Bakanlık" ya da "· kaynak OSM".

## 2026-11-18 · v2-138 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-138

- Telefon görünümündeki boşluklar 8dp ızgarasına hizalandı: yan kenarlar 16dp, öğe araları 8dp, bölüm araları 24dp.
- Kart, kısayol ve durum kutularının yükseklikleri eşitlendi (64 / 96dp); iç boşluklar her kenarda aynı.
- Saat bölümünün üstü ve altı daha dengeli; listenin sonunda alttaki düğmelerin altında kalan kart kalmıyor.
