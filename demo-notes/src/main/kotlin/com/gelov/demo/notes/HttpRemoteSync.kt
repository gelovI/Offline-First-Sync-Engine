package com.gelov.demo.notes

import com.gelov.sync.core.RemotePull
import com.gelov.sync.core.RemotePushAck
import com.gelov.sync.core.RemoteSync
import com.gelov.sync.core.api.SyncPullResponse
import com.gelov.sync.core.api.SyncPushRequest
import com.gelov.sync.core.api.SyncPushResponse
import com.gelov.sync.core.api.WireChange
import com.gelov.sync.core.model.Change
import com.gelov.sync.core.model.Op
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.time.Instant

class HttpRemoteSync(
    private val baseUrl: String = "http://localhost:8080",
    private val clientId: String,
    private val serverIdStore: SqlDelightServerIdStore,
    private val onServerReset: () -> Unit
) : RemoteSync {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
        }
    }

    override fun push(entity: String, changes: List<Change>): RemotePushAck {
        val req = SyncPushRequest(
            entity = entity,
            changes = changes.map { it.toWire(entity) },
            clientId = clientId
        )

        val resp: SyncPushResponse = runBlockingIO {
            http.post("$baseUrl/sync/push") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

        System.err.println(
            "PUSH: (ignored serverId) resp.serverId=${resp.serverId}"
        )

        return RemotePushAck(
            acceptedChangeIds = resp.acceptedChangeIds,
            serverCursor = resp.serverCursor,
            serverTime = resp.serverTime,
            serverId = resp.serverId
        )
    }


    override fun pull(entity: String, afterCursor: Long, limit: Int): RemotePull {

        fun doPull(cursor: Long): SyncPullResponse = runBlockingIO {
            http.get("$baseUrl/sync/pull") {
                url {
                    parameters.append("entity", entity)
                    parameters.append("cursor", cursor.toString())
                    parameters.append("limit", limit.toString())
                    parameters.append("excludeClientId", clientId)
                }
            }.body()
        }

        var cursorUsed = afterCursor
        var resp = doPull(cursorUsed)

        val stored = serverIdStore.get()
        println("PULL: storedServerId=$stored resp.serverId=${resp.serverId} cursorUsed=$cursorUsed resp.nextCursor=${resp.nextCursor}")

        if (stored != null && stored != resp.serverId) {
            println(">>> SERVER RESET detected on PULL: $stored -> ${resp.serverId}. Re-pulling from cursor=0")
            onServerReset()
            serverIdStore.set(resp.serverId)
            cursorUsed = 0L
            resp = doPull(cursorUsed)
            println("PULL(RETRY): resp.serverId=${resp.serverId} cursorUsed=$cursorUsed resp.nextCursor=${resp.nextCursor}")
        } else {
            serverIdStore.set(resp.serverId)
        }

        val domainChanges =
            if (resp.changes.isEmpty()) emptyList()
            else resp.changes.map { it.toDomain() }

        return RemotePull(
            changes = domainChanges,
            nextCursor = resp.nextCursor
        )
    }

    private fun Change.toWire(entity: String): WireChange =
        WireChange(
            entity = entity,
            id = this.id,
            op = this.op.name,
            clientUpdatedAt = this.clientUpdatedAt.toString(),
            payloadJson = this.payloadJson,
            originClientId = this.originClientId,
            changeId = changeId
        )

    private fun WireChange.toDomain(): Change =
        Change(
            entity = entity,
            id = id,
            op = Op.valueOf(op),
            clientUpdatedAt = Instant.parse(clientUpdatedAt),
            payloadJson = payloadJson,
            originClientId = originClientId ?: "",
            changeId = requireNotNull(changeId) {
                "WireChange.changeId missing – protocol violation"
            }
        )

    private fun <T> runBlockingIO(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
