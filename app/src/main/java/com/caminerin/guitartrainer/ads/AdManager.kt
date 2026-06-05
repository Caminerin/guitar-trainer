package com.caminerin.guitartrainer.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Gestiona todos los anuncios de la app. IDs de TEST de Google.
 *
 * Al publicar, sustituir las 4 constantes *_AD_ID por tus IDs reales de AdMob.
 */
object AdManager {

    // ----- IDs de TEST oficiales de Google -----
    private const val APP_OPEN_AD_ID   = "ca-app-pub-3940256099942544/9257395921"
    const val BANNER_AD_ID             = "ca-app-pub-3940256099942544/6300978111"
    private const val INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val REWARDED_AD_ID   = "ca-app-pub-3940256099942544/5224354917"

    // ----- SharedPreferences -----
    private const val PREFS = "guitar_prefs"
    private const val KEY_AD_FREE_UNTIL = "ad_free_until"
    private const val KEY_REWARD_COUNT  = "ad_reward_count"

    // ----- Cadencia -----
    private const val EXERCISES_PER_INTERSTITIAL = 3
    private const val MINUTES_PER_INTERSTITIAL = 5L
    private const val REWARDS_FOR_FREE = 3
    private const val FREE_HOURS = 24L

    // ----- Estado -----
    private var adFreeUntil = 0L
    var rewardCount = 0; private set
    private var exerciseCount = 0
    private var lastInterstitialTime = 0L
    private var appOpenShown = false
    private var appStartTime = 0L
    // Si el App Open aún no estaba cargado al abrir, lo mostramos en cuanto cargue
    // (dentro de una ventana corta de arranque).
    private var pendingAppOpenActivity: Activity? = null
    private const val APP_OPEN_WINDOW_MS = 12_000L

    // ----- Ads cargados -----
    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // ----- Listener para que la UI reaccione -----
    var onAdFreeChanged: (() -> Unit)? = null

    // ===== Inicialización =====

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        adFreeUntil = prefs.getLong(KEY_AD_FREE_UNTIL, 0L)
        rewardCount = prefs.getInt(KEY_REWARD_COUNT, 0)
        lastInterstitialTime = System.currentTimeMillis()
        exerciseCount = 0
        appOpenShown = false
        appStartTime = System.currentTimeMillis()
        loadAppOpen(context)
        loadInterstitial(context)
        loadRewarded(context)
    }

    fun isAdFree(): Boolean = System.currentTimeMillis() < adFreeUntil

    fun adFreeRemainingMs(): Long =
        (adFreeUntil - System.currentTimeMillis()).coerceAtLeast(0)

    // ===== App Open =====

    fun showAppOpenIfReady(activity: Activity) {
        if (isAdFree() || appOpenShown) return
        val ad = appOpenAd
        if (ad != null) {
            appOpenShown = true
            pendingAppOpenActivity = null
            ad.fullScreenContentCallback = dismissCallback { loadAppOpen(activity) }
            ad.show(activity)
        } else {
            // Aún no ha cargado: lo mostraremos en cuanto cargue (ventana de arranque).
            pendingAppOpenActivity = activity
        }
    }

    private fun loadAppOpen(context: Context) {
        AppOpenAd.load(
            context, APP_OPEN_AD_ID, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    // Si estábamos esperando para mostrarlo al abrir, hazlo ya.
                    val act = pendingAppOpenActivity
                    val withinWindow =
                        System.currentTimeMillis() - appStartTime < APP_OPEN_WINDOW_MS
                    if (act != null && !appOpenShown && !isAdFree() && withinWindow) {
                        showAppOpenIfReady(act)
                    }
                }
                override fun onAdFailedToLoad(err: LoadAdError) { appOpenAd = null }
            }
        )
    }

    // ===== Intersticial =====

    fun onExerciseCompleted(activity: Activity) {
        if (isAdFree()) return
        exerciseCount++
        val minutesPassed = (System.currentTimeMillis() - lastInterstitialTime) / 60_000
        if (exerciseCount >= EXERCISES_PER_INTERSTITIAL ||
            minutesPassed >= MINUTES_PER_INTERSTITIAL
        ) {
            showInterstitial(activity)
        }
    }

    private fun showInterstitial(activity: Activity) {
        val ad = interstitialAd ?: return
        exerciseCount = 0
        lastInterstitialTime = System.currentTimeMillis()
        ad.fullScreenContentCallback = dismissCallback { loadInterstitial(activity) }
        ad.show(activity)
    }

    private fun loadInterstitial(context: Context) {
        InterstitialAd.load(
            context, INTERSTITIAL_AD_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { interstitialAd = null }
            }
        )
    }

    // ===== Recompensado =====

    fun isRewardedReady(): Boolean = rewardedAd != null

    fun showRewarded(activity: Activity, onDone: () -> Unit) {
        val ad = rewardedAd ?: return
        ad.fullScreenContentCallback = dismissCallback { loadRewarded(activity) }
        ad.show(activity) { _ ->
            rewardCount++
            if (rewardCount >= REWARDS_FOR_FREE) {
                adFreeUntil = System.currentTimeMillis() + FREE_HOURS * 3_600_000
                rewardCount = 0
                onAdFreeChanged?.invoke()
            }
            savePrefs(activity)
            onDone()
        }
    }

    private fun loadRewarded(context: Context) {
        RewardedAd.load(
            context, REWARDED_AD_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { rewardedAd = null }
            }
        )
    }

    // ===== Utilidades =====

    private fun savePrefs(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_AD_FREE_UNTIL, adFreeUntil)
            .putInt(KEY_REWARD_COUNT, rewardCount)
            .apply()
    }

    private fun dismissCallback(reload: () -> Unit) =
        object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { reload() }
            override fun onAdFailedToShowFullScreenContent(err: AdError) { reload() }
        }
}
