package com.example.test_opencv


import android.graphics.Bitmap
import android.view.Surface
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import org.opencv.core.*
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream

// Converts ImageProxy to OpenCV Mat
fun imageToMat(image: ImageProxy): Mat {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer[nv21, 0, ySize]
    vBuffer[nv21, ySize, vSize]
    uBuffer[nv21, ySize + vSize, uSize]
    val yuv = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
    yuv.put(0, 0, nv21)
    val mat = Mat()
    Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2RGB_NV21, 3)
    return mat
}

//    Rotate the image
fun fixMatRotation(matOrg: Mat, previewView: PreviewView): Mat {
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

// Inverts colors in  image
fun invertColorOnImage( matOrg: Mat): Bitmap {
        val matInverted = Mat()

        // Apply color inversion
        Core.bitwise_not(matOrg, matInverted)

        // Convert Mat to Bitmap
        val bitmap = Bitmap.createBitmap(matInverted.cols(), matInverted.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matInverted, bitmap)

        return bitmap
}

// Draws borders around detected objects
fun processAndDrawBorders(matOrg: Mat): Bitmap {

        val matGray = Mat()
        val matEdges = Mat()
        val matContours = matOrg.clone()

        // Convert to grayscale
        Imgproc.cvtColor(matOrg, matGray, Imgproc.COLOR_RGB2GRAY)

        // Apply Canny edge detection
        Imgproc.Canny(matGray, matEdges, 100.0, 200.0)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(matEdges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // Draw contours (borders)
        Imgproc.drawContours(matContours, contours, -1, Scalar(0.0, 255.0, 0.0), 3)

        // Convert Mat to Bitmap
        val bitmap = Bitmap.createBitmap(matContours.cols(), matContours.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(matContours, bitmap)

        return bitmap

}

fun matToByteArray(mat: Mat): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bitmap)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream) // Compress to reduce size
    return byteArrayOutputStream.toByteArray()
}
