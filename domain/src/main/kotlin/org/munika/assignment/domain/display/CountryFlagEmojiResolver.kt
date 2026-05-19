package org.munika.assignment.domain.display

import java.text.Normalizer
import java.util.Locale

/**
 * Resolves display-only flag emoji for MyMUN position names.
 *
 * The resolver is intentionally conservative: it prefers exact names and generated abbreviations,
 * falls back to guarded substring matching, and returns no emoji when the best match is ambiguous.
 */
object CountryFlagEmojiResolver {
    private const val ResourcePath = "/org/munika/assignment/domain/flags/emoji-flags.tsv"

    private val index: FlagIndex by lazy { FlagIndex(loadEntries()) }

    fun emojiFor(positionName: String): String? = index.resolve(positionName)?.emoji

    fun assetNameFor(positionName: String): String? = emojiFor(positionName)?.twemojiAssetName()

    fun displayNameFor(positionName: String): String =
        emojiFor(positionName)?.let { emoji -> "$emoji $positionName" } ?: positionName

    private fun loadEntries(): List<FlagEntry> {
        val stream = CountryFlagEmojiResolver::class.java.getResourceAsStream(ResourcePath)
            ?: error("Missing bundled country flag emoji resource: $ResourcePath")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split('\t', limit = 2)
                    check(parts.size == 2) { "Invalid flag emoji row: $line" }
                    FlagEntry(parts[0], parts[1])
                }
                .toList()
        }
    }
}

private class FlagIndex(entries: List<FlagEntry>) {
    private val exactIndex: Map<String, Set<FlagEntry>>
    private val abbreviationIndex: Map<String, Set<FlagEntry>>
    private val searchableEntries: List<SearchableFlagEntry>

    init {
        val exactEntries = mutableMapOf<String, MutableSet<FlagEntry>>()
        val abbreviationEntries = mutableMapOf<String, MutableSet<FlagEntry>>()
        val searchable = mutableListOf<SearchableFlagEntry>()
        entries.forEach { entry ->
            val forms = entry.nameForms()
            forms.forEach { form -> exactEntries.add(form, entry) }
            entry.aliases().forEach { alias -> exactEntries.add(alias, entry) }
            forms.flatMap { it.abbreviations() }
                .forEach { abbreviation -> abbreviationEntries.add(abbreviation, entry) }
            searchable += SearchableFlagEntry(entry, forms.filter { it.canBeSubstringMatch() }.toSet())
        }
        exactIndex = exactEntries
        abbreviationIndex = abbreviationEntries
        searchableEntries = searchable
    }

    fun resolve(positionName: String): FlagEntry? {
        val normalized = positionName.normalizeCountryName()
        if (normalized.isBlank()) return null
        exactIndex[normalized]?.singleOrNull()?.let { return it }
        abbreviationIndex[normalized]?.singleOrNull()?.let { return it }
        if (!normalized.canBeSubstringMatch()) return null
        return substringMatch(normalized)
    }

    private fun substringMatch(normalized: String): FlagEntry? {
        val candidates = searchableEntries.mapNotNull { searchable ->
            val bestForm = searchable.forms
                .filter { form -> normalized.containsTokenSequence(form) || form.containsTokenSequence(normalized) }
                .maxByOrNull { it.length }
            bestForm?.let { Candidate(searchable.entry, it.length) }
        }
        val bestScore = candidates.maxOfOrNull { it.score } ?: return null
        val bestEntries = candidates.filter { it.score == bestScore }.map { it.entry }.toSet()
        return bestEntries.singleOrNull()
    }
}

private data class FlagEntry(
    val emoji: String,
    val name: String,
)

private data class SearchableFlagEntry(
    val entry: FlagEntry,
    val forms: Set<String>,
)

private data class Candidate(
    val entry: FlagEntry,
    val score: Int,
)

