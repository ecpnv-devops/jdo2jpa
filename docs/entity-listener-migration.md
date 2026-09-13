# Entity superclass and listener migration

## Completed comparison and open acceptance gates

The candidate6 comparison completed regeneration for 151 modules and clean installation of all 148 JPA reactor modules on both sides, using the recorded symmetric consumer corrections.
See `entity-listener-comparison-handoff.md` and `entity-listener-evidence/` for the complete reviewed differences and commands.
The turnover module is repeat-stable with exactly one annotation-defined configured listener per target entity.
Full-file idempotence remains open for baseline-reproduced cleanup changes in three other affected modules, and runtime rollout acceptance remains consumer-owned and unverified.

## Generated-source compatibility notice

This change intentionally changes generated Java source even though the existing three-argument `ExtendWithClassForAnnotationConditionally` constructor and legacy regex YAML properties remain available.
The standard datastore rule now requires an explicitly declared DATASTORE enum value and recognises compact, commented, multiline, qualified, and static-imported forms.
Bare `@PersistenceCapable` no longer gains a superclass through the old regex visitor's accidental empty-argument match; review those classes' identity mappings before adopting regenerated output.
An explicit APPLICATION value or an omitted identityType does not match the structured rule.
The standalone legacy regex recipe retains its previous matching contract.

The comparison also exposed and corrected nested `EntityScan` placement.
Only types that themselves declare `ComponentScan` receive the annotation; enclosing interfaces or helpers do not qualify merely because they contain a nested configuration.
Changes to nested configurations are preserved when an enclosing configuration also changes.
This intentional configuration correction accounts for four of the ten differing files in the completed comparison.

Concrete JPA entities receive the configured Isis-era listener only if they have neither an explicit entity-level listener declaration nor an effective inherited registration.
Explicit custom-only and empty declarations are preserved as overrides.
Abstract entities are not automatically annotated.
Superclass-listener exclusions and ancestors scheduled to receive a listener in the same source set are accounted for.
Test sources also see planned main-source ancestors in the same identified project; main sources never borrow test declarations, and different project versions remain isolated.
By default, the generated annotation retains the existing fully qualified Isis listener spelling so downstream listener-replacement recipes continue to work.
Consumers can set the recipe-environment property `jdo2jpa.entityListenerClass` to their final integration listener FQN.
For Estatio, set the Maven project property to `org.estatio.base.prod.integration.OrmEntityListener` before loading the recipes.
Using the final listener consistently avoids treating an already-replaced inherited listener as unrelated during repeat runs; arbitrary custom listeners are not automatically assumed equivalent.

Listener decisions use a refreshed scan after the first editing cycle because OpenRewrite scans all files before applying preceding recipes.
This prevents duplicate listeners when a later-visited parent becomes an entity or acquires a listener-bearing superclass during that cycle.
Keep the normal three-cycle execution budget; do not force a single-cycle run.

Superclass insertion now preserves the attributed supertype as well as the extends expression.
The annotation-attribute propagation helper uses qualified-name keys and the highest annotated ancestor, preventing newly visible framework superclasses from changing the existing entity inheritance strategy.

## Maven integration

The resolver scans Maven POM resolution markers and associates Java files with the nearest module POM and source set.
It reads the selected compile/provided dependency artifacts from the configured local Maven repository, including resolved versions, classifiers, timestamped snapshots, and transitive annotation dependencies.
It does not download dependencies or use the plugin JVM's runtime classpath as the application classpath.
Missing required artifacts cause metadata resolution to fail rather than being interpreted as an unannotated parent.
Existing full source/reference types take precedence over dependency fallback, and shallow JavaSourceSet entries are never authoritative inheritance metadata.

