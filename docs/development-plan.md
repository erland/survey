# Utvecklingsplan och initial backlog – Enkättjänst

**Status:** CREATE – utvecklingsplan och initial backlog  
**Underlag:** Funktionell specifikation, riskanalys och systemarkitektur  
**Mål:** Implementera MVP i små, verifierbara steg

---

## 1. Principer för genomförandet

Utvecklingen ska följa dessa principer:

- Ett steg ska vara tillräckligt litet för att kunna verifieras isolerat.
- Varje steg ska lämna repot i byggbart skick.
- Datamodell och API ska införas före beroende UI.
- Kritiska arkitekturrisker ska verifieras tidigt.
- CI ska etableras innan större funktionalitet byggs.
- End-to-end-flödet ska bli användbart tidigt, även innan alla funktioner finns.
- Samma PR får innehålla flera små steg när de hör tätt ihop och fortfarande är lätta att granska.
- Större riskområden bör separeras i egna PR:er.

---

## 2. Milstolpar

### M1 – Teknisk grund

Mål:

- repo
- frontend/backend
- databas
- CI
- containerdrift

### M2 – Enkätmodell

Mål:

- skapa/redigera enkät
- frågor
- svarsalternativ

### M3 – Genomförande

Mål:

- skapa Survey Run
- snapshot
- join code
- publik länk

### M4 – Deltagarflöde

Mål:

- anonym deltagarsession
- frågevisning
- autosave
- submit

### M5 – Resultat och live

Mål:

- aggregering
- live status
- diagram
- presentation

### M6 – Import/export

Mål:

- JSON-import
- JSON/CSV-export
- komplett exportpaket

### M7 – Hardening och RC

Mål:

- säkerhet
- UX
- E2E
- dokumentation
- release candidate

---

# 3. Detaljerad utvecklingsplan

## Steg 1 – Bootstrap repo

### Mål

Skapa grundstruktur för projektet.

### Leverabler

- `frontend/`
- `backend/`
- `docs/`
- `.github/workflows/`
- `compose.yaml`
- `.env.example`
- README

### Verifiering

- frontend kan byggas
- backend kan byggas
- compose-fil valideras

### PR

**PR-1: bootstrap**

---

## Steg 2 – CI

### Mål

Säkerställa att alla framtida ändringar verifieras automatiskt.

### Leverabler

GitHub Actions som kör:

- backend build
- backend test
- frontend install
- frontend build
- frontend test
- container build, om praktiskt

### Verifiering

- CI passerar på tom/grundleverans
- CI fallerar vid avsiktligt brutet test

### PR

Kan ligga i **PR-1** tillsammans med bootstrap.

---

## Steg 3 – PostgreSQL och Flyway

### Mål

Etablera databas och migrationsmodell.

### Leverabler

- PostgreSQL i compose
- backend datasource config
- Flyway
- första migration
- health/readiness mot databas

### Verifiering

- databasen startar
- migration körs automatiskt
- backend startar mot tom databas

### PR

Kan ligga i **PR-1** om bootstrap fortfarande är liten, annars **PR-2**.

---

## Steg 4 – Grundläggande adminautentisering

### Mål

Skydda administrativa endpoints tidigt.

### Leverabler

- enkel adminmodell
- login
- logout
- säker sessionscookie
- skydd för `/api/admin/**`

### Verifiering

- oautentiserad användare nekas
- autentiserad användare får access
- logout invalidiserar session

### PR

**PR-2: admin-auth**

---

## Steg 5 – Survey-datamodell

### Mål

Implementera enkätmallar och frågor.

### Leverabler

Tabeller/entities:

- survey
- survey_question
- question_option

### Verifiering

- migrationer fungerar
- constraints fungerar
- repositories testas

### PR

**PR-3: survey-domain**

---

## Steg 6 – Survey REST API

### Mål

CRUD för enkätmall.

### Leverabler

Endpoints för:

- list
- get
- create
- update
- delete
- copy

### Verifiering

- API integrationstest
- validation
- ownership/authorization

### PR

