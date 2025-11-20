package com.example.gamesudoku

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import android.view.View
import android.graphics.Color
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class RealmQuestActivity : AppCompatActivity() {

    private lateinit var questCodex: QuestCodex
    private lateinit var realm: QuestRealm
    private lateinit var puzzleChain: PuzzleChain
    private lateinit var puzzleAdapter: PuzzleChainAdapter
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realm_quest)

        questCodex = QuestCodex(this)
        soundManager = SoundManager.getInstance(this)
        
        val realmId = intent.getStringExtra("realm_id") ?: "echoes"
        realm = questCodex.getRealmById(realmId) ?: questCodex.getRealms().first()
        puzzleChain = questCodex.getPuzzleChain(realmId) ?: return

        setupUI()
        setupPuzzleList()
    }

    private fun setupUI() {
        // Set realm title
        findViewById<TextView>(R.id.realmTitleText).text = realm.name
        
        // Set progress
        val progress = (realm.puzzlesCompleted * 100) / realm.totalPuzzles
        val clamped = progress.coerceIn(0, 100)
        findViewById<TextView>(R.id.realmProgressText).text = "$clamped%"
        findViewById<ProgressBar>(R.id.realmProgressBar)?.progress = clamped
        
        // Check if realm is perfect (all puzzles 3 stars)
        val isPerfect = questCodex.isRealmPerfect(realm.id)
        if (isPerfect) {
            // Show perfect badge indicator
            val titleText = findViewById<TextView>(R.id.realmTitleText)
            titleText.text = "${realm.name} ⭐ Perfect!"
        }
        
        // Set theme-specific background
        val backgroundView = findViewById<View>(R.id.realmBackground)
        backgroundView.setBackgroundResource(getRealmBackground(realm.theme))
        
        // Setup back button (ImageButton) - go back to Sudoku Quest window
        findViewById<View>(R.id.backButton).setOnClickListener {
            val intent = Intent(this, RealmSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // Tabs removed; always show puzzles list
    }

    // Tab logic removed

    private fun setupPuzzleList() {
        val recyclerView = findViewById<RecyclerView>(R.id.puzzleRecyclerView)
        // Load puzzles with their saved status
        // Ensure puzzle 1 is always unlocked (for new or existing realms)
        val firstPuzzle = puzzleChain.puzzles.firstOrNull()
        if (firstPuzzle != null) {
            val savedFirstPuzzle = questCodex.getSavedPuzzle(firstPuzzle.id)
            if (savedFirstPuzzle == null || !savedFirstPuzzle.isUnlocked) {
                // Initialize puzzle 1 as unlocked if it doesn't exist or is locked
                questCodex.devUnlockPuzzle(realm.id, firstPuzzle.id)
                // Refresh puzzle chain after unlocking
                puzzleChain = questCodex.getPuzzleChain(realm.id) ?: puzzleChain
            }
        }
        
        // Load puzzles with their saved status, but ensure proper unlocking logic
        val puzzlesWithStatus = puzzleChain.puzzles.mapIndexed { index, puzzle ->
            val savedPuzzle = questCodex.getSavedPuzzle(puzzle.id)
            val basePuzzle = savedPuzzle ?: puzzle
            
            // For puzzle 1, ensure it's unlocked
            if (index == 0) {
                val unlockedPuzzle = basePuzzle.copy(isUnlocked = true)
                // Save the corrected state if it was different
                if (savedPuzzle?.isUnlocked != true) {
                    questCodex.savePuzzle(unlockedPuzzle)
                }
                unlockedPuzzle
            } else {
                // For puzzles 2, 3, 4, etc. (including puzzle 10), check if previous puzzle is completed
                val previousPuzzle = puzzleChain.puzzles[index - 1]
                val savedPreviousPuzzle = questCodex.getSavedPuzzle(previousPuzzle.id)
                val isPreviousCompleted = savedPreviousPuzzle?.isCompleted == true
                
                // Only unlock if previous puzzle is completed
                if (isPreviousCompleted) {
                    val unlockedPuzzle = basePuzzle.copy(isUnlocked = true)
                    // Save the corrected state if it was different
                    if (savedPuzzle?.isUnlocked != true) {
                        questCodex.savePuzzle(unlockedPuzzle)
                    }
                    unlockedPuzzle
                } else {
                    // Lock this puzzle if previous is not completed
                    val lockedPuzzle = basePuzzle.copy(isUnlocked = false)
                    // Save the corrected state if it was incorrectly unlocked
                    if (savedPuzzle?.isUnlocked == true) {
                        questCodex.savePuzzle(lockedPuzzle)
                    }
                    lockedPuzzle
                }
            }
        }
        val inProgressPuzzleIds = getInProgressPuzzleIds()
        puzzleAdapter = PuzzleChainAdapter(
            puzzles = puzzlesWithStatus,
            inProgressPuzzleIds = inProgressPuzzleIds,
            onPuzzleClick = { puzzle ->
                if (puzzle.isCompleted) {
                    GameNotification.showInfo(this, "Puzzle is already completed!", 2000)
                } else if (puzzle.isUnlocked) {
                    startPuzzle(puzzle)
                } else {
                    GameNotification.showError(this, "Complete previous puzzles to unlock this one!", 2000)
                }
            },
            onPuzzleLongClick = null // Removed hold functionality
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = puzzleAdapter
        recyclerView.itemAnimator = object : androidx.recyclerview.widget.DefaultItemAnimator() {
            override fun animateAdd(holder: RecyclerView.ViewHolder?): Boolean {
                holder?.itemView?.animate()?.scaleX(1.02f)?.scaleY(1.02f)?.setDuration(120)?.withEndAction {
                    holder.itemView.scaleX = 1.0f
                    holder.itemView.scaleY = 1.0f
                }?.start()
                return super.animateAdd(holder)
            }
        }
    }

    // Codex removed

    private fun startPuzzle(puzzle: QuestPuzzle) {
        if (!TEST_MODE) {
            val latestPuzzle = questCodex.getSavedPuzzle(puzzle.id)
            if (latestPuzzle?.isCompleted == true || puzzle.isCompleted) {
                GameNotification.showInfo(this, "Puzzle is already completed!", 2000)
                return
            }
        }

        val savedBoardState = questCodex.loadPuzzleBoardState(puzzle.id)
        val attemptStore = AttemptStateStore(this)
        if (savedBoardState == null) {
            attemptStore.clear(puzzle.id)
        } else {
            attemptStore.setMistakes(puzzle.id, savedBoardState.mistakes)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("quest_puzzle_id", puzzle.id)
            putExtra("realm_id", realm.id)
            putExtra("board_size", puzzle.boardSize)
            putExtra("difficulty", puzzle.difficulty.name)
        }
        startActivity(intent)
    }

    private fun getRealmBackground(theme: RealmTheme): Int {
        return when (theme) {
            RealmTheme.ECHOES -> R.drawable.echoes_background
            RealmTheme.TRIALS -> R.drawable.trials_background
            RealmTheme.FLAME -> R.drawable.flame_background
            RealmTheme.SHADOWS -> R.drawable.shadows_background
        }
    }

    override fun onBackPressed() {
        // Handle system back button - go back to Sudoku Quest window
        val intent = Intent(this, RealmSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh puzzle status when returning from game
        realm = questCodex.getRealmById(realm.id) ?: realm
        puzzleChain = questCodex.getPuzzleChain(realm.id) ?: puzzleChain
        
        setupUI()
        
        // Ensure puzzle 1 is always unlocked (for new or existing realms)
        val firstPuzzle = puzzleChain.puzzles.firstOrNull()
        if (firstPuzzle != null) {
            val savedFirstPuzzle = questCodex.getSavedPuzzle(firstPuzzle.id)
            if (savedFirstPuzzle == null || !savedFirstPuzzle.isUnlocked) {
                // Initialize puzzle 1 as unlocked if it doesn't exist or is locked
                questCodex.devUnlockPuzzle(realm.id, firstPuzzle.id)
                // Refresh puzzle chain after unlocking
                puzzleChain = questCodex.getPuzzleChain(realm.id) ?: puzzleChain
            }
        }
        
        // Fix any incorrectly unlocked puzzles (puzzles 2, 3, etc. should be locked if puzzle 1 is not completed)
        puzzleChain.puzzles.forEachIndexed { index, puzzle ->
            if (index > 0) { // Only check puzzles 2, 3, 4, etc.
                val savedPuzzle = questCodex.getSavedPuzzle(puzzle.id)
                if (savedPuzzle?.isUnlocked == true) {
                    // Check if previous puzzle is completed
                    val previousPuzzle = puzzleChain.puzzles[index - 1]
                    val savedPreviousPuzzle = questCodex.getSavedPuzzle(previousPuzzle.id)
                    val isPreviousCompleted = savedPreviousPuzzle?.isCompleted == true
                    
                    // Lock this puzzle if previous is not completed
                    if (!isPreviousCompleted && savedPuzzle != null) {
                        questCodex.savePuzzle(savedPuzzle.copy(isUnlocked = false))
                    }
                }
            }
        }
        
        // Load puzzles with their saved status, but ensure proper unlocking logic
        val puzzlesWithStatus = puzzleChain.puzzles.mapIndexed { index, puzzle ->
            val savedPuzzle = questCodex.getSavedPuzzle(puzzle.id)
            val basePuzzle = if (savedPuzzle != null) {
                // Keep saved completion status but use current realm difficulty
                savedPuzzle.copy(difficulty = puzzle.difficulty)
            } else {
                puzzle
            }
            
            // For puzzle 1, ensure it's unlocked
            if (index == 0) {
                basePuzzle.copy(isUnlocked = true)
            } else {
                // For puzzles 2, 3, 4, etc. (including puzzle 10), check if previous puzzle is completed
                val previousPuzzle = puzzleChain.puzzles[index - 1]
                val savedPreviousPuzzle = questCodex.getSavedPuzzle(previousPuzzle.id)
                val isPreviousCompleted = savedPreviousPuzzle?.isCompleted == true
                
                // Only unlock if previous puzzle is completed
                if (isPreviousCompleted) {
                    basePuzzle.copy(isUnlocked = true)
                } else {
                    // Lock this puzzle if previous is not completed
                    basePuzzle.copy(isUnlocked = false)
                }
            }
        }
        val inProgressPuzzleIds = getInProgressPuzzleIds()
        puzzleAdapter.updatePuzzles(puzzlesWithStatus, inProgressPuzzleIds)
        // Codex removed
    }
    
    companion object {
        // Toggle to false to disable test mode
        private const val TEST_MODE = false
    }
    
    private fun testPuzzleCompletion(puzzle: QuestPuzzle) {
        if (puzzle.isCompleted) {
            GameNotification.showInfo(this, "[TEST] Puzzle already completed", 1500)
            return
        }

        // Unlock puzzle first if it's locked (for testing)
        if (!puzzle.isUnlocked) {
            questCodex.devUnlockPuzzle(realm.id, puzzle.id)
            GameNotification.showInfo(this, "[TEST] Puzzle unlocked", 1500)
        }
        
        // Simulate completion with test values
        val testTimeSeconds = 120L // 2 minutes for testing
        val testMistakes = 2
        
        // Record completion
        questCodex.recordPuzzleCompletion(realm.id, puzzle.id, testTimeSeconds, testMistakes)
        
        // Show test notification
        GameNotification.showSuccess(this, "[TEST] Opening completion dialog...", 2000)
        
        // Start MainActivity in test completion mode immediately
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("quest_puzzle_id", puzzle.id)
            putExtra("realm_id", realm.id)
            putExtra("board_size", puzzle.boardSize)
            putExtra("difficulty", puzzle.difficulty.name)
            putExtra("test_completion", true) // Flag to trigger immediate completion
        }
        startActivity(intent)
    }

    private fun getInProgressPuzzleIds(): Set<String> {
        return puzzleChain.puzzles
            .filter { puzzle ->
                !puzzle.isCompleted && questCodex.loadPuzzleBoardState(puzzle.id) != null
            }
            .map { it.id }
            .toSet()
    }
}

class PuzzleChainAdapter(
    private var puzzles: List<QuestPuzzle>,
    private var inProgressPuzzleIds: Set<String>,
    private val onPuzzleClick: (QuestPuzzle) -> Unit,
    private val onPuzzleLongClick: ((QuestPuzzle) -> Unit)? = null
) : RecyclerView.Adapter<PuzzleChainAdapter.PuzzleViewHolder>() {

    class PuzzleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.puzzleCard)
        val puzzleNumber: TextView = view.findViewById(R.id.puzzleNumberText)
        val difficulty: TextView = view.findViewById(R.id.difficultyText)
        val status: TextView = view.findViewById(R.id.statusText)
        val stars: TextView = view.findViewById(R.id.starsText)
        val lockIcon: ImageView = view.findViewById(R.id.lockIcon)
        val mistakesSmall: TextView = view.findViewById(R.id.mistakesSmall)
     }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PuzzleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_puzzle_chain, parent, false)
        return PuzzleViewHolder(view)
    }

    override fun onBindViewHolder(holder: PuzzleViewHolder, position: Int) {
        val puzzle = puzzles[position]
        
        holder.puzzleNumber.text = "Puzzle ${puzzle.puzzleNumber}"
        holder.difficulty.text = puzzle.difficulty.name
        
        // Pull live attempt snapshot for mistakes/failed state
        val attempt = AttemptStateStore(holder.itemView.context)
        val liveMistakes = attempt.getMistakes(puzzle.id)
        val mistakes = when {
            liveMistakes > 0 -> liveMistakes
            puzzle.isCompleted -> puzzle.lastMistakes
            else -> 0
        }
        val failed = attempt.isFailed(puzzle.id)

        holder.mistakesSmall.text = "$mistakes/∞"
        when {
            mistakes >= 10 -> holder.mistakesSmall.setTextColor(Color.parseColor("#C62828"))
            mistakes >= 1 -> holder.mistakesSmall.setTextColor(Color.parseColor("#FF8F00"))
            else -> holder.mistakesSmall.setTextColor(Color.parseColor("#6B4C2A"))
        }

        val isInProgress = !puzzle.isCompleted && inProgressPuzzleIds.contains(puzzle.id)

        if (puzzle.isCompleted) {
            holder.status.text = "Completed"
            holder.status.setTextColor(Color.parseColor("#4CAF50"))
            holder.stars.text = (" "+"⭐").repeat(puzzle.stars).trim()
            holder.lockIcon.visibility = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#E8F5E8"))
            holder.card.strokeColor = Color.parseColor("#C8E6C9")
        } else if (isInProgress) {
            holder.status.text = "Continue"
            holder.status.setTextColor(Color.parseColor("#1976D2"))
            holder.stars.text = ""
            holder.lockIcon.visibility = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
            holder.card.strokeColor = Color.parseColor("#90CAF9")
        } else if (failed) {
            holder.status.text = "Failed"
            holder.status.setTextColor(Color.parseColor("#C62828"))
            holder.stars.text = ""
            holder.lockIcon.visibility = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
            holder.card.strokeColor = Color.parseColor("#FFCDD2")
        } else if (puzzle.isUnlocked) {
            holder.status.text = "Available"
            holder.status.setTextColor(Color.parseColor("#FF9800"))
            holder.stars.text = ""
            holder.lockIcon.visibility = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#FFF8E1"))
            holder.card.strokeColor = Color.parseColor("#FFECB3")
        } else {
            holder.status.text = "Locked"
            holder.status.setTextColor(Color.parseColor("#9E9E9E"))
            holder.stars.text = ""
            holder.lockIcon.visibility = View.VISIBLE
            holder.card.setCardBackgroundColor(Color.parseColor("#F2F2F2"))
            holder.card.strokeColor = Color.parseColor("#E0E0E0")
        }
        
        holder.card.setOnClickListener {
            onPuzzleClick(puzzle)
        }
        
        // Hold functionality removed - players must complete puzzles sequentially
    }

    override fun getItemCount() = puzzles.size

    fun updatePuzzles(newPuzzles: List<QuestPuzzle>, updatedInProgressIds: Set<String>) {
        puzzles = newPuzzles
        inProgressPuzzleIds = updatedInProgressIds
        notifyDataSetChanged()
    }
}
