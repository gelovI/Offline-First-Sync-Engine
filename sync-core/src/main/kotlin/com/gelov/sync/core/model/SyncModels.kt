package com.gelov.sync.core.model

import java.time.Instant

enum class Op { UPSERT, DELETE }

data class Change(
    val entity: String,
    val id: String,
    val op: Op,
    val clientUpdatedAt: Instant,
    val payloadJson: String?
)
