package com.example.nid_reader_xml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NidUtils {

    suspend fun extractNidDataWithDetection(bitmap: Bitmap): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val fullText = runTextRecognition(bitmap)
        Log.d("NidUtils", "extractNidDataWithDetection: $fullText")
        val lines = fullText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        // Extract name
        val name = extractName(fullText, results)
        results["name"] = name

        // Extract dob
        val dob = extractDob(fullText)
        if (dob != null) results["dob"] = dob

        // Extract NID number
        var nidFound: String? = null
        var isSmartNid = false

        val idNoRegex = Regex("ID NO[:\\s]+(\\b\\d{9,10}\\b)")
        nidFound = idNoRegex.find(fullText)?.groupValues?.get(1)
        if (nidFound != null) {
            isSmartNid = false
        } else {
            val nidNoRegex = Regex("NID No[.:\\s]+(\\b\\d{3}\\s\\d{3}\\s\\d{4}\\b)")
            nidFound = nidNoRegex.find(fullText)?.groupValues?.get(1)
            if (nidFound != null) {
                isSmartNid = true
            } else {
                val nidIndex = lines.indexOfFirst {
                    it.matches(Regex("NID No[.:\\s]*")) || it.matches(Regex("ID NO[:\\s]*"))
                }
                if (nidIndex != -1 && nidIndex < lines.size - 1) {
                    val nextLine = lines[nidIndex + 1]
                    val smartNidMatch = Regex("\\b\\d{3}\\s\\d{3}\\s\\d{4}\\b").find(nextLine)
                    if (smartNidMatch != null) {
                        nidFound = smartNidMatch.value
                        isSmartNid = true
                    } else {
                        val nonSmartNidMatch = Regex("\\b\\d{9,10}\\b").find(nextLine)
                        nidFound = nonSmartNidMatch?.value
                        if (nidFound != null) isSmartNid = false
                    }
                }
            }
        }

        if (nidFound != null) {
            results["nid"] = nidFound
            results["card_type"] = if (isSmartNid) "Smart NID" else "Non-Smart NID"
        }

        return results
    }

    private fun extractName(fullText: String, results: MutableMap<String, String>): String {
        val ignoreKeywords = listOf(
            "government", "people", "republic", "bangladesh",
            "date of birth", "smart nid", "national", "id", "name"
        )
        val nameRegex = Regex("Name[:\\s]+([A-Za-z\\s.]+)")
        val name = nameRegex.find(fullText)?.groupValues?.get(1)?.trim()
        return if (name != null && !ignoreKeywords.any { keyword -> name.contains(keyword, ignoreCase = true) }) {
            name
        } else {
            ""
        }
    }

    suspend fun runTextRecognition(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                cont.resume(visionText.text) {}
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    fun extractDob(text: String): String? {
        val dobRegex = Regex("(\\d{2}[-/ ]\\d{2}[-/ ]\\d{4}|\\d{2} [A-Za-z]{3} \\d{4})")
        return dobRegex.find(text)?.value
    }
}
