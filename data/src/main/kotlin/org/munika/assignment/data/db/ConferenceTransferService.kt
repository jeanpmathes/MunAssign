package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports and exports one conference as a self-contained `.munassign` SQLite snapshot.
 *
 * Transfer files keep the persisted schema shape, while imports remap conference-owned IDs so
 * snapshots can safely move between local databases.
 */
class ConferenceTransferService(
    private val databasePath: Path = DatabaseFactory.defaultDatabasePath(),
) {
    suspend fun exportConference(conferenceId: String, destinationPath: Path): Unit = withContext(Dispatchers.IO) {
        require(databasePath.toAbsolutePath().normalize() != destinationPath.toAbsolutePath().normalize()) {
            "Cannot export over the active MunAssign database."
        }
        Files.createDirectories(destinationPath.toAbsolutePath().parent)
        val tempPath = Files.createTempFile(destinationPath.toAbsolutePath().parent, "munassign-export-", ".tmp")
        Files.deleteIfExists(tempPath)
        try {
            DatabaseFactory.connect(tempPath)
            sqliteConnection(databasePath).use { source ->
                sqliteConnection(tempPath).use { target ->
                    target.transaction {
                        exportConferenceRows(source, target, conferenceId)
                    }
                }
            }
            Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (exception: Exception) {
            Files.deleteIfExists(tempPath)
            throw exception
        }
    }

    suspend fun importConferenceAsNew(sourcePath: Path): String = withContext(Dispatchers.IO) {
        importConference(sourcePath, replaceTargetConferenceId = null)
    }

    suspend fun replaceConference(targetConferenceId: String, sourcePath: Path): String = withContext(Dispatchers.IO) {
        importConference(sourcePath, replaceTargetConferenceId = targetConferenceId)
    }

    private fun importConference(sourcePath: Path, replaceTargetConferenceId: String?): String {
        require(databasePath.toAbsolutePath().normalize() != sourcePath.toAbsolutePath().normalize()) {
            "Cannot import the active MunAssign database."
        }
        require(Files.isRegularFile(sourcePath)) { "Transfer file does not exist." }
        val tempPath = Files.createTempFile("munassign-import-", ".db")
        try {
            Files.copy(sourcePath, tempPath, StandardCopyOption.REPLACE_EXISTING)
            validateTransferSchemaVersion(tempPath)
            DatabaseFactory.connect(tempPath)
            sqliteConnection(tempPath).use { source ->
                sqliteConnection(databasePath).use { target ->
                    return target.transaction {
                        importConferenceRows(source, target, replaceTargetConferenceId)
                    }
                }
            }
        } catch (exception: TransferException) {
            throw exception
        } catch (exception: IllegalArgumentException) {
            throw exception
        } catch (exception: Exception) {
            throw TransferException("The selected file is not a valid MunAssign transfer file.", exception)
        } finally {
            Files.deleteIfExists(tempPath)
        }
    }

    private fun validateTransferSchemaVersion(path: Path) {
        sqliteConnection(path).use { connection ->
            val hasMigrationTable = connection.tableExists("schema_migrations")
            if (!hasMigrationTable) {
                throw TransferException("The selected file is not a MunAssign transfer file.")
            }
            val version = connection.querySingleInt("SELECT MAX(version) FROM schema_migrations")
                ?: throw TransferException("The selected file is not a MunAssign transfer file.")
            if (version > DatabaseFactory.CURRENT_SCHEMA_VERSION) {
                throw TransferException("This transfer file was created by a newer MunAssign version.")
            }
        }
    }

    private fun exportConferenceRows(source: Connection, target: Connection, conferenceId: String) {
        val conference = source.selectRows("conferences", "id = ?", listOf(conferenceId)).singleOrNull()
            ?: throw IllegalArgumentException("Conference $conferenceId does not exist.")
        target.insertRow("conferences", conference)

        copyByConference(source, target, "delegations", conferenceId)
        copyByConference(source, target, "committees", conferenceId)
        val positionIds = copyByConference(source, target, "positions", conferenceId)
        val delegateIds = copyByConference(source, target, "delegates", conferenceId)
        val reservationIds = copyByConference(source, target, "reservations", conferenceId)
        val questionIds = copyByConference(source, target, "delegate_questions", conferenceId)
        val updateRunIds = copyByConference(source, target, "mymun_update_runs", conferenceId)

        copyByIds(source, target, "slots", "position_id", positionIds)
        copyByIds(source, target, "preferences", "delegate_id", delegateIds)
        copyByIds(source, target, "delegate_comments", "delegate_id", delegateIds)
        copyByIds(source, target, "delegate_question_rules", "question_id", questionIds)
        copyByIds(source, target, "mun_experience_answer_mappings", "question_id", questionIds)
        copyByIds(source, target, "high_schooler_answer_mappings", "question_id", questionIds)
        copyByIds(source, target, "language_level_answer_mappings", "question_id", questionIds)
        copyByIds(source, target, "delegate_question_answers", "delegate_id", delegateIds)
        copyByIds(source, target, "answer_issue_resolutions", "conference_id", setOf(conferenceId))
        copyByIds(source, target, "reservation_delegate_links", "reservation_id", reservationIds)
        copyByIds(source, target, "mymun_update_issues", "update_run_id", updateRunIds)
    }

    private fun importConferenceRows(source: Connection, target: Connection, replaceTargetConferenceId: String?): String {
        val sourceConferences = source.selectRows("conferences")
        if (sourceConferences.size != 1) {
            throw TransferException("Transfer files must contain exactly one conference.")
        }
        val sourceConference = sourceConferences.single()
        val sourceConferenceId = sourceConference.string("id")
        val now = System.currentTimeMillis()
        val targetConferenceId = replaceTargetConferenceId ?: UUID.randomUUID().toString()
        val conferenceIdMap = mapOf(sourceConferenceId to targetConferenceId)

        val delegationIdMap = source.idMap("delegations", "conference_id = ?", listOf(sourceConferenceId))
        val committeeIdMap = source.idMap("committees", "conference_id = ?", listOf(sourceConferenceId))
        val positionIdMap = source.idMap("positions", "conference_id = ?", listOf(sourceConferenceId))
        val delegateIdMap = source.idMap("delegates", "conference_id = ?", listOf(sourceConferenceId))
        val reservationIdMap = source.idMap("reservations", "conference_id = ?", listOf(sourceConferenceId))
        val questionIdMap = source.idMap("delegate_questions", "conference_id = ?", listOf(sourceConferenceId))
        val updateRunIdMap = source.idMap("mymun_update_runs", "conference_id = ?", listOf(sourceConferenceId))
        val slotIdMap = source.idMapIn("slots", "position_id", positionIdMap.keys)
        val preferenceIdMap = source.idMapIn("preferences", "delegate_id", delegateIdMap.keys)
        val commentIdMap = source.idMapIn("delegate_comments", "delegate_id", delegateIdMap.keys)
        val questionRuleIdMap = source.idMapIn("delegate_question_rules", "question_id", questionIdMap.keys)
        val munExperienceMapIdMap = source.idMapIn("mun_experience_answer_mappings", "question_id", questionIdMap.keys)
        val highSchoolerMapIdMap = source.idMapIn("high_schooler_answer_mappings", "question_id", questionIdMap.keys)
        val languageLevelMapIdMap = source.idMapIn("language_level_answer_mappings", "question_id", questionIdMap.keys)
        val answerIdMap = source.idMapIn("delegate_question_answers", "delegate_id", delegateIdMap.keys)
        val issueResolutionIdMap = source.idMap("answer_issue_resolutions", "conference_id = ?", listOf(sourceConferenceId))
        val reservationLinkIdMap = source.idMapIn("reservation_delegate_links", "reservation_id", reservationIdMap.keys)
        val updateIssueIdMap = source.idMapIn("mymun_update_issues", "update_run_id", updateRunIdMap.keys)

        if (replaceTargetConferenceId == null) {
            val displayOrder = target.querySingleInt("SELECT COALESCE(MAX(display_order), -1) + 1 FROM conferences") ?: 0
            target.insertRow(
                "conferences",
                sourceConference.mapValues { (column, value) ->
                    when (column) {
                        "id" -> targetConferenceId
                        "display_order" -> displayOrder
                        else -> value
                    }
                },
            )
        } else {
            val localConference = target.selectRows("conferences", "id = ?", listOf(replaceTargetConferenceId)).singleOrNull()
                ?: throw IllegalArgumentException("Conference $replaceTargetConferenceId does not exist.")
            deleteConferenceContents(target, replaceTargetConferenceId)
            target.updateConferenceForReplacement(
                conferenceId = replaceTargetConferenceId,
                name = localConference.string("name"),
                displayOrder = localConference.int("display_order"),
                createdAt = localConference.long("created_at_epoch_millis"),
                updatedAt = now,
                lastMymunUpdateAt = sourceConference.optionalLong("last_mymun_update_at_epoch_millis"),
            )
        }

        copyImportedByConference(source, target, "delegations", sourceConferenceId) { row ->
            row.remap("id", delegationIdMap)
                .remap("conference_id", conferenceIdMap)
                .remapNullable("head_delegate_id", delegateIdMap)
        }
        copyImportedByConference(source, target, "committees", sourceConferenceId) { row ->
            row.remap("id", committeeIdMap).remap("conference_id", conferenceIdMap)
        }
        copyImportedByConference(source, target, "positions", sourceConferenceId) { row ->
            row.remap("id", positionIdMap)
                .remap("conference_id", conferenceIdMap)
                .remap("committee_id", committeeIdMap)
                .remapNullable("mapped_committee_id", committeeIdMap)
        }
        copyImportedByConference(source, target, "delegates", sourceConferenceId) { row ->
            row.remap("id", delegateIdMap)
                .remap("conference_id", conferenceIdMap)
                .remapNullable("delegation_id", delegationIdMap)
        }
        copyImportedByConference(source, target, "reservations", sourceConferenceId) { row ->
            row.remap("id", reservationIdMap)
                .remap("conference_id", conferenceIdMap)
                .remapNullable("delegation_id", delegationIdMap)
        }
        copyImportedByConference(source, target, "delegate_questions", sourceConferenceId) { row ->
            row.remap("id", questionIdMap).remap("conference_id", conferenceIdMap)
        }
        copyImportedByConference(source, target, "mymun_update_runs", sourceConferenceId) { row ->
            row.remap("id", updateRunIdMap).remap("conference_id", conferenceIdMap)
        }

        copyImportedByIds(source, target, "slots", "position_id", positionIdMap.keys) { row ->
            row.remap("id", slotIdMap)
                .remap("position_id", positionIdMap)
                .remapNullable("assigned_delegate_id", delegateIdMap)
        }
        copyImportedByIds(source, target, "preferences", "delegate_id", delegateIdMap.keys) { row ->
            row.remap("id", preferenceIdMap)
                .remap("delegate_id", delegateIdMap)
                .remap("position_id", positionIdMap)
        }
        copyImportedByIds(source, target, "delegate_comments", "delegate_id", delegateIdMap.keys) { row ->
            row.remap("id", commentIdMap).remap("delegate_id", delegateIdMap)
        }
        copyImportedByIds(source, target, "delegate_question_rules", "question_id", questionIdMap.keys) { row ->
            row.remap("id", questionRuleIdMap).remap("question_id", questionIdMap)
        }
        copyImportedByIds(source, target, "mun_experience_answer_mappings", "question_id", questionIdMap.keys) { row ->
            row.remap("id", munExperienceMapIdMap).remap("question_id", questionIdMap)
        }
        copyImportedByIds(source, target, "high_schooler_answer_mappings", "question_id", questionIdMap.keys) { row ->
            row.remap("id", highSchoolerMapIdMap).remap("question_id", questionIdMap)
        }
        copyImportedByIds(source, target, "language_level_answer_mappings", "question_id", questionIdMap.keys) { row ->
            row.remap("id", languageLevelMapIdMap).remap("question_id", questionIdMap)
        }
        copyImportedByIds(source, target, "delegate_question_answers", "delegate_id", delegateIdMap.keys) { row ->
            row.remap("id", answerIdMap)
                .remap("delegate_id", delegateIdMap)
                .remap("question_id", questionIdMap)
        }
        copyImportedByConference(source, target, "answer_issue_resolutions", sourceConferenceId) { row ->
            row.remap("id", issueResolutionIdMap)
                .remap("conference_id", conferenceIdMap)
                .remap("delegate_id", delegateIdMap)
                .remapNullable("question_id", questionIdMap)
        }
        copyImportedByIds(source, target, "reservation_delegate_links", "reservation_id", reservationIdMap.keys) { row ->
            row.remap("id", reservationLinkIdMap)
                .remap("reservation_id", reservationIdMap)
                .remap("delegate_id", delegateIdMap)
        }
        copyImportedByIds(source, target, "mymun_update_issues", "update_run_id", updateRunIdMap.keys) { row ->
            row.remap("id", updateIssueIdMap).remap("update_run_id", updateRunIdMap)
        }

        return targetConferenceId
    }

    private fun copyByConference(source: Connection, target: Connection, table: String, conferenceId: String): Set<String> {
        val rows = source.selectRows(table, "conference_id = ?", listOf(conferenceId))
        rows.forEach { target.insertRow(table, it) }
        return rows.mapTo(mutableSetOf()) { it.string("id") }
    }

    private fun copyByIds(source: Connection, target: Connection, table: String, column: String, ids: Set<String>) {
        source.selectRowsIn(table, column, ids).forEach { target.insertRow(table, it) }
    }

    private fun copyImportedByConference(
        source: Connection,
        target: Connection,
        table: String,
        conferenceId: String,
        transform: (DbRow) -> DbRow,
    ) {
        source.selectRows(table, "conference_id = ?", listOf(conferenceId)).forEach { target.insertRow(table, transform(it)) }
    }

    private fun copyImportedByIds(
        source: Connection,
        target: Connection,
        table: String,
        column: String,
        ids: Set<String>,
        transform: (DbRow) -> DbRow,
    ) {
        source.selectRowsIn(table, column, ids).forEach { target.insertRow(table, transform(it)) }
    }

    private fun deleteConferenceContents(connection: Connection, conferenceId: String) {
        val delegateIds = connection.ids("delegates", "conference_id = ?", listOf(conferenceId))
        val positionIds = connection.ids("positions", "conference_id = ?", listOf(conferenceId))
        val questionIds = connection.ids("delegate_questions", "conference_id = ?", listOf(conferenceId))
        val reservationIds = connection.ids("reservations", "conference_id = ?", listOf(conferenceId))
        val updateRunIds = connection.ids("mymun_update_runs", "conference_id = ?", listOf(conferenceId))

        connection.deleteIn("mymun_update_issues", "update_run_id", updateRunIds)
        connection.deleteWhere("mymun_update_runs", "conference_id = ?", listOf(conferenceId))
        connection.deleteWhere("answer_issue_resolutions", "conference_id = ?", listOf(conferenceId))
        connection.deleteIn("reservation_delegate_links", "reservation_id", reservationIds)
        connection.deleteWhere("reservations", "conference_id = ?", listOf(conferenceId))
        connection.deleteIn("delegate_question_answers", "delegate_id", delegateIds)
        connection.deleteIn("language_level_answer_mappings", "question_id", questionIds)
        connection.deleteIn("high_schooler_answer_mappings", "question_id", questionIds)
        connection.deleteIn("mun_experience_answer_mappings", "question_id", questionIds)
        connection.deleteIn("delegate_question_rules", "question_id", questionIds)
        connection.deleteWhere("delegate_questions", "conference_id = ?", listOf(conferenceId))
        connection.deleteIn("preferences", "delegate_id", delegateIds)
        connection.deleteIn("delegate_comments", "delegate_id", delegateIds)
        connection.deleteIn("slots", "position_id", positionIds)
        connection.deleteWhere("positions", "conference_id = ?", listOf(conferenceId))
        connection.deleteWhere("delegates", "conference_id = ?", listOf(conferenceId))
        connection.deleteWhere("delegations", "conference_id = ?", listOf(conferenceId))
        connection.deleteWhere("committees", "conference_id = ?", listOf(conferenceId))
    }

    private fun sqliteConnection(path: Path): Connection =
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}?foreign_keys=on").also { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        }

    private fun <T> Connection.transaction(block: () -> T): T {
        val previousAutoCommit = autoCommit
        autoCommit = false
        return try {
            val result = block()
            commit()
            result
        } catch (exception: Exception) {
            rollback()
            throw exception
        } finally {
            autoCommit = previousAutoCommit
        }
    }
}

