/**
 * Validation utilities for the AI service
 */

function validateBoard(board) {
  if (!Array.isArray(board)) return false;
  
  // Check if it's a 6x6 or 9x9 board
  if (board.length !== 6 && board.length !== 9) return false;
  
  for (let row of board) {
    if (!Array.isArray(row) || row.length !== board.length) return false;
    for (let cell of row) {
      if (!Number.isInteger(cell) || cell < 0 || cell > board.length) return false;
    }
  }
  
  return true;
}

function validateHintLevel(level) {
  return ['soft', 'explicit', 'autoPlace'].includes(level);
}

function validatePlayerSkill(skill) {
  return typeof skill === 'number' && skill >= 0 && skill <= 1;
}

function validateTechniqueProfile(profile) {
  if (typeof profile !== 'object' || profile === null) return false;
  
  const validTechniques = ['singles', 'pairs', 'triples', 'xwing', 'swordfish'];
  const total = Object.values(profile).reduce((sum, val) => sum + val, 0);
  
  // Check if all values are numbers and sum to approximately 1.0
  for (let [key, value] of Object.entries(profile)) {
    if (!validTechniques.includes(key) || typeof value !== 'number' || value < 0) {
      return false;
    }
  }
  
  return Math.abs(total - 1.0) < 0.1; // Allow small floating point errors
}

module.exports = {
  validateBoard,
  validateHintLevel,
  validatePlayerSkill,
  validateTechniqueProfile
};
