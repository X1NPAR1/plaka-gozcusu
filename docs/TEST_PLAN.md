# Test Plan

Automated results are reported in `TEST_REPORT.md` (passed / failed / skipped separately). Unit tests are never
presented as proof of real-camera behaviour.

## 1. Automated
| Area | Type | Phase |
|---|---|---|
| Plate normalization/validation, OCR confusions, consensus, quality, ranking, best frame | JVM unit (table-driven) | 6 |
| Tracker association/expiry, motion classification, scene shift, dedup | JVM unit | 6 |
| Coordinate transforms, ImageProxy closing, ring buffer, capture rate limiting | JVM unit | 3 |
| Room DAOs, FTS search, migrations (MigrationTestHelper) | Robolectric/instrumented | 4 |
| Atomic writes, crash recovery, orphan sweep, SHA-256, retention/cap, export ZIP | JVM/Robolectric | 4, 8 |
| Model golden tests, missing/corrupt model | JVM/instrumented | 5 |
| Pipeline replay (injected Clock) | JVM | 6–7 |
| Session state machine, watchdog, thermal adaptation | JVM unit | 7 |
| Compose UI flows (fake camera) | Compose UI | 8 |
| Startup/scroll macrobenchmark, inference microbenchmark | Device | 5, 10 |

## 2. Edge case matrix
| Edge case | Expected behaviour | Test |
|---|---|---|
| Night, headlights into camera | Exposure comp; low-quality candidates rejected; status UNREADABLE rather than guess | Manual D/F (night) + quality unit |
| Plate glare / reflective plate | Clipping detector lowers quality; other frames preferred | Quality unit + manual F |
| Rain, fog, dirty plate | NEEDS_REVIEW/UNREADABLE, raw OCR kept | Manual F |
| Vehicle at an angle | Deskew; lower confidence | Preprocessing unit + manual |
| Two-line (square) plate | Two-line handling in OCR crop | Parser unit + golden |
| Motorcycle | Vehicle class kept; rear plate small → honest status | Manual E/F |
| Partially occluded plate | No invented characters; NEEDS_REVIEW | Consensus unit |
| Trailer | Separate plate may differ; no forced merge | Manual H |
| Vehicle without plate | NO_PLATE_VISIBLE observation | Replay |
| Very fast vehicle (blur) | Blur score; best available frame; UNREADABLE if needed | Quality unit + manual |
| Reversing / U-turn | Single track, direction updated; one observation | Tracker unit + replay |
| Traffic jam (many slow tracks) | Bounded track count, OCR budget, no spam | Replay |
| Vehicle stops in front of house | Optional single "stopped" event | Motion unit |
| Parked cars always in view | Stationary → no repeated observations | Motion unit + replay + manual E |
| Pedestrians / bicycles | Not stored as vehicles (class filter) | Detector filter unit |
| Reflections through window glass | Guidance + ROI; documented limitation | Manual C |
| Camera bumped / drifting | Scene-shift → pause + warn; ROI recalibration prompt | Motion unit + manual |
| Sunrise/sunset transitions | AE adapts; quality scoring | Manual D |
| Clock / time-zone change, DST | UTC epoch + zone id stored; UI shows local time correctly | Time unit |
| Storage full mid-write | Atomic temp+rename; no partial files; monitoring degrades → stops with warning | Storage unit |
| App killed mid-save | Temp files swept; no DB row without files | Storage unit |
| Corrupted / missing model | Typed error, ModelUnavailable state, Turkish message | Model unit |
| Device lacks camera features | Capability report; graceful fallback | Camera unit + manual C |
| Very old / slow device | Adaptive scheduler, Düşük load profile | Benchmark |
| Two similar vehicles close together | Two observations | Tracker/dedup unit + manual H |
| Foreign plates | Not forced to TR format; NEEDS_REVIEW | Parser unit |
| Special/official/diplomatic/military plates | NEEDS_REVIEW with raw text | Parser unit |
| Camera taken by another app / call | Interrupted state, auto-recover, notification | Lifecycle + manual L |
| Permission revoked while running | Stop safely, explain | Lifecycle |
| Reboot | No silent auto-start; "resume" flow | Manual L |
| Overheating | Degrade → pause with warning | Thermal unit + manual I |

