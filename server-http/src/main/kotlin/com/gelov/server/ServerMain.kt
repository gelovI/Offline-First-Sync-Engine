package com.gelov.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gelov.server.store.ServerStoreDatabase
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.util.UUID
import com.gelov.sync.core.api.SyncPushRequest
import com.gelov.sync.core.api.SyncPushResponse
import com.gelov.sync.core.api.SyncPullResponse
import com.gelov.sync.core.api.SeedNoteRequest
import com.gelov.sync.core.api.WireChange
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode


fun main() {
    val driver = JdbcSqliteDriver("jdbc:sqlite:server_store.db")
    ServerStoreDatabase.Schema.create(driver)

    val storeDb = ServerStoreDatabase(driver)
    val store = PersistentServerStore(storeDb)

    val q = storeDb.server_storeQueries

    val serverId = q.getServerKv("serverId")
        .executeAsOneOrNull()
        ?: UUID.randomUUID().toString().also { newId ->
            q.putServerKv("serverId", newId)
        }

    println("SERVER_ID (persistent): $serverId")

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
        }

        routing {
            get("/health") {
                call.respondText("OK")
            }

            post("/sync/push") {
                val raw = call.receiveText()
                val req = Json.decodeFromString<SyncPushRequest>(raw)

                val result = store.push(
                    entity = req.entity,
                    changes = req.changes,
                    clientId = req.clientId
                )

                val resp = SyncPushResponse(
                    acceptedChangeIds = result.acceptedChangeIds,
                    serverCursor = result.serverCursor,
                    serverTime = result.serverTime,
                    serverId = serverId
                )

                call.respond(resp)
            }

            get("/sync/pull") {
                val entity = call.request.queryParameters["entity"] ?: "note"
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val excludeClientId = call.request.queryParameters["excludeClientId"]

                val result = store.pull(entity, cursor, limit, excludeClientId)
                val resp = SyncPullResponse(
                    changes = result.changes,
                    serverTime = result.serverTime,
                    nextCursor = result.nextCursor,
                    serverId = serverId
                )

                call.respondText(
                    text = Json.encodeToString(resp),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }

            post("/debug/seed-note") {
                val req = call.receive<SeedNoteRequest>()
                val id = req.id ?: UUID.randomUUID().toString()

                store.push(
                    entity = "note",
                    changes = listOf(
                        WireChange(
                            entity = "note",
                            id = id,
                            op = "UPSERT",
                            clientUpdatedAt = java.time.Instant.now().toString(),
                            payloadJson = """{"title":"${req.title}","text":"${req.text}"}"""
                        )
                    ),
                    clientId = "SERVER_SEED"
                )

                call.respondText("OK")
            }
        }
    }.start(wait = true)
}
