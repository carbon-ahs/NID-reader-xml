package com.example.nid_reader_xml

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views using findViewById
        val textViewCounter = findViewById<TextView>(R.id.textViewCounter)
        val buttonIncrement = findViewById<Button>(R.id.buttonIncrement)
        val buttonOpenCamera = findViewById<Button>(R.id.buttonOpenCamera)

        // Observe LiveData from ViewModel for counter
        viewModel.counter.observe(this) { count ->
            textViewCounter.text = count.toString()
        }

        // Set click listener for increment button
        buttonIncrement.setOnClickListener {
            viewModel.incrementCounter()
        }

        // Set click listener for camera button to start CameraActivity
        buttonOpenCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }
}