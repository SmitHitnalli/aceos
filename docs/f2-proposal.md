# F2 proposal — consistent asset availability

Approved 12 October 2026 under D-007; implemented and locally verified on `codex/role-based-foundation`. See [foundation setup and verification](foundation.md) for delivered behaviour and actual results. The original approved scope and named maintenance plan below are retained.

## Scope and expected behaviour

Separate an asset's manual lifecycle (`ACTIVE`, `OUT_OF_SERVICE`, `RETIRED`) from operational availability. Availability precedence: retired → explicit manual/safety hold → under maintenance while any started unverified job remains → active. Keep inspection compliance a separate labelled result.

- Submission, acceptance and future assignment alone do not take an asset offline. Starting repair does; completion remains awaiting verification.
- Verifying one repair closes its complaint but does not make the asset available while another started repair or hold remains. Permit overlapping corrective repairs; starting on an out-of-service asset does not clear the hold.
- Treat failed inspections as safety relevant for the current simple model. Failure atomically creates a hold and marks the asset unavailable. Retain each failure's history.
- Only administrators can clear a safety hold, with a required reason and supporting later passing reinspection or verified corrective repair on the same asset. Record evidence and actor; clearance of one hold does not erase another.
- Requesters may report non-retired assets, including those under maintenance/out of service. Retired assets block new operations. Retirement is blocked while unfinished jobs, scheduled inspections or active preventive schedules remain; preserve historical records.
- No cancellation/reopening/repair-return/comment/checklist expansion; those stay Phase 4.

## Affected areas

Numbered migration runner/resources, asset model/repository, work-order and inspection transactions, availability coordination, administrator hold/lifecycle controls, asset directory/status totals, focused tests, setup/recovery documentation. Keep Java/JavaFX/JDBC/MySQL. Full dashboards and connected asset profiles remain later phases.

Add `manual_lifecycle` and auditable safety hold records, retaining the existing effective `assets.status` for current screens/queries. Use transaction-bound asset locks and one availability calculation for start/completion/verification/inspection failure/manual change/hold clearance. Preserve F1 security coordination and ownership checks.

## Migration and verification

Migrate only the isolated development/test schemas after backup/restore verification; do not copy/mutate the original v1 database. Preserve existing ACTIVE/OUT_OF_SERVICE/RETIRED manual states. Map UNDER_MAINTENANCE only when supported by started-unverified work. Report inconsistent rows or historical failed-inspection ambiguity and require reviewed mapping; do not silently reinterpret valuable data.

Acceptance checks: two overlapping jobs; completion/verification in both orders; failure during repair; two independent holds; supported/unsupported clearance; technician clearance denial; explicit out-of-service/retirement guards; stale/concurrent transitions; rollback if any linked write fails; migration rerun/checksum/interruption; preserved IDs/history; accurate scoped status/totals. Capture minimal administrator controls and asset statuses at supported window sizes. Report remaining human checks honestly.

## Exact Brain maintenance plan for F2 approval

Re-read each target before targeted edits. Apply after approved and verified work, not during this proposal.

1. `02 Projects/AceOS/STATE.md`: extend Status with verified F2 manual lifecycle/derived availability/safety hold behaviour; replace Current Task / Next with Phase 2 shell/dashboard review; append actual F2 check/migration outcomes and remaining manual checks under Verification. Retain F1/release baseline detail.
2. `02 Projects/AceOS/TASKS.md`: check the F2 availability task only once verified; retain human F1 checks if unfinished; make Phase 2 review the active next chunk. Leave Phase 2/3 implementation unchecked.
3. `02 Projects/AceOS/DECISIONS.md`: append D-030 “Asset Availability and Safety Holds” with the approved precedence, overlapping repairs, failure/clearance evidence, retirement guards and data-preserving mapping rules/reasons. Preserve D-027 through D-029.
4. `02 Projects/AceOS/Sessions/2026-10-12.md`: append a concise F2 outcome block with delivered rules, actual verification, preserved data and next Phase 2 review. If work finishes another date, propose the changed dated targets before writing them.
5. `01 Journals/Daily/2026-10-12.md`: append concise F2 completion/check/next-review bullets.
6. `02 Projects/AceOS/PROJECT.md`: update the implementation status to “F1/F2 foundation implemented and locally verified; role dashboard/connected-screen milestone still in progress.” Preserve the released version and architecture.

Maintain README/current-built Notion with actual availability rules and migration limitations; use focused commits/pushes on the development branch. No deployment or release/tag during this chunk. Material changes/ambiguous data mappings require another decision.
