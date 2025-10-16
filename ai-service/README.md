# Sudoku AI Service

A lightweight AI assistant service for the Sudoku game, providing hints, chat assistance, and adaptive difficulty.

## Features

- **Deterministic Solver**: Rule-based solver with step-by-step explanations
- **Hint System**: Three levels of hints (soft, explicit, auto-place)
- **Chat Assistant**: Natural language questions about the puzzle
- **Player Skill Assessment**: Adaptive difficulty based on performance
- **Privacy-First**: All AI features are opt-in

## Quick Start

1. Install dependencies:
```bash
npm install
```

2. Start the development server:
```bash
npm run dev
```

3. Test the service:
```bash
curl http://localhost:3001/health
```

## API Endpoints

### Health Check
- `GET /health` - Service health status

### AI Hints
- `POST /ai/hint` - Get a hint for the current puzzle
- `POST /ai/chat` - Ask a question about the puzzle
- `POST /ai/player_skill` - Calculate player skill level
- `POST /ai/generate_puzzle` - Generate a new puzzle

### Debug
- `POST /debug/solve` - Test the solver directly

## Example Usage

### Get a Hint
```bash
curl -X POST http://localhost:3001/ai/hint \
  -H "Content-Type: application/json" \
  -d '{
    "board": [[1,2,3,4,5,0],[4,5,6,1,2,3],[2,3,4,5,6,1],[3,4,5,6,1,2],[5,6,1,2,3,4],[6,1,2,3,4,5]],
    "hintLevel": "soft"
  }'
```

### Ask a Question
```bash
curl -X POST http://localhost:3001/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "board": [[1,2,3,4,5,0],[4,5,6,1,2,3],[2,3,4,5,6,1],[3,4,5,6,1,2],[5,6,1,2,3,4],[6,1,2,3,4,5]],
    "message": "Why can'\''t 5 go in r1c6?"
  }'
```

## Solver Techniques

The solver implements these techniques in order:

1. **Naked Single** - Cell with only one possible value
2. **Hidden Single** - Number that can only go in one cell in a unit
3. **Naked Pair** - Two cells with the same two candidates
4. **Pointing Pair** - Candidates in a box that point to a row/column

## Configuration

Set these environment variables:

- `PORT` - Server port (default: 3001)
- `CLIENT_URL` - Allowed CORS origin (default: http://localhost:3000)
- `NODE_ENV` - Environment (development/production)

## Testing

Run the test suite:
```bash
npm test
```

## Development

The service is designed to be stateless and lightweight. All heavy computation (solving, LLM calls) should be done server-side to keep the mobile client responsive.

## Privacy

- All AI features are opt-in
- Board data is only sent to server with user consent
- No persistent storage of user data
- Rate limiting prevents abuse
