package com.example.nid_reader_xml

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val imageView = findViewById<ImageView>(R.id.imageViewPreview)
        val textView = findViewById<TextView>(R.id.textViewMsg)
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        if (imagePath != null) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                imageView.setImageURI(android.net.Uri.fromFile(imageFile))
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            }
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        }

        val nidData = intent.getSerializableExtra("NID_DATA") as? HashMap<String, String>
        if (nidData != null) {
            val name = nidData["name"] ?: "N/A"
            val dob = nidData["dob"] ?: "N/A"
//            textView.text = "Name: $name\nDOB: $dob\nPath: $imagePath"
            textView.text = nidData.toString()
        } else {
            textView.text = "No NID data found"
        }
    }
}