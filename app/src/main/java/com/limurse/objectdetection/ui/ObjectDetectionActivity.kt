package com.limurse.objectdetection.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.limurse.objectdetection.PrePostProcessor
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import com.limurse.objectdetection.ui.ObjectDetectionActivity.AnalysisResult
import com.limurse.objectdetection.PrePostProcessor.outputsToNMSPredictions
import com.limurse.objectdetection.model.Result
import org.pytorch.demo.objectdetection.R
import org.pytorch.torchvision.TensorImageUtils
import java.io.ByteArrayOutputStream
import java.io.IOException

class ObjectDetectionActivity : AbstractCameraXActivity<AnalysisResult?>() {
    private var mModule: Module? = null
    private var mResultView: ResultView? = null

    data class AnalysisResult(val mResults: ArrayList<Result>)

    override val contentViewLayoutId: Int
        get() = R.layout.activity_object_detection
    override val cameraPreviewView: PreviewView
        get() {
            mResultView = findViewById(R.id.resultView)
            return findViewById(R.id.cameraPreviewView)
        }

    override fun applyToUiAnalyzeImageResult(result: AnalysisResult?) {
        Log.d("ObjectDetectionActivity", "Applying results to UI ${result?.mResults}")
        mResultView?.setResults(result?.mResults)
        mResultView?.invalidate()
    }

    private fun imgToBitmap(image: Image?): Bitmap {
        val planes = image!!.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    @WorkerThread
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyzeImage(image: ImageProxy, rotationDegrees: Int): AnalysisResult? {
        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(
                    MainActivity.assetFilePath(
                        applicationContext,
                        "yolov5s.torchscript.ptl"
                    )
                )
            }
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
            return null
        }
        var bitmap = imgToBitmap(image.image)
        val matrix = Matrix()
        matrix.postRotate(90.0f)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            PrePostProcessor.mInputWidth,
            PrePostProcessor.mInputHeight,
            true
        )
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            PrePostProcessor.NO_MEAN_RGB,
            PrePostProcessor.NO_STD_RGB
        )
        val outputTuple = mModule!!.forward(IValue.from(inputTensor)).toTuple()
        val outputTensor = outputTuple[0].toTensor()
        val outputs = outputTensor.dataAsFloatArray
        val imgScaleX = bitmap.width.toFloat() / PrePostProcessor.mInputWidth
        val imgScaleY = bitmap.height.toFloat() / PrePostProcessor.mInputHeight
        val ivScaleX = mResultView!!.width.toFloat() / bitmap.width
        val ivScaleY = mResultView!!.height.toFloat() / bitmap.height
        val results =
            outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0f, 0f)
        return AnalysisResult(results)
    }
}