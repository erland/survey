# Survey Service 0.1.0-rc.1

## Syfte

0.1.0-rc.1 är den första sammanhängande release candidate-versionen av Survey Service. Fokus är live-workshops med anonymt deltagande, enkel administratörshantering och direkt visning av resultat.

## Huvudflöde

1. Administratören loggar in.
2. En enkät skapas eller importeras.
3. Ett workshopgenomförande startas från enkätmallen.
4. Deltagare ansluter via QR-kod, direktlänk eller kortkod.
5. Svar sparas löpande och skickas in anonymt.
6. Administratören följer status och resultat live.
7. Resultat kan visas i read-only presentationsläge.
8. Enkätdefinition och resultat kan exporteras för vidare hantering eller arkivering.

## Teknik

- React 19 + TypeScript + Vite
- Quarkus / Java 21
- PostgreSQL 17 + Flyway
- Server-Sent Events för liveuppdateringar
- Playwright för E2E
- Produktionsprofil med Nginx och Docker Compose

## Säkerhetsbaslinje

RC:n innehåller SameSite=Strict för admincookie, same-origin-kontroll för skrivande adminanrop, API-säkerhetsheaders, begränsning av requeststorlek samt enkel rate limiting.

Deltagartoken lagras endast hashad på serversidan. Presentationstoken lagras också endast hashad.

## Verifiering

GitHub Actions kör:

- backendtest och Maven verify
- frontendtest
- frontend production build
- Playwright E2E med PostgreSQL, backend och frontend

RC ska inte taggas förrän samtliga CI-jobb är gröna på den slutliga RC-committen.

## Kända begränsningar

- Anonymitet innebär att exakt en-person-ett-svar inte kan garanteras.
- Rate limiting är per instans och delas inte mellan flera backendnoder.
- TLS-certifikat hanteras av extern reverse proxy, inte av applikationscontainrarna.
- Horisontell skalning av live/SSE-flödet kräver ytterligare design för delad eventdistribution.
- Backupscript finns, men automatisk schemaläggning, rotation och off-site-lagring ingår inte i denna RC.
- Administratörsautentisering är lokal i RC:n; OIDC är en möjlig senare utveckling.

## Föreslagen releaseprocedur efter merge

1. Verifiera att CI på `main` är grön.
2. Skapa taggen `v0.1.0-rc.1`.
3. Skapa GitHub prerelease med denna release note som grund.
4. Verifiera att release-workflowen publicerar:
   - `ghcr.io/erland/survey-backend:0.1.0-rc.1`
   - `ghcr.io/erland/survey-frontend:0.1.0-rc.1`
5. Bygg eller dra images och starta produktionsprofilen i målmiljön.
6. Kör smoke test enligt RC-checklistan.
