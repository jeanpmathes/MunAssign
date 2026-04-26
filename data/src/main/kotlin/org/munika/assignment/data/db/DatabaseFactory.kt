package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    fun defaultDatabasePath(home: Path = Path(System.getProperty("user.home"))): Path =
        home.resolve(".munassign").resolve("data.db")

    fun connect(path: Path = defaultDatabasePath()): Database {
        Files.createDirectories(path.parent)
        return Database.connect("jdbc:sqlite:${path.toAbsolutePath()}", driver = "org.sqlite.JDBC")
    }
}
