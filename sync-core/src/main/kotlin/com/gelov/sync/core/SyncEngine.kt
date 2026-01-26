package com.gelov.sync.core

import com.gelov.sync.core.model.Change

interface RemoteSync {
    fun push(entity: String, changes: List<Change>): RemotePushAck
    fun pull(entity: String, afterCursor: Long, limit: Int = 100): RemotePull
}

data class RemotePushAck(
    val acceptedChangeIds: List<String>,
    val serverCursor: Long,
    val serverTime: String,
    val serverId: String
)

data class RemotePull(
    val changes: List<Change>,
    val nextCursor: Long
)

class SyncEngine(
    private val outbox: LocalOutbox,
    private val cursorStore: CursorStore,
    private val remote: RemoteSync,
    private val applier: (Change) -> ApplyResult
) {
    fun syncOnce(entity: String, pushLimit: Int = 50, pullLimit: Int = 100): SyncReport {
        val cursorBefore = cursorStore.getCursor()

        val pending = outbox.peekBatch(pushLimit)
        val toPush = pending.map { it.change }

        var pushed = 0
        var acked = 0
        var ackedOutboxIds: List<Long> = emptyList()


        if (toPush.isNotEmpty()) {
            try {
                val pushAck = remote.push(entity, toPush)

                val accepted = pushAck.acceptedChangeIds.toSet()
                val ackedOutboxIds = pending
                    .filter { it.change.changeId in accepted }
                    .map { it.outboxId }

                outbox.markAcked(ackedOutboxIds)

                // server hat etwas nicht accepted
                pending
                    .filter { it.change.changeId !in accepted }
                    .forEach { outbox.markFailed(it.outboxId, "Not accepted by server") }

                pushed = toPush.size
                acked = ackedOutboxIds.size

            } catch (t: Throwable) {
                val msg = "${t::class.simpleName}: ${t.message ?: "push failed"}"
                pending.forEach { outbox.markFailed(it.outboxId, msg) }

                // Kein Pull wenn Push kaputt
                return SyncReport(
                    entity = entity,
                    cursorBefore = cursorBefore,
                    pushed = toPush.size,
                    acked = 0,
                    pulled = 0,
                    applied = 0,
                    ignored = 0,
                    cursorAfter = cursorBefore
                )
            }
        }

        val pulled = try {
            remote.pull(
                entity = entity,
                afterCursor = cursorBefore,
                limit = pullLimit
            )
        } catch (t: Throwable) {
            return SyncReport(
                entity = entity,
                cursorBefore = cursorBefore,
                pushed = toPush.size,
                acked = ackedOutboxIds.size,
                pulled = 0,
                applied = 0,
                ignored = 0,
                cursorAfter = cursorBefore
            )
        }

        var applied = 0
        var ignored = 0
        pulled.changes.forEach { c ->
            when (applier(c)) {
                ApplyResult.APPLIED -> applied++
                ApplyResult.IGNORED -> ignored++
            }
        }

        cursorStore.setCursor(pulled.nextCursor)
        val cursorAfter = cursorStore.getCursor()

        return SyncReport(
            entity = entity,
            cursorBefore = cursorBefore,
            pushed = pushed,
            acked = acked,
            pulled = pulled.changes.size,
            applied = applied,
            ignored = ignored,
            cursorAfter = cursorAfter
        )
    }
}

enum class ApplyResult { APPLIED, IGNORED }
