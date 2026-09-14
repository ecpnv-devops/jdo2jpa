# Idempotence follow-up: tasks 6.6 and 7.3

## Discriminator annotation drift

A focused composed test reproduces a stale-parent-annotation defect in `RemoveInheritedAnnotations`.
The sequence adds `DiscriminatorColumn` to a source parent and then attempts to remove the redundant annotation from its child.
The original implementation reads only parent `JavaType` annotations and leaves the child annotation unchanged, even over three cycles.
The failing test log is `/tmp/f04-idempotence-red.log`.
This reproduces the mechanism consistent with the two round6 test-subclass discriminator differences; fresh consumer regeneration must still verify the complete application cases.

The follow-up implementation uses the existing module/source-set-scoped `EntityTypeResolver` source index.
It refreshes the scan before removing annotations, prefers source-declared parent annotations over stale type annotations, and retains existing dependency type metadata when no source declaration is available.
The public nested visitor constructor and its annotation-removal hook remain available.
Tests cover parent-first and child-first visitation and an already-normalized hierarchy.
The composed transformation needs two changing cycles and is stable in the third; it does not promise a single changing cycle.
Normal successful-case type validation remains enabled.

`mvn -o clean verify` with Java 21 reports 260 tests, zero failures/errors, and two existing skips.
The log is `/tmp/f04-idempotence-verify.log`.
This is library verification only, not acceptance of a new immutable candidate.
Candidate6 and its completed round6 comparison remain unchanged reference evidence.
A new candidate must be independently pinned and verified against the consumer before task 6.6 can be completed.
Tasks 6.2–6.5, 6.7, and 7.2 are reopened for that validation rather than transferring candidate6 results to changed code.

## Remaining import drift

The two capex repository files in the completed candidate tree are byte-identical to their original pinned inputs.
Their unused `Collection` and static `search` imports therefore predate regeneration.
Repeat-run logs attribute their removal to `com.ecpnv.openrewrite.java.RemovedUnusedImports` during `rewrite_local`.
That recipe deliberately uses `NoMissingTypes` as a safety precondition.
A controlled disposable probe restored the original capex inputs and ran all three passes plus another local pass against the completed candidate6 dependency repository, without generated QueryDSL sources.
All four commands succeeded, the resulting Java tree matched the completed round6 Java tree, and neither unused import was removed.
Adding only the 17 genuine generated QueryDSL Java sources then caused both imports to be removed by the local pass, with no compiled classes or test classes supplied.
This isolates availability of generated QueryDSL sources as sufficient to change cleanup behaviour for these files.
Moving only compiled classes out of an otherwise completed target directory did not prevent cleanup.
No safety precondition was disabled, and original round6 evidence was not modified.
Logs are `/tmp/f04-capex-fresh-{0,1,2,3}-*.log` and `/tmp/f04-capex-generated-sources-only.log`.
The corresponding JSON records are retained in `docs/entity-listener-evidence/`.

An explicit cleanup stage after JPA annotation processing, followed by compilation and repeat validation, is the proposed consumer-pipeline correction.
This changes the declared processing sequence and requires approval, documentation, symmetric application, and fresh verification of the next immutable candidate.
It has not been added or accepted by this follow-up.
Do not bypass the missing-types precondition, fabricate metamodel sources, silently clean comparison inputs, or call the current output stable.
Task 6.6 remains open.

## Runtime prerequisite

Task 7.3 remains open and consumer-owned.
No disposable runtime/dataset or startup procedure has been approved for this check.
The application maintainer must identify the non-production environment, safe fixture data, startup/profile instructions, and tester before lifecycle writes are performed.
The operation matrix and required callback/downstream observations remain in `entity-listener-runtime-handoff.md`.
No database was started, contacted, or modified by this investigation.
