# Survey package format v1

Ett komplett exportpaket är en ZIP-fil med formatet `survey-package`, version `1`.

Paketet är avsett för arkivering, överföring och vidare analys av ett genomfört enkättillfälle.

## Innehåll

```text
survey-package.zip
  manifest.json
  survey.json
  run.json
  responses.json
  responses.csv
```

### `manifest.json`

Innehåller paketformat, formatversion, skapandetid, applikationsversion och en lista över filer i paketet.

### `survey.json`

En `survey-definition` version 1 byggd från **genomförandets snapshot**. Den beskriver därför exakt de frågor och svarsalternativ som deltagarna fick, även om den ursprungliga enkätmallen senare har ändrats.

### `run.json`

Versionsmärkt metadata om själva genomförandet: titel, status, publik identifierare, kortkod och relevanta tidsstämplar.

### `responses.json`

Samma `survey-result-export` version 1 som den separata JSON-resultatexporten.

### `responses.csv`

Samma CSV-resultat som den separata CSV-exporten.

## Anonymitet

Paketet innehåller inte deltagartoken, tokenhash eller interna participant-session-ID:n. Resultat använder exportspecifika identifierare som `anon-001`.

Fritext exporteras däremot ordagrant och kan därför innehålla identifierande uppgifter som deltagaren själv har skrivit.

## Import

Version 1 definierar paketet främst som arkivformat. `survey.json` kan importeras med tjänstens vanliga survey-definition-import. Import av ett helt historiskt paket kan införas separat senare om det behövs.
