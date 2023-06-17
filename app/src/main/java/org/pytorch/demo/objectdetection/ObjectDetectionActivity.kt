package org.pytorch.demo.objectdetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewStub
import androidx.annotation.WorkerThread
import androidx.camera.core.ImageProxy
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.demo.objectdetection.ObjectDetectionActivity.AnalysisResult
import org.pytorch.demo.objectdetection.PrePostProcessor.outputsToNMSPredictions
import org.pytorch.torchvision.TensorImageUtils
import java.io.ByteArrayOutputStream
import java.io.IOException

class ObjectDetectionActivity : AbstractCameraXActivity<AnalysisResult?>() {
    private var mModule: Module? = null
    private var mResultView: ResultView? = null

    class AnalysisResult(val mResults: ArrayList<Result>)

    override val contentViewLayoutId: Int
        protected get() = R.layout.activity_object_detection
    override val cameraPreviewTextureView: TextureView
        protected get() {
            mResultView = findViewById(R.id.resultView)
            return (findViewById<View>(R.id.object_detection_texture_view_stub) as ViewStub)
                .inflate()
                .findViewById(R.id.object_detection_texture_view)
        }

    override fun applyToUiAnalyzeImageResult(result: AnalysisResult?) {
        mResultView!!.setResults(result?.mResults)
        mResultView!!.invalidate()
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
    override fun analyzeImage(image: ImageProxy?, rotationDegrees: Int): AnalysisResult? {
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
        var bitmap = imgToBitmap(image!!.image)
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