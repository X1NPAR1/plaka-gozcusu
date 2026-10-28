# Kullanım Kılavuzu

## Günlük kullanım
- **Ana ekran:** izleme durumu, "İzlemeyi başlat / Durdur", bugünün sayıları (araç, okunan, inceleme gerekli, okunamadı), kayıtların kapladığı alan ve son 5 kayıt.
- **Ekranı karart:** izleme sürerken ekranı siyaha çevirir (pil ve ekran yanığı için). Dokununca geri gelir.
- Telefonun ekranı kapalıyken de izleme bildirimi görünüyorsa izleme sürüyordur (cihaza göre değişebilir; test I).

## Kayıt durumları
| Durum | Anlamı |
|---|---|
| Yüksek güven | Birden fazla karede aynı plaka, iyi görüntü kalitesiyle okundu. **Yine de otomatiktir.** |
| İnceleme gerekli | Okuma var ama karelerde farklılık / düşük kalite / özel plaka biçimi. Fotoğrafa bak. |
| Okunamadı | Metin görüldü ama geçerli bir Türk plakası çıkarılamadı. Uygulama harf uydurmaz. |
| Plaka görünmüyor | Araçta hiç metin okunamadı (açı, uzaklık, gece). |
| Kullanıcı doğruladı / düzeltti | Sen onayladın veya düzelttin; otomatik okuma "düzeltme öncesi" olarak saklanır. |

## Bir olayı bulma (ör. "dün 14:00–14:30 arası")
1. **Olay Bul** sekmesi → "Dün" hazır seçeneği veya tarih + başlangıç + bitiş saatini seç.
2. O aralıktaki **tüm** araçlar (okunamayanlar dahil) zaman sırasıyla listelenir.
3. Bir kayda dokun → sahne fotoğrafı (iki parmakla yakınlaştır), araç ve plaka kırpıntısı, zamanlar.
4. **Tümünü koru:** bu kayıtlar saklama süresi dolsa da silinmez.
5. **Kanıt paketi oluştur:** ZIP'i nereye kaydedeceğini seç. İçinde orijinal fotoğraflar, Türkçe `rapor.html` ve `manifest.json` (SHA-256 özetleri) bulunur.

## Kayıt detayında
- **Dosya bütünlüğünü doğrula:** fotoğrafların kayıt anındaki SHA-256 ile aynı olup olmadığını kontrol eder.
- **Plakayı düzelt / Not ekle / Koru / Yıldızla / Sil / Dışa aktar.**

## Kanıt paketini doğrulama (bilgisayarda)
```powershell
Get-FileHash .\kayitlar\<kayıt-id>\sahne.jpg -Algorithm SHA256
```
Çıkan değer `manifest.json`/`rapor.html` içindeki değerle aynı olmalı.

## Saklama ve silme
Ayarlar → "Kayıtları sakla: N gün" ve "En fazla kullanılacak alan". Her gün bir kez, korunmayan en eski kayıtlar silinir. Kayıt silindiğinde fotoğrafları da silinir.
