# Systemarkitektur och teknisk design – Enkättjänst

**Status:** CREATE – systemarkitektur och teknisk design  
**Underlag:** Funktionell specifikation samt risk- och genomförbarhetsanalys  
**Primärt användningsfall:** Liveenkäter i workshop  
**Sekundärt användningsfall:** Tidsbegränsade enkäter via distribuerad länk

---

## 1. Arkitekturmål

Arkitekturen ska optimera för:

- enkel användning i workshop,
- anonymt deltagande,
- robust liveuppdatering,
- tydlig separation mellan enkätmall och genomförande,
- enkel containerbaserad drift,
- möjlighet att köra både internt och externt,
- enkel export/import,
- låg teknisk komplexitet,
- möjlighet att vidareutveckla utan större ombyggnad.

Systemet ska inte överdesignas för mycket hög trafik eller komplex multi-tenant-drift i MVP.

---

## 2. Rekommenderad teknikstack

### Frontend

- React
- TypeScript
- Vite
- React Router
- TanStack Query eller motsvarande för server state
- enkel komponentbaserad UI-lösning
- responsiv CSS, mobile-first för deltagarflödet

Diagram:

- Recharts, Chart.js eller motsvarande
- alternativt enklare egna stapeldiagram med HTML/CSS där det räcker

### Backend

- Java
- Quarkus
- RESTEasy Reactive / Jakarta REST
- Hibernate ORM / Panache eller vanlig JPA
- Flyway för databasmigrering
- Bean Validation
- SSE för liveuppdatering

### Databas

- PostgreSQL

### Test

Frontend:

- Vitest
- React Testing Library
- Playwright för några centrala end-to-end-flöden

Backend:

- JUnit 5
- Quarkus Test
- Testcontainers för PostgreSQL i integrationstest

### Paketering

- Docker/OCI containers
- Docker Compose för lokal utveckling
- kompatibelt med Podman Compose
- kompatibelt med deployment via Coolify eller motsvarande

---

## 3. Övergripande komponentmodell

```text
┌─────────────────────────────────────────────────────┐
│                    Webbläsare                       │
│                                                     │
│  ┌────────────────┐   ┌─────────────────────────┐  │
│  │ Deltagarvy     │   │ Admin/Presentation     │  │
│  │ React          │   │ React                  │  │
│  └───────┬────────┘   └────────────┬────────────┘  │
└──────────┼─────────────────────────┼───────────────┘
           │ REST                    │ REST + SSE
           ▼                         ▼
┌─────────────────────────────────────────────────────┐
│                  Quarkus Backend                    │
│                                                     │
│  Survey API                                         │
│  Survey Run API                                     │
│  Participant API                                    │
│  Response API                                       │
│  Results API                                        │
│  Live Event Service                                 │
│  Import/Export Service                              │
│  Authentication / Authorization                     │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
              ┌───────────────────┐
              │    PostgreSQL     │
              │                   │
              │ surveys           │
              │ runs              │
              │ questions         │
              │ participant sess. │
              │ responses         │
              └───────────────────┘
```

Systemet består av en frontend och en backend. Frontend kan byggas som en enda React-applikation med olika routes och layouts för deltagare respektive administratör.

---

## 4. Frontendarkitektur

### 4.1 Huvudområden

Frontend delas logiskt i tre delar:

1. Admin
2. Deltagare
3. Presentation

### 4.2 Föreslagen route-struktur

```text
/
  Startsida / ange kortkod

/join/:code
  Slår upp ett genomförande via kortkod

/r/:publicId
  Deltagarvy för genomförande

/r/:publicId/complete
  Bekräftelse efter inlämning

/admin
  Administratörsöversikt

/admin/surveys
  Enkätmallar

/admin/surveys/new
  Skapa enkät

/admin/surveys/:surveyId
  Redigera enkät

/admin/surveys/:surveyId/runs
  Genomföranden

/admin/runs/:runId
  Genomförandeöversikt

/admin/runs/:runId/live
  Live-resultat

/admin/runs/:runId/export
  Export

/present/:presentationToken
  Presentationsvy
```

