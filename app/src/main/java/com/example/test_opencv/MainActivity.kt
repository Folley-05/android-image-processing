package com.example.test_opencv


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var imageView: ImageView
    private var camera: Camera? = null

    private val YOUR_SERVER_PORT: Int = 3500
    private val YOUR_SERVER_ADDRESS: String ="192.168.43.201"

    // Initialise the view
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //  Initialise variable
        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()
        imageView=findViewById(R.id.imageView)

        // Check and request permissions if needed
        if (checkPermissions()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    //    Handle permissions
    private val PERMISSION_REQUEST_CODE = 1001
    // List of required permissions
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    //  Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    // Function to check if all required permissions are granted
    private fun checkPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // Handle permission request results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted → Start camera
                startCamera()
            } else {
                // Some permissions denied → Show a message and close the app
                Toast.makeText(this, "Permissions are required for the app to function.", Toast.LENGTH_LONG).show()
                // finish()
            }
        }
    }

    //  Function to start camera
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // initialise the camera process and add image analyzer
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // add image processing
            val imageAnalysis = ImageAnalysis.Builder()
                .build()

            // process the image
            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                try {

                    var matOrg= imageToMat(image)
                    matOrg= fixMatRotation(matOrg, previewView)
                    val bitmap= processAndDrawBorders(matOrg)

                    // Send to server
                    Log.d("DEBUG", "Now send image to server")
                    sendImageToServer(bitmap)

                    // Update UI on the main thread
                    runOnUiThread {
                        imageView.setImageBitmap(bitmap)
                        Log.d("DEBUG", "Object borders drawn")
                    }
                } catch(e: Exception) {
                    Log.e("CameraX", "Error processing image: ${e.message}")
                } finally {
                    image.close()
                }
            })

            try {
                Log.d("DEBUG", "Preview: $preview")
                Log.d("DEBUG", "ImageAnalysis: $imageAnalysis")
                Log.d("DEBUG", "CameraSelector: $cameraSelector")
                Log.d("DEBUG", "CameraProvider: $cameraProvider")
                cameraProvider.unbindAll()
                camera=cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview, imageAnalysis)
                preview.setSurfaceProvider(previewView.createSurfaceProvider(camera!!.cameraInfo))
            } catch (e: Exception) {
                Log.e("CameraX", "Error processing camera images ${e.message}")
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun sendImageToServer(bitmap: Bitmap) {
        Thread {
            try {
                val socket = Socket(YOUR_SERVER_ADDRESS, YOUR_SERVER_PORT)
                val outputStream: OutputStream = socket.getOutputStream()

                // Convert Bitmap to ByteArray
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                val byteArray = byteArrayOutputStream.toByteArray()

                // Send data through socket
                outputStream.write(byteArray)
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                Log.d("DEBUG", "Error sending image: ${e.message}")
            }
        }.start()
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_CODE = 1001

        init {
            System.loadLibrary("opencv_java4")
        }
    }
}