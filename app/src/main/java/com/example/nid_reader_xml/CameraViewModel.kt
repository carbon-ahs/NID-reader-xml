package com.example.nid_reader_xml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nid_reader_xml.NidUtils.extractNidData
import com.example.nid_reader_xml.NidUtils.extractNidDataWithDetection
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraViewModel : ViewModel() {
    private val _cameraInitialized = MutableLiveData<Boolean>()
    val cameraInitialized: LiveData<Boolean> = _cameraInitialized

    private val _captureResult = MutableLiveData<String?>()
    val captureResult: LiveData<String?> = _captureResult
    private val _nidData = MutableLiveData<Map<String, String>?>()
    val nidData: LiveData<Map<String, String>?> get() = _nidData
    lateinit var imageCapture: ImageCapture
        private set

    private lateinit var context: Context
    private val TAG = "CameraViewModel"

    fun initialize(context: Context, previewView: androidx.camera.view.PreviewView) {
        this.context = context

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // ✅ Get screen ratio dynamically
            val metrics = context.resources.displayMetrics
            val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

            val rotation = previewView.display.rotation

            val preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                preview.setSurfaceProvider(previewView.surfaceProvider)
                _cameraInitialized.value = true
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

       fun takePhoto(
        left: Float, top: Float, right: Float, bottom: Float,
        previewWidth: Int, previewHeight: Int
    ) {
        val photoFile = File(
            getOutputDirectory(),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )
        val originalFile = File(getOutputDirectory(), "original_${photoFile.name}")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                            ?: run {
                                Log.e(TAG, "Failed to load bitmap")
                                _captureResult.value = "Failed to load captured image"
                                return
                            }

                        val rotationDegrees = rotationDegreesFromSurface(imageCapture.targetRotation)
                        val rotatedBitmap = if (rotationDegrees != 0) {
                            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//                                .also { bitmap.recycle() }
                        } else bitmap

                        FileOutputStream(originalFile).use { out ->
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }

                        val (adjustedLeft, adjustedTop, adjustedRight, adjustedBottom) = when (rotationDegrees) {
                            90 -> listOf(top, previewWidth - right, bottom, previewWidth - left)
                            270 -> listOf(previewHeight - bottom, left, previewHeight - top, right)
                            180 -> listOf(previewWidth - right, previewHeight - bottom, previewWidth - left, previewHeight - top)
                            else -> listOf(left, top, right, bottom)
                        }

                        val normLeft = adjustedLeft.toFloat() / previewWidth
                        val normTop = adjustedTop.toFloat() / previewHeight
                        val normRight = adjustedRight.toFloat() / previewWidth
                        val normBottom = adjustedBottom.toFloat() / previewHeight

                        val cropLeft = (normLeft * rotatedBitmap.width).toInt()
                        val cropTop = (normTop * rotatedBitmap.height).toInt()
                        val cropRight = (normRight * rotatedBitmap.width).toInt()
                        val cropBottom = (normBottom * rotatedBitmap.height).toInt()

                        val safeLeft = cropLeft.coerceIn(0, rotatedBitmap.width - 1)
                        val safeTop = cropTop.coerceIn(0, rotatedBitmap.height - 1)
                        val safeWidth = (cropRight - cropLeft).coerceAtLeast(1).coerceAtMost(rotatedBitmap.width - safeLeft)
                        val safeHeight = (cropBottom - cropTop).coerceAtLeast(1).coerceAtMost(rotatedBitmap.height - safeTop)

                        val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, safeLeft, safeTop, safeWidth, safeHeight)

                        FileOutputStream(photoFile).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }


//                        viewModelScope.launch {
//                            _nidData.postValue(null) // clear old data immediately
//                            var nidData: Map<String, String>? = null
//                            try {
//                                nidData = extractNidDataWithDetection(croppedBitmap)
//                                Log.d(TAG, "NID Data: $nidData")
//                            } catch (e: Exception) {
//                                Log.e(TAG, "NID extraction failed: ${e.message}", e)
//                            }
//                            _nidData.postValue(nidData)
//                        }

                        // --- IMPORTANT: do OCR first (suspend) and set nidData synchronously ---
                        viewModelScope.launch {
                            try {
                                _nidData.value = null

                                // extractNidData is suspend — runs OCR and parsing
                                val nidData = try {
                                    extractNidData(croppedBitmap) // suspend function
                                } catch (e: Exception) {
                                    Log.e(TAG, "NID extraction failed", e)
                                    null
                                }

                                // set LiveData synchronously (we're on Main dispatcher)
                                nidData?.let {
                                    _nidData.value = it
                                    Log.d(TAG, "NID Data posted: $it")
                                } ?: run {
                                    Log.w(TAG, "NID Data is null or empty")
                                }

                                // only after nidData is set (or processing finished) set capture result
                                _captureResult.value = "Photo saved: ${photoFile.absolutePath}"
                            } catch (e: Exception) {
                                Log.e(TAG, "Processing coroutine failed: ${e.message}", e)
                                _captureResult.value = "Failed to process photo: ${e.message}"
                            }
                        }

//                        _captureResult.value = "Photo saved: ${photoFile.absolutePath}"
                    } catch (e: Exception) {
                        Log.e(TAG, "Cropping failed: ${e.message}", e)
                        _captureResult.value = "Failed to crop image: ${e.message}"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed: ${exception.message}", exception)
                    _captureResult.value = "Failed to save photo: ${exception.message}"
                }
            }
        )
    }

    fun clearCaptureResult() {
        _captureResult.value = null
    }

    private fun getOutputDirectory(): File {
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "NIDReader").apply { mkdirs() }
        }
        return mediaDir?.takeIf { it.exists() } ?: context.filesDir
    }

    private fun rotationDegreesFromSurface(rotation: Int): Int {
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    fun getStr() : String {
        return "ssjfsjf";
    }
}
