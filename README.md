# OPS – Offline-First Task & Project Manager

OPS ist eine Offline-First Android App zur Verwaltung von Tasks und Projekten.
Der Fokus liegt auf robuster Synchronisation, klaren State-Modellen
und einer sauberen Trennung von UI, Domain und Datenebene.

---

## Features

* Offline-First Architektur (voll nutzbar ohne Netzwerk)
* SQLDelight + Tombstones (`deletedAt`) für Sync-sichere Datenhaltung
* Outbox-Pattern (Push) + Cursor-basierter Pull-Sync
* Projekte mit Farbcodierung & Task-Zuordnung
* Jetpack Compose UI mit klaren ViewModel-States
* Emulator ↔ reales Gerät synchronisierbar

---

## Architektur

```mermaid
graph TD
  UI[Compose UI<br/>TaskList / ProjectsScreen]
  VM[ViewModels<br/>TaskListVM / ProjectsVM]
  UC[UseCases<br/>CreateTask / CreateProject]
  REPO[Repositories<br/>TaskRepository / ProjectRepository]
  DB[(SQLDelight<br/>Local DB)]
  OUTBOX[Outbox / Tombstones]
  SYNC[SyncOnce<br/>Push / Pull]
  API[(Remote API)]

  UI --> VM
  VM --> UC
  UC --> REPO
  REPO --> DB
  DB --> OUTBOX
  OUTBOX --> SYNC
  SYNC --> API
  API --> SYNC
  SYNC --> DB
```

## Offline-First Synchronisation (Outbox + LWW)
```mermaid
sequenceDiagram
  participant UI
  participant VM
  participant DB
  participant OUTBOX
  participant SYNC
  participant API

  UI->>VM: User Action
  VM->>DB: Write (updatedAt, deletedAt)
  DB->>OUTBOX: Enqueue Change
  Note right of DB: App bleibt voll funktionsfähig offline

  SYNC->>OUTBOX: Read pending changes
  SYNC->>API: Push changes
  API-->>SYNC: ACK / Remote changes
  SYNC->>DB: Apply changes (LWW)
```

**Offline-First Prinzip**

- Alle Writes gehen zuerst in die lokale DB
- Änderungen werden über eine Outbox persistiert
- Sync ist retry-safe und idempotent
- Konflikte werden per Last-Write-Wins (`updatedAt`) gelöst
- UI blockiert nie auf Netzwerk

**UI**

* Jetpack Compose (Material 3)
* State-Hoisting über ViewModels
* Lifecycle-aware StateFlows

**State & Domain**

* ViewModel + StateFlow
* Klare UseCases (z. B. `CreateProject`, `SyncOnce`)
* Keine Logik in der UI

**Persistence**

* SQLDelight (SQLite)
* UUID-basierte IDs
* `updatedAt` / `deletedAt` für konfliktfreie Synchronisation

**Sync**

* Push: Outbox-Tabelle (retry-safe)
* Pull: Cursor-basierte API
* Konfliktstrategie: Last-Write-Wins

---

## Tech Stack

* Kotlin
* Jetpack Compose
* SQLDelight
* Ktor (Client & Server)
* Coroutines / Flow

---

## Status

Aktiv in Entwicklung.
Der aktuelle Fokus liegt auf Stabilität, transparenter Synchronisation
und einer sauberen Projekt-Verwaltung.
