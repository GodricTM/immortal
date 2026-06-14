/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.URL

/**
 * Piper TTS engine using Sherpa-ONNX with espeak-ng for high-quality neural text-to-speech.
 * Downloads voice model and espeak-ng data on first use (~16MB total for en_US-lessac-medium).
 */
class PiperTTS(private val context: Context) {

  companion object {
    private const val TAG = "PiperTTS"
    private const val MODEL_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx"
    private const val TOKENS_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/tokens.txt"
    private const val ESPEAK_DATA_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/espeak-ng-data.tar.bz2"
  }

  private var tts: OfflineTts? = null
  private var isReady = false
  private var audioTrack: AudioTrack? = null

  private val modelFile: File by lazy { File(context.filesDir, "piper_model.onnx") }
  private val tokensFile: File by lazy { File(context.filesDir, "tokens.txt") }
  private val espeakDataDir: File by lazy { File(context.filesDir, "espeak-ng-data") }

  /**
   * Initialize the TTS engine. Downloads model files if not present.
   */
  fun initialize(onComplete: (Boolean) -> Unit) {
    Thread {
      try {
        // Download files if not present
        if (!modelFile.exists()) {
          Log.d(TAG, "Downloading Piper voice model...")
          downloadFile(MODEL_URL, modelFile)
        }

        if (!tokensFile.exists()) {
          Log.d(TAG, "Downloading tokens file...")
          downloadFile(TOKENS_URL, tokensFile)
        }

        if (!espeakDataDir.exists()) {
          Log.d(TAG, "Downloading espeak-ng data...")
          downloadAndExtractEspeakData()
        }

        // Create Sherpa-ONNX TTS configuration
        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelFile.absolutePath,
            tokens = tokensFile.absolutePath,
            dataDir = espeakDataDir.absolutePath,
            lexicon = "",
            dictDir = "",
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )

        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )

        val config = OfflineTtsConfig(
            model = modelConfig,
            ruleFsts = "",
            maxNumSentences = 1
        )

        // Initialize TTS (Sherpa-ONNX Android requires AssetManager as first arg)
        tts = OfflineTts(context.assets, config)

        isReady = true
        Log.d(TAG, "Piper TTS initialized successfully (Sherpa-ONNX)")
        onComplete(true)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Piper TTS", e)
        // Delete any partial/corrupt files so the next launch retries the download
        // rather than getting stuck on a broken file forever.
        runCatching { modelFile.delete() }
        runCatching { tokensFile.delete() }
        runCatching { espeakDataDir.deleteRecursively() }
        isReady = false
        onComplete(false)
      }
    }.start()
  }

  private fun downloadFile(url: String, dest: File) {
    URL(url).openStream().use { input ->
      dest.outputStream().use { output ->
        input.copyTo(output)
      }
    }
  }

  private fun downloadAndExtractEspeakData() {
    val tarFile = File(context.cacheDir, "espeak-ng-data.tar.bz2")
    try {
      downloadFile(ESPEAK_DATA_URL, tarFile)
      BZip2CompressorInputStream(tarFile.inputStream().buffered()).use { bz2 ->
        TarArchiveInputStream(bz2).use { tar ->
          var entry = tar.nextEntry
          while (entry != null) {
            if (!tar.canReadEntryData(entry)) { entry = tar.nextEntry; continue }
            val outFile = File(context.filesDir, entry.name)
            if (entry.isDirectory) {
              outFile.mkdirs()
            } else {
              outFile.parentFile?.mkdirs()
              outFile.outputStream().use { tar.copyTo(it) }
            }
            entry = tar.nextEntry
          }
        }
      }
    } finally {
      tarFile.delete()
    }
  }

  /**
   * Synthesize speech from text and play it.
   */
  fun speak(text: String): Boolean {
    if (!isReady || tts == null) return false

    return try {
      // Generate audio using Sherpa-ONNX
      val audio: GeneratedAudio = tts!!.generate(
          text = text,
          sid = 0,
          speed = 1.0f
      )

      // Play the audio
      playAudio(audio.samples, audio.sampleRate)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to speak", e)
      false
    }
  }

  private fun playAudio(samples: FloatArray, sampleRate: Int) {
    // Stop any existing playback
    audioTrack?.stop()
    audioTrack?.release()

    // Convert float to PCM16
    val pcmData = ShortArray(samples.size)
    for (i in samples.indices) {
      val sample = (samples[i] * Short.MAX_VALUE).toInt()
      pcmData[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(pcmData.size * 2)

    audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .build()

    audioTrack?.play()
    audioTrack?.write(pcmData, 0, pcmData.size)
  }

  fun stop() {
    audioTrack?.stop()
  }

  fun shutdown() {
    audioTrack?.release()
    audioTrack = null
    tts?.release()
    tts = null
    isReady = false
  }
}
