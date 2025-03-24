package app.revanced.extension.youtube.patches.utils.requests

import androidx.annotation.GuardedBy
import app.revanced.extension.shared.patches.client.YouTubeAppClient
import app.revanced.extension.shared.patches.spoof.requests.PlayerRoutes
import app.revanced.extension.shared.requests.Requester
import app.revanced.extension.shared.utils.Logger
import app.revanced.extension.shared.utils.Utils
import app.revanced.extension.youtube.patches.utils.requests.DeletePlaylistRequest.Companion.HTTP_TIMEOUT_MILLISECONDS
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.Objects
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeletePlaylistRequest private constructor(
    private val playlistId: String,
    private val playerHeaders: Map<String, String>,
) {
    private val future: Future<Boolean> = Utils.submitOnBackgroundThread {
        fetch(
            playlistId,
            playerHeaders,
        )
    }

    val result: Boolean?
        get() {
            try {
                return future[MAX_MILLISECONDS_TO_WAIT_FOR_FETCH.toLong(), TimeUnit.MILLISECONDS]
            } catch (ex: TimeoutException) {
                Logger.printInfo(
                    { "getResult timed out" },
                    ex
                )
            } catch (ex: InterruptedException) {
                Logger.printException(
                    { "getResult interrupted" },
                    ex
                )
                Thread.currentThread().interrupt() // Restore interrupt status flag.
            } catch (ex: ExecutionException) {
                Logger.printException(
                    { "getResult failure" },
                    ex
                )
            }

            return null
        }

    companion object {
        /**
         * TCP connection and HTTP read timeout.
         */
        private const val HTTP_TIMEOUT_MILLISECONDS = 10 * 1000

        /**
         * Any arbitrarily large value, but must be at least twice [HTTP_TIMEOUT_MILLISECONDS]
         */
        private const val MAX_MILLISECONDS_TO_WAIT_FOR_FETCH = 20 * 1000

        @GuardedBy("itself")
        val cache: MutableMap<String, DeletePlaylistRequest> = Collections.synchronizedMap(
            object : LinkedHashMap<String, DeletePlaylistRequest>(100) {
                private val CACHE_LIMIT = 50

                override fun removeEldestEntry(eldest: Map.Entry<String, DeletePlaylistRequest>): Boolean {
                    return size > CACHE_LIMIT // Evict the oldest entry if over the cache limit.
                }
            })

        @JvmStatic
        fun clear() {
            synchronized(cache) {
                cache.clear()
            }
        }

        @JvmStatic
        fun fetchRequestIfNeeded(
            playlistId: String,
            playerHeaders: Map<String, String>
        ) {
            Objects.requireNonNull(playlistId)
            synchronized(cache) {
                if (!cache.containsKey(playlistId)) {
                    cache[playlistId] = DeletePlaylistRequest(
                        playlistId,
                        playerHeaders
                    )
                }
            }
        }

        @JvmStatic
        fun getRequestForPlaylistId(playlistId: String): DeletePlaylistRequest? {
            synchronized(cache) {
                return cache[playlistId]
            }
        }

        private fun handleConnectionError(toastMessage: String, ex: Exception?) {
            Logger.printInfo({ toastMessage }, ex)
        }

        private val REQUEST_HEADER_KEYS = arrayOf(
            "Authorization",  // Available only to logged-in users.
            "X-GOOG-API-FORMAT-VERSION",
            "X-Goog-Visitor-Id"
        )

        private fun sendRequest(
            playlistId: String,
            playerHeaders: Map<String, String>
        ): JSONObject? {
            Objects.requireNonNull(playlistId)

            val startTime = System.currentTimeMillis()
            // 'playlist/delete' request does not require PoToken.
            val clientType = YouTubeAppClient.ClientType.ANDROID
            val clientTypeName = clientType.name
            Logger.printDebug { "Fetching delete playlist request, playlistId: $playlistId, using client: $clientTypeName" }

            try {
                val connection = PlayerRoutes.getPlayerResponseConnectionFromRoute(
                    PlayerRoutes.DELETE_PLAYLIST,
                    clientType,
                )
                connection.connectTimeout = HTTP_TIMEOUT_MILLISECONDS
                connection.readTimeout = HTTP_TIMEOUT_MILLISECONDS

                for (key in REQUEST_HEADER_KEYS) {
                    var value = playerHeaders[key]
                    if (value != null) {
                        connection.setRequestProperty(key, value)
                    }
                }

                val requestBody = PlayerRoutes.deletePlaylistRequestBody(playlistId)

                connection.setFixedLengthStreamingMode(requestBody.size)
                connection.outputStream.write(requestBody)

                val responseCode = connection.responseCode
                if (responseCode == 200) return Requester.parseJSONObject(connection)

                handleConnectionError(
                    (clientTypeName + " not available with response code: "
                            + responseCode + " message: " + connection.responseMessage),
                    null
                )
            } catch (ex: SocketTimeoutException) {
                handleConnectionError("Connection timeout", ex)
            } catch (ex: IOException) {
                handleConnectionError("Network error", ex)
            } catch (ex: Exception) {
                Logger.printException({ "sendRequest failed" }, ex)
            } finally {
                Logger.printDebug { "playlist: " + playlistId + " took: " + (System.currentTimeMillis() - startTime) + "ms" }
            }

            return null
        }

        private fun parseResponse(json: JSONObject): Boolean? {
            try {
                return json.has("command")
            } catch (e: JSONException) {
                val jsonForMessage = json.toString()
                Logger.printException(
                    { "Fetch failed while processing response data for response: $jsonForMessage" },
                    e
                )
            }

            return null
        }

        private fun fetch(
            playlistId: String,
            playerHeaders: Map<String, String>
        ): Boolean? {
            val json = sendRequest(playlistId, playerHeaders)
            if (json != null) {
                return parseResponse(json)
            }

            return null
        }
    }
}
