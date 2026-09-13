## Context

The main migration adds an entity superclass before running `com.ecpnv.openrewrite.jdo2jpa.v2x.causeway`.
The current listener configuration uses `AddAnnotationConditionally` with `allowInherited=false` and `disallowedModifierType=Abstract`.
Its inheritance guard accepts a parent known to be abstract but rejects `JavaType.Unknown`, without checking whether a parent actually provides a listener.

`ExtendWithClassForAnnotationConditionally` creates its replacement extends clause with a JavaTemplate backed by `JavaParserFactory`.
That factory uses the JVM classpath and fixed resource jars rather than explicitly supplying the application module's classpath.
An isolated probe against the current compiled recipes showed an inserted `EntityAbstract` attributed as Unknown and the listener omitted, while a pre-existing resolved abstract superclass received the listener.
The probe also showed that `identityType=IdentityType.DATASTORE` skips insertion whereas `identityType = IdentityType.DATASTORE` triggers it.

Estatio's two turnover entities take the failing route because they initially have no superclass and explicitly use the spaced datastore-identity argument.
Several other classes avoid it because their argument spelling does not match or because they already have a resolved abstract superclass.
The later Estatio listener replacement cannot repair an annotation that was never inserted.

The third F04 entity, BackgroundCommandsOrchestration, is maintained in an excluded `java-jpa` source set and has a separate downstream cause.
No recipe can repair a file its caller excludes.

## Goals / Non-Goals

**Goals:**

- Make the superclass-insertion and listener recipes compose correctly within one migration execution.
- Make explicit datastore identity matching independent of Java formatting and enum-reference spelling.
- Preserve meaningful superclass attribution for subsequent recipes.
- Add the configured listener where it is missing from the effective listener hierarchy and no explicit entity-level listener declaration overrides automatic insertion.
- Preserve custom listener declarations and existing recipe option compatibility.
- Give an actionable error for a hierarchy that cannot be resolved safely.
- Test the actual recipe sequence, including second runs and multiple source files.

**Non-Goals:**

- Editing either Estatio worktree's application code or changing source-set exclusions.
- Fixing audit callbacks, orphan removal, or other review findings.
- Replacing all printed-text conditions throughout the recipe library.
- Inferring new rules for omitted JDO identityType values.
- Globally changing `AddAnnotationConditionally.allowInherited` semantics for unrelated consumers.

## Decisions

### 1. Use a Causeway-specific effective-listener decision

Replace the Causeway YAML's generic abstract-parent heuristic with a dedicated listener recipe or a narrowly scoped helper used by that recipe.
The recipe shall examine the concrete entity's own listener declaration and the effective superclass listener chain.
It shall account for `ExcludeSuperclassListeners` and resolve listener class identities, rather than comparing only simple names or assuming every concrete parent has a listener.
Abstract classes shall not acquire a new listener solely because of this change, but existing listeners on abstract classes shall count for concrete descendants.

Use a source hierarchy index plus attributed dependency types to evaluate multi-file inheritance.
Within one OpenRewrite source set, compute planned ancestor additions before applying edits so source visitation order cannot introduce duplicates.
Estatio invokes OpenRewrite per submodule, so that index cannot predict additions in another module.
Cross-module inheritance is supported only through attributed dependencies representing the parent's already-migrated state.
The consuming workflow must regenerate, compile, and make parent artifacts available before parsing dependent modules, with separate baseline/candidate dependency repositories to prevent contamination.
Unmigrated or stale parent artifacts and predicting future cross-module changes are explicitly unsupported; the recipe cannot reliably detect a resolvable but stale binary, so dependency provenance is a consumer precondition and an acceptance check.
A module-boundary fixture must compile a migrated parent to a dependency artifact and process the child in a separate recipe invocation with no parent source visible.
Test both a parent carrying the configured listener and a parent without it, plus exclusions and unavailable parent metadata.
If the pinned Estatio orchestration cannot satisfy this ordering, acceptance is blocked pending a reviewed orchestration adjustment rather than claiming whole-project duplicate avoidance.

