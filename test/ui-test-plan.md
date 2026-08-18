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

## Test case: Add and list a task

- Aim: Verify that a task is added and then displayed by the `list` command.

### Inputs

```text
read book
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
     added: read book
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Here are the tasks in your list:
     1.[ ] read book
     ____________________________________________________________
Enter a command ('bye' to exit):      ____________________________________________________________
     Bye. Hope to see you again soon!
     ____________________________________________________________
```
