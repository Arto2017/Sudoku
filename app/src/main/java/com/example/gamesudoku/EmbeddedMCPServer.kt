package com.example.gamesudoku

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded MCP Server that runs directly in the Android app
 * No need for external Node.js server - everything is self-contained
 */
class EmbeddedMCPServer(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddedMCPServer"
        private const val SERVER_PORT = 8080
        private const val MAX_CONNECTIONS = 10
    }
    
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val threadPool = Executors.newFixedThreadPool(MAX_CONNECTIONS)
    
    // Sudoku solving techniques
    private val sudokuTechniques = mapOf(
        "naked_single" to TechniqueInfo(
            name = "Naked Single",
            difficulty = "easy",
            description = "A cell that can only contain one possible number"
        ),
        "hidden_single" to TechniqueInfo(
            name = "Hidden Single", 
            difficulty = "easy",
            description = "A number that can only go in one cell within a row, column, or box"
        ),
        "naked_pair" to TechniqueInfo(
            name = "Naked Pair",
            difficulty = "medium", 
            description = "Two cells in the same unit that contain only the same two candidates"
        ),
        "pointing_pair" to TechniqueInfo(
            name = "Pointing Pair",
            difficulty = "medium",
            description = "A candidate appears only twice in a box and both cells are in the same row or column"
        ),
        "x_wing" to TechniqueInfo(
            name = "X-Wing",
            difficulty = "hard",
            description = "A candidate appears exactly twice in two rows and the same two columns"
        )
    )
    
    /**
     * Start the embedded MCP server
     */
    fun start() {
        if (isRunning.get()) {
            Log.w(TAG, "Server is already running")
            return
        }
        
        serverScope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                isRunning.set(true)
                Log.i(TAG, "Embedded MCP Server started on port $SERVER_PORT")
                
                while (isRunning.get() && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        threadPool.submit {
                            handleClient(clientSocket)
                        }
                    } catch (e: SocketException) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                isRunning.set(false)
            }
        }
    }
    
    /**
     * Stop the embedded MCP server
     */
    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        threadPool.shutdown()
        serverScope.cancel()
        Log.i(TAG, "Embedded MCP Server stopped")
    }
    
    /**
     * Check if server is running
     */
    fun isServerRunning(): Boolean = isRunning.get()
    
    /**
     * Get server URL for MCP client
     */
    fun getServerUrl(): String = "http://localhost:$SERVER_PORT/mcp"
    
    /**
     * Handle client connection
     */
    private fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            val request = StringBuilder()
            var line: String?
            var contentLength = 0
            
            // Read HTTP headers
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.startsWith("Content-Length:")) {
                    contentLength = line!!.substringAfter(":").trim().toInt()
                }
                request.append(line).append("\n")
            }
            
            // Read request body
            if (contentLength > 0) {
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                request.append(String(body))
            }
            
            // Process request
            val response = processRequest(request.toString())
            
            // Send response
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: application/json")
            writer.println("Access-Control-Allow-Origin: *")
            writer.println("Access-Control-Allow-Methods: POST, GET, OPTIONS")
            writer.println("Access-Control-Allow-Headers: Content-Type")
            writer.println("Content-Length: ${response.length}")
            writer.println()
            writer.println(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }
    
    /**
     * Process MCP request
     */
    private fun processRequest(request: String): String {
        return try {
            // Extract JSON from request body
            val jsonStart = request.indexOf("{")
            if (jsonStart == -1) {
                return """{"error": "Invalid request format"}"""
            }
            
            val jsonBody = request.substring(jsonStart)
            val method = extractJsonValue(jsonBody, "method")
            
            when (method) {
                "get_smart_hint" -> processSmartHint(jsonBody)
                "analyze_mistake" -> processMistakeAnalysis(jsonBody)
                "generate_puzzle" -> processPuzzleGeneration(jsonBody)
                "get_learning_recommendations" -> processLearningRecommendations(jsonBody)
                else -> """{"error": "Unknown method: $method"}"""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request", e)
            """{"error": "Internal server error: ${e.message}"}"""
        }
    }
    
    /**
     * Process smart hint request
     */
    private fun processSmartHint(jsonBody: String): String {
        val boardState = extractJsonArray(jsonBody, "board_state")
        val boardSize = extractJsonInt(jsonBody, "board_size")
        
        val board = arrayToBoard(boardState, boardSize)
        val hint = findBestHint(board, boardSize)
        
        return if (hint != null) {
            val technique = sudokuTechniques[hint.technique]!!
            """{
                "technique": "${technique.name}",
                "explanation": "${technique.description}. Try placing ${hint.number} in cell (${hint.row + 1}, ${hint.col + 1}).",
                "cell": {"row": ${hint.row}, "col": ${hint.col}},
                "confidence": 0.9,
                "difficulty": "${technique.difficulty}"
            }"""
        } else {
            """{
                "technique": "No obvious moves",
                "explanation": "Look for cells with fewer possible numbers, or try using elimination techniques.",
                "confidence": 0.5,
                "difficulty": "medium"
            }"""
        }
    }
    
    /**
     * Process mistake analysis request
     */
    private fun processMistakeAnalysis(jsonBody: String): String {
        val move = extractJsonObject(jsonBody, "move")
        val number = extractJsonInt(move, "number")
        
        return """{
            "explanation": "The number $number conflicts with existing numbers in the same row, column, or box.",
            "correct_value": 0,
            "learning_tip": "Always check that your number doesn't already appear in the same row, column, or 3x3 box.",
            "technique": "Basic validation",
            "confidence": 0.8
        }"""
    }
    
    /**
     * Process puzzle generation request
     */
    private fun processPuzzleGeneration(jsonBody: String): String {
        val difficulty = extractJsonValue(jsonBody, "difficulty")
        val boardSize = extractJsonInt(jsonBody, "board_size")
        
        val clues = generateSimplePuzzle(difficulty, boardSize)
        
        return """{
            "clues": [${clues.joinToString(",")}],
            "difficulty": "$difficulty",
            "estimated_time": ${getEstimatedTime(difficulty)},
            "techniques": ["naked_single", "hidden_single"],
            "theme": "classic"
        }"""
    }
    
    /**
     * Process learning recommendations request
     */
    private fun processLearningRecommendations(jsonBody: String): String {
        return """{
            "recommendations": [
                {
                    "type": "technique",
                    "title": "Learn Hidden Singles",
                    "description": "Practice identifying numbers that can only go in one cell within a row, column, or box.",
                    "priority": 1
                },
                {
                    "type": "practice",
                    "title": "Medium Difficulty Puzzles",
                    "description": "Try solving more medium difficulty puzzles to improve your skills.",
                    "priority": 2
                }
            ],
            "next_difficulty": "medium",
            "focus_areas": ["hidden_singles", "elimination"]
        }"""
    }
    
    // Helper methods for Sudoku solving
    
    private fun arrayToBoard(array: List<Int>, size: Int): Array<IntArray> {
        val board = Array(size) { IntArray(size) }
        for (i in array.indices) {
            val row = i / size
            val col = i % size
            board[row][col] = array[i]
        }
        return board
    }
    
    private fun findBestHint(board: Array<IntArray>, size: Int): HintResult? {
        // Try to find naked singles first
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (board[row][col] == 0) {
                    val candidates = getCandidates(board, row, col, size)
                    if (candidates.size == 1) {
                        return HintResult(row, col, candidates[0], "naked_single")
                    }
                }
            }
        }
        
        // Try to find hidden singles
        for (row in 0 until size) {
            for (num in 1..size) {
                var count = 0
                var col = -1
                for (c in 0 until size) {
                    if (board[row][c] == 0 && isValidMove(board, row, c, num, size)) {
                        count++
                        col = c
                    }
                }
                if (count == 1) {
                    return HintResult(row, col, num, "hidden_single")
                }
            }
        }
        
        return null
    }
    
    private fun getCandidates(board: Array<IntArray>, row: Int, col: Int, size: Int): List<Int> {
        if (board[row][col] != 0) return emptyList()
        
        val candidates = mutableListOf<Int>()
        for (num in 1..size) {
            if (isValidMove(board, row, col, num, size)) {
                candidates.add(num)
            }
        }
        return candidates
    }
    
    private fun isValidMove(board: Array<IntArray>, row: Int, col: Int, num: Int, size: Int): Boolean {
        // Check row
        for (x in 0 until size) {
            if (board[row][x] == num) return false
        }
        
        // Check column
        for (x in 0 until size) {
            if (board[x][col] == num) return false
        }
        
        // Check box
        val boxSize = kotlin.math.sqrt(size.toDouble()).toInt()
        val startRow = (row / boxSize) * boxSize
        val startCol = (col / boxSize) * boxSize
        
        for (i in 0 until boxSize) {
            for (j in 0 until boxSize) {
                if (board[startRow + i][startCol + j] == num) return false
            }
        }
        
        return true
    }
    
    private fun generateSimplePuzzle(difficulty: String, size: Int): List<Int> {
        val clues = MutableList(size * size) { 0 }
        val numClues = when (difficulty) {
            "easy" -> (size * size * 0.4).toInt()
            "medium" -> (size * size * 0.3).toInt()
            else -> (size * size * 0.25).toInt()
        }
        
        var filled = 0
        while (filled < numClues) {
            val index = (0 until size * size).random()
            val num = (1..size).random()
            
            if (clues[index] == 0) {
                clues[index] = num
                filled++
            }
        }
        
        return clues
    }
    
    private fun getEstimatedTime(difficulty: String): Int {
        return when (difficulty) {
            "easy" -> 300
            "medium" -> 600
            "hard" -> 900
            else -> 600
        }
    }
    
    // JSON parsing helpers
    private fun extractJsonValue(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\""
        val regex = pattern.toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
    
    private fun extractJsonInt(json: String, key: String): Int {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = pattern.toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toInt() ?: 0
    }
    
    private fun extractJsonArray(json: String, key: String): List<Int> {
        val pattern = "\"$key\"\\s*:\\s*\\[([^\\]]+)\\]"
        val regex = pattern.toRegex()
        val match = regex.find(json)?.groupValues?.get(1)
        return match?.split(",")?.map { it.trim().toIntOrNull() ?: 0 } ?: emptyList()
    }
    
    private fun extractJsonObject(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*(\\{[^}]+\\})"
        val regex = pattern.toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: "{}"
    }
}

// Data classes
data class TechniqueInfo(
    val name: String,
    val difficulty: String,
    val description: String
)

data class HintResult(
    val row: Int,
    val col: Int,
    val number: Int,
    val technique: String
)