Preserve an existing entity-level `EntityListeners` declaration verbatim, including unrelated/custom listeners and an empty declaration, instead of replacing or appending to it automatically.
Any such explicit declaration suppresses automatic Causeway listener insertion.
Such a declaration is an explicit application customization and is distinct from an absent declaration.
When an ancestor has unrelated listeners but not the configured Causeway listener, a concrete entity without its own declaration still needs the configured listener.

**Alternatives rejected:** setting `allowInherited=true` globally or adding the listener to every concrete subclass.
Both bypass the observed guard but can introduce redundant inherited callbacks and change unrelated recipe behaviour.
Retaining the abstract-parent test while accepting Unknown would still mistake abstractness for listener inheritance.

### 2. Resolve superclass information from the application, not fabricated flags

When adding the extends clause, resolve the configured superclass from available application source/type metadata or a properly configured parser classpath.
Reuse that resolved type in the inserted hierarchy and keep the class's attributed supertype consistent with its extends tree.
Use the same resolution strategy where the class-specific superclass helper shares the insertion mechanism.
Do not manufacture an Abstract flag merely because the configured name contains `Abstract`.
Treat `JavaSourceSet.getClasspath()` as a shallow name index, not authoritative modifier, superclass, or annotation metadata.
For dependency fallback, use a fresh `JavaParser.fromJavaVersion().classpath(Collection<Path>)` against the current module's application dependency artifacts and parse an internal field-only source such as `class __SuperclassMetadataProbe { example.DependencyParent parent; }`.
Read the attributed field type and use it for the inserted identifier and class-level supertype; never emit the probe as application source or add it to the migration's source set.
The field-only probe does not invent a superclass declaration, modifiers, annotations, constructors, or methods: the parser obtains them from the dependency bytecode.
Use full source/bytecode-derived types ahead of shallow index entries, and do not let scanning a later JavaSourceSet marker overwrite already-resolved metadata.
A metadata parser must have a module-specific classpath and type cache so baseline/candidate or module versions cannot contaminate each other.

The Causeway recipe shall be able to recover a superclass reference from source declarations/imports and available attributed types if an earlier transformation left the extends expression incompletely attributed.
For the combined regression fixture, the application superclass is available as source or as an application dependency, not added artificially to the JavaTemplate factory's global classpath.
The test must demonstrate successful resolution in that application-like setup.

If the hierarchy genuinely cannot be recovered, invoke the execution context's error handler with an `UnresolvedEntityHierarchyException` carrying the entity FQN, unresolved superclass FQN/reference, recipe identity, and source/classpath remediation advice.
Resolve the decision before applying edits to the affected class; if the handler returns, the failing recipe returns that class exactly as received, including its imports and annotations.
Do not additionally throw when the handler returns; unaffected classes may continue processing.
If the handler throws, allow that failure to propagate through the runner's normal error handling.
This is not transactional rollback: successful transformations from earlier recipes or other classes may remain in the partial output.
The acceptance/consumer runner must collect these errors, exit nonzero, and reject all partial output from that run even when OpenRewrite itself continues processing.
Tests must use a recording handler to assert the exception type and fields, unchanged affected input to the failing recipe, and permitted earlier edits, then assert runner rejection with a nonzero exit.
No recovery marker or data-table row is required as a substitute for this error contract.
The initial spike must prove this runner behaviour as well as attribution; silently relying on a logged message is insufficient.

**Alternatives rejected:** embedding an Estatio-specific superclass stub in the recipe or requiring application classes on the plugin JVM's global classpath.
These conceal the attribution problem and make the library depend on a particular consuming application.

### 3. Introduce an opt-in structural enum condition