## 3. Manuel testler (Türkçe)

Aşağıdaki testler gerçek telefonda yapılır (Faz 9). Her testin sonucunu şablonla bildir.

### Test A — Kurulum
**Amaç:** APK'nın telefona yüklenip açıldığını doğrulamak.
**Gerekenler:** Telefon, APK dosyası (`PlakaGozcusu-0.1.0-release.apk`).
**Hazırlık:** docs/DEVICE_SETUP.md §1.
**Adım adım yapılacaklar:**
1. APK'yı yükle.
2. Uygulamayı aç.
3. Tanıtım ekranının Türkçe göründüğünü kontrol et.
**Beklenen sonuç:** Uygulama açılır, 4 adımlı tanıtım görünür.
**Başarısız sayılır eğer…** Kurulum hatası, açılışta kapanma, İngilizce metin.
**Toplanacak ekran görüntüsü/log:** Ana ekran ekran görüntüsü.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test A sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test B — İzinler
**Amaç:** Kamera ve bildirim izni senaryolarını denemek.
**Gerekenler:** Telefon.
**Hazırlık:** Uygulamayı yeni kur.
**Adım adım yapılacaklar:**
1. Kalibrasyonda kamera iznini **reddet** → açıklama + "Kamera iznini ver" görünmeli.
2. Tekrar reddet → "Ayarları aç" görünmeli.
3. Ayarlardan izni ver, geri dön → önizleme gelmeli.
4. "İzlemeyi başlat"ta bildirim iznini reddet → izleme yine başlamalı.
**Beklenen sonuç:** Her durumda doğru Türkçe açıklama; çökme yok.
**Başarısız sayılır eğer…** Çökme, boş ekran, yanlış metin.
**Toplanacak ekran görüntüsü/log:** Her adımın ekran görüntüsü.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test B sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test C — Kamera önizleme ve kalibrasyon
**Amaç:** Yakınlaştırma, yol alanı, odak kilidi ve test fotoğrafını denemek.
**Gerekenler:** Sabitlenmiş telefon.
**Hazırlık:** docs/DEVICE_SETUP.md §2–3.
**Adım adım yapılacaklar:**
1. Yakınlaştırmayı değiştir.
2. Yol alanına 4 nokta koy.
3. Odak modunda yola dokun, "Odağı kilitle".
4. "Test fotoğrafı çek".
5. "Bitti" → Ayarlar → "Kalibrasyonu yeniden yap" ile değerlerin korunduğunu kontrol et.
**Beklenen sonuç:** Önizleme düz (yan değil), noktalar dokunduğun yerde, test fotoğrafı çözünürlüğü görünür.
**Başarısız sayılır eğer…** Dönük görüntü, noktalar kayık, fotoğraf çekilemiyor.
**Toplanacak ekran görüntüsü/log:** Kalibrasyon ekranı görüntüsü, kamera bilgi satırı.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test C sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test D — Görüntü uygunluğu (GO/NO-GO)
**Amaç:** Plakaların bu telefon ve mesafede okunabilir piksel genişliğinde olup olmadığını ölçmek.
**Gerekenler:** Kendi veya izin veren birinin aracı, mezura/adım ile mesafe tahmini.
**Hazırlık:** Aracı yolun izlenen bölümüne park et.
**Adım adım yapılacaklar:**
1. Gündüz test fotoğrafı çek (kalibrasyon ekranı).
2. Fotoğrafı bilgisayarda aç, plakanın genişliğini piksel olarak ölç (Paint: seçim kutusunun boyutu).
3. Aynısını akşamüstü ve gece tekrarla.
**Beklenen sonuç:** Ölçülen plaka genişlikleri not edilir (piksel). Kabaca 100+ px gündüz iyi bir işarettir; kesin eşik bu testle belirlenecek.
**Başarısız sayılır eğer…** Plaka 60 px altında veya harfler gözle okunmuyor → NO-GO; daha yakın montaj / yakınlaştırma / farklı telefon gerekir.
**Toplanacak ekran görüntüsü/log:** Üç fotoğraf (paylaşmadan önce plakayı karart) ve ölçülen piksel değerleri.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test D sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test E — Araç tespiti ve hareket
**Amaç:** Hareketli araçların kaydedildiğini, park halindekilerin kaydedilmediğini görmek.
**Gerekenler:** Yoldan geçen araçlar, kadrajda park etmiş bir araç.
**Hazırlık:** İzlemeyi başlat, ana ekranda canlı görüntüyü aç.
**Adım adım yapılacaklar:**
1. 10 dakika izle.
2. Geçen araç sayısını elle say.
3. Park halindeki aracın kayıt üretmediğini kontrol et.
**Beklenen sonuç:** Geçen araçların çoğu kayıt olur; park eden araç kayıt üretmez.
**Başarısız sayılır eğer…** Park eden araç için sürekli kayıt veya geçen araçların çoğunun kaçması.
**Toplanacak ekran görüntüsü/log:** Elle sayım vs. uygulama sayısı.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test E sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test F — Plaka okuma
**Amaç:** Okuma doğruluğunu bilinen plakalarla ölçmek.
**Gerekenler:** Kendi/izinli araçlar, plakalarını bildiğin.
**Hazırlık:** İzleme açık.
**Adım adım yapılacaklar:**
1. Aracı izlenen yoldan 5 kez geçir (gündüz).
2. Her kayıt için: doğru / kısmen / okunamadı / yanlış yaz.
3. Gece tekrarla.
**Beklenen sonuç:** Sonuçlar dürüstçe kaydedilir; yanlış okumada durum "Yüksek güven" olmamalı (olursa yaz).
**Başarısız sayılır eğer…** Kayıt yok, ya da yanlış plaka "Yüksek güven" ile.
**Toplanacak ekran görüntüsü/log:** Tablo: geçiş no, gerçek plaka (kendine sakla), uygulama sonucu, durum.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test F sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test G — En iyi kare seçimi
**Amaç:** Kaydedilen sahne/kırpıntının geçişin en iyi anı olduğunu görmek.
**Gerekenler:** Test F kayıtları.
**Hazırlık:** —
**Adım adım yapılacaklar:**
1. Kayıt detayında sahne, araç ve plaka kırpıntısına bak.
2. Plaka kırpıntısı gerçekten plakayı mı gösteriyor?
**Beklenen sonuç:** Kırpıntılar ilgili aracı/plakayı gösterir, net olan kare seçilmiştir.
**Başarısız sayılır eğer…** Boş/yanlış bölge, sürekli bulanık kare.
**Toplanacak ekran görüntüsü/log:** Detay ekran görüntüsü (plakayı karart).
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test G sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test H — Tekrar kaydı engelleme
**Amaç:** Bir geçişin tek kayıt olduğunu, iki aracın ayrı kaldığını görmek.
**Gerekenler:** İki araç (biri izinli).
**Hazırlık:** İzleme açık.
**Adım adım yapılacaklar:**
1. Tek araçla bir geçiş yap → 1 kayıt.
2. İki aracı arka arkaya geçir → 2 kayıt.
3. Aynı aracı 1 dk içinde tekrar geçir → 2. kayıtta "bağlantılı kayıt" notu görülebilir.
**Beklenen sonuç:** Geçiş başına bir kayıt; araçlar karışmaz; hiçbir kayıt silinmez.
**Başarısız sayılır eğer…** Bir geçiş için birden çok kayıt, iki aracın tek kayıtta birleşmesi.
**Toplanacak ekran görüntüsü/log:** Kayıtlar listesinin görüntüsü.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test H sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test I — Uzun süreli çalışma
**Amaç:** 2+ saat kesintisiz çalışmayı, ısınmayı ve depolama artışını ölçmek.
**Gerekenler:** Şarj aleti.
**Hazırlık:** Telefon şarjda, izleme açık.
**Adım adım yapılacaklar:**
1. Ekranı karart, sonra ekranı kilitle.
2. 2 saat bekle.
3. Bildirimin hâlâ "İzleniyor" olduğunu, kayıtların geldiğini, telefonun sıcaklığını ve Ana ekrandaki kaplanan alanı not et.
**Beklenen sonuç:** İzleme kesilmez; aşırı ısınmada uyarı verip yavaşlar/duraklar.
**Başarısız sayılır eğer…** Uygulama kapanır, bildirim kaybolur, ekran kapalıyken hiç kayıt yok.
**Toplanacak ekran görüntüsü/log:** Başlangıç/bitiş saatleri, alan değerleri, varsa `adb logcat -d -s PlakaGozcusu:*` çıktısı.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test I sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test J — Olay Bul ve kanıt dışa aktarma
**Amaç:** Bilinen bir geçişi zamanla bulup doğrulanabilir paket çıkarmak.
**Gerekenler:** Test F'den saatini bildiğin bir geçiş.
**Hazırlık:** —
**Adım adım yapılacaklar:**
1. Olay Bul → tarih ve 10 dk'lık aralık seç.
2. Kaydı bul → "Tümünü koru".
3. "Kanıt paketi oluştur" → konumu seç.
4. ZIP'i bilgisayara al, `rapor.html`'i aç, bir fotoğrafın SHA-256'sını `Get-FileHash` ile karşılaştır.
**Beklenen sonuç:** Kayıt bulunur, ZIP açılır, özetler eşleşir.
**Başarısız sayılır eğer…** Kayıt bulunamaz, ZIP bozuk, özet eşleşmez.
**Toplanacak ekran görüntüsü/log:** ZIP içerik listesi, hash karşılaştırması.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test J sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test K — Depolama ve saklama süresi
**Amaç:** Koruma ve silmenin çalıştığını görmek.
**Gerekenler:** Birkaç kayıt.
**Hazırlık:** —
**Adım adım yapılacaklar:**
1. Bir kaydı koru, başka bir kaydı sil.
2. Silinen kaydın listeden kalktığını kontrol et.
3. Ayarlar → saklama süresini değiştir; korunan kaydın kaldığını (ertesi gün) kontrol et.
**Beklenen sonuç:** Silme fotoğraflarla birlikte; korunan kayıt kalır.
**Başarısız sayılır eğer…** Silinen kayıt geri gelir; korunan kayıt silinir.
**Toplanacak ekran görüntüsü/log:** Önce/sonra listeleri.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test K sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.

