package com.gelov.server

import com.gelov.server.store.ServerStoreDatabase
import com.gelov.sync.core.api.WireChange
import java.time.Instant

class PersistentServerStore(
    private val db: ServerStoreDatabase
) {
    private val q = db.server_storeQueries

    data class PushResult(
        val acceptedChangeIds: List<String>,
        val serverCursor: Long,
        val serverTime: String
    )

    data class PullResult(
        val changes: List<WireChange>,
        val nextCursor: Long,
        val serverTime: String
    )

    fun push(entity: String, changes: List<WireChange>, clientId: String): PushResult {
        val acceptedChangeIds = mutableListOf<String>()

        db.transaction {
            for (c in changes) {
                val changeId = c.changeId
                    ?: error("Server requires changeId for idempotent push")

                // immer accepted → Client darf ACKen
                acceptedChangeIds += changeId

                // idempotenter Insert (INSERT OR IGNORE muss in SQL stehen!)
                q.insertChange(
                    entity = entity,
                    id = c.id,
                    op = c.op,
                    clientUpdatedAt = c.clientUpdatedAt,
                    payloadJson = c.payloadJson,
                    changeId = changeId,
                    originClientId = clientId
                )
            }
        }

        val serverCursor = q.maxCursor().executeAsOne()
        val serverTime = Instant.now().toString()
        return PushResult(
            acceptedChangeIds = acceptedChangeIds,
            serverCursor = serverCursor,
            serverTime = serverTime
        )
    }

    fun pull(entity: String, afterCursor: Long, limit: Int, excludeClientId: String?): PullResult {
        val rows: List<Row> =
            if (excludeClientId.isNullOrBlank()) {
                q.pullAfter(entity, afterCursor, limit.toLong())
                    .executeAsList()
                    .map { r ->
                        Row(
                            cursorId = r.cursorId,
                            entity = r.entity,
                            id = r.id,
                            op = r.op,
                            clientUpdatedAt = r.clientUpdatedAt,
                            payloadJson = r.payloadJson,
                            changeId = r.changeId,
                            originClientId = r.originClientId
                        )
                    }
            } else {
                q.pullAfterExcludingClient(entity, afterCursor, excludeClientId, limit.toLong())
                    .executeAsList()
                    .map { r ->
                        Row(
                            cursorId = r.cursorId,
                            entity = r.entity,
                            id = r.id,
                            op = r.op,
                            clientUpdatedAt = r.clientUpdatedAt,
                            payloadJson = r.payloadJson,
                            changeId = r.changeId,
                            originClientId = r.originClientId
                        )
                    }
            }

        val changes = rows.map { r ->
            WireChange(
                entity = r.entity,
                id = r.id,
                op = r.op,
                clientUpdatedAt = r.clientUpdatedAt,
                payloadJson = r.payloadJson,
                originClientId = r.originClientId,
                changeId = r.changeId
            )
        }

        val nextCursor = rows.lastOrNull()?.cursorId ?: afterCursor
        val serverTime = Instant.now().toString()
        return PullResult(changes, nextCursor, serverTime)
    }

    private data class Row(
        val cursorId: Long,
        val entity: String,
        val id: String,
        val op: String,
        val clientUpdatedAt: String,
        val payloadJson: String?,
        val changeId: String,
        val originClientId: String
    )
}