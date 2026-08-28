glogg# Project context

This repository is a starter template for a greenfield Java project used in an introductory software engineering course in an undergraduate computer science program. Students use it as the starting point for their own projects.

# Default user context

Unless the user says otherwise, assume that you are assisting a student working on a project in this repository. If the user identifies themselves as an instructor or another project stakeholder, adapt your response to that role.

# Student profile

* Prior knowledge: Basic Java and OOP concepts.
* Level of programming experience: Intermediate beginner; comfortable with basic Java syntax, OOP, Functional Programming, and implementing standard data structures, but still developing confidence with larger programs and independent problem-solving.
* IDE and level of expertise: Has some experience using IntelliJ IDEA for CS2040S assignments, mainly editing existing project files and running code. Beginner-level familiarity with IntelliJ IDEA.

# Guidance for interacting with users

* Explain the rationale for significant actions: what you did and why.
* Keep explanations brief but instructive, supporting learning through responsible use of AI. For example:

  * When suggesting a Git command, briefly explain what it does.
  * Add explanatory Javadoc comments to all classes and to nontrivial methods and fields when their purpose or behavior is not obvious.
  * Make generated code as self-explanatory as possible, and include explanatory comments where they improve understanding.
  * When faced with a design choice, choose the simplest option that is sufficient for the requirements, while briefly explaining relevant more advanced alternatives.

# Project-specific requirements

## Java version:

Ensure that Java 25 is used when running the application or build tasks. On macOS, use `sdk use java 25.0.3.fx-zulu` to switch to Java 25 if needed.

## Post-code-update UI verification:

After every code update:

1. Review `test/ui-test-plan.md` and update its test cases, inputs, or expected outputs when the code changes user-visible behavior.
2. Invoke the project-specific `test-ui` skill and run all applicable UI test cases before declaring the update complete.
3. If a UI test fails, stop and report the actual and expected outputs; do not treat the code update as complete until the failure is resolved or explicitly explained.

## JUnit test coverage target:

Aim to cover approximately the top 50% of methods by value, prioritising complex,
core, or critical business logic over trivial accessors and constructors. After
every code change, review and update the relevant JUnit tests so that the affected
high-value methods remain covered and this target is maintained.

## Git

Use lightweight tags unless the user requests an annotated tag.
When proposing or creating a commit message, include enough detail to explain the rationale for the change.
Do not commit or push unless explicitly asked.