Presentationsvyn bör kunna få en separat, tidsbegränsad eller revokerbar token för att undvika att administratörens fulla session behöver ligga i projektorwebbläsaren.

---

## 5. Backendarkitektur

### 5.1 Moduler/tjänster

Backend kan hållas som en modulär monolit.

Rekommenderade paket/moduler:

```text
survey
  SurveyResource
  SurveyService
  SurveyRepository

run
  SurveyRunResource
  SurveyRunService
  SurveyRunRepository

participant
  ParticipantResource
  ParticipantSessionService

response
  ResponseResource
  ResponseService
  ResponseRepository

result
  ResultResource
  ResultService

live
  LiveEventResource
  LiveEventService

export
  ExportResource
  ExportService

importing
  ImportResource
  ImportService

auth
  AuthService
  SecurityContext
```

Ingen mikroservicearkitektur rekommenderas.

---

## 6. Domänmodell

## 6. Domänmodell

Administrativa resurser ägs av `SurveyAccount`, inte av en enskild administratör. `AdminUser` får åtkomst genom medlemskap i `survey_account_admin`. `createdByAdminUserId` är historisk/audit-metadata och används inte som tenant-gräns.



### 6.1 Survey

Representerar redigerbar enkätmall.

Exempel:

```text
Survey
- id
- surveyAccountId
- createdByAdminUserId
- title
- description
- status
- createdAt
- updatedAt
- version
```

### 6.2 SurveyQuestion

```text
SurveyQuestion
- id
- surveyId
- position
- type
- text
- required
- scaleMin
- scaleMax
- scaleMinLabel
- scaleMaxLabel
```

QuestionType:

```text
TEXT
YES_NO
SINGLE_CHOICE
MULTIPLE_CHOICE
SCALE
```

### 6.3 QuestionOption

```text
QuestionOption
- id
- questionId
- position
- value
- label
```

### 6.4 SurveyRun

Representerar ett faktiskt genomförande.

```text
SurveyRun
- id
- surveyId
- publicId
- joinCode
- title
- status
- opensAt
- closesAt
- createdAt
- openedAt
- closedAt
- createdBy
```

RunStatus:

```text
DRAFT
SCHEDULED
OPEN
CLOSED
```

### 6.5 SurveyRunQuestion

Snapshot av fråga.

```text
SurveyRunQuestion
- id
- runId
- sourceQuestionId
- position
- type
- text
- required
- scaleMin
- scaleMax
- scaleMinLabel
- scaleMaxLabel
```

### 6.6 SurveyRunOption

Snapshot av svarsalternativ.

```text
SurveyRunOption
- id
- runQuestionId
- sourceOptionId
- position
- value
- label
```

### 6.7 ParticipantSession

```text
ParticipantSession
- id
- runId
- clientTokenHash
- status
- startedAt
- lastActivityAt
- submittedAt
- version
```

ParticipantSessionStatus:

```text
ACTIVE
SUBMITTED
EXPIRED
```

Servern bör inte behöva lagra klienttoken i klartext. En hash kan användas.

### 6.8 Response

```text
Response
- id
- participantSessionId
- runQuestionId
- updatedAt
```

### 6.9 ResponseValue

För att stödja flera svarstyper:

```text
ResponseValue
- id
- responseId
- textValue
- numericValue
- booleanValue
- optionId
```

Det kan även lösas med JSONB, men normaliserade fält ger tydligare integritet och enklare aggregering.

---

## 7. Snapshot-strategi

När SurveyRun skapas:

1. Survey läses.
2. Alla SurveyQuestion kopieras till SurveyRunQuestion.
3. Alla QuestionOption kopieras till SurveyRunOption.
4. Genomförandet arbetar därefter endast mot snapshot-tabellerna.

Fördel:

- historiska resultat förblir stabila,
- mall kan ändras efter genomförandet,
- export blir reproducerbar.

Snapshot bör ske i en databastransaktion.

---

## 8. Deltagarsessionsflöde

