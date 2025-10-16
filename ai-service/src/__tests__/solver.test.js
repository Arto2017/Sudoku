/**
 * Unit tests for the Sudoku solver
 */

const { solveStep, renderExplanation, computeCandidates } = require('../solver');

describe('Sudoku Solver', () => {
  describe('computeCandidates', () => {
    test('should compute candidates for empty 6x6 board', () => {
      const board = Array(6).fill().map(() => Array(6).fill(0));
      const candidates = computeCandidates(board);
      
      expect(candidates).toHaveLength(6);
      expect(candidates[0]).toHaveLength(6);
      expect(candidates[0][0]).toEqual([1, 2, 3, 4, 5, 6]);
    });

    test('should compute candidates for partially filled board', () => {
      const board = [
        [1, 2, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0]
      ];
      const candidates = computeCandidates(board);
      
      // Cell (0,2) should not have candidates 1 or 2
      expect(candidates[0][2]).not.toContain(1);
      expect(candidates[0][2]).not.toContain(2);
      expect(candidates[0][2]).toContain(3);
    });
  });

  describe('solveStep', () => {
    test('should find naked single', () => {
      const board = [
        [1, 2, 3, 4, 5, 0],
        [4, 5, 6, 1, 2, 3],
        [2, 3, 4, 5, 6, 1],
        [3, 4, 5, 6, 1, 2],
        [5, 6, 1, 2, 3, 4],
        [6, 1, 2, 3, 4, 5]
      ];
      
      const step = solveStep(board);
      
      expect(step).not.toBeNull();
      expect(step.technique).toBe('naked_single');
      expect(step.cell).toEqual({ r: 0, c: 5 });
      expect(step.value).toBe(6);
    });

    test('should find hidden single in row', () => {
      const board = [
        [1, 2, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0]
      ];
      
      // Fill some cells to create a hidden single
      board[0][2] = 3;
      board[0][3] = 4;
      board[0][4] = 5;
      board[0][5] = 6;
      
      const step = solveStep(board);
      
      expect(step).not.toBeNull();
      expect(step.technique).toBe('hidden_single');
      expect(step.cell).toEqual({ r: 0, c: 1 });
      expect(step.value).toBe(2);
    });

    test('should return null when no simple step found', () => {
      const board = [
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0],
        [0, 0, 0, 0, 0, 0]
      ];
      
      const step = solveStep(board);
      
      expect(step).toBeNull();
    });
  });

  describe('renderExplanation', () => {
    test('should render naked single explanation', () => {
      const step = {
        technique: 'naked_single',
        cell: { r: 0, c: 5 },
        value: 6,
        explainSteps: ['Only candidate left in row 1, column 6 is 6']
      };
      
      const explanation = renderExplanation(step);
      
      expect(explanation).toBe('Only candidate left in row 1, column 6 is 6 — place 6 in r1c6.');
    });

    test('should render hidden single explanation', () => {
      const step = {
        technique: 'hidden_single',
        cell: { r: 0, c: 1 },
        value: 2,
        explainSteps: ['2 can only go in row 1, column 2 in this row']
      };
      
      const explanation = renderExplanation(step);
      
      expect(explanation).toBe('2 can only go in row 1, column 2 in this row — place 2 in r1c2.');
    });
  });
});
