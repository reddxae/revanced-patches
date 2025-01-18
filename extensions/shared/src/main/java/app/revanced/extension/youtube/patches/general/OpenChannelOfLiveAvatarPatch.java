package app.revanced.extension.youtube.patches.general;

import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.youtube.patches.general.requests.VideoDetailsRequest;
import app.revanced.extension.youtube.settings.Settings;
import app.revanced.extension.youtube.utils.VideoUtils;

@SuppressWarnings("unused")
public final class OpenChannelOfLiveAvatarPatch {
    private static final boolean OPEN_CHANNEL_OF_LIVE_AVATAR =
            Settings.OPEN_CHANNEL_OF_LIVE_AVATAR.get();

    private static volatile String videoId = "";
    private static volatile boolean liveChannelAvatarClicked = false;

    public static void liveChannelAvatarClicked() {
        liveChannelAvatarClicked = true;
    }

    public static boolean openChannelOfLiveAvatar() {
        try {
            if (!OPEN_CHANNEL_OF_LIVE_AVATAR) {
                return false;
            }
            if (!liveChannelAvatarClicked) {
                return false;
            }
            VideoDetailsRequest request = VideoDetailsRequest.getRequestForVideoId(videoId);
            if (request != null) {
                String channelId = request.getInfo();
                if (channelId != null) {
                    liveChannelAvatarClicked = false;
                    VideoUtils.openChannel(channelId);
                    return true;
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "openChannelOfLiveAvatar failure", ex);
        }
        return false;
    }

    public static void openChannelOfLiveAvatar(String newlyLoadedVideoId) {
        try {
            if (!OPEN_CHANNEL_OF_LIVE_AVATAR) {
                return;
            }
            if (newlyLoadedVideoId.isEmpty()) {
                return;
            }
            if (!liveChannelAvatarClicked) {
                return;
            }
            videoId = newlyLoadedVideoId;
            VideoDetailsRequest.fetchRequestIfNeeded(newlyLoadedVideoId);
        } catch (Exception ex) {
            Logger.printException(() -> "openChannelOfLiveAvatar failure", ex);
        }
    }

}
