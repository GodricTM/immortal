/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen camera preview activity. Shows live camera feed from the Portal's
 * front-facing camera. Tap anywhere to exit.
 */
class CameraPreviewActivity : ComponentActivity() {
  private lateinit var textureView: TextureView
  private lateinit var statusText: TextView
  private var cameraDevice: CameraDevice? = null
  private var captureSession: CameraCaptureSession? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  companion object {
    private const val TAG = "CameraPreview"
    private const val CAMERA_PERMISSION_REQUEST = 100
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Immersive fullscreen
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    val root = FrameLayout(this)
    root.setBackgroundColor(0xFF000000.toInt())

    textureView = TextureView(this)
    textureView.surfaceTextureListener = textureListener
    root.addView(textureView, FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    ))

    statusText = TextView(this)
    statusText.setTextColor(0xFFFFFFFF.toInt())
    statusText.textSize = 18f
    statusText.setPadding(32, 32, 32, 32)
    statusText.text = "Starting camera..."
    root.addView(statusText)

    root.setOnTouchListener { _, event ->
      if (event.action == MotionEvent.ACTION_UP) {
        finish()
      }
      true
    }

    setContentView(root)

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == CAMERA_PERMISSION_REQUEST) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        if (textureView.isAvailable) {
          openCamera()
        }
      } else {
        statusText.text = "Camera permission denied. Tap to exit."
        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private val textureListener = object : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
      if (ContextCompat.checkSelfPermission(this@CameraPreviewActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        openCamera()
      }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
  }

  private fun openCamera() {
    val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      // Find front-facing camera (Portal's main camera)
      var frontCameraId: String? = null
      for (cameraId in manager.cameraIdList) {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
          frontCameraId = cameraId
          break
        }
      }

      if (frontCameraId == null) {
        // Fallback to first available camera
        frontCameraId = manager.cameraIdList.firstOrNull()
      }

      if (frontCameraId == null) {
        statusText.text = "No camera found. Tap to exit."
        return
      }

      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        return
      }

      manager.openCamera(frontCameraId, stateCallback, mainHandler)
      statusText.visibility = View.GONE
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Camera access exception", e)
      statusText.text = "Camera access error. Tap to exit."
    } catch (e: SecurityException) {
      Log.e(TAG, "Security exception", e)
      statusText.text = "Camera permission error. Tap to exit."
    }
  }

  private val stateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(camera: CameraDevice) {
      cameraDevice = camera
      createCameraPreviewSession()
    }

    override fun onDisconnected(camera: CameraDevice) {
      camera.close()
      cameraDevice = null
    }

    override fun onError(camera: CameraDevice, error: Int) {
      camera.close()
      cameraDevice = null
      runOnUiThread {
        statusText.visibility = View.VISIBLE
        statusText.text = "Camera error ($error). Tap to exit."
      }
    }
  }

  private fun createCameraPreviewSession() {
    val camera = cameraDevice ?: return
    val texture = textureView.surfaceTexture ?: return

    // Set default buffer size
    texture.setDefaultBufferSize(1280, 720)
    val surface = Surface(texture)

    try {
      val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
      captureRequestBuilder.addTarget(surface)

      camera.createCaptureSession(
        listOf(surface),
        object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(session: CameraCaptureSession) {
            if (cameraDevice == null) return

            captureSession = session
            try {
              captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
              )
              session.setRepeatingRequest(captureRequestBuilder.build(), null, mainHandler)
            } catch (e: CameraAccessException) {
              Log.e(TAG, "Failed to start preview", e)
            }
          }

          override fun onConfigureFailed(session: CameraCaptureSession) {
            runOnUiThread {
              statusText.visibility = View.VISIBLE
              statusText.text = "Camera configuration failed. Tap to exit."
            }
          }
        },
        mainHandler
      )
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Failed to create preview session", e)
    }
  }

  override fun onPause() {
    super.onPause()
    closeCamera()
  }

  private fun closeCamera() {
    captureSession?.close()
    captureSession = null
    cameraDevice?.close()
    cameraDevice = null
  }
}
