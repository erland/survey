# Fleranvändarstöd och enkätkonton – arkitektur och migreringsplan

## 1. Mål

Införa fleranvändarstöd utan att ändra deltagarnas anonyma flöde.

Administrativ data ska ägas av ett **enkätkonto** (survey account), inte av en enskild administratör. En administratör kan vara medlem i ett eller flera enkätkonton och får endast se data i de konton där medlemskap finns.

## 2. Roller

### Systemadministratör

Systemadministratören är den användare som skapats via bootstrap-konfigurationen.

Endast systemadministratören får:

- skapa nya enkätkonton,
- skapa eller koppla den första enkätadministratören till ett nytt enkätkonto,
- hantera nödsituationer där ett konto annars saknar administratör.

Vanliga enkätadministratörer får inte skapa nya enkätkonton.

### Enkätadministratör

En enkätadministratör är medlem i ett eller flera enkätkonton.

En enkätadministratör får inom ett konto där medlemskap finns:

- skapa, redigera, kopiera och radera enkäter,
- skapa och administrera enkätgenomföranden,
- läsa resultat och exportera data,
- lägga till befintliga eller nya enkätadministratörer,
- ta bort andra enkätadministratörers medlemskap.

Ett enkätkonto får aldrig lämnas utan minst en enkätadministratör.

## 3. Datamodell

### admin_user

Befintlig tabell behålls som global identitet.

Nya fält:

- `system_admin BOOLEAN NOT NULL DEFAULT FALSE`
- `active BOOLEAN NOT NULL DEFAULT TRUE`

### survey_account

Ny tabell:

- `id UUID PRIMARY KEY`
- `name VARCHAR(300) NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

### survey_account_admin

Ny kopplingstabell:

- `survey_account_id UUID NOT NULL`
- `admin_user_id UUID NOT NULL`
- `role VARCHAR(32) NOT NULL DEFAULT 'ADMIN'`
- `created_at TIMESTAMPTZ NOT NULL`
- primary key på `(survey_account_id, admin_user_id)`

Första versionen använder endast rollen `ADMIN`, men role-fältet behålls för framtida `EDITOR`/`VIEWER`.

### survey

Nuvarande `owner_id` ersätts av:

- `survey_account_id UUID NOT NULL`
- `created_by_admin_user_id UUID NULL`

`survey_account_id` anger ägarskap.
`created_by_admin_user_id` är endast auditinformation och får inte styra behörighet.

## 4. Ägarskap

All verksamhetsdata ska härledas från enkätkontot:

```text
survey_account
  |
  +-- survey
       |
       +-- survey_run
            |
            +-- participant_session
            +-- response
            +-- result/export