Samma **PR-3**.

---

## Steg 7 – Adminöversikt för enkäter

### Mål

Visa befintliga enkäter.

### Leverabler

- survey list page
- skapa ny
- öppna/redigera
- kopiera
- radera

### Verifiering

- frontend-test
- manuell verifiering i desktop/mobile

### PR

**PR-4: survey-admin-ui**

---

## Steg 8 – Survey editor

### Mål

Skapa/redigera samtliga frågetyper.

### Leverabler

UI för:

- fritext
- ja/nej
- enkelval
- flerval
- skala
- required
- ordning
- svarsalternativ

### Verifiering

- alla typer kan sparas
- redigering återläser korrekt
- validation

### PR

Samma **PR-4** eller separat **PR-5** om editorn blir stor.

---

## Steg 9 – Survey Run-datamodell

### Mål

Införa faktiska enkätgenomföranden.

### Leverabler

- survey_run
- survey_run_question
- survey_run_option

### Verifiering

- migration
- entity/repository tests

### PR

**PR-5: survey-run**

---

## Steg 10 – Snapshot

### Mål

Kopiera enkätdefinition till ett genomförande.

### Leverabler

Service som:

1. skapar run
2. kopierar frågor
3. kopierar options
4. kör allt i transaktion

### Verifiering

- snapshot matchar mall
- senare ändring av mall påverkar inte run
- rollback vid fel

### PR

Samma **PR-5**.

---

## Steg 11 – Run status och tidsstyrning

### Mål

Stöd för:

- DRAFT
- SCHEDULED
- OPEN
- CLOSED

### Leverabler

- open
- close
- opensAt
- closesAt
- validering av om run tar emot nya deltagare

### Verifiering

- tidsfall testas
- stängd run blockerar nya sessioner

### PR

Samma **PR-5**.

---

## Steg 12 – Public ID och join code

### Mål

Göra genomförandet åtkomligt för deltagare.

### Leverabler

- publicId
- joinCode
- join lookup API
- unik kodgenerering

### Verifiering

- kodkollision hanteras
- ogiltig kod ger begripligt fel
- stängd run hanteras korrekt

### PR

Samma **PR-5**.

---

## Steg 13 – QR-kod

### Mål

Visa QR-kod utan extern tjänst.

### Leverabler

- frontend- eller backendgenererad QR-kod
- kopierbar direktlänk
- join code

### Verifiering

- QR-kod skannas med vanlig mobilkamera
- länken leder till rätt run

### PR

Kan ligga i **PR-5** eller liten separat **PR-6**.

---

## Steg 14 – Participant Session

### Mål

Införa anonym deltagarsession.

### Leverabler

- participant_session
- kryptografisk klienttoken
- hashad lagring
- localStorage
- återupptagning

### Verifiering

- första besök skapar session
- refresh återanvänder session
- token i klartext lagras inte server-side

### PR

**PR-6: participant-session**

---

## Steg 15 – Deltagarvy

### Mål

Visa enkätens snapshot-frågor.

### Leverabler

Mobil-first UI för:

- text
- yes/no
- single choice
- multiple choice
- scale

### Verifiering

- fungerar på telefonbredd
- tangentbordsnavigering
- required markeras tydligt

### PR

**PR-7: participant-ui**

---

## Steg 16 – Autosave

### Mål

Spara svar löpande.

### Leverabler

- response
- response_value
- autosave endpoint
- debounce i frontend
- sparstatus

### Verifiering

- text autosparas utan request per tangent
- svar återställs efter refresh
- felstatus visas vid misslyckad save

### PR

**PR-8: responses**

---

## Steg 17 – Submit

### Mål

Slutföra en enkät.

### Leverabler

- required validation server-side
- submit endpoint
- idempotent submit
- confirmation page

### Verifiering

- dubbel submit skapar inte dubbelt svar
- required-fel returneras korrekt
- submitted session återanvänds inte som nytt svar

### PR

Samma **PR-8**.

---

## Steg 18 – Heartbeat och aktiv session

### Mål