class TransferException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private typealias DbRow = Map<String, Any?>

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

private fun Connection.selectRows(table: String): List<DbRow> =
    createStatement().use { statement ->
        statement.executeQuery("SELECT * FROM $table").use { it.toRows() }
    }

private fun Connection.selectRows(table: String, where: String, args: List<Any?>): List<DbRow> =
    prepareStatement("SELECT * FROM $table WHERE $where").use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { it.toRows() }
    }

private fun Connection.selectRowsIn(table: String, column: String, ids: Set<String>): List<DbRow> {
    if (ids.isEmpty()) return emptyList()
    val placeholders = ids.joinToString(",") { "?" }
    return selectRows(table, "$column IN ($placeholders)", ids.toList())
}

private fun Connection.ids(table: String, where: String, args: List<Any?>): Set<String> =
    selectRows(table, where, args).mapTo(mutableSetOf()) { it.string("id") }

private fun Connection.idMap(table: String, where: String, args: List<Any?>): Map<String, String> =
    ids(table, where, args).associateWith { UUID.randomUUID().toString() }

private fun Connection.idMapIn(table: String, column: String, ids: Set<String>): Map<String, String> =
    selectRowsIn(table, column, ids).mapTo(mutableSetOf()) { it.string("id") }.associateWith { UUID.randomUUID().toString() }

