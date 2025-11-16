package com.example.gamesudoku

import android.content.Context
import com.google.gson.Gson

data class QuestRealm(
    val id: String,
    val name: String,
    val description: String,
    val boardSize: Int,
    val difficulty: SudokuGenerator.Difficulty,
    val theme: RealmTheme,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val puzzlesCompleted: Int = 0,
    val totalPuzzles: Int = 10,
    val codexFragments: List<CodexFragment> = emptyList()
)

data class CodexFragment(
    val id: String,
    val title: String,
    val content: String,
    val glyph: String,
    val isUnlocked: Boolean = false
)

data class PuzzleChain(
    val realmId: String,
    val puzzles: List<QuestPuzzle>,
    val miniChallenges: List<MiniChallenge>
)

data class QuestPuzzle(
    val id: String,
    val realmId: String,
    val puzzleNumber: Int,
    val boardSize: Int,
    val difficulty: SudokuGenerator.Difficulty,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val bestTime: Long = 0,
    val stars: Int = 0,
    val codexFragmentId: String? = null,
    // Attempt-state snapshots (optional; live state comes from AttemptStateStore)
    val lastMistakes: Int = 0,
    val isFailed: Boolean = false
)

data class MiniChallenge(
    val id: String,
    val realmId: String,
    val challengeType: ChallengeType,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val reward: String
)

enum class ChallengeType {
    TIME_TRIAL,
    MIRRORED_LOGIC,
    FOG_OF_WAR,
    REVERSED_ROWS,
    HIDDEN_CELLS
}

enum class RealmTheme {
    ECHOES,     // 6x6 Medium - Mystical, ethereal
    TRIALS,     // 6x6 Intermediate - Ancient, stone
    FLAME,      // 9x9 Intermediate - Fiery, intense
    SHADOWS     // 9x9 Expert - Dark, mysterious
}

