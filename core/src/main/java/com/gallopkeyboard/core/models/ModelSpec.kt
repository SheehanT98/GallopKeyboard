package com.gallopkeyboard.core.models

/**
 * Describes a single downloadable on-device model file.
 *
 * @param id Stable identifier (e.g. "parakeet-encoder").
 * @param url HTTPS URL to a version-pinned release file.
 * @param sha256 Lowercase hex SHA-256 digest (64 characters).
 * @param sizeBytes Exact byte length of the release file.
 * @param relPath Path relative to [android.content.Context.filesDir].
 */
data class ModelSpec(
    val id: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val relPath: String,
) {
    init {
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "sha256 must be 64 lowercase hex characters for $id"
        }
    }
}
