/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.java.MoveAnnotationsToAttribute;


/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class IndexesTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(PARSER)
                .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Index");
    }

    /**
     * The `moveIndexConstraintAnnotation` method is a test case validating the transformation and migration of
     * `javax.persistence.Index` annotations into an `indexes` attribute of the `javax.persistence.Table` annotation
     * within an annotated class declaration.
     * <p>
     * This method utilizes the `MoveAnnotationsToAttribute` recipe to achieve the following:
     * <p>
     * - Locates the `@Index` annotation applied to a class.
     * - Integrates the `@Index` definitions into the `indexes` attribute of the `@Table` annotation,
     * ensuring adherence to proper syntax and imports.
     * - Retains other existing attributes of the `@Table` annotation and consolidates the changes.
     * - Removes the standalone `@Index` annotation from the class after its values are successfully migrated.
     * <p>
     * Key Features:
     * - Verifies the correct transformation of annotations by asserting the resulting structure of the class.
     * - Ensures that the `@Table` annotation is accurately augmented with the `indexes` attribute, incorporating
     * all existing `@Index` instances.
     * - Confirms clean and syntactically valid code by removing unnecessary annotations and maintaining appropriate imports.
     * <p>
     * Preconditions:
     * - The target class contains one or more `@Index` annotations and an existing `@Table` annotation.
     * <p>
     * Postconditions:
     * - The `@Index` annotations are embedded within the `indexes` attribute of the `@Table` annotation.
     * - The original `@Index` annotations are removed from the class.
     * <p>
     * Dependencies:
     * - The `MoveAnnotationsToAttribute` recipe is used to perform the migration.
     * - Constants for fully qualified annotation names (`Constants.Jpa.INDEX_ANNOTATION_FULL` and `Constants.Jpa.TABLE_ANNOTATION_FULL`)
     * and the `indexes` attribute name (`Constants.Jpa.TABLE_ARGUMENT_INDEXES`) are leveraged to guide the transformation.
     */
    @DocumentExample
    @Test
    void moveIndexConstraintAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.INDEX_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_INDEXES)),
                //language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                @Index(name = "SomeEntityNameIndex", columnList = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * The `moveIndexConstraintAnnotationWithNoPreexistingTableAnnotation` method is a test case designed
     * to validate the behavior of moving `@Index` annotations into the `indexes` attribute of the
     * `@javax.persistence.Table` annotation when there is no preexisting `@Table` annotation in the
     * class being processed.
     * <p>
     * This method performs the following:
     * <p>
     * - Applies the `MoveAnnotationsToAttribute` recipe, which facilitates the migration of
     * `@Index` annotations into the `indexes` attribute of the `@Table` annotation in classes.
     * - Ensures that a `@Table` annotation is added to the class if it does not already exist.
     * - Validates the transformation process by comparing the input and expected output class declarations.
     * - Verifies that the `indexes` attribute of the newly added `@Table` annotation is populated with
     * the corresponding `@Index` annotations, and that the standalone `@Index` annotations are removed.
     * - Ensures the proper imports are added for the `javax.persistence.Table` and `javax.persistence.Index`
     * annotations.
     * <p>
     * Preconditions:
     * - The target class contains one or more `@Index` annotations and does not have an existing
     * `@Table` annotation.
     * <p>
     * Postconditions:
     * - A new `@Table` annotation is added to the class.
     * - The `@Index` annotations are embedded within the `indexes` attribute of the `@Table` annotation.
     * - The standalone `@Index` annotations are removed.
     * <p>
     * Dependencies:
     * - The `MoveAnnotationsToAttribute` recipe is used for the transformation.
     * - Constants such as `Constants.Jpa.INDEX_ANNOTATION_FULL`, `Constants.Jpa.TABLE_ANNOTATION_FULL`,
     * and `Constants.Jpa.TABLE_ARGUMENT_INDEXES` are utilized to define the fully qualified names
     * and attribute name during the migration process.
     */
    @DocumentExample
    @Test
    void moveIndexConstraintAnnotationWithNoPreexistingTableAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.INDEX_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_INDEXES)),
                //language=java
                java(
                        """
                                import javax.persistence.Index;
                                
                                @Index(name = "SomeEntityNameIndex", columnList = {"name"})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                
                                
                                @javax.persistence.Table(indexes = {@javax.persistence.Index(name = "SomeEntityNameIndex",
                                        columnList = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * The `moveMultipleIndexConstraintAnnotation` method is a test case aimed at validating the transformation
     * of multiple `@Index` annotations into the `indexes` attribute of the `@javax.persistence.Table` annotation
     * within a class declaration.
     * <p>
     * This method performs the following tasks:
     * <p>
     * - Utilizes the `MoveAnnotationsToAttribute` recipe to migrate multiple `@Index` annotations into the
     * `indexes` attribute of the existing `@Table` annotation.
     * - Ensures that all `@Index` annotations are properly grouped under a singular `indexes` attribute of the
     * `@Table` annotation while preserving correct syntax and semantics.
     * - Verifies that the standalone `@Index` annotations are removed from the class after their migration.
     * - Asserts the correctness of the transformation by comparing the generated code structure with
     * expected outcomes.
     * <p>
     * Preconditions:
     * - The target class contains two or more `@Index` annotations and an existing `@Table` annotation.
     * <p>
     * Postconditions:
     * - The `@Index` annotations are embedded within the `indexes` attribute of the `@Table` annotation.
     * - All standalone `@Index` annotations are removed.
     * - The integrity of the class structure, including imports and other annotations, is maintained.
     * <p>
     * Dependencies:
     * - The `MoveAnnotationsToAttribute` recipe is used for executing the migration process.
     * - Fully qualified constants, including `Constants.Jpa.INDEX_ANNOTATION_FULL`, `Constants.Jpa.TABLE_ANNOTATION_FULL`,
     * and `Constants.Jpa.TABLE_ARGUMENT_INDEXES`, are referenced to guide the transformation accurately.
     */
    @DocumentExample
    @Test
    void moveMultipleIndexConstraintAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.INDEX_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_INDEXES)),
                //language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                @Index(name = "SomeEntityIdIndex", columnList = {"id"})
                                @Index(name = "SomeEntityNameIndex", columnList = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( name = "SomeEntityIdIndex",
                                columnList = {"id"}), @javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * Validates the transformation of a `@Index` annotation into the `indexes` attribute
     * of a `@Table` annotation in a class declaration.
     * <p>
     * This method ensures migration of the `@Index` annotation to be part of the `indexes`
     * attribute inside the `@Table` annotation while maintaining the integrity of other
     * existing annotations and imports. The standalone `@Index` annotation is removed after
     * its transformation.
     * <p>
     * Key Features:
     * - Converts `@Index` to an inline definition within the `indexes` attribute of the `@Table` annotation.
     * - Ensures proper syntax and semantically valid annotation structure.
     * - Removes redundant `@Index` annotations after they are migrated.
     * - Validates the accuracy of the updated structure by comparing it to the expected output.
     * <p>
     * Preconditions:
     * - A `@Index` annotation exists within the class body, alongside a `@Table` annotation where it will be incorporated.
     * <p>
     * Postconditions:
     * - The `@Index` definition is moved into the `indexes` attribute of the `@Table` annotation.
     * - The `@Index` annotation is removed from its previous standalone position.
     * - Proper imports for the modified annotations are retained or introduced.
     * <p>
     * Dependencies:
     * - Relies on the `MoveAnnotationsToAttribute` recipe for modifying and relocating annotations.
     * - Utilizes constants for annotation names and attributes to guide the transformation process.
     */
    @DocumentExample
    @Test
    void moveIndexAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.jdo.annotations.Index;
                                
                                @Index(name = "SomeEntityNameIndex", members = {"name"}, table = "table", 
                                unique = "false", columns = {"col1", "col2"}, extensions = "")
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = "name")})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * The `moveIndexAnnotationFromIndexes` method is a test case designed to validate the migration
     * of `@javax.jdo.annotations.Index` annotations in a JDO entity class to the `indexes` attribute
     * of the `@javax.persistence.Table` annotation in a JPA entity class.
     * <p>
     * This method ensures the proper transformation of the entity class from JDO to JPA by:
     * - Converting standalone `@Index` annotations into inline definitions under the `indexes` attribute
     * of the `@Table` annotation.
     * - Ensuring that all the indices specified for the JDO entity are preserved and correctly formatted
     * as part of the JPA entity class.
     * - Retaining existing `@PersistenceCapable` schema configurations and translating it to
     * the JPA equivalent using `@Table`'s `schema` attribute.
     * - Removing the original standalone `@Index` annotations after migration to prevent redundancy.
     * - Ensuring that imports are updated to reflect the transition from JDO to JPA annotations.
     * <p>
     * Preconditions:
     * - The input class must be annotated with `@PersistenceCapable`.
     * - One or more `@Index` annotations exist in the JDO entity class.
     * <p>
     * Postconditions:
     * - `@PersistenceCapable` is replaced with equivalent `@Entity` and `@Table` annotations.
     * - The `indexes` attribute in the `@Table` annotation contains all the converted `@Index` annotations.
     * - Redundant `@Index` annotations from JDO are removed.
     * - Proper imports for JPA annotations are added and imports for JDO annotations are excluded.
     * <p>
     * Dependencies:
     * - Relies on the `MoveAnnotationsToAttribute` recipe to handle the transformation of index
     * annotations into the `indexes` attribute of the `@javax.persistence.Table` annotation.
     * - Assumes the presence of utilities or constants for managing fully qualified names of
     * JDO and JPA annotations and their attributes.
     */
    @DocumentExample
    @Test
    void moveMultipleIndexAnnotations() {
        rewriteRun(
                spec -> spec.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.Index;
                                
                                @PersistenceCapable(schema = "schemaName")
                                @Index(name = "Person__name__IDX", members = {"firstName", "lastName"})
                                @Index(name = "Person__email__IDX", members = {"email"})
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """,
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                @Table( schema = "schemaName", indexes = {@Index( name = "Person__name__IDX",
                                columnList = "firstName, lastName"), @Index( name = "Person__email__IDX",
                                columnList = "email")})
                                public class SomeEntity extends EntityAbstract {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """
                )
        );
    }


    /**
     * This method tests the refactoring process of migrating JDO-specific annotations
     * (`@Indices` and `@Index` from `javax.jdo.annotations`) to their equivalent JPA-based
     * annotations (`@Table` and `@Index` from `javax.persistence`). The goal is to ensure
     * that persistent entity definitions annotated using JDO are correctly transformed
     * into JPA entity representations.
     * <p>
     * Specifically, it verifies the following:
     * - JDO's `@PersistenceCapable` annotation with its schema attribute is successfully
     * replaced by JPA's `@Entity` and `@Table` annotations, preserving the schema name.
     * - JDO's `@Indices` and contained `@Index` entries are converted into JPA's `@Table`
     * annotation with an `indexes` attribute containing equivalent `@Index` definitions.
     * - Capable of handling class-level annotations and converting them without affecting
     * field definitions.
     * <p>
     * The test uses OpenRewrite to apply the required recipe transformation, ensuring the
     * input JDO-based code is rewritten into the expected JPA-compliant output.
     */
    @DocumentExample
    @Test
    void moveMultipleIndexAnnotationsFromIndices() {
        rewriteRun(
                spec -> spec.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;
                                
                                @PersistenceCapable(schema = "schemaName")
                                @Indices({
                                  @Index(name = "Person__name__IDX", members = {"firstName", "lastName"}),
                                  @Index(name = "Person__email__IDX", members = {"email"}),
                                })
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """,
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                @Table( schema = "schemaName", indexes = {@Index( name = "Person__name__IDX",
                                columnList = "firstName, lastName"), @Index( name = "Person__email__IDX",
                                columnList = "email")})
                                public class SomeEntity extends EntityAbstract {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void moveMultipleIndexAnnotationsFromIndices2() {
        rewriteRun(
                spec -> spec.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Index"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;
                                
                                @Indices({
                                        @Index(
                                                name = "CommChannelRole_comm_channel_type_IDX",
                                                members = { "communication", "channel", "type" }
                                        ),
                                        @Index(
                                                name = "Communication_channel_comm_type_IDX",
                                                members = { "channel", "communication", "type" }
                                        ),
                                        @Index(
                                                name = "CommChannelRole_comm_type_channel_IDX",
                                                members = { "communication", "type", "channel" }
                                        ),
                                        @Index(
                                                name = "Communication_channel_type_comm_IDX",
                                                members = { "channel", "type", "communication" }
                                        ),
                                })
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """,
                        """
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                @javax.persistence.Table(indexes = {@javax.persistence.Index(name = "Person__name__IDX",
                                        columnList = "firstName, lastName"), @javax.persistence.Index(name = "Person__email__IDX",
                                        columnList = "email")})
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """
                )
        );
    }

    /**
     * Refactors a Java class to move `@Index` annotations from fields and methods to the class level,
     * adhering to JPA-compatible syntax. This transformation ensures that index definitions are
     * part of the `@Table` annotation using the `indexes` attribute.
     * <p>
     * The method primarily handles:
     * - Moving `@Index` annotations from fields or methods to the enclosing class.
     * - Mapping index details such as name, columnList, and unique properties to the JPA equivalent.
     * - Transforming the code structure while preserving semantic equivalence.
     * <p>
     * This is required when converting codebases from frameworks like JDO to JPA.
     * <p>
     * Preconditions:
     * - The class and its members must contain valid annotations as per the provided language/java syntax.
     * <p>
     * Postconditions:
     * - All `@Index` annotations on fields and methods are moved to the `indexes` attribute of the `@Table` annotation on the class.
     * <p>
     * Test Behavior:
     * - Ensures annotations are correctly transformed without altering unrelated parts of the class.
     * - Validates correct mapping and usage of attributes for `@Table` and `@Index`.
     */
    @DocumentExample
    @Test
    void moveIndexFromFieldToClass() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.jdo.annotations.Index;
                                import java.util.Date;
                                
                                @Index(name = "SomeEntityNameIndex", members = {"name"}, table = "table", 
                                unique = "false", columns = {"col1", "col2"}, extensions = "")
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        @Index(name = "SomeEntityIdIndex", unique = "true")
                                        private int id;
                                        private String name;
                                        @Index(name = "SomeEntityDateIndex")
                                        public Date getBirthDate(){ return null;}
                                }
                                """,
                        """
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                import java.util.Date;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( columnList = "id",
                                name = "SomeEntityIdIndex",
                                unique = "true"), @javax.persistence.Index( columnList = "birthDate",
                                name = "SomeEntityDateIndex"), @javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = "name")})
                                public class SomeEntity {
                                """ + "        " + """
                                
                                        private int id;
                                        private String name;
                                """ + "        " + """
                                
                                        public Date getBirthDate(){ return null;}
                                }
                                """
                )
        );
    }
}
