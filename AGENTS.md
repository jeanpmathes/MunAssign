# Agent Notes

Always read these project docs before substantial work:

- `docs/DESIGN.md`
- `docs/DATA_MODEL.md`
- `docs/TODO.md`
- `docs/FAQ.md`

Use `docs/FAQ.md` as the shared question inbox:

- If a decision is unclear and affects implementation, add a concise question to `docs/FAQ.md`.
- If the user answers a FAQ question in chat, incorporate the answer into the relevant project docs or code, then remove that question from `docs/FAQ.md`.
- If the user answers directly in `docs/FAQ.md`, treat that answer as project guidance, incorporate it where needed, then remove or rewrite the question.
- Keep FAQ questions specific and actionable. Do not leave answered questions lingering.

Terminology:

- MyMUN Excel ingestion is called an **update**.
- `.munassign` file transfer is called **import/export**.
- MyMUN-confirmed assignments come from the `Countries.Delegates` column.
- App-created assignments are pending until MyMUN confirms them in a later update.
