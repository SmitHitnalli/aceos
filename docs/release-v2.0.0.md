# AceOS v2.0.0 college freeze

Selected by Smit on 13 October 2026. v2.0.0 separates the final account-enabled, migrated college application from preserved v1.0.0. The release identity is the local annotated `v2.0.0` tag on `codex/role-based-foundation`; publication to GitHub is a separate action.

## Frozen feature scope

All delivered accounts/session/role workspaces, assets/profiles/availability, complaints/reasons/comments/withdrawal/reopening, repair assignment/completion/return/correction/exact verification, workload, inspections/independent holds and preventive schedule features remain. No further feature expansion, deployment or pre-deployment preparation. Task 6 was closed at Smit's request, not claimed as generated demo/viva material.

## Final validation

The complete configured Maven verify run passed **89 tests**: 46 database acceptance, 3 password/session and 40 JavaFX, with **zero failures, errors or skips**. The v2.0.0 JAR built in that same successful run. Both supported layout sizes and existing human confirmations are carried forward. An older dashboard test's shortcut-parent expectation was corrected to reflect the existing correction route; application navigation semantics were not changed.

Documentation/schema/link and packaging identity/integrity checks supplement the test run. Private schemas/data were not packaged, and only disposable aceos_next_test fixtures were mutated by tests. Existing dev/original data and V000–V004 resources remain preserved.

## Package

The college ZIP includes the application, nine runtime JARs, source, migrations, current report/reference documents, launcher and final test evidence. Java24 and separate MySQL8 configuration are prerequisites; credentials, backups, .local and .git are excluded. A separate source ZIP contains the exact frozen source tree. Per-file and archive SHA-256 checks identify the outputs. See distribution/START-HERE.md and docs/foundation.md.

Published v1.0.0 references are unchanged. A local v2.0.0 freeze does not assert that a GitHub tag/release or deployment has been published.
