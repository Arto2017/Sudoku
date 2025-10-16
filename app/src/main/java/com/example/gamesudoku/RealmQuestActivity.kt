package com.example.gamesudoku

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realm_quest)

        questCodex = QuestCodex(this)
        
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
        
        // Set theme-specific background
        val backgroundView = findViewById<View>(R.id.realmBackground)
        backgroundView.setBackgroundResource(getRealmBackground(realm.theme))
        
        // Setup back button (ImageButton)
        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Tabs removed; always show puzzles list
    }

    // Tab logic removed

    private fun setupPuzzleList() {
        val recyclerView = findViewById<RecyclerView>(R.id.puzzleRecyclerView)
        // Load puzzles with their saved status
        val puzzlesWithStatus = puzzleChain.puzzles.map { puzzle ->
            questCodex.getSavedPuzzle(puzzle.id) ?: puzzle
        }
        puzzleAdapter = PuzzleChainAdapter(puzzlesWithStatus) { puzzle ->
            if (puzzle.isUnlocked) {
                startPuzzle(puzzle)
            } else {
                Toast.makeText(this, "Complete previous puzzles to unlock this one!", Toast.LENGTH_SHORT).show()
            }
        }
        
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
        // Clear attempt state so each start is fresh 0/4
        AttemptStateStore(this).clear(puzzle.id)

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

    override fun onResume() {
        super.onResume()
        // Refresh puzzle status when returning from game
        realm = questCodex.getRealmById(realm.id) ?: realm
        puzzleChain = questCodex.getPuzzleChain(realm.id) ?: puzzleChain
        
        setupUI()
        // Load puzzles with their saved status, but always use current realm difficulty
        val puzzlesWithStatus = puzzleChain.puzzles.map { puzzle ->
            val savedPuzzle = questCodex.getSavedPuzzle(puzzle.id)
            if (savedPuzzle != null) {
                // Keep saved completion status but use current realm difficulty
                savedPuzzle.copy(difficulty = puzzle.difficulty)
            } else {
                puzzle
            }
        }
        puzzleAdapter.updatePuzzles(puzzlesWithStatus)
        // Codex removed
    }
}

class PuzzleChainAdapter(
    private var puzzles: List<QuestPuzzle>,
    private val onPuzzleClick: (QuestPuzzle) -> Unit
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
        val mistakes = attempt.getMistakes(puzzle.id)
        val failed = attempt.isFailed(puzzle.id)

        holder.mistakesSmall.text = "$mistakes/4"
        when {
            failed || mistakes >= 4 -> holder.mistakesSmall.setTextColor(Color.parseColor("#C62828"))
            mistakes >= 1 -> holder.mistakesSmall.setTextColor(Color.parseColor("#FF8F00"))
            else -> holder.mistakesSmall.setTextColor(Color.parseColor("#6B4C2A"))
        }

        if (puzzle.isCompleted) {
            holder.status.text = "Completed"
            holder.status.setTextColor(Color.parseColor("#4CAF50"))
            holder.stars.text = (" "+"⭐").repeat(puzzle.stars).trim()
            holder.lockIcon.visibility = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#E8F5E8"))
            holder.card.strokeColor = Color.parseColor("#C8E6C9")
        } else if (failed || mistakes >= 4) {
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
    }

    override fun getItemCount() = puzzles.size

    fun updatePuzzles(newPuzzles: List<QuestPuzzle>) {
        puzzles = newPuzzles
        notifyDataSetChanged()
    }
}
