# Yolda yapılacaklar (kullanıcının istekleri)

Her turda sıradaki ilk açık madde yapılır, sürüm çıkınca [x] ile işaretlenir ve sürüm yazılır.
Yapılamayan ya da Android'in izin vermediği maddeler gerekçesiyle "Yapılamaz" altına taşınır.

## Açık (sırayla)

C. [ ] Kart logoları başlıkla aynı hizada değil, küçük ve ortalı kalıyor: başlık satırıyla hizala, gerekirse büyüt
D. [ ] Kartlardaki erişim düğmeleri büyük olsun (TV / radyo kanal kutucukları, kısayol çipleri, favori kutucukları); en az 56dp, araçta daha büyük
E. [ ] TV ve radyo düzenleme: tek sıralama; kartın içinden tüm liste düzenlenir; kartın yüzünde ve Kanallar ekranında aynı sıra
F. [ ] Sesli asistan: Gemini bağlıysa anlama da Gemini ile (doğal cümleler: "en yakın eczaneye yol tarifi", "biraz rahatlatıcı bir şey aç" → komut), bağlı değilse yerel model; hemen her soruya yanıt
G. [ ] "Kaldığın yerden" kartında daha çok veri: son dinlenen / izlenen birkaç öğe, ilerleme, ne zaman
H. [ ] Ekolayzer kartı (hazır ayarlar: Pop, Rock, Konuşma, Bas, Araç; bant ayarları)
B. [ ] Kartlardaki simge ve düğmelerin kalanı (kanal / favori kutucukları, kısayol çipleri) 48dp altında kalmasın
0. [ ] Kalıcı imza anahtarı: güncellemeler eskinin üzerine kurulsun, ayarlar / kart düzeni / görünüm silinmesin (kullanıcı onayı bekliyor: anahtar oluşturma ve GitHub gizli ayarları)
7. [ ] Her karta dokununca kendi sayfası (henüz sayfası olmayan kartlar için)
8. [ ] Medya kartı: telefondaki videolar (en yeni üstte, klasörler, kaldığı yerden) ve fotoğraflar (yalnız telefonda)
9. [ ] Mobil veri sınırı ve uyarısı (aç / kapat); aşınca devam edene devam
10. [ ] TV'de çok kanal değiştirince görüntünün gitmesi (tanı günlüğüyle)
11. [ ] Gidilen yerdeki otoparklar (OpenStreetMap)
12. [ ] Android Auto'da profesyonel görünüm: kalan sadeleştirmeler
13. [ ] Hava durumuna göre animasyonlu tema (gece / gündüz ayarlı)
14. [ ] Sisteme ölçülü animasyonlar (geçişler, kart açılışı)
15. [ ] Reklam engelleyiciyi güçlendirme
16. [ ] Yakıt fiyatları kartı (açık ve kullanım şartlarına uygun bir kaynak bulunursa)
17. [ ] Telefon görünümüne cihaz testleri
18. [ ] Padding / boşluk sorunlarının kalanı (kullanıcıdan örnek ekran görüntüsü)

## Sürekli (her 3 turda bir)

- Kısa çevrimiçi araştırma (Android Auto / CarPlay yönergeleri, Material 3, sürüş uygulamalarındaki yeni özellikler); bulunan en iyi 1 fikir buraya madde olarak eklenir ve sıraya girer.

## Yapılanlar

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