Retain the existing `annotationCondition` regex path for compatibility with callers using arbitrary annotation text conditions.
Add optional string options named `annotationAttributeName` and `annotationAttributeValue`, and switch only the datastore-identity YAML rule to them with values `identityType` and `javax.jdo.annotations.IdentityType.DATASTORE` respectively.
Retain the public constructor `(String annotationPattern, String annotationCondition, String extendsFullClassName)` with the same parameter order and behaviour, delegating to the extended constructor with both new options null.
Use an extended five-argument JSON creator with the existing three parameters followed by the two new options, retaining the existing YAML property names and exposing the new options as optional in the recipe descriptor.
Existing Java callers must compile unchanged and retain the three-argument constructor signature; existing YAML configurations must deserialize unchanged.
Add direct-construction tests and descriptor/YAML loading tests for legacy, structured, conflicting, and incomplete configurations.
Treat simultaneous regex and structured conditions, or an incomplete structured condition, as invalid configuration with a clear validation message.

For the structured condition, inspect the assignment and resolved enum constant rather than `argument.toString()`.
Support simple names, qualified enum references, fully qualified references, and static imports using attribution with source/import resolution where needed.
Whitespace and comments must have no effect.
A different identity constant must not match.
An omitted identityType must not cause new default-policy inference in this opt-in rule.

**Alternatives rejected:** normalising whitespace in the existing regex or broadening it with `\s*`.
Those approaches still fail on comments, qualification, and static imports and leave a semantic migration rule dependent on printed source.

### 4. Test composition, not just each visitor in isolation

Keep focused tests for option validation, enum matching, superclass metadata, and listener hierarchy decisions.
Add regressions executing superclass insertion, entity annotation conversion, and Causeway listener insertion in the same order as the configured migration.
Use both turnover-class shapes, including table/schema constants, rather than only an already annotated JPA class.

Include a resolved pre-existing abstract superclass, a newly inserted superclass, a concrete parent without a listener, an abstract parent with a listener, and a parent/child pair both eligible for insertion.
Exercise multiple source files and reversed source order.
Include an existing custom listener and an excluded-superclass-listener case.
Run the resulting output again and require no changes.
Normal type-attribution validation shall remain enabled for successful cases; disabling it would hide the root defect.

### 5. Prove the mechanism before implementing the feature

The first implementation phase is a bounded attribution and error-propagation spike against the repository's actual OpenRewrite version.
It must insert both a source-defined and a dependency-defined superclass, preserve the extends expression and class-level attributed supertype consistently, and allow the next visitor to read its FQN, modifiers, and listener annotations with normal type validation enabled.
The dependency case must use an application classpath rather than template-global resource jars.
Also prove the module-boundary fixture and the recording-handler/nonzero-runner error contract.
Record the exact APIs, test commands, and outcomes in this design before proceeding with structural matching or production listener changes.
If any criterion fails, stop and revise the design for review; do not disable attribution validation, fabricate types, or proceed on an unverified mechanism.

## Risks / Trade-offs

- [Correct matching affects more classes than before] → Explicitly test formerly compact spellings and document the intentional increase in eligible superclass insertions.
- [Hierarchy decisions depend on source visitation order] → Compute effective and planned ancestor listener registrations before applying additions, and reverse fixture order in tests.
- [Unresolved dependency types make duplication impossible to assess safely] → Resolve from application metadata where available and fail with an actionable diagnostic otherwise.
- [Existing custom listeners may intentionally replace framework integration] → Preserve explicit entity-level declarations rather than silently merging them.
- [Generic recipe option compatibility regresses] → Keep the regex mode opt-in behaviour unchanged and test old configurations alongside the new structured mode.
- [Source-aware resolution is more complex than changing one boolean] → Isolate it in small helpers and cover both source-defined and dependency-defined superclasses.
- [The excluded BackgroundCommandsOrchestration remains unfixed] → Document the separate downstream source edit and do not imply regenerating Estatio repairs it.

## Migration Plan

Complete the gated spike first, then add failing combined-recipe tests and implement structural matching, superclass attribution/recovery, and the Causeway-specific inheritance decision.
Run focused tests, descriptor loading tests, and a final unskipped `mvn clean verify` with a supported JDK against the exact candidate source revision.
Record the full command, JDK/Maven versions, profiles, exit status, test summary, and log location.

### Pinned same-input production-scale validation

