/**
 * Deterministic Sudoku solver with step-by-step explanations
 * Implements basic techniques: Naked Single, Hidden Single, Naked Pair, etc.
 */

class SudokuSolver {
  constructor() {
    this.size = 9; // Default to 9x9, will be set based on board
  }

  /**
   * Find the next logical step to solve the puzzle
   * @param {number[][]} board - 2D array representing the Sudoku board (0 = empty)
   * @returns {Object|null} - Step object with technique, cell, value, and explanation
   */
  solveStep(board) {
    this.size = board.length;
    this.board = board;
    this.candidates = this.computeCandidates(board);
    
    // Try techniques in order of difficulty
    let step = this.findNakedSingle();
    if (step) return step;
    
    step = this.findHiddenSingle();
    if (step) return step;
    
    step = this.findNakedPair();
    if (step) return step;
    
    step = this.findPointingPair();
    if (step) return step;
    
    // No simple step found
    return null;
  }

  /**
   * Compute all possible candidates for each empty cell
   * @param {number[][]} board - The Sudoku board
   * @returns {number[][][]} - 3D array of candidates for each cell
   */
  computeCandidates(board) {
    const size = board.length;
    const candidates = Array(size).fill().map(() => 
      Array(size).fill().map(() => [])
    );
    
    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) {
        if (board[r][c] === 0) {
          candidates[r][c] = this.getCellCandidates(board, r, c);
        }
      }
    }
    
    return candidates;
  }

  /**
   * Get possible values for a specific cell
   * @param {number[][]} board - The Sudoku board
   * @param {number} row - Row index
   * @param {number} col - Column index
   * @returns {number[]} - Array of possible values
   */
  getCellCandidates(board, row, col) {
    const size = board.length;
    const candidates = [];
    
    for (let value = 1; value <= size; value++) {
      if (this.isValidPlacement(board, row, col, value)) {
        candidates.push(value);
      }
    }
    
    return candidates;
  }

  /**
   * Check if a value can be placed in a specific cell
   * @param {number[][]} board - The Sudoku board
   * @param {number} row - Row index
   * @param {number} col - Column index
   * @param {number} value - Value to check
   * @returns {boolean} - True if placement is valid
   */
  isValidPlacement(board, row, col, value) {
    const size = board.length;
    const boxSize = Math.sqrt(size);
    
    // Check row
    for (let c = 0; c < size; c++) {
      if (board[row][c] === value) return false;
    }
    
    // Check column
    for (let r = 0; r < size; r++) {
      if (board[r][col] === value) return false;
    }
    
    // Check box
    const boxRow = Math.floor(row / boxSize) * boxSize;
    const boxCol = Math.floor(col / boxSize) * boxSize;
    
    for (let r = boxRow; r < boxRow + boxSize; r++) {
      for (let c = boxCol; c < boxCol + boxSize; c++) {
        if (board[r][c] === value) return false;
      }
    }
    
    return true;
  }

  /**
   * Find naked single (cell with only one candidate)
   * @returns {Object|null} - Step object or null
   */
  findNakedSingle() {
    for (let r = 0; r < this.size; r++) {
      for (let c = 0; c < this.size; c++) {
        if (this.board[r][c] === 0 && this.candidates[r][c].length === 1) {
          return {
            technique: 'naked_single',
            cell: { r, c },
            value: this.candidates[r][c][0],
            explainSteps: [`Only candidate left in row ${r+1}, column ${c+1} is ${this.candidates[r][c][0]}`]
          };
        }
      }
    }
    return null;
  }

  /**
   * Find hidden single (number that can only go in one cell in a unit)
   * @returns {Object|null} - Step object or null
   */
  findHiddenSingle() {
    // Check rows
    for (let r = 0; r < this.size; r++) {
      const step = this.findHiddenSingleInRow(r);
      if (step) return step;
    }
    
    // Check columns
    for (let c = 0; c < this.size; c++) {
      const step = this.findHiddenSingleInColumn(c);
      if (step) return step;
    }
    
    // Check boxes
    const boxSize = Math.sqrt(this.size);
    for (let boxR = 0; boxR < boxSize; boxR++) {
      for (let boxC = 0; boxC < boxSize; boxC++) {
        const step = this.findHiddenSingleInBox(boxR, boxC);
        if (step) return step;
      }
    }
    
    return null;
  }

  findHiddenSingleInRow(row) {
    for (let value = 1; value <= this.size; value++) {
      const possibleCols = [];
      for (let c = 0; c < this.size; c++) {
        if (this.board[row][c] === 0 && this.candidates[row][c].includes(value)) {
          possibleCols.push(c);
        }
      }
      if (possibleCols.length === 1) {
        return {
          technique: 'hidden_single',
          cell: { r: row, c: possibleCols[0] },
          value,
          explainSteps: [`${value} can only go in row ${row+1}, column ${possibleCols[0]+1} in this row`]
        };
      }
    }
    return null;
  }

  findHiddenSingleInColumn(col) {
    for (let value = 1; value <= this.size; value++) {
      const possibleRows = [];
      for (let r = 0; r < this.size; r++) {
        if (this.board[r][col] === 0 && this.candidates[r][col].includes(value)) {
          possibleRows.push(r);
        }
      }
      if (possibleRows.length === 1) {
        return {
          technique: 'hidden_single',
          cell: { r: possibleRows[0], c: col },
          value,
          explainSteps: [`${value} can only go in row ${possibleRows[0]+1}, column ${col+1} in this column`]
        };
      }
    }
    return null;
  }

  findHiddenSingleInBox(boxRow, boxCol) {
    const boxSize = Math.sqrt(this.size);
    const startRow = boxRow * boxSize;
    const startCol = boxCol * boxSize;
    
    for (let value = 1; value <= this.size; value++) {
      const possibleCells = [];
      for (let r = startRow; r < startRow + boxSize; r++) {
        for (let c = startCol; c < startCol + boxSize; c++) {
          if (this.board[r][c] === 0 && this.candidates[r][c].includes(value)) {
            possibleCells.push({ r, c });
          }
        }
      }
      if (possibleCells.length === 1) {
        const cell = possibleCells[0];
        return {
          technique: 'hidden_single',
          cell,
          value,
          explainSteps: [`${value} can only go in row ${cell.r+1}, column ${cell.c+1} in this box`]
        };
      }
    }
    return null;
  }

  /**
   * Find naked pair (two cells in a unit with the same two candidates)
   * @returns {Object|null} - Step object or null
   */
  findNakedPair() {
    // Check rows for naked pairs
    for (let r = 0; r < this.size; r++) {
      const step = this.findNakedPairInRow(r);
      if (step) return step;
    }
    
    // Check columns for naked pairs
    for (let c = 0; c < this.size; c++) {
      const step = this.findNakedPairInColumn(c);
      if (step) return step;
    }
    
    // Check boxes for naked pairs
    const boxSize = Math.sqrt(this.size);
    for (let boxR = 0; boxR < boxSize; boxR++) {
      for (let boxC = 0; boxC < boxSize; boxC++) {
        const step = this.findNakedPairInBox(boxR, boxC);
        if (step) return step;
      }
    }
    
    return null;
  }

  findNakedPairInRow(row) {
    const pairs = [];
    for (let c = 0; c < this.size; c++) {
      if (this.board[row][c] === 0 && this.candidates[row][c].length === 2) {
        const candidates = this.candidates[row][c];
        const key = candidates.sort().join(',');
        if (!pairs[key]) {
          pairs[key] = [];
        }
        pairs[key].push(c);
      }
    }
    
    for (let [candidates, cols] of Object.entries(pairs)) {
      if (cols.length === 2) {
        const [val1, val2] = candidates.split(',').map(Number);
        return {
          technique: 'naked_pair',
          cell: { r: row, c: cols[0] },
          value: val1,
          explainSteps: [`Naked pair ${val1},${val2} in row ${row+1} eliminates these candidates from other cells`],
          affectedCells: cols.map(c => ({ r: row, c }))
        };
      }
    }
    return null;
  }

  findNakedPairInColumn(col) {
    const pairs = [];
    for (let r = 0; r < this.size; r++) {
      if (this.board[r][col] === 0 && this.candidates[r][col].length === 2) {
        const candidates = this.candidates[r][col];
        const key = candidates.sort().join(',');
        if (!pairs[key]) {
          pairs[key] = [];
        }
        pairs[key].push(r);
      }
    }
    
    for (let [candidates, rows] of Object.entries(pairs)) {
      if (rows.length === 2) {
        const [val1, val2] = candidates.split(',').map(Number);
        return {
          technique: 'naked_pair',
          cell: { r: rows[0], c: col },
          value: val1,
          explainSteps: [`Naked pair ${val1},${val2} in column ${col+1} eliminates these candidates from other cells`],
          affectedCells: rows.map(r => ({ r, c: col }))
        };
      }
    }
    return null;
  }

  findNakedPairInBox(boxRow, boxCol) {
    const boxSize = Math.sqrt(this.size);
    const startRow = boxRow * boxSize;
    const startCol = boxCol * boxSize;
    const pairs = [];
    
    for (let r = startRow; r < startRow + boxSize; r++) {
      for (let c = startCol; c < startCol + boxSize; c++) {
        if (this.board[r][c] === 0 && this.candidates[r][c].length === 2) {
          const candidates = this.candidates[r][c];
          const key = candidates.sort().join(',');
          if (!pairs[key]) {
            pairs[key] = [];
          }
          pairs[key].push({ r, c });
        }
      }
    }
    
    for (let [candidates, cells] of Object.entries(pairs)) {
      if (cells.length === 2) {
        const [val1, val2] = candidates.split(',').map(Number);
        return {
          technique: 'naked_pair',
          cell: cells[0],
          value: val1,
          explainSteps: [`Naked pair ${val1},${val2} in this box eliminates these candidates from other cells`],
          affectedCells: cells
        };
      }
    }
    return null;
  }

  /**
   * Find pointing pair (candidates in a box that point to a row or column)
   * @returns {Object|null} - Step object or null
   */
  findPointingPair() {
    const boxSize = Math.sqrt(this.size);
    
    for (let boxR = 0; boxR < boxSize; boxR++) {
      for (let boxC = 0; boxC < boxSize; boxC++) {
        const step = this.findPointingPairInBox(boxR, boxC);
        if (step) return step;
      }
    }
    
    return null;
  }

  findPointingPairInBox(boxRow, boxCol) {
    const boxSize = Math.sqrt(this.size);
    const startRow = boxRow * boxSize;
    const startCol = boxCol * boxSize;
    
    for (let value = 1; value <= this.size; value++) {
      const possibleCells = [];
      for (let r = startRow; r < startRow + boxSize; r++) {
        for (let c = startCol; c < startCol + boxSize; c++) {
          if (this.board[r][c] === 0 && this.candidates[r][c].includes(value)) {
            possibleCells.push({ r, c });
          }
        }
      }
      
      if (possibleCells.length >= 2 && possibleCells.length <= 3) {
        // Check if all cells are in the same row or column
        const rows = [...new Set(possibleCells.map(cell => cell.r))];
        const cols = [...new Set(possibleCells.map(cell => cell.c))];
        
        if (rows.length === 1) {
          // Pointing pair in row
          return {
            technique: 'pointing_pair',
            cell: possibleCells[0],
            value,
            explainSteps: [`${value} in this box can only go in row ${rows[0]+1}, so it can be eliminated from other cells in that row`],
            affectedCells: possibleCells
          };
        } else if (cols.length === 1) {
          // Pointing pair in column
          return {
            technique: 'pointing_pair',
            cell: possibleCells[0],
            value,
            explainSteps: [`${value} in this box can only go in column ${cols[0]+1}, so it can be eliminated from other cells in that column`],
            affectedCells: possibleCells
          };
        }
      }
    }
    
    return null;
  }

  /**
   * Render a human-readable explanation for a solving step
   * @param {Object} step - The solving step
   * @returns {string} - Human-readable explanation
   */
  renderExplanation(step) {
    const { technique, cell, value, explainSteps } = step;
    const row = cell.r + 1;
    const col = cell.c + 1;
    
    switch (technique) {
      case 'naked_single':
        return `Only candidate left in row ${row}, column ${col} is ${value} — place ${value} in r${row}c${col}.`;
      
      case 'hidden_single':
        return explainSteps[0] + ` — place ${value} in r${row}c${col}.`;
      
      case 'naked_pair':
        return explainSteps[0] + ` This is a naked pair technique.`;
      
      case 'pointing_pair':
        return explainSteps[0] + ` This is a pointing pair technique.`;
      
      default:
        return `Found ${technique.replace('_', ' ')}: place ${value} in r${row}c${col}.`;
    }
  }
}

// Create singleton instance
const solver = new SudokuSolver();

module.exports = {
  solveStep: (board) => solver.solveStep(board),
  renderExplanation: (step) => solver.renderExplanation(step),
  computeCandidates: (board) => solver.computeCandidates(board)
};