### 8.1 Första besök

Klienten öppnar:

```text
/r/:publicId
```

Frontend kontrollerar localStorage efter:

```text
survey-session::<publicId>
```

Om ingen token finns:

```http
POST /api/public/runs/{publicId}/participants
```

Backend:

1. validerar att genomförandet får ta emot deltagare,
2. skapar ParticipantSession,
3. genererar kryptografiskt slumpmässig clientToken,
4. lagrar endast hash,
5. returnerar token till klienten.

Klienten lagrar token lokalt.

### 8.2 Återbesök

Klienten skickar token:

```http
Authorization: Participant <token>
```

eller i särskilt headerfält:

```http
X-Participant-Token: <token>
```

Backend matchar hash och återupptar sessionen.

Ett separat deltagartokenformat rekommenderas hellre än cookies om det förenklar anonymitet och tydlighet.

### 8.3 Aktivitet

Klienten skickar heartbeat ungefär var 30:e sekund:

```http
POST /api/public/runs/{publicId}/participant/heartbeat
```

Backend uppdaterar:

```text
lastActivityAt
```

### 8.4 Autosave

När ett svar ändras:

```http
PUT /api/public/runs/{publicId}/responses/{questionId}
```

Backend uppdaterar eller skapar svaret för aktuell ParticipantSession.

Frontend debouncar autosave, exempelvis 300–800 ms beroende på kontrolltyp.

Fritext bör inte skicka varje tangenttryckning utan debounce.

### 8.5 Submit

```http
POST /api/public/runs/{publicId}/submit
```

Backend:

1. låser/validerar sessionen,
2. validerar obligatoriska frågor,
3. markerar sessionen SUBMITTED,
4. sätter submittedAt,
5. publicerar live-event.

Operationen ska vara idempotent.

---

## 9. Liveuppdatering

### 9.1 SSE-endpoint

```http
GET /api/admin/accounts/{accountId}/runs/{runId}/events
Accept: text/event-stream
```

Eventtyper:

```text
participant_started
participant_activity
participant_submitted
response_updated
run_status_changed
```

MVP behöver inte skicka fulla svar i varje event.

Event kan istället indikera:

```json
{
  "type": "response_updated",
  "runId": "...",
  "questionId": "...",
  "timestamp": "..."
}
```

Frontend kan därefter refresha relevanta queries.

Det håller eventformatet enkelt och minskar risk för inkonsistens.

### 9.2 Alternativ optimering senare

Servern kan senare skicka färdiga aggregat i eventen om belastning kräver det.

---

## 10. Resultat-API

### Översikt

```http
GET /api/admin/accounts/{accountId}/runs/{runId}/summary
```

Svar:

```json
{
  "started": 27,
  "active": 4,
  "submitted": 23
}
```

### Resultat per fråga

```http
GET /api/admin/accounts/{accountId}/runs/{runId}/results/{questionId}
```

Ja/nej:

```json
{
  "questionId": "...",
  "type": "YES_NO",
  "responseCount": 22,
  "values": [
    {"value": true, "count": 17},
    {"value": false, "count": 5}
  ]
}
```

Skala:

```json
{
  "questionId": "...",
  "type": "SCALE",
  "responseCount": 22,
  "values": [
    {"value": 1, "count": 1},
    {"value": 2, "count": 2},
    {"value": 3, "count": 4},
    {"value": 4, "count": 9},
    {"value": 5, "count": 6}
  ]
}
```

Fritext:

```json
{
  "questionId": "...",
  "type": "TEXT",
  "responseCount": 18,
  "values": [
    {"text": "..."},
    {"text": "..."}
  ]
}
```

---

## 11. Aktiv deltagare

Definition för MVP:

```text
ACTIVE =
  participant.status = ACTIVE
  AND lastActivityAt >= now() - 90 seconds
```

Heartbeat:

```text
30 sekunder
```

Det ger normalt 2–3 uteblivna heartbeat innan deltagaren försvinner från "aktiva".

UI bör visa:

