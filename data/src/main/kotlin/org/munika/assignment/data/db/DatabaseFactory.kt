package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import kotlin.io.path.Path
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
import org.munika.assignment.data.db.tables.AnswerRuleTypeTable
import org.munika.assignment.data.db.tables.ConferenceTable
import org.munika.assignment.data.db.tables.MunAssignTables
import org.munika.assignment.data.db.tables.MunAssignFileMetadataTable
import org.munika.assignment.data.db.tables.MymunUpdateFilenamePartTable
import org.munika.assignment.data.db.tables.MymunUpdateFilenameSignatureTable
import org.munika.assignment.data.db.tables.MymunUpdateIssueTable
import org.munika.assignment.data.db.tables.MymunUpdateRunTable
import org.munika.assignment.data.db.tables.PreferenceCompletionTable
import org.munika.assignment.data.db.tables.QuestionHandlingTypeTable
import org.munika.assignment.data.db.tables.QuestionRoleTable
import org.munika.assignment.data.db.tables.ReservationDelegateLinkTable
import org.munika.assignment.data.db.tables.ReservationTable
import org.munika.assignment.data.db.tables.SchemaMigrationTable
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.MymunUpdateFilenameCandidate
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.sqlite.SQLiteConfig

/**
 * Creates SQLite connections for the local MunAssign database.
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    /** Highest applied migration version; derived so it cannot drift from the migrations list. */
    val CURRENT_SCHEMA_VERSION: Int by lazy { migrations.maxOf { it.version } }

    /** Returns the application database path beneath the supplied user [home]. */
    fun defaultDatabasePath(home: Path = Path(System.getProperty("user.home"))): Path =
        home.resolve(".munassign").resolve("data.db")

    /** Opens the SQLite database at [path], applies migrations, and seeds stable lookup rows. */
    fun connect(path: Path = defaultDatabasePath()): Database {
        Files.createDirectories(path.parent)
        val url = "jdbc:sqlite:${path.toAbsolutePath()}"
        val config = SQLiteConfig().apply {
            enforceForeignKeys(true)
            setBusyTimeout(5000)
            setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
        }
        val database = Database.connect(getNewConnection = { DriverManager.getConnection(url, config.toProperties()) })
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
                TransactionManager.current().exec(
                    "UPDATE conferences SET display_order = ? WHERE id = ?",
                    args = listOf(IntegerColumnType() to index, VarCharColumnType() to id),
                )
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
        SchemaMigration(version = 8, name = "add_committee_mode_and_position_mapping") {
            addColumnIfMissing("committees", "mode", "VARCHAR(32) NOT NULL DEFAULT 'University'")
            addColumnIfMissing("positions", "mapped_committee_id", "VARCHAR(36) NULL REFERENCES committees(id) ON DELETE SET NULL")
            TransactionManager.current().exec(
                "UPDATE committees SET mode = CASE WHEN is_high_school_committee THEN 'HighSchool' ELSE 'University' END",
            )
        },
        SchemaMigration(version = 9, name = "normalize_role_backed_question_handling") {
            TransactionManager.current().exec(
                """
                UPDATE delegate_questions
                SET handling_type_id = '${StandardQuestionHandlingTypes.ExpectedTypedAnswer.id}'
                WHERE role_id IS NOT NULL
                  AND handling_type_id <> '${StandardQuestionHandlingTypes.ExpectedTypedAnswer.id}'
                """.trimIndent(),
            )
        },
        SchemaMigration(version = 10, name = "add_delegation_organizer_state") {
            addColumnIfMissing("delegations", "is_high_school_delegation", "BOOLEAN NOT NULL DEFAULT FALSE")
            addColumnIfMissing("delegations", "needs_review", "BOOLEAN NOT NULL DEFAULT FALSE")
        },
        SchemaMigration(version = 11, name = "add_mymun_update_filename_signatures") {
            SchemaUtils.create(MymunUpdateFilenameSignatureTable, MymunUpdateFilenamePartTable)
            backfillMymunUpdateFilenameSignatures()
        },
        SchemaMigration(version = 12, name = "add_munassign_file_metadata") {
            SchemaUtils.create(MunAssignFileMetadataTable)
            if (MunAssignFileMetadataTable.selectAll().none()) {
                MunAssignFileMetadataTable.insert {
                    it[singleton] = 1
                    it[fileId] = UUID.randomUUID().toString()
                    it[kind] = MunAssignFileKind.ApplicationDatabase.name
                }
            }
        },
        SchemaMigration(version = 13, name = "add_payment_tiers") {
            addColumnIfMissing(
                "committees",
                "payment_tier",
                "VARCHAR(32) NOT NULL DEFAULT '${PaymentTier.Full.name}'",
            )
            addColumnIfMissing("delegates", "payment_tier", "VARCHAR(32) NULL")
        },
        SchemaMigration(version = 14, name = "add_preference_completions") {
            SchemaUtils.create(PreferenceCompletionTable)
        },
        SchemaMigration(version = 15, name = "expand_preference_completions_to_corrections") {
            addColumnIfMissing("conferences", "last_mymun_preference_ranks", "VARCHAR(512) NULL")
            if (preferenceCompletionSchemaNeedsUpgrade()) {
                TransactionManager.current().exec(
                    """
                    CREATE TABLE preference_completions_v15 (
                        id VARCHAR(36) NOT NULL PRIMARY KEY,
                        delegate_id VARCHAR(36) NOT NULL REFERENCES delegates(id) ON DELETE CASCADE,
                        position_id VARCHAR(36) NULL REFERENCES positions(id) ON DELETE SET NULL,
                        rank INT NOT NULL,
                        origin VARCHAR(32) NULL,
                        empty_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
                        baseline_position_id VARCHAR(36) NULL REFERENCES positions(id) ON DELETE SET NULL,
                        baseline_raw_preference_text TEXT NULL,
                        detected_ranks VARCHAR(512) NULL
                    )
                    """.trimIndent(),
                )
                TransactionManager.current().exec(
                    """
                    INSERT INTO preference_completions_v15 (
                        id, delegate_id, position_id, rank, origin, empty_acknowledged,
                        baseline_position_id, baseline_raw_preference_text, detected_ranks
                    )
                    SELECT id, delegate_id, position_id, rank, 'Organizer', FALSE, NULL, NULL, NULL
                    FROM preference_completions
                    """.trimIndent(),
                )
                TransactionManager.current().exec("DROP TABLE preference_completions")
                TransactionManager.current().exec("ALTER TABLE preference_completions_v15 RENAME TO preference_completions")
                TransactionManager.current().exec(
                    "CREATE UNIQUE INDEX ux_preference_completions_delegate_rank " +
                        "ON preference_completions(delegate_id, rank)",
                )
                TransactionManager.current().exec(
                    "CREATE UNIQUE INDEX ux_preference_completions_delegate_position " +
                        "ON preference_completions(delegate_id, position_id)",
                )
            }
        },
    )

    private fun preferenceCompletionSchemaNeedsUpgrade(): Boolean =
        TransactionManager.current().exec("PRAGMA table_info(preference_completions)") { resultSet ->
            var hasOrigin = false
            var positionIsRequired = false
            while (resultSet.next()) {
                when (resultSet.getString("name")) {
                    "origin" -> hasOrigin = true
                    "position_id" -> positionIsRequired = resultSet.getInt("notnull") == 1
                }
            }
            !hasOrigin || positionIsRequired
        } ?: true

    private fun backfillMymunUpdateFilenameSignatures() {
        ConferenceTable.selectAll().toList().forEach { conference ->
            val conferenceId = conference[ConferenceTable.id]
            val latestSuccessfulRun = MymunUpdateRunTable.selectAll()
                .where { MymunUpdateRunTable.conferenceId eq conferenceId }
                .orderBy(
                    MymunUpdateRunTable.completedAtEpochMillis to org.jetbrains.exposed.v1.core.SortOrder.DESC,
                    MymunUpdateRunTable.startedAtEpochMillis to org.jetbrains.exposed.v1.core.SortOrder.DESC,
                )
                .toList()
                .firstOrNull { run ->
                    run[MymunUpdateRunTable.completedAtEpochMillis] != null &&
                        MymunUpdateIssueTable.selectAll().where {
                            (MymunUpdateIssueTable.updateRunId eq run[MymunUpdateRunTable.id]) and
                                (MymunUpdateIssueTable.severity eq UpdateIssueSeverity.Error.name)
                        }.none()
                }
            val filename = latestSuccessfulRun?.get(MymunUpdateRunTable.sourceFilename) ?: return@forEach
            val candidate = MymunUpdateFilenameCandidate.fromFilename(filename)
            if (candidate.parts.isEmpty()) return@forEach

            val signatureId = UUID.randomUUID().toString()
            MymunUpdateFilenameSignatureTable.insert {
                it[id] = signatureId
                it[MymunUpdateFilenameSignatureTable.conferenceId] = conferenceId
                it[trustedAtEpochMillis] = latestSuccessfulRun[MymunUpdateRunTable.completedAtEpochMillis]!!
            }
            candidate.parts.forEachIndexed { index, part ->
                MymunUpdateFilenamePartTable.insert {
                    it[MymunUpdateFilenamePartTable.signatureId] = signatureId
                    it[displayOrder] = index
                    it[displayValue] = part.displayValue
                    it[normalizedValue] = part.normalizedValue
                }
            }
        }
    }

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
