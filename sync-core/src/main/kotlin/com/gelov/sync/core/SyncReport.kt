package com.gelov.sync.core

data class SyncReport(
    val entity: String,
    val cursorBefore: Long,
    val pushed: Int,
    val acked: Int,
    val pulled: Int,
    val applied: Int,
    val ignored: Int,
    val cursorAfter: Long
)
