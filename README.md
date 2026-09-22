# Survey Service

En enkel enkättjänst primärt för liveanvändning i workshops, med anonymt deltagande och senare stöd för live-resultat, QR/kortkod samt import/export.

## Status

Projektet är i CREATE-läge. Den här versionen innehåller React/TypeScript/Vite-frontend, Quarkus/Java-backend, CI-grund, PostgreSQL/Flyway-baseline samt grundläggande lokal administratörsautentisering.

## Förutsättningar

- Node.js 22+
- npm 10+
- Java 21
- Maven 3.9+
- Docker Compose eller Podman Compose för full bootstrap-verifiering

## Reproducerbar verifiering

Kör hela bootstrap-kontrollen med:

```bash
./scripts/verify-bootstrap.sh
```

Skriptet bygger/testar frontend och backend samt validerar Compose när nödvändiga verktyg finns. Om verktyg eller dependency resolution saknas rapporteras steget som blockerat i stället för godkänt.

## Frontend

```bash
cd frontend
npm ci
npm run dev
```

Produktionsbygge:

```bash
npm run build
```

## Databas

Starta PostgreSQL:

```bash
docker compose up -d postgres
```

Kontrollera status:

```bash
docker compose ps
```

Flyway kör migrationerna i `backend/src/main/resources/db/migration/` automatiskt när backend startar. V2 skapar tabeller för lokal administratör och administratörssessioner.

## Backend

Starta först PostgreSQL och kör därefter:

```bash
cd backend
mvn quarkus:dev
```

Verifiering:

```bash
mvn test
mvn package
```

Backendens bootstrap-endpoint finns på:

```text
GET http://localhost:8080/api/status
```

Databasens readiness ingår i Quarkus health:

```text
GET http://localhost:8080/q/health/ready
```

## Administratörsautentisering

För lokal utveckling kan en första administratör skapas från miljövariabler:

```bash
export ADMIN_BOOTSTRAP_USER=admin
export ADMIN_BOOTSTRAP_PASSWORD='change-me'
export ADMIN_COOKIE_SECURE=false
```

Starta sedan backend. Bootstrap-användaren skapas bara om användarnamnet inte redan finns.

