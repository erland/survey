# Survey definition export format

The portable survey definition format is intended for moving survey templates between compatible installations and for version-controlled archival.

## Version 1

Top-level fields:

- `format`: always `survey-definition`
- `version`: currently `1`
- `exportedAt`: ISO-8601 timestamp describing when the file was generated
- `survey`: the portable survey definition

The survey definition contains:

- `title`
- `description`
- `questions`, in display order

Each question contains:

- `type`: `TEXT`, `YES_NO`, `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, or `SCALE`
- `text`
- `required`
- scale settings when applicable
- `options` for choice questions, in display order

Internal database UUIDs, owner information, timestamps, survey status, runs, participants and responses are intentionally excluded.

Example:

```json
{
  "format": "survey-definition",
  "version": 1,
  "exportedAt": "2026-09-22T06:00:00Z",
  "survey": {
    "title": "Arkitekturworkshop",
    "description": "Kort introduktion",
    "questions": [
      {
        "type": "SCALE",
        "text": "Hur tydligt är målet?",
        "required": true,
        "scaleMin": 1,
        "scaleMax": 5,
        "scaleMinLabel": "Otydligt",
        "scaleMaxLabel": "Mycket tydligt",
        "options": []
      }
    ]
  }
}
```

Unknown future versions must not be assumed to be compatible. Import support is implemented separately and must explicitly validate `format` and `version`.

## Import

Version 1 can be imported through `POST /api/admin/surveys/import` using the exported JSON document as the request body.

Import rules:

- `format` must be `survey-definition`.
- `version` must be `1`.
- The full document is validated before persistence completes.
- Validation failure rolls back the import; no partial survey is kept.
- An import always creates a new survey with status `DRAFT`.
- Import never overwrites an existing survey.
- `exportedAt` is informational and is not used to identify or update an existing survey.
