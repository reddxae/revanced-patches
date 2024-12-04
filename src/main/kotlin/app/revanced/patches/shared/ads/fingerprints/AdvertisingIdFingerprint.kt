package app.revanced.patches.shared.ads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.util.MethodUtil

internal object AdvertisingIdFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf("a."),
    customFingerprint = { methodDef, classDef ->
        MethodUtil.isConstructor(methodDef) &&
                classDef.fields.find { it.type == "Lcom/google/android/libraries/youtube/innertube/model/ads/InstreamAd;" } != null
    }
)