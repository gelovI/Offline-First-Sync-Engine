package com.gelov.demo.notes

import com.gelov.sync.core.RemotePull
import com.gelov.sync.core.RemotePushAck
import com.gelov.sync.core.RemoteSync
import com.gelov.sync.core.model.Change

class DemoRemoteSync(private val server: ServerSimulator) : RemoteSync {
    override fun push(entity: String, changes: List<Change>): RemotePushAck {
        val ack = server.push(entity, changes)
        return RemotePushAck(acceptedChangeIds = ack.acceptedChangeIds)
    }

    override fun pull(entity: String, afterCursor: Long, limit: Int): RemotePull {
        val res = server.pull(entity, afterCursor, limit.toLong())
        return RemotePull(changes = res.changes, nextCursor = res.nextCursor)
    }
}
