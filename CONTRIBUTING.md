# Methodology Summary

## Philosophy

This project follows specification-driven development.

The primary goal is to reduce ambiguity for both humans and AI systems.

Implementation is considered a downstream artifact derived from specifications.

Preferred order:

```text
Use Cases
    ↓
Actors
    ↓
Capabilities
    ↓
Contracts
    ↓
Stages
    ↓
State Machine
    ↓
Workflows
    ↓
Invariants
    ↓
Tests
    ↓
Implementation
```

---

# Core Concepts

## Use Case

Use cases describe system behavior from the user's perspective.

Use cases answer:

> What problem does the system solve?

Use cases are the starting point of all design work.

---

## Actor

An actor performs work within the system.

Examples:

* User
* Watcher
* MetadataProvider
* Orchestrator

Actors own capabilities.

---

## Capability

A capability is a business-level operation.

Examples:

* scan-directory
* extract-metadata
* identify-plaud-record
* write-record

Capabilities answer:

> What does the system do?

Capabilities should remain implementation independent.

---

## Contract

Contracts define the data exchanged between capabilities.

Contracts are implemented using Malli schemas.

Example:

```clojure
[:map
 [:asset-id uuid?]
 [:canonical-path string?]]
```

Contracts define:

* inputs
* outputs
* validation rules

Capabilities should not be implemented before contracts exist.

---

## Emergent Domain Model

The domain model is not designed upfront.

It emerges from recurring concepts found in contracts.

Example:

Repeated schemas may eventually reveal concepts such as:

* Asset
* Metadata
* PlaudRecord
* Document

The domain model should evolve naturally from implementation experience and use cases.

Malli schemas remain the primary source of truth.

---

## Stage

A stage represents a system state.

Examples:

* init
* monitor
* metadata_enrichment
* record_preparation
* record_staging

Stages define ownership and valid transitions.

---

## State Machine

The state machine defines all valid stage transitions.

Example:

```text
init
  -> monitor
  -> metadata_enrichment
  -> record_preparation
  -> record_staging
```

The state machine is authoritative.

---

## Workflow

A workflow is a valid path through the state machine.

Example:

```text
init
 -> monitor
 -> metadata_enrichment
 -> record_preparation
 -> record_staging
```

Workflows are used for orchestration and test generation.

---

## Invariant

An invariant is a property that must always hold.

Examples:

* Asset must never be lost.
* Metadata never exists without an asset.
* Asset identifiers are unique.
* Ingestion is idempotent.

Invariants should drive property tests.

---

## Cross-Cutting Concerns

Cross-cutting concerns apply across domains.

Examples:

* logging
* metrics
* tracing
* auditing

They are not business capabilities.

Example:

```hcl
cross_cutting_concern "logging" {
    applies_to = ["*"]
}
```

---

# Development Process

## 1. Define Use Cases

Describe behavior from the user's perspective.

---

## 2. Identify Actors

Identify responsibilities within the system.

---

## 3. Define Capabilities

Extract capabilities from use case verbs.

---

## 4. Define Contracts

Create Malli schemas for capability inputs and outputs.

---

## 5. Define Stages

Describe system lifecycle states.

---

## 6. Define State Machine

Describe valid stage transitions.

---

## 7. Define Workflows

Describe valid paths through the state machine.

---

## 8. Define Invariants

Describe properties that must always hold.

---

## 9. Generate Tests

Tests should be derived from:

* contracts
* workflows
* invariants

Priority:

1. State transition tests
2. Workflow tests
3. Contract tests
4. Property tests
5. Integration tests

---

## 10. Implement

Implementation exists to satisfy specifications.

Code is not the source of truth.

### 10.1. Synchronize results to kanban.org ticket.

---

# AI-Assisted Development

AI tools are implementation assistants.

Examples:

* Aider
* ChatGPT
* Claude Code

AI should consume:

```text
usecases/
workflow.hcl
contracts/
invariants/
adr/
```

before generating code.

Preferred workflow:

```text
Use Case
    ↓
Contract
    ↓
State Machine
    ↓
Tests
    ↓
Implementation
```

Avoid asking AI to invent architecture.

Prefer asking AI to implement capabilities against existing specifications.

---

# Source of Truth

The source of truth is:

1. Use Cases
2. Contracts
3. State Machine
4. Workflows
5. Invariants
6. ADRs
7. Tests

The domain model emerges from these artifacts.
Implementation is a realization of the above artifacts.
When implementation and specification disagree, update one of them immediately.
Long-term maintainability is preferred over short-term implementation speed.

# Compliance & Traceability
## The Kanban-as-Source-of-Truth Principle
Every technical task, implementation detail, or architectural change
must be mapped to a specific ticket in kanban.org. The ticket serves
as the single source of truth for human oversight.

## AI-Automated Traceability
To ensure compliance and maintain a historical record of system
evolution, AI agents acting as implementation assistants are required
to perform Ticket Synchronization upon the completion of any task:

- **Context Injection:** After implementing or modifying a capability,
  the AI must provide a structured summary of changes (e.g., modified
  contracts, affected invariants, or new test coverage) directly into
  the corresponding ticket.

- **Compliance Verification:** If a change impacts an Invariant, the
  AI must explicitly document how the new implementation preserves the
  invariant.

- **Audit Trail:** The ticket's comment history serves as an immutable log of "Why" and "How" the code reached its current state.

## Required AI Interaction Protocol
When working on a ticket (e.g., KAN-123), the AI agent should follow this post-action hook:

Plaintext

``` text
[AI AUTO-UPDATE: KAN-123]
- Summary: Implemented capability X.
- Contracts: Updated schema Y.
- Invariants: Verified Z remains intact.
- Artifacts: Linked PR/Commit hash.
```

This requirement applies to all development workflows, ensuring that any human reviewer can assess the project's health purely by reading the kanban board without digging through git logs.
