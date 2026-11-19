# Yolda yapılacaklar (kullanıcının istekleri)

Her turda sıradaki ilk açık madde yapılır, sürüm çıkınca [x] ile işaretlenir ve sürüm yazılır.
Yapılamayan ya da Android'in izin vermediği maddeler gerekçesiyle "Yapılamaz" altına taşınır.

## Açık (sırayla)

V2. [x] (v2-172) Video kalitesini oynatıcının içinden de değiştirme ve sesle ("video kalitesini 720 yap")
S. [x] (v2-155) TV yayınlarını daha hızlı açan kaynaklar: kanal başına birden çok yayın adresi, en hızlı açılanı seç, ölü adresleri ele, açılış süresini ölç
T. [x] (v2-156) Daha çok radyo kanalı: açık radyo dizini (radio-browser) ile Türkiye istasyonlarının tamamı, tür / şehir süzgeci, çalışmayanları ele
K. [x] (v2-157) Daha çok sürüş uyarısı, hepsi sesli: hemzemin geçit, kasis / hız tümseği, yaya geçidi (okul yakını), keskin viraj, tünel, gişe, köprü (soğukta buzlanma); her biri Sürüş ekranından aç / kapat
L. [x] (v2-158) Uyarı geçmişi: verilen bütün uyarılar zaman ve yerle kaydedilir; Sürüş ekranında ve Yolculuklar özetinde listelenir
I. [x] (v2-160) Navigasyon aramasında yer önerileri (yazdıkça adres / yer önerileri; seçince rota)
J. [x] (v2-161) Akaryakıt fiyatları kartı (açık kaynak araştırması; bulunamazsa yakındaki istasyonlar kartı + fiyatı kullanıcı girer)
C. [x] (v2-162) Kart logoları başlıkla aynı hizada değil, küçük ve ortalı kalıyor: başlık satırıyla hizala, gerekirse büyüt
D. [x] (v2-163) Kartlardaki erişim düğmeleri büyük olsun (TV / radyo kanal kutucukları, kısayol çipleri, favori kutucukları); en az 56dp, araçta daha büyük
E. [x] (v2-166) TV ve radyo düzenleme: tek sıralama; kartın içinden tüm liste düzenlenir; kartın yüzünde ve Kanallar ekranında aynı sıra
F. [x] (v2-168) Sesli asistan: Gemini bağlıysa anlama da Gemini ile (doğal cümleler: "en yakın eczaneye yol tarifi", "biraz rahatlatıcı bir şey aç" → komut), bağlı değilse yerel model; hemen her soruya yanıt. Gemini bütün uygulamayı yönetebilsin: her ekran / kart / düğme için bir eylem kataloğu (araç çağırma), yerel motor da aynı kataloğu kullanır
G. [x] (v2-169) "Kaldığın yerden" kartında daha çok veri: son dinlenen / izlenen birkaç öğe, ilerleme, ne zaman
H. [x] (v2-170) Ekolayzer kartı (hazır ayarlar: Pop, Rock, Konuşma, Bas, Araç; bant ayarları)
B. [x] (v2-173) Kartlardaki simge ve düğmelerin kalanı (kanal / favori kutucukları, kısayol çipleri) 48dp altında kalmasın
0. [ ] Kalıcı imza anahtarı: güncellemeler eskinin üzerine kurulsun, ayarlar / kart düzeni / görünüm silinmesin (kullanıcı onayı bekliyor: anahtar oluşturma ve GitHub gizli ayarları)
7. [x] (v2-174) Her karta dokununca kendi sayfası (henüz sayfası olmayan kartlar için)
8. [ ] Medya kartı: telefondaki videolar (en yeni üstte, klasörler, kaldığı yerden) ve fotoğraflar (yalnız telefonda)
9. [ ] Mobil veri sınırı ve uyarısı (aç / kapat); aşınca devam edene devam
10. [ ] TV'de çok kanal değiştirince görüntünün gitmesi (tanı günlüğüyle)
11. [ ] Gidilen yerdeki otoparklar (OpenStreetMap)
12. [ ] Android Auto'da profesyonel görünüm: kalan sadeleştirmeler
13. [ ] Hava durumuna göre animasyonlu tema (gece / gündüz ayarlı)
14. [ ] Sisteme ölçülü animasyonlar (geçişler, kart açılışı)
15. [ ] Reklam engelleyiciyi güçlendirme
17. [x] (v2-176) Telefon görünümüne cihaz testleri
18. [ ] Padding / boşluk sorunlarının kalanı (kullanıcıdan örnek ekran görüntüsü)

