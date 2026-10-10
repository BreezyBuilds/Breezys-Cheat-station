package com.breezybuilds.cheatstation.storage

data class StorageStatus(
    val state: StorageState,
    val title: String,
    val detail: String
)

fun StorageState.toStatus(
    detail: String? = null
): StorageStatus {
    val status = when (this) {
        StorageState.NOT_CONFIGURED ->
            "NOT CONFIGURED" to "Storage has not been configured."

        StorageState.DETECTED ->
            "DETECTED" to "Emulator detected; storage access is not connected yet."

        StorageState.ACCESSIBLE ->
            "READY" to "Storage is connected and available."

        StorageState.PERMISSION_REQUIRED ->
            "SETUP NEEDED" to "Storage was detected but permission is required."

        StorageState.TRANSFER_ONLY ->
            "TRANSFER ONLY" to "Cheats can be prepared for manual emulator import."

        StorageState.INVALID ->
            "INVALID" to "The configured emulator or storage is no longer available."
    }

    return StorageStatus(
        state = this,
        title = status.first,
        detail = detail ?: status.second
    )
}
