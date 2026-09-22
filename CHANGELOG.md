# Changelog

Alla större ändringar i Survey Service dokumenteras här.

## [0.1.0-rc.1] - 2026-09-22

Första release candidate för workshop- och enkätflödet.

### Tillagt

- Lokal administratörsinloggning med hashade lösenord och server-side sessioner.
- Enkätmallar med fritext, ja/nej, enkelval, flerval och skala.
- Survey Run med immutable snapshot av frågor och svarsalternativ.
- Delning via direktlänk, kortkod och QR-kod.
- Anonyma deltagarsessioner med hashade klienttoken.
- Autosave, heartbeat, obligatoriska frågor och idempotent submit.
- Live-summary, SSE och fallback-polling.
- Resultatdiagram och fritextresultat.
- Read-only presentationsläge med tidsbegränsad presentationstoken.
- Import/export av enkätdefinitioner.
- Resultatexport som JSON och CSV.
- Komplett arkiveringspaket som ZIP.
- UX- och tillgänglighetsförbättringar.
- Säkerhetsbaslinje med CSRF-origin-kontroll, SameSite-cookie, rate limiting och säkerhetsheaders.
- Playwright-baserat E2E-flöde i CI.
- Produktionsprofil med Docker Compose, Nginx, Quarkus och PostgreSQL.
- Databasbackup och restore-skript.

### Korrigerat under RC-förberedelser

- JAX-RS route-konflikter för Survey Run och publika lookup-endpoints.
- Bodyless POST-anrop som tidigare kunde ge HTTP 415.
- Adminprincipal transporteras nu via request-scoped context.
- Säkerhetsfilter normaliserar request paths.
- Frontend skickar endast JSON Content-Type när request body finns.
- Autosave uppdaterar inte längre den versionerade deltagarsessionen och undviker optimistic-lock-kollisioner med heartbeat.

### Kända begränsningar

- Deltagande är anonymt och skyddet mot dubbla svar är best-effort, inte identitetskontroll.
- In-memory rate limiting gäller per applikationsinstans.
- Presentationstoken är read-only men ska behandlas som en hemlig länk tills den löper ut eller återkallas.
- Produktionsdrift förutsätter extern HTTPS-terminering och korrekt proxyhantering enligt `docs/deployment.md`.
- Backup/restore är PostgreSQL-baserat och har inte automatiserats med retention eller schemaläggning.
- Projektet har ännu ingen formell stabil 1.0-kompatibilitetsgaranti.
