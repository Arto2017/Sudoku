const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const { v4: uuidv4 } = require('uuid');

const solver = require('./src/solver');
const { validateBoard, validateHintLevel } = require('./src/validation');

const app = express();
const PORT = process.env.PORT || 3001;

// Security middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CLIENT_URL || 'http://localhost:3000',
  credentials: true
}));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // limit each IP to 100 requests per windowMs
  message: 'Too many requests from this IP, please try again later.'
});
app.use('/ai/', limiter);

app.use(express.json({ limit: '10mb' }));

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'healthy', 
    timestamp: new Date().toISOString(),
    version: '1.0.0'
  });
});

// Debug solver endpoint
app.post('/debug/solve', (req, res) => {
  try {
    const { board } = req.body;
    if (!validateBoard(board)) {
      return res.status(400).json({ error: 'Invalid board format' });
    }
    
    const result = solver.solveStep(board);
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: 'Solver error', details: error.message });
  }
});

// AI Hint endpoint
app.post('/ai/hint', (req, res) => {
  try {
    const { board, hintLevel = 'soft', playerSkill = 0.5, puzzleId } = req.body;
    
    if (!validateBoard(board)) {
      return res.status(400).json({ error: 'Invalid board format' });
    }
    
    if (!validateHintLevel(hintLevel)) {
      return res.status(400).json({ error: 'Invalid hint level' });
    }
    
    const step = solver.solveStep(board);
    if (!step) {
      return res.json({
        hintType: 'none',
        explanation: 'No logical single-step hint available. Try looking for hidden singles or pairs.',
        confidence: 0.0
      });
    }
    
    const explanation = solver.renderExplanation(step);
    const response = {
      hintType: hintLevel === 'soft' ? 'nudge' : 'place',
      cells: [step.cell],
      explanation,
      confidence: 0.95
    };
    
    if (hintLevel === 'explicit' || hintLevel === 'autoPlace') {
      response.action = {
        type: 'place',
        r: step.cell.r,
        c: step.cell.c,
        value: step.value
      };
    }
    
    res.json(response);
  } catch (error) {
    res.status(500).json({ error: 'Hint generation error', details: error.message });
  }
});

// AI Chat endpoint (mocked for now)
app.post('/ai/chat', (req, res) => {
  try {
    const { board, message, history = [], playerSkill = 0.5 } = req.body;
    
    if (!validateBoard(board)) {
      return res.status(400).json({ error: 'Invalid board format' });
    }
    
    // Mock response for now - will integrate with LLM later
    const step = solver.solveStep(board);
    let replyText = "I can help you with Sudoku strategies! ";
    
    if (step) {
      replyText += `I found a ${step.technique.replace('_', ' ')}: ${solver.renderExplanation(step)}`;
    } else {
      replyText += "Try looking for cells with only one possible number, or numbers that can only go in one place in a row, column, or box.";
    }
    
    res.json({
      replyText,
      suggestedAction: step ? {
        type: 'highlight',
        cells: [step.cell]
      } : null,
      confidence: 0.8
    });
  } catch (error) {
    res.status(500).json({ error: 'Chat error', details: error.message });
  }
});

// Player skill computation endpoint
app.post('/ai/player_skill', (req, res) => {
  try {
    const { metrics } = req.body;
    const { avgSolveTime = 600, incorrectsPerGame = 2, puzzlesCompleted = 0, hintsUsedPerGame = 0 } = metrics;
    
    // Simple skill calculation (0.0 to 1.0)
    let skill = 0.5; // base
    
    // Time factor (faster = higher skill)
    if (avgSolveTime < 300) skill += 0.2;
    else if (avgSolveTime > 900) skill -= 0.2;
    
    // Accuracy factor
    if (incorrectsPerGame < 1) skill += 0.2;
    else if (incorrectsPerGame > 4) skill -= 0.2;
    
    // Experience factor
    if (puzzlesCompleted > 20) skill += 0.1;
    
    // Independence factor (fewer hints = higher skill)
    if (hintsUsedPerGame < 0.5) skill += 0.1;
    else if (hintsUsedPerGame > 2) skill -= 0.1;
    
    skill = Math.max(0.0, Math.min(1.0, skill));
    
    res.json({ playerSkill: skill });
  } catch (error) {
    res.status(500).json({ error: 'Skill calculation error', details: error.message });
  }
});

// Puzzle generation endpoint
app.post('/ai/generate_puzzle', (req, res) => {
  try {
    const { targetDifficulty = 'medium', techniqueProfile = {}, seed } = req.body;
    
    // Mock puzzle generation for now
    const puzzleId = uuidv4();
    const board = generateMockPuzzle(targetDifficulty);
    
    res.json({
      puzzleId,
      board,
      difficulty: targetDifficulty,
      techniqueProfile
    });
  } catch (error) {
    res.status(500).json({ error: 'Puzzle generation error', details: error.message });
  }
});

// Mock puzzle generator
function generateMockPuzzle(difficulty) {
  // Return a simple 6x6 puzzle for testing
  const board = Array(6).fill().map(() => Array(6).fill(0));
  
  // Add some starting numbers
  board[0][0] = 1; board[0][1] = 2; board[0][2] = 3;
  board[1][0] = 4; board[1][1] = 5; board[1][2] = 6;
  board[2][0] = 2; board[2][1] = 1; board[2][2] = 4;
  
  return board;
}

// Error handling middleware
app.use((error, req, res, next) => {
  console.error('Server error:', error);
  res.status(500).json({ 
    error: 'Internal server error',
    message: process.env.NODE_ENV === 'development' ? error.message : 'Something went wrong'
  });
});

// 404 handler
app.use('*', (req, res) => {
  res.status(404).json({ error: 'Endpoint not found' });
});

app.listen(PORT, () => {
  console.log(`Sudoku AI Service running on port ${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/health`);
});

module.exports = app;
