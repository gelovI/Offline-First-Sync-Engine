package com.gelov.sync.sqldelight

import com.gelov.sync.core.CursorStore
import com.gelov.sync.core.LocalOutbox
import com.gelov.sync.core.OutboxItem
import com.gelov.sync.core.model.Change
import com.gelov.sync.core.model.Op
import com.gelov.sync.db.SyncDatabase
import java.time.Instant

class SqlDelightOutbox(
    private val db: SyncDatabase
) : LocalOutbox {

    private val q = db.syncQueries

    override fun enqueue(change: Change) {
        db.transaction {
            val existing = q.selectPendingForRecord(
                entity = change.entity,
                recordId = change.id
            ).executeAsOneOrNull()

            if (existing == null) {
                // kein Pending → neu anlegen
                q.insertPendingOutbox(
                    entity = change.entity,
                    recordId = change.id,
                    op = change.op.name,
                    payloadJson = change.payloadJson,
                    clientUpdatedAt = change.clientUpdatedAt.toString(),
                    originClientId = change.originClientId,
                    createdAt = Instant.now().toString(),
                    changeId = change.changeId
                )
                return@transaction
            }

            // -------- LWW + Tie-break (identisch zum Apply) --------
            val existingAt = Instant.parse(existing.clientUpdatedAt)
            val incomingAt = change.clientUpdatedAt

            val incomingIsNewer = when {
                incomingAt.isAfter(existingAt) -> true
                incomingAt.isBefore(existingAt) -> false
                else -> change.originClientId > existing.originClientId
            }

            if (!incomingIsNewer) {
                // incoming ist älter → Pending bleibt unverändert
                return@transaction
            }

            // incoming gewinnt → UPDATE statt delete+insert
            q.updatePendingOutboxById(
                op = change.op.name,
                payloadJson = change.payloadJson,
                clientUpdatedAt = change.clientUpdatedAt.toString(),
                originClientId = change.originClientId,
                outboxId = existing.outboxId
            )
        }
    }

    override fun peekBatch(limit: Int): List<OutboxItem> {
        val now = Instant.now().toString()
        return q.selectReady(now, limit.toLong())
            .executeAsList()
            .map { row ->
                OutboxItem(
                    outboxId = row.outboxId,
                    change = Change(
                        entity = row.entity,
                        id = row.recordId,
                        op = Op.valueOf(row.op),
                        clientUpdatedAt = Instant.parse(row.clientUpdatedAt),
                        payloadJson = row.payloadJson,
                        originClientId = row.originClientId,
                        changeId = row.changeId
                    )
                )
            }
    }

    fun debugPrintOutboxAll() {
        println("Outbox (ALL):")
        q.selectAllOutbox().executeAsList().forEach { r ->
            println(
                "- outboxId=${r.outboxId} status=${r.status} attempts=${r.attemptCount} " +
                        "next=${r.nextAttemptAt} changeId=${r.changeId} err=${r.lastError}"
            )
        }
    }


    override fun markAcked(outboxIds: List<Long>) {
        if (outboxIds.isEmpty()) return
        q.markAcked(outboxIds)
    }

    override fun markFailed(outboxId: Long, error: String) {
        val maxAttempts = 5L
        val nextAttemptAt = Instant.now().plusSeconds(10).toString()

        q.markFailed(
            maxAttempts,
            error,
            nextAttemptAt,
            outboxId
        )
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
