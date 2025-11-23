package com.artashes.sudoku

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.*

class MysticForestQuestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val questPath = Path()
    private val nodePositions = mutableListOf<PointF>()
    private val nodeStates = mutableListOf<NodeState>()
    
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var pathProgress = 0f
    private var glowAnimation = 0f
    
    private var onNodeClickListener: ((Int) -> Unit)? = null
    
    data class NodeState(
        val isCompleted: Boolean,
        val isUnlocked: Boolean,
        val difficulty: String,
        val levelId: Int
    )
    
    init {
        setupPaints()
        startGlowAnimation()
    }
    
    private fun setupPaints() {
        // Path paint - glowing green line
        pathPaint.apply {
            color = Color.parseColor("#4CAF50")
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Node paint - glowing circles
        nodePaint.apply {
            style = Paint.Style.FILL
            strokeWidth = 4f
        }
        
        // Glow paint for magical effects
        glowPaint.apply {
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Text paint
        textPaint.apply {
            color = Color.parseColor("#FFD700")
            textSize = 16f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    
    private fun startGlowAnimation() {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 2000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            glowAnimation = animation.animatedValue as Float
            invalidate()
        }
        animator.start()
    }
    
    fun setQuestData(levels: List<QuestLevel>) {
        nodeStates.clear()
        levels.forEach { level ->
            nodeStates.add(NodeState(
                isCompleted = level.isCompleted,
                isUnlocked = level.isUnlocked,
                difficulty = when (level.difficulty) {
                    SudokuGenerator.Difficulty.EASY -> "EASY"
                    SudokuGenerator.Difficulty.MEDIUM -> "MEDIUM"
                    SudokuGenerator.Difficulty.HARD -> "HARD"
                    SudokuGenerator.Difficulty.EXPERT -> "EXPERT"
                },
                levelId = level.id
            ))
        }
        calculateNodePositions()
        invalidate()
    }
    
    fun setOnNodeClickListener(listener: (Int) -> Unit) {
        onNodeClickListener = listener
    }
    
    private fun calculateNodePositions() {
        nodePositions.clear()
        val numNodes = nodeStates.size
        
        // Create winding path positions
        for (i in 0 until numNodes) {
            val progress = i.toFloat() / (numNodes - 1)
            val x = screenWidth * 0.5f + sin(progress * PI.toFloat() * 2) * screenWidth * 0.3f
            val y = screenHeight * 0.2f + progress * screenHeight * 0.6f
            nodePositions.add(PointF(x, y))
        }
        
        // Create path
        questPath.reset()
        if (nodePositions.isNotEmpty()) {
            questPath.moveTo(nodePositions[0].x, nodePositions[0].y)
            for (i in 1 until nodePositions.size) {
                val prev = nodePositions[i - 1]
                val curr = nodePositions[i]
                
                // Create smooth curve
                val controlX = (prev.x + curr.x) / 2
                val controlY = prev.y + (curr.y - prev.y) * 0.3f
                questPath.quadTo(controlX, controlY, curr.x, curr.y)
            }
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
        screenHeight = h.toFloat()
        calculateNodePositions()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw forest background
        drawForestBackground(canvas)
        
        // Draw quest path
        drawQuestPath(canvas)
        
        // Draw level nodes
        drawLevelNodes(canvas)
        
        // Draw ancient tree (boss node)
        drawAncientTree(canvas)
        
        // Draw fireflies
        drawFireflies(canvas)
    }
    
    private fun drawForestBackground(canvas: Canvas) {
        // Forest gradient background
        val gradient = LinearGradient(
            0f, 0f, 0f, screenHeight,
            Color.parseColor("#1B5E20"), // Dark green
            Color.parseColor("#2E7D32"), // Medium green
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)
        paint.shader = null
        
        // Draw tree silhouettes
        drawTreeSilhouettes(canvas)
        
        // Draw mist/fog
        drawMist(canvas)
    }
    
    private fun drawTreeSilhouettes(canvas: Canvas) {
        paint.color = Color.parseColor("#0D4F14")
        paint.style = Paint.Style.FILL
        
        // Draw several tree silhouettes
        for (i in 0..8) {
            val x = screenWidth * (i / 8f)
            val treeWidth = 60f + (i % 3) * 20f
            val treeHeight = 200f + (i % 2) * 100f
            val y = screenHeight - treeHeight * 0.3f
            
            // Tree trunk
            canvas.drawRect(x - 10f, y, x + 10f, screenHeight, paint)
            
            // Tree foliage (triangular)
            val path = Path()
            path.moveTo(x - treeWidth/2, y)
            path.lineTo(x + treeWidth/2, y)
            path.lineTo(x, y - treeHeight)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
    
    private fun drawMist(canvas: Canvas) {
        paint.color = Color.parseColor("#80FFFFFF")
        paint.style = Paint.Style.FILL
        paint.maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
        
        // Draw mist layers
        for (i in 0..3) {
            val y = screenHeight * 0.3f + i * 100f
            val alpha = (100 - i * 20).coerceAtLeast(20)
            paint.alpha = alpha
            canvas.drawRect(0f, y, screenWidth, y + 80f, paint)
        }
        paint.maskFilter = null
        paint.alpha = 255
    }
    
    private fun drawQuestPath(canvas: Canvas) {
        // Draw glowing path
        pathPaint.color = Color.parseColor("#4CAF50")
        pathPaint.alpha = (150 + glowAnimation * 50).toInt()
        canvas.drawPath(questPath, pathPaint)
    }
    
    private fun drawLevelNodes(canvas: Canvas) {
        nodePositions.forEachIndexed { index, position ->
            if (index < nodeStates.size) {
                val state = nodeStates[index]
                drawLevelNode(canvas, position, state, index)
            }
        }
    }
    
    private fun drawLevelNode(canvas: Canvas, position: PointF, state: NodeState, index: Int) {
        val nodeRadius = 40f
        val glowRadius = nodeRadius + 20f * glowAnimation
        
        // Node color based on state
        val nodeColor = when {
            state.isCompleted -> Color.parseColor("#4CAF50") // Green
            state.isUnlocked -> Color.parseColor("#FF9800") // Orange
            else -> Color.parseColor("#9E9E9E") // Gray
        }
        
        // Draw glow
        glowPaint.color = nodeColor
        glowPaint.alpha = (80 + glowAnimation * 40).toInt()
        canvas.drawCircle(position.x, position.y, glowRadius, glowPaint)
        
        // Draw node
        nodePaint.color = nodeColor
        canvas.drawCircle(position.x, position.y, nodeRadius, nodePaint)
        
        // Draw leaf icon inside node
        drawLeafIcon(canvas, position, nodeRadius * 0.6f)
        
        // Draw difficulty text
        textPaint.color = Color.parseColor("#FFD700")
        textPaint.textSize = 14f
        canvas.drawText(state.difficulty, position.x, position.y + nodeRadius + 25f, textPaint)
        
        // Draw level number
        textPaint.color = Color.WHITE
        textPaint.textSize = 12f
        canvas.drawText("${index + 1}", position.x, position.y + 5f, textPaint)
    }
    
    private fun drawLeafIcon(canvas: Canvas, position: PointF, size: Float) {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        
        val leafPath = Path()
        leafPath.moveTo(position.x, position.y - size)
        leafPath.quadTo(position.x + size * 0.5f, position.y - size * 0.3f, position.x + size * 0.3f, position.y)
        leafPath.quadTo(position.x + size * 0.5f, position.y + size * 0.3f, position.x, position.y + size)
        leafPath.quadTo(position.x - size * 0.5f, position.y + size * 0.3f, position.x - size * 0.3f, position.y)
        leafPath.quadTo(position.x - size * 0.5f, position.y - size * 0.3f, position.x, position.y - size)
        leafPath.close()
        
        canvas.drawPath(leafPath, paint)
    }
    
    private fun drawAncientTree(canvas: Canvas) {
        val treeX = screenWidth * 0.8f
        val treeY = screenHeight * 0.3f
        val treeSize = 120f
        
        // Tree glow
        glowPaint.color = Color.parseColor("#FFD700")
        glowPaint.alpha = (100 + glowAnimation * 50).toInt()
        canvas.drawCircle(treeX, treeY, treeSize + 30f * glowAnimation, glowPaint)
        
        // Tree trunk
        paint.color = Color.parseColor("#8B4513")
        paint.style = Paint.Style.FILL
        canvas.drawRect(treeX - 20f, treeY, treeX + 20f, treeY + treeSize, paint)
        
        // Tree foliage (glowing)
        paint.color = Color.parseColor("#FFD700")
        val foliagePath = Path()
        foliagePath.moveTo(treeX - treeSize/2, treeY)
        foliagePath.lineTo(treeX + treeSize/2, treeY)
        foliagePath.lineTo(treeX, treeY - treeSize)
        foliagePath.close()
        canvas.drawPath(foliagePath, paint)
        
        // Rune symbol
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(treeX, treeY, 15f, paint)
        canvas.drawLine(treeX - 10f, treeY, treeX + 10f, treeY, paint)
        canvas.drawLine(treeX, treeY - 10f, treeX, treeY + 10f, paint)
        
        // "BOSS" text
        textPaint.color = Color.parseColor("#FFD700")
        textPaint.textSize = 16f
        textPaint.isFakeBoldText = true
        canvas.drawText("BOSS", treeX, treeY + treeSize + 30f, textPaint)
    }
    
    private fun drawFireflies(canvas: Canvas) {
        paint.color = Color.parseColor("#4CAF50")
        paint.style = Paint.Style.FILL
        paint.alpha = (100 + glowAnimation * 50).toInt()
        
        // Draw scattered fireflies
        for (i in 0..15) {
            val x = screenWidth * (i / 15f) + sin(glowAnimation * PI.toFloat() * 2 + i) * 50f
            val y = screenHeight * 0.2f + (i % 5) * 100f + cos(glowAnimation * PI.toFloat() * 2 + i) * 30f
            canvas.drawCircle(x, y, 3f, paint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y
                
                // Check if any node was touched
                nodePositions.forEachIndexed { index, position ->
                    val distance = sqrt((touchX - position.x).pow(2) + (touchY - position.y).pow(2))
                    if (distance <= 50f && index < nodeStates.size) {
                        val state = nodeStates[index]
                        if (state.isUnlocked) {
                            onNodeClickListener?.invoke(state.levelId)
                            return true
                        }
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
