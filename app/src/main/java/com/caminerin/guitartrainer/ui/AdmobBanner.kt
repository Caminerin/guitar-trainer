package com.caminerin.guitartrainer.ui

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner AdMob reutilizable.
 *
 * Usa el ID de unidad de anuncio de TEST oficial de Google. Al publicar de verdad,
 * sustituir [TEST_BANNER_AD_UNIT_ID] por el ID real de tu cuenta AdMob
 * (formato `ca-app-pub-XXXXXXXX/YYYYYYYY`) y el App ID del AndroidManifest.
 */
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

private fun adaptiveAdSize(context: Context): AdSize {
    val activity = context as? Activity ?: return AdSize.BANNER
    val metrics: DisplayMetrics = activity.resources.displayMetrics
    val density = metrics.density
    var widthPx = metrics.widthPixels.toFloat()
    val adWidthDp = (widthPx / density).toInt().coerceAtLeast(320)
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
}

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adaptiveAdSize(ctx))
                adUnitId = TEST_BANNER_AD_UNIT_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
