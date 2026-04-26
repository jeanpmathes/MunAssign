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