private val ManualAliases: Map<String, String> = mapOf(
    "usa" to "United States",
    "u s a" to "United States",
    "united states of america" to "United States",
    "america" to "United States",
    "us" to "United States",
    "u s" to "United States",
    "uk" to "United Kingdom",
    "u k" to "United Kingdom",
    "great britain" to "United Kingdom",
    "britain" to "United Kingdom",
    "drc" to "Congo - Kinshasa",
    "democratic republic of the congo" to "Congo - Kinshasa",
    "republic of the congo" to "Congo - Brazzaville",
    "russian federation" to "Russia",
    "republic of korea" to "South Korea",
    "korea republic" to "South Korea",
    "dprk" to "North Korea",
    "democratic people's republic of korea" to "North Korea",
    "iran islamic republic" to "Iran",
    "islamic republic of iran" to "Iran",
    "syrian arab republic" to "Syria",
    "venezuela bolivarian republic" to "Venezuela",
    "bolivarian republic of venezuela" to "Venezuela",
    "bolivia plurinational state" to "Bolivia",
    "plurinational state of bolivia" to "Bolivia",
    "united republic of tanzania" to "Tanzania",
    "holy see" to "Vatican City",
).mapKeys { (alias) -> alias.normalizeCountryName() }

private val GenericSubstringForms: Set<String> = setOf(
    "arab",
    "american",
    "british",
    "central",
    "congo",
    "democratic",
    "east",
    "federal",
    "french",
    "island",
    "islands",
    "kingdom",
    "north",
    "northern",
    "people",
    "peoples",
    "republic",
    "saint",
    "south",
    "state",
    "states",
    "territories",
    "territory",
    "united",
    "west",
    "western",
)

private val AbbreviationStopWords: Set<String> = setOf(
    "and",
    "of",
    "the",
)

private fun FlagEntry.nameForms(): Set<String> {
    val normalized = name.normalizeCountryName()
    val forms = mutableSetOf(normalized)
    val withoutParentheses = name.replace(Regex("\\([^)]*\\)"), " ").normalizeCountryName()
    if (withoutParentheses.isNotBlank()) forms += withoutParentheses
    Regex("\\(([^)]*)\\)").findAll(name)
        .map { it.groupValues[1].normalizeCountryName() }
        .filter { it.isNotBlank() }
        .forEach { forms += it }
    if (" - " in name) {
        val parts = name.split(" - ").map { it.normalizeCountryName() }.filter { it.canBeSubstringMatch() }
        forms += parts
    }
    return forms
}

private fun FlagEntry.aliases(): Set<String> =
    ManualAliases
        .filterValues { it == name }
        .keys

private fun String.abbreviations(): Set<String> {
    val words = split(' ').filter { it.isNotBlank() && it !in AbbreviationStopWords }
    if (words.size < 2) return emptySet()
    val abbreviation = words.joinToString("") { it.first().toString() }
    return if (abbreviation.length >= 2) setOf(abbreviation) else emptySet()
}

private fun String.normalizeCountryName(): String {
    val withoutMarks = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return withoutMarks
        .lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("\\bu\\s*\\.\\s*s\\s*\\."), "u s")
        .replace(Regex("\\bu\\s*\\.\\s*k\\s*\\."), "u k")
        .replace(Regex("\\bst\\."), "saint")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun String.canBeSubstringMatch(): Boolean =
    length >= 4 && this !in GenericSubstringForms

private fun String.containsTokenSequence(other: String): Boolean =
    " $this ".contains(" $other ")

private fun String.twemojiAssetName(): String {
    val codepoints = mutableListOf<String>()
    var index = 0
    while (index < length) {
        val codepoint = Character.codePointAt(this, index)
        codepoints += codepoint.toString(16)
        index += Character.charCount(codepoint)
    }
    return codepoints.joinToString("-")
}

private fun <K, V> MutableMap<K, MutableSet<V>>.add(key: K, value: V) {
    getOrPut(key) { mutableSetOf() } += value
}
