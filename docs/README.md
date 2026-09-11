# Herta User Guide

Herta keeps active tasks and archived tasks separately. Active tasks are stored
in `data/herta.txt`; archived tasks are stored in `data/archive.txt`. When a
custom active file is configured, `archive.txt` is placed beside it.

## Adding tasks

Use the existing commands to add tasks:

```text
todo <description>
deadline <description> /by <date/time>
event <description> /from <start> /to <end>
```

Tasks can be listed, searched, filtered, sorted for display, marked complete,
unmarked, or deleted using the existing commands. These commands operate only
on active tasks.

## Archiving completed tasks

Only completed active tasks can be selected explicitly. Select one or more
numbers, inclusive ranges, or a mixture of both:

```text
archive 2
archive 2-5
archive 1 3-5 8
```

Selectors are de-duplicated, and archived tasks are appended in their active-list
order. To archive every completed active task, use:

```text
archive all
```

Incomplete tasks are skipped by `archive all`. The command validates an
explicit selection before changing either collection.

## Viewing and restoring archived tasks

View the archive with independently numbered tasks:

```text
archived
```

Restore exactly one archived task by its archive number:

```text
restore 1
```

Restored tasks are appended to the active list. Their type, description,
dates, times, and completion status are preserved.

## Storage

Both task files use the existing one-record-per-line UTF-8 format:

```text
T | 1 | finish report
D | 1 | submit report | 2019-10-15T00:00
E | 1 | project meeting | 2019-10-15T00:00 | 2019-10-16T00:00
```

An absent archive file is treated as an empty archive. Successful archive and
restore operations stage and validate both resulting files before committing
them. If persistence fails, Herta reports an error and keeps the original
collections and file contents.
