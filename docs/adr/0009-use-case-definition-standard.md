# ADR 0009: Standard for Use-Case Definition
it is a copy of ~/org/docs/adr/0008-use-case-definition-standard.md

## Status
Accepted

## Context
To ensure clarity, auditability, and automation of our system (based
on HCL architecture and Malli validation), we need a unified format
for describing processes. Without standardization, ambiguity arises
regarding who is responsible for what (Actors) and what the safe
boundaries of a process are (Alternative Flows).

## Decision
We are establishing a mandatory standard for documenting all future processes and tasks using templates. Each use-case defined in this manner must be mappable to:

- **workflow.hcl**: Definition of actors and state transitions.
- **Malli schemas**: Definition of input/output contracts.

## Template (Mandatory Structure):

``` markdown
 # Use Case: [Title]

 ## 1. Actor
 - Definition of the role and its 'Capabilities'.

 ## 2. Trigger (Precondition)
 - Necessary system state for execution.

 ## 3. Flow (Main Scenario)
 - Procedure defined using verbs (Verbs = transformations/actions).

 ## 4. Alternative Flows (Exceptions)
 - Definition of 'Guard Clauses' to ensure compliance.

 ## 5. Postconditions (Invariants)
 - What must hold true upon completion (system state).

 ## 6. Compliance / Audit Requirements (Archiving)
 - Requirements for logging and auditability.
```

## Consequences
### Pros:

- **Aider-Ready**: Aider receives structured input that directly references HCL and Malli.
- **Auditability**: Compliance/logging requirements are part of the definition, not an afterthought.
- **Cognitive Load Reduction**: Breaking tasks into "Main Flow" and "Alternative Flows" helps in addressing one component at a time during implementation.

### Cons:
Requires discipline in maintaining documentation (though this overhead is offset by increased development speed with Aider).
