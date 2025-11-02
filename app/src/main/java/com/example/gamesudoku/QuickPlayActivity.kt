package com.example.gamesudoku

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class QuickPlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOARD_SIZE = "board_size"
        const val EXTRA_DIFFICULTY = "difficulty"
    }

    private var selectedBoardSize = 9
    private var selectedDifficulty = SudokuGenerator.Difficulty.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_play)

        // Start entrance animations
        startEntranceAnimations()

        // Back button - returns to game
        findViewById<View>(R.id.quickPlayBackButton)?.setOnClickListener {
            finish()
        }

        // Grid size selection
        findViewById<View>(R.id.btn6x6).setOnClickListener {
            selectGridSize(6)
        }

        findViewById<View>(R.id.btn9x9).setOnClickListener {
            selectGridSize(9)
        }

        // Difficulty rows with smooth animations
        setupDifficultyRow(R.id.easyRow, SudokuGenerator.Difficulty.EASY)
        setupDifficultyRow(R.id.mediumRow, SudokuGenerator.Difficulty.MEDIUM)
        setupDifficultyRow(R.id.hardRow, SudokuGenerator.Difficulty.HARD)
        setupDifficultyRow(R.id.expertRow, SudokuGenerator.Difficulty.EXPERT)
    }

    private fun startEntranceAnimations() {
        val topBar = findViewById<View>(R.id.topBar)
        val gridSizeContainer = findViewById<View>(R.id.gridSizeContainer)
        val difficultyContainer = findViewById<View>(R.id.difficultyContainer)

        // Initially hide elements that will animate in
        topBar.alpha = 0f
        topBar.translationY = -30f
        gridSizeContainer.alpha = 0f
        gridSizeContainer.translationY = -20f
        difficultyContainer.alpha = 0f
        difficultyContainer.translationY = 20f

        // Animate top bar first
        topBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate grid size selection after delay
        Handler(Looper.getMainLooper()).postDelayed({
            gridSizeContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }, 100)

        // Animate difficulty rows with stagger
        Handler(Looper.getMainLooper()).postDelayed({
            animateDifficultyRows()
        }, 200)
    }

    private fun animateDifficultyRows() {
        val difficultyContainer = findViewById<View>(R.id.difficultyContainer)
        val rows = listOf(
            findViewById<View>(R.id.easyRow),
            findViewById<View>(R.id.mediumRow),
            findViewById<View>(R.id.hardRow),
            findViewById<View>(R.id.expertRow)
        )

        // Show container
        difficultyContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate each row with stagger
        rows.forEachIndexed { index, row ->
            row.alpha = 0f
            row.translationY = 20f
            row.scaleX = 0.95f
            row.scaleY = 0.95f

            Handler(Looper.getMainLooper()).postDelayed({
                row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }, (index * 60).toLong())
        }
    }

    private fun selectGridSize(boardSize: Int) {
        selectedBoardSize = boardSize
        
        val btn6x6 = findViewById<View>(R.id.btn6x6)
        val btn9x9 = findViewById<View>(R.id.btn9x9)
        
        // Find TextView children in the LinearLayouts
        val text6x6 = btn6x6.findViewById<TextView>(android.R.id.text1) ?: 
                     (btn6x6 as? android.widget.LinearLayout)?.getChildAt(0) as? TextView
        val text9x9 = btn9x9.findViewById<TextView>(android.R.id.text1) ?: 
                     (btn9x9 as? android.widget.LinearLayout)?.getChildAt(0) as? TextView

        if (boardSize == 6) {
            btn6x6.setBackgroundResource(R.drawable.segmented_pill_selected)
            btn9x9.setBackgroundResource(R.drawable.segmented_pill_unselected)
            text6x6?.setTextColor(resources.getColor(android.R.color.white, null))
            text9x9?.setTextColor(resources.getColor(R.color.text_muted, null))
        } else {
            btn6x6.setBackgroundResource(R.drawable.segmented_pill_unselected)
            btn9x9.setBackgroundResource(R.drawable.segmented_pill_selected)
            text6x6?.setTextColor(resources.getColor(R.color.text_muted, null))
            text9x9?.setTextColor(resources.getColor(android.R.color.white, null))
        }

    }

    private fun setupDifficultyRow(rowId: Int, difficulty: SudokuGenerator.Difficulty) {
        val row = findViewById<View>(rowId)
        
        row.setOnClickListener {
            // Add click animation - slide right then start game
            row.animate()
                .translationX(20f)
                .setDuration(100)
                .withEndAction {
                    row.animate()
                        .translationX(0f)
                        .setDuration(100)
                        .withEndAction {
                            startGame(selectedBoardSize, difficulty)
                        }
                        .start()
                }
                .start()
        }
    }
    
    private fun startGame(boardSize: Int, difficulty: SudokuGenerator.Difficulty) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_BOARD_SIZE, boardSize)
            putExtra(EXTRA_DIFFICULTY, difficulty.name)
        }
        startActivity(intent)
    }
}