class QuestCodex(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("quest_codex", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val realms = listOf(
        QuestRealm(
            id = "echoes",
            name = "Realm of Echoes",
            description = "Where ancient whispers guide the way through mystical puzzles",
            boardSize = 6,
            difficulty = SudokuGenerator.Difficulty.EASY,
            theme = RealmTheme.ECHOES,
            isUnlocked = true,
            totalPuzzles = 10,
            codexFragments = createEchoesFragments()
        ),
        QuestRealm(
            id = "trials",
            name = "Realm of Trials",
            description = "Test your skills against the ancient stone guardians",
            boardSize = 6,
            difficulty = SudokuGenerator.Difficulty.MEDIUM,
            theme = RealmTheme.TRIALS,
            isUnlocked = false,
            totalPuzzles = 10,
            codexFragments = createTrialsFragments()
        ),
        QuestRealm(
            id = "flame",
            name = "Realm of Flame",
            description = "Burning challenges that forge the strongest minds",
            boardSize = 9,
            difficulty = SudokuGenerator.Difficulty.MEDIUM,
            theme = RealmTheme.FLAME,
            isUnlocked = false,
            totalPuzzles = 10,
            codexFragments = createFlameFragments()
        ),
        QuestRealm(
            id = "shadows",
            name = "Realm of Shadows",
            description = "The ultimate test where light meets darkness",
            boardSize = 9,
            difficulty = SudokuGenerator.Difficulty.HARD,
            theme = RealmTheme.SHADOWS,
            isUnlocked = false,
            totalPuzzles = 10,
            codexFragments = createShadowsFragments()
        )
    )
    
    private val puzzleChains = createPuzzleChains()
    
    fun getRealms(): List<QuestRealm> {
        return realms.map { realm ->
            val savedRealm = getSavedRealm(realm.id)
            // Derive progress from saved puzzles to avoid stale/overcounted values
            val chain = getPuzzleChain(realm.id)
            val completedCount = chain?.puzzles?.count { puzzle ->
                val saved = getSavedPuzzle(puzzle.id)
                saved?.isCompleted == true
            } ?: savedRealm.puzzlesCompleted
            val clampedCompleted = minOf(completedCount, realm.totalPuzzles)
            val derivedCompletedFlag = clampedCompleted >= realm.totalPuzzles || savedRealm.isCompleted

            realm.copy(
                isUnlocked = savedRealm.isUnlocked,
                isCompleted = derivedCompletedFlag,
                puzzlesCompleted = clampedCompleted
            )
        }
    }
    
    fun getRealmById(realmId: String): QuestRealm? {
        return getRealms().find { it.id == realmId }
    }
    
    fun getPuzzleChain(realmId: String): PuzzleChain? {
        return puzzleChains.find { it.realmId == realmId }
    }
    
    fun recordPuzzleCompletion(realmId: String, puzzleId: String, timeInSeconds: Long, mistakes: Int) {
        val realm = getRealmById(realmId) ?: return
        val puzzleChain = getPuzzleChain(realmId) ?: return
        
        // Update puzzle completion
        val puzzle = puzzleChain.puzzles.find { it.id == puzzleId }
        puzzle?.let { questPuzzle ->
            val stars = calculateStars(timeInSeconds, mistakes, questPuzzle.boardSize)
            val updatedPuzzle = questPuzzle.copy(
                isCompleted = true,
                bestTime = if (questPuzzle.bestTime == 0L || timeInSeconds < questPuzzle.bestTime) timeInSeconds else questPuzzle.bestTime,
                stars = maxOf(questPuzzle.stars, stars),
                lastMistakes = mistakes,
                isFailed = false
            )
            savePuzzle(updatedPuzzle)
            
            // Special handling: If puzzle 10 is completed, mark all previous puzzles (1-9) as completed too
            if (questPuzzle.puzzleNumber == 10) {
                android.util.Log.d("QuestCodex", "Puzzle 10 completed - marking all previous puzzles as completed")
                puzzleChain.puzzles.filter { it.puzzleNumber < 10 }.forEach { prevPuzzle ->
                    val savedPrevPuzzle = getSavedPuzzle(prevPuzzle.id)
                    if (savedPrevPuzzle?.isCompleted != true) {
                        // Mark as completed with default values (or keep existing best time/stars if better)
                        val completedPrevPuzzle = prevPuzzle.copy(
                            isCompleted = true,
                            bestTime = savedPrevPuzzle?.bestTime ?: 0L,
                            stars = savedPrevPuzzle?.stars ?: 0,
                            isUnlocked = true
                        )
                        savePuzzle(completedPrevPuzzle)
                        android.util.Log.d("QuestCodex", "Marked puzzle ${prevPuzzle.puzzleNumber} as completed")
                    }
                }
            }
            
            // Unlock codex fragment if this puzzle has one
            questPuzzle.codexFragmentId?.let { fragmentId ->
                unlockCodexFragment(realmId, fragmentId)
            }
            
            // Check if realm is completed
            val completedPuzzles = puzzleChain.puzzles.count { puzzle ->
                val savedPuzzle = getSavedPuzzle(puzzle.id)
                savedPuzzle?.isCompleted == true
            }
            if (completedPuzzles >= realm.totalPuzzles) {
                completeRealm(realmId)
            } else {
                // Unlock next puzzle
                unlockNextPuzzle(realmId, questPuzzle.puzzleNumber)
            }
            
            // Update realm progress using the accurate completed count and clamp to total
            val clampedCompleted = minOf(completedPuzzles, realm.totalPuzzles)
            updateRealmProgress(realmId, clampedCompleted)
        }
    }
    
    fun calculateStars(timeInSeconds: Long, mistakes: Int, boardSize: Int): Int {
        // Simplified: Perfect = 3, mistakes = 2, many mistakes = 1
        return when {
            mistakes == 0 -> 3  // Perfect completion
            mistakes <= 2 -> 2  // Some mistakes
            else -> 1           // Many mistakes
        }
    }
    
    // Get total stars collected across all quests
    fun getTotalStars(): Int {
        return realms.sumOf { realm ->
            val chain = getPuzzleChain(realm.id)
            chain?.puzzles?.sumOf { puzzle ->
                val saved = getSavedPuzzle(puzzle.id)
                saved?.stars ?: 0
            } ?: 0
        }
    }
    
    // Get maximum possible stars (4 realms × 10 puzzles × 3 stars = 120)
    fun getMaxStars(): Int {
        return realms.sumOf { realm ->
            realm.totalPuzzles * 3
        }
    }
    
    // Check if a realm is perfect (all puzzles have 3 stars)
    fun isRealmPerfect(realmId: String): Boolean {
        val chain = getPuzzleChain(realmId) ?: return false
        val realm = getRealmById(realmId) ?: return false
        
        return chain.puzzles.all { puzzle ->
            val saved = getSavedPuzzle(puzzle.id)
            saved?.isCompleted == true && saved.stars == 3
        } && chain.puzzles.size == realm.totalPuzzles
    }
    
    // Get star progress percentage
    fun getStarProgressPercentage(): Int {
        val total = getTotalStars()
        val max = getMaxStars()
        return if (max > 0) (total * 100) / max else 0
    }
    
    // Get next milestone and remaining stars needed
    fun getNextMilestone(): Pair<String, Int> {
        val total = getTotalStars()
        val milestones = listOf(30, 60, 90, 120)
        val next = milestones.find { it > total }
        
        return if (next != null) {
            val remaining = next - total
            val message = when (next) {
                30 -> "Reach 30 Stars!"
                60 -> "Reach 60 Stars!"
                90 -> "Reach 90 Stars!"
                120 -> "Perfect Master - 120 Stars!"
                else -> "Keep going!"
            }
            Pair(message, remaining)
        } else {
            Pair("Perfect Master! All 120 Stars!", 0)
        }
    }
    
    // Get motivational message based on star count
    fun getMotivationalMessage(): String {
        val total = getTotalStars()
        val percentage = getStarProgressPercentage()
        
        return when {
            total == 0 -> "Start your journey! Collect your first star!"
            total < 10 -> "Great start! Keep collecting stars!"
            total < 30 -> "You're making progress! Aim for 30 stars!"
            total < 60 -> "Excellent work! You're halfway there!"
            total < 90 -> "Amazing! You're a star collector!"
            total < 120 -> "Incredible! Almost perfect mastery!"
            total == 120 -> "PERFECT MASTER! All 120 stars collected! 🏆"
            else -> "Keep pushing for perfection!"
        }
    }
    
    // Get count of perfect realms (all puzzles 3 stars)
    fun getPerfectRealmCount(): Int {
        return realms.count { realm ->
            isRealmPerfect(realm.id)
        }
    }
    
    // Keep old method for backwards compatibility but mark as deprecated
    @Deprecated("Use public calculateStars instead", ReplaceWith("calculateStars(timeInSeconds, mistakes, boardSize)"))
    private fun calculateStarsPrivate(timeInSeconds: Long, mistakes: Int, boardSize: Int): Int {
        return calculateStars(timeInSeconds, mistakes, boardSize)
    }
    
    private fun unlockNextPuzzle(realmId: String, currentPuzzleNumber: Int) {
        val puzzleChain = getPuzzleChain(realmId) ?: return
        val nextPuzzle = puzzleChain.puzzles.find { it.puzzleNumber == currentPuzzleNumber + 1 }
        nextPuzzle?.let {
            savePuzzle(it.copy(isUnlocked = true))
        }
    }
    
    private fun completeRealm(realmId: String) {
        val realm = getRealmById(realmId) ?: return
        val updatedRealm = realm.copy(isCompleted = true)
        saveRealm(updatedRealm)
        
        // Unlock next realm
        val currentIndex = realms.indexOfFirst { it.id == realmId }
        if (currentIndex < realms.size - 1) {
            val nextRealm = realms[currentIndex + 1]
            saveRealm(nextRealm.copy(isUnlocked = true))
        }
    }
    
    private fun updateRealmProgress(realmId: String, completedPuzzles: Int) {
        val realm = getRealmById(realmId) ?: return
        val updatedRealm = realm.copy(puzzlesCompleted = completedPuzzles)
        saveRealm(updatedRealm)
    }
    
    private fun unlockCodexFragment(realmId: String, fragmentId: String) {
        val realm = getRealmById(realmId) ?: return
        val updatedFragments = realm.codexFragments.map { fragment ->
            if (fragment.id == fragmentId) fragment.copy(isUnlocked = true) else fragment
        }
        val updatedRealm = realm.copy(codexFragments = updatedFragments)
        saveRealm(updatedRealm)
    }
    
    // Persistence methods
    private fun saveRealm(realm: QuestRealm) {
        val json = gson.toJson(realm)
        sharedPreferences.edit().putString("realm_${realm.id}", json).apply()
    }
    
    private fun getSavedRealm(realmId: String): QuestRealm {
        val json = sharedPreferences.getString("realm_$realmId", null)
        return if (json != null) {
            gson.fromJson(json, QuestRealm::class.java)
        } else {
            realms.find { it.id == realmId } ?: realms.first()
        }
    }
    
    fun savePuzzle(puzzle: QuestPuzzle) {
        val json = gson.toJson(puzzle)
        sharedPreferences.edit().putString("puzzle_${puzzle.id}", json).apply()
    }
    
    fun getSavedPuzzle(puzzleId: String): QuestPuzzle? {
        val json = sharedPreferences.getString("puzzle_$puzzleId", null)
        return if (json != null) {
            gson.fromJson(json, QuestPuzzle::class.java)
        } else {
            null
        }
    }
    
    // Reset all progress
    fun resetAllProgress() {
        sharedPreferences.edit().clear().apply()
    }
    
    // DEV ONLY: Unlock a realm and its first puzzle for testing
    fun devUnlockRealm(realmId: String) {
        val realm = getRealmById(realmId) ?: return
        saveRealm(realm.copy(isUnlocked = true))
        val chain = getPuzzleChain(realmId) ?: return
        // Unlock first three puzzles to speed testing
        chain.puzzles.take(3).forEach { puzzle ->
            savePuzzle(puzzle.copy(isUnlocked = true))
        }
    }
    
    // DEV ONLY: Unlock a specific puzzle for testing
    fun devUnlockPuzzle(realmId: String, puzzleId: String) {
        val chain = getPuzzleChain(realmId) ?: return
        val puzzle = chain.puzzles.find { it.id == puzzleId } ?: return
        savePuzzle(puzzle.copy(isUnlocked = true))
    }
    
    // Save quest puzzle board state (current board + fixed cells)
    fun savePuzzleBoardState(
        puzzleId: String,
        board: Array<IntArray>,
        fixed: Array<BooleanArray>,
        secondsElapsed: Int,
        mistakes: Int,
        hintsRemaining: Int,
        hintsUsed: Int,
        maxHints: Int
    ) {
        // Convert arrays to lists for JSON serialization
        val boardList = board.map { it.toList() }.toList()
        val fixedList = fixed.map { it.toList() }.toList()
        
        val state = PuzzleBoardState(
            board = boardList,
            fixed = fixedList,
            secondsElapsed = secondsElapsed,
            mistakes = mistakes,
            hintsRemaining = hintsRemaining,
            hintsUsed = hintsUsed,
            maxHints = maxHints
        )
        
        val json = gson.toJson(state)
        sharedPreferences.edit().putString("puzzle_state_$puzzleId", json).apply()
    }
    
    // Load quest puzzle board state
    fun loadPuzzleBoardState(puzzleId: String): PuzzleBoardState? {
        val json = sharedPreferences.getString("puzzle_state_$puzzleId", null) ?: return null
        
        return try {
            gson.fromJson(json, PuzzleBoardState::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // Clear quest puzzle board state (when puzzle is completed)
    fun clearPuzzleBoardState(puzzleId: String) {
        sharedPreferences.edit().remove("puzzle_state_$puzzleId").apply()
    }
    
    data class PuzzleBoardState(
        val board: List<List<Int>>,
        val fixed: List<List<Boolean>>,
        val secondsElapsed: Int,
        val mistakes: Int,
        val hintsRemaining: Int? = null,
        val hintsUsed: Int? = null,
        val maxHints: Int? = null
    )
    
    // Reset difficulty for existing puzzles to match current realm settings
    fun resetPuzzleDifficulties() {
        realms.forEach { realm ->
            val chain = getPuzzleChain(realm.id)
            chain?.puzzles?.forEach { puzzle ->
                val savedPuzzle = getSavedPuzzle(puzzle.id)
                if (savedPuzzle != null && savedPuzzle.difficulty != realm.difficulty) {
                    // Update difficulty to match current realm setting
                    val updatedPuzzle = savedPuzzle.copy(difficulty = realm.difficulty)
                    savePuzzle(updatedPuzzle)
                }
            }
        }
    }
    
    // Helper methods to create puzzle chains and fragments
    private fun createPuzzleChains(): List<PuzzleChain> {
        return realms.map { realm ->
            val puzzles = (1..realm.totalPuzzles).map { puzzleNumber ->
                QuestPuzzle(
                    id = "${realm.id}_puzzle_$puzzleNumber",
                    realmId = realm.id,
                    puzzleNumber = puzzleNumber,
                    boardSize = realm.boardSize,
                    difficulty = realm.difficulty,
                    isUnlocked = puzzleNumber == 1,
                    codexFragmentId = if (puzzleNumber % 3 == 0) "${realm.id}_fragment_${puzzleNumber / 3}" else null
                )
            }
            
            val miniChallenges = (1..3).map { challengeNumber ->
                MiniChallenge(
                    id = "${realm.id}_challenge_$challengeNumber",
                    realmId = realm.id,
                    challengeType = when (challengeNumber) {
                        1 -> ChallengeType.TIME_TRIAL
                        2 -> ChallengeType.MIRRORED_LOGIC
                        3 -> ChallengeType.FOG_OF_WAR
                        else -> ChallengeType.TIME_TRIAL
                    },
                    reward = "Codex Fragment ${challengeNumber}"
                )
            }
            
            PuzzleChain(realm.id, puzzles, miniChallenges)
        }
    }
    
    private fun createEchoesFragments(): List<CodexFragment> {
        return listOf(
            CodexFragment("echoes_fragment_1", "The Whispering Wind", "Ancient echoes carry secrets through the mystical realm...", "🌪️"),
            CodexFragment("echoes_fragment_2", "Ethereal Patterns", "Patterns emerge from the void, revealing hidden truths...", "✨"),
            CodexFragment("echoes_fragment_3", "Mystic Resonance", "The realm resonates with the harmony of perfect solutions...", "🎵")
        )
    }
    
    private fun createTrialsFragments(): List<CodexFragment> {
        return listOf(
            CodexFragment("trials_fragment_1", "Stone Guardians", "Ancient stone guardians test the worthiness of seekers...", "🗿"),
            CodexFragment("trials_fragment_2", "Temple Wisdom", "The temple's wisdom is revealed through perseverance...", "🏛️"),
            CodexFragment("trials_fragment_3", "Trial Master", "Only the master of trials may proceed to greater challenges...", "👑")
        )
    }
    
    private fun createFlameFragments(): List<CodexFragment> {
        return listOf(
            CodexFragment("flame_fragment_1", "Burning Passion", "The flame burns brightest in the heart of the solver...", "🔥"),
            CodexFragment("flame_fragment_2", "Phoenix Rising", "From the ashes of failure, success rises like a phoenix...", "🦅"),
            CodexFragment("flame_fragment_3", "Inferno Master", "The master of flame commands the power of transformation...", "⚡")
        )
    }
    
    private fun createShadowsFragments(): List<CodexFragment> {
        return listOf(
            CodexFragment("shadows_fragment_1", "Shadow Walker", "In darkness, the true path becomes clear...", "👤"),
            CodexFragment("shadows_fragment_2", "Void Mastery", "The void holds infinite possibilities for those who dare...", "🌌"),
            CodexFragment("shadows_fragment_3", "Shadow Lord", "The lord of shadows commands the ultimate power...", "👑")
        )
    }
}
