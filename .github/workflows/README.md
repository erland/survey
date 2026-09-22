# Workflows

## CI

`ci.yml` verifies both application parts on pushes to `main`, pull requests and manual runs.

### Backend

- Temurin Java 21
- Maven dependency cache
- `mvn --batch-mode --no-transfer-progress verify`

### Frontend

- Node.js 22
- npm dependency cache once `package-lock.json` exists
- `npm install --no-audit --no-fund`
- `npm test`
- `npm run build`

The repository does not yet contain a generated `package-lock.json` because dependency resolution is unavailable in the current bootstrap environment. Once dependencies can be installed, commit the generated lock file and change the install command to `npm ci --no-audit --no-fund`.
