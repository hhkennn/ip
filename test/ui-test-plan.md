# UI Test Plan

- Program command: `powershell -NoProfile -ExecutionPolicy Bypass -File test/reset-data-and-run.ps1`
- Working directory: `.`
- Java requirement: Java 25
- Setup command: `$javaSources = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | Where-Object { $_.BaseName -notin @('DialogBox', 'Launcher', 'Main', 'MainWindow') } | ForEach-Object { $_.FullName }; javac -d out $javaSources`
- Session log: `test/ui-test-session.log`

Run this plan through the project-specific `test-ui` workflow. That workflow
checks the Java version and runs the setup command before invoking the runner;
invoking the runner directly assumes those prerequisites are already complete.

The setup command excludes JavaFX-specific classes because these tests launch
the console entry point directly. The runner starts a fresh process for each
test case. The launcher clears the ignored runtime data file before starting
each process so that persisted tasks from one case do not affect another case.
It sends the input commands in order and compares the complete console output
with the expected output. Add one command per line in each `Inputs` block. The
output below is intentionally kept exact, including prompts, indentation, and
separators.

Saved-task loading is covered separately in `test/load-ui-test-plan.md` because
that plan supplies a persisted data fixture before startup.

## Coverage summary

| Behavior | Valid input | Missing or malformed input | Boundary or state check |
| --- | --- | --- | --- |
| Start and exit | `bye` | End of input | Fresh process per case |
| List tasks | Populated list | Empty list | State checked after errors |
| Find tasks | Matching keyword in descriptions | Empty keyword or no matches | Original task numbering and order are preserved |
| Add tasks | Todo, deadline, event with valid date/time | Empty fields, invalid delimiters, and invalid date/time | Whitespace normalization and date formatting |
| Update tasks | Mark and unmark | Missing, nonnumeric, and out-of-range numbers | State preserved after errors |
| Delete tasks | Valid one-based number | Missing, nonnumeric, and out-of-range numbers | Remaining tasks renumbered |
| Save tasks | Add, mark, unmark, and delete | File-write errors are outside this happy-path test | Complete list is rewritten to `data/herta.txt` |
| Command matching | All supported commands | Command-name lookalikes | Lookalikes do not change state |

## Test case: Exit immediately

- Aim: Verify that the application displays its welcome screen and exits cleanly when the user enters `bye`.

### Inputs

```text
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Find tasks by description keyword

- Aim: Verify that `find` performs a case-insensitive substring search, preserves the original task numbers, and reports empty searches and searches without matches.

### Inputs

```text
todo read book
todo call June
todo return book
find BOOK
find missing
find
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] read book
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] call June
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] return book
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Looking for something? How predictable. Here are the matches:
     1.[T][ ] read book
     3.[T][ ] return book
     ____________________________________________________________
Your command?      ____________________________________________________________
     Looking for something? How predictable. Here are the matches:
     I found nothing. Perhaps the task was only in your imagination.
     ____________________________________________________________
Your command?      ____________________________________________________________
     A blank search? Use: find <keyword>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: List an empty task list

- Aim: Verify that `list` handles a fresh task list without displaying task entries.

### Inputs

```text
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Delete tasks

- Aim: Verify that a valid one-based task number removes the task, shifts the remaining list, and reports the new count, while invalid numbers leave the list unchanged.

### Inputs

```text
todo read book
todo return book
delete 1
list
delete 0
delete 2
delete nope
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] read book
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] return book
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. It's gone:
       [T][ ] read book
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] return book
     ____________________________________________________________
Your command?      ____________________________________________________________
     That task doesn't exist. Did you even check the list?
     ____________________________________________________________
Your command?      ____________________________________________________________
     That task doesn't exist. Did you even check the list?
     ____________________________________________________________
Your command?      ____________________________________________________________
     That's not a task number. Try: delete 1.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] return book
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Preserve state after empty and boundary commands

- Aim: Verify that an empty command and invalid boundary task numbers do not change a task, while valid mark and unmark commands do.

### Inputs

