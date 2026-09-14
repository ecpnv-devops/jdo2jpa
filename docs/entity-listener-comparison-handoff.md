# Entity listener comparison handoff

## Outcome

The baseline/candidate6 comparison completed across all 151 modules in the union of the pinned reactors.
Each side completed 134 executions of each of the three rewrite profiles and 148 clean JPA installations.
The latest execution of every required stage has exit zero, no recorded Maven errors, and no hierarchy errors.
Seven rejected attempts per side remain preserved in the workspace, together with the restored-input checkpoints and symmetric consumer corrections.
Successful narrow diagnostics have not been substituted for the complete comparison.

The complete generated-tree comparison contains ten differing Java files in seven modules, with no other differing tracked or nonignored output files.
The differences are two missing-listener insertions, four nested `EntityScan` scope corrections, and four unused-import/import-group whitespace changes.
The reviewed inventory records compiled superclass and declared/annotation-defined effective listener metadata for the affected types.
The `EntityScan` corrections move scanning to types that themselves declare `ComponentScan`; they do not propagate scanning to enclosing interfaces or helper types.

## F04 result

Both `TurnoverRollupRun` and `TurnoverRollupRunOrchItem` extend `EntityAbstract` on both sides.
The baseline has no declared or annotation-defined inherited listener for either entity.
Candidate6 declares one `OrmEntityListener` on each entity, with no additional inherited registration.
Both sides successfully clean-installed `estatio-mallcomm/mallcommturnover` after regenerating and installing its required parents.
Candidate6's entire turnover module is unchanged by a repeat of all three rewrite passes.
The maintained, excluded `BackgroundCommandsOrchestration.java` is byte-identical to the pinned input on both sides.
Repository XML review found listener overrides for nine framework entities, not for the turnover entities or their `EntityAbstract` superclass.
Runtime-selected dependency mappings and dynamic metadata still require consumer verification.

## Remaining gates

**Do not claim full-file idempotence or runtime rollout acceptance.**
All seven affected modules were checked on both sides in isolated generated-output snapshots.
Candidate6 is fully repeat-stable in four modules, including turnover and financial.
Its repeat changes in the other three modules exactly reproduce on the baseline:

- Capex removes unused imports from `IncomingInvoiceItemRepository` and `PurchaseOrderRepository`.
- Communications removes `DiscriminatorColumn` from the test subclass `CommunicationChannelOwnerLinkForDemoObjectWithNotes`.
- Document removes `DiscriminatorColumn` from the test subclass `DocumentTemplateForTesting`.

No candidate listener changes occur on repeat.
The baseline additionally adds the missing turnover listeners and more nested `EntityScan` annotations on repeat, without correcting its overly broad enclosing-type scope.
Task 6.6 is complete for candidate6 under the user-confirmed one-shot CI workflow from clean prod input.
Full-file repeat stability is not required; these exceptions remain diagnostic findings, not claims of idempotence.
The later cleanup commit 5f0acf2 is not covered by candidate6's acceptance.
Task 7.3 remains consumer-owned and pending.
See `entity-listener-runtime-handoff.md` for the owner role, operation matrix, and separate maintained-source repair.
Rollout acceptance remains explicitly open without requiring runtime access to complete otherwise accepted library work.

## Artifact and reproduction

Candidate: `com.ecpnv.openrewrite:jdo2jpa:1.2.4-f04-candidate6`.
Source: `f91f8e780a20925436963b27492fe5b04969f943`.
SHA-256: `8b8ab10efc148c63b8e0032ea371fd8f0cf852943f857b06d25633012f5a90ac`.
The immutable build reports 257 tests, zero failures/errors, and two existing skips.
The artifact is locally installed, not remotely published by this workflow.
The baseline coordinate, source revision, checksums, environment, and exact commands are in `entity-listener-acceptance.json`.

The workspace is `/tmp/f04-acceptance-401d6c4/round6`.
Use Java 21 from `/Users/danhaywood/.sdkman/candidates/java/21.0.10-tem` with `MAVEN_OPTS='-Xmx6g -Xss4m'`.
For fresh reproduction, create two detached Estatio inputs at `4dd98637d572240ff99cb8c998590074c27c839d`, with pipeline resources pinned to `9def23901fb3309bfc084a6e5cf7fcdbaf884691`.
Apply the checked `entity-listener-evidence/consumer-input-adjustments.patch` identically before processing sources.
Use separate dependency repositories with the documented seed provenance, and select the immutable recipe coordinate per side.
Run the recorded parent-first driver rather than jumping directly to the turnover module against unmigrated parents.
The driver snapshot retains its original workspace path; relocate that constant and its input files explicitly if reproducing elsewhere.
Do not rerun over a completed or rejected working tree as though it were a fresh input.

After successful parent-first regeneration, the recorded candidate turnover installation command is:

```sh
mvn -o \
  -Dmaven.repo.local=/tmp/f04-acceptance-401d6c4/round6/repositories/candidate \
  -Djdo2jpa.version=1.2.4-f04-candidate6 \
  -pl estatio-mallcomm/mallcommturnover \
  -Ddisable_dn -DskipTests \
  '-Pjpa,jpa-apt,!jdo,!jdo-apt' clean install
```

`entity-listener-evidence/` retains the complete reviewed changed-class inventory, completion counts, F04 commands and diffs, repeat-review exceptions, repository XML review, input patch/amendments, and harness source snapshots.
Full command logs, full-tree hashes, isolated module classpaths, and installed-parent artifact provenance remain in the comparison workspace.
The Estatio application maintainer owns adopting the consumer POM amendments, assigning a runtime tester, and separately repairing the excluded maintained orchestration entity.