M. [ ] Hız sınırı için daha iyi kaynak araştırması (ücretsiz / izinli API'ler, OSM verisinin daha geniş çekilmesi, yol sınıfı + yerleşim alanı poligonu ile daha doğru tahmin); hedef: hemen her yolda sınır
N. [ ] "Pro" görünüm: tasarım dili (yazı ölçeği, renk ve gölge tokenları, tek ikon seti, kart şablonu), bütün ekranlara uygulanması
O. [ ] Her tıklamanın testi: cihaz testlerinde her ekrandaki tıklanabilir öğelerin taranıp tıklanması (çökme / boş ekran yok), raporlanan hataların düzeltilmesi
P. [ ] Kartları kullanışlı ve modern yapma turu: her kartın yüzünde en sık 3 işi tek dokunuşla yapma, canlı bilgi, tutarlı düzen

### Yeni kart fikirleri (sırayla eklenecek)

Q1. [ ] Takvim (bugünkü etkinlikler, sıradaki toplantıya yol tarifi)
Q2. [ ] Hatırlatıcılar ve sesli notlar
Q3. [x] (v2-179) Döviz / altın
Q4. [x] (v2-179) Haberler (başlıklar, sesli okuma)
Q5. [ ] Podcast ve sesli kitap
Q6. [ ] Trafik durumu (eve / işe varış süresi)
Q7. [ ] Elektrikli araç şarj istasyonları
Q8. [ ] Araç bakım ve yakıt defteri (muayene, sigorta, yağ değişimi hatırlatma)
Q9. [x] (v2-179) Namaz vakitleri
Q10. [ ] Telefon durumu (pil, veri, sinyal) ve hızlı ayarlar
Q11. [ ] Hızlı mesaj (WhatsApp / SMS hazır cevaplar, "varınca haber ver")
Q12. [ ] Spor skorları
R1. [ ] Araç hareket edince videodan sese geçiş (araştırma: Android Auto 2026 "video-to-audio handoff"): TV / YouTube videosu araç 10 km/s'yi geçince yalnız ses olarak sürer (görüntü kapanır, veri azalır), park edince görüntü geri gelir; Sürüş ekranından aç / kapat
R3. [ ] Dayanıklılık testinde donma (ANR "no focused window"): neden bulundu (v2-175): rastgele dokunuş "Video + harita"yı açıyor, uygulama küçük pencereye (PiP) geçiyor, harita mağazaya yönleniyor; uygulama küçük penceredeyken gelen tuşlar odaksız pencereye düşüyor. v2-175: harita uygulaması yoksa küçük pencereye geçilmez. Kalan: başlatıcıdan dönünce küçük pencereden tam ekrana çıkış (onNewIntent'te PiP'ten çık), sonra monkey yeniden denenecek
R2. [ ] Uyarı geçmişi için sesli komut ("son uyarılar", "az önce ne dedin") ve Gemini'nin geçmişi okuyabilmesi

## Sürekli (her 3 turda bir)

- Kısa çevrimiçi araştırma (Android Auto / CarPlay yönergeleri, Material 3, sürüş uygulamalarındaki yeni özellikler); bulunan en iyi 1 fikir buraya madde olarak eklenir ve sıraya girer.
- Kendi önerilerim: her turda fark ettiğim kullanılabilirlik / görsel sorunları da madde olarak eklenir (kullanıcıdan beklemeden).

## Yapılanlar

- [x] Araçta mesajları sesli okuma ve sesle yanıtlama: WhatsApp, Telegram, SMS (v2-153)
- [x] Kartlar ayrıldı: Sürüş yalnız radar / hız, Park ayrı kart ve sayfa, Yolculuklarda sil / temizle (v2-152)
- [x] Videolar varsayılan 480p, kalite seçimi Ayarlar kartı ve Menü'de (v2-151)
- [x] Telefon kartında uygulamaların araçta açılmaması (WhatsApp vb.) düzeltildi (v2-150)
- [x] Düzeltme: telefon görünümü gerçek telefonda hiç açılmıyordu (v2-149)
- [x] Yolculuklar (sürüş geçmişi) kartı: bütün yolculuklar, ayrıntı, bu ayın toplamı (v2-148)
- [x] Kart simgeleri ve düğmeleri büyüdü, en az 48dp (v2-147)
- [x] Kişiler sayfası: kişiler, altta ekle, düzenle (v2-145, düzeltme v2-146)
- [x] Aracın önceki / sonraki tuşları ve döner düğmeyle kart geçişi (v2-144)
- [x] Araçtan inince yolculuk özeti bildirimi ve özet ekranı (v2-143)
- [x] Hız sınırı girilmemiş yollarda tahmini yasal sınır (v2-142)
- [x] Video açıkken kartların küçülmesi düzeltildi (v2-141)
- [x] Araçta hava takibi; değişince bildirim ve sesli uyarı (v2-140)
- [x] Radar kaynağı değişince sesli uyarı ve bildirimde kaynak (v2-139)
- [x] Telefon görünümünde 8dp boşluk düzeni (v2-138)
- [x] Telefon görünümü, Telefon | Araç anahtarı, sade kartlar (v2-137)
- [x] Gemini bağlantısı, araçta arama (v2-136)
- [x] Kanal sırası ve başa dönme, pil, kart düğmeleri, tema renkleri, imza, araçta büyük yazı (v2-134)

## Yapılamaz / sınırlı

- Netflix vb. korumalı videolar araç ekranına aktarılınca siyah görünür (Android koruması).
- Telefon ekranını araçta kullanmak için Android her oturumda telefonda bir kez "paylaşımı başlat" onayı ister.
