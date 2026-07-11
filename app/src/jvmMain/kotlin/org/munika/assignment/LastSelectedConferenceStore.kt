package org.munika.assignment

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private const val ConferenceIdKey = "conferenceId"

/**
 * Machine-local app state for the conference MunAssign should reopen on startup.
 */
class LastSelectedConferenceStore(
    private val path: Path,
) {
    /**
     * Loads the last selected conference ID, returning `null` when no usable value is saved.
     */
    fun load(): String? {
        val properties = Properties()
        runCatching {
            Files.newInputStream(path).use(properties::load)
        }.getOrElse {
            return null
        }
        return properties.getProperty(ConferenceIdKey)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Persists the last selected conference ID, or clears it when [conferenceId] is absent.
     */
    fun save(conferenceId: String?) {
        val properties = Properties().apply {
            conferenceId?.trim()?.takeIf { it.isNotBlank() }?.let { selected ->
                setProperty(ConferenceIdKey, selected)
            }
        }
        path.parent?.let(Files::createDirectories)
        Files.newOutputStream(path).use { output ->
            properties.store(output, "MunAssign last selected conference")
        }
    }
}
