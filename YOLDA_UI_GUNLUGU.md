# Yolda arayüz günlüğü

Her arayüz turunda yapılan iyileştirmeler. En yenisi en üstte.

## 2026-11-18 · v2-140 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-140

- Araçtayken (ya da yolculuk sürerken) bulunduğun yerin havası 15 dakikada ya da 10 km'de bir kontrol edilir.
- Yağmur, kar, sis, fırtına başlayınca, hava düzelince, sıcaklık 3 derecenin altına inince ve bir saat içinde yağış beklenince bildirim gelir, sesli söylenir (haritadayken de).
- Sürüş ekranında "Hava uyarıları" ile açılıp kapatılır.

## 2026-11-18 · v2-139 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-139

- Radar kaynağı değişince sesli ve yazılı uyarı: hedef girilip Bakanlık listesi yüklenince "Radar kaynağı İçişleri Bakanlığı. Rotada N radar var"; alınamazsa ya da hedefe varınca "Radar kaynağı OpenStreetMap".
- Uyarı haritadayken de duyulur (radar sesli uyarısı açıksa); aynı hedefte "alınamadı" bir kez söylenir.
- Sürüş bildiriminde artık kaynak yazıyor: "· kaynak Bakanlık" ya da "· kaynak OSM".

## 2026-11-18 · v2-138 · https://github.com/yunusemreakdeniz/YoldaV2/releases/tag/v2-138

- Telefon görünümündeki boşluklar 8dp ızgarasına hizalandı: yan kenarlar 16dp, öğe araları 8dp, bölüm araları 24dp.
- Kart, kısayol ve durum kutularının yükseklikleri eşitlendi (64 / 96dp); iç boşluklar her kenarda aynı.
- Saat bölümünün üstü ve altı daha dengeli; listenin sonunda alttaki düğmelerin altında kalan kart kalmıyor.
