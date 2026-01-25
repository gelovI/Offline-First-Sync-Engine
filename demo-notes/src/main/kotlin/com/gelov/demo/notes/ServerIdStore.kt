package com.gelov.demo.notes

class ServerIdStore {
    @Volatile private var serverId: String? = null
    fun get(): String? = serverId
    fun set(id: String) { serverId = id }
}