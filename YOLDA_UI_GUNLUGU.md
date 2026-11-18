# Yolda arayüz günlüğü

Her arayüz turunda yapılan iyileştirmeler. En yenisi en üstte.

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
