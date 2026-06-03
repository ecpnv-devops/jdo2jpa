# JDO to JPA migration

This project provides JDO to JPA migration recipes using Open Rewrite.
> This project is sponsered by [Eurocommercial Properties](https://www.eurocommercialproperties.com/)

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
