package com.example.nid_reader_xml

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraActivity : AppCompatActivity() {
    private val viewModel: CameraViewModel by viewModels()
    private lateinit var previewView: PreviewView
    private lateinit var idCardOverlay: IDCardOverlay
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val STORAGE_PERMISSION_REQUEST_CODE = 1002
    private val TAG = "CameraActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Find views using findViewById
        previewView = findViewById(R.id.previewView)
        idCardOverlay = findViewById(R.id.idCardOverlay)
        val buttonCapture = findViewById<Button>(R.id.buttonCapture)

        // Set click listener for capture button
        buttonCapture.setOnClickListener {
            if (checkStoragePermission()) {
                val rect = idCardOverlay.getOverlayRect()
                Log.d(TAG, "Overlay Rect: left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}")
                viewModel.takePhoto(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    previewView.width,
                    previewView.height
                )
            } else {
                requestStoragePermission()
            }
        }

        // Observe ViewModel LiveData
        viewModel.cameraInitialized.observe(this) { initialized ->
            if (initialized) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(this, cameraSelector, preview, viewModel.imageCapture)
                        Log.d(TAG, "Camera bound successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind camera: ${e.message}", e)
                        Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }, ContextCompat.getMainExecutor(this))
            }
        }

        viewModel.captureResult.observe(this) { result ->
            result?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                if (it.startsWith("Photo saved")) {
                    val imagePath = it.substringAfter("Photo saved: ").trim()
                    Log.d(TAG, "Navigating to ImagePreviewActivity with path: $imagePath")
                    val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                        putExtra("IMAGE_PATH", imagePath)
                    }
                    startActivity(intent)
                } else if (it.startsWith("Failed")) {
                    Log.e(TAG, "Capture result: $it")
                    finish()
                }
                viewModel.clearCaptureResult()
            }
        }

        // Check camera permission and initialize camera
        if (checkCameraPermission()) {
            viewModel.initialize(this)
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT < 30) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No storage permission needed for API 30+
        }
    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Toast.makeText(this, "Camera permission is needed to open the camera", Toast.LENGTH_SHORT).show()
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val rect = idCardOverlay.getOverlayRect()
            Log.d(TAG, "Overlay Rect (storage permission granted): left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}")
            viewModel.takePhoto(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                previewView.width,
                previewView.height
            )
            return
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, "Storage permission is needed to save photos", Toast.LENGTH_SHORT).show()
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.initialize(this)
                } else {
                    Log.e(TAG, "Camera permission denied")
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val rect = idCardOverlay.getOverlayRect()
                    Log.d(TAG, "Overlay Rect (storage permission granted): left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}")
                    viewModel.takePhoto(
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom,
                        previewView.width,
                        previewView.height
                    )
                } else {
                    Log.e(TAG, "Storage permission denied")
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}