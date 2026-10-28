---
phase: 1
title: Baseline and package rename
status: completed
priority: P2
dependencies: []
---

# Phase 1: Baseline and package rename

## Overview

Get a revertible baseline commit in place, then do the purely mechanical rename `com.example.app` →
`com.company.project`. Class bodies don't change, only package lines, imports, and three class names.

## Requirements

- Functional: identical behavior; every existing test passes.
- Non-functional: one mechanical diff that's easy to review; no layout changes yet.

## Related Code Files

All paths under `spring-base/`.

- Move: `src/main/java/com/example/app/**` → `src/main/java/com/company/project/**` (same subfolders for now)
- Move: `src/test/java/com/example/app/**` → `src/test/java/com/company/project/**`
- Rename: `Application.java` → `ProjectApplication.java`
- Rename (test): `TestApplication.java` → `TestProjectApplication.java` (Boot's `Test<MainClass>` convention)
- Rename (test): `ApplicationTests.java` → `ProjectApplicationTest.java`
- Modify: `pom.xml`: `<groupId>com.example</groupId>` → `com.company` (artifactId, name stay `spring-base`)
- Modify: `compose.yaml`: comment path to `src/test/java/com/company/project/TestcontainersConfiguration.java`

## Implementation Steps

1. **Baseline commit (ask the user first).** The repo has no commits and everything is untracked, so nothing
   can be reverted. Ask the user, then `git add -A && git commit -m "chore: initial spring-base template"`
   (`.gitignore` already excludes `.env`, `target/`; confirm `git status` lists neither before committing).
2. `git mv src/main/java/com/example/app src/main/java/com/company/project` (create `com/company` first; same for
   `src/test`). Remove the empty `com/example` dirs. If the user declined the baseline commit, use plain `mv`
   (git can't move untracked files), and note that every later rollback step then has no git safety net.
3. Rewrite package/imports: `grep -rl 'com\.example\.app' src | xargs sed -i 's/com\.example\.app/com.company.project/g'`.
4. Rename `Application` → `ProjectApplication` (file + class + `SpringApplication.run(ProjectApplication.class, …)`),
   then update `TestApplication` → `TestProjectApplication` (`SpringApplication.from(ProjectApplication::main)`).
5. Rename `ApplicationTests` → `ProjectApplicationTest` (class name only).
6. Update `pom.xml` groupId and the `compose.yaml` comment.
7. `./mvnw clean verify`. **`clean` is mandatory**: `target/classes` still holds `com/example/app` classes, and a
   second `@SpringBootApplication` on the classpath would break the context. Record the surefire total
   ("Tests run: N"). Phase 4 compares against it.
8. Commit: `refactor: rename base package to com.company.project` (with the user's go-ahead, as in step 1).

## Success Criteria

- [ ] `grep -r "com.example" src pom.xml compose.yaml` empty.
- [ ] `./mvnw clean verify` green, same test count as before (no test lost).

## Risk Assessment

- **IDE stale config**: IntelliJ run configs/cache may point at the old main class. Mitigation: reload the Maven
  project; `.idea/` has no `com.example` references today (checked).
- **Missed reference**: string references (YAML, comments) aren't caught by the compiler. Mitigation: the grep in
  success criteria. `README.md` is deliberately excluded here and rewritten in phase 4.
- Rollback: `git reset --hard` to the baseline commit.