```text
23 klara
4 svarar just nu
27 har påbörjat
```

---

## 12. Hantering av stängning

Rekommenderat MVP-beteende:

- inga nya deltagarsessioner får skapas efter stängning,
- redan påbörjade sessioner får slutföra,
- administratören kan senare få alternativet "Stäng omedelbart".

Detta innebär att:

```text
run.status = CLOSED
```

inte automatiskt invalidiserar existerande ParticipantSession.

För att hindra obegränsat sena svar kan en framtida:

```text
finalSubmissionDeadline
```

införas.

---

## 13. Adminautentisering

### Rekommenderad arkitektur

Backend ska skydda `/api/admin/**`.

Frontend ska inte kunna kringgå detta.

Auth-lagret abstraheras från domänlogiken.

### MVP-alternativ

Första implementationen kan stödja lokal administratör:

```text
email/username
passwordHash
```

med säker sessionscookie.

Men designen bör göra det enkelt att senare byta till OIDC.

### Sessionsegenskaper

Cookie:

- HttpOnly
- Secure
- SameSite=Lax eller Strict där möjligt

Sessionen ska ha tidsgräns.

---

## 14. Presentationsläge

Presentationsläge ska inte kräva full administratörsbehörighet.

Rekommenderad modell:

```text
PresentationToken
- id
- runId
- tokenHash
- expiresAt
- revokedAt
```

Token ger endast rättighet att läsa presentationsdata.

Exempel:

```text
/present/AbC123...
```

Presentationsvyn kan öppnas i separat webbläsare/fönster på projektordator.

MVP kan initialt använda administratörssessionen om detta annars försenar implementationen, men tokenmodellen är bättre.

---

## 15. Importformat

JSON-format ska versionsmärkas.

Exempel:

```json
{
  "format": "survey-definition",
  "version": 1,
  "survey": {
    "title": "Workshop",
    "description": "...",
    "questions": [
      {
        "type": "SCALE",
        "text": "Hur tydligt är målet?",
        "required": true,
        "scale": {
          "min": 1,
          "max": 5,
          "minLabel": "Otydligt",
          "maxLabel": "Mycket tydligt"
        }
      }
    ]
  }
}
```

Import ska:

1. validera format/version,
2. validera alla frågetyper,
3. validera options/skala,
4. avvisa hela importen vid fel,
5. skapa ny Survey.

Import ska aldrig skriva över befintlig enkät implicit.

---

## 16. Komplett exportpaket

Rekommenderat ZIP-format:

```text
survey-export.zip
  manifest.json
  survey.json
  run.json
  responses.json
  responses.csv
```

`manifest.json`:

```json
{
  "format": "survey-package",
  "version": 1,
  "createdAt": "...",
  "applicationVersion": "..."
}
```

Detta ger ett format som både kan arkiveras och importeras senare.

---

## 17. CSV-export

Rekommenderad struktur:

```text
participant_session_id,
submitted_at,
Q1__teamwork,
Q2__recommend,
Q3__comments
```

Exempel:

```text
anon-001,2026-09-22T10:42:00Z,"Ja","Alternativ A;Alternativ C","Bra workshop"
```

För anonymitet bör participant_session_id i export vara ett separat export-ID, inte intern token/hash.

Flerval använder exempelvis:

```text
;
```

som intern separator i cellen.

CSV-export ska använda korrekt quoting enligt RFC 4180-liknande regler.

---

## 18. Databasconstraints

Minimikrav:

```text
survey_question:
  unique(survey_id, position)

question_option:
  unique(question_id, position)

survey_run:
  unique(public_id)
  unique(join_code)

survey_run_question:
  unique(run_id, position)

participant_session:
  unique(run_id, client_token_hash)

response:
  unique(participant_session_id, run_question_id)

survey_run_option:
  unique(run_question_id, position)
```

Foreign keys ska användas konsekvent.

---

## 19. API-principer

### Publikt API

```text
/api/public/**
```

Får endast exponera data som krävs för deltagande.

### Admin-API

```text
/api/admin/**
```

