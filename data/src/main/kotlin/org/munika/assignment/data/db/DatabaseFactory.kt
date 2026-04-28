package org.munika.assignment.data.db

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.munika.assignment.data.db.tables.AnswerRuleTypeTable
import org.munika.assignment.data.db.tables.MunAssignTables
import org.munika.assignment.data.db.tables.QuestionHandlingTypeTable
import org.munika.assignment.data.db.tables.QuestionRoleTable
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles

/**
 * Creates SQLite connections for the local MunAssign database.
 */
object DatabaseFactory {
    fun defaultDatabasePath(home: Path = Path(System.getProperty("user.home"))): Path =
        home.resolve(".munassign").resolve("data.db")

    fun connect(path: Path = defaultDatabasePath()): Database {
        Files.createDirectories(path.parent)
        val database = Database.connect("jdbc:sqlite:${path.toAbsolutePath()}?foreign_keys=on", driver = "org.sqlite.JDBC")
        transaction(database) {
            SchemaUtils.create(*MunAssignTables)
            seedLookups()
        }
        return database
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
        "notice_when_non_blank" to "Notice when non-blank",
        "required_answer" to "Required answer",
    )
}
