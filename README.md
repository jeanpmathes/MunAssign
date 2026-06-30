# MunAssign

MunAssign is a desktop assignment manager for Model United Nations conferences.

The project follows the architecture described in [docs/DESIGN.md](docs/DESIGN.md):

- `domain` contains pure Kotlin models, repository interfaces, and use cases.
- `data` contains SQLite/Exposed persistence and MyMUN Excel import code.
- `app` contains the Compose Desktop UI, ViewModels, and Koin wiring.

Run the desktop app with:

```shell
.\gradlew.bat :app:run
```

Build a portable Windows zip with:

```shell
.\gradlew.bat :app:packagePortableZip
```

The zip is also produced by normal Gradle assemble/build tasks. It is written
under `app/build/compose/binaries/main/zip` and contains `MunAssign.exe`,
`app/`, and `runtime/` at the archive root. The app does not install or
register anything system-wide.
