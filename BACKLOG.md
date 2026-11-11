# TaskPulse Backlog

This is the single progress-tracking list for proposed fixes, refactoring, features, and tests. Check an item when it is complete; use `In progress` or `Blocked` in its description when more context is needed.

## Bug fixes

- [ ] **BUG-001 — Handle empty label lists in CSV**: Correctly deserialize records with an empty label list.
- [ ] **BUG-002 — Validate task updates**: Reject negative estimates and blank titles consistently on every task update path.
- [ ] **BUG-003 — Report trailing empty CSV lines clearly**: Handle files ending in empty lines and provide an actionable parsing error when appropriate.
- [ ] **BUG-004 — Validate project references on project updates**: If a project update endpoint or method is introduced, prevent tasks from referencing nonexistent project IDs.
- [X] **BUG-005 — Support escaped characters in quoted CLI arguments**: Teach `CommandTokenizer` to handle `\n`, `\t`, and `\\` inside quoted strings.
- [ ] **BUG-006 — Parse CLI enum values case-insensitively**: Normalize user-supplied enum values before validation.

## Refactoring and design

- [ ] **REFACTOR-001 — Decompose CLI command handling**: Replace the monolithic `TaskPulseCli` switch with dedicated command handlers (such as `ProjectCliHandler`, `TaskCliHandler`, and `WorklogCliHandler`), a command registry, and reusable parsing for options such as `--project`, `--state`, and `--priority`.
- [x] **REFACTOR-002 — Encapsulate task state transitions**: Move transition permissions and state-specific constraints from enum switches into discrete transition handlers or state implementations.
- [ ] **REFACTOR-003 — Encapsulate task construction**: Introduce a `TaskBuilder` to simplify task creation for tests and persistence.
- [ ] **REFACTOR-004 — Extract CSV entity mappers**: Move row-to-entity parsing and entity-to-row serialization from `CsvStorageManager` into `ProjectCsvMapper`, `TaskCsvMapper`, and `WorklogCsvMapper`; keep storage responsible for file I/O, transactional consistency, and relationship validation.
- [ ] **REFACTOR-005 — Isolate query filtering**: Extract `TaskService.matchesQuery(...)` into a query evaluator with composable specifications, including project, state, overdue, and text-search filters; support combining specifications with `and()`, `or()`, and `not()`.
- [X] **REFACTOR-006 — Centralize CLI error formatting**: Unify user-facing error presentation in a dedicated handler rather than separate catch blocks in `executeLine`.
- [ ] **REFACTOR-007 — Add a workspace unit of work**: Track modified entities and commit writes to all workspace CSV files atomically, rolling back on failure.

## Features

- [ ] **FEATURE-001 — Export project summary reports**: Implement FR-08 project metrics and task breakdowns in Markdown and JSON, including task counts by state, overdue counts, total recorded time, and estimate variance. Use `ReportExporter` strategies for Markdown and JSON.
- [ ] **FEATURE-002 — Add the report export CLI command**: Support `report export <project-id> --format <markdown|json> [--output <file-path>]`.
- [ ] **FEATURE-003 — Add task list sorting and date filters**: Support `--sort title|priority|duedate`, `--from <date>`, `--to <date>`, and `--overdue` in `task list`.
- [ ] **FEATURE-004 — Highlight tasks over estimate**: Add a CLI view or `task list --exceeded` option for tasks whose recorded time exceeds their estimate.
- [X] **FEATURE-005 — Back up and restore a workspace**: Add commands to export a workspace as a compressed archive or import an external CSV bundle.

## Tests

- [ ] **TEST-001 — Verify CSV escaping and round trips**: Add property-based or randomized tests for CSV escaping and round-trip preservation.
- [ ] **TEST-002 — Cover end-to-end CLI workflows**: Add integration tests that exercise the CLI through stdin/stdout piping.
- [ ] **TEST-003 — Cover date and time edge cases**: Increase branch coverage for date-range queries and time parsing.