### Test L — Hata kurtarma
**Amaç:** Kamera çakışması, yeniden başlatma ve yer değiştirmeyi denemek.
**Gerekenler:** Başka bir kamera uygulaması.
**Hazırlık:** İzleme açık.
**Adım adım yapılacaklar:**
1. Kamera uygulamasını aç-kapat → izleme kendiliğinden devam etmeli.
2. Telefonu hafifçe oynat → "yerinden oynamış olabilir" uyarısı.
3. Telefonu yeniden başlat → uygulama kendiliğinden kamerayı açmamalı; açıp "İzlemeyi başlat" ile sürmeli.
**Beklenen sonuç:** Uygun Türkçe uyarılar, çökme yok.
**Başarısız sayılır eğer…** Çökme, kendiliğinden kamera açılması, takılı kalma.
**Toplanacak ekran görüntüsü/log:** Ekran görüntüleri + log.
**Sonucu Claude Code'a nasıl bildireceksin:**
```
Test L sonucu: BAŞARILI / BAŞARISIZ / KISMEN
Telefon modeli ve Android sürümü:
Ortam (gündüz/akşam/gece, hava):
Gözlem:
Ekler:
```
**Gizlilik/güvenlik notu:** Önce kendi veya izin veren kişilerin araçlarıyla test et; paylaşacağın görüntülerde başkalarına ait plakaları ve yüzleri karart. Telefonu düşmeyecek şekilde sabitle.
