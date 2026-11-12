# PropertyFlow Copilot Instructions

Before generating or modifying code, use the project documentation
under `/docs` as the source of truth.

Read at minimum:

- [../docs/project-context.md](../docs/project-context.md)
- [docs/vision.md](../docs/vision.md)
- [../docs/architecture/c4-context.md](../docs/architecture/c4-context.md)
- [docs/architecture/c4-container.md](../docs/architecture/c4-container.md)
- [../docs/architecture/module-structure.md](../docs/architecture/module-structure.md)
- all accepted ADRs under `docs/architecture/adr/`

## Architecture

- Follow all accepted ADRs.
- Do not change architectural decisions without proposing an ADR change.
- Do not introduce microservices unless explicitly justified by an accepted ADR.
- Keep external systems isolated from the domain logic through explicit interfaces.
- Follow the module and dependency rules in [../docs/architecture/module-structure.md](../docs/architecture/module-structure.md).
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
- Human decisions are mandatory for costs or binding external orders (including technician callouts), very urgent or severe cases, and strong or escalated tenant complaints, as specified in FALL-10 in [docs/specifications/fallverwaltung.md](../docs/specifications/fallverwaltung.md). Outside these boundaries, only explicitly agreed and server-validated automated actions and closures under FALL-07/FALL-10 are allowed.
- Relevant AI decisions must remain auditable.
- AI or RAG failures must not prevent a tenant request from being persisted
  and manually processed.

## Development

- Prefer small, focused changes.
- Add automated tests for generated business logic.
- Do not invent requirements.

## Code style and simplicity

- Prefer simple, readable code and direct solutions over clever or overly abstract designs.
- Implement only current requirements; do not add speculative extension points or functionality for hypothetical future needs.
- Introduce abstractions only when they provide a concrete benefit or are required by the accepted architecture. Preserve the defined module boundaries and external integration interfaces.
- Do not add extra layers, generic frameworks, or base classes without a clear, current need.
- Use meaningful English names and avoid unclear abbreviations.
- In Java, use `PascalCase` for classes, `camelCase` for methods and variables, `UPPER_SNAKE_CASE` for constants, and lowercase package names.
- Follow existing project conventions for naming, formatting, and code organization.
- Use comments to explain why a decision was made, rather than restating what the code does.
- Do not use `var` in code; prefer explicit types for clarity.

### String constants

- Extract string literals into named constants when they represent a stable contract value, a centrally maintained message or prompt, or the same concept reused in multiple places. Extract long prompts even when used once if this makes the calling code easier to read.
- In Java, prefer `private static final String` with meaningful English `UPPER_SNAKE_CASE` names. Keep constants in the class or module that owns their meaning; widen visibility only when actual consumers require it. Do not introduce a global constants collection.
- Do not extract every string mechanically. Keep obvious, one-off local strings and independent test inputs/expected values inline when a constant would add indirection without improving clarity. Identical text does not imply the same semantic constant.
- Keep environment-dependent values in configuration and secrets outside source code. Use enums for closed sets of domain values rather than introducing string constants for them.

### Java formatting (mandatory)

Apply these rules to all new or modified Java code, including tests and provisional/demo code. These explicit rules take precedence over compact formatting in older files. Keep unrelated files unchanged unless a broader formatting pass is requested.

- Use four spaces per indentation level; do not use tabs. Use eight spaces for continuation indentation where needed.
- Place opening braces on the declaration or control-statement line and closing braces on their own line. Use braces for all control-statement bodies, even single statements.
- Put each statement on its own line. Expand methods, constructors and multi-statement lambda bodies over multiple lines; do not compress them into one-liners.
- Put annotations such as `@Test` and `@Override` on separate lines above their declarations.
- Use explicit imports, including static imports; do not use wildcard imports. Sort regular imports alphabetically, followed by a blank line and alphabetically sorted static imports. Remove unused imports.
- Separate methods, constructors and nested type declarations with a blank line. Use blank lines to separate logical steps within a method and constants from instance fields.
- Format enum constants one per line. Put the closing brace of empty records, interfaces and classes on its own line.
- Format long builder and fluent call chains with one call per line and consistent continuation indentation. Keep nested builders clearly indented.
- Aim for a maximum line length of 150 characters. Wrap long argument lists, conditions and array initializers at logical boundaries; allow exceptions for indivisible URLs and literals whose contents must remain unchanged.
- Use spaces around operators and after commas; do not add spaces just inside parentheses.
- Format Javadoc as a multiline `/** ... */` block with a leading `*` on each content line. Keep comments concise and explain intent when useful.
- Preserve behavior, string contents and text-block whitespace semantics during formatting. Review the diff for accidental functional changes and formatting consistency before finishing.
