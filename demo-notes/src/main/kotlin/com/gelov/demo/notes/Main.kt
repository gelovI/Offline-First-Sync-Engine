package com.gelov.demo.notes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gelov.sync.core.ApplyResult
import com.gelov.sync.core.SyncEngine
import com.gelov.sync.core.model.Change
import com.gelov.sync.core.model.Op
import com.gelov.sync.db.SyncDatabase
import com.gelov.sync.sqldelight.SqlDelightCursorStore
import com.gelov.sync.sqldelight.SqlDelightOutbox
import java.io.File
import java.time.Instant
import java.util.UUID

fun main() {
    System.err.println("### STDERR: MAIN START ###")
    println("### STDOUT: MAIN START ###")
    Thread.sleep(400)

    val dbFile = File("offline_first_sync_demo.db")
//    if (dbFile.exists()) dbFile.delete()

    // 1) Create/open demo DB file
    // 1) Create/open demo DB file
    val driver = JdbcSqliteDriver("jdbc:sqlite:offline_first_sync_demo.db")

    val isNewDb = !dbFile.exists() || dbFile.length() == 0L

    if (isNewDb) {
        SyncDatabase.Schema.create(driver)
        NotesDatabase.Schema.create(driver)
    } else {
        // Demo: wir überspringen migrations erstmal.
        // Wenn du später echte .sqm migrations nutzt, bauen wir das sauber aus.
    }

    val syncDb = SyncDatabase(driver)
    val notesDb = NotesDatabase(driver)

    val outbox = SqlDelightOutbox(syncDb)
    val cursorStore = SqlDelightCursorStore(syncDb)

    System.err.println("Cursor at start: ${cursorStore.getCursor()}")

    // 3) Create a note locally (simulate offline write)
    val existing = notesDb.notesQueries.selectAllNotes().executeAsList()
    if (existing.isEmpty()) {
        val noteId = UUID.randomUUID().toString()
        val now = Instant.now()

        val title = "First note"
        val text = "Hello Offline-First!"

        notesDb.notesQueries.upsertNote(
            id = noteId,
            title = title,
            text = text,
            updatedAt = now.toString(),
            deletedAt = null
        )

        val payloadJson = """{"title":${title.jsonEscape()},"text":${text.jsonEscape()}}"""
        outbox.enqueue(
            Change(
                entity = "note",
                id = noteId,
                op = Op.UPSERT,
                clientUpdatedAt = now,
                payloadJson = payloadJson
            )
        )
        System.err.println("\nSeeded first note + queued outbox.")
    } else {
        System.err.println("\nDB already has ${existing.size} note(s). No new seed.")
    }

    // 6) Show pending outbox
    System.err.println("\nPending outbox:")
    val pending = outbox.peekBatch(limit = 50)
    pending.forEach { item ->
        System.err.println("- outboxId=${item.outboxId} entity=${item.change.entity} id=${item.change.id} op=${item.change.op} at=${item.change.clientUpdatedAt}")
        System.err.println("  payload=${item.change.payloadJson}")
    }

    // ---- STEP 14: SyncEngine wiring ----
    val server = ServerSimulator(notesDb)
    val remote = DemoRemoteSync(server)

    val applier: (Change) -> ApplyResult = { c ->
        val local = notesDb.notesQueries.selectNoteById(c.id).executeAsOneOrNull()
        val localUpdatedAt = local?.updatedAt?.let { Instant.parse(it) }

        val incomingAt = c.clientUpdatedAt
        val shouldApply = localUpdatedAt == null || !incomingAt.isBefore(localUpdatedAt) // incoming >= local

        if (!shouldApply) {
            System.err.println("IGNORED (LWW): id=${c.id} local=$localUpdatedAt incoming=$incomingAt")
            ApplyResult.IGNORED
        } else {
            when (c.op) {
                Op.UPSERT -> {
                    val (t, x) = parseNotePayload(c.payloadJson)
                    notesDb.notesQueries.upsertNote(
                        id = c.id,
                        title = t,
                        text = x,
                        updatedAt = incomingAt.toString(),
                        deletedAt = null
                    )
                }
                Op.DELETE -> {
                    // Tombstone (keep existing title/text if present)
                    notesDb.notesQueries.upsertNote(
                        id = c.id,
                        title = local?.title ?: "",
                        text = local?.text ?: "",
                        updatedAt = incomingAt.toString(),
                        deletedAt = incomingAt.toString()
                    )
                }
            }
            ApplyResult.APPLIED
        }
    }

    val engine = SyncEngine(
        outbox = outbox,
        cursorStore = cursorStore,
        remote = remote,
        applier = applier
    )

    System.err.println("\n== SyncOnce ==")

    // --- DEMO: simulate a remote change happening on the server ---
    if (cursorStore.getCursor() == 1L) {
        val serverNow = Instant.now()
        server.push(
            entity = "note",
            changes = listOf(
                Change(
                    entity = "note",
                    id = "server-note-1",
                    op = Op.UPSERT,
                    clientUpdatedAt = serverNow,
                    payloadJson = """{"title":"From Server","text":"Pulled change!"}"""
                )
            )
        )
        System.err.println("Simulated remote server change: server-note-1")
    }

    val report = engine.syncOnce(entity = "note", pushLimit = 50, pullLimit = 100)
    System.err.println("SYNC REPORT: $report")

    System.err.println("\nDone.")
}

/** Produces a JSON string literal (with quotes). */
private fun String.jsonEscape(): String {
    val escaped = buildString(length + 16) {
        append('"')
        for (ch in this@jsonEscape) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
    return escaped
}

private fun parseNotePayload(json: String?): Pair<String, String> {
    if (json.isNullOrBlank()) return "" to ""

    fun find(key: String): String {
        val idx = json.indexOf("\"$key\":")
        if (idx < 0) return ""
        val firstQuote = json.indexOf('"', idx + key.length + 3)
        if (firstQuote < 0) return ""
        val start = firstQuote + 1
        val end = json.indexOf('"', start)
        if (end < 0) return ""
        return json.substring(start, end)
    }

    return find("title") to find("text")
}
