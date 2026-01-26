package com.gelov.demo.notes

import com.gelov.sync.core.RemotePull
import com.gelov.sync.core.RemotePushAck
import com.gelov.sync.core.RemoteSync
import com.gelov.sync.core.model.Change
import java.time.Instant

class DemoRemoteSync(private val server: ServerSimulator) : RemoteSync {
    override fun push(entity: String, changes: List<Change>): RemotePushAck {
        val now = Instant.now().toString()
        val serverId = "DEMO_SERVER" // oder UUID.randomUUID().toString() wenn du willst

        return RemotePushAck(
            acceptedChangeIds = changes.mapNotNull { it.changeId },
            serverCursor = 0L,
            serverTime = now,
            serverId = serverId
        )
    }

    override fun pull(entity: String, afterCursor: Long, limit: Int): RemotePull {
        val res = server.pull(entity, afterCursor, limit.toLong())
        return RemotePull(changes = res.changes, nextCursor = res.nextCursor)
    }
}
