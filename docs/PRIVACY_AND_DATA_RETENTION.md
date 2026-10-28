# Gizlilik ve Veri Saklama

- **Amaç:** Yalnızca ev güvenliği. Tüm işleme telefonda yapılır; internet, bulut, analiz veya kimlik sorgulaması yoktur.
- **Kaydedilenler:** sahne fotoğrafı, araç ve plaka kırpıntısı, otomatik okunan plaka metni (yanlış olabilir), zaman bilgileri, güven puanları, SHA-256 özetleri. Konum kaydedilmez.
- **Saklama:** Varsayılan süre ve depolama sınırı ayarlardan değiştirilebilir. 'Koru' işaretli kayıtlar otomatik silinmez.
- **Silme:** Kayıtlar tek tek veya toplu silinebilir; silme dosyaları da kaldırır.
- **Sorumluluk:** Kamerayı öncelikle kendi mülkünün önüne ve bitişik yola yönelt; komşuların özel alanlarını kaydetme. Bu belge KVKK konusunda bilgilendirme amaçlıdır, hukuki uygunluk garantisi vermez.

## İsteğe bağlı yapay zekâ doğrulaması (0.3.0+)
- **Varsayılan olarak kapalıdır.** Açarsan, emin olunamayan kayıtların yalnızca plaka (yoksa araç) kırpıntısı ve
  uygulamanın okuduğu aday metinler Google Gemini'ye gönderilir. Başka hiçbir veri (sahne fotoğrafı, konum, zaman) gönderilmez.
- Ücretsiz Gemini kullanımında Google gönderilen içeriği ürünlerini geliştirmek için kullanabilir.
- Başkalarına ait plakaları üçüncü tarafa göndermenin KVKK sorumluluğu kullanıcıdadır. Kapalı tutulduğunda uygulama hiçbir veriyi cihazdan çıkarmaz.
- API anahtarı Android Keystore ile şifreli saklanır; loglara ve dışa aktarımlara girmez. Ayarlar → "Anahtarı sil" ile kaldırılır.
