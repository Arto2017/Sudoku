package com.example.gamesudoku

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * Anti-cheat and verification system for Daily Challenges
 */
object DailyChallengeVerifier {
    
    private const val SECRET_KEY = "DailySudokuSecret2024" // In production, this should be server-side
    
    /**
     * Generate server signature for puzzle verification
     */
    fun generatePuzzleSignature(puzzle: DailyChallengeGenerator.DailyPuzzle): String {
        val data = "${puzzle.puzzleId}|${puzzle.clues.joinToString(",")}|${puzzle.difficulty}|${puzzle.minClues}"
        return generateHMAC(data, SECRET_KEY)
    }
    
    /**
     * Verify puzzle signature
     */
    fun verifyPuzzleSignature(puzzle: DailyChallengeGenerator.DailyPuzzle, signature: String): Boolean {
        val expectedSignature = generatePuzzleSignature(puzzle)
        return signature == expectedSignature
    }
    
    /**
     * Generate client hash for submission verification
     */
    fun generateSubmissionHash(
        puzzleId: String,
        userId: String,
        completedGrid: IntArray,
        timeSeconds: Int,
        movesCount: Int,
        timestamp: Long
    ): String {
        val data = "$puzzleId|$userId|${completedGrid.joinToString(",")}|$timeSeconds|$movesCount|$timestamp"
        return generateSHA256(data)
    }
    
    /**
     * Verify submission hash
     */
    fun verifySubmissionHash(
        submission: DailyChallengeGenerator.DailySubmission,
        timestamp: Long
    ): Boolean {
        val expectedHash = generateSubmissionHash(
            submission.puzzleId,
            submission.userId,
            submission.completedGrid,
            submission.timeSeconds,
            submission.movesCount,
            timestamp
        )
        return submission.clientHash == expectedHash
    }
    
    /**
     * Validate completion time (anti-speedrun protection)
     */
    fun validateCompletionTime(
        difficulty: DailyChallengeGenerator.Difficulty,
        timeSeconds: Int,
        movesCount: Int
    ): ValidationResult {
        val minTime = getMinimumTimeForDifficulty(difficulty)
        val maxTime = getMaximumTimeForDifficulty(difficulty)
        
        return when {
            timeSeconds < minTime -> ValidationResult.INVALID_TIME_TOO_FAST
            timeSeconds > maxTime -> ValidationResult.INVALID_TIME_TOO_SLOW
            movesCount < getMinimumMovesForDifficulty(difficulty) -> ValidationResult.INVALID_MOVES_TOO_FEW
            movesCount > getMaximumMovesForDifficulty(difficulty) -> ValidationResult.INVALID_MOVES_TOO_MANY
            else -> ValidationResult.VALID
        }
    }
    
    /**
     * Detect suspicious patterns in completion
     */
    fun detectSuspiciousPatterns(
        puzzle: DailyChallengeGenerator.DailyPuzzle,
        completedGrid: IntArray,
        timeSeconds: Int,
        movesCount: Int
    ): List<SuspiciousPattern> {
        val patterns = mutableListOf<SuspiciousPattern>()
        
        // Check for perfect solution (no mistakes)
        if (isPerfectSolution(puzzle, completedGrid)) {
            patterns.add(SuspiciousPattern.PERFECT_SOLUTION)
        }
        
        // Check for unrealistic speed
        if (timeSeconds < getMinimumTimeForDifficulty(puzzle.difficulty)) {
            patterns.add(SuspiciousPattern.UNREALISTIC_SPEED)
        }
        
        // Check for too few moves
        if (movesCount < getMinimumMovesForDifficulty(puzzle.difficulty)) {
            patterns.add(SuspiciousPattern.TOO_FEW_MOVES)
        }
        
        // Check for pattern in completion times (same time multiple days)
        // This would require historical data
        
        return patterns
    }
    
    /**
     * Generate secure random seed for additional verification
     */
    fun generateSecureSeed(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify puzzle solution is correct
     */
    fun verifySolution(puzzle: DailyChallengeGenerator.DailyPuzzle, completedGrid: IntArray): Boolean {
        return DailyChallengeGenerator.verifyCompletion(puzzle, completedGrid)
    }
    
    /**
     * Check if solution is perfect (no backtracking needed)
     */
    private fun isPerfectSolution(puzzle: DailyChallengeGenerator.DailyPuzzle, completedGrid: IntArray): Boolean {
        // A perfect solution would have very few moves relative to empty cells
        val emptyCells = puzzle.clues.count { it == 0 }
        val filledCells = completedGrid.count { it != 0 }
        
        // Perfect solution should fill exactly the empty cells
        return filledCells == emptyCells
    }
    
    /**
     * Get minimum time for difficulty (in seconds)
     */
    private fun getMinimumTimeForDifficulty(difficulty: DailyChallengeGenerator.Difficulty): Int {
        return when (difficulty) {
            DailyChallengeGenerator.Difficulty.EASY -> 30    // 30 seconds minimum
            DailyChallengeGenerator.Difficulty.MEDIUM -> 60  // 1 minute minimum
            DailyChallengeGenerator.Difficulty.HARD -> 120   // 2 minutes minimum
        }
    }
    
    /**
     * Get maximum time for difficulty (in seconds)
     */
    private fun getMaximumTimeForDifficulty(difficulty: DailyChallengeGenerator.Difficulty): Int {
        return when (difficulty) {
            DailyChallengeGenerator.Difficulty.EASY -> 3600    // 1 hour maximum
            DailyChallengeGenerator.Difficulty.MEDIUM -> 7200  // 2 hours maximum
            DailyChallengeGenerator.Difficulty.HARD -> 10800   // 3 hours maximum
        }
    }
    
    /**
     * Get minimum moves for difficulty
     */
    private fun getMinimumMovesForDifficulty(difficulty: DailyChallengeGenerator.Difficulty): Int {
        val emptyCells = when (difficulty) {
            DailyChallengeGenerator.Difficulty.EASY -> 32    // 49 clues = 32 empty
            DailyChallengeGenerator.Difficulty.MEDIUM -> 40  // 41 clues = 40 empty
            DailyChallengeGenerator.Difficulty.HARD -> 56    // 25 clues = 56 empty
        }
        return emptyCells // Minimum moves = number of empty cells
    }
    
    /**
     * Get maximum moves for difficulty
     */
    private fun getMaximumMovesForDifficulty(difficulty: DailyChallengeGenerator.Difficulty): Int {
        val emptyCells = getMinimumMovesForDifficulty(difficulty)
        return emptyCells * 3 // Allow up to 3x the minimum (accounting for mistakes)
    }
    
    /**
     * Generate HMAC-SHA256
     */
    private fun generateHMAC(data: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = key.toByteArray()
        val dataBytes = data.toByteArray()
        
        // Simple HMAC implementation (in production, use proper HMAC)
        val combined = keyBytes + dataBytes
        val hashBytes = digest.digest(combined)
        
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Generate SHA256 hash
     */
    private fun generateSHA256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    enum class ValidationResult {
        VALID,
        INVALID_TIME_TOO_FAST,
        INVALID_TIME_TOO_SLOW,
        INVALID_MOVES_TOO_FEW,
        INVALID_MOVES_TOO_MANY,
        INVALID_SIGNATURE,
        INVALID_SOLUTION
    }
    
    enum class SuspiciousPattern {
        PERFECT_SOLUTION,
        UNREALISTIC_SPEED,
        TOO_FEW_MOVES,
        REPEATED_TIMES,
        PATTERN_DETECTED
    }
}




