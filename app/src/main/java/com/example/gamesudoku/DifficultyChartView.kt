package com.example.gamesudoku

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import com.example.gamesudoku.SudokuGenerator.Difficulty

class DifficultyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    
    private var easyCount = 0
    private var mediumCount = 0
    private var hardCount = 0
    private var expertCount = 0
    
    private val easyColor = Color.parseColor("#4CAF50")
    private val mediumColor = Color.parseColor("#FFC107")
    private val hardColor = Color.parseColor("#FF9800")
    private val expertColor = Color.parseColor("#F44336")

    fun updateData(easy: Int, medium: Int, hard: Int, expert: Int = 0) {
        easyCount = easy
        mediumCount = medium
        hardCount = hard
        expertCount = expert
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val total = easyCount + mediumCount + hardCount + expertCount
        if (total == 0) {
            // Draw empty circle if no data
            paint.color = Color.LTGRAY
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width / 2f, height / 2f, min(width, height) / 2f - 4f, paint)
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 4f
        
        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        var startAngle = -90f // Start from top
        
        // Draw Easy segment (green)
        if (easyCount > 0) {
            val sweepAngle = (easyCount.toFloat() / total) * 360f
            paint.color = easyColor
            paint.style = Paint.Style.FILL
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
        
        // Draw Medium segment (yellow)
        if (mediumCount > 0) {
            val sweepAngle = (mediumCount.toFloat() / total) * 360f
            paint.color = mediumColor
            paint.style = Paint.Style.FILL
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
        
        // Draw Hard segment (orange)
        if (hardCount > 0) {
            val sweepAngle = (hardCount.toFloat() / total) * 360f
            paint.color = hardColor
            paint.style = Paint.Style.FILL
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
        
        // Draw Expert segment (red)
        if (expertCount > 0) {
            val sweepAngle = (expertCount.toFloat() / total) * 360f
            paint.color = expertColor
            paint.style = Paint.Style.FILL
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
        }
        
        // Draw border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(centerX, centerY, radius, paint)
    }
}
