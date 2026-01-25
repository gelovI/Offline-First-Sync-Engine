package com.gelov.demo.notes

import com.gelov.sync.db.SyncDatabase

class SqlDelightServerIdStore(
    db: SyncDatabase
) {
    private val q = db.syncQueries
    private val KEY = "serverId"

    fun get(): String? =
        q.getKv(KEY).executeAsOneOrNull()?.v

    fun set(id: String) {
        q.upsertKv(KEY, id)
    }

    fun clear() {
        q.deleteKv(KEY)
    }
}