private fun Connection.insertRow(table: String, row: DbRow) {
    val columns = row.keys.toList()
    val placeholders = columns.joinToString(",") { "?" }
    val columnNames = columns.joinToString(",")
    prepareStatement("INSERT INTO $table ($columnNames) VALUES ($placeholders)").use { statement ->
        columns.forEachIndexed { index, column -> statement.setObject(index + 1, row[column]) }
        statement.executeUpdate()
    }
}

private fun Connection.deleteWhere(table: String, where: String, args: List<Any?>) {
    prepareStatement("DELETE FROM $table WHERE $where").use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }
}

private fun Connection.deleteIn(table: String, column: String, ids: Set<String>) {
    if (ids.isEmpty()) return
    val placeholders = ids.joinToString(",") { "?" }
    deleteWhere(table, "$column IN ($placeholders)", ids.toList())
}

private fun Connection.updateConferenceForReplacement(
    conferenceId: String,
    name: String,
    displayOrder: Int,
    createdAt: Long,
    updatedAt: Long,
    lastMymunUpdateAt: Long?,
) {
    prepareStatement(
        """
        UPDATE conferences
        SET name = ?,
            display_order = ?,
            created_at_epoch_millis = ?,
            updated_at_epoch_millis = ?,
            last_mymun_update_at_epoch_millis = ?
        WHERE id = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, name)
        statement.setInt(2, displayOrder)
        statement.setLong(3, createdAt)
        statement.setLong(4, updatedAt)
        statement.setObject(5, lastMymunUpdateAt)
        statement.setString(6, conferenceId)
        statement.executeUpdate()
    }
}

private fun ResultSet.toRows(): List<DbRow> {
    val metadata = metaData
    val columns = (1..metadata.columnCount).map { metadata.getColumnName(it) }
    val rows = mutableListOf<DbRow>()
    while (next()) {
        rows += columns.associateWith { column -> getObject(column) }
    }
    return rows
}

private fun DbRow.string(column: String): String =
    requireNotNull(this[column] as? String) { "Expected text column $column." }

private fun DbRow.int(column: String): Int =
    (requireNotNull(this[column]) { "Expected integer column $column." } as Number).toInt()

private fun DbRow.long(column: String): Long =
    (requireNotNull(this[column]) { "Expected long column $column." } as Number).toLong()

private fun DbRow.optionalLong(column: String): Long? =
    (this[column] as? Number)?.toLong()

private fun DbRow.remap(column: String, idMap: Map<String, String>): DbRow =
    mapValues { (key, value) ->
        if (key == column) idMap.requireMapped(value as String, column) else value
    }

private fun DbRow.remapNullable(column: String, idMap: Map<String, String>): DbRow =
    mapValues { (key, value) ->
        if (key == column && value != null) idMap.requireMapped(value as String, column) else value
    }

private fun Map<String, String>.requireMapped(value: String, column: String): String =
    requireNotNull(this[value]) { "Transfer file references missing $column value $value." }
