package com.limurse.objectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ResultView : View {
    private var mPaintRectangle: Paint? = null
    private var mPaintText: Paint? = null
    private var mResults: ArrayList<Result>? = null

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        mPaintRectangle = Paint()
        mPaintRectangle!!.color = Color.YELLOW
        mPaintText = Paint()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mResults == null) return
        for (result in mResults!!) {
            mPaintRectangle!!.strokeWidth = 5f
            mPaintRectangle!!.style = Paint.Style.STROKE
            canvas.drawRect(result.rect, mPaintRectangle!!)
            val mPath = Path()
            val mRectF = RectF(
                result.rect.left.toFloat(),
                result.rect.top.toFloat(),
                (result.rect.left + TEXT_WIDTH).toFloat(),
                (result.rect.top + TEXT_HEIGHT).toFloat()
            )
            mPath.addRect(mRectF, Path.Direction.CW)
            mPaintText!!.color = Color.MAGENTA
            canvas.drawPath(mPath, mPaintText!!)
            mPaintText!!.color = Color.WHITE
            mPaintText!!.strokeWidth = 0f
            mPaintText!!.style = Paint.Style.FILL
            mPaintText!!.textSize = 32f
            canvas.drawText(
                String.format(
                    "%s %.2f",
                    PrePostProcessor.mClasses[result.classIndex],
                    result.score
                ),
                (result.rect.left + TEXT_X).toFloat(),
                (result.rect.top + TEXT_Y).toFloat(),
                mPaintText!!
            )
        }
    }

    fun setResults(results: ArrayList<Result>?) {
        mResults = results
    }

    companion object {
        private const val TEXT_X = 40
        private const val TEXT_Y = 35
        private const val TEXT_WIDTH = 260
        private const val TEXT_HEIGHT = 50
    }
}