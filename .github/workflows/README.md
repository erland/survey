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


## Docker release

`release-docker.yml` körs när en GitHub Release publiceras.

Workflowen:

- checkar ut exakt release-taggen,
- tar versionsnumret från taggen och tar bort ett inledande `v`,
- loggar in mot GitHub Container Registry med `GITHUB_TOKEN`,
- bygger backend- och frontend-images med Docker Buildx,
- pushar versionssatta images till GHCR.

Exempel för release-taggen `v0.1.0-rc.1`:

```text
ghcr.io/erland/survey-backend:0.1.0-rc.1
ghcr.io/erland/survey-frontend:0.1.0-rc.1
```

Workflowen kräver `packages: write` och skapar inte taggen `latest`; release-taggen är den explicita versionskällan.