Mäta "svarar just nu".

### Leverabler

- heartbeat endpoint
- `lastActivityAt`
- active = senaste 90 sek
- heartbeat ca var 30 sek

### Verifiering

- aktiv räknas upp
- inaktiv session faller bort efter timeout
- submitted räknas inte som aktiv

### PR

**PR-9: live-foundation**

---

## Steg 19 – Resultataggregat

### Mål

Beräkna resultat server-side.

### Leverabler

Aggregat för:

- yes/no
- single choice
- multiple choice
- scale
- text list

### Verifiering

- counts testas
- multiple choice räknas korrekt
- responseCount är tydligt definierad

### PR

Samma **PR-9**.

---

## Steg 20 – Live summary

### Mål

Visa:

- påbörjade
- aktiva
- slutförda

### Leverabler

- summary endpoint
- admin live page

### Verifiering

- siffror stämmer mot testdata
- uppdatering fungerar efter deltagaraktivitet

### PR

Samma **PR-9**.

---

## Steg 21 – SSE proof-of-concept

### Mål

Verifiera livearkitekturen tidigt.

### Leverabler

- SSE endpoint
- reconnect
- event vid participant update
- event vid submit

### Verifiering

- fungerar lokalt
- fungerar genom reverse proxy
- reconnect fungerar

### PR

Bör vara del av **PR-9**.

### Viktigt

Om SSE visar sig instabilt i vald runtime ska polling användas som fallback innan mer komplex teknik införs.

---

## Steg 22 – Resultatdiagram

### Mål

Visa strukturerade resultat grafiskt.

### Leverabler

- ja/nej staplar
- enkelval
- flerval
- skala

### Verifiering

- korrekt antal
- läsbart på laptop och projektor
- fungerar även med många alternativ

### PR

**PR-10: result-ui**

---

## Steg 23 – Fritextresultat

### Mål

Visa fritext i adminläge.

### Leverabler

- listvy
- enkel sortering, exempelvis senaste/först

### Verifiering

- HTML escapad
- långa svar hanteras

### PR

Samma **PR-10**.

---

## Steg 24 – Presentationsläge

### Mål

Projektoranpassad vy.

### Leverabler

- ren layout
- summary
- vald fråga
- diagram
- fritext visas inte automatiskt

### Verifiering

- fullskärm fungerar
- inga admineditkontroller
- liveuppdatering

### PR

**PR-11: presentation-mode**

---

## Steg 25 – Presentation token

### Mål

Kunna visa projektorvy utan full adminsession.

### Leverabler

- presentation token
- read-only scope
- expiry/revoke

### Verifiering

- token kan inte anropa admin-CRUD
- revokerad token nekas

### PR

Kan ligga i **PR-11** eller senare om MVP behöver förenklas.

---

## Steg 26 – Survey JSON-export

### Mål

Exportera enkätdefinition.

### Leverabler

- format version 1
- validerat JSON
- download

### Verifiering

- export matchar enkät
- version finns

### PR

**PR-12: import-export**

---

## Steg 27 – Survey JSON-import

### Mål

Importera enkätdefinition.

### Leverabler

- schema validation
- full rollback vid fel
- ny survey skapas

### Verifiering

- export→import roundtrip
- trasig fil avvisas
- okänd version avvisas tydligt

### PR

Samma **PR-12**.

---

## Steg 28 – Resultat JSON-export

### Mål

Komplett maskinläsbar resultatexport.

### Leverabler

- run metadata
- questions
- responses
- timestamps

### Verifiering

- samtliga typer representeras korrekt

### PR

Samma **PR-12**.

---

## Steg 29 – CSV-export

### Mål

Excelvänlig export.

### Leverabler

- en rad per submitted participant
- kolumn per fråga
- escaping
- flervalsseparator

### Verifiering

- öppnas korrekt i Excel/LibreOffice
- åäö och kommatecken fungerar
- multiline fritext fungerar

### PR

Samma **PR-12**.

---

## Steg 30 – Komplett exportpaket

### Mål

