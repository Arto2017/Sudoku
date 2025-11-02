// Simple MCP Server Example for Sudoku AI Features
// Run with: node mcp-server-example.js

const express = require('express');
const cors = require('cors');
const app = express();
const PORT = 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Simple Sudoku solving techniques for hints
const SUDOKU_TECHNIQUES = {
    'naked_single': {
        name: 'Naked Single',
        difficulty: 'easy',
        description: 'A cell that can only contain one possible number'
    },
    'hidden_single': {
        name: 'Hidden Single', 
        difficulty: 'easy',
        description: 'A number that can only go in one cell within a row, column, or box'
    },
    'naked_pair': {
        name: 'Naked Pair',
        difficulty: 'medium', 
        description: 'Two cells in the same unit that contain only the same two candidates'
    },
    'pointing_pair': {
        name: 'Pointing Pair',
        difficulty: 'medium',
        description: 'A candidate appears only twice in a box and both cells are in the same row or column'
    },
    'x_wing': {
        name: 'X-Wing',
        difficulty: 'hard',
        description: 'A candidate appears exactly twice in two rows and the same two columns'
    }
};

// Helper function to convert 1D array to 2D board
function arrayToBoard(array, size) {
    const board = [];
    for (let i = 0; i < size; i++) {
        board.push(array.slice(i * size, (i + 1) * size));
    }
    return board;
}

// Helper function to convert 2D board to 1D array
function boardToArray(board) {
    return board.flat();
}

// Simple Sudoku validation
function isValidMove(board, row, col, num, size) {
    // Check row
    for (let x = 0; x < size; x++) {
        if (board[row][x] === num) return false;
    }
    
    // Check column
    for (let x = 0; x < size; x++) {
        if (board[x][col] === num) return false;
    }
    
    // Check box
    const boxSize = Math.sqrt(size);
    const startRow = Math.floor(row / boxSize) * boxSize;
    const startCol = Math.floor(col / boxSize) * boxSize;
    
    for (let i = 0; i < boxSize; i++) {
        for (let j = 0; j < boxSize; j++) {
            if (board[startRow + i][startCol + j] === num) return false;
        }
    }
    
    return true;
}

// Find possible candidates for a cell
function getCandidates(board, row, col, size) {
    if (board[row][col] !== 0) return [];
    
    const candidates = [];
    for (let num = 1; num <= size; num++) {
        if (isValidMove(board, row, col, num, size)) {
            candidates.push(num);
        }
    }
    return candidates;
}

// Find naked singles (cells with only one candidate)
function findNakedSingles(board, size) {
    for (let row = 0; row < size; row++) {
        for (let col = 0; col < size; col++) {
            if (board[row][col] === 0) {
                const candidates = getCandidates(board, row, col, size);
                if (candidates.length === 1) {
                    return {
                        row, col, number: candidates[0],
                        technique: 'naked_single'
                    };
                }
            }
        }
    }
    return null;
}

// Find hidden singles
function findHiddenSingles(board, size) {
    // Check rows
    for (let row = 0; row < size; row++) {
        for (let num = 1; num <= size; num++) {
            let count = 0;
            let col = -1;
            for (let c = 0; c < size; c++) {
                if (board[row][c] === 0 && isValidMove(board, row, c, num, size)) {
                    count++;
                    col = c;
                }
            }
            if (count === 1) {
                return {
                    row, col, number: num,
                    technique: 'hidden_single'
                };
            }
        }
    }
    
    // Check columns
    for (let col = 0; col < size; col++) {
        for (let num = 1; num <= size; num++) {
            let count = 0;
            let row = -1;
            for (let r = 0; r < size; r++) {
                if (board[r][col] === 0 && isValidMove(board, r, col, num, size)) {
                    count++;
                    row = r;
                }
            }
            if (count === 1) {
                return {
                    row, col, number: num,
                    technique: 'hidden_single'
                };
            }
        }
    }
    
    return null;
}

