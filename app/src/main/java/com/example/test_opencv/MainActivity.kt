package com.example.test_opencv


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var imageView: ImageView
    private var camera: Camera? = null

    // Initialise the view
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
//        Manifest.permission.WRITE_EXTERNAL_STORAGE
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
//                finish()
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
            // preview.setSurfaceProvider(previewView.surfaceProvider)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // add image processing
            val imageAnalysis = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                try {
                    var matOrg = imageToMat(image) // Check if this crashes
                    matOrg=fixMatRotation(matOrg)
                    val matInverted = Mat()
                    Core.bitwise_not(matOrg, matInverted)

                    val bitmap = Bitmap.createBitmap(matInverted.cols(), matInverted.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(matInverted, bitmap)

                    runOnUiThread {
                        imageView.setImageBitmap(bitmap)
                        Log.d("DEBUG", "ImageView flip applied")
                    }
                } catch (e: Exception) {
                    Log.e("CameraX", "Error processing image: ${e.message}")
                } finally {
                    image.close() // Make sure the image is closed
                    Log.d("DEBUG", "Image will be closed successfully")
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
                Log.e("CameraX", "Error binding use cases: ${e.message}")
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    //    Convert image from camera to mat format compatible with OpenCV
    private fun imageToMat(image: ImageProxy): Mat {
        val yBuffer = image.planes[0].buffer
        val ySize = yBuffer.remaining()
        val nv21 = ByteArray(ySize)
        yBuffer[nv21, 0, ySize]

        val yuv = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuv.put(0, 0, nv21)

        val mat = Mat()
        Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2RGB_NV21) // Convert YUV to RGB
        return mat
    }

    private fun fixMatRotation(matOrg: Mat): Mat {
        val mat: Mat
        when (previewView!!.display.rotation) {
            Surface.ROTATION_0 -> {
                mat = Mat(matOrg.cols(), matOrg.rows(), matOrg.type())
                Core.transpose(matOrg, mat)
                Core.flip(mat, mat, 1)
            }

            Surface.ROTATION_90 -> mat = matOrg
            Surface.ROTATION_270 -> {
                mat = matOrg
                Core.flip(mat, mat, -1)
            }

            else -> {
                mat = Mat(matOrg.cols(), matOrg.rows(), matOrg.type())
                Core.transpose(matOrg, mat)
                Core.flip(mat, mat, 1)
            }
        }
        return mat
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//    }

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_CODE = 1001

        init {
            System.loadLibrary("opencv_java4")
        }
    }
}