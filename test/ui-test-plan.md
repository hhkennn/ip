# UI Test Plan

- Program command: `powershell -NoProfile -ExecutionPolicy Bypass -File test/reset-data-and-run.ps1`
- Working directory: `.`
- Java requirement: Java 25
- Setup command: `javac -d out src/main/java/*.java`
- Session log: `test/ui-test-session.log`

Run this plan through the project-specific `test-ui` workflow. That workflow
checks the Java version and runs the setup command before invoking the runner;
invoking the runner directly assumes those prerequisites are already complete.

The runner starts a fresh process for each test case. The launcher clears the
ignored runtime data file before starting each process so that persisted tasks
from one case do not affect another case. It sends the input commands in order
and compares the complete console output with the expected output. Add one
command per line in each `Inputs` block. The output below is intentionally kept
exact, including prompts, indentation, and separators.

Saved-task loading is covered separately in `test/load-ui-test-plan.md` because
that plan supplies a persisted data fixture before startup.

## Coverage summary

| Behavior | Valid input | Missing or malformed input | Boundary or state check |
| --- | --- | --- | --- |
| Start and exit | `bye` | End of input | Fresh process per case |
| List tasks | Populated list | Empty list | State checked after errors |
| Add tasks | Todo, deadline, event | Empty fields and invalid delimiters | Whitespace normalization |
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
deadline report /by Friday
deadlines report /by Friday
event meeting /from Mon /to Tue
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
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [D][ ] report (by: Friday)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] meeting (from: Mon to: Tue)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] stable
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Mon to: Tue)
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
deadline report /by Friday
deadline report
list
event meeting /from Monday /to Tuesday
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
       [D][ ] report (by: Friday)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the deadline format? Use: deadline <description> /by <date/time>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] alpha
     2.[D][ ] report (by: Friday)
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] meeting (from: Monday to: Tuesday)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the event format? Use: event <description> /from <start> /to <end>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] alpha
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Monday to: Tuesday)
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
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Monday to: Tuesday)
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
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Monday to: Tuesday)
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
deadline   submit report   /by   Friday
deadline submit report /by
event   meeting /from Mon /to Tue
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
       [D][ ] submit report (by: Friday)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the deadline format? Use: deadline <description> /by <date/time>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] meeting (from: Mon to: Tue)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Did you even read the event format? Use: event <description> /from <start> /to <end>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] spaced description
     2.[D][ ] submit report (by: Friday)
     3.[E][ ] meeting (from: Mon to: Tue)
     ____________________________________________________________
Your command?      ____________________________________________________________
     A blank todo? Even I can't organise nothing. Use: todo <description>.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] spaced description
     2.[D][ ] submit report (by: Friday)
     3.[E][ ] meeting (from: Mon to: Tue)
     ____________________________________________________________
Your command?      ____________________________________________________________
     Leaving already? Goodbye.
     ____________________________________________________________
```

## Test case: Handle invalid commands

- Aim: Verify that empty descriptions, unknown commands, malformed or lookalike delimiters, and missing or nonnumeric task numbers produce helpful errors without adding tasks.

### Inputs

```text
todo
blah
read book
deadline return book
event project meeting /from Mon 2pm
deadline report /bye Friday
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
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Your command?      ____________________________________________________________
     That command is invalid. Were you just guessing?
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
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

- Aim: Verify that deadlines and events retain their date/time strings and display the correct task type.

### Inputs

```text
todo borrow book
deadline return book /by Sunday
event project meeting /from Mon 2pm /to 4pm
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
       [D][ ] return book (by: Sunday)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] project meeting (from: Mon 2pm to: 4pm)
     That makes 3 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][ ] borrow book
     2.[D][ ] return book (by: Sunday)
     3.[E][ ] project meeting (from: Mon 2pm to: 4pm)
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
deadline save deadline /by Friday
event save event /from Monday /to Tuesday
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
       [D][ ] save deadline (by: Friday)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     There. I've added it:
       [E][ ] save event (from: Monday to: Tuesday)
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
       [D][ ] save deadline (by: Friday)
     That makes 2 tasks. Try to keep up.
     ____________________________________________________________
Your command?      ____________________________________________________________
     Let's see what you've managed to pile up:
     1.[T][X] save todo
     2.[E][ ] save event (from: Monday to: Tuesday)
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
