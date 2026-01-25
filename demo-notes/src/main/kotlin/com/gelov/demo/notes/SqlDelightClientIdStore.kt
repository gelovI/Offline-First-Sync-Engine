package com.gelov.demo.notes

import com.gelov.sync.db.SyncDatabase
import java.util.UUID

class SqlDelightClientIdStore(db: SyncDatabase) {
    private val q = db.syncQueries
    private val KEY = "clientId"

    fun getOrCreate(): String {
        val existing = q.getKv(KEY).executeAsOneOrNull()?.v
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        q.upsertKv(KEY, id)
        return id
    }
}