API:

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
GET  /api/admin/ping   # exempel på skyddad admin-endpoint
```

Lösenord lagras som PBKDF2-SHA256-hashar. Sessions-token skickas som `HttpOnly`-cookie och lagras bara hashad i databasen. I produktion ska `ADMIN_COOKIE_SECURE=true` användas tillsammans med HTTPS.

## Dokumentation

- `docs/functional-specification.md`
- `docs/risk-feasibility-analysis.md`
- `docs/architecture.md`
- `docs/development-plan.md`

## Nästa steg

Se `.system-builder/work-status.yaml` för aktuell status och nästa planerade steg.


## CI

GitHub Actions workflow `.github/workflows/ci.yml` verifies backend and frontend independently on pull requests, pushes to `main`, and manual runs.

The frontend workflow currently uses `npm install` because the bootstrap environment could not reach the npm registry and therefore could not generate `package-lock.json`. Generate and commit the lock file as soon as dependency resolution is available, then switch CI to `npm ci`.


## Survey REST API (STEP-06)

Adminskyddade endpoints finns under `/api/admin/surveys` för listning, hämtning, skapande, uppdatering, radering och kopiering av enkätmallar. API:t validerar frågetyper, skalor och svarsalternativ och begränsar åtkomst till inloggad administratörs egna enkäter.


## Admin UI och Survey editor

Admin-UI:t stödjer nu inloggning, listning, skapande, kopiering och radering av enkäter. Survey Editorn stödjer fritext, ja/nej, enkelval, flerval och skala, samt obligatoriska frågor, ordning, svarsalternativ och skalaetiketter.

## Survey Run domain

STEP-09 adds the persistence model for concrete survey runs and immutable question snapshots: `survey_run`, `survey_run_question`, and `survey_run_option`. API and snapshot creation services are added in later steps.

## Survey Run snapshot

`SurveyRunSnapshotService` creates a draft run from an owned survey in one transaction. Questions and answer options are copied to run-specific snapshot entities, so later edits to the source survey do not alter historical runs. A unique public identifier and six-character join code are generated when the run is created.


## Survey Run status och tidsstyrning

`SurveyRunLifecycleService` hanterar omedelbar öppning, schemaläggning, stängning och tidsbaserade statusövergångar för enkätgenomföranden. Ett schemalagt genomförande börjar acceptera nya deltagare när `opensAt` har passerat och slutar när `closesAt` har passerat. `DRAFT` och `CLOSED` accepterar aldrig nya deltagarsessioner.

## Publikt Survey Run-lookup

STEP-12 exponerar publika lookup-endpoints som används av direktlänkar och kortkoder:

```text
GET /api/public/runs/{publicId}
GET /api/public/runs/join/{joinCode}
```

Lookup returnerar endast metadata som behövs för att hitta rätt genomförande. Frågorna exponeras först i deltagarflödet. Kortkoder är case-insensitive. DRAFT/framtida SCHEDULED ger `RUN_NOT_OPEN`, stängda genomföranden ger `RUN_CLOSED`, och okända koder/id ger `RUN_NOT_FOUND`.

## Delning av workshopgenomförande

I admineditorn kan ett sparat enkätunderlag användas för att starta ett nytt workshopgenomförande. Systemet skapar en immutable snapshot av frågorna, öppnar genomförandet och visar:

- direktlänk
- sex tecken lång anslutningskod
- lokalt genererad QR-kod

QR-koden genereras i frontend och kräver ingen extern QR-tjänst. Själva deltagarflödet för `/r/{publicId}` implementeras i kommande steg.

## Anonymous participant sessions

Participant links use `/r/{publicId}`. On first visit the browser creates an anonymous participant session via the public API. A cryptographically random token is returned to the browser and stored in `localStorage` under a key scoped to the survey run. Only a SHA-256 hash of that token is stored in PostgreSQL. Refreshing or reopening the same link in the same browser resumes the existing session rather than creating a new one under normal conditions.

This is deliberately best-effort duplicate protection, not identity verification. Clearing browser storage, private browsing, or another device can create another participant session.


## Participant question view

The public participant route establishes or resumes an anonymous participant session and then loads the immutable Survey Run snapshot. The participant UI renders text, yes/no, single choice, multiple choice and scale questions responsively. Answers are local UI state in this step; server autosave is introduced in the next step.

## STEP-16 – Autosave av svar

Deltagarsvar lagras nu löpande i `response` och `response_value`. Deltagarvyn läser tillbaka sparade svar när en session återupptas och sparar ändringar med debounce (600 ms för fritext, 250 ms för övriga kontroller). UI visar `Sparar…`, `Svar sparade` eller felstatus. Slutlig submit/required-validering införs i nästa steg.

### STEP-17 – Slutlig inlämning

Deltagare kan nu skicka in enkäten slutligt. Backend verifierar obligatoriska frågor mot faktiskt sparade svar, markerar deltagarsessionen som `SUBMITTED` och gör submit idempotent. Efter inlämning går det inte längre att ändra svar och deltagaren visas en bekräftelsevy.

## STEP-18 – Heartbeat och aktiva deltagarsessioner

Deltagarvyn skickar ett heartbeat när en aktiv deltagarsession är redo och därefter ungefär var 30:e sekund till:

```text
POST /api/public/runs/{publicId}/participants/current/heartbeat
X-Participant-Token: <token>
```

Backend uppdaterar `participant_session.last_activity_at`. En deltagarsession räknas som aktiv när:

```text
status = ACTIVE
AND last_activity_at >= now - 90 seconds
```

Detta är ett praktiskt närvaromått för workshopvyn, inte ett exakt antal fysiska personer. Submitted/expired sessions räknas inte som aktiva.

## STEP-19 – Server-side resultataggregering

Admin-API:t kan nu hämta aggregerade resultat för ett helt genomförande eller en enskild fråga:

```text
GET /api/admin/runs/{runId}/results
GET /api/admin/runs/{runId}/results/{questionId}
```

Aggregeringen sker server-side och stödjer ja/nej, enkelval, flerval, skala och fritext. Resultaten bygger på sparade svar från både aktiva och inskickade deltagarsessioner; `EXPIRED`-sessioner räknas inte. Det gör att samma API kan användas för live-resultat i workshopläge. `responseCount` räknar deltagarsessioner som faktiskt har ett svar på frågan, medan flerval räknar varje valt alternativ separat.

## Live summary

Adminvyn visar nu för valt genomförande:

- antal deltagarsessioner som har påbörjats,
- antal som räknas som aktiva just nu (aktiv status och aktivitet inom 90 sekunder),
- antal slutförda deltagarsessioner.

I STEP-20 uppdateras summeringen med enkel polling var femte sekund. Nästa steg byter live-signalen till SSE så att uppdatering kan ske händelsedrivet.

## Liveuppdatering (SSE)

Adminvyn ansluter till `GET /api/admin/runs/{runId}/events` med Server-Sent Events. Backend skickar händelser efter lyckad databascommit för deltagarstart, aktivitet, svarsändring och submit. Klienten hämtar därefter aktuell summary från servern. `EventSource` återansluter automatiskt; om SSE tillfälligt felar aktiveras 5-sekunders polling som fallback tills anslutningen är tillbaka.

I en reverse proxy måste SSE-responsen få strömmas utan aggressiv buffering/timeouts. Detta ska verifieras i deployment-steget.

## STEP-22 – Resultatdiagram

Adminvyn visar nu live-resultat grafiskt för ja/nej, enkelval, flerval och skala. Diagrammen bygger på server-side aggregat och uppdateras via samma SSE-flöde som deltagarstatusen, med polling som fallback. Fritext visas ännu bara som antal svar och får sin lista i nästa steg.


## STEP-23 – Fritextresultat

Adminvyn visar nu fritextsvar som en separat, lättläst lista per fråga. Svaren sorteras med senast uppdaterade först, radbrytningar bevaras och långa ord/länkliknande strängar bryts så att layouten inte spricker. React renderar texten som textinnehåll, vilket innebär att HTML i ett deltagarsvar inte exekveras. Fritext ingår fortfarande inte automatiskt i presentationsläge.


## Presentation mode (STEP-24)

An administrator can open `/present/{runId}` in a separate window from the run sharing view. The page is projector-oriented, shows started/active/submitted counts, lets the presenter choose a question, and updates over SSE with polling fallback. Free-text contents are intentionally not rendered in presentation mode. STEP-24 reuses the current admin session; a restricted presentation token is planned in STEP-25.

## Presentation token (STEP-25)

Presentationsläget använder nu en separat, tidsbegränsad read-only-token i stället för administratörens session. Administratören skapar token via:

```text
POST /api/admin/runs/{runId}/presentation-tokens
```

Servern lagrar endast SHA-256-hash av tokenen. Standardgiltigheten är 12 timmar och kan styras med `PRESENTATION_TOKEN_HOURS`. Token kan återkallas via:

```text
DELETE /api/admin/runs/{runId}/presentation-tokens/{tokenId}
```

Projektorvyn använder därefter:

```text
GET /api/presentation/{token}
GET /api/presentation/{token}/events
```

Presentationstoken ger ingen åtkomst till `/api/admin/**`. Presentation API returnerar live-summary och aggregerade strukturerade resultat. För fritextfrågor returneras endast antal svar; själva fritextinnehållet exponeras inte via presentationstoken. Presentation responses markeras `no-store`.

## Survey JSON export (STEP-26)

En sparad enkätmall kan exporteras från admineditorn med **Exportera JSON**. Backend-endpointen är:

```text
GET /api/admin/surveys/{surveyId}/export
```

Exporten använder det portabla formatet `survey-definition` version `1` och returneras som en JSON-attachment. Den innehåller titel, beskrivning, frågor, required-inställningar, skaldefinitioner och svarsalternativ. Interna UUID:n, ägare, survey-status, timestamps, genomföranden och svar ingår inte. Formatet dokumenteras i `docs/survey-definition-format.md`.

## JSON import

Administratören kan importera ett tidigare exporterat `survey-definition` version 1. Importen valideras atomärt och skapar alltid en ny enkät i `DRAFT`-status; befintliga enkäter skrivs aldrig över.

## Result JSON export

Administratören kan exportera ett genomförandes snapshot och insamlade svar via:

```text
GET /api/admin/runs/{runId}/export/json
```

Formatet är `survey-result-export` version 1. Deltagartoken, token-hash och interna deltagar-ID:n exporteras inte. Se `docs/survey-result-export-format.md`.


## Result CSV export (STEP-29)

Administratören kan exportera samma resultatunderlag som CSV via:

```text
GET /api/admin/runs/{runId}/export/csv
```

CSV-filen använder UTF-8 med BOM för smidig öppning i Excel, en rad per anonym deltagarsession och en kolumn per fråga. Flerval lagras med semikolon inom cellen. Alla fält citeras så kommatecken, citationstecken och flerradig fritext bevaras korrekt. Formatet dokumenteras i `docs/survey-result-csv-format.md`.

## Komplett exportpaket

Ett genomförande kan exporteras som ett arkiveringsbart ZIP-paket från adminvyn. Paketet innehåller `manifest.json`, snapshot-baserad `survey.json`, `run.json`, `responses.json` och `responses.csv`. Se `docs/survey-package-format.md`.


## UX notes (STEP-31)

- `/` is the participant landing page for entering a short join code.
- `/admin` is the administrator entry point.
- Survey editor has a direct Workshop shortcut to the sharing/live area.
- Participant view shows answered-question progress and keeps submit controls visible on smaller screens.
- The primary workshop path is now: create/edit survey -> Workshop -> Start run -> QR/code -> Live -> Present.

## Accessibility (STEP-32)

The primary participant and admin flows include visible keyboard focus, skip links, semantic fieldsets/labels, live-region status messages, semantic progress, reduced-motion handling, forced-colors support, and text equivalents for result charts.

## Security baseline

The MVP now includes a small security hardening layer: `SameSite=Strict` admin cookies, same-origin checks for unsafe admin requests, configurable HTTP body limits, in-memory rate limiting for auth/public/presentation endpoints, API security headers and a frontend CSP. See `docs/security.md` for deployment assumptions, especially trusted proxy handling of `X-Forwarded-For`/`X-Real-IP`.

## End-to-end tests

The main user journey is covered with Playwright/Chromium. The E2E suite starts from the browser UI and verifies admin login, survey authoring, workshop start, anonymous participant submission, live results, read-only presentation mode, result downloads and survey definition import/export.

With backend and frontend already running locally:

```bash
cd frontend
npm install
npx playwright install chromium
npm run test:e2e
```

The default E2E base URL is `http://127.0.0.1:5173`. Override it with `E2E_BASE_URL` when needed. GitHub Actions starts PostgreSQL, the Quarkus application and Vite automatically before running the suite.

## STEP-35 – Produktionsdrift

En separat produktionsprofil finns i `compose.production.yaml`. Den bygger React till en Nginx-container, Quarkus till en Java 21-container och använder PostgreSQL 17 med persistent volym. Endast frontendporten exponeras; extern reverse proxy terminerar HTTPS. Nginx proxar `/api/` till backend och är konfigurerad för SSE utan buffering.

Se `docs/deployment.md` och `.env.production.example`. Backup/restore finns i `scripts/backup-db.sh` respektive `scripts/restore-db.sh`.
