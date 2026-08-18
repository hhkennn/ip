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
Enter a command ('bye' to exit):      ____________________________________________________________
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
Enter a command ('bye' to exit):      ____________________________________________________________
     Oops! A todo description cannot be empty. Try: todo <description>.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Oops! I don't recognise that command. Try todo, deadline, event, list, mark, or unmark.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Oops! I don't recognise that command. Try todo, deadline, event, list, mark, or unmark.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Oops! A deadline must look like: deadline <description> /by <date/time>.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Oops! An event must look like: event <description> /from <start> /to <end>.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Oops! Please provide a valid task number.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
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
Enter a command ('bye' to exit):      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] read book
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] read book
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
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
Enter a command ('bye' to exit):      ____________________________________________________________
     Got it. I've added this task:
       [T][ ] borrow book
     Now you have 1 tasks in the list.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Got it. I've added this task:
       [D][ ] return book (by: Sunday)
     Now you have 2 tasks in the list.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Got it. I've added this task:
       [E][ ] project meeting (from: Mon 2pm to: 4pm)
     Now you have 3 tasks in the list.
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Here are the tasks in your list:
     1.[T][ ] borrow book
     2.[D][ ] return book (by: Sunday)
     3.[E][ ] project meeting (from: Mon 2pm to: 4pm)
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Bye. Hope to see you again soon!
     ____________________________________________________________
```
