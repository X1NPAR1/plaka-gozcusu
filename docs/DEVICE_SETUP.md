# Cihaz Kurulumu

## 1. APK'yı telefona yükleme

**Kolay yol (USB kablosu gerekmez):**
1. `PlakaGozcusu-0.1.0-release.apk` dosyasını telefona kopyala (USB, Google Drive vb.).
2. Telefonda dosyaya dokun. "Bilinmeyen kaynaklardan yükleme" izni sorulursa bu kaynağa izin ver.
3. "Yükle"ye dokun. Kurulum bitince izni yeniden kapatabilirsin.

**ADB ile (USB hata ayıklama):**
1. Ayarlar → Telefon hakkında → "Derleme numarası"na 7 kez dokun (Geliştirici seçenekleri açılır).
2. Ayarlar → Geliştirici seçenekleri → "USB hata ayıklama"yı aç.
3. Telefonu USB ile bağla, telefonda çıkan "USB hata ayıklamaya izin ver" sorusunu onayla.
4. Bilgisayarda (PowerShell):
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "F:\DevCache\release\PlakaGozcusu-0.1.0-release.apk"
```
`devices` çıktısında cihazın yanında `device` yazmalı (`unauthorized` ise 3. adımdaki onayı ver).

## 2. Telefonu sabitleme
- Sağlam bir pencere/raf tutucusu kullan; telefon düşmemeli, rüzgârda titrememeli.
- Kamera camına mümkün olduğunca yakın dursun (cam yansımasını azaltır); odadaki ışıkları kapatmak gece yansımayı azaltır.
- Telefonu **şarja bağlı** tut, doğrudan güneş almayan bir yere koy (ısınma).
- Kamerayı kendi mülkünün önüne ve bitişik yola yönelt; komşuların özel alanlarını kadraja alma.

## 3. İlk açılış ve kalibrasyon
1. Tanıtımı geç → kamera iznini ver.
2. **Yakınlaştırma:** plakaların okunacağı yol bölümü kadrajı doldursun ama araç tamamen sığsın.
3. **Yol alanı:** "Yol alanı" modunda yolun köşelerine sırayla dokun (en az 3 nokta). Park alanlarını dışarıda bırak.
4. **Odak:** "Odak" moduna geç, yolun ortasına dokun, "Odağı kilitle".
5. "Test fotoğrafı çek" → çekilen çözünürlüğü kontrol et. "Teorik plaka genişliği" kartı lens bilgisinden hesaplanan **üst sınırdır**; gerçek değer test D'de ölçülür.
6. "Bitti" → ayarlar kaydedilir.

## 4. Pil ve arka plan (üretici ayarları)
Uygulama izlerken kalıcı bir bildirim gösterir. Bazı üreticiler arka plandaki uygulamaları kapatır:
- **Samsung:** Ayarlar → Pil → Arka plan kullanım sınırları → "Hiç uyumayan uygulamalar"a Plaka Gözcüsü'nü ekle.
- **Xiaomi/Redmi/POCO:** Uygulama bilgisi → Pil tasarrufu → "Kısıtlama yok"; Otomatik başlatma → açık.
- **Huawei/Honor:** Pil → Uygulama başlatma → Plaka Gözcüsü → "Manuel yönet", üç seçeneği de aç.
- **Oppo/Realme/OnePlus:** Pil → "Arka planda çalışmaya izin ver".
- Genel: Ayarlar → Uygulamalar → Plaka Gözcüsü → Pil → "Kısıtlanmamış".

Telefon yeniden başlarsa izleme **kendiliğinden başlamaz** (Android kuralı); uygulamayı açıp "İzlemeyi başlat"a dokun.

## 5. Sorun giderme
| Belirti | Çözüm |
|---|---|
| "Kamera başka bir uygulama tarafından kullanılıyor" | Kamera kullanan uygulamayı kapat; izleme kendiliğinden sürer. |
| "Telefon yerinden oynamış olabilir" | Tutucuyu sabitle; görüntü 3 sn sabit kalınca kayıt devam eder. Gerekirse kalibrasyonu yenile. |
| "Telefon çok ısındı" | Gölgeye al, kılıfı çıkar, Ayarlar → Analiz yükü → Düşük. |
| Kayıt yok | Yol alanı doğru mu? Araçlar yol alanı içinde hareket ediyor mu? Bildirim "İzleniyor" mu? |
| Logları paylaşmak | `adb logcat -d -s PlakaGozcusu:* > plaka-log.txt` (loglarda plaka metni yoktur). |
