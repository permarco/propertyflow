# PropertyFlow Copilot Instructions

Before generating or modifying code, use the project documentation
under `/docs` as the source of truth.

Read at minimum:

- `docs/vision.md`
- `docs/architecture/c4-context.md`
- `docs/architecture/c4-container.md`
- `docs/architecture/adr/ADR-001-modular-monolith.md`

## Architecture

- Follow all accepted ADRs.
- Do not change architectural decisions without proposing an ADR change.
- Do not introduce microservices unless explicitly justified by an accepted ADR.
- Keep external systems isolated from the domain logic through explicit interfaces.

## Safety

- Treat LLM output as untrusted input.
- Do not send unnecessary tenant or rental data to external LLM providers.
- AI recommendations must never replace the final human decision.
- Relevant AI decisions must remain auditable.
- AI or RAG failures must not prevent a tenant request from being persisted
  and manually processed.

## Development

- Prefer small, focused changes.
- Add automated tests for generated business logic.
- Do not invent requirements.