```text
todo core

list
mark 0
list
mark 1
list
unmark 0
list
unmark 1
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] core
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Nothing? Were you expecting me to read your mind?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     That task doesn't exist. Did you even check the list?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. It's marked complete:
       [T][X] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][X] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     That task doesn't exist. Did you even check the list?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][X] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     As you wish. It's incomplete again:
       [T][ ] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] core
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Reject command lookalikes

- Aim: Verify that command names with extra characters are rejected and do not add unintended tasks.

### Inputs

```text
todo stable
todotask
deadline report /by 2/12/2019 1800
deadlines report /by Friday
event meeting /from 2019-10-15 /to 2019-10-16
events meeting /from Mon /to Tue
listall
marking 1
unmarking 1
deleting 1
byebye
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] stable
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] report (by: Dec 02 2019, 6:00 PM)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] stable
     2.[D][ ] report (by: Dec 02 2019, 6:00 PM)
     3.[E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Handle EOF input

- Aim: Verify that the chatbot exits cleanly when standard input is closed before `bye`.

### Inputs

```text
todo before eof
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] before eof
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Preserve state across errors

- Aim: Verify that invalid commands interleaved with valid additions and updates do not change the task list.

### Inputs

```text
todo alpha
todo
list
deadline report /by 2019-10-15
deadline report
list
event meeting /from 2019-10-15 /to 2019-10-16
event meeting /from Monday
list
mark 1
mark 9
list
unmark 1
unmark nope
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] alpha
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     A blank todo? Even I can't organise nothing. Use: todo <description>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] alpha
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] report (by: Oct 15 2019)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the deadline format? Use: deadline <description> /by <date/time>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] alpha
     2.[D][ ] report (by: Oct 15 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the event format? Use: event <description> /from <start> /to <end>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] alpha
     2.[D][ ] report (by: Oct 15 2019)
     3.[E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. It's marked complete:
       [T][X] alpha
     ____________________________________________________________
Your command?      ____________________________________________________________
     That task doesn't exist. Did you even check the list?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][X] alpha
     2.[D][ ] report (by: Oct 15 2019)
     3.[E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     As you wish. It's incomplete again:
       [T][ ] alpha
     ____________________________________________________________
Your command?      ____________________________________________________________
     That's not a task number. Try: unmark 1.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] alpha
     2.[D][ ] report (by: Oct 15 2019)
     3.[E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Handle command formatting edge cases

- Aim: Verify that extra spaces are trimmed correctly and malformed commands do not add tasks.

### Inputs

```text
todo   spaced description
todo    
list
deadline   submit report   /by   2019-10-15
deadline submit report /by
event   meeting /from   2019-10-15 /to   2019-10-16
event meeting /from Mon /to
list
todo
blah
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] spaced description
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     A blank todo? Even I can't organise nothing. Use: todo <description>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] spaced description
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] submit report (by: Oct 15 2019)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the deadline format? Use: deadline <description> /by <date/time>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the event format? Use: event <description> /from <start> /to <end>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] spaced description
     2.[D][ ] submit report (by: Oct 15 2019)
     3.[E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     A blank todo? Even I can't organise nothing. Use: todo <description>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] spaced description
     2.[D][ ] submit report (by: Oct 15 2019)
     3.[E][ ] meeting (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Handle invalid commands

- Aim: Verify that empty descriptions, unknown commands, malformed or lookalike delimiters, invalid dates, invalid event ranges, and missing or nonnumeric task numbers produce helpful errors without adding tasks.

### Inputs

```text
todo
blah
read book
deadline return book
event project meeting /from Mon 2pm
deadline report /bye Friday
deadline report /by 31/02/2019 1800
event meeting /from nope /to 2019-10-16
event meeting /from 2019-10-16 /to 2019-10-15
event meeting /fromage Mon /today Tue
mark abc
mark
unmark
delete
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     A blank todo? Even I can't organise nothing. Use: todo <description>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, find, filter, upcoming, sort, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the deadline format? Use: deadline <description> /by <date/time>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the event format? Use: event <description> /from <start> /to <end>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the deadline format? Use: deadline <description> /by <date/time>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That is not a date. Use a real one, such as 2019-10-15 or 2/12/2019 1800.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Those dates won't do. Use something valid, such as 2019-10-15 or 2/12/2019 1800.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Time moves forward. Make the event end after it starts.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the event format? Use: event <description> /from <start> /to <end>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That's not a task number. Try: mark 1.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That's not a task number. Try: mark 1.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That's not a task number. Try: unmark 1.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That's not a task number. Try: delete 1.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Add and list a task

- Aim: Verify that leading command whitespace is ignored and a todo is displayed by `list`.

The three input lines intentionally have two leading spaces.

### Inputs

```text
  todo read book
  list
  bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] read book
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] read book
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Add deadlines and events

- Aim: Verify that deadlines parse date/time input into a typed value, display it in a readable format, and retain the correct task type.

### Inputs

```text
todo borrow book
deadline return book /by 2/12/2019 1800
event project meeting /from 2/12/2019 1800 /to 3/12/2019 1800
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] borrow book
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] return book (by: Dec 02 2019, 6:00 PM)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] project meeting (from: Dec 02 2019, 6:00 PM to: Dec 03 2019, 6:00 PM)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] borrow book
     2.[D][ ] return book (by: Dec 02 2019, 6:00 PM)
     3.[E][ ] project meeting (from: Dec 02 2019, 6:00 PM to: Dec 03 2019, 6:00 PM)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Save task changes to disk

