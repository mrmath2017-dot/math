package com.smartmath.teacher.data.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EdgeTtsService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val wssUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EA654941A3E45684D6F6FF60"

    /**
     * Synthesizes Syrian speech using Microsoft Edge TTS and saves it as an MP3 file.
     * @param text Spoken explanation in Syrian Shami dialect
     * @param voiceName Either "ar-SY-LaithNeural" or "ar-SY-AmanyNeural"
     */
    suspend fun synthesizeSpeech(text: String, voiceName: String = "ar-SY-LaithNeural"): File = withContext(Dispatchers.IO) {
        val audioBytes = fetchAudioStream(text, voiceName)
        val cacheFile = File(context.cacheDir, "math_teacher_${System.currentTimeMillis()}.mp3")
        FileOutputStream(cacheFile).use { fos ->
            fos.write(audioBytes)
        }
        cacheFile
    }

    private suspend fun fetchAudioStream(text: String, voiceName: String): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val request = Request.Builder()
                .url(wssUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
                .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .build()

            val audioBuffer = ByteArrayOutputStream()

            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 1. Send speech.config
                    val configMsg = "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                    webSocket.send(configMsg)

                    // 2. Prepare SSML
                    val escapedText = text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&apos;")

                    val timestamp = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US).format(Date())
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ar-SY'>" +
                            "<voice name='$voiceName'>" +
                            "<prosody pitch='+0Hz' rate='+0%'>" +
                            escapedText +
                            "</prosody>" +
                            "</voice></speak>"

                    val ssmlMsg = "X-RequestId:$requestId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:$timestamp\r\n" +
                            "Path:ssml\r\n\r\n" +
                            ssml

                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val raw = bytes.toByteArray()
                    if (raw.size >= 2) {
                        // Microsoft binary protocol:
                        // First 2 bytes = length of headers (Big Endian)
                        val headerLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                        val headerOffset = 2 + headerLen
                        if (raw.size > headerOffset) {
                            val audioPayload = raw.copyOfRange(headerOffset, raw.size)
                            synchronized(audioBuffer) {
                                audioBuffer.write(audioPayload)
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, textMessage: String) {
                    if (textMessage.contains("Path:turn.end")) {
                        webSocket.close(1000, "Done")
                        val result = audioBuffer.toByteArray()
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("EdgeTtsService", "WebSocket synthesis error: ${t.message}", t)
                    if (continuation.isActive) {
                        // If we already buffered some audio, return it, otherwise error
                        val buffered = audioBuffer.toByteArray()
                        if (buffered.isNotEmpty()) {
                            continuation.resume(buffered)
                        } else {
                            continuation.resumeWithException(t)
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (continuation.isActive) {
                        continuation.resume(audioBuffer.toByteArray())
                    }
                }
            })

            continuation.invokeOnCancellation {
                webSocket.cancel()
            }
        }
}
