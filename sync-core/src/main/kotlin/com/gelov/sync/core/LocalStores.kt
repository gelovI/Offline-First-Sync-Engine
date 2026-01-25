package com.gelov.sync.core

import com.gelov.sync.core.model.Change

data class OutboxItem(
    val outboxId: Long,
    val change: Change
)

interface LocalOutbox {
    fun enqueue(change: Change)
    fun peekBatch(limit: Int): List<OutboxItem>

    fun markAcked(changeIds: List<String>)
    fun markFailed(changeId: String, error: String)

    fun hasPending(): Boolean
}

interface CursorStore {
    fun getCursor(): Long
    fun setCursor(cursor: Long)
}
