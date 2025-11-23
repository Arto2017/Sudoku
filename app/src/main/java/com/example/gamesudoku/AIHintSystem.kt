package com.artashes.sudoku

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

/**
 * AI-powered hint system that provides intelligent hints using MCP
 * Integrates with existing SudokuBoardView and game activities
 */
class AIHintSystem(private val context: Context) {
    
    companion object {
        private const val TAG = "AIHintSystem"
        private const val MAX_HINTS_PER_GAME = 3
        private const val HINT_COOLDOWN_MS = 2000L
    }
    
    private val mcpService = MCPService(context)
    private val embeddedServer = EmbeddedMCPServer(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var hintsUsed = 0
    private var lastHintTime = 0L
    private var currentHint: AIHint? = null
    private var isServerStarted = false
    
    // Callbacks for UI updates
    private var onHintReceived: ((AIHint) -> Unit)? = null
    private var onHintError: ((String) -> Unit)? = null
    private var onHintLoading: (() -> Unit)? = null
    
    /**
     * Start the embedded MCP server automatically
     */
    fun startEmbeddedServer() {
        if (!isServerStarted) {
            embeddedServer.start()
            isServerStarted = true
            Log.d(TAG, "Embedded MCP server started automatically")
        }
    }
    
    /**
     * Request an AI-powered hint for the current board state
     */
    fun requestHint(
        boardState: Array<IntArray>, 
        boardSize: Int,
        onHint: (AIHint) -> Unit,
        onError: (String) -> Unit,
        onLoading: () -> Unit
    ) {
        // Ensure embedded server is running
        startEmbeddedServer()
        // Check hint limits
        if (hintsUsed >= MAX_HINTS_PER_GAME) {
            onError("Maximum hints reached for this game")
            return
        }
        
        // Check cooldown
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHintTime < HINT_COOLDOWN_MS) {
            onError("Please wait before requesting another hint")
            return
        }
        
        // Set callbacks
        onHintReceived = onHint
        onHintError = onError
        onHintLoading = onLoading
        
        // Show loading state
        onLoading()
        
        // Request hint from MCP service
        scope.launch {
            try {
                val hint = mcpService.getSmartHint(boardState, boardSize)
                if (hint != null) {
                    hintsUsed++
                    lastHintTime = currentTime
                    currentHint = hint
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        onHint(hint)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Unable to generate hint at this time")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting hint", e)
                withContext(Dispatchers.Main) {
                    onError("Failed to get hint: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Analyze a mistake and provide learning feedback
     */
    fun analyzeMistake(
        boardState: Array<IntArray>,
        move: Move,
        boardSize: Int,
        onAnalysis: (MistakeAnalysis) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val analysis = mcpService.analyzeMistake(boardState, move, boardSize)
                if (analysis != null) {
                    withContext(Dispatchers.Main) {
                        onAnalysis(analysis)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Unable to analyze mistake")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing mistake", e)
                withContext(Dispatchers.Main) {
                    onError("Failed to analyze mistake: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Get personalized learning recommendations
     */
    fun getLearningRecommendations(
        performanceData: PerformanceData,
        onRecommendations: (LearningRecommendations) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val recommendations = mcpService.getLearningRecommendations(performanceData)
                if (recommendations != null) {
                    withContext(Dispatchers.Main) {
                        onRecommendations(recommendations)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Unable to get learning recommendations")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting learning recommendations", e)
                withContext(Dispatchers.Main) {
                    onError("Failed to get recommendations: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Generate a custom puzzle with AI
     */
    fun generateCustomPuzzle(
        difficulty: String,
        boardSize: Int,
        preferences: PuzzlePreferences? = null,
        onPuzzle: (CustomPuzzle) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val puzzle = mcpService.generateCustomPuzzle(difficulty, boardSize, preferences)
                if (puzzle != null) {
                    withContext(Dispatchers.Main) {
                        onPuzzle(puzzle)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Unable to generate custom puzzle")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating custom puzzle", e)
                withContext(Dispatchers.Main) {
                    onError("Failed to generate puzzle: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Get current hint information
     */
    fun getCurrentHint(): AIHint? = currentHint
    
    /**
     * Get hints used count
     */
    fun getHintsUsed(): Int = hintsUsed
    
    /**
     * Get hints remaining
     */
    fun getHintsRemaining(): Int = MAX_HINTS_PER_GAME - hintsUsed
    
    /**
     * Check if hint is available (not on cooldown and under limit)
     */
    fun isHintAvailable(): Boolean {
        val currentTime = System.currentTimeMillis()
        return hintsUsed < MAX_HINTS_PER_GAME && 
               (currentTime - lastHintTime) >= HINT_COOLDOWN_MS
    }
    
    /**
     * Reset hint system for new game
     */
    fun resetForNewGame() {
        hintsUsed = 0
        lastHintTime = 0L
        currentHint = null
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
        mcpService.cleanup()
        if (isServerStarted) {
            embeddedServer.stop()
            isServerStarted = false
        }
    }
}

/**
 * Enhanced hint dialog for AI hints
 */
class AIHintDialog {
    
    companion object {
        fun showHint(
            context: Context,
            hint: AIHint,
            onApply: () -> Unit,
            onDismiss: () -> Unit
        ) {
            val dialog = android.app.AlertDialog.Builder(context)
                .setTitle("AI Hint: ${hint.technique}")
                .setMessage(buildHintMessage(hint))
                .setPositiveButton("Apply Hint") { _, _ ->
                    onApply()
                }
                .setNegativeButton("Dismiss") { _, _ ->
                    onDismiss()
                }
                .setCancelable(true)
                .create()
            
            dialog.setOnCancelListener {
                onDismiss()
            }
            
            dialog.show()
        }
        
        private fun buildHintMessage(hint: AIHint): String {
            val confidence = (hint.confidence * 100).toInt()
            val message = StringBuilder()
            
            message.append("${hint.explanation}\n\n")
            
            if (hint.cell != null) {
                message.append("📍 Focus on cell (${hint.cell.first + 1}, ${hint.cell.second + 1})\n")
            }
            
            message.append("🎯 Technique: ${hint.technique}\n")
            message.append("📊 Difficulty: ${hint.difficulty}\n")
            message.append("🎲 Confidence: $confidence%")
            
            return message.toString()
        }
    }
}

/**
 * Mistake analysis dialog
 */
class MistakeAnalysisDialog {
    
    companion object {
        fun showAnalysis(
            context: Context,
            analysis: MistakeAnalysis,
            onDismiss: () -> Unit
        ) {
            val dialog = android.app.AlertDialog.Builder(context)
                .setTitle("Mistake Analysis")
                .setMessage(buildAnalysisMessage(analysis))
                .setPositiveButton("Got it!") { _, _ ->
                    onDismiss()
                }
                .setCancelable(true)
                .create()
            
            dialog.setOnCancelListener {
                onDismiss()
            }
            
            dialog.show()
        }
        
        private fun buildAnalysisMessage(analysis: MistakeAnalysis): String {
            val confidence = (analysis.confidence * 100).toInt()
            val message = StringBuilder()
            
            message.append("❌ ${analysis.explanation}\n\n")
            message.append("✅ Correct value: ${analysis.correctValue}\n")
            message.append("💡 Learning tip: ${analysis.learningTip}\n")
            message.append("🎯 Technique: ${analysis.technique}\n")
            message.append("🎲 Confidence: $confidence%")
            
            return message.toString()
        }
    }
}
