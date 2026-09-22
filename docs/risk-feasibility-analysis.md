# Risk- och genomförbarhetsanalys – Enkättjänst

**Status:** CREATE – risk- och genomförbarhetssteg  
**Underlag:** Funktionell specifikation för MVP  
**Primärt användningsfall:** Liveenkäter i workshop  
**Sekundärt användningsfall:** Tidsbegränsade enkäter via distribuerad länk

## 1. Sammanfattning

Tjänsten är tekniskt okomplicerad att bygga som en modern webbapplikation med backend och persistent lagring. Den största risken ligger inte i frågehantering eller diagram, utan i kombinationen av:

- anonymitet,
- stöd mot dubbelsvar,
- live-status för pågående deltagare,
- robusthet vid nätverksavbrott,
- enkel användning i workshop.

Ingen identifierad risk blockerar MVP.

Den rekommenderade lösningen är att:

- skilja enkätmall från genomförande,
- använda anonym deltagarsession med slumpmässigt sessions-ID,
- lagra sessions-ID lokalt i webbläsaren,
- göra inlämning idempotent,
- använda server-side lagring för svar och deltagarstatus,
- använda Server-Sent Events eller WebSocket för liveuppdatering,
- aldrig använda IP-adress som primär deltagaridentifierare,
- behandla "pågående deltagare" som en approximativ status baserad på senaste aktivitet,
- använda en relationsdatabas som primär datalagring,
- hålla första driftprofilen containerbaserad och enkel.

## 2. Riskbedömningsskala

### Sannolikhet

- Låg
- Medel
- Hög

### Konsekvens

- Låg
- Medel
- Hög

### Prioritet

Riskprioritet bedöms som kombinationen av sannolikhet och konsekvens.

## 3. Identifierade huvudrisker

### RISK-001 – Anonymitet kontra skydd mot flera svar

**Sannolikhet:** Hög  
**Konsekvens:** Medel  
**Prioritet:** Hög

Det går inte att samtidigt garantera full anonymitet och exakt ett svar per fysisk person utan någon form av identifierande mekanism.

Ett lokalt sessions-ID kan hindra oavsiktliga dubbelsvar från samma webbläsare, men en deltagare kan fortfarande:

- byta webbläsare,
- använda privat läge,
- rensa lokal lagring,
- använda en annan enhet.

#### Rekommenderad hantering

MVP ska uttryckligen erbjuda:

> stöd mot oavsiktliga multipla svar

och inte:

> garanti om ett svar per person.

Teknisk modell:

- slumpmässigt genererat participantSessionId,
- lagring i webbläsarens lokala lagring,
- serverside unik constraint mellan participantSessionId och surveySessionId,
- idempotent submit-endpoint.

#### Senare förbättring

Engångskoder kan läggas till som ett separat läge där:

- koden verifieras,
- kodens användning markeras separat,
- koden inte lagras tillsammans med svarsinnehållet.

Det ger starkare dubblettskydd utan att svar behöver kopplas till en person.

---

### RISK-002 – IP-adresser och teknisk spårbarhet kan underminera upplevd anonymitet

**Sannolikhet:** Medel  
**Konsekvens:** Hög  
**Prioritet:** Hög

Även om applikationen inte samlar in namn eller e-post kan proxyloggar, webbserverloggar och infrastruktur logga IP-adresser.

#### Rekommenderad hantering

- använd inte IP-adress för deltagaridentifiering,
- logga inte deltagar-IP i applikationslogik om det inte krävs,
- dokumentera att infrastruktur kan ha tekniska accessloggar,
- minimera retention av sådana loggar,
- separera svarsinnehåll från teknisk requestloggning,
- undvik user-agent-fingerprinting.

MVP ska designas för dataminimering även i drift.

---

### RISK-003 – "Pågående deltagare" är inte ett exakt mått

**Sannolikhet:** Hög  
**Konsekvens:** Låg till medel  
**Prioritet:** Medel

En webbläsare kan stängas, gå offline eller lämnas öppen. Servern vet därför inte exakt om personen fortfarande aktivt svarar.

#### Rekommenderad hantering

Definiera aktiv deltagarsession som exempelvis:

