package com.gelov.sync.core

import com.gelov.sync.core.model.Change

interface RemoteSync {
    fun push(entity: String, changes: List<Change>): RemotePushAck
    fun pull(entity: String, afterCursor: Long, limit: Int = 100): RemotePull
}

data class RemotePushAck(val acceptedChangeIds: List<String>)
data class RemotePull(val changes: List<Change>, val nextCursor: Long)

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

        val pushAck = remote.push(entity, toPush)

        val ackedChangeIds = pending
            .map { it.change.changeId }
            .filter { it in pushAck.acceptedChangeIds }

        outbox.markAcked(ackedChangeIds)

        val pulled = remote.pull(entity, afterCursor = cursorBefore, limit = pullLimit)

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
            pushed = toPush.size,
            acked = pushAck.acceptedChangeIds.size,
            pulled = pulled.changes.size,
            applied = applied,
            ignored = ignored,
            cursorAfter = cursorAfter
        )
    }
}

enum class ApplyResult { APPLIED, IGNORED }
