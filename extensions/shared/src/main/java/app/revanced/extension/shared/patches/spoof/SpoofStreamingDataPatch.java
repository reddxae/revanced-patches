package app.revanced.extension.shared.patches.spoof;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.protos.youtube.api.innertube.StreamingDataOuterClass$StreamingData;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.revanced.extension.shared.patches.BlockRequestPatch;
import app.revanced.extension.shared.patches.spoof.requests.StreamingDataRequest;
import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.Utils;

@SuppressWarnings("unused")
public class SpoofStreamingDataPatch extends BlockRequestPatch {

    /**
     * Key: videoId.
     * Value: original [streamingData.formats].
     */
    private static final ConcurrentHashMap<String, List<?>> formatsMap = new ConcurrentHashMap<>(20, 0.8f);

    /**
     * Injection point.
     */
    public static boolean isSpoofingEnabled() {
        return SPOOF_STREAMING_DATA;
    }

    /**
     * Injection point.
     * This method is only invoked when playing a livestream on an iOS client.
     */
    public static boolean fixHLSCurrentTime(boolean original) {
        if (!SPOOF_STREAMING_DATA) {
            return original;
        }
        return false;
    }

    /**
     * Injection point.
     */
    public static void fetchStreams(String url, Map<String, String> requestHeaders) {
        if (SPOOF_STREAMING_DATA) {
            try {
                Uri uri = Uri.parse(url);
                String path = uri.getPath();

                // 'heartbeat' has no video id and appears to be only after playback has started.
                // 'refresh' has no video id and appears to happen when waiting for a livestream to start.
                if (path != null && path.contains("player") && !path.contains("heartbeat")
                        && !path.contains("refresh")) {
                    String id = uri.getQueryParameter("id");
                    if (id == null) {
                        Logger.printException(() -> "Ignoring request that has no video id." +
                                " Url: " + url + " headers: " + requestHeaders);
                        return;
                    }

                    StreamingDataRequest.fetchRequest(id, requestHeaders);
                }
            } catch (Exception ex) {
                Logger.printException(() -> "buildRequest failure", ex);
            }
        }
    }

    /**
     * Injection point.
     * Fix playback by replace the streaming data.
     * Called after {@link #fetchStreams(String, Map)}.
     */
    @Nullable
    public static ByteBuffer getStreamingData(String videoId) {
        if (SPOOF_STREAMING_DATA) {
            try {
                StreamingDataRequest request = StreamingDataRequest.getRequestForVideoId(videoId);
                if (request != null) {
                    // This hook is always called off the main thread,
                    // but this can later be called for the same video id from the main thread.
                    // This is not a concern, since the fetch will always be finished
                    // and never block the main thread.
                    // But if debugging, then still verify this is the situation.
                    if (BaseSettings.ENABLE_DEBUG_LOGGING.get() && !request.fetchCompleted() && Utils.isCurrentlyOnMainThread()) {
                        Logger.printException(() -> "Error: Blocking main thread");
                    }

                    var stream = request.getStream();
                    if (stream != null) {
                        Logger.printDebug(() -> "Overriding video stream: " + videoId);
                        return stream;
                    }
                }

                Logger.printDebug(() -> "Not overriding streaming data (video stream is null): " + videoId);
            } catch (Exception ex) {
                Logger.printException(() -> "getStreamingData failure", ex);
            }
        }

        return null;
    }

    /**
     * Injection point.
     * <p>
     * If spoofed [streamingData.formats] is empty,
     * Put the original [streamingData.formats] into the HashMap.
     * <p>
     * Called after {@link #getStreamingData(String)}.
     */
    public static void setFormats(String videoId, StreamingDataOuterClass$StreamingData originalStreamingData, StreamingDataOuterClass$StreamingData spoofed) {
        if (formatsIsEmpty(spoofed)) {
            formatsMap.put(videoId, getFormatsFromStreamingData(originalStreamingData));
            Logger.printDebug(() -> "New formats video id: " + videoId);
        }
    }

    private static boolean formatsIsEmpty(StreamingDataOuterClass$StreamingData streamingData) {
        List<?> formats = getFormatsFromStreamingData(streamingData);
        return formats == null || formats.size() == 0;
    }

    private static List<?> getFormatsFromStreamingData(StreamingDataOuterClass$StreamingData streamingData) {
        try {
            // Field e: 'formats'.
            Field field = streamingData.getClass().getDeclaredField("e");
            field.setAccessible(true);
            if (field.get(streamingData) instanceof List<?> list) {
                return list;
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            Logger.printException(() -> "Reflection error accessing formats", ex);
        }
        return null;
    }

    /**
     * Looks like the initial value for the videoId field.
     */
    private static final String MASKED_VIDEO_ID = "zzzzzzzzzzz";

    /**
     * Injection point.
     * <p>
     * When measuring the length of a video in an Android YouTube client,
     * the client first checks if the streaming data contains [streamingData.formats.approxDurationMs].
     * <p>
     * If the streaming data response contains [approxDurationMs] (Long type, actual value), this value will be the video length.
     * <p>
     * If [streamingData.formats] (List type) is empty, the [approxDurationMs] value cannot be accessed,
     * So it falls back to the value of [videoDetails.lengthSeconds] (Integer type, approximate value) multiplied by 1000.
     * <p>
     * For iOS clients, [streamingData.formats] (List type) is always empty, so it always falls back to the approximate value.
     * <p>
     * Called after {@link #getStreamingData(String)}.
     */
    public static List<?> getOriginalFormats(String videoId, List<?> spoofedFormats) {
        if (SPOOF_STREAMING_DATA) {
            try {
                if (videoId != null && !videoId.equals(MASKED_VIDEO_ID) && spoofedFormats.size() == 0) {
                    List<?> androidFormats = formatsMap.get(videoId);
                    if (androidFormats != null) {
                        Logger.printDebug(() -> "Overriding iOS formats to original formats: " + videoId);
                        return androidFormats;
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "getOriginalFormats failure", ex);
            }
        }
        return spoofedFormats;
    }

    /**
     * Injection point.
     * Called after {@link #getStreamingData(String)}.
     */
    @Nullable
    public static byte[] removeVideoPlaybackPostBody(Uri uri, int method, byte[] postData) {
        if (SPOOF_STREAMING_DATA) {
            try {
                final int methodPost = 2;
                if (method == methodPost) {
                    String path = uri.getPath();
                    if (path != null && path.contains("videoplayback")) {
                        return null;
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "removeVideoPlaybackPostBody failure", ex);
            }
        }

        return postData;
    }

    /**
     * Injection point.
     */
    public static String appendSpoofedClient(String videoFormat) {
        try {
            if (SPOOF_STREAMING_DATA && BaseSettings.SPOOF_STREAMING_DATA_STATS_FOR_NERDS.get()
                    && !TextUtils.isEmpty(videoFormat)) {
                // Force LTR layout, to match the same LTR video time/length layout YouTube uses for all languages
                return "\u202D" + videoFormat + String.format("\u2009(%s)", StreamingDataRequest.getLastSpoofedClientName()); // u202D = left to right override
            }
        } catch (Exception ex) {
            Logger.printException(() -> "appendSpoofedClient failure", ex);
        }

        return videoFormat;
    }
}