> deltagarsession med aktivitet de senaste 60 sekunderna som ännu inte är slutförd.

Klienten skickar heartbeat medan formuläret är aktivt.

Administratörsgränssnittet ska använda formuleringar som:

- "Svarar nu"
- "Aktiva just nu"

inte:

- "Exakt antal personer i enkäten"

#### Rekommenderad första parameter

- heartbeat: var 20–30 sekund
- session räknas aktiv: senaste aktivitet <= 60–90 sekunder

Detta gör värdet tillräckligt stabilt för workshopbruk utan hög trafikbelastning.

---

### RISK-004 – Liveuppdatering blir onödigt komplex om fel teknik väljs

**Sannolikhet:** Medel  
**Konsekvens:** Medel  
**Prioritet:** Medel

Tvåvägskommunikation behövs inte för huvuddelen av administratörens livevy. Ett fullskaligt WebSocket-upplägg kan därför ge mer komplexitet än nödvändigt.

#### Alternativ

1. Polling
2. Server-Sent Events (SSE)
3. WebSocket

#### Rekommendation

För MVP rekommenderas **Server-Sent Events** för admin/presentationsvy.

Fördelar:

- server → klient är det huvudsakliga behovet,
- enklare än WebSocket,
- fungerar väl för resultat- och statusuppdatering,
- lättare att felsöka,
- automatisk återanslutning finns inbyggd i EventSource.

Polling är acceptabel fallback om SSE visar sig komplicerat i vald driftsmiljö.

WebSocket bör bara väljas om framtida behov motiverar tvåvägskommunikation i realtid.

---

### RISK-005 – Resultat kan visas inkonsekvent om aggregering görs fel

**Sannolikhet:** Medel  
**Konsekvens:** Medel  
**Prioritet:** Medel

Om resultat räknas om i klienten eller genom flera separata endpoint-anrop finns risk för temporärt inkonsistenta siffror.

#### Rekommenderad hantering

Servern ska vara auktoritativ för:

- antal påbörjade sessioner,
- antal slutförda sessioner,
- aggregerade resultat.

Klienten får färdiga aggregat eller hämtar dem från ett samlat endpoint.

Databastransaktioner används vid slutlig inlämning.

---

### RISK-006 – Samtidiga svar kan skapa race conditions

**Sannolikhet:** Medel  
**Konsekvens:** Medel  
**Prioritet:** Medel

Vid workshop kan tiotals deltagare skicka svar samtidigt.

#### Rekommenderad hantering

- relationsdatabas,
- transaktioner,
- unik constraint på deltagarsessionens slutliga submission,
- idempotency vid submit,
- inga read-modify-write-räknare utan databasstöd,
- aggregat ska räknas från svar eller uppdateras atomärt.

Detta är ett normalt backendproblem och innebär ingen särskild genomförbarhetsrisk.

---

### RISK-007 – Förlorade svar vid dålig nätverksanslutning

**Sannolikhet:** Medel  
**Konsekvens:** Hög  
**Prioritet:** Hög

Workshopmiljöer kan ha instabilt Wi‑Fi eller deltagare kan tillfälligt tappa anslutningen.

#### Rekommenderad hantering

Svar bör sparas successivt.

Rekommenderad modell:

- lokal browser state,
- autosave till server efter förändring med debounce,
- tydlig statusindikering:
  - sparat
  - sparar
  - offline/ej synkroniserat
- återförsök när anslutningen kommer tillbaka.

Full offline-first PWA är inte nödvändig för MVP.

---

### RISK-008 – Enkätmall ändras efter att resultat börjat samlas in

**Sannolikhet:** Hög  
**Konsekvens:** Hög  
**Prioritet:** Hög

Om ett aktivt genomförande refererar direkt till en redigerbar mall kan resultat bli omöjliga att tolka historiskt.

#### Rekommenderad hantering

När ett genomförande skapas ska systemet skapa en ögonblicksbild av:

- frågor,
- ordning,
- frågetyper,
- svarsalternativ,
- obligatoriskhet,
- skalinställningar.

Genomförandet läser därefter inte från den redigerbara mallen.

Detta är en central arkitekturprincip.

---

### RISK-009 – Kortkod kan krocka eller vara lätt att gissa

**Sannolikhet:** Medel  
**Konsekvens:** Låg till medel  
**Prioritet:** Medel

Mycket korta koder ger god användbarhet men högre risk för kollision och gissning.

#### Rekommenderad hantering

- 5–6 tecken,
- case-insensitive,
- undvik visuellt förväxlingsbara tecken som O/0 och I/1,
- unik constraint i databasen,
- retry vid kodkollision.

Exempel:

`K7M4Q`

Kortkoden ska vara en locator, inte ett säkerhetslösenord.

---

### RISK-010 – QR-kod via extern tjänst kan läcka enkätlänken

**Sannolikhet:** Låg  
**Konsekvens:** Medel  
**Prioritet:** Låg till medel

Om en extern QR-tjänst används skickas länken till tredje part.

#### Rekommenderad hantering

QR-koder ska genereras lokalt i applikationen med ett bibliotek.

---

### RISK-011 – CSV-export kan bli svårtolkad för flervalsfrågor

**Sannolikhet:** Hög  
**Konsekvens:** Låg  
**Prioritet:** Medel

Enkätsvar är inte naturligt helt platta.

#### Rekommenderad hantering

Definiera exportformatet tidigt.

Rekommenderat CSV-format:

- en rad per slutförd deltagarsession,
- en kolumn per fråga,
- flervalsalternativ kodas med dokumenterad separator,
- kolumnnamn innehåller stabilt question-id och läsbar titel.

JSON-exporten blir det kompletta formatet.

CSV är bekvämlighetsformat för Excel/analys.

---

### RISK-012 – Fritext kan innehålla känsligt eller identifierande innehåll

**Sannolikhet:** Medel  
**Konsekvens:** Medel till hög  
**Prioritet:** Medel

Systemet kan vara anonymt men deltagaren kan själv skriva namn eller andra identifierande uppgifter i fritext.

#### Rekommenderad hantering

- informera administratören om detta,
- möjlighet att skriva instruktion i enkäten,
- undvik att systemet automatiskt påstår att all insamlad information alltid är anonym,
- framtida möjlighet till moderering eller dölja fritext i presentationsläge.

MVP behöver inte automatiskt försöka avidentifiera fritext.

---

### RISK-013 – Presentationsläge kan oavsiktligt exponera fritext

**Sannolikhet:** Medel  
**Konsekvens:** Medel  
**Prioritet:** Medel

Fritext kan innehålla oväntade eller olämpliga formuleringar som inte bör projiceras direkt.

#### Rekommenderad hantering

Strukturerade svar kan visas live automatiskt.

Fritext bör i presentationsläge:

- inte visas automatiskt,
- kräva aktivt val av administratören,
- gärna kunna modereras senare.

För MVP rekommenderas att fritextresultat visas i adminvyn men inte automatiskt i projektorläge.

---

### RISK-014 – Stor mängd realtidsuppdateringar kan ge onödig belastning

**Sannolikhet:** Låg för MVP  
**Konsekvens:** Medel  
**Prioritet:** Låg

Typiska workshopvolymer är små.

#### Rekommenderad hantering

Första kapacitetsmål:

- minst 100 samtidiga deltagarsessioner per genomförande,
- minst 10 parallella administratörs-/presentationsanslutningar,
- liveuppdatering inom några sekunder.

Detta ger god marginal för normalt workshopbruk utan att göra arkitekturen överdimensionerad.

---

## 4. Genomförbarhet per huvudområde

### 4.1 Enkätredigering

**Bedömning:** Enkel

Vanlig CRUD med dynamiska frågetyper.

Risknivå: låg.

### 4.2 Deltagargränssnitt

**Bedömning:** Enkel till medel

Kräver god UX och autosave men inga tekniskt ovanliga lösningar.

Risknivå: låg till medel.

### 4.3 Anonym sessionshantering

**Bedömning:** Medel

Tekniskt enkel men kräver tydlig definition av vad anonymitet och dubblettskydd betyder.

Risknivå: medel.

### 4.4 Live-resultat

