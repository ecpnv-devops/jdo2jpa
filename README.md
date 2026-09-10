# JDO to JPA migration

This project provides JDO to JPA migration recipes using Open Rewrite.
> This project is sponsored by [Eurocommercial Properties](https://www.eurocommercialproperties.com/)

The most common JDO Patterns are supported, but there might be some less common or edge-cases that are not (yet) supported.

It supports:
* root entity that is used to extend all entities, if you don't want this then fork and change the recipe or write an recipe to run after this migration is finished
* [migration of](src/main/resources/META-INF/rewrite/datanucleus-jdo-to-jpa-eclipselink.yml)
  - @PersistenceCapable
  - @Unique
  - @Index
  - @Persistent
  - @Column
  - @Inheritance
  - @Discriminator
  - @Query
  - @PrimaryKey based on a generic root entity
* additional recipes specific for Datanucleus as JDO implementation
* additional recipes specific for EclipseLink as JPA implementation
* optional recipes specific for migrating Apache Causeway from JDO to JPA
  - Using Causeway specific entity listeners
  - Migrating your layouts
  - Migrating Causeway specific JDO classes to JPA
* Best practices to generate and modify your entity for one to many associations

It provides:
* Many new generic recipes that can be used in your own recipes, see [com.ecpnv.openrewrite.java](src/main/java/com/ecpnv/openrewrite/java)
  * For example [AddAnnotationConditionally](src/main/java/com/ecpnv/openrewrite/java/AddAnnotationConditionally.java) that will only add an annotation when a regular expression matches an annotation on a field, class or method, optionally matches the modifier(s), inherited classes and/or kind
  * Or for removing xml tags use [com.ecpnv.openrewrite.xml.RemoveXmlTag](src/main/java/com/ecpnv/openrewrite/xml/RemoveXmlTag.java)
* [RewriteUtils](src/main/java/com/ecpnv/openrewrite/util/RewriteUtils.java) with some convience methods

Not supported:
* Hibernate should be supported but is not (yet) tested.
* Hibernate specific patterns or annotations are not supported.

## Fetch and LOB metadata

The `v2x.Persistent` recipe converts relationships before translating scalar metadata and removes JDO persistence metadata only after both stages.
For scalar fields and properties, explicit JDO `defaultFetchGroup = "false"` becomes `@Basic(fetch = FetchType.LAZY)`.
Explicit true, omitted metadata, and declarations without `@Persistent` retain JPA's eager basic default.
Relationships, collections, and maps remain governed by the association recipes and do not receive `@Basic`.
JPA lazy basic fetching is a provider hint and can require provider features such as EclipseLink weaving.

The `v2x.Column` recipe translates explicit JDO `jdbcType = "BLOB"` and `jdbcType = "CLOB"` metadata to `@Lob` before removing JDO-only column attributes.
LOB classification does not by itself imply lazy fetching.
Java types, column names, converters, and unrelated annotation attributes are preserved.

## Releasing

Releases are published to GitHub Packages by the `Maven Package` workflow.
The workflow publishes every push, including snapshot versions, while pushing a tag by itself does not publish a package.

Use the release helper script to keep the steps parameterized and easy to rerun:

```bash
./scripts/release.sh --help
```

1. Prepare the release version on `main`:

   ```bash
   ./scripts/release.sh prepare 1.2.3
   ```

2. Build and verify the release candidate:

   ```bash
   ./scripts/release.sh verify 1.2.3
   ```

3. Commit and push the release version:

   ```bash
   ./scripts/release.sh publish 1.2.3
   ```

   Wait for the `Maven Package` workflow to succeed and confirm that `com.ecpnv.openrewrite:jdo2jpa:1.2.3` is available in GitHub Packages.

4. Tag and push the release tag:

   ```bash
   ./scripts/release.sh tag 1.2.3
   ```

5. Create the GitHub Release (manual UI or via the GitHub CLI when it is installed):

   ```bash
   ./scripts/release.sh github-release 1.2.3
   ```

   If `gh` is not installed, the script prints the equivalent manual steps for the GitHub UI: open **Releases**, select **Draft a new release**, pick the existing `v1.2.3` tag, add a title and notes, then publish.

6. Advance `main` to the next snapshot version:

   ```bash
   ./scripts/release.sh snapshot 1.2.4
   ```

The GitHub Release is separate from the Maven package and does not publish, convert, or promote package versions.
A snapshot package is not converted into a release package; pushing the non-snapshot version to `main` builds and deploys a separate package version.

The final snapshot push publishes the new snapshot version because the workflow also deploys snapshot versions from `main`.
Pushes to other branches are published under a unique version containing the branch name, workflow run number, and commit SHA.
