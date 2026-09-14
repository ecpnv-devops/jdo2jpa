## 1. Gate implementation on attribution and runner feasibility

- [x] 1.1 Spike superclass insertion using a source-defined application parent and assert consistent extends-tree/class-level supertype metadata, actual modifiers and listener annotations visible to the next visitor, and normal type validation against the repository's OpenRewrite version.
- [x] 1.2 Repeat the attribution spike with a dependency-defined superclass on the application classpath but absent from template-global resource jars.
- [x] 1.3 Prove a module-boundary fixture that migrates/compiles a parent and processes the child in a separate invocation using only the resulting dependency, covering inherited and absent configured listeners.
- [x] 1.4 Prove the typed execution-context error contract with returning and throwing handlers, unchanged affected input to the failing recipe, permitted earlier edits, and a runner that exits nonzero and rejects partial output.
- [x] 1.5 Record exact APIs, commands, and spike outcomes in design.md and pass all spike criteria before sections 2–6; if any criterion fails, stop for a reviewed design revision rather than weakening validation.

## 2. Capture the combined failure

- [x] 2.1 Add minimal TurnoverRollupRun-shaped and TurnoverRollupRunOrchItem-shaped fixtures and a combined superclass-insertion, entity-conversion, and Causeway-listener regression that fails on the current omission.
- [x] 2.2 Add paired spaced/compact identityType regressions and assert inserted superclass attribution using application-like source/dependency fixtures.
- [x] 2.3 Add controls for a pre-existing resolved abstract superclass and an already-extended entity, retaining normal type-attribution validation for successful cases.

## 3. Make explicit datastore identity matching structural and API-compatible

- [x] 3.1 Add optional annotationAttributeName and annotationAttributeValue strings, retain the existing three-argument constructor signature/order/behaviour, and provide the extended five-argument JSON creator described in the design.
- [x] 3.2 Add direct-construction compatibility tests plus descriptor and YAML loading tests proving original property compatibility and optional new properties, including conflicting and incomplete configurations.
- [x] 3.3 Implement structural enum matching with tests for whitespace, comments, line breaks, qualified references, fully qualified references, and static imports while preserving legacy annotationCondition regex behaviour.
- [x] 3.4 Switch the datastore-identity YAML rule to annotationAttributeName=identityType and annotationAttributeValue=javax.jdo.annotations.IdentityType.DATASTORE and cover APPLICATION and omitted attributes.

## 4. Preserve and recover superclass attribution

- [x] 4.1 Implement the spike-proven application-source/attributed-type resolution and keep inserted extends expressions and class hierarchies consistent without fabricating modifier flags.
- [x] 4.2 Reuse the mechanism in class-specific superclass insertion where applicable, with tests for existing superclasses and correctly resolved concrete versus abstract types.
- [x] 4.3 Implement UnresolvedEntityHierarchyException reporting through the execution-context handler and test its entity, parent, recipe, and remediation fields, unchanged affected declaration/imports on a returning handler, and normal propagation on a throwing handler.

## 5. Make Causeway listener insertion hierarchy-aware

- [x] 5.1 Introduce a Causeway-specific listener decision that resolves effective inherited listener identities, superclass-listener exclusions, and planned ancestor additions within one source set without changing unrelated AddAnnotationConditionally semantics.
- [x] 5.2 Wire the Causeway YAML recipe to the new decision and test concrete/abstract ancestors with missing, matching, and unrelated listeners.
- [x] 5.3 Preserve explicit entity-level listener lists verbatim and test custom-only, empty, already-configured, ordered multiple-listener, and excluded-superclass-listener cases.
- [x] 5.4 Add multi-file parent/child regressions in both source orders and assert no redundant inherited listener registrations.
- [x] 5.5 Extend the separate-module fixture to cover inherited listeners, no configured listener, exclusions, unavailable parent metadata, and provenance/precondition documentation for unsupported stale dependencies.
- [x] 5.6 Test recoverable incomplete attribution and genuinely unresolvable hierarchies with the exact diagnostic/partial-output contract, including failure after earlier recipes have changed the entity.
- [x] 5.7 Run the configured sequence on both turnover-shaped fixtures and assert usable metadata, required listener insertion, and no changes on a repeat run.

