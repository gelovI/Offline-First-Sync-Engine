package com.gelov.demo.notes

import com.gelov.sync.core.model.Change
import com.gelov.sync.core.model.Op
import java.time.Instant

class ServerSimulator(private val db: NotesDatabase) {

    private val q = db.serverQueries

    data class PushAck(val accepted: Int, val serverCursor: Long, val serverTime: Instant)
    data class PullAck(val changes: List<Change>, val nextCursor: Long, val serverTime: Instant)

    fun push(entity: String, changes: List<Change>): PushAck {
        val now = Instant.now()

        changes.forEach { c ->
            q.insertServerChange(
                entity = entity,
                record_id = c.id,
                op = c.op.name,
                payload_json = c.payloadJson,
                updated_at = c.clientUpdatedAt.toString()
            )
        }

        val newCursor = q.selectMaxServerChangeId(entity).executeAsOne()
        q.setServerCursor(entity = entity, cursor = newCursor)

        return PushAck(accepted = changes.size, serverCursor = newCursor, serverTime = now)
    }

    fun pull(entity: String, afterCursor: Long, limit: Long = 100): PullAck {
        val rows = q.pullServerChanges(entity, afterCursor, limit).executeAsList()
        // rows -> Change
        val changes = rows.map { r ->
            Change(
                entity = r.entity,
                id = r.record_id,
                op = Op.valueOf(r.op),
                clientUpdatedAt = Instant.parse(r.updated_at), // für Demo ok
                payloadJson = r.payload_json
            )
        }

        val next = if (rows.isEmpty()) afterCursor else rows.last().id
        return PullAck(changes = changes, nextCursor = next, serverTime = Instant.now())
    }
}