package com.gelov.demo.notes

import com.gelov.sync.core.model.Change
import com.gelov.sync.core.model.Op
import java.time.Instant

class ServerSimulator(private val db: NotesDatabase) {

    private val q = db.serverQueries

    data class PushAck(
        val acceptedChangeIds: List<String>
    )
    data class PullAck(val changes: List<Change>, val nextCursor: Long, val serverTime: Instant)

    fun push(entity: String, changes: List<Change>): PushAck {

        changes.forEach { c ->
            q.insertServerChange(
                entity = entity,
                record_id = c.id,
                op = c.op.name,
                payload_json = c.payloadJson,
                updated_at = c.clientUpdatedAt.toString(),
                originClientId = c.originClientId,
                changeId = c.changeId
            )
            // insert should be INSERT OR IGNORE on change_id unique
        }

        val newCursor = q.selectMaxServerChangeId(entity).executeAsOne()
        q.setServerCursor(entity = entity, cursor = newCursor)

        return PushAck(
            acceptedChangeIds = changes.mapNotNull { it.changeId }
        )
    }

    fun pull(entity: String, afterCursor: Long, limit: Long = 100): PullAck {
        val rows = q.pullServerChanges(entity, afterCursor, limit).executeAsList()

        val changes = rows.map { r ->
            Change(
                entity = r.entity,
                id = r.record_id,
                op = Op.valueOf(r.op),
                clientUpdatedAt = Instant.parse(r.updated_at),
                payloadJson = r.payload_json,
                originClientId = r.originClientId,
                changeId = r.changeId
            )
        }

        val next = if (rows.isEmpty()) afterCursor else rows.last().id
        return PullAck(changes = changes, nextCursor = next, serverTime = Instant.now())
    }
}