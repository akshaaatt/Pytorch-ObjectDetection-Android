package com.limurse.objectdetection.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.limurse.objectdetection.PrePostProcessor
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import com.limurse.objectdetection.PrePostProcessor.outputsToNMSPredictions
import org.pytorch.demo.objectdetection.R
import org.pytorch.torchvision.TensorImageUtils
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity(), Runnable {
    private var mImageIndex = 0
    private val mTestImages = arrayOf("test1.png", "test2.jpg", "test3.png")
    private var mImageView: ImageView? = null
    private var mResultView: ResultView? = null
    private var mButtonDetect: Button? = null
    private var mProgressBar: ProgressBar? = null
    private var mBitmap: Bitmap? = null
    private var mModule: Module? = null
    private var mImgScaleX = 0f
    private var mImgScaleY = 0f
    private var mIvScaleX = 0f
    private var mIvScaleY = 0f
    private var mStartX = 0f
    private var mStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
        setContentView(R.layout.activity_main)
        try {
            mBitmap = BitmapFactory.decodeStream(assets.open(mTestImages[mImageIndex]))
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
            finish()
        }
        mImageView = findViewById(R.id.imageView)
        mImageView?.setImageBitmap(mBitmap)
        mResultView = findViewById(R.id.resultView)
        mResultView?.visibility = View.INVISIBLE
        val buttonTest = findViewById<Button>(R.id.testButton)
        buttonTest.text = "Test Image 1/3"
        buttonTest.setOnClickListener {
            mResultView?.visibility = View.INVISIBLE
            mImageIndex = (mImageIndex + 1) % mTestImages.size
            buttonTest.text = String.format("Text Image %d/%d", mImageIndex + 1, mTestImages.size)
            try {
                mBitmap = BitmapFactory.decodeStream(assets.open(mTestImages[mImageIndex]))
                mImageView?.setImageBitmap(mBitmap)
            } catch (e: IOException) {
                Log.e("Object Detection", "Error reading assets", e)
                finish()
            }
        }
        val buttonSelect = findViewById<Button>(R.id.selectButton)
        buttonSelect.setOnClickListener {
            mResultView?.visibility = View.INVISIBLE
            val options = arrayOf<CharSequence>("Choose from Photos", "Take Picture", "Cancel")
            val builder = AlertDialog.Builder(this)
            builder.setTitle("New Test Image")
            builder.setItems(options) { dialog, item ->
                when (options[item]) {
                    "Take Picture" -> takePicture.launch(null)
                    "Choose from Photos" -> getContent.launch("image/*")
                    "Cancel" -> dialog.dismiss()
                }
            }
            builder.show()
        }
        val buttonLive = findViewById<Button>(R.id.liveButton)
        buttonLive.setOnClickListener {
            val intent = Intent(this@MainActivity, ObjectDetectionActivity::class.java)
            startActivity(intent)
        }
        mButtonDetect = findViewById(R.id.detectButton)
        mProgressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        mButtonDetect?.setOnClickListener {
            mButtonDetect?.isEnabled = false
            mProgressBar!!.visibility = ProgressBar.VISIBLE
            mButtonDetect?.text = getString(R.string.run_model)
            mImgScaleX = mBitmap!!.width.toFloat() / PrePostProcessor.mInputWidth
            mImgScaleY = mBitmap!!.height.toFloat() / PrePostProcessor.mInputHeight
            mIvScaleX = (when {
                mBitmap!!.width > mBitmap!!.height -> mImageView?.width!!.toFloat() / mBitmap!!.width
                else -> mImageView?.height!!.toFloat() / mBitmap!!.height
            })
            mIvScaleY = (when {
                mBitmap!!.height > mBitmap!!.width -> mImageView?.height!!.toFloat() / mBitmap!!.height
                else -> mImageView?.width!!.toFloat() / mBitmap!!.width
            })
            mStartX = (mImageView!!.width - mIvScaleX * mBitmap!!.width) / 2
            mStartY = (mImageView!!.height - mIvScaleY * mBitmap!!.height) / 2
            val thread = Thread(this@MainActivity)
            thread.start()
        }
        try {
            mModule = LiteModuleLoader.load(assetFilePath(applicationContext, "yolov5s.torchscript.ptl"))
            val br = BufferedReader(InputStreamReader(assets.open("classes.txt")))
            val classes: ArrayList<String> = ArrayList()
            br.useLines { lines ->
                lines.forEach {
                    classes.add(it)
                }
            }
            PrePostProcessor.mClasses = arrayOfNulls(classes.size)
            classes.toArray<String>(PrePostProcessor.mClasses)
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
            finish()
        }

    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let {
            val matrix = Matrix()
            matrix.postRotate(90.0f)
            mBitmap = Bitmap.createBitmap(
                it,
                0,
                0,
                it.width,
                it.height,
                matrix,
                true
            )
            mImageView?.setImageBitmap(mBitmap)
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
        uri?.let {
            contentResolver.query(it, filePathColumn, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                val picturePath = cursor.getString(columnIndex)
                mBitmap = BitmapFactory.decodeFile(picturePath)
                val matrix = Matrix()
                matrix.postRotate(90.0f)
                mBitmap = Bitmap.createBitmap(
                    mBitmap!!,
                    0,
                    0,
                    mBitmap!!.width,
                    mBitmap!!.height,
                    matrix,
                    true
                )
                mImageView?.setImageBitmap(mBitmap)
            }
        }
    }

    override fun run() {
        val resizedBitmap = Bitmap.createScaledBitmap(
            (mBitmap)!!,
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
        val results = outputsToNMSPredictions(
            outputs,
            mImgScaleX,
            mImgScaleY,
            mIvScaleX,
            mIvScaleY,
            mStartX,
            mStartY
        )
        runOnUiThread {
            mButtonDetect!!.isEnabled = true
            mButtonDetect!!.text = getString(R.string.detect)
            mProgressBar!!.visibility = ProgressBar.INVISIBLE
            mResultView!!.setResults(results)
            mResultView!!.invalidate()
            mResultView!!.visibility = View.VISIBLE
        }
    }

    companion object {
        @Throws(IOException::class)
        fun assetFilePath(context: Context, assetName: String?): String {
            val file = File(context.filesDir, assetName)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            context.assets.open((assetName)!!).use { `is` ->
                FileOutputStream(file).use { os ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while ((`is`.read(buffer).also { read = it }) != -1) {
                        os.write(buffer, 0, read)
                    }
                    os.flush()
                }
                return file.absolutePath
            }
        }
    }
}