**Bedömning:** Medel

SSE eller enkel polling är fullt tillräckligt.

Risknivå: medel.

### 4.5 Diagram

**Bedömning:** Enkel

Kan lösas med etablerat frontendbibliotek eller egen enklare visualisering.

Risknivå: låg.

### 4.6 Import/export

**Bedömning:** Enkel till medel

JSON är okomplicerat. CSV kräver tydlig specifikation för flervalsfrågor.

Risknivå: låg.

### 4.7 Drift

**Bedömning:** Enkel

Tjänsten passar mycket väl som containeriserad webbapplikation.

Risknivå: låg.

## 5. Rekommenderad teknisk grundmodell

Detta är ännu inte full arkitektur, men riskanalysen pekar tydligt mot följande:

### Frontend

- React
- TypeScript
- responsivt gränssnitt
- mobil-first för deltagarflödet
- desktop/projector-optimerat admin- och presentationsläge

### Backend

- stateless webb-API så långt det är möjligt
- server-side autentisering för administratörer
- endpoints för deltagarsessioner och svar
- SSE för liveuppdateringar

### Databas

Relationsdatabas rekommenderas.

PostgreSQL är ett naturligt val.

Skäl:

- transaktioner,
- constraints,
- JSON-stöd där lämpligt,
- välkänt driftmönster,
- enkel backup/export.

### Grundläggande datamodell

- admin_user
- survey
- survey_question
- question_option
- survey_run
- survey_run_question
- survey_run_option
- participant_session
- response
- response_value

Alternativt kan genomförandets fråge-snapshot lagras som JSON, men en normaliserad modell ger bättre sökbarhet och tydligare integritet.

## 6. Rekommenderade beslut

### BESLUT-001

MVP ska använda anonym webbläsarsession, inte identifierad deltagare.

### BESLUT-002

Dubblettskydd i MVP är best-effort och inte identitetsverifiering.

### BESLUT-003

IP-adress ska inte användas som deltagarnyckel.

### BESLUT-004

En enkätmall ska snapshotas när ett genomförande skapas.

### BESLUT-005

Resultat och status ska beräknas server-side.

### BESLUT-006

Liveuppdatering implementeras primärt med SSE.

### BESLUT-007

Pågående deltagare definieras genom heartbeat/senaste aktivitet.

### BESLUT-008

Svar ska autosparas.

### BESLUT-009

Fritext ska inte automatiskt exponeras i presentationsläge.

### BESLUT-010

JSON är primärt portabelt exportformat; CSV är analysformat.

### BESLUT-011

Första kapacitetsmål är minst 100 samtidiga deltagare per enkätgenomförande.

### BESLUT-012

PostgreSQL används som primär databas om inget särskilt driftkrav talar emot det.

## 7. Säkerhets- och integritetsbedömning för MVP

Tjänsten hanterar sannolikt inte känsliga uppgifter avsiktligt, men fritext innebär att sådana uppgifter ändå kan förekomma.

Minimikrav:

- HTTPS
- administratörsautentisering
- server-side authorization
- skydd mot CSRF där relevant
- parametriserade databasfrågor/ORM
- output escaping
- Content Security Policy där praktiskt möjligt
- rate limiting på publika endpoints
- begränsning av requeststorlek
- inga externa script från opålitliga CDN:er i känsliga flöden
- backup av databas
- säker hantering av adminsessioner

Deltagarendpoint behöver skydd mot missbruk, men CAPTCHA rekommenderas inte som standard eftersom det försämrar workshopflödet kraftigt.

## 8. Driftalternativ

### Alternativ A – Docker/Podman Compose

Frontend/backend/databas körs som containers.

**Fördelar**

- enkelt att installera,
- portabelt,
- lämpar sig för egen drift,
- passar intern miljö.

**Nackdelar**

- backup och uppgraderingar måste hanteras.

**Bedömning:** Stark kandidat.

### Alternativ B – Coolify

Samma containerlösning körs via Coolify.

**Fördelar**

- enkel deployment,
- HTTPS och domänhantering,
- bra för mindre egenhostad tjänst.

**Nackdelar**

