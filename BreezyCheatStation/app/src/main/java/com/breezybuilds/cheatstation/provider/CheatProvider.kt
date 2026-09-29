package com.breezybuilds.cheatstation.provider

enum class ProviderError { OFFLINE, NOT_FOUND, RATE_LIMITED, HTTP, INVALID, CONFIG }

class ProviderException(val kind: ProviderError, message: String) : Exception(message)

data class ProviderResult(val titleId: String, val text: String, val fetchedAt: Long, val sourceUrl: String)

/** A source of cheat files. Add new sources by implementing this interface. */
interface CheatProvider {
    val id: String
    val displayName: String

    /** Downloads the raw cheat file text for a Title ID. Throws [ProviderException] with a user-friendly message. */
    suspend fun fetch(titleId: String): ProviderResult

    /** Title IDs the source has cheats for, or null when the source can't enumerate them. */
    suspend fun listTitleIds(): Set<String>?
}