- Pin both clean Estatio input trees to prod `4dd98637d572240ff99cb8c998590074c27c839d`.
- Use JPA `907c723889d3306352c2e50cfbbae3df6406d949` for reconciliation only, never as one side's starting input or an expected-output oracle.
- Before regeneration, record exact baseline and candidate recipe coordinates, immutable/distinct versions, source commits, and SHA-256 artifact checksums in an acceptance manifest; do not overwrite one SNAPSHOT coordinate with both builds.
- Select the baseline from the pre-change recipe implementation and tie the candidate to the final verified implementation; the manifest must contain resolved values, not placeholders, before either run starts.
- Use identical recipe selection, source inputs, JDK/Maven, profiles, exclusions, and non-recipe dependency versions for both runs, with isolated build trees and local repositories.
- Use the same parent-first regeneration/build orchestration for both runs and record any harness-only coordinate or ordering adjustments explicitly; never compare an old orchestration to a new one as though only the recipe changed.
- Capture complete commands, exit statuses, logs, dependency provenance, and generated-tree inventories for all configured rewrite passes.
- Compare baseline/candidate output across the complete generated tree and inventory every changed class with module, change kind, old/new superclass, listener declaration/effective inheritance, and justification.
- Permit only intended superclass/listener changes and necessary imports; report all other source, mapping, resource, and configuration differences and resolve them before acceptance rather than normalizing them away.
- Confirm both turnover entities gain the intended integration and maintained/excluded BackgroundCommandsOrchestration remains unchanged by regeneration.
- Compile directly affected modules plus required reactor dependencies and representative Mallcomm integration, including `estatio-mallcomm/mallcommturnover`, wherever the baseline supports compilation; record exact module selections, commands, profiles, and outcomes for baseline and candidate.
- Treat a candidate-specific build regression as a library acceptance failure.
- Record unrelated failures with logs and baseline reproduction where possible; do not skip failing checks or substitute a narrower build as a pass, but keep a baseline-reproduced consumer blocker as a non-blocking handoff rather than a recipe metadata failure.
- Verify repeat-run stability and absence of redundant effective listener registrations using the actual per-module dependency topology.

### Release and consumer handoff

Publish breaking generated-source release notes covering newly eligible compact identity declarations, added superclasses/listeners, constructor/YAML compatibility, custom-listener overrides, the parent-first dependency precondition, and error/partial-output handling.
Hand off the pinned inputs, exact candidate artifact, output inventory, and reproducible commands to the Estatio consumer.
Require a non-production F04/Mallcomm run that exercises both turnover entities through applicable persist/update/remove operations and verifies the intended lifecycle callbacks fire exactly once with no inherited duplication.
Record scenario inputs, callback observation method, expected/actual counts, tester, and result; a missing runtime environment is a rollout blocker, not a passed test.
The maintained BackgroundCommandsOrchestration listener addition and its runtime verification remain a separately owned downstream fix and must not be attributed to this recipe.
Library acceptance requires the gated spike, recipe build, regeneration/diff review, and absence of candidate-specific compilation regressions.
Consumer runtime checks and baseline-reproduced consumer build blockers remain explicit, non-blocking rollout handoffs and do not prevent completion or archival of the recipe change.
Rollback is to retain the previous recipe artifact and discard regenerated application changes before deployment; this proposal introduces no database migration.

## Open Questions

Product-scope and failure-policy decisions are fixed above.
The exact attribution APIs and runner error propagation remain a gated engineering experiment, not permission to begin the remaining implementation speculatively.
The spike must replace this uncertainty with recorded passing evidence or trigger a design revision.
Exact baseline/candidate artifact versions and implementation commits must be resolved in the acceptance manifest once those artifacts exist; none are claimed to have been built or validated by this proposal.

## Gated Spike Outcome

The attribution spike ran against OpenRewrite 8.47.3 under JDK 21 with `mvn -Dtest=SuperclassAttributionSpikeTest test`.
For a source-defined application parent, a scanning recipe obtained the source `JavaType.FullyQualified`, applied the extends template, assigned that type to the inserted `J.Identifier` with `withType`, and updated the child `JavaType.Class` with `withSupertype`.
The resulting extends expression and class-level supertype agreed, and the next test callback observed the parent's real abstract flag and `Deprecated` annotation with normal type validation enabled.
Task 1.1 therefore passed.

