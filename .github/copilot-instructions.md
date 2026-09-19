# PropertyFlow Copilot Instructions

Before generating or modifying code, use the project documentation
under `/docs` as the source of truth.

Read at minimum:

- `docs/vision.md`
- `docs/architecture/c4-context.md`
- `docs/architecture/c4-container.md`
- `docs/architecture/module-structure.md`
- all accepted ADRs under `docs/architecture/adr/`

## Architecture

- Follow all accepted ADRs.
- Do not change architectural decisions without proposing an ADR change.
- Do not introduce microservices unless explicitly justified by an accepted ADR.
- Keep external systems isolated from the domain logic through explicit interfaces.
- Follow the module and dependency rules in `docs/architecture/module-structure.md`.
- Structure backend code by business capability; do not introduce global root-level `controller`, `service`, `repository` or `entity` packages.
- Do not access another module's persistence implementation directly.

## Technology and workflow

- The backend is a modular monolith based on Spring Boot.
- Use the current project-defined Spring AI version for AI integrations.
- Camunda 8 is used for long-running workflow orchestration.
- Camunda is not embedded in the PropertyFlow backend.
- PropertyFlow integrates with Camunda through its supported client APIs and job workers.
- Business data remains owned by PropertyFlow.
- Camunda process variables should contain only the data required for workflow orchestration.
- Camunda job workers must be designed with retries and idempotency in mind.

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
