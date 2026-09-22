# Survey result export format

## Format

- `format`: `survey-result-export`
- `version`: `1`

Exporten är en komplett maskinläsbar ögonblicksbild av ett enkätgenomförande för extern analys.

## Integritetsprincip

Exporten innehåller inte:

- intern `participant_session.id`
- deltagartoken eller token-hash
- adminanvändar-ID
- databas-ID för frågor, svar eller alternativ
- IP-adress eller user agent

Deltagare får i stället exportspecifika ID:n som `anon-001`. Dessa ID:n är endast till för att hålla ihop en deltagares svar inom den exporterade filen.

Fritext kan naturligtvis innehålla personuppgifter som deltagaren själv har skrivit.

## Struktur

```json
{
  "format": "survey-result-export",
  "version": 1,
  "exportedAt": "2026-09-22T07:00:00Z",
  "run": {
    "title": "Arkitekturworkshop",
    "status": "OPEN",
    "publicId": "...",
    "joinCode": "K7M4QX",
    "createdAt": "...",
    "openedAt": "...",
    "closedAt": null,
    "opensAt": null,
    "closesAt": null
  },
  "questions": [
    {
      "key": "Q1",
      "position": 0,
      "type": "SCALE",
      "text": "Hur tydligt är målet?",
      "required": true,
      "scaleMin": 1,
      "scaleMax": 5,
      "scaleMinLabel": "Otydligt",
      "scaleMaxLabel": "Tydligt",
      "options": []
    }
  ],
  "responses": [
    {
      "participantId": "anon-001",
      "status": "SUBMITTED",
      "startedAt": "...",
      "submittedAt": "...",
      "answers": [
        {
          "questionKey": "Q1",
          "textValue": null,
          "booleanValue": null,
          "numericValue": 4,
          "optionValues": [],
          "updatedAt": "..."
        }
      ]
    }
  ]
}
```

## Deltagare som inkluderas

Version 1 exporterar både `ACTIVE` och `SUBMITTED` deltagarsessioner som har lagrats i genomförandet. `EXPIRED` sessioner exkluderas. Detta gör att en pågående workshop kan exporteras utan att invänta stängning.

## Frågenycklar

Frågor får portabla nycklar `Q1`, `Q2` osv. utifrån snapshotens frågeordning. Svaren refererar till dessa nycklar i stället för interna UUID:n.
