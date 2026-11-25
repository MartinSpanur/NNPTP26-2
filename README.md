[![CI](https://github.com/rodi0878/NNPTP26-2/actions/workflows/ci.yml/badge.svg)](https://github.com/rodi0878/NNPTP26-2/actions/workflows/ci.yml)

[![codecov](https://codecov.io/gh/rodi0878/NNPTP26-2/graph/badge.svg?token=7Y6B0HFZ1G)](https://codecov.io/gh/rodi0878/NNPTP26-2)

## Interactive command quoting

Use matching single or double quotes for arguments containing spaces, for example
`project add "Programming Course"`. An unclosed quote rejects the entire command
without changing data and displays an error asking you to close the quote. The
interactive session stays open so you can enter a corrected command.
Escaped quotes inside a quoted argument are treated as text, not closing quotes.

## CLI option values

For `task list` and `worklog log`, use `--option=value` when a value begins
with `--`, for example `task list --search=--overdue` or
`worklog log <task-id> 30 --author Dev --desc="--verbose details"`.
The space-separated form still rejects another switch as a missing value.
Unknown options and value options without a following value are rejected.

## Task creation options

```text
task add <project-id> <title> [description] [--priority <priority>] [--estimate <minutes>] [--due <YYYY-MM-DD>]
```

Defaults are `MEDIUM` priority, no estimate or due date.
An invalid or unknown option is rejected and task is not created.

## Workspace backup and restore (FEATURE-005)

Use the following commands either interactively or as application arguments:

```text
workspace export "backup.zip"
workspace import "backup.zip"
workspace import "external-csv-directory"
```

Export writes a compressed ZIP snapshot of the current session, including unsaved
changes. The destination must not already exist; its parent directory must exist.
Import **replaces the entire current workspace**, rather than merging records.
Back up your current workspace first. Imported changes are saved to `.taskpulse`
when the application exits, like other commands.

A bundle must contain `projects.csv`, `tasks.csv`, and `worklogs.csv` at its root,
using the same UTF-8 headers and column order as TaskPulse's CSV storage. Include
header-only files for empty entity collections. Import accepts either a directory
containing those files or a ZIP containing exactly those three entries. ZIP paths,
duplicate entries, missing files, incorrect headers, invalid values and broken
project/task references are rejected before the current session is changed.
Each imported CSV file is limited to 64 MiB of uncompressed data. Other files in a
CSV directory are ignored. Paths containing spaces should be quoted interactively.

## CSV quoting

Fields containing commas, double quotes or line breaks must be enclosed in double
quotes. A literal double quote inside a quoted field is written as `""`.
Opening quotes must be at the start of a field; after a closing quote, only a
comma, line ending or end of file is allowed. Spaces outside quoted fields are
not ignored. Malformed quoting causes loading or import to fail with an error
instead of silently changing field values.

# See also

* [BACKLOG](BACKLOG.md)
* [CONTRIBUTING](CONTRIBUTING.md)
* [PRD](PRD.md)
