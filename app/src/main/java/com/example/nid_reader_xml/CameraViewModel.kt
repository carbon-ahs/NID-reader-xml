package com.example.nid_reader_xml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel : ViewModel() {
    private val _cameraInitialized = MutableLiveData<Boolean>()
    val cameraInitialized: LiveData<Boolean> = _cameraInitialized

    private val _captureResult = MutableLiveData<String?>()
    val captureResult: LiveData<String?> = _captureResult

    lateinit var imageCapture: ImageCapture
        private set

    private lateinit var context: Context
    private val TAG = "CameraViewModel"

    fun initialize(context: Context) {
        this.context = context
        imageCapture = ImageCapture.Builder()
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build()
        _cameraInitialized.value = true
    }

    fun takePhoto(left: Float, top: Float, right: Float, bottom: Float, previewWidth: Int, previewHeight: Int) {
        val photoFile = File(
            getOutputDirectory(),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )
        val originalFile = File(getOutputDirectory(), "original_${photoFile.name}")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        // Load the captured image
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        if (bitmap == null) {
                            Log.e(TAG, "Failed to load bitmap from ${photoFile.absolutePath}")
                            _captureResult.value = "Failed to load captured image"
                            return
                        }

                        // Get rotation from ImageCapture
                        val rotationDegrees = imageCapture.targetRotation
                        Log.d(TAG, "Preview: ${previewWidth}x${previewHeight}")
                        Log.d(TAG, "Bitmap (raw): ${bitmap.width}x${bitmap.height}")
                        Log.d(TAG, "Overlay Rect: left=$left, top=$top, right=$right, bottom=$bottom")
                        Log.d(TAG, "Rotation: $rotationDegrees degrees")

                        // Save original image for debugging
                        FileOutputStream(originalFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        Log.d(TAG, "Original image saved to ${originalFile.absolutePath}")

                        // Rotate bitmap if needed
                        val rotatedBitmap = if (rotationDegrees != 0) {
                            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            bitmap.recycle()
                            rotated
                        } else {
                            bitmap
                        }
                        Log.d(TAG, "Bitmap (after rotation): ${rotatedBitmap.width}x${rotatedBitmap.height}")

                        // Adjust coordinates for rotation
                        val (adjustedLeft, adjustedTop, adjustedRight, adjustedBottom) = when (rotationDegrees) {
                            90 -> listOf(top, previewWidth - right, bottom, previewWidth - left)
                            270 -> listOf(previewHeight - bottom, left, previewHeight - top, right)
                            180 -> listOf(previewWidth - right, previewHeight - bottom, previewWidth - left, previewHeight - top)
                            else -> listOf(left, top, right, bottom)
                        }
                        Log.d(TAG, "Adjusted Rect: left=$adjustedLeft, top=$adjustedTop, right=$adjustedRight, bottom=$adjustedBottom")

                        // Calculate scaling factors considering fitCenter
                        val previewAspectRatio = previewWidth.toFloat() / previewHeight
                        val bitmapAspectRatio = rotatedBitmap.width.toFloat() / rotatedBitmap.height
                        val scaleX = rotatedBitmap.width.toFloat() / previewWidth
                        val scaleY = rotatedBitmap.height.toFloat() / previewHeight
                        val effectivePreviewWidth = if (previewAspectRatio > bitmapAspectRatio) {
                            (previewHeight * bitmapAspectRatio).toInt().coerceAtMost(previewWidth)
                        } else {
                            previewWidth
                        }
                        val effectivePreviewHeight = if (previewAspectRatio > bitmapAspectRatio) {
                            previewHeight
                        } else {
                            (previewWidth / bitmapAspectRatio).toInt().coerceAtMost(previewHeight)
                        }
                        Log.d(TAG, "Effective Preview: ${effectivePreviewWidth}x${effectivePreviewHeight}")

                        // Map overlay coordinates to bitmap
                        val cropLeft = ((adjustedLeft / previewWidth) * effectivePreviewWidth * (scaleX)).toInt().coerceIn(0, rotatedBitmap.width)
                        val cropTop = ((adjustedTop / previewHeight) * effectivePreviewHeight * (scaleY)).toInt().coerceIn(0, rotatedBitmap.height)
                        val cropWidth = (((adjustedRight - adjustedLeft) / previewWidth) * effectivePreviewWidth * (scaleX)).toInt().coerceIn(0, rotatedBitmap.width - cropLeft)
                        val cropHeight = (((adjustedBottom - adjustedTop) / previewHeight) * effectivePreviewHeight * (scaleY)).toInt().coerceIn(0, rotatedBitmap.height - cropTop)

                        // Log expected vs. actual crop bounds
                        Log.d(TAG, "Expected crop: left=${(adjustedLeft / previewWidth) * effectivePreviewWidth * scaleX}, " +
                                "top=${(adjustedTop / previewHeight) * effectivePreviewHeight * scaleY}, " +
                                "width=${((adjustedRight - adjustedLeft) / previewWidth) * effectivePreviewWidth * scaleX}, " +
                                "height=${((adjustedBottom - adjustedTop) / previewHeight) * effectivePreviewHeight * scaleY}")
                        Log.d(TAG, "Actual crop bounds: left=$cropLeft, top=$cropTop, width=$cropWidth, height=$cropHeight")

                        // Crop the bitmap
                        val croppedBitmap = Bitmap.createBitmap(
                            rotatedBitmap,
                            cropLeft,
                            cropTop,
                            cropWidth,
                            cropHeight
                        )

                        // Save the cropped bitmap
                        FileOutputStream(photoFile).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        Log.d(TAG, "Cropped image saved to ${photoFile.absolutePath}")
                        croppedBitmap.recycle()
                        rotatedBitmap.recycle()

                        _captureResult.value = "Photo saved: ${photoFile.absolutePath}"
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
        return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
    }
}