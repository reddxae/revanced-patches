package app.revanced.patches.shared.spoof.blockrequest

import app.revanced.util.fingerprint.legacyFingerprint
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal val buildInitPlaybackRequestFingerprint = legacyFingerprint(
    name = "buildInitPlaybackRequestFingerprint",
    returnType = "Lorg/chromium/net/UrlRequest\$Builder;",
    opcodes = listOf(
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IGET_OBJECT, // Moves the request URI string to a register to build the request with.
    ),
    strings = listOf(
        "Content-Type",
        "Range",
    ),
    customFingerprint = { method, _ ->
        indexOfUriToStringInstruction(method) >= 0
    },
)

internal val buildPlayerRequestURIFingerprint = legacyFingerprint(
    name = "buildPlayerRequestURIFingerprint",
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "key",
        "asig",
    ),
    customFingerprint = { method, _ ->
        indexOfUriToStringInstruction(method) >= 0
    },
)

internal fun indexOfUriToStringInstruction(method: Method) =
    method.indexOfFirstInstruction {
        opcode == Opcode.INVOKE_VIRTUAL &&
                getReference<MethodReference>().toString() == "Landroid/net/Uri;->toString()Ljava/lang/String;"
    }

// Feature flag that turns on Platypus programming language code compiled to native C++.
// This code appears to replace the player config after the streams are loaded.
// Flag is present in YouTube 19.34, but is missing Platypus stream replacement code until 19.43.
// Flag and Platypus code is also present in newer versions of YouTube Music.
internal const val ONESIE_ENCRYPTION_FEATURE_FLAG = 45645570L

internal val onesieEncryptionFeatureFlagFingerprint = legacyFingerprint(
    name = "onesieEncryptionFeatureFlagFingerprint",
    literals = listOf(ONESIE_ENCRYPTION_FEATURE_FLAG),
)
