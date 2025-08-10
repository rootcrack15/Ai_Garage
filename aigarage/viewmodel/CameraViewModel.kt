package com.rootcrack.aigarage.viewmodels // KENDİ PAKET ADINIZ (dosyadakiyle aynı)

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Job

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("AdPrefs", Context.MODE_PRIVATE)

    // Bu StateFlow, CameraScreen tarafından reklamın gösterilip gösterilmeyeceğini
    // anlık olarak dinlemek için KULLANILMAYACAK. Bunun yerine shouldShowAd() kullanılacak.
    // Ancak, bir reklamın gösterilme *zamanı geldiğinde* bunu tetiklemek için bir mekanizma olabilir.
    // Şimdilik CameraScreen'deki mantığa uyum sağlamak için bunu yorum satırına alabiliriz
    // veya daha basit bir adlandırma kullanabiliriz.
    // private val _triggerShowAd = MutableStateFlow(false)
    // val triggerShowAd: StateFlow<Boolean> = _triggerShowAd.asStateFlow()

    private var adDisplayDelayJob: Job? = null // Gecikmeli reklam için iş

    companion object {
        const val PHOTOS_TAKEN_COUNT_KEY = "photos_taken_count_for_ad"
        const val AD_INTERVAL = 2 // Her 2 başarılı kayıttan sonra reklam
        const val AD_DISPLAY_DELAY_MS = 3000L // Reklamı göstermeden önceki gecikme (isteğe bağlı)
    }

    private fun getPhotosTakenCountSinceLastAd(): Int {
        return sharedPreferences.getInt(PHOTOS_TAKEN_COUNT_KEY, 0)
    }

    private fun incrementPhotosTakenCountAndPersist() {
        val currentCount = getPhotosTakenCountSinceLastAd()
        sharedPreferences.edit {
            putInt(PHOTOS_TAKEN_COUNT_KEY, currentCount + 1)
        }
        Log.d("CameraViewModel", "Photos taken count since last ad updated to: ${currentCount + 1}")
    }

    /**
     * CameraScreen tarafından çağrılır.
     * Bir fotoğraf başarıyla kaydedildikten sonra reklam gösterilip gösterilmeyeceğini kontrol eder.
     * Sayaç artırılır ve AD_INTERVAL'e ulaşıldıysa true döner.
     */
    fun incrementPhotoCounterAndCheckAd(): Boolean {
        incrementPhotosTakenCountAndPersist()
        val currentTotalPhotos = getPhotosTakenCountSinceLastAd()
        Log.d("CameraViewModel", "Photo saved. Total photos since last ad for interval: $currentTotalPhotos")

        val shouldDisplay = currentTotalPhotos >= AD_INTERVAL
        if (shouldDisplay) {
            Log.d("CameraViewModel", "AD_INTERVAL ($AD_INTERVAL) reached. Ad should be shown.")
        }
        return shouldDisplay
        // Gecikmeli reklam gösterme mantığı CameraScreen'e taşındı (loadInterstitialAd ile)
        // Bu yüzden buradaki delay ve _triggerShowAd kaldırıldı.
    }

    /**
     * CameraScreen tarafından çağrılır.
     * Mevcut durumda reklam gösterilip gösterilmeyeceğini SADECE kontrol eder, sayacı artırmaz.
     * incrementPhotoCounterAndCheckAd() bu işlevi zaten üstlendiği için
     * bu fonksiyonun şu anki CameraScreen mantığında direkt bir kullanımı olmayabilir
     * ama gelecekte farklı bir kontrol için tutulabilir.
     * Ya da direkt incrementPhotoCounterAndCheckAd() sonucu kullanılabilir.
     * CameraScreen'deki if (cameraViewModel.shouldShowAd()) yerine
     * if (cameraViewModel.incrementPhotoCounterAndCheckAd()) daha mantıklı olabilir
     * eğer her kayıtta hem sayacı artırıp hem de kontrol etmek istiyorsak.
     * Şimdiki CameraScreen.kt'deki kullanımına göre düzenleyelim:
     * Fotoğraf kaydedildikten sonra önce incrementPhotoCounterAndCheckAd() çağrılıyor.
     * Sonra if(shouldShowAd()) ile tekrar kontrol ediliyor. Bu biraz fazla.
     * Bu fonksiyonu, sadece "bir sonraki fotoğraf çekiminde reklam gösterilecek mi?"
     * bilgisini vermek için kullanalım, sayacı değiştirmesin.
     */
    fun shouldShowAd(): Boolean {
        // Bu fonksiyonun CameraScreen'deki çağrıldığı yer, fotoğrafın ZATEN kaydedildiği
        // ve incrementPhotoCounterAndCheckAd'in ZATEN çağrıldığı yerdir.
        // Dolayısıyla, incrementPhotoCounterAndCheckAd ZATEN sayacı güncelledi
        // ve reklam gösterilip gösterilmeyeceğine karar verdi.
        // Bu yüzden bu fonksiyon ya kaldırılabilir ya da incrementPhotoCounterAndCheckAd'in sonucunu
        // bir state'e alıp o state'i döndürebilir.
        // Mevcut CameraScreen.kt'deki kullanımda, incrementPhotoCounterAndCheckAd'in
        // reklam gösterilmesi gerektiğini döndürdüğü durumda bu fonksiyonun da true dönmesi beklenir.
        // En basit haliyle, son sayaca göre karar verir.
        val photosTaken = getPhotosTakenCountSinceLastAd()
        val shouldShow = photosTaken >= AD_INTERVAL
        Log.d("CameraViewModel", "shouldShowAd() called. Photos since last ad: $photosTaken, AD_INTERVAL: $AD_INTERVAL. Should show: $shouldShow")
        return shouldShow
    }


    /**
     * CameraScreen tarafından reklam başarıyla gösterildikten sonra çağrılır.
     * Reklam sayacını sıfırlar.
     */
    fun resetAdCounter() {
        sharedPreferences.edit {
            putInt(PHOTOS_TAKEN_COUNT_KEY, 0)
        }
        Log.d("CameraViewModel", "Ad counter reset to 0.")
    }

    // Bu fonksiyonlar önceki yapıda vardı, şimdiki CameraScreen mantığında
    // direkt kullanılmıyor ama referans olarak veya farklı bir akış için durabilir.
    /*
    // Bu fonksiyon fotoğraf başarıyla kaydedildiğinde çağrılacak
    fun onPhotoSuccessfullySaved() { // Bu artık incrementPhotoCounterAndCheckAd tarafından yönetiliyor
        incrementPhotosTakenCountAndPersist()

        val currentTotalPhotos = getPhotosTakenCountSinceLastAd()
        Log.d("CameraViewModel", "Photo saved. Total photos for ad interval: $currentTotalPhotos")

        if (currentTotalPhotos > 0 && currentTotalPhotos % AD_INTERVAL == 0) {
            adDisplayDelayJob?.cancel() // Önceki bir gecikme varsa iptal et
            adDisplayDelayJob = CoroutineScope(Dispatchers.Main).launch {
                Log.d("CameraViewModel", "Ad will be shown in ${AD_DISPLAY_DELAY_MS / 1000} seconds...")
                delay(AD_DISPLAY_DELAY_MS) // X saniye bekle
                if (this.isActive) { // Coroutine hala aktifse devam et
                    Log.d("CameraViewModel", "Triggering ad display now.")
                    // _triggerShowAd.value = true // CameraScreen bunu dinlemiyor
                }
            }
        }
    }

    fun adShownOrDismissed() { // Bu artık resetAdCounter tarafından yönetiliyor
        // _triggerShowAd.value = false // Reklam gösterildikten veya kapatıldıktan sonra state'i sıfırla
        resetAdCounter() // Sayacı sıfırla
        Log.d("CameraViewModel", "Ad trigger reset and counter reset.")
    }
    */

    override fun onCleared() {
        super.onCleared()
        adDisplayDelayJob?.cancel()
        Log.d("CameraViewModel", "ViewModel cleared, ad display job cancelled.")
    }
}

