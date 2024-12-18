package app.revanced.extension.shared.patches.spoof;

import static app.revanced.extension.shared.utils.Utils.isSDKAbove;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.libraries.youtube.innertube.model.media.FormatStreamModel;
import com.google.protos.youtube.api.innertube.StreamingDataOuterClass$StreamingData;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import app.revanced.extension.shared.patches.BlockRequestPatch;
import app.revanced.extension.shared.patches.client.AppClient.ClientType;
import app.revanced.extension.shared.patches.spoof.requests.StreamingDataRequest;
import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.Utils;

@SuppressWarnings("unused")
public class SpoofStreamingDataPatch extends BlockRequestPatch {

    /**
     * Key: videoId.
     * Value: Original StreamingData of Android client.
     */
    private static final Map<String, StreamingDataOuterClass$StreamingData> streamingDataMap = Collections.synchronizedMap(
            new LinkedHashMap<>(10) {
                private static final int CACHE_LIMIT = 5;

                @Override
                protected boolean removeEldestEntry(Entry eldest) {
                    return size() > CACHE_LIMIT; // Evict the oldest entry if over the cache limit.
                }
            });

    /**
     * Injection point.
     */
    public static boolean isSpoofingEnabled() {
        return SPOOF_STREAMING_DATA;
    }

    /**
     * Injection point.
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
     *
     * @param originalStreamingData Original StreamingData.
     */
    @Nullable
    public static ByteBuffer getStreamingData(String videoId, StreamingDataOuterClass$StreamingData originalStreamingData) {
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
                        ByteBuffer spoofedStreamingData = stream.first;
                        ClientType spoofedClientType = stream.second;

                        Logger.printDebug(() -> "Overriding video stream: " + videoId);

                        // Put the videoId and originalStreamingData into a HashMap.
                        if (spoofedClientType == ClientType.IOS) {
                            // For YT Music 6.20.51, which is supported by RVX, it can run on Android 5.0 (SDK 21).
                            // The IDE does not make any suggestions since the project's minSDK is 24, but you should check the SDK version for compatibility with SDK 21.
                            if (isSDKAbove(24)) {
                                streamingDataMap.putIfAbsent(videoId, originalStreamingData);
                            } else {
                                if (!streamingDataMap.containsKey(videoId)) {
                                    streamingDataMap.put(videoId, originalStreamingData);
                                }
                            }
                        }

                        return spoofedStreamingData;
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
     * In iOS Clients, Progressive Streaming are not available, so 'formats' field have been removed
     * completely from the initial response of streaming data.
     * Therefore, {@link FormatStreamModel} class is never be initialized, and the video length field
     * is set with an estimated value from `adaptiveFormats` instead.
     * <p>
     * To get workaround with this, replace streamingData (spoofedStreamingData) with originalStreamingData,
     * which is only used to initialize the {@link FormatStreamModel} class to calculate the video length.
     * The playback issues shouldn't occur since the integrity check is not applied for Progressive Stream.
     * <p>
     * Called after {@link #getStreamingData(String, StreamingDataOuterClass$StreamingData)}.
     *
     * @param spoofedStreamingData Spoofed StreamingData.
     */
    public static StreamingDataOuterClass$StreamingData getOriginalStreamingData(String videoId, StreamingDataOuterClass$StreamingData spoofedStreamingData) {
        if (SPOOF_STREAMING_DATA) {
            try {
                StreamingDataOuterClass$StreamingData androidStreamingData = streamingDataMap.get(videoId);
                if (androidStreamingData != null) {
                    Logger.printDebug(() -> "Overriding iOS streaming data to original streaming data: " + videoId);
                    return androidStreamingData;
                } else {
                    Logger.printDebug(() -> "Not overriding original streaming data as spoofed client is not iOS: " + videoId);
                }
            } catch (Exception ex) {
                Logger.printException(() -> "getOriginalStreamingData failure", ex);
            }
        }
        return spoofedStreamingData;
    }

    /**
     * Injection point.
     * Called after {@link #getStreamingData(String, StreamingDataOuterClass$StreamingData)}.
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
