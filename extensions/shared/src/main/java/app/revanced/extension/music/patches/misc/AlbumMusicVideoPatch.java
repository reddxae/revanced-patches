package app.revanced.extension.music.patches.misc;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import app.revanced.extension.music.patches.misc.requests.PipedRequester;
import app.revanced.extension.music.settings.Settings;
import app.revanced.extension.music.utils.VideoUtils;
import app.revanced.extension.shared.utils.Logger;

@SuppressWarnings("unused")
public class AlbumMusicVideoPatch {
    private static final String YOUTUBE_MUSIC_ALBUM_PREFIX = "OLAK";
    private static final boolean DISABLE_MUSIC_VIDEO_IN_ALBUM =
            Settings.DISABLE_MUSIC_VIDEO_IN_ALBUM.get();

    private static final AtomicBoolean isVideoLaunched = new AtomicBoolean(false);

    @NonNull
    private static volatile String playerResponseVideoId = "";

    @NonNull
    private static volatile String currentVideoId = "";

    /**
     * Injection point.
     */
    public static void newPlayerResponse(@NonNull String videoId, @NonNull String playlistId, final int playlistIndex) {
        if (!DISABLE_MUSIC_VIDEO_IN_ALBUM) {
            return;
        }
        if (!playlistId.startsWith(YOUTUBE_MUSIC_ALBUM_PREFIX)) {
            return;
        }
        if (playlistIndex < 0) {
            return;
        }
        if (playerResponseVideoId.equals(videoId)) {
            return;
        }
        playerResponseVideoId = videoId;

        // Fetch from piped instances.
        PipedRequester.fetchRequestIfNeeded(videoId, playlistId, playlistIndex);
    }

    /**
     * Injection point.
     */
    public static void newVideoLoaded(@NonNull String videoId) {
        if (!DISABLE_MUSIC_VIDEO_IN_ALBUM) {
            return;
        }
        if (currentVideoId.equals(videoId)) {
            return;
        }
        currentVideoId = videoId;

        // If the user is using a not fast enough internet connection, there will be a slight delay.
        // Otherwise, the video may open repeatedly.
        VideoUtils.runOnMainThreadDelayed(() -> openOfficialMusicIfNeeded(videoId), 750);
    }

    private static void openOfficialMusicIfNeeded(@NonNull String videoId) {
        try {
            PipedRequester request = PipedRequester.getRequestForVideoId(videoId);
            if (request == null) {
                return;
            }
            String songId = request.getStream();
            if (songId == null) {
                return;
            }

            // It is handled by YouTube Music's internal code.
            // There is a slight delay before the dismiss request is reflected.
            VideoUtils.dismissQueue();

            // Every time a new video is opened, a snack bar appears indicating that the account has been switched.
            // To prevent this, hide the snack bar while a new video is opening.
            isVideoLaunched.compareAndSet(false, true);

            // The newly opened video is not a music video.
            // To prevent fetch requests from being sent, set the video id to the newly opened video
            VideoUtils.runOnMainThreadDelayed(() -> {
                playerResponseVideoId = songId;
                currentVideoId = songId;
                VideoUtils.openInYouTubeMusic(songId);
            }, 750);

            // If a new video is opened, the snack bar will be shown.
            VideoUtils.runOnMainThreadDelayed(() -> isVideoLaunched.compareAndSet(true, false), 1500);
        } catch (Exception ex) {
            Logger.printException(() -> "openOfficialMusicIfNeeded failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static boolean hideSnackBar() {
        if (!DISABLE_MUSIC_VIDEO_IN_ALBUM) {
            return false;
        }
        return isVideoLaunched.get();
    }

}
