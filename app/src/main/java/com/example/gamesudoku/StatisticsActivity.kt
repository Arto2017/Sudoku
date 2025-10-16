package com.example.gamesudoku

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class StatisticsActivity : AppCompatActivity() {
    private lateinit var statsManager: StatsManager
    private lateinit var recentGamesAdapter: RecentGamesAdapter
    private lateinit var difficultyChartView: DifficultyChartView
    private lateinit var activeSortText: TextView
    
    private var currentSortType = SortType.DATE
    private var currentSortOrder = SortOrder.DESCENDING

    enum class SortType {
        DATE, TIME, DIFFICULTY, BOARD_SIZE
    }

    enum class SortOrder {
        ASCENDING, DESCENDING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        statsManager = StatsManager(this)
        setupViews()
        loadStatistics()
    }

    private fun setupViews() {
        // Setup custom chart view
        difficultyChartView = findViewById(R.id.difficultyChart)
        activeSortText = findViewById(R.id.activeSortText)
        
        // Setup RecyclerView for recent games
        val recyclerView = findViewById<RecyclerView>(R.id.recentGamesRecyclerView)
        recentGamesAdapter = RecentGamesAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recentGamesAdapter

        // Setup sort buttons
        setupSortButtons()

        // Setup buttons
        findViewById<View>(R.id.clearDataButton).setOnClickListener {
            showClearDataDialog()
        }

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun setupSortButtons() {
        // Date sorting
        findViewById<Button>(R.id.sortDateButton).setOnClickListener {
            if (currentSortType == SortType.DATE) {
                toggleSortOrder()
            } else {
                currentSortType = SortType.DATE
                currentSortOrder = SortOrder.DESCENDING
            }
            updateSortDisplay()
            loadStatistics()
        }

        // Time sorting
        findViewById<Button>(R.id.sortTimeButton).setOnClickListener {
            if (currentSortType == SortType.TIME) {
                toggleSortOrder()
            } else {
                currentSortType = SortType.TIME
                currentSortOrder = SortOrder.ASCENDING
            }
            updateSortDisplay()
            loadStatistics()
        }

        // Difficulty sorting
        findViewById<Button>(R.id.sortDifficultyButton).setOnClickListener {
            if (currentSortType == SortType.DIFFICULTY) {
                toggleSortOrder()
            } else {
                currentSortType = SortType.DIFFICULTY
                currentSortOrder = SortOrder.ASCENDING
            }
            updateSortDisplay()
            loadStatistics()
        }

        // Board size sorting
        findViewById<Button>(R.id.sortBoardSizeButton).setOnClickListener {
            if (currentSortType == SortType.BOARD_SIZE) {
                toggleSortOrder()
            } else {
                currentSortType = SortType.BOARD_SIZE
                currentSortOrder = SortOrder.ASCENDING
            }
            updateSortDisplay()
            loadStatistics()
        }
    }

    private fun toggleSortOrder() {
        currentSortOrder = if (currentSortOrder == SortOrder.ASCENDING) {
            SortOrder.DESCENDING
        } else {
            SortOrder.ASCENDING
        }
    }

    private fun updateSortDisplay() {
        val sortDescription = when (currentSortType) {
            SortType.DATE -> {
                val order = if (currentSortOrder == SortOrder.DESCENDING) "Newest First" else "Oldest First"
                "Sorted by: Date ($order)"
            }
            SortType.TIME -> {
                val order = if (currentSortOrder == SortOrder.ASCENDING) "Fastest First" else "Slowest First"
                "Sorted by: Time ($order)"
            }
            SortType.DIFFICULTY -> {
                val order = if (currentSortOrder == SortOrder.ASCENDING) "Easy → Hard" else "Hard → Easy"
                "Sorted by: Difficulty ($order)"
            }
            SortType.BOARD_SIZE -> {
                val order = if (currentSortOrder == SortOrder.ASCENDING) "6x6 → 9x9" else "9x9 → 6x6"
                "Sorted by: Board Size ($order)"
            }
        }
        activeSortText.text = sortDescription

        // Update button states
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val buttons = listOf(
            findViewById<Button>(R.id.sortDateButton),
            findViewById<Button>(R.id.sortTimeButton),
            findViewById<Button>(R.id.sortDifficultyButton),
            findViewById<Button>(R.id.sortBoardSizeButton)
        )

        buttons.forEach { button ->
            button.isSelected = false
        }

        val activeButton = when (currentSortType) {
            SortType.DATE -> findViewById<Button>(R.id.sortDateButton)
            SortType.TIME -> findViewById<Button>(R.id.sortTimeButton)
            SortType.DIFFICULTY -> findViewById<Button>(R.id.sortDifficultyButton)
            SortType.BOARD_SIZE -> findViewById<Button>(R.id.sortBoardSizeButton)
        }
        activeButton.isSelected = true
    }

    private fun loadStatistics() {
        val stats = statsManager.getGameStats()
        val recentResults = getSortedResults()

        // Update summary statistics
        findViewById<TextView>(R.id.totalGamesText).text = "Total Games Played: ${stats.totalGamesPlayed}"
        
        val bestTime = statsManager.getBestTime(9, SudokuGenerator.Difficulty.HARD)
        val bestTimeText = if (bestTime != null) {
            "Best Time (9x9 Hard): ${statsManager.formatTime(bestTime)}"
        } else {
            "Best Time (9x9 Hard): --:--"
        }
        findViewById<TextView>(R.id.bestTimeText).text = bestTimeText

        // Update difficulty chart with actual data
        val easyCount = stats.gamesByDifficulty[SudokuGenerator.Difficulty.EASY] ?: 0
        val mediumCount = stats.gamesByDifficulty[SudokuGenerator.Difficulty.MEDIUM] ?: 0
        val hardCount = stats.gamesByDifficulty[SudokuGenerator.Difficulty.HARD] ?: 0
        val expertCount = stats.gamesByDifficulty[SudokuGenerator.Difficulty.EXPERT] ?: 0
        difficultyChartView.updateData(easyCount, mediumCount, hardCount, expertCount)

        // Update recent games
        recentGamesAdapter.updateResults(recentResults)
    }

    private fun getSortedResults(): List<GameResult> {
        val results = statsManager.getGameResults()
        val sortedResults = when (currentSortType) {
            SortType.DATE -> {
                if (currentSortOrder == SortOrder.DESCENDING) {
                    results.sortedByDescending { it.date }
                } else {
                    results.sortedBy { it.date }
                }
            }
            SortType.TIME -> {
                val completedResults = results.filter { it.completed }
                if (currentSortOrder == SortOrder.ASCENDING) {
                    completedResults.sortedBy { it.timeInSeconds }
                } else {
                    completedResults.sortedByDescending { it.timeInSeconds }
                }
            }
            SortType.DIFFICULTY -> {
                if (currentSortOrder == SortOrder.ASCENDING) {
                    results.sortedBy { it.difficulty.ordinal }
                } else {
                    results.sortedByDescending { it.difficulty.ordinal }
                }
            }
            SortType.BOARD_SIZE -> {
                if (currentSortOrder == SortOrder.ASCENDING) {
                    results.sortedBy { it.boardSize }
                } else {
                    results.sortedByDescending { it.boardSize }
                }
            }
        }
        return sortedResults.take(20) // Show last 20 games
    }

    private fun showClearDataDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_history, null)
        
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            dialog.dismiss()
            statsManager.clearAllData()
            loadStatistics()
        }
        
        // Style the dialog with completely transparent background
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.5f)
        dialog.show()
    }

    private inner class RecentGamesAdapter : RecyclerView.Adapter<RecentGamesAdapter.ViewHolder>() {
        private var results: List<GameResult> = emptyList()

        fun updateResults(newResults: List<GameResult>) {
            results = newResults
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_game_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = results[position]
            holder.bind(result)
        }

        override fun getItemCount(): Int = results.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val gameDateText: TextView = itemView.findViewById(R.id.gameDateText)
            private val gameInfoText: TextView = itemView.findViewById(R.id.gameInfoText)
            private val starRatingText: TextView = itemView.findViewById(R.id.starRatingText)

            fun bind(result: GameResult) {
                // Set date
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                gameDateText.text = dateFormat.format(result.date)

                // Set game info
                val difficultyText = when (result.difficulty) {
                    SudokuGenerator.Difficulty.EASY -> "Easy"
                    SudokuGenerator.Difficulty.MEDIUM -> "Medium"
                    SudokuGenerator.Difficulty.HARD -> "Hard"
                    SudokuGenerator.Difficulty.EXPERT -> "Expert"
                }
                gameInfoText.text = "${result.boardSize}x${result.boardSize} • $difficultyText"

                // Set star rating based on performance
                val stars = calculateStarRating(result)
                starRatingText.text = "⭐".repeat(stars)

                // Set click listener for detailed view
                itemView.setOnClickListener {
                    showGameDetails(result)
                }
            }

            private fun calculateStarRating(result: GameResult): Int {
                if (!result.completed) return 0
                
                // Calculate rating based on time, mistakes, and difficulty
                val baseRating = when (result.difficulty) {
                    SudokuGenerator.Difficulty.EASY -> 1
                    SudokuGenerator.Difficulty.MEDIUM -> 2
                    SudokuGenerator.Difficulty.HARD -> 3
                    SudokuGenerator.Difficulty.EXPERT -> 3
                }
                
                val timeBonus = when {
                    result.timeInSeconds < 60 -> 2
                    result.timeInSeconds < 300 -> 1
                    else -> 0
                }
                
                val mistakePenalty = when {
                    result.mistakes == 0 -> 1
                    result.mistakes <= 2 -> 0
                    else -> -1
                }
                
                return (baseRating + timeBonus + mistakePenalty).coerceIn(0, 3)
            }
        }
    }

    private fun showGameDetails(result: GameResult) {
        val difficultyText = when (result.difficulty) {
            SudokuGenerator.Difficulty.EASY -> "Easy"
            SudokuGenerator.Difficulty.MEDIUM -> "Medium"
            SudokuGenerator.Difficulty.HARD -> "Hard"
            SudokuGenerator.Difficulty.EXPERT -> "Expert"
        }
        
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        val timeText = statsManager.formatTime(result.timeInSeconds)
        
        val message = """
            📅 Date: ${dateFormat.format(result.date)}
            🎯 Game: ${result.boardSize}x${result.boardSize} $difficultyText
            ⏱ Time: $timeText
            ❌ Mistakes: ${result.mistakes}
            ${if (result.completed) "✅ Completed" else "❌ Incomplete"}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Game Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
