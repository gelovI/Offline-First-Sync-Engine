package com.gelov.sync.core.api

import kotlinx.serialization.Serializable

@Serializable
data class SyncPushRequest(
    val entity: String,
    val changes: List<WireChange>,
    val clientId: String
)

@Serializable
data class SyncPushResponse(
    val acceptedChangeIds: List<String>,
    val serverCursor: Long,
    val serverTime: String,
    val serverId: String
)

@Serializable
data class SyncPullResponse(
    val changes: List<WireChange>,
    val serverTime: String,
    val nextCursor: Long,
    val serverId: String
)

@Serializable
data class WireChange(
    val entity: String,
    val id: String,
    val op: String,            // "UPSERT" | "DELETE"
    val clientUpdatedAt: String, // ISO-8601 string
    val originClientId: String? = null,
    val payloadJson: String? = null,
    val changeId: String? = null
)

@Serializable
data class SeedNoteRequest(
    val id: String? = null,
    val title: String,
    val text: String
)

@Serializable
data class SeedNoteResponse(
    val accepted: Int,
    val serverCursor: Long,
    val serverTime: String
)