### Initial dependency-index failure

The original dependency-defined spike built an application dependency JAR outside the template-global resource classpath and exposed it through `JavaSourceSet.build`.
OpenRewrite resolved the dependency FQN, but `JavaSourceSet.getClasspath()` supplied a shallow type with no abstract flag and no annotation metadata.
The exact task 1.2 assertion failed with `flags and annotations for example.DependencyParent: []`.
This is a limitation of that marker's name index, not of the Java parser's bytecode attribution.
The control regression now explicitly checks `JavaType.ShallowClass`, the missing Abstract flag, and the empty annotation list; shallow entries can still carry a default Public flag.

### Proven dependency-bytecode resolution

Task 1.2 now passes using a separate parser configured with explicit application dependency paths.
The previously disabled test is enabled, and all four tests in `SuperclassAttributionSpikeTest` pass with normal type validation.
`InsertResolvedSuperclass.getInitialValue` parses the internal field-only probe, extracts its fully attributed field type, and retains it ahead of JavaSourceSet index entries using `putIfAbsent` for those entries.
The existing template parser remains unchanged and does not receive the application superclass JAR; both the inserted `J.Identifier.withType(...)` and `JavaType.Class.withSupertype(...)` use the bytecode-derived parent type.
The following visitor observes the real parent FQN, Abstract modifier, and Deprecated annotation.
An additional regression compiles a real `javax.persistence.EntityListeners` annotation onto the dependency parent and verifies its `JavaType.Annotation` element value is an `ArrayElementValue` whose reference array contains `example.DependencyParent$Callback`.
That regression uses the existing Jakarta Persistence 2.2.3 API JAR, whose package is javax.persistence, and introduces no new dependency.
The generated parent JAR and sources are temporary JUnit fixtures cleaned up through `@TempDir`.

Evidence: `JAVA_HOME=/Users/danhaywood/.sdkman/candidates/java/21.0.10-tem` with its bin directory prepended to PATH; `mvn clean verify` exited 0 with 225 tests, 0 failures, 0 errors, and 2 existing skipped tests.
The superclass spike accounts for 4 tests with 0 skips.
The local build log is `/tmp/f04-unblock-clean-verify.log`; durable reruns use the checked-in test source rather than relying on that temporary log.
This verifies the spike checkout, not the eventual release candidate or Estatio acceptance run.

### Completed module-boundary and error-contract spikes

The dependency-metadata blocker is removed without narrowing the cross-module guarantee or adding superclass stubs/global template resources.
The test recipe's `applicationClasspath` constructor property is test-only and is not a new production recipe option or a change to the agreed public API.
The spike explicitly supplies artifact paths; it does not prove automatic classpath discovery from an Estatio Maven invocation.
For production, retain existing fully attributed source/reference types where available and supply the metadata parser with the current module's actual resolved dependency paths, including annotation APIs and transitive superclass/listener dependencies.
Do not try to reconstruct those paths from `JavaSourceSet.getClasspath()` FQNs or use the plugin JVM's runtime classpath as a substitute.
Maven-aware artifact resolution or an explicit runner-to-resolver classpath handoff must preserve module/version provenance during feature implementation; missing paths retain the specified unresolved-hierarchy error policy.

Task 1.3 passes with two separate-invocation module-boundary regressions.
The first runs the configured Causeway migration on a parent, compiles that migrated source into a dependency JAR, parses the child separately with no parent source, and observes the configured listener identity from bytecode.
The second compiles a migrated parent with only an explicit unrelated listener and proves the child invocation distinguishes that absence while retaining the unrelated listener identity.
Both tests use isolated temporary JARs and explicit application dependency paths.

Task 1.4 passes with `InMemoryExecutionContext.getOnError()`, `Recipe.run(...)`, and `InMemoryLargeSourceSet`.
A returning handler records the typed entity, parent, recipe, and remediation fields while the failing recipe produces no changes.
A throwing handler propagates normally through the runner.
A composed spike proves that an earlier recipe edit remains in the internal changeset while an acceptance wrapper returns exit code 1 and publishes no partial sources after the hierarchy error.

