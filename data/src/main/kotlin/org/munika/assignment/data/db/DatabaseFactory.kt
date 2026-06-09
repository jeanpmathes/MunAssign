package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
import org.munika.assignment.data.db.tables.AnswerRuleTypeTable
import org.munika.assignment.data.db.tables.MunAssignTables
import org.munika.assignment.data.db.tables.QuestionHandlingTypeTable
import org.munika.assignment.data.db.tables.QuestionRoleTable
import org.munika.assignment.data.db.tables.ReservationDelegateLinkTable
import org.munika.assignment.data.db.tables.ReservationTable
import org.munika.assignment.data.db.tables.SchemaMigrationTable
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles

/**
 * Creates SQLite connections for the local MunAssign database.
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun defaultDatabasePath(home: Path = Path(System.getProperty("user.home"))): Path =
        home.resolve(".munassign").resolve("data.db")

    fun connect(path: Path = defaultDatabasePath()): Database {
        Files.createDirectories(path.parent)
        val database = Database.connect("jdbc:sqlite:${path.toAbsolutePath()}?foreign_keys=on", driver = "org.sqlite.JDBC")
        transaction(database) {
            migrateSchema()
            seedLookups()
        }
        return database
    }

    private fun migrateSchema() {
        SchemaUtils.create(SchemaMigrationTable)
        val appliedVersions = SchemaMigrationTable.selectAll()
            .map { it[SchemaMigrationTable.version] }
            .toSet()

        migrations.filterNot { it.version in appliedVersions }.forEach { migration ->
            logger.info("Applying database migration {} ({})", migration.version, migration.name)
            migration.apply()
            SchemaMigrationTable.insert {
                it[version] = migration.version
                it[name] = migration.name
                it[appliedAtEpochMillis] = System.currentTimeMillis()
            }
            logger.info("Applied database migration {} ({})", migration.version, migration.name)
        }
    }

    private fun seedLookups() {
        StandardQuestionRoles.all.forEach { role ->
            if (QuestionRoleTable.selectAll().none { it[QuestionRoleTable.id] == role.id }) {
                QuestionRoleTable.insert {
                    it[id] = role.id
                    it[name] = role.name
                }
            }
        }

        StandardQuestionHandlingTypes.all.forEach { handlingType ->
            if (QuestionHandlingTypeTable.selectAll().none { it[QuestionHandlingTypeTable.id] == handlingType.id }) {
                QuestionHandlingTypeTable.insert {
                    it[id] = handlingType.id
                    it[name] = handlingType.name
                }
            }
        }

        answerRuleDefaults.forEach { (id, name) ->
            if (AnswerRuleTypeTable.selectAll().none { it[AnswerRuleTypeTable.id] == id }) {
                AnswerRuleTypeTable.insert {
                    it[AnswerRuleTypeTable.id] = id
                    it[AnswerRuleTypeTable.name] = name
                }
            }
        }
    }

    private val answerRuleDefaults: List<Pair<String, String>> = listOf(
        "minimum_sentence_count" to "Minimum sentence count",
        "minimum_text_length" to "Minimum text length",
        "notice_when_affirmative" to "Notice when affirmative",
        "notice_when_non_blank" to "Notice when non-blank",
        "required_answer" to "Required answer",
    )

    private val migrations: List<SchemaMigration> = listOf(
        SchemaMigration(version = 1, name = "create_initial_schema") {
            SchemaUtils.create(*MunAssignTables)
        },
        SchemaMigration(version = 2, name = "add_structured_update_issue_metadata") {
            addColumnIfMissing("mymun_update_issues", "code", "VARCHAR(128) NULL")
            addColumnIfMissing("mymun_update_issues", "column_number", "INT NULL")
        },
        SchemaMigration(version = 3, name = "add_conference_display_order") {
            addColumnIfMissing("conferences", "display_order", "INT NOT NULL DEFAULT 0")
            val conferenceIds = TransactionManager.current().exec(
                "SELECT id FROM conferences ORDER BY updated_at_epoch_millis, created_at_epoch_millis, id",
            ) { resultSet ->
                generateSequence {
                    if (resultSet.next()) resultSet.getString("id") else null
                }.toList()
            }.orEmpty()
            conferenceIds.forEachIndexed { index, id ->
                TransactionManager.current().exec("UPDATE conferences SET display_order = $index WHERE id = '$id'")
            }
        },
        SchemaMigration(version = 4, name = "add_question_display_order") {
            addColumnIfMissing("delegate_questions", "display_order", "INT NOT NULL DEFAULT 0")
            val questionIds = TransactionManager.current().exec(
                "SELECT id FROM delegate_questions ORDER BY conference_id, rowid",
            ) { resultSet ->
                generateSequence {
                    if (resultSet.next()) resultSet.getString("id") else null
                }.toList()
            }.orEmpty()
            questionIds.forEachIndexed { index, id ->
                TransactionManager.current().exec("UPDATE delegate_questions SET display_order = $index WHERE id = '$id'")
            }
        },
        SchemaMigration(version = 5, name = "add_committee_high_school_status") {
            addColumnIfMissing("committees", "is_high_school_committee", "BOOLEAN NOT NULL DEFAULT FALSE")
        },
        SchemaMigration(version = 6, name = "add_answer_issue_resolutions") {
            SchemaUtils.create(AnswerIssueResolutionTable)
        },
        SchemaMigration(version = 7, name = "add_reservations") {
            SchemaUtils.create(ReservationTable, ReservationDelegateLinkTable)
        },
    )

    private fun addColumnIfMissing(tableName: String, columnName: String, definition: String) {
        if (!columnExists(tableName, columnName)) {
            TransactionManager.current().exec("ALTER TABLE $tableName ADD COLUMN $columnName $definition")
        }
    }

    private fun columnExists(tableName: String, columnName: String): Boolean =
        TransactionManager.current().exec("PRAGMA table_info($tableName)") { resultSet ->
            generateSequence {
                if (resultSet.next()) resultSet.getString("name") else null
            }.any { it == columnName }
        } ?: false

    private data class SchemaMigration(
        val version: Int,
        val name: String,
        val apply: () -> Unit,
    )
}