When Maven parsing is disabled, the resolver can instead use `JavaSourceSet.getGavToTypes()` to identify the already-selected artifact coordinates.
It locates those coordinates in the configured local repository and checks each candidate JAR against the indexed class names before reading bytecode.
Missing artifacts and different matching classifier/snapshot bytecode are rejected rather than guessed.
Packaging-only variants are accepted only when every class entry and its bytecode match and the `Class-Path` and `Multi-Release` manifest settings agree.
The metadata probe uses the classpath without service-discovered annotation processing, so processor-registration resources and module-path naming do not select different metadata.
This covers QueryDSL's normal and JDO APT packaging without weakening checks on superclass bytecode.
This uses the index for artifact identity, never for modifiers or annotations.

The pinned Estatio profiles can retain `<skipMavenParsing>true</skipMavenParsing>` when the source-set artifact index is available, avoiding expensive independent Maven-model re-resolution.
If coordinates are unavailable or ambiguous, enable Maven parsing or provide complete attributed source/reference types; apply the same harness setting to both comparison sides.
OpenRewrite 8.47.3 recognises custom local-repository roots for its GAV index when the root is named `repository` or contains `repository.xml`; the isolated comparison repositories use that marker.
Unresolvable metadata still produces the typed hierarchy error.

Cross-module guarantees require already-migrated parent artifacts.
Regenerate, compile, and install parents before parsing children, using separate baseline and candidate local repositories.
A resolvable stale binary cannot be identified reliably from its type name, so record artifact provenance and do not claim whole-project duplicate avoidance without that ordering.
No additional public classpath recipe option or global recipe-specific system property is required.

Keep the original JDO annotation API explicitly available in each rewrite profile while parents are progressively installed as JPA artifacts.
In the pinned Estatio harness this is `javax.jdo:jdo-api:3.2.1` in `rewrite`, `rewrite_post`, and `rewrite_local`, not in the JPA compilation profile.
Otherwise dependency-reduced parent POMs can remove JDO attribution from subsequent inputs and leave annotations unconverted.
Use a clean JPA build after rewriting each module so stale JDO-generated QueryDSL classes cannot shadow the migrated parent's metamodel.
Use the JPA reactor for JPA installation and the original JDO reactor scope for rewriting; their active module lists differ.

## Errors and partial output

`UnresolvedEntityHierarchyException` identifies the entity, unresolved parent/reference, recipe, and remediation.
The error is delivered through the execution context's error handler.
When the handler returns, the failing recipe leaves the affected class unchanged from its own input; earlier recipe edits are not rolled back.
A throwing handler follows the runner's normal error propagation.

Run Maven migration commands in disposable worktrees through the supplied guard when the Maven execution context may log errors without failing the process:

```sh
python3 /path/to/jdo2jpa/scripts/check-rewrite-run.py --log /path/to/run.log -- \
  mvn -Prewrite rewrite:runNoFork -Ddisable_dn -DskipTests
```

The guard returns nonzero for a failed command or a logged unresolved-hierarchy error, including when Maven itself exits zero.
It does not publish or roll back generated files; reject all partial output in the disposable worktree after a nonzero exit.
Repeat the guard for each configured rewrite pass and do not proceed to publication after a failed pass.

## Validation and consumer handoff

The production regression suite covers Maven-parsed module metadata without supplying paths to the recipe, same-FQN parents at different versions, dependency annotation arrays, source-order independence, explicit overrides, exclusions, typed failures, and composed turnover-shaped migrations with schema/table constants and repeat-run checks.
The pinned production-scale comparison uses Estatio prod `4dd98637d572240ff99cb8c998590074c27c839d` on both sides.
JPA `907c723889d3306352c2e50cfbbae3df6406d949` is reconciliation-only.
Record artifact coordinates, checksums, source revisions, exact commands, complete generated-tree differences, affected-class inventory, and baseline/candidate build results before acceptance.

Consumer rollout additionally requires a non-production F04/Mallcomm scenario exercising applicable persist/update/remove operations for both turnover entities.
Record expected and actual callback counts and demonstrate exactly-once integration without inherited duplication.
The maintained/excluded `BackgroundCommandsOrchestration` source is not repaired by these recipes and requires a separately owned listener fix and runtime verification.
Missing consumer runtime evidence remains a rollout handoff, not a passing callback test.
