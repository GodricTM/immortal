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
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Records and plays the single "leave a note" voice memo to [NotesConfig.audioFile].
 *
 * Why [AudioRecord]/WAV instead of [MediaRecorder]/AAC: on Portal the one mic a
 * sideloaded app can reach is shared with the device's always-on listeners, which
 * keep reclaiming the input ("dead IAudioRecord, creating a new one" in logcat).
 * MediaRecorder's AAC encoder simply stops getting frames when that happens and
 * finalises a clip ~0.25 s long no matter how long you hold the button. A raw
 * [AudioRecord] read loop recovers from the reclaim and keeps producing samples,
 * so we get a full-length take; we write it straight to WAV (no encoder to starve)
 * and apply a little gain because the near-field handset mic is quiet.
 *
 * One instance owns one recording/player at a time; call [release] when done.
 */
class AudioNote(private val context: Context) {
  private val TAG = "ImmortalNote"
  private val sampleRate = 44100
  private val gain = 3.5f // near-field handset mic is quiet; lift it to audible

  @Volatile private var capturing = false
  private var recordThread: Thread? = null
  @Volatile private var peak = 0
  private var recordStartMs = 0L
  private var player: MediaPlayer? = null

  val isRecording: Boolean get() = capturing
  val isPlaying: Boolean get() = player?.isPlaying == true

  private val pcmFile: File get() = File(context.filesDir, "leave_note.pcm")

  /** Begin recording to the note file (overwrites any previous memo). */
  fun startRecording(): Boolean {
    stopPlaying()
    val minBuf = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufSize = if (minBuf > 0) minBuf * 2 else sampleRate
    // MIC first — VOICE_RECOGNITION collides with the wake-word engine.
    val sources = intArrayOf(
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.DEFAULT,
    )
    var record: AudioRecord? = null
    for (src in sources) {
      val r = runCatching {
        AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize)
      }.getOrNull()
      if (r != null && r.state == AudioRecord.STATE_INITIALIZED) { record = r; break }
      runCatching { r?.release() }
    }
    val rec = record ?: run { Log.w(TAG, "no usable AudioRecord source"); return false }

    return runCatching {
      runCatching { pcmFile.delete() }
      NotesConfig.audioFile(context).delete()
      val out = BufferedOutputStream(FileOutputStream(pcmFile))
      rec.startRecording()
      capturing = true
      peak = 0
      recordStartMs = System.currentTimeMillis()
      recordThread = Thread {
        val buf = ShortArray(bufSize / 2)
        val bytes = ByteArray(buf.size * 2)
        try {
          while (capturing) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) continue
            var localPeak = peak
            var bi = 0
            for (i in 0 until n) {
              val raw = buf[i].toInt()
              val a = if (raw < 0) -raw else raw
              if (a > localPeak) localPeak = a
              var v = (raw * gain).toInt()
              if (v > 32767) v = 32767 else if (v < -32768) v = -32768
              bytes[bi++] = (v and 0xFF).toByte()
              bytes[bi++] = ((v shr 8) and 0xFF).toByte()
            }
            peak = localPeak
            out.write(bytes, 0, bi)
          }
        } catch (t: Throwable) {
          Log.w(TAG, "capture loop ended", t)
        } finally {
          runCatching { out.flush(); out.close() }
          runCatching { rec.stop() }
          runCatching { rec.release() }
        }
      }.also { it.start() }
      Log.i(TAG, "recording started (AudioRecord/WAV)")
      true
    }.getOrElse {
      Log.w(TAG, "startRecording failed", it)
      capturing = false
      runCatching { rec.release() }
      false
    }
  }

  /** Stop and finalize the recording. Returns true if a playable file resulted. */
  fun stopRecording(): Boolean {
    if (!capturing && recordThread == null) return NotesConfig.hasAudioNote(context)
    val tooShort = System.currentTimeMillis() - recordStartMs < 400
    capturing = false
    runCatching { recordThread?.join(2000) }
    recordThread = null
    val wav = NotesConfig.audioFile(context)
    val pcmLen = pcmFile.length()
    if (tooShort || pcmLen <= 0) {
      Log.w(TAG, "recording too short / empty — discarding (pcm=$pcmLen)")
      runCatching { pcmFile.delete() }
      return false
    }
    val ok = runCatching { writeWav(pcmFile, wav, sampleRate) }.isSuccess
    runCatching { pcmFile.delete() }
    Log.i(TAG, "wrote ${wav.length()} bytes WAV (${pcmLen / (sampleRate * 2.0)} s) ok=$ok")
    return ok && wav.exists() && wav.length() > 44
  }

  /** Peak input level since the last poll, 0..32767. ~0 means the mic is hearing
   *  near-silence (speaker too far from the device). Drives the UI meter. */
  fun peakLevel(): Int = peak.also { peak = 0 }

  /** Wrap raw 16-bit mono PCM in a canonical 44-byte WAV header. */
  private fun writeWav(pcm: File, wav: File, rate: Int) {
    val dataLen = pcm.length().toInt()
    val totalLen = dataLen + 36
    val byteRate = rate * 2 // mono, 16-bit
    RandomAccessFile(wav, "rw").use { raf ->
      raf.setLength(0)
      fun le32(v: Int) = byteArrayOf(
          (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
          ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
      fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
      raf.write("RIFF".toByteArray()); raf.write(le32(totalLen)); raf.write("WAVE".toByteArray())
      raf.write("fmt ".toByteArray()); raf.write(le32(16)); raf.write(le16(1)) // PCM
      raf.write(le16(1)) // mono
      raf.write(le32(rate)); raf.write(le32(byteRate)); raf.write(le16(2)); raf.write(le16(16))
      raf.write("data".toByteArray()); raf.write(le32(dataLen))
      pcm.inputStream().use { it.copyTo(java.io.FileOutputStream(raf.fd)) }
    }
  }

  /** Play the saved memo, invoking [onDone] when it finishes (or fails). */
  fun play(onDone: () -> Unit = {}) {
    val file = NotesConfig.audioFile(context)
    if (!file.exists() || file.length() <= 44) {
      Log.w(TAG, "play: no file (${file.length()} bytes)"); onDone(); return
    }
    runCatching {
      stopPlaying()
      val mp = MediaPlayer()
      mp.setAudioAttributes(
          AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
      java.io.FileInputStream(file).use { fis -> mp.setDataSource(fis.fd) }
      mp.setOnCompletionListener { stopPlaying(); onDone() }
      mp.setOnErrorListener { _, what, extra ->
        Log.w(TAG, "MediaPlayer error what=$what extra=$extra"); stopPlaying(); onDone(); true
      }
      mp.prepare()
      mp.start()
      player = mp
    }.onFailure { Log.w(TAG, "play failed", it); onDone() }
  }

  fun stopPlaying() {
    player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
    player = null
  }

  fun release() {
    stopRecording()
    stopPlaying()
  }
}