- lägger till plattformsberoende i driftmanualen.

**Bedömning:** Bra distributionsprofil, men systemet ska inte byggas beroende av Coolify.

### Alternativ C – Publik molntjänst

Tekniskt enkelt, men inte nödvändigt för MVP.

**Bedömning:** Arkitekturen bör tillåta detta utan att kräva det.

## 9. Föreslagen retentionmodell

MVP bör inte automatiskt radera data utan ett tydligt administrativt beslut.

Rekommenderat:

- enkäter och resultat sparas tills administratören raderar dem,
- UI visar skapandedatum och senaste aktivitet,
- framtida version kan lägga till automatisk retention.

Detta är enklare och mindre överraskande för användaren.

## 10. Rekommenderat beteende när enkät stängs

Den funktionella specifikationen lämnade detta öppet.

Rekommendation:

- inga nya deltagarsessioner får skapas efter stängning,
- redan startade sessioner får en kort respitperiod, exempelvis 5 minuter,
- administratören kan välja "Stäng omedelbart" om det behövs.

För MVP kan det förenklas till:

> påbörjade sessioner får slutföra efter stängning, men inga nya sessioner får starta.

Det är mer användarvänligt i workshop och kräver mindre överraskande beteende.

## 11. Rekommenderad adminautentisering

Autentisering är inte kärnfunktionalitet och bör hållas enkel.

Möjliga första alternativ:

- lokal användare + lösenord,
- OIDC/OAuth via extern identitetsleverantör,
- reverse-proxy-baserad autentisering i intern drift.

För MVP rekommenderas att arkitekturen stödjer utbytbar autentisering, men att första implementationen använder en enkel etablerad lösning.

Om tjänsten ska distribueras brett är OIDC ett bättre långsiktigt mål än egen avancerad användarhantering.

## 12. Risker som uttryckligen inte behöver lösas i MVP

- avancerat skydd mot avsiktligt röstfusk,
- full offline-funktionalitet,
- multi-region-drift,
- extrem skalning,
- automatisk anonymisering av fritext,
- avancerad analys,
- AI-tolkning av svar,
- SSO för många identitetsleverantörer,
- realtidsredigering mellan flera administratörer.

## 13. Samlad genomförbarhetsbedömning

### Funktionell genomförbarhet

**Hög**

Samtliga MVP-krav kan implementeras med etablerad webbteknik.

### Teknisk komplexitet

**Låg till medel**

Livefunktion och anonym sessionshantering kräver omsorg, men ingen ny eller riskfylld teknik.

### Driftskomplexitet

**Låg**

En containeriserad applikation med PostgreSQL är tillräcklig.

### Integritetskomplexitet

**Medel**

Främst på grund av skillnaden mellan faktisk anonymitet, tekniska loggar och deltagarnas möjlighet att skriva identifierande fritext.

### Skalbarhet

**God för målbilden**

Arkitekturen kan enkelt hantera workshopvolymer och betydligt mer utan speciallösningar.

## 14. Slutsats

Inga blockerande risker har identifierats.

Projektet bör gå vidare med följande arkitekturprinciper låsta:

1. Survey och Survey Run är separata.
2. Survey Run innehåller snapshot av frågorna.
3. Deltagare är anonyma och saknar konto.
4. Participant Session är en slumpmässig teknisk session.
5. Dubblettskydd är best-effort i MVP.
6. Svar autosparas.
7. Submit är idempotent.
8. Liveuppdatering sker via SSE eller likvärdig enkel mekanism.
9. Aktiv deltagare beräknas via senaste aktivitet.
10. Resultat aggregeras server-side.
11. PostgreSQL används som primär lagring.
12. Tjänsten paketeras containerbaserat.
13. Fritext exponeras inte automatiskt i presentationsläge.

## 15. Nästa steg

Nästa CREATE-steg bör vara **systemarkitektur och teknisk design**.

Det steget bör konkretisera:

- komponenter,
- frontend/backend-ansvar,
- API-struktur,
- datamodell,
- liveflöde,
- autentisering,
- deltagarsessionsflöde,
- import/exportformat,
- container- och driftstruktur,
- rekommenderad teknikstack.
