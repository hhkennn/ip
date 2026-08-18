# UI Test Plan

- Program command: `java -cp out Herta`
- Working directory: `.`
- Java requirement: Java 25
- Setup command: `javac -d out src/main/java/*.java`
- Session log: `test/ui-test-session.log`

The runner starts a fresh process for each test case. It sends the input commands
in order and compares the complete console output with the expected output. Add
one command per line in each `Inputs` block. The output below is intentionally
kept exact, including prompts, indentation, and separators.

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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] read book
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] return book
     Now you have 2 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Noted. I've removed this task:
       [T][ ] read book
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] return book
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! That task number is not in your list. Try: delete 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! That task number is not in your list. Try: delete 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! Please provide a valid task number. Try: delete 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] return book
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] core
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! Please enter a command, such as: todo read book.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! That task number is not in your list. Try: mark 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Nice! I've marked this task as done:
       [T][X] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][X] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! That task number is not in your list. Try: unmark 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][X] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     OK, I've marked this task as not done yet:
       [T][ ] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] core
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] stable
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! I don't recognise that command :( 
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [D][ ] report (by: Friday)
     Now you have 2 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! I don't recognise that command :( 
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [E][ ] meeting (from: Mon to: Tue)
     Now you have 3 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! I don't recognise that command :( 
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] stable
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Mon to: Tue)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
     ____________________________________________________________
```

## Test case: Handle EOF input

- Aim: Verify that the chatbot exits cleanly when the input stream ends before `bye`, such as when the user sends Ctrl+D.

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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] before eof
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] alpha
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A todo description cannot be empty. Try: todo <description>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] alpha
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [D][ ] report (by: Friday)
     Now you have 2 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A deadline must look like: deadline <description> /by <date/time>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] alpha
     2.[D][ ] report (by: Friday)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [E][ ] meeting (from: Monday to: Tuesday)
     Now you have 3 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! An event must look like: event <description> /from <start> /to <end>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] alpha
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Monday to: Tuesday)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Nice! I've marked this task as done:
       [T][X] alpha
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! That task number is not in your list. Try: mark 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][X] alpha
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Monday to: Tuesday)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     OK, I've marked this task as not done yet:
       [T][ ] alpha
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! Please provide a valid task number. Try: unmark 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] alpha
     2.[D][ ] report (by: Friday)
     3.[E][ ] meeting (from: Monday to: Tuesday)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] spaced description
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A todo description cannot be empty. Try: todo <description>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] spaced description
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [D][ ] submit report (by: Friday)
     Now you have 2 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A deadline must look like: deadline <description> /by <date/time>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [E][ ] meeting (from: Mon to: Tue)
     Now you have 3 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! An event must look like: event <description> /from <start> /to <end>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] spaced description
     2.[D][ ] submit report (by: Friday)
     3.[E][ ] meeting (from: Mon to: Tue)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A todo description cannot be empty. Try: todo <description>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! I don't recognise that command :( 
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] spaced description
     2.[D][ ] submit report (by: Friday)
     3.[E][ ] meeting (from: Mon to: Tue)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
     ____________________________________________________________
```

## Test case: Handle invalid commands

- Aim: Verify that empty task descriptions, bare task text, unknown commands, malformed deadlines/events, and invalid task numbers produce helpful errors without adding tasks.

### Inputs

```text
todo
blah
read book
deadline return book
event project meeting /from Mon 2pm
mark abc
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A todo description cannot be empty. Try: todo <description>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! I don't recognise that command :( 
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! I don't recognise that command :( 
     Try todo, deadline, event, list, mark, unmark, delete, and bye.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! A deadline must look like: deadline <description> /by <date/time>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! An event must look like: event <description> /from <start> /to <end>.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Oops! Please provide a valid task number. Try: mark 1.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
     ____________________________________________________________
```

## Test case: Add and list a task

- Aim: Verify that a todo is added and then displayed by the `list` command.

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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] read book
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] read book
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
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
     Hello! I'm Herta.
     What can I do for you?
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] borrow book
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [D][ ] return book (by: Sunday)
     Now you have 2 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Got it. I've added this task:
       [E][ ] project meeting (from: Mon 2pm to: 4pm)
     Now you have 3 tasks in the list.
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] borrow book
     2.[D][ ] return book (by: Sunday)
     3.[E][ ] project meeting (from: Mon 2pm to: 4pm)
     ____________________________________________________________
Enter a command:      ____________________________________________________________
     Bye. Hope to see you again soon!
     ____________________________________________________________
```
