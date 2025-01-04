package app.revanced.patches.music.misc.album

import app.revanced.util.fingerprint.legacyFingerprint
import app.revanced.util.or
import com.android.tools.smali.dexlib2.AccessFlags


internal val snackBarParentFingerprint = legacyFingerprint(
    name = "snackBarParentFingerprint",
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf("No suitable parent found from the given view. Please provide a valid view.")
)
