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

fun nowForWrite(): Instant {
    val fixedProp = System.getProperty("fixedAt")?.trim()

    return if (!fixedProp.isNullOrEmpty()) {
        Instant.parse(fixedProp)
    } else {
        Instant.now()
    }
}

fun main() {
    System.err.println("### STDERR: MAIN START ###")
    println("### STDOUT: MAIN START ###")
    Thread.sleep(400)

    val clientName = System.getProperty("client", "A")
    println("sys.client=" + System.getProperty("client"))
    println("sys.flow=" + System.getProperty("flow"))

    val flow = System.getProperty("flow", "seedDelete")
    val argId = System.getProperty("id") // optional
    val argTitle = System.getProperty("title")
    val argText = System.getProperty("text")

    println("sys.client=" + System.getProperty("client"))
    println("sys.flow=" + System.getProperty("flow"))
    println("Flow: $flow")
    println("sys.fixedAt=" + System.getProperty("fixedAt"))

    val dbFile = File("offline_first_sync_demo_${clientName}.db")
    val isNewDb = !dbFile.exists() || dbFile.length() == 0L

    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.path}")
    if (isNewDb) {
        SyncDatabase.Schema.create(driver)
        NotesDatabase.Schema.create(driver)
    }

    val syncDb = SyncDatabase(driver)
    val notesDb = NotesDatabase(driver)

    val outbox = SqlDelightOutbox(syncDb)
    val cursorStore = SqlDelightCursorStore(syncDb)
    val serverIdStore = SqlDelightServerIdStore(syncDb)
    println("ServerId at startup (from DB): ${serverIdStore.get()}")

    val clientIdStore = SqlDelightClientIdStore(syncDb)
    val clientId = clientIdStore.getOrCreate()
    println("ClientId at startup (from DB): $clientId")

    val remote = HttpRemoteSync(
        baseUrl = "http://localhost:8080",
        clientId = clientId,
        serverIdStore = serverIdStore,
        onServerReset = {
            println(">>> SERVER RESET → cursor = 0")
            cursorStore.setCursor(0L)
        }
    )

    System.err.println("Cursor at start: ${cursorStore.getCursor()}")

    fun pickActiveIdOrNull(): String? =
        notesDb.notesQueries.selectAllActiveNotes().executeAsList().firstOrNull()?.id

    fun seedOne(): String {
        val noteId = UUID.randomUUID().toString()
        val now = nowForWrite()
        val title = argTitle ?: "First note"
        val text = argText ?: "Hello Offline-First!"

        notesDb.notesQueries.upsertNote(
            id = noteId,
            title = title,
            text = text,
            updatedAt = now.toString(),
            updatedByClientId = clientId,
            deletedAt = null
        )

        val payloadJson = """{"title":${title.jsonEscape()},"text":${text.jsonEscape()}}"""
        outbox.enqueue(
            Change(
                entity = "note",
                id = noteId,
                op = Op.UPSERT,
                clientUpdatedAt = now,
                payloadJson = payloadJson,
                originClientId = clientId,
                changeId = UUID.randomUUID().toString()
            )
        )
        System.err.println("\nSeeded note id=$noteId + queued UPSERT.")
        return noteId
    }

    fun updateNote(targetId: String) {
        val now = nowForWrite()
        val current = notesDb.notesQueries.selectNoteById(targetId).executeAsOneOrNull()

        val newTitle = argTitle ?: ((current?.title ?: "Note") + " (edited)")
        val newText = argText ?: "from $clientName @ $now"
        notesDb.notesQueries.upsertNote(
            id = targetId,
            title = newTitle,
            text = newText,
            updatedAt = now.toString(),
            updatedByClientId = clientId,
            deletedAt = null
        )

        val payloadJson = """{"title":${newTitle.jsonEscape()},"text":${newText.jsonEscape()}}"""
        outbox.enqueue(
            Change(
                entity = "note",
                id = targetId,
                op = Op.UPSERT,
                clientUpdatedAt = now,
                payloadJson = payloadJson,
                originClientId = clientId,
                changeId = UUID.randomUUID().toString()
            )
        )
        System.err.println("Updated locally + queued UPSERT for id=$targetId title=$newTitle")
    }

    fun deleteNote(targetId: String) {
        val now = nowForWrite()
        val local = notesDb.notesQueries.selectNoteById(targetId).executeAsOneOrNull()

        notesDb.notesQueries.upsertNote(
            id = targetId,
            title = local?.title ?: "",
            text = local?.text ?: "",
            updatedAt = now.toString(),
            updatedByClientId = clientId,
            deletedAt = now.toString()
        )

        outbox.enqueue(
            Change(
                entity = "note",
                id = targetId,
                op = Op.DELETE,
                clientUpdatedAt = now,
                payloadJson = null,
                originClientId = clientId,
                changeId = UUID.randomUUID().toString()
            )
        )

        System.err.println("Deleted locally + queued DELETE for id=$targetId")
    }

    when (flow) {
        "seedDelete" -> {
            val id = pickActiveIdOrNull() ?: seedOne()
            deleteNote(id)
        }
        "seed" -> {
            val existing = notesDb.notesQueries.selectAllActiveNotes().executeAsList()
            if (existing.isEmpty()) seedOne() else System.err.println("\nDB already has ${existing.size} note(s). No new seed.")
        }
        "update" -> {
            val id = argId ?: pickActiveIdOrNull() ?: seedOne()
            updateNote(id)
        }
        "delete" -> {
            val id = argId ?: pickActiveIdOrNull()
            if (id != null) deleteNote(id) else System.err.println("No active note to delete.")
        }
        "sync" -> {
            // nothing; just sync
        }
        else -> {
            System.err.println("Unknown flow=$flow; defaulting to seedDelete")
            val id = pickActiveIdOrNull() ?: seedOne()
            deleteNote(id)
        }
    }

    // Show pending outbox
    System.err.println("\nPending outbox:")
    val pending = outbox.peekBatch(limit = 50)
    pending.forEach { item ->
        System.err.println("- outboxId=${item.outboxId} entity=${item.change.entity} id=${item.change.id} op=${item.change.op} at=${item.change.clientUpdatedAt}")
        System.err.println("  payload=${item.change.payloadJson}")
    }

    val applier: (Change) -> ApplyResult = { c ->
        val local = notesDb.notesQueries.selectNoteById(c.id).executeAsOneOrNull()
        val localUpdatedAt = local?.updatedAt?.let { Instant.parse(it) }

        val incomingAt = c.clientUpdatedAt
        val shouldApply = when {
            localUpdatedAt == null -> true
            incomingAt.isAfter(localUpdatedAt) -> true
            incomingAt.isBefore(localUpdatedAt) -> false
            else -> { // equal timestamp → tie-break
                val localBy = local?.updatedByClientId.orEmpty()
                val incomingBy = c.originClientId
                incomingBy > localBy
            }
        }

        if (!shouldApply) {
            System.err.println(
                "IGNORED (LWW/TIE): id=${c.id} local=$localUpdatedAt incoming=$incomingAt " +
                        "localBy=${local?.updatedByClientId} incomingBy=${c.originClientId}"
            )
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
                        updatedByClientId = c.originClientId,
                        deletedAt = null
                    )
                }
                Op.DELETE -> {
                    // Tombstone
                    notesDb.notesQueries.upsertNote(
                        id = c.id,
                        title = local?.title ?: "",
                        text = local?.text ?: "",
                        updatedAt = incomingAt.toString(),
                        updatedByClientId = c.originClientId,
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

    val doSync = System.getProperty("doSync", "true").toBoolean()

    if (!doSync) {
        System.err.println("Skipping syncOnce (doSync=false)")
        System.err.println("Pending outbox remains queued.")
        System.err.println("\nDone.")
        return
    }

    val report = engine.syncOnce(entity = "note", pushLimit = 50, pullLimit = 100)
    System.err.println("SYNC REPORT: $report")

    println("ServerId after sync (saved): ${serverIdStore.get()}")


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
