# Entity superclass and listener migration

## Generated-source compatibility notice

This change intentionally changes generated Java source even though the existing three-argument `ExtendWithClassForAnnotationConditionally` constructor and legacy regex YAML properties remain available.
The standard datastore rule now requires an explicitly declared DATASTORE enum value and recognises compact, commented, multiline, qualified, and static-imported forms.
Bare `@PersistenceCapable` no longer gains a superclass through the old regex visitor's accidental empty-argument match; review those classes' identity mappings before adopting regenerated output.
An explicit APPLICATION value or an omitted identityType does not match the structured rule.
The standalone legacy regex recipe retains its previous matching contract.

Concrete JPA entities receive the configured Isis-era listener only if they have neither an explicit entity-level listener declaration nor an effective inherited registration.
Explicit custom-only and empty declarations are preserved as overrides.
Abstract entities are not automatically annotated.
Superclass-listener exclusions and ancestors scheduled to receive a listener in the same source set are accounted for.
The generated annotation retains the existing fully qualified Isis listener spelling so downstream listener-replacement recipes continue to work.

Superclass insertion now preserves the attributed supertype as well as the extends expression.
The annotation-attribute propagation helper uses qualified-name keys and the highest annotated ancestor, preventing newly visible framework superclasses from changing the existing entity inheritance strategy.

## Maven integration

The resolver scans Maven POM resolution markers and associates Java files with the nearest module POM and source set.
It reads the selected compile/provided dependency artifacts from the configured local Maven repository, including resolved versions, classifiers, timestamped snapshots, and transitive annotation dependencies.
It does not download dependencies or use the plugin JVM's runtime classpath as the application classpath.
Missing required artifacts cause metadata resolution to fail rather than being interpreted as an unannotated parent.
Existing full source/reference types take precedence over dependency fallback, and shallow JavaSourceSet entries are never authoritative inheritance metadata.

**Enable Maven parsing for dependency fallback.**
The pinned Estatio profiles set `<skipMavenParsing>true</skipMavenParsing>`; change this to `false` in the migration harness for both baseline and candidate runs.
Changing only the candidate harness would invalidate a same-input comparison.
When Maven metadata is deliberately excluded, only already-attributed source/reference types and genuinely resolved existing template types are available; absent metadata produces the unresolved-hierarchy error.

Cross-module guarantees require already-migrated parent artifacts.
Regenerate, compile, and install parents before parsing children, using separate baseline and candidate local repositories.
A resolvable stale binary cannot be identified reliably from its type name, so record artifact provenance and do not claim whole-project duplicate avoidance without that ordering.
No additional public classpath recipe option or global recipe-specific system property is required.

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
