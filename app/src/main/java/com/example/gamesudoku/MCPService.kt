package com.artashes.sudoku

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * MCP (Model Context Protocol) Service for AI-powered Sudoku features
 * Provides intelligent hints, puzzle analysis, and learning assistance
 */
class MCPService(private val context: Context) {
    
    companion object {
        private const val TAG = "MCPService"
        // Use embedded server - no external server needed!
        private const val MCP_SERVER_URL = "http://localhost:8080/mcp"
        private const val TIMEOUT_MS = 5000
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Get an intelligent hint for the current board state
     */
    suspend fun getSmartHint(boardState: Array<IntArray>, boardSize: Int): AIHint? {
        return try {
            val request = JSONObject().apply {
                put("method", "get_smart_hint")
                put("board_state", flattenBoard(boardState))
                put("board_size", boardSize)
                put("timestamp", System.currentTimeMillis())
            }
            
            val response = makeMCPRequest(request)
            parseHintResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting smart hint", e)
            null
        }
    }
    
    /**
     * Analyze a mistake and provide learning feedback
     */
    suspend fun analyzeMistake(
        boardState: Array<IntArray>, 
        move: Move, 
        boardSize: Int
    ): MistakeAnalysis? {
        return try {
            val request = JSONObject().apply {
                put("method", "analyze_mistake")
                put("board_state", flattenBoard(boardState))
                put("move", JSONObject().apply {
                    put("row", move.row)
                    put("col", move.col)
                    put("number", move.number)
                    put("is_correct", move.isCorrect)
                })
                put("board_size", boardSize)
            }
            
            val response = makeMCPRequest(request)
            parseMistakeAnalysis(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing mistake", e)
            null
        }
    }
    
    /**
     * Generate a custom puzzle with specified difficulty
     */
    suspend fun generateCustomPuzzle(
        difficulty: String, 
        boardSize: Int,
        preferences: PuzzlePreferences? = null
    ): CustomPuzzle? {
        return try {
            val request = JSONObject().apply {
                put("method", "generate_puzzle")
                put("difficulty", difficulty)
                put("board_size", boardSize)
                preferences?.let { prefs ->
                    put("preferences", JSONObject().apply {
                        put("theme", prefs.theme)
                        put("techniques", prefs.preferredTechniques)
                        put("time_limit", prefs.timeLimit)
                    })
                }
            }
            
            val response = makeMCPRequest(request)
            parsePuzzleResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating custom puzzle", e)
            null
        }
    }
    
    /**
     * Get personalized learning recommendations
     */
    suspend fun getLearningRecommendations(
        performanceData: PerformanceData
    ): LearningRecommendations? {
        return try {
            val request = JSONObject().apply {
                put("method", "get_learning_recommendations")
                put("performance", JSONObject().apply {
                    put("average_solve_time", performanceData.averageSolveTime)
                    put("mistake_patterns", performanceData.mistakePatterns)
                    put("techniques_used", performanceData.techniquesUsed)
                    put("difficulty_progression", performanceData.difficultyProgression)
                })
            }
            
            val response = makeMCPRequest(request)
            parseLearningRecommendations(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting learning recommendations", e)
            null
        }
    }
    
    /**
     * Make HTTP request to MCP server
     */
    private suspend fun makeMCPRequest(request: JSONObject): String {
        return withContext(Dispatchers.IO) {
            val url = URL(MCP_SERVER_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                
                // Send request
                val outputStream = connection.outputStream
                val writer = BufferedWriter(OutputStreamWriter(outputStream))
                writer.write(request.toString())
                writer.flush()
                writer.close()
                
                // Read response
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    response.toString()
                } else {
                    throw IOException("HTTP error: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    /**
     * Parse hint response from MCP server
     */
    private fun parseHintResponse(response: String): AIHint? {
        return try {
            val json = JSONObject(response)
            AIHint(
                technique = json.getString("technique"),
                explanation = json.getString("explanation"),
                cell = if (json.has("cell")) {
                    val cellJson = json.getJSONObject("cell")
                    Pair(cellJson.getInt("row"), cellJson.getInt("col"))
                } else null,
                confidence = json.getDouble("confidence").toFloat(),
                difficulty = json.getString("difficulty")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hint response", e)
            null
        }
    }
    
    /**
     * Parse mistake analysis response
     */
    private fun parseMistakeAnalysis(response: String): MistakeAnalysis? {
        return try {
            val json = JSONObject(response)
            MistakeAnalysis(
                explanation = json.getString("explanation"),
                correctValue = json.getInt("correct_value"),
                learningTip = json.getString("learning_tip"),
                technique = json.getString("technique"),
                confidence = json.getDouble("confidence").toFloat()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing mistake analysis", e)
            null
        }
    }
    
    /**
     * Parse puzzle generation response
     */
    private fun parsePuzzleResponse(response: String): CustomPuzzle? {
        return try {
            val json = JSONObject(response)
            val clues = json.getJSONArray("clues")
            val cluesArray = IntArray(clues.length()) { clues.getInt(it) }
            
            CustomPuzzle(
                clues = cluesArray,
                difficulty = json.getString("difficulty"),
                estimatedTime = json.getInt("estimated_time"),
                techniques = json.getJSONArray("techniques").let { array ->
                    (0 until array.length()).map { array.getString(it) }
                },
                theme = json.optString("theme", "classic")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing puzzle response", e)
            null
        }
    }
    
    /**
     * Parse learning recommendations response
     */
    private fun parseLearningRecommendations(response: String): LearningRecommendations? {
        return try {
            val json = JSONObject(response)
            val recommendations = json.getJSONArray("recommendations")
            val recList = (0 until recommendations.length()).map { index ->
                val rec = recommendations.getJSONObject(index)
                LearningRecommendation(
                    type = rec.getString("type"),
                    title = rec.getString("title"),
                    description = rec.getString("description"),
                    priority = rec.getInt("priority")
                )
            }
            
            LearningRecommendations(
                recommendations = recList,
                nextDifficulty = json.optString("next_difficulty", "medium"),
                focusAreas = json.getJSONArray("focus_areas").let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing learning recommendations", e)
            null
        }
    }
    
    /**
     * Helper function to flatten 2D board array to 1D array
     */
    private fun flattenBoard(board: Array<IntArray>): IntArray {
        val flattened = mutableListOf<Int>()
        for (row in board) {
            for (cell in row) {
                flattened.add(cell)
            }
        }
        return flattened.toIntArray()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Data classes for MCP communication
 */
data class AIHint(
    val technique: String,
    val explanation: String,
    val cell: Pair<Int, Int>? = null,
    val confidence: Float,
    val difficulty: String
)

data class Move(
    val row: Int,
    val col: Int,
    val number: Int,
    val isCorrect: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class MistakeAnalysis(
    val explanation: String,
    val correctValue: Int,
    val learningTip: String,
    val technique: String,
    val confidence: Float
)

data class PuzzlePreferences(
    val theme: String = "classic",
    val preferredTechniques: List<String> = emptyList(),
    val timeLimit: Int = 0
)

data class CustomPuzzle(
    val clues: IntArray,
    val difficulty: String,
    val estimatedTime: Int,
    val techniques: List<String>,
    val theme: String
)

data class PerformanceData(
    val averageSolveTime: Long,
    val mistakePatterns: Map<String, Int>,
    val techniquesUsed: List<String>,
    val difficultyProgression: List<String>
)

data class LearningRecommendation(
    val type: String,
    val title: String,
    val description: String,
    val priority: Int
)

data class LearningRecommendations(
    val recommendations: List<LearningRecommendation>,
    val nextDifficulty: String,
    val focusAreas: List<String>
)
