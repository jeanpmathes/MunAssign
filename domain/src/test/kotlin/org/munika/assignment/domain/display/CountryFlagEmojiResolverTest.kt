package org.munika.assignment.domain.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryFlagEmojiResolverTest {
    @Test
    fun `resolves exact country names from bundled emoji data`() {
        assertEquals("\uD83C\uDDEB\uD83C\uDDF7", CountryFlagEmojiResolver.emojiFor("France"))
    }

    @Test
    fun `resolves generated abbreviations`() {
        assertEquals("\uD83C\uDDE6\uD83C\uDDEA", CountryFlagEmojiResolver.emojiFor("UAE"))
        assertEquals("\uD83C\uDDE8\uD83C\uDDEB", CountryFlagEmojiResolver.emojiFor("CAR"))
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", CountryFlagEmojiResolver.emojiFor("US"))
    }

    @Test
    fun `resolves manual aliases`() {
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", CountryFlagEmojiResolver.emojiFor("USA"))
        assertEquals("\uD83C\uDDEC\uD83C\uDDE7", CountryFlagEmojiResolver.emojiFor("UK"))
        assertEquals("\uD83C\uDDF0\uD83C\uDDF7", CountryFlagEmojiResolver.emojiFor("Republic of Korea"))
        assertEquals("\uD83C\uDDE8\uD83C\uDDE9", CountryFlagEmojiResolver.emojiFor("DRC"))
    }

    @Test
    fun `resolves guarded substring matches`() {
        assertEquals("\uD83C\uDDEB\uD83C\uDDF7", CountryFlagEmojiResolver.emojiFor("Republic of France"))
        assertEquals("\uD83C\uDDEB\uD83C\uDDF7", CountryFlagEmojiResolver.emojiFor("France - GA"))
    }

    @Test
    fun `does not resolve ambiguous generic or role names`() {
        assertNull(CountryFlagEmojiResolver.emojiFor("Republic"))
        assertNull(CountryFlagEmojiResolver.emojiFor("United"))
        assertNull(CountryFlagEmojiResolver.emojiFor("South"))
        assertNull(CountryFlagEmojiResolver.emojiFor("Congo"))
        assertNull(CountryFlagEmojiResolver.emojiFor("Chair"))
        assertNull(CountryFlagEmojiResolver.emojiFor("Observer"))
        assertNull(CountryFlagEmojiResolver.emojiFor("Press Corps"))
    }

    @Test
    fun `decorates country names and keeps role names plain`() {
        assertEquals("\uD83C\uDDEB\uD83C\uDDF7 France", CountryFlagEmojiResolver.displayNameFor("France"))
        assertEquals("1f1eb-1f1f7", CountryFlagEmojiResolver.assetNameFor("France"))
        assertEquals("Press Corps", CountryFlagEmojiResolver.displayNameFor("Press Corps"))
    }
}
