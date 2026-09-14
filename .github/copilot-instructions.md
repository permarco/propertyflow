# PropertyFlow Copilot Instructions

Before generating or modifying code, use the project documentation in `/docs`
as the source of truth.

Relevant documents:

- `docs/vision.md`
- `docs/architecture/c4-context.md`
- `docs/architecture/c4-container.md`
- `docs/architecture/adr/ADR-001-modular-monolith.md`

## Architecture rules

- The backend is a modular monolith.
- Structure backend code by business capability, not by global technical layers.
- Current business modules are:
    - intake
    - triage
    - recommendation
    - caseprocessing
- Do not introduce microservices unless an ADR explicitly changes the architecture.
- External systems must be accessed through explicit interfaces/adapters.
- Camunda 7 is embedded in the backend application.
- Failure of AI or RAG must not prevent a tenant request from being persisted
  and manually processed.

## AI and security rules

- Treat LLM output as untrusted input.
- Do not expose unnecessary tenant or rental data to external LLM providers.
- AI recommendations never replace the final human decision.
- Relevant AI decisions must remain auditable.

## Development

- Prefer small, focused changes.
- Add automated tests for generated business logic.
- Do not invent requirements that are not supported by the project documentation.
