# Yolda (kişisel sürüm)

Yolda, [Fermata Auto](https://github.com/AndreyPavlenko/Fermata)'nun kişisel bir derlemesidir.
Fermata'nın yazarı Andrey Pavlenko'dur; lisans GPL-3.0'dır (`LICENSE`). Taban alınan Fermata
sürümünün commit kimliği `UPSTREAM_COMMIT` dosyasındadır.

Fermata'dan farkları:

- Uygulama adı **Yolda**, paket kimliği `me.aap.fermata.auto.yolda` (Fermata/Mola ile çakışmaz)
- Yolda simgesi
- `kanallar/yolda.m3u`: yalnız kanalların kendi yayın sunucularındaki adreslerden oluşan Türk TV listesi
- GitHub Actions: sabit anahtarla imzalı arm64 APK, Releases'a yüklenir

## Kurulum

1. Releases'tan `Yolda-N.apk` indirin.
2. KingInstaller ile (Shizuku seçeneği açık) kurun; normal kurulumda Android Auto göstermez.
3. Android Auto → Sürüm'e 10 kez dokun → ⋮ → Geliştirici ayarları → Bilinmeyen kaynaklar.
4. Yolda → Ayarlar → Eklentiler → TV'yi açın; TV → + → `kanallar/yolda.m3u` dosyasını ekleyin.

Arka koltuk ekranı için hazırlanmıştır. Araç hareket halindeyken sürücünün görebileceği ekranda video açmayın.

## Fermata'yı güncellemek

```bash
git remote add upstream https://github.com/AndreyPavlenko/Fermata.git
git fetch upstream
git merge upstream/master   # çakışma olursa yalnız simge ve workflow dosyalarında olur
```
