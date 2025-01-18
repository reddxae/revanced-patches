package app.revanced.patches.youtube.general.channel

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.youtube.utils.compatibility.Constants.COMPATIBLE_PACKAGE
import app.revanced.patches.youtube.utils.extension.Constants.GENERAL_PATH
import app.revanced.patches.youtube.utils.patch.PatchList.OPEN_CHANNEL_OF_LIVE_AVATAR
import app.revanced.patches.youtube.utils.resourceid.sharedResourceIdPatch
import app.revanced.patches.youtube.utils.settings.ResourceUtils.addPreference
import app.revanced.patches.youtube.utils.settings.settingsPatch
import app.revanced.patches.youtube.video.playbackstart.playbackStartDescriptorPatch
import app.revanced.patches.youtube.video.playbackstart.playbackStartVideoIdReference
import app.revanced.util.fingerprint.methodOrThrow
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "$GENERAL_PATH/OpenChannelOfLiveAvatarPatch;"

@Suppress("unused")
val layoutSwitchPatch = bytecodePatch(
    OPEN_CHANNEL_OF_LIVE_AVATAR.title,
    OPEN_CHANNEL_OF_LIVE_AVATAR.summary,
) {
    compatibleWith(COMPATIBLE_PACKAGE)

    dependsOn(
        playbackStartDescriptorPatch,
        sharedResourceIdPatch,
        settingsPatch,
    )

    execute {

        elementsImageFingerprint.methodOrThrow().addInstruction(
            0,
            "invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->liveChannelAvatarClicked()V"
        )

        clientSettingEndpointFingerprint.methodOrThrow().apply {
            val eqzIndex = indexOfFirstInstructionReversedOrThrow(Opcode.IF_EQZ)
            var freeIndex = indexOfFirstInstructionReversedOrThrow(eqzIndex, Opcode.NEW_INSTANCE)
            var freeRegister = getInstruction<OneRegisterInstruction>(freeIndex).registerA

            addInstructionsWithLabels(
                eqzIndex, """
                    invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->openChannelOfLiveAvatar()Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :ignore
                    return-void
                    :ignore
                    nop
                    """
            )

            val playbackStartIndex = indexOfPlaybackStartDescriptorInstruction(this) + 1
            val playbackStartRegister = getInstruction<OneRegisterInstruction>(playbackStartIndex).registerA

            freeIndex = indexOfFirstInstructionOrThrow(playbackStartIndex, Opcode.CONST_STRING)
            freeRegister = getInstruction<OneRegisterInstruction>(freeIndex).registerA

            addInstructions(
                playbackStartIndex + 1, """
                    invoke-virtual { v$playbackStartRegister }, $playbackStartVideoIdReference
                    move-result-object v$freeRegister
                    invoke-static { v$freeRegister }, $EXTENSION_CLASS_DESCRIPTOR->openChannelOfLiveAvatar(Ljava/lang/String;)V
                    """
            )
        }

        // region add settings

        addPreference(
            arrayOf(
                "PREFERENCE_SCREEN: GENERAL",
                "PREFERENCE_CATEGORY: GENERAL_EXPERIMENTAL_FLAGS",
                "SETTINGS: OPEN_CHANNEL_OF_LIVE_AVATAR"
            ),
            OPEN_CHANNEL_OF_LIVE_AVATAR
        )

        // endregion

    }
}
