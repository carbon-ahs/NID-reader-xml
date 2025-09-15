package com.example.nid_reader_xml

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

class CameraActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var idCardOverlay: IDCardOverlay
    private lateinit var viewModel: CameraViewModel
    private val TAG = "CameraActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Find views
        previewView = findViewById(R.id.previewView)
        idCardOverlay = findViewById(R.id.idCardOverlay)
        val buttonCapture = findViewById<Button>(R.id.buttonCapture)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]

//        viewModel.nidData.observe(this) { nidData ->
//            textViewMsg.text = nidData.toString() // ✅ updates automatically
//            if (nidData != null) {
//                this.nidData = nidData
//            }
//        }
//        textViewMsg.setText(viewModel.getStr())

        // Capture button
        buttonCapture.setOnClickListener {
            if (checkStoragePermission()) {
                val rect = idCardOverlay.getOverlayRect()
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

        // Observe camera initialization
        viewModel.cameraInitialized.observe(this) { initialized ->
            if (initialized) {
                startCamera()
            }
        }

        // Observe capture results
        viewModel.captureResult.observe(this) { result ->
            result?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                if (it.startsWith("Photo saved")) {
                    val imagePath = it.substringAfter("Photo saved: ").trim()
                    val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                        putExtra("IMAGE_PATH", imagePath)
                    }
                    startActivity(intent)
                } else if (it.startsWith("Failed")) {
                    finish()
                }
                viewModel.clearCaptureResult()
            }
        }

        // Check camera permission
        if (checkCameraPermission()) {
            viewModel.initialize(this, previewView)
        } else {
            requestCameraPermission()
        }
    }

    // ---------------------
    // Permissions
    // ---------------------
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.initialize(this, previewView)
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val rect = idCardOverlay.getOverlayRect()
            viewModel.takePhoto(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                previewView.width,
                previewView.height
            )
        } else {
            Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
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
        } else true
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val rect = idCardOverlay.getOverlayRect()
            viewModel.takePhoto(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                previewView.width,
                previewView.height
            )
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ---------------------
    // Camera setup
    // ---------------------
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
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
