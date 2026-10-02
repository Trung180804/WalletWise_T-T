# Firebase Emulators Setup for Support Chat E2E Testing

## Prerequisites
- Node.js >= 18
- Java JDK >= 11
- Firebase CLI (`npx -y firebase-tools`)

## Running Emulators

### Windows (PowerShell)
```powershell
npx firebase-tools emulators:start --config firebase/online-support/firebase.json --project demo-walletwise
```

### macOS / Linux (Bash)
```bash
npx firebase-tools emulators:start --config firebase/online-support/firebase.json --project demo-walletwise
```

## Running Rules Tests

From the Web Admin repository:
```bash
npm test
```