Evidence: `JAVA_HOME=/Users/danhaywood/.sdkman/candidates/java/21.0.10-tem` with its bin directory prepended to PATH; `mvn -Dtest=SuperclassAttributionSpikeTest,UnresolvedHierarchyErrorContractSpikeTest test` exited 0 with 9 tests, 0 failures, 0 errors, and 0 skips.
Tasks 1.1 through 1.4 now satisfy the gated source, dependency, module-boundary, and error-propagation criteria.
The exact APIs, commands, and outcomes are recorded in this section and the preceding spike sections, so task 1.5 passes and feature implementation may proceed.

## Resolved Production Module Classpath Handoff

Tasks 3.1 through 3.3 are complete: the generic recipe retains its three-argument constructor, adds the optional five-argument JSON form, validates condition modes, and structurally matches all required enum reference forms.
Legacy and structured YAML loading, descriptor exposure, invalid configurations, and direct Java construction are covered.
The standard YAML now uses the structured condition after superclass attribution was repaired.
Bare PersistenceCapable no longer gains a superclass through the legacy regex path's empty-argument shortcut, consistent with the explicit-only condition specification; this generated-source change is called out in the release notes and must be inventoried during consumer validation.

`EntityTypeResolver` now scans POM `MavenResolutionResult` markers as well as Java compilation units and associates each unit with its nearest POM and source set.
It resolves selected compile/provided or test artifacts from the module's Maven settings/local repository, respecting the standard Maven local-repository override, resolved coordinates, classifiers, timestamped snapshots, and transitive annotation dependencies.
Resolution is local-only and does not download artifacts or use the plugin JVM classpath as the application classpath.
Missing classpath artifacts cannot be interpreted as an unannotated dependency.
Existing full source/reference types take precedence, and caches are scoped to one scan, module, and source set.
Both superclass helpers reuse this mechanism and update the extends type and class-level supertype together.
The existing template's genuinely attributed type can still be retained for bundled supporting types; no Unknown or shallow type is accepted as superclass metadata.

The pinned Estatio profiles explicitly disable Maven parsing.
The baseline and candidate acceptance harnesses must both set `skipMavenParsing` to false to enable dependency fallback; this is a documented consumer configuration prerequisite rather than a new recipe option.
`EntityHierarchyMigrationTest` proves automatic lookup using real MavenParser-generated resolution markers, no artifact-path constructor option, a private local repository, two modules with different versions of the same parent FQN, and a transitive JPA annotation dependency.
It also verifies that the full declarative recipe's preconditions do not prevent metadata scanning.

`AddCausewayEntityListener` computes source ancestor additions from the scan snapshot, uses source annotations instead of potentially stale annotation type metadata, and reads dependency annotation class-array values from bytecode.
It preserves explicit overrides and superclass-listener exclusions and resolves recoverable unknown extends references using source/import information.
The production error tests cover returning and throwing handlers and earlier edits retained in the internal changeset.
`scripts/check-rewrite-run.py` supplies a command-line guard that rejects logged typed errors even if Maven exits zero; disposable-worktree output must not be published after rejection.

Preserving superclass metadata exposed a composition defect in annotation-attribute propagation.
`CopyAnnotationAttributeFromSubclassToParentClass` now keys its scan by FQN rather than replaceable type-object identity and recognises the highest annotated ancestor rather than mistaking an unannotated framework superclass for the entity inheritance root.
The existing inheritance strategy regression retains its original SINGLE_TABLE expectation; no strategy change was accepted to make the tests pass.
Legacy fixtures with unimported IdentityType references were qualified so structural matching tests valid Java rather than relying on a printed-text match of an unresolved enum.

Evidence: `mvn -Dtest=ExtendWithClassForAnnotationConditionallyTest test` passes 9 tests with no failures or skips, and `mvn test` passes 235 tests with no failures, no errors, and 2 existing skips under JDK 21.
