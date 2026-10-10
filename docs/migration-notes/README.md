# Migration notes knowledge base

One Markdown file per topic, named `<topic>.md`. The `lookup_migration_note`
agent tool reads these by filename (case-insensitive, matching exactly how
the model names the topic in its tool call) — nothing here is auto-generated
or scraped; these are meant to be hand-written, specific, and short.

Suggested topics, matching the `FailureCategory` taxonomy in
`buildparse/.../FailureCategory.java` and DESIGN.md section 6:

- `javax-jakarta.md`
- `spring-security-config.md`
- `hibernate-6.md`
- `removed-renamed-api.md`
- `dependency-conflict.md`

Empty for now — add notes here as the agent loop (Phase 3+) surfaces real
failures worth writing a note about, rather than writing speculative notes
for categories that haven't come up yet.