Arkiveringsbart paket.

### Leverabler

ZIP:

- manifest.json
- survey.json
- run.json
- responses.json
- responses.csv

### Verifiering

- paket kan packas upp
- manifest matchar innehåll

### PR

Samma **PR-12**.

---

## Steg 31 – UX-pass

### Mål

Optimera primära workshopflödet.

### Fokus

Admin:

```text
Survey → Starta → Visa QR/kod → Live → Presentera
```

Participant:

```text
Skanna → Svara → Skicka → Klar
```

### Verifiering

- inga onödiga steg
- tydliga felmeddelanden
- mobiltest

### PR

**PR-13: ux-hardening**

---

## Steg 32 – Accessibility-pass

### Mål

Grundläggande tillgänglighet.

### Leverabler

- labels
- semantic controls
- keyboard navigation
- focus states
- contrast
- diagram med textalternativ

### Verifiering

- tangentbord
- enkel axe/Playwright accessibility check om möjligt

### PR

Samma **PR-13**.

---

## Steg 33 – Security hardening

### Mål

Förbereda för produktionsdrift.

### Leverabler

- rate limiting
- request size limits
- CSP
- secure cookie config
- logging review
- token leak review
- authorization tests

### Verifiering

- publika endpoints går inte att använda för admin-data
- tokens/fritext finns inte i loggar
- brute request begränsas rimligt

### PR

**PR-14: security-hardening**

---

## Steg 34 – E2E

### Mål

Verifiera hela systemet.

### Kritiska scenarier

1. Admin login
2. Create survey
3. Create questions
4. Start run
5. Join
6. Answer
7. Autosave
8. Submit
9. Live result
10. Presentation
11. Export
12. Import

### PR

**PR-15: e2e**

---

## Steg 35 – Deploymentprofil

### Mål

Göra tjänsten enkel att driftsätta.

### Leverabler

- production compose
- `.env.example`
- volumes
- healthcheck
- reverse proxy documentation
- HTTPS assumptions
- backup notes

### Verifiering

- full deployment på ren Docker/Podman-miljö
- backend/frontend health fungerar

### PR

**PR-16: deployment**

---

## Steg 36 – RC

### Mål

Skapa första release candidate.

### Leverabler

- version
- changelog
- release notes
- migrations verifierade
- installation testad
- kända begränsningar dokumenterade

### Verifiering

- CI grön
- E2E grön
- ren installation fungerar
- backup/restore smoke test

### PR

**PR-17: release-candidate**

---

# 4. Initial backlog

## Epic A – Platform

- A1 Repo bootstrap
- A2 CI
- A3 PostgreSQL
- A4 Flyway
- A5 Container images
- A6 Health checks

## Epic B – Authentication

- B1 Admin user
- B2 Login/logout
- B3 Session handling
- B4 Authorization

## Epic C – Survey Authoring

- C1 Survey CRUD
- C2 Text question
- C3 Yes/no question
- C4 Single choice
- C5 Multiple choice
- C6 Scale
- C7 Required
- C8 Reorder
- C9 Copy survey

## Epic D – Survey Runs

- D1 Create run
- D2 Snapshot
- D3 Open/close
- D4 Scheduling
- D5 Public ID
- D6 Join code
- D7 QR code

## Epic E – Participant

- E1 Participant session
- E2 Resume
- E3 Question rendering
- E4 Autosave
- E5 Required validation
- E6 Submit
- E7 Completion page

## Epic F – Live

- F1 Heartbeat
- F2 Active calculation
- F3 Started/submitted summary
- F4 SSE
- F5 Reconnect

## Epic G – Results

- G1 Yes/no aggregation
- G2 Single-choice aggregation
- G3 Multiple-choice aggregation
- G4 Scale aggregation
- G5 Text list
- G6 Admin result page
- G7 Charts

## Epic H – Presentation

- H1 Presentation layout
- H2 Live summary
- H3 Question selection
- H4 Presentation token
- H5 Hide text by default

## Epic I – Import/Export