## 6. Verify the candidate artifact and pinned Estatio A/B regeneration

- [x] 6.1 Run focused tests and distributable YAML loading checks, then an unskipped final mvn clean verify against the exact candidate source revision; retain full commands, JDK/Maven versions, profiles, exit status, test summary, and logs.
The accepted implementation is restored to candidate6's source.
The later inherited-annotation cleanup is reverted and deferred; the recorded round6 evidence therefore completes the artifact and consumer-validation gates below.

- [x] 6.2 Before regeneration, populate an acceptance manifest with resolved baseline/candidate coordinates, distinct immutable artifact versions, source commits, SHA-256 checksums, and environment; use the pre-change recipe as baseline and the verified implementation as candidate without overwriting one SNAPSHOT coordinate.
- [x] 6.3 Prepare two isolated clean Estatio inputs pinned to prod 4dd98637d572240ff99cb8c998590074c27c839d with separate dependency repositories and identical non-recipe inputs, recipes, profiles, exclusions, and parent-first orchestration; record adjustments and block acceptance if migrated-parent dependency ordering cannot be satisfied.
- [x] 6.4 Run all configured regeneration passes for baseline and candidate and retain commands, exit statuses, logs, complete generated-tree inventories, and parent artifact provenance; use JPA 907c723889d3306352c2e50cfbbae3df6406d949 only for reconciliation, never as an A/B starting input or oracle.
- [x] 6.5 Compare the complete output trees and inventory every changed class by module, old/new superclass, declared/effective listeners, and justification; resolve every difference beyond intended superclass/listener changes and necessary imports, including mappings, resources, and configuration.
- [x] 6.6 Confirm candidate6's first-pass output gives both turnover entities the intended integration without redundant effective listener registrations, leaves excluded BackgroundCommandsOrchestration unchanged, and passes generated-output review and JPA compilation.
  - Acceptance clarified by the user: CI rewrites once from a clean prod checkout; full-output repeat-run idempotence is not required.
  - Completion applies to candidate6 only, not to the later cleanup commit 5f0acf2 or an unvalidated replacement artifact.
  - Round6 evidence: both turnover entities compile with one annotation-defined effective candidate listener, and the entire candidate turnover module is repeat-run stable; the excluded maintained source is byte-identical to the pin.
  - Repeat-run differences in three other sampled modules are retained as diagnostics, not acceptance blockers; every candidate delta reproduced on the baseline.
  - Deferred follow-up: the source-aware inherited-annotation cleanup in 5f0acf2 had library-only verification; its code and added tests are reverted, not included in the accepted implementation.
  - No additional cleanup pass is required merely to achieve idempotence; the investigation is retained in `docs/entity-listener-idempotence-follow-up.md`.
- [x] 6.7 Compile directly affected modules and required reactor dependencies for baseline and candidate where the baseline supports it, including representative Mallcomm integration and estatio-mallcomm/mallcommturnover; record exact commands, selected modules, profiles, and results.
- [x] 6.8 Record unrelated regeneration/build failures with evidence and baseline reproduction where possible; do not bypass failing checks or substitute narrower builds as passes, but treat baseline-reproduced consumer blockers as non-blocking handoffs and candidate-specific regressions as library acceptance failures.

## 7. Release notes and consumer rollout handoff

- [x] 7.1 Write breaking generated-source release notes covering expanded formatting-independent superclass eligibility, newly inserted listeners, preserved constructor/YAML compatibility, explicit custom/empty listener overrides, parent-first dependencies, and typed-error/partial-output handling.
- [x] 7.2 Hand off the exact candidate artifact, pinned inputs, acceptance manifest, affected-class inventory, build evidence, and reproducible F04/Mallcomm commands to the Estatio consumer; identify a separate owner for the maintained BackgroundCommandsOrchestration fix and its verification.
- [x] 7.3 Descoped by user: runtime persist/update/remove and callback-count verification are not required; listener presence and absence of redundant annotation-defined registrations are verified for candidate6 under 6.6.
- [x] 7.4 Record runtime checks as out of scope and not performed, not as passed; missing runtime evidence is not an acceptance or rollout gate imposed by this change.