Kräver autentisering och authorization.

### Import/export

```text
/api/admin/import/**
/api/admin/export/**
```

### Felmodell

Gemensam struktur:

```json
{
  "code": "RUN_CLOSED",
  "message": "Enkäten är stängd.",
  "details": {}
}
```

Frontend ska inte bero på HTTP-textmeddelanden utan på stabil `code`.

---

## 20. Säkerhetsdesign

### Publika endpoints

Skydd:

- rate limiting
- request size limit
- input validation
- inga interna ID:n behöver exponeras där publicId räcker
- deltagartoken ska vara slumpmässig och lång
- token lagras hashead server-side

### Admin

- session-cookie
- CSRF-skydd där relevant
- authorization per resurs
- output escaping i UI
- Content Security Policy
- inga känsliga värden i frontend bundles

### Loggning

Undvik i applikationslogg:

- deltagartoken
- fritextsvar
- rå request body
- onödig IP-loggning

---

## 21. PWA-stöd

Tjänsten kan göras installerbar som PWA, men offline-first behöver inte ingå i MVP.

PWA kan ge:

- app-liknande känsla,
- snabb åtkomst för administratörer,
- ikon/startskärm.

Deltagare behöver normalt inte installera något.

---

## 22. Deployment

### Containerstruktur

```text
survey-service/
  frontend/
  backend/
  docker/
  compose.yaml
```

Runtime:

```text
frontend
backend
postgres
```

Alternativt kan frontend byggas statiskt och serveras av backend eller separat nginx-container.

### Rekommenderad MVP

För enkel drift:

```text
frontend -> nginx container
backend  -> Quarkus container
db       -> PostgreSQL container
```

Backend:

```text
8080
```

Frontend:

```text
80
```

Extern reverse proxy hanterar HTTPS.

---

## 23. Lokal utveckling

```text
docker compose up -d postgres
pnpm dev
./mvnw quarkus:dev
```

Alternativt:

```text
docker compose up
```

för full stack.

---

## 24. Miljövariabler

Backend:

```text
DB_URL
DB_USERNAME
DB_PASSWORD

APP_BASE_URL

SESSION_SECRET

ADMIN_BOOTSTRAP_USER
ADMIN_BOOTSTRAP_PASSWORD

CORS_ORIGINS
```

Frontend:

```text
VITE_API_BASE_URL
```

I produktionsdeploy kan API och frontend med fördel ligga på samma origin för att förenkla CORS.

---

## 25. Backup

PostgreSQL ska vara den enda persistenta primära datakällan.

Backup:

- pg_dump
- schemalagd backup i driftmiljö
- testad restore-process

Exporterade enkätpaket är inte ersättning för databasbackup.

---

## 26. Skalning

För MVP räcker en backendinstans.

Om flera backendinstanser senare används måste SSE-events delas mellan instanser.

Möjliga framtida lösningar:

- PostgreSQL LISTEN/NOTIFY
- Redis pub/sub
- message broker

Det ska inte införas innan behov finns.

---

## 27. Caching

Ingen avancerad cache behövs i MVP.

Enkätsnapshot och resultat kan läsas direkt från PostgreSQL.

Frontend kan använda normal query caching för UX.

---

## 28. Teststrategi

### Backend unit/integration

Testa minst:

- skapa survey
- snapshot till run
- participant session
- autosave
- required validation
- idempotent submit
- stängt run
- tidsstyrt run
- aggregation
- importvalidering
- export
- authorization

### Frontend

Testa minst:

- alla frågetyper
- required validation
- join code
- mobil layout
- autosave-status
- live summary
- chart rendering
- presentation mode

### End-to-end

Kritiska E2E-flöden:

1. Admin skapar survey.
2. Admin startar run.
3. Deltagare ansluter.
4. Deltagare svarar.
5. Admin ser aktivitet live.
6. Deltagare submit.
7. Admin ser resultat.
8. Export fungerar.
9. Survey importeras på nytt.

---

## 29. Observability

MVP bör minst ha:

