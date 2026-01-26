# Offline-First Sync Engine (Kotlin)

A lightweight, **offline-first synchronization engine** built with Kotlin and SQLDelight.
Designed for **mobile & desktop apps** that must work reliably without constant connectivity.

This project is not a demo toy – it focuses on **real-world sync problems**:
idempotency, retries, conflict handling, and server resets.

---

## ✨ Features

- **Offline-first by design**
  - All local changes are stored in an Outbox
  - Works fully offline, syncs when connectivity returns

- **Idempotent sync (ChangeId)**
  - Every change has a globally unique `changeId`
  - Server uses append-only change log with de-duplication

- **Reliable retries with backoff**
  - FAILED → retry after `nextAttemptAt`
  - Automatic retry scheduling
  - DEAD state after max attempts

- **Cursor-based pull**
  - Efficient incremental sync
  - Server reset detection via `serverId`

- **Conflict handling**
  - Last-Write-Wins (timestamp + originClientId tie-break)
  - Deterministic & predictable behavior

---

## 🧠 Architecture Overview
Client
├─ Local DB (SQLDelight)
│ ├─ domain tables
│ ├─ outbox (append-only intent log)
│ └─ cursor_state
│
├─ SyncEngine
│ ├─ push (outbox → server)
│ ├─ pull (server → local)
│ └─ retry / backoff
│
└─ RemoteSync (HTTP)

Server
├─ append-only change_log
├─ idempotent inserts (changeId)
├─ cursor-based pull
└─ serverId for reset detection


---

## 🔁 Sync Flow

1. **Local change**
   - Saved locally
   - Enqueued in `outbox` with `changeId`

2. **Push**
   - Client sends pending changes
   - Server ACKs accepted `changeId`s
   - Client marks only ACKed items as done

3. **Pull**
   - Client pulls changes after last cursor
   - Applies changes deterministically
   - Cursor is advanced

4. **Retry**
   - Network failure → FAILED
   - Retry after `nextAttemptAt`
   - Eventually marked DEAD if max retries exceeded

---

## 🧪 Demo

The `demo-notes` module simulates:
- multiple clients (A / B)
- offline updates
- server downtime
- retries & recovery
- conflict resolution

Run example:
```bash
./gradlew :demo-notes:run -Dclient=A -Dflow=update
./gradlew :demo-notes:run -Dclient=A -Dflow=sync
./gradlew :demo-notes:run -Dclient=B -Dflow=sync

🛠 Tech Stack

Kotlin

SQLDelight

Ktor (client & server)

SQLite

Gradle (multi-module)

🎯 Motivation

Most sync examples stop at “it works on localhost”.
This project explores what actually breaks in production:
network errors, retries, duplicates, restarts, and conflicts.

📌 Status

Actively developed
Next steps:

batching & compression

pluggable conflict strategies

metrics & observability
