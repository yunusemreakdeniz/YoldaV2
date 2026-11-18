# Yolda arayüz günlüğü

Her arayüz turunda yapılan iyileştirmeler. En yenisi en üstte.

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
