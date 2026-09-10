# Skill: Testing and Runtime Diagnosis

- Start with named JUnit tests and expand to `./gradlew test` after focused success.
- Use `runServer` for dedicated dev-start compatibility and `runApiTestClient` for automatic
  example loading, integrated server start, and packet round-trip proof.
- Record timestamps and exact log lines that establish startup or runtime behavior.
- A long-running process invalidates claims about newly rebuilt classes; stop and restart first.
- Do not promote known unrelated warnings into task failures, but do record real exceptions and
  failed tasks.
- Gameplay-only events remain manual pending until someone performs the real action.