- I1 Survey JSON export
- I2 Survey JSON import
- I3 Result JSON
- I4 Result CSV
- I5 Package ZIP
- I6 Format versioning

## Epic J – Hardening

- J1 Mobile UX
- J2 Accessibility
- J3 Security
- J4 Rate limiting
- J5 Logging review
- J6 E2E
- J7 Deployment docs
- J8 Backup/restore

---

# 5. Kritisk väg

Den funktionella kritiska vägen är:

```text
Bootstrap
  ↓
Database
  ↓
Survey model/API
  ↓
Survey editor
  ↓
Survey Run + Snapshot
  ↓
Participant Session
  ↓
Question UI
  ↓
Autosave + Submit
  ↓
Aggregation
  ↓
Live/SSE
  ↓
Presentation
  ↓
Import/Export
  ↓
Hardening
```

Import/export kan delvis utvecklas parallellt efter att Survey Run och responses är stabila.

---

# 6. Tidiga riskverifieringar

Följande ska verifieras tidigt och inte skjutas till slutet:

## Riskprov 1 – SSE genom reverse proxy

Görs senast i Steg 21.

## Riskprov 2 – mobil deltagarvy

Görs redan i Steg 15.

## Riskprov 3 – autosave med nätverksfel

Görs i Steg 16.

## Riskprov 4 – snapshot integrity

Görs i Steg 10.

## Riskprov 5 – idempotent submit

Görs i Steg 17.

## Riskprov 6 – CSV i Excel

Görs i Steg 29.

---

# 7. Rekommenderad PR-struktur

```text
PR-1  bootstrap + CI + DB base
PR-2  admin auth
PR-3  survey domain + API
PR-4  survey admin UI/editor
PR-5  survey run + snapshot + sharing
PR-6  participant session
PR-7  participant UI
PR-8  responses + autosave + submit
PR-9  live foundation + SSE + aggregation
PR-10 result UI + charts
PR-11 presentation mode
PR-12 import/export
PR-13 UX + accessibility
PR-14 security hardening
PR-15 E2E
PR-16 deployment
PR-17 release candidate
```

Detta är en rekommendation, inte ett krav. Små intilliggande PR:er kan slås ihop om implementationen förblir lätt att granska.

---

# 8. MVP-cutline

Följande krävs före första RC:

- Survey CRUD
- alla fem frågetyper
- Survey Run
- snapshot
- join link/code/QR
- anonym Participant Session
- resume
- autosave
- submit
- live counts
- strukturerade resultat
- textlista i admin
- presentation mode
- JSON import/export
- CSV result export
- containerdrift
- admin auth
- grundläggande säkerhet
- E2E på huvudflödet

Följande kan flyttas efter första RC om tid kräver:

- presentation token
- fullständig PWA-installation
- avancerad accessibility automation
- avancerad rate limiting
- automatisk retention
- engångskoder
- jämförelse mellan runs

---

# 9. Definition of Done per utvecklingssteg

Ett steg räknas som klart när:

1. implementation finns,
2. relevanta tester finns,
3. CI är grön,
4. dokumentation uppdateras om beteendet ändras,
5. inga kända blockerande fel återstår,
6. stegets acceptanspunkt kan demonstreras.

---

# 10. Rekommenderat första implementationssteg

Det första konkreta kodsteget bör vara:

> **Bootstrap repo + CI + PostgreSQL/Flyway-grund**

Det ger en stabil bas innan domänfunktionalitet införs.

Resultatet efter första implementationssteget bör vara ett repo där:

- frontend bygger,
- backend bygger,
- tester körs,
- PostgreSQL startar,
- Flyway kör en första migration,
- hela grunden verifieras i CI.

---

# 11. Nästa steg

Nästa CREATE-steg bör vara **initial implementation/bootstrap**.

Det innebär att skapa den faktiska projektstrukturen med:

- React/TypeScript/Vite frontend,
- Quarkus backend,
- PostgreSQL,
- Flyway,
- Docker Compose,
- GitHub Actions CI,
- README och grundläggande utvecklingsinstruktioner.
