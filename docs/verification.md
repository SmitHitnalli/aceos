# Verification evidence

As of 13 October 2026, final college feature scope is closed and repair-return verification is complete. Evidence below distinguishes automated checks, visual inspection and Smit's human confirmation. The final configured v2.0.0 verify run and local college freeze are complete; GitHub publication is separate.

## Final v2.0.0 configured suite

On 13 October 2026, the complete configured Maven verify run passed 89 tests: 46 DB acceptance, 3 password/session and 40 JavaFX; zero failures/errors/skips. The JAR built in the same successful run. This is the full-run result, distinct from overlapping earlier focused counts. One older test assertion was updated for the existing correction shortcut’s parent tab; no new navigation behavior was introduced.

## Repair return final feature

| Evidence | Result |
|---|---|
| Focused database checks across implementation and verification | 10 distinct checks; zero failures/errors/skips in final runs |
| Focused JavaFX checks across implementation and verification | 6 distinct checks; zero failures/errors/skips in final runs |
| Packaging after checks | Passed with tests skipped in packaging step |
| Controlled role-shell layouts | Reviewed at 800×520 and 1280×800 |
| Live return/correction/resubmission/verification/history flow | Smit confirmed all five steps worked |

Repeated focused runs overlap these totals. Earlier chunk counts also overlap and must not be added into a unique project-wide total. The final configured 89-test suite is reported separately above.

Database checks cover permissions/ownership/reset-invalidated sessions, repeated cycles, immutable earlier evidence/timestamps, exact submission/return-review guards, competing return/verify and duplicate resubmission, audit-failure rollback, workload counts, privacy, accepted evidence, independent holds and overlapping repair availability. Migration checks cover V004 upgrade, fresh setup, preflight inconsistency, rerun, STARTED/checksum mismatch, weakened correction rules and missing/cross-work references. Runtime UPDATE/DELETE on submissions/reviews is denied.

UI checks cover role actions, dashboard correction filter, history/dispositions, action labels/focus/visibility, distinct drafts, duplicate/save departure protection, refresh-before-retry, saved-result/read-failure recovery, retained read-only drafts after resolution, and Stay/Discard.

## Earlier delivered chunks

F1/F2, Phase 2, all six connected Phase 3 chunks, recovery/navigation and Phase 4 comments/reasons, workload and withdrawal/reopening were locally verified in their recorded focused runs. Existing source tests remain available. This document carries those outcomes forward without rerunning unchanged workflows for documentation-only changes.

Smit confirmed administrator/requester/technician sign-in, complaint-to-verified-repair, seven-tab navigation, comments/reasons, both workload walkthroughs, withdrawal/reopening, and the remaining keyboard/screen-reader/live reset/session-invalidation/action-feedback checks. These are human-reported outcomes, not independent automated audits. Individual earlier preventive/inspection edge cases were not separately recorded as live checks.

## Migration preservation

Both isolated pre-V005 backups were restored with all 15 table checksums matching. V005 preserved all 14 original non-migration tables/fields and V001–V004 metadata. Historical unknown actors/reviews were not synthesized. Original aceos, V000–V004 resources and frozen v1.0.0 were preserved. Private backups/configuration are not deliverable test fixtures.

## Reproducing checks safely

Use `mvn verify`, configured only for disposable `aceos_next_test`, with private runtime/operator variables described in [setup](foundation.md). Integration fixtures may clear/rebuild that schema; never point them at development or original data. Enable `ACEOS_UI_TEST=true` for graphical checks. A run with skipped integration/UI tests establishes only the checks that actually ran.

The final JAR is aceos-2.0.0.jar. The local v2.0.0 tag identifies the college freeze; no public GitHub release is claimed by these local checks.
