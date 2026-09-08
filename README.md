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

## Releasing

Releases are published to GitHub Packages by the `Maven Package` GitHub Actions workflow.
The workflow publishes every push, including snapshot versions, while pushing a tag by itself does not publish a package.

To release a new version, first merge the required changes into `main` and check out a clean, up-to-date `main` branch.
Set the release version in the root `pom.xml`, replacing `1.2.2` below with the version being released:

```bash
mvn versions:set \
  -DnewVersion=1.2.2 \
  -DgenerateBackupPoms=false
```

Run the build and tests, then verify that the only intended change is the version update:

```bash
mvn clean verify
git status --short
git diff
```

Commit and push the release version:

```bash
git commit -am "Release 1.2.2"
git push origin main
```

Wait for the `Maven Package` workflow to succeed and confirm that `com.ecpnv.openrewrite:jdo2jpa:1.2.2` is available in GitHub Packages.
Tag the release commit and push the tag:

```bash
git tag v1.2.2
git push origin v1.2.2
```

Creating the optional GitHub Release is the only manual GitHub UI step.
In the GitHub UI:

1. Open **Releases**.
2. Select **Draft a new release**.
3. Select the existing `v1.2.2` tag.
4. Add a title and release notes.
5. Publish the release.

Alternatively, create it with the GitHub CLI:

```bash
gh release create v1.2.2 \
  --title "v1.2.2" \
  --generate-notes
```

The GitHub Release is separate from the Maven package and does not publish, convert, or promote package versions.
A snapshot package is not converted into a release package; pushing the non-snapshot version to `main` builds and deploys a separate package version.

Advance `main` to the next snapshot version:

```bash
mvn versions:set \
  -DnewVersion=1.2.3-SNAPSHOT \
  -DgenerateBackupPoms=false
git commit -am "Prepare dev"
git push origin main
```

The final push publishes the new snapshot version because the workflow also deploys snapshot versions from `main`.
Pushes to other branches are published under a unique version containing the branch name, workflow run number, and commit SHA.
