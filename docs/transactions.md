# Atomic transactions and consistency

These examples explain the implemented service transaction boundary. The pseudocode is explanatory, not a replacement SQL script or executable database reset.

## Shared transaction boundary

`AceOsService.execute` opens `Transactions`, checks schema compatibility, locks `security_gate`, validates the session/required role, enters the SQL scope, performs the operation, appends its audit event and commits. The owner rolls back if no commit occurs. READ COMMITTED avoids retaining an early schema-read snapshot while waiting for a lock. Asset locks coordinate availability/evidence changes. The local gate deliberately serializes service operations for this college application.

Repositories borrow a transaction-bound JDBC connection. Its proxy ignores their commit/close/setAutoCommit calls; only the owner commits/closes. Prepared statements bind inputs. Expected-state updates must affect one row; stale or inconsistent actions abort.

## Example one Administrator return

```text
begin authorized administrator transaction
load scoped work order and lock its non-retired asset
check COMPLETED, unverified, open linked complaint
check expected current submission and no earlier review of it
insert immutable RETURN review with administrator and required reason
conditionally change COMPLETED to RETURNED for that same submission
recalculate availability and append audit
commit every write together
```

If audit insertion or the state change fails, the review and status change roll back. No reason remains without its return. Source: `RepairReviewRepository.returnForCorrection`, `AceOsService.returnRepair`.

## Example two Technician correction

```text
begin authorized assigned-technician transaction
check RETURNED and exact current submission plus return-review ID
insert the next numbered maintenance submission with correction text
conditionally move RETURNED to COMPLETED and advance current_submission_id
retain assigned_at, started_at and first completed_at
recalculate availability and append audit
commit
```

Competing resubmissions cannot both succeed for the same return. UNIQUE(work_order_id, submission_number) and expected state/evidence checks prevent duplicate history. A failure rolls back the inserted record and pointer/state change. Source: `RepairReviewRepository.resubmit`, `AceOsService.resubmitRepair`.

## Example three Verification and resolution

Verification requires the displayed latest submission ID and an unreviewed COMPLETED repair. In one transaction it inserts the VERIFY review, writes `verified_submission_id`/`verified_at`, changes work to VERIFIED, resolves its WORK_ORDER_CREATED complaint, recalculates availability and audits. A stale screen cannot accept a newer correction merely because state returned to COMPLETED. Another unfinished repair or safety hold still restricts availability. Source: `RepairReviewRepository.verify`.

## Example four Failed inspection and independent hold

The assigned technician completes a scheduled inspection with result/findings/date. A FAILED result creates a separate safety hold in the same authorized transaction and recalculates availability. A later PASSED result records inspection success but does not clear that hold. Administrator clearance separately validates the active hold, required reason and later same-asset evidence, recording actor/evidence/time atomically. Source: `AssetInspectionRepository`, `AvailabilityRepository` and their service actions.

## Other atomic operations

Complaint acceptance/rejection/withdrawal/reopening save status, required append-only reason and audit together. Initial repair completion saves first completion and its maintenance record/current pointer together. Account changes serialize with assignments and invalidate authentication versions so stale sessions cannot write.

## Database constraints and their limits

Primary/foreign/unique/check constraints protect identities, references, states and evidence shape. A complaint has at most one work order; a work order can have many numbered submissions; a submission has at most one terminal review. Composite current/verified evidence FKs enforce same-work linkage. Role, temporal-evidence and ownership rules still require service checks; database constraints alone are not the full business policy.

MySQL DDL migrations are separate operator operations and are not treated as rollback-safe application transactions. Interrupted migrations require deliberate restoration/review, not an automatic down migration.
