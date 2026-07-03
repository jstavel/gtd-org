# ADR 0007: Project Visualization, Flat Kanban, and GitHub Portfolio Sync
this is a copy of ADR from ~/org/docs/adr/0004-project-visualization-and-workflow.md

*   **Status:** Accepted
*   **Date:** 2026-06-03

## Context 
When building projects intended as a "Showcase" or "Proof of Work" (PoW) for external review (e.g., GitHub), there is a tension
between the developer's local Emacs/Org-mode workflow and how project
management renders on a standard web interface. Standard nested
Org-mode files render poorly on GitHub, leading to bad visibility, yet
full automation of status syncing introduces unnecessary engineering
overhead.

## Decision
We establish a lightweight, disciplined visualization strategy to balance high-level transparency on GitHub with low-friction execution in Emacs:
1. **The Execution Board (`kanban.org`):** This file is the primary daily engine. It strictly follows a "Flat Kanban" structure, utilizing flat Org-mode checkboxes (`- [ ] Task`) instead of heavily nested headlines (`****`). This guarantees beautiful, native rendering of task lists on the GitHub web UI.
2. **The High-Level Index (`README.md`):** The project readme acts as the executive dashboard. It contains a static Markdown table representing macro-milestones and their status (`Backlog`, `Next`, `WIP`, `Done`).
3. **Manual Lifecycle Sync:** To avoid over-engineering, the `README.md` dashboard is strictly updated *manually*, and only when a major Milestone shifts states. Granular tracking remains entirely isolated inside `kanban.org`.
4. **ID-Based Persistence:** All cross-document links between projects, runway tasks, and the flat network must use immutable Org-mode IDs (`:ID:`) rather than relative file paths. This ensures links never break when files move across horizons (e.g., moving from `0_runway/` to `1_horizon/`).

## Consequences
- **Positive:** Excellent repository readability on GitHub, turning a private "Second Brain" project into a polished public portfolio piece.
- **Positive:** Zero cognitive load spent on building or maintaining custom automated sync scripts.
- **Negative:** Requires manual discipline to update the `README.md` status table when milestones change.
- **Negative:** Requires running `org-id-update-id-locations` periodically after moving files across local directories.
