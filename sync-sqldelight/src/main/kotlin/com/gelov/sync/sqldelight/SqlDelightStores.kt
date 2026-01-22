package com.gelov.sync.sqldelight

import com.gelov.sync.core.CursorStore
import com.gelov.sync.core.LocalOutbox
import com.gelov.sync.core.OutboxItem
import com.gelov.sync.core.model.Change
import com.gelov.sync.core.model.Op
import com.gelov.sync.db.SelectPending
import com.gelov.sync.db.SyncDatabase
import java.time.Instant

class SqlDelightOutbox(
    private val db: SyncDatabase
) : LocalOutbox {

    private val q = db.syncQueries

    override fun enqueue(change: Change) {
        q.insertOutbox(
            entity = change.entity,
            recordId = change.id,
            op = change.op.name,
            payloadJson = change.payloadJson,
            clientUpdatedAt = change.clientUpdatedAt.toString(),
            createdAt = Instant.now().toString()
        )
    }

    override fun peekBatch(limit: Int): List<OutboxItem> =
        q.selectPending(limit.toLong()).executeAsList().map { row: SelectPending ->
            OutboxItem(
                outboxId = row.outboxId,
                change = Change(
                    entity = row.entity,
                    id = row.recordId,
                    op = Op.valueOf(row.op),
                    clientUpdatedAt = Instant.parse(row.clientUpdatedAt),
                    payloadJson = row.payloadJson
                )
            )
        }

    override fun markAcked(outboxIds: List<Long>) {
        if (outboxIds.isEmpty()) return
        q.markAcked(outboxIds)
    }

    override fun markFailed(outboxId: Long, error: String) {
        q.markFailed(lastError = error, outboxId = outboxId)
    }

    override fun hasPending(): Boolean =
        q.hasPending().executeAsOne()
}

class SqlDelightCursorStore(
    private val db: SyncDatabase
) : CursorStore {

    private val q = db.syncQueries

    init {
        q.initCursorRow()
    }

    override fun getCursor(): Long = q.getCursor().executeAsOne()

    override fun setCursor(cursor: Long) {
        q.setCursor(cursor)
    }
}
