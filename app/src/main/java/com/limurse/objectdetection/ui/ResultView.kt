package com.limurse.objectdetection.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.limurse.objectdetection.PrePostProcessor
import com.limurse.objectdetection.model.Result

class ResultView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val mPaintRectangle: Paint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val mPaintText: Paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 0f
        style = Paint.Style.FILL
        textSize = 32f
    }

    private var mResults: List<Result> = emptyList()

    // Preallocate Path and RectF objects
    private val mPath: Path = Path()
    private val mRectF: RectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mResults.forEach { result ->
            if (result.score < 0.3) return@forEach
            canvas.drawRect(result.rect, mPaintRectangle)
            mPath.reset()
            mRectF.set(
                result.rect.left.toFloat(),
                result.rect.top.toFloat(),
                (result.rect.left + TEXT_WIDTH).toFloat(),
                (result.rect.top + TEXT_HEIGHT).toFloat()
            )
            mPath.addRect(mRectF, Path.Direction.CW)
            mPaintText.color = Color.MAGENTA
            canvas.drawPath(mPath, mPaintText)
            mPaintText.color = Color.WHITE
            canvas.drawText(
                String.format(
                    "%s %.2f",
                    PrePostProcessor.mClasses[result.classIndex],
                    result.score
                ),
                (result.rect.left + TEXT_X).toFloat(),
                (result.rect.top + TEXT_Y).toFloat(),
                mPaintText
            )
        }
    }

    fun setResults(results: List<Result>?) {
        mResults = results ?: emptyList()
    }

    companion object {
        private const val TEXT_X = 40
        private const val TEXT_Y = 35
        private const val TEXT_WIDTH = 260
        private const val TEXT_HEIGHT = 50
    }
}