- Aim: Verify that successful additions, status changes, and deletion are persisted by rewriting the complete task list in `data/herta.txt`.

### Inputs

```text
todo save todo
deadline save deadline /by 2019-10-15
event save event /from 2019-10-15 /to 2019-10-16
mark 1
unmark 1
mark 1
delete 2
list
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] save todo
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] save deadline (by: Oct 15 2019)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] save event (from: Oct 15 2019 to: Oct 16 2019)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. It's marked complete:
       [T][X] save todo
     ____________________________________________________________
Your command?      ____________________________________________________________
     As you wish. It's incomplete again:
       [T][ ] save todo
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. It's marked complete:
       [T][X] save todo
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. It's gone:
       [D][ ] save deadline (by: Oct 15 2019)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][X] save todo
     2.[E][ ] save event (from: Oct 15 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Reject task text that breaks storage format

- Aim: Verify that a task containing the storage delimiter is rejected without changing the local task list or saved data.

### Inputs

```text
todo contains | separator
list
bye
```
### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     Failed to save tasks: Invalid saved task: type T requires 3 fields.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```
## Test case: Query and sort date-aware tasks

- Aim: Verify that ISO and slash-format dates can find tasks occurring on a date, show upcoming incomplete tasks, and display all tasks in chronological order without changing their stored order.

### Inputs

```text
todo buy groceries
deadline submit report /by 2019-10-15
event project meeting /from 2019-10-14 /to 2019-10-16
deadline future report /by 9999-12-31
event future meeting /from 9999-12-30 /to 9999-12-31
filter /on 2019-10-15
filter /on 2020-01-01
upcoming 4000000
sort date
list
filter /on 15/10/2019
filter /on 31/02/2019
filter 2019-10-15
upcoming 0
sort time
bye
```

### Expected output

```text
     ____________________________________________________________
      _   _           _
     | | | | ___ _ __| |_ __ _
     | |_| |/ _ \ '__| __/ _` |
     |  _  |  __/ |  | || (_| |
     |_| |_|\___|_|   \__\__,_|
     Oh, you're here. I'm Herta.
     Well? What do you want?
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [T][ ] buy groceries
     That makes 1 task. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] submit report (by: Oct 15 2019)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] project meeting (from: Oct 14 2019 to: Oct 16 2019)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] future report (by: Dec 31 9999)
     That makes 4 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] future meeting (from: Dec 30 9999 to: Dec 31 9999)
     That makes 5 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Here is what your schedule has for Oct 15 2019, if anything:
     2.[D][ ] submit report (by: Oct 15 2019)
     3.[E][ ] project meeting (from: Oct 14 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Here is what your schedule has for Jan 01 2020, if anything:
     Nothing scheduled. A remarkably empty date.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Your next 4000000 days. Try not to fall behind:
     4.[D][ ] future report (by: Dec 31 9999)
     5.[E][ ] future meeting (from: Dec 30 9999 to: Dec 31 9999)
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. Your tasks are in date order.
     3.[E][ ] project meeting (from: Oct 14 2019 to: Oct 16 2019)
     2.[D][ ] submit report (by: Oct 15 2019)
     5.[E][ ] future meeting (from: Dec 30 9999 to: Dec 31 9999)
     4.[D][ ] future report (by: Dec 31 9999)
     1.[T][ ] buy groceries
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] buy groceries
     2.[D][ ] submit report (by: Oct 15 2019)
     3.[E][ ] project meeting (from: Oct 14 2019 to: Oct 16 2019)
     4.[D][ ] future report (by: Dec 31 9999)
     5.[E][ ] future meeting (from: Dec 30 9999 to: Dec 31 9999)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Here is what your schedule has for Oct 15 2019, if anything:
     2.[D][ ] submit report (by: Oct 15 2019)
     3.[E][ ] project meeting (from: Oct 14 2019 to: Oct 16 2019)
     ____________________________________________________________
Your command?      ____________________________________________________________
     That date won't do. Try 2019-10-15 or 15/10/2019.
     ____________________________________________________________
Your command?      ____________________________________________________________
     You forgot the /on. Use: filter /on <date>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That range makes no sense. Use a positive number of days.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That is not a sorting option. Use: sort date.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```
