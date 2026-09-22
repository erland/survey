# Survey result CSV format

STEP-29 exports run responses as UTF-8 CSV with a UTF-8 BOM for convenient opening in Excel.

- One row per non-expired participant session.
- First columns: `participant_id`, `status`, `started_at`, `submitted_at`.
- One following column per snapshot question, named `Qn__<question text>`.
- Participant identifiers are synthetic `anon-NNN` export IDs.
- Multiple-choice values are joined with `;` inside a single CSV cell.
- Yes/no values are exported as `Ja` / `Nej`.
- Scale values are exported as numbers.
- Free text is preserved verbatim, including line breaks.
- Every field is quoted and embedded quotes are doubled. Records use CRLF line endings.
- `EXPIRED` participant sessions are excluded, matching the JSON result export.

Free-text content can still contain personal information entered by a participant.