- strukturerade backendloggar
- health endpoints
- readiness/liveness
- request correlation ID
- felräkning

Quarkus endpoints:

```text
/q/health/live
/q/health/ready
```

Metrics kan läggas till senare.

---

## 30. Rekommenderad projektstruktur

```text
survey-service/
├── README.md
├── compose.yaml
├── .env.example
├── docs/
│   ├── functional-specification.md
│   ├── risk-feasibility-analysis.md
│   └── architecture.md
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── admin/
│       ├── participant/
│       ├── presentation/
│       ├── api/
│       ├── components/
│       └── routes/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       │   └── db/migration/
│       └── test/
└── .github/
    └── workflows/
        └── ci.yml
```

---

## 31. Arkitekturbeslut

### ADR-001 – Modulär monolit

**Beslut:** En backendapplikation.

**Skäl:** Lägre komplexitet, tillräckligt för målbilden.

---

### ADR-002 – PostgreSQL

**Beslut:** PostgreSQL som primär databas.

**Skäl:** Transaktioner, constraints, välbeprövad drift och exportmöjligheter.

---

### ADR-003 – Snapshot av Survey Run

**Beslut:** Frågor och alternativ kopieras till run-specifika tabeller.

**Skäl:** Historisk stabilitet.

---

### ADR-004 – SSE för liveuppdatering

**Beslut:** Server-Sent Events för admin/presentation.

**Skäl:** Enklare än WebSocket och matchar server→klient-behovet.

---

### ADR-005 – Anonym participant token

**Beslut:** Slumpmässig token i klienten, hashad på servern.

**Skäl:** Återupptagning och best-effort dubblettskydd utan identifierat konto.

---

### ADR-006 – React + TypeScript

**Beslut:** React/TypeScript/Vite.

**Skäl:** Passar både deltagar-, admin- och presentationsgränssnitt väl.

---

### ADR-007 – Quarkus

**Beslut:** Quarkus/Java för backend.

**Skäl:** Bra containerstöd, REST, SSE, JPA, validation och health-funktioner i en kompakt plattform.

---

### ADR-008 – Containerbaserad drift

**Beslut:** OCI-containers och Compose-kompatibel deployment.

**Skäl:** Enkel lokal drift och enkel övergång till Podman, Coolify eller annan containerplattform.

---

## 32. Arkitekturrisker som återstår

Inga blockerande risker.

Följande ska verifieras tidigt i implementationen:

1. SSE fungerar stabilt genom vald reverse proxy.
2. autosave ger bra UX på mobil.
3. snapshot-modellen känns smidig i databas/API.
4. deltagartoken fungerar väl vid refresh och återbesök.
5. diagram fungerar bra i projektorläge.
6. exportformatet är praktiskt i Excel.

---

## 33. Rekommenderad MVP-sekvens för implementation

1. Repo + CI + containergrund
2. Databasmodell + Flyway
3. Survey CRUD
4. Survey editor
5. Survey Run + snapshot
6. Join code + public link
7. Participant session
8. Question rendering
9. Autosave
10. Submit
11. Result aggregation
12. SSE
13. Live admin view
14. Presentation mode
15. Import/export
16. Hardening
17. E2E-test
18. Release candidate

---

## 34. Definition of Done för arkitektursteget

Arkitektursteget anses klart när följande är fastslaget:

- komponentmodell,
- teknikstack,
- datamodell,
- API-principer,
- sessionsmodell,
- snapshotmodell,
- liveuppdatering,
- autentiseringsmodell,
- import/exportstrategi,
- containerstruktur,
- teststrategi,
- huvudsakliga ADR-beslut.

Samtliga är definierade i detta dokument.

---

## 35. Nästa steg

Nästa CREATE-steg bör vara **utvecklingsplan och initial backlog**.

Det steget bör bryta ner MVP:n till små implementerbara steg med:

- ordningsföljd,
- beroenden,
- mål per steg,
- konkreta leverabler,
- verifiering/test per steg,
- vilka steg som lämpar sig för samma PR respektive separata PR:er.
