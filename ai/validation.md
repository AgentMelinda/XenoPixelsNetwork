# Validation Matrix

| Change | Minimum focused check | Broader check |
|---|---|---|
| Pure Java logic | Relevant JUnit class | `./gradlew test` |
| Public API | API tests and standalone addon build | full build plus runtime example |
| Mixin/DMZ hook | compile plus exact target inspection | fresh client/server run and real trigger |
| Addon packet registry | registry tests and addon compile | login ping/pong on both logical sides |
| DMZ data patch | merge/file tests | fresh server start, backup, write, reload, second-start idempotence |
| Resources/UI | resource lookup or focused render check | fresh client log and affected screen/world |
| Build/release | exact task and artifact inspection | CI-equivalent build/hash checks |

## Standard commands

```
./gradlew test
./gradlew build jarJar serverJar -PofflineMcMeta
./gradlew buildApiExampleAddon -PofflineMcMeta
./gradlew runServer
./gradlew runApiTestClient
```

Use `-PofflineMcMeta` for build/test reproducibility, not client/server runs that need the normal
runtime-native metadata variants.

## Evidence levels

- **Compiled:** source compiled only.
- **Unit verified:** named automated tests passed.
- **Packaged:** expected artifact exists and its contents were inspected.
- **Startup verified:** a fresh process loaded the relevant mod/code path.
- **Runtime verified:** the actual feature or packet produced current observable evidence.
- **Manual pending:** implementation exists, but required human gameplay interaction was not done.
