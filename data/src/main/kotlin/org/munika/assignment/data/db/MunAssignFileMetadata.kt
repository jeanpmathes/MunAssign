package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import org.sqlite.SQLiteConfig

/** Persisted role of a MunAssign SQLite file. */
enum class MunAssignFileKind {
    ApplicationDatabase,
    ConferenceTransfer,
    SynchronizedConference,
}

/** Stable marker stored inside every newly created MunAssign SQLite file. */
data class MunAssignFileMetadata(
    val fileId: String,
    val kind: MunAssignFileKind,
)

/** Read-only classification result for a candidate MunAssign file. */
sealed interface MunAssignFileInspection {
    val schemaVersion: Int?

    data class Valid(
        val metadata: MunAssignFileMetadata,
        override val schemaVersion: Int,
        val conferenceId: String?,
        val conferenceName: String?,
        val conferenceCount: Int,
    ) : MunAssignFileInspection

    data class LegacyTransfer(
        override val schemaVersion: Int,
        val conferenceId: String?,
        val conferenceName: String?,
        val conferenceCount: Int,
    ) : MunAssignFileInspection

    data class Invalid(
        val message: String,
        override val schemaVersion: Int? = null,
        val inaccessible: Boolean = false,
    ) : MunAssignFileInspection
}

/** Reads file identity and conference cardinality without migrating or otherwise modifying the file. */
class MunAssignFileInspector {
    fun inspect(path: Path): MunAssignFileInspection {
        if (!Files.isRegularFile(path)) {
            return MunAssignFileInspection.Invalid("The selected file does not exist.")
        }
        return runCatching {
            readOnlyConnection(path).use connectionUse@ { connection ->
                if (!connection.tableExists("schema_migrations")) {
                    return@connectionUse MunAssignFileInspection.Invalid("The selected file is not a MunAssign file.")
                }
                val version = connection.querySingleInt("SELECT MAX(version) FROM schema_migrations")
                    ?: return@connectionUse MunAssignFileInspection.Invalid("The selected file has no schema version.")
                val conferences = if (connection.tableExists("conferences")) {
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT id, name FROM conferences ORDER BY rowid").use { result ->
                            buildList {
                                while (result.next()) add(result.getString("id") to result.getString("name"))
                            }
                        }
                    }
                } else {
                    emptyList()
                }
                if (!connection.tableExists("munassign_file_metadata")) {
                    return@connectionUse MunAssignFileInspection.LegacyTransfer(
                        schemaVersion = version,
                        conferenceId = conferences.singleOrNull()?.first,
                        conferenceName = conferences.singleOrNull()?.second,
                        conferenceCount = conferences.size,
                    )
                }
                val metadata = connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT file_id, kind FROM munassign_file_metadata WHERE singleton = 1",
                    ).use { result ->
                        if (!result.next()) null else {
                            val kind = runCatching { MunAssignFileKind.valueOf(result.getString("kind")) }.getOrNull()
                                ?: return@connectionUse MunAssignFileInspection.Invalid(
                                    "The selected file has an unknown MunAssign file kind.",
                                    version,
                                )
                            MunAssignFileMetadata(result.getString("file_id"), kind)
                        }
                    }
                } ?: return@connectionUse MunAssignFileInspection.Invalid(
                    "The selected file has no MunAssign file identity.",
                    version,
                )
                MunAssignFileInspection.Valid(
                    metadata = metadata,
                    schemaVersion = version,
                    conferenceId = conferences.singleOrNull()?.first,
                    conferenceName = conferences.singleOrNull()?.second,
                    conferenceCount = conferences.size,
                )
            }
        }.getOrElse { exception ->
            MunAssignFileInspection.Invalid(
                message = if (exception is SecurityException || exception is java.nio.file.AccessDeniedException) {
                    "The selected file is not accessible."
                } else {
                    "The selected file is not a valid MunAssign SQLite file."
                },
                inaccessible = exception is SecurityException || exception is java.nio.file.AccessDeniedException,
            )
        }
    }

    private fun readOnlyConnection(path: Path): Connection {
        val config = SQLiteConfig().apply {
            setReadOnly(true)
            setBusyTimeout(5000)
        }
        return DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}", config.toProperties())
    }
}

/** Updates the file marker after a database has been created and migrated. */
class MunAssignFileMetadataStore(
    private val path: Path,
) {
    fun setKind(kind: MunAssignFileKind): MunAssignFileMetadata {
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.autoCommit = false
            return try {
                val existingId = connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT file_id FROM munassign_file_metadata WHERE singleton = 1",
                    ).use { result -> if (result.next()) result.getString("file_id") else null }
                }
                val fileId = existingId ?: UUID.randomUUID().toString()
                connection.prepareStatement(
                    """
                    INSERT INTO munassign_file_metadata(singleton, file_id, kind)
                    VALUES(1, ?, ?)
                    ON CONFLICT(singleton) DO UPDATE SET kind = excluded.kind
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, fileId)
                    statement.setString(2, kind.name)
                    statement.executeUpdate()
                }
                connection.commit()
                MunAssignFileMetadata(fileId, kind)
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
    }
}

private fun Connection.tableExists(tableName: String): Boolean =
    prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").use { statement ->
        statement.setString(1, tableName)
        statement.executeQuery().use { it.next() }
    }

private fun Connection.querySingleInt(sql: String): Int? =
    createStatement().use { statement ->
        statement.executeQuery(sql).use { result ->
            if (result.next()) result.getObject(1)?.let { (it as Number).toInt() } else null
        }
    }