```

Behörighet ska aldrig baseras på vem som skapade enkäten.

## 5. Auktorisering

Varje administrativ request ska ha ett explicit `accountId`.

Rekommenderad API-struktur:

```text
GET  /api/admin/accounts
GET  /api/admin/accounts/{accountId}
GET  /api/admin/accounts/{accountId}/surveys
POST /api/admin/accounts/{accountId}/surveys
GET  /api/admin/accounts/{accountId}/runs/{runId}
GET  /api/admin/accounts/{accountId}/admins
POST /api/admin/accounts/{accountId}/admins
DELETE /api/admin/accounts/{accountId}/admins/{adminUserId}
```

Systemadministratörsfunktioner:

```text
POST /api/system/accounts
GET  /api/system/accounts
```

Backend ska för varje account-scoped request verifiera:

1. giltig administratörssession,
2. aktiv användare,
3. medlemskap i efterfrågat `survey_account`,
4. att efterfrågad resurs tillhör samma konto.

Systemadministratörsstatus får inte implicit ge vanlig account-access om inte API:t uttryckligen är systemadministrativt.

## 6. Val av enkätkonto

Efter login:

- 0 medlemskap: visa informationsvy om att användaren inte är kopplad till något enkätkonto,
- 1 medlemskap: välj kontot automatiskt,
- 2+ medlemskap: visa kontoväljare.

Valt konto ska inte lagras som global server-side sessionsstatus eftersom flera browserflikar kan arbeta i olika konton.

Frontend ska i stället bära valt `accountId` i route/state och alla relevanta API-anrop.

## 7. Skapa enkätkonto

Endast systemadministratören får skapa konto.

Skapandet ska vara atomärt:

1. skapa `survey_account`,
2. hitta eller skapa första `admin_user`,
3. skapa `survey_account_admin`,
4. commit.

Om något steg misslyckas ska inget konto skapas.

Ett nytt konto måste alltid ha minst en enkätadministratör innan transaktionen slutförs.

## 8. Hantering av administratörer

En enkätadministratör får lägga till och ta bort administratörer inom ett konto där användaren själv är medlem.

Regler:

- samma `admin_user` kan vara medlem i flera konton,
- sista administratören i ett konto får inte tas bort,
- ett medlemskap kan tas bort utan att användarkontot tas bort,
- en användare utan medlemskap kan inaktiveras eller tas bort av systemadministratören,
- fysisk DELETE av användare bör undvikas om auditreferenser finns; `active=false` är förstahandsval.

## 9. Bootstrap-admin

Bootstrap-konfigurationen används endast för att initialt skapa systemadministratören.

När användaren skapas ska:

```text
system_admin = true
active = true
```

Bootstrap-lösenordet kan därefter tas bort från runtime-konfigurationen.

Att känna till bootstrap-credentials ger inte rätt att skapa ytterligare systemadministratörer efter bootstrap.

## 10. Migrering av befintlig installation

Migreringen ska bevara befintliga enkäter och resultat.

Föreslagen ordning:

1. lägg till `system_admin` och `active` på `admin_user`,
2. markera befintlig bootstrap-admin som systemadmin,
3. skapa `survey_account`,
4. skapa `survey_account_admin`,
5. skapa ett initialt konto, exempelvis `Default`,
6. koppla befintliga administratörer som har enkäter till `Default`,
7. lägg till nullable `survey.survey_account_id`,
8. sätt alla befintliga enkäter till `Default`,
9. sätt `created_by_admin_user_id = owner_id`,
10. verifiera att alla enkäter har konto,
11. gör `survey_account_id` NOT NULL,
12. ta bort gamla owner-baserade authorization paths,
13. ta bort `survey.owner_id` först när all kod använder kontoägande.

Migreringen ska kunna köras automatiskt med Flyway på en befintlig 1.0-databas.

## 11. Säkerhetskrav

Tenant-isolering är ett säkerhetskrav.

Tester ska minst verifiera:

- admin i konto A kan inte lista konto B:s enkäter,
- admin i konto A kan inte läsa en känd survey UUID från konto B,
- admin i konto A kan inte läsa run/result/export från konto B,
- manipulerat `accountId` nekas server-side,
- systemadmin-endpoints nekas för vanlig enkätadministratör,
- sista administratören i ett konto kan inte tas bort.

## 12. Nästa implementationssteg

STEP-38 ska endast införa domän och migration:

- `survey_account`,
- `survey_account_admin`,
- nya `admin_user`-fält,
- migrering av befintlig data till ett initialt konto,
- `survey.survey_account_id`,
- repositories/entities och migrationstester.

Account-scoped API och UI införs först i senare steg.


## STEP-38 övergångsläge

Efter STEP-38 finns både `survey.owner_id` och `survey.survey_account_id`.

Detta är avsiktligt:

- `survey_account_id` är det framtida och obligatoriska verksamhetsägandet,
- `owner_id` används temporärt av befintliga API:er för att undvika att STEP-38 samtidigt blir en full authorization-migrering,
- all ny survey-data får både `survey_account_id` och `created_by_admin_user_id`,
- STEP-39 flyttar all authorization till account membership,
- först därefter tas `owner_id` bort i en separat migration.

Denna övergång gör datamigreringen isolerad och bakåtkompatibel medan tenant-isoleringen kan införas och testas separat.
