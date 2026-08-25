# Data Path UI Test Plan

- Program command: `powershell -NoProfile -ExecutionPolicy Bypass -File test/directory-data-and-run.ps1`
- Working directory: `.`
- Java requirement: Java 25
- Setup command: `javac -d out src/main/java/*.java`
- Session log: `test/directory-data-ui-test-session.log`

The launcher temporarily makes `data/herta.txt` a directory. Herta should
report the invalid data path instead of crashing.

## Test case: Reject a directory data path

- Aim: Verify that the data path must refer to a regular file.

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
     Failed to load tasks: data path is not a regular file.
```