// Generate a simple puzzle
function generatePuzzle(difficulty, size) {
    // This is a simplified puzzle generator
    // In a real implementation, you'd use more sophisticated algorithms
    
    const clues = new Array(size * size).fill(0);
    const numClues = difficulty === 'easy' ? Math.floor(size * size * 0.4) :
                     difficulty === 'medium' ? Math.floor(size * size * 0.3) :
                     Math.floor(size * size * 0.25);
    
    // Fill some random cells with valid numbers
    let filled = 0;
    while (filled < numClues) {
        const index = Math.floor(Math.random() * (size * size));
        const num = Math.floor(Math.random() * size) + 1;
        
        if (clues[index] === 0) {
            clues[index] = num;
            filled++;
        }
    }
    
    return clues;
}

// API Endpoints

// Get smart hint
app.post('/mcp', (req, res) => {
    try {
        const { method, board_state, board_size } = req.body;
        
        if (method === 'get_smart_hint') {
            const board = arrayToBoard(board_state, board_size);
            
            // Try to find a hint using different techniques
            let hint = findNakedSingles(board, board_size);
            if (!hint) {
                hint = findHiddenSingles(board, board_size);
            }
            
            if (hint) {
                const technique = SUDOKU_TECHNIQUES[hint.technique];
                res.json({
                    technique: technique.name,
                    explanation: `${technique.description}. Try placing ${hint.number} in cell (${hint.row + 1}, ${hint.col + 1}).`,
                    cell: {
                        row: hint.row,
                        col: hint.col
                    },
                    confidence: 0.9,
                    difficulty: technique.difficulty
                });
            } else {
                res.json({
                    technique: 'No obvious moves',
                    explanation: 'Look for cells with fewer possible numbers, or try using elimination techniques.',
                    confidence: 0.5,
                    difficulty: 'medium'
                });
            }
        }
        
        else if (method === 'analyze_mistake') {
            const { move } = req.body;
            res.json({
                explanation: `The number ${move.number} conflicts with existing numbers in the same row, column, or box.`,
                correct_value: 0, // Would need more sophisticated analysis
                learning_tip: 'Always check that your number doesn\'t already appear in the same row, column, or 3x3 box.',
                technique: 'Basic validation',
                confidence: 0.8
            });
        }
        
        else if (method === 'generate_puzzle') {
            const { difficulty, board_size } = req.body;
            const clues = generatePuzzle(difficulty, board_size);
            
            res.json({
                clues: clues,
                difficulty: difficulty,
                estimated_time: difficulty === 'easy' ? 300 : difficulty === 'medium' ? 600 : 900,
                techniques: ['naked_single', 'hidden_single'],
                theme: 'classic'
            });
        }
        
        else if (method === 'get_learning_recommendations') {
            res.json({
                recommendations: [
                    {
                        type: 'technique',
                        title: 'Learn Hidden Singles',
                        description: 'Practice identifying numbers that can only go in one cell within a row, column, or box.',
                        priority: 1
                    },
                    {
                        type: 'practice',
                        title: 'Medium Difficulty Puzzles',
                        description: 'Try solving more medium difficulty puzzles to improve your skills.',
                        priority: 2
                    }
                ],
                next_difficulty: 'medium',
                focus_areas: ['hidden_singles', 'elimination']
            });
        }
        
        else {
            res.status(400).json({ error: 'Unknown method' });
        }
        
    } catch (error) {
        console.error('Error processing request:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ status: 'OK', message: 'MCP Sudoku Server is running' });
});

// Start server
app.listen(PORT, () => {
    console.log(`🚀 MCP Sudoku Server running on http://localhost:${PORT}`);
    console.log(`📡 Health check: http://localhost:${PORT}/health`);
    console.log(`🎯 MCP endpoint: http://localhost:${PORT}/mcp`);
});

// Export for testing
module.exports = app;





