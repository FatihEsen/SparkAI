<div align="center">
  <h1>🚗 SparkAI</h1>
  <p><strong>Yapay Zeka Destekli OBD-II Araç Arıza Tespit Sistemi</strong></p>
  <p>AI-powered vehicle diagnostic system for real-time OBD-II fault detection</p>
</div>

---

## 📋 Proje Hakkında

**SparkAI**, araçlardaki motor ve sistem arızalarını yapay zeka kullanarak otomatik olarak tespit eden, tanılayan ve analiz eden modern bir Android uygulamasıdır. OBD-II (On-Board Diagnostics) protokolünü kullanarak araç sensörlerinden veri okur ve AI modelleri ile arıza türünü belirler.

### ✨ Özellikler

- 🤖 **Yapay Zeka Destekli Tanı**: Google Gemini AI kullanarak akıllı arıza analizi
- 📡 **OBD-II Entegrasyonu**: Araç sensörlerinden gerçek zamanlı veri okuma
- ⚡ **Hızlı ve Doğru**: Arızaları anında tespit ve kategorize etme
- 📱 **Kullanıcı Dostu Arayüz**: Kolay anlaşılır ve sezgisel tasarım
- 🔍 **Detaylı Raporlar**: Arıza nedenleri ve çözüm önerileri
- 🛠️ **Bakım Tavsiyesi**: Preventif bakım önerileri sunma

---

## 🛠️ Teknoloji Stack

- **Dil**: Kotlin
- **Platform**: Android
- **AI/ML**: Google Gemini API
- **OBD-II**: OBD-II araç diagnostik protokolü
- **IDE**: Android Studio

---

## 📦 Başlangıç

### Ön Koşullar

- [Android Studio](https://developer.android.com/studio) (en son sürüm)
- Java Development Kit (JDK) 11+
- Android SDK 28+
- Google Gemini API Key

### Kurulum

1. **Projeyi klonlayın**
   ```bash
   git clone https://github.com/FatihEsen/SparkAI.git
   cd SparkAI
   ```

2. **Android Studio'da açın**
   - Android Studio'yu başlatın
   - **File** → **Open** seçin
   - Proje klasörünü seçin

3. **Gradle bağımlılıklarını senkronize edin**
   - Android Studio otomatik olarak bağımlılıkları indirip konfigüre edecektir

4. **.env dosyası oluşturun**
   ```bash
   # Proje kök dizininde .env dosyası oluşturun
   GEMINI_API_KEY=your_api_key_here
   ```
   - `.env.example` dosyasını referans alabilirsiniz

5. **Signing konfigürasyonunu düzenleyin** (varsa)
   - `app/build.gradle.kts` dosyasından debug signing config satırını silin:
   ```kotlin
   // signingConfig = signingConfigs.getByName("debugConfig")
   ```

6. **Uygulamayı çalıştırın**
   - Emülatör veya fiziksel cihaz bağlayın
   - **Run** → **Run 'app'** seçin veya `Shift + F10` tuşlayın

---

## 🚀 Kullanım

1. Uygulamayı açın
2. Araçınıza OBD-II bağlantı kurun (Bluetooth)
3. Arıza kodlarını okutun
4. AI analizi sonucunu görüntüleyin
5. Detaylı çözüm önerilerini inceleyin

---

## 📁 Proje Yapısı

```
SparkAI/
├── app/                          # Ana uygulama modülü
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/            # Kotlin kaynak kodu
│   │   │   ├── res/             # Kaynaklar (layout, drawable, vb.)
│   │   │   └── AndroidManifest.xml
│   │   └── test/                # Test kodları
│   └── build.gradle.kts         # Gradle yapılandırması
├── .env.example                 # Ortam değişkenleri örneği
└── README.md                    # Bu dosya
```

---

## 🔑 API Yapılandırması

### Google Gemini API

1. [Google AI Studio](https://ai.google.dev/) ziyaret edin
2. Yeni API key oluşturun
3. `.env` dosyasına ekleyin:
   ```
   GEMINI_API_KEY=your_generated_key
   ```

---

## 📝 Gereklilikler

- **Minimum Android versiyonu**: Android 8.0 (API 28)
- **Hedef Android versiyonu**: Android 14+ (API 34+)
- **RAM**: En az 2GB (önerilir: 4GB+)
- **İnternet bağlantısı**: AI analizi için gereklidir

---

## 🤝 Katkıda Bulunma

Projeye katkıda bulunmak isterseniz:

1. Projeyi fork edin
2. Feature branch'i oluşturun (`git checkout -b feature/AmazingFeature`)
3. Değişiklikleri commit edin (`git commit -m 'Add some AmazingFeature'`)
4. Branch'ı push edin (`git push origin feature/AmazingFeature`)
5. Pull Request açın

---

## 📄 Lisans

Bu proje MIT Lisansı altında lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasını görün.

---

## 👨‍💻 Geliştirici

**Fatih Esen** - [@FatihEsen](https://github.com/FatihEsen)

---

## 📞 İletişim & Destek

- GitHub Issues: [Sorun bildirin](https://github.com/FatihEsen/SparkAI/issues)
- Email: fatih@example.com

---

## 🙏 Teşekkürler

- [Google Gemini API](https://ai.google.dev/)
- [Android Developers](https://developer.android.com/)
- [Kotlin Community](https://kotlinlang.org/)

---

<div align="center">
  <p>⭐ Projeyi beğendiyseniz star vermeyi unutmayın!</p>
  <p>Made with ❤️ by Fatih Esen</p>
</div>
