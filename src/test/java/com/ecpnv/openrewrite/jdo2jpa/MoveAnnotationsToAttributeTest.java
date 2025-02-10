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
class MoveAnnotationsToAttributeTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Unique");
    }

    /**
     * This test method verifies the functionality of the `MoveAnnotationsToAttribute` recipe.
     * The recipe is applied to ensure that a specific annotation (`@UniqueConstraint`) is correctly moved
     * into an attribute (`uniqueConstraints`) of another annotation (`@Table`).
     * <p>
     * Functionality tested:
     * - Identifies the `@UniqueConstraint` annotation on a class.
     * - Moves the `@UniqueConstraint` into the `uniqueConstraints` attribute of the `@Table` annotation.
     * - Ensures proper formatting and structure after the migration.
     * - Validates that the source annotation is removed from its original location and correctly added as an attribute.
     * - Confirms appropriate handling of imports for the modified annotations.
     * <p>
     * Preconditions:
     * - The source annotation (`@UniqueConstraint`) is present and correctly defined on the class.
     * - The target annotation (`@Table`) exists and may or may not already define a `uniqueConstraints` attribute.
     * <p>
     * Postconditions:
     * - The `@UniqueConstraint` annotation is removed from the class level.
     * - The `uniqueConstraints` attribute is created or updated within the `@Table` annotation.
     * - The resulting class annotations include the modified `@Table` annotation incorporating the migrated data.
     * <p>
     * Expected behavior:
     * - The test creates an initial Java class input with the `@UniqueConstraint` annotation defined outside of `@Table`.
     * - The recipe adjusts the input such that the `@UniqueConstraint` annotation is embedded as an attribute of the `@Table` annotation.
     * - The output properly reflects the migration, ensuring correctness and adherence to Java syntax.
     */
    @DocumentExample
    @Test
    void moveUniqueConstraintAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.UNIQUE_CONSTRAINT_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_UNIQUE_CONSTRAINTS)),
                //language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                @UniqueConstraint(name = "SomeEntityNameUnique", columnNames = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", uniqueConstraints = {@javax.persistence.UniqueConstraint( name = "SomeEntityNameUnique",
                                columnNames = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * This test method verifies the functionality of the `MoveAnnotationsToAttribute` recipe
     * when there is no pre-existing `@Table` annotation on the class where the `@UniqueConstraint` annotation is present.
     * <p>
     * Functionality tested:
     * - Identifies the `@UniqueConstraint` annotation on a class.
     * - Creates a `@Table` annotation if it does not already exist.
     * - Moves the `@UniqueConstraint` annotation into the `uniqueConstraints` attribute of the newly created `@Table` annotation.
     * - Ensures proper formatting and structure after the modification.
     * - Validates that the source annotation is removed from its original location and correctly embedded within the new `@Table` annotation.
     * - Confirms appropriate handling of imports for the modified annotations.
     * <p>
     * Preconditions:
     * - The source annotation (`@UniqueConstraint`) is present and correctly defined on the class.
     * - The target annotation (`@Table`) is not yet defined on the class.
     * <p>
     * Postconditions:
     * - The `@UniqueConstraint` annotation is removed from the class level.
     * - A new `@Table` annotation is created with the `uniqueConstraints` attribute containing the migrated
     * `@UniqueConstraint` data.
     * - The resulting class includes the new `@Table` annotation with the embedded `uniqueConstraints` attribute.
     * <p>
     * Expected behavior:
     * - The test starts with an initial Java class that lacks the `@Table` annotation but contains the
     * `@UniqueConstraint` annotation defined at the class level.
     * - After applying the recipe, the output reflects the migration by creating a `@Table` annotation and embedding
     * the `@UniqueConstraint` data within its `uniqueConstraints` attribute.
     * - The output ensures correctness, adherence to Java syntax, and proper formatting of the resulting annotations.
     */
    @DocumentExample
    @Test
    void moveUniqueConstraintAnnotationWithNoPreexistingTableAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.UNIQUE_CONSTRAINT_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_UNIQUE_CONSTRAINTS)),
                //language=java
                java(
                        """
                                import javax.persistence.UniqueConstraint;
                                
                                @UniqueConstraint(name = "SomeEntityNameUnique", columnNames = {"name"})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                @javax.persistence.Table(uniqueConstraints = {@javax.persistence.UniqueConstraint(name = "SomeEntityNameUnique",
                                        columnNames = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * This test method verifies the functionality of the `MoveAnnotationsToAttribute` recipe when handling multiple
     * `@UniqueConstraint` annotations. The test ensures that multiple `@UniqueConstraint` annotations are correctly
     * moved into the `uniqueConstraints` attribute of the `@Table` annotation.
     * <p>
     * Functionality tested:
     * - Identifies multiple `@UniqueConstraint` annotations present on a class.
     * - Moves all identified `@UniqueConstraint` annotations into the `uniqueConstraints` attribute of the `@Table` annotation.
     * - Ensures proper formatting, correctness of structure, and adherence to Java syntax after the migration.
     * - Validates that the source `@UniqueConstraint` annotations are removed from their original location.
     * - Confirms appropriate handling of imports for the modified annotations and migration.
     * <p>
     * Preconditions:
     * - The source annotations (`@UniqueConstraint`) are correctly defined on the class at the top level.
     * - The target annotation (`@Table`) exists, whether or not it already defines a `uniqueConstraints` attribute.
     * <p>
     * Postconditions:
     * - The `@UniqueConstraint` annotations are removed from the class level.
     * - The `uniqueConstraints` attribute of the `@Table` annotation is created or updated to include the migrated `@UniqueConstraint` data.
     * - The resulting annotations on the class include a correctly formatted and complete `@Table` annotation with all `@UniqueConstraint` data embedded.
     * <p>
     * Expected behavior:
     * - The recipe processes a Java class containing multiple `@UniqueConstraint` annotations defined separately.
     * - The recipe modifies the input such that all the `@UniqueConstraint` annotations are embedded as a single attribute
     * (`uniqueConstraints`) within the `@Table` annotation.
     * - The output reflects this migration accurately, ensuring correctness, proper formatting, and syntactic validity.
     */
    @DocumentExample
    @Test
    void moveMultipleUniqueConstraintAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.UNIQUE_CONSTRAINT_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_UNIQUE_CONSTRAINTS)),
                //language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                @UniqueConstraint(name = "SomeEntityIdUnique", columnNames = {"id"})
                                @UniqueConstraint(name = "SomeEntityNameUnique", columnNames = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", uniqueConstraints = {@javax.persistence.UniqueConstraint( name = "SomeEntityIdUnique",
                                columnNames = {"id"}), @javax.persistence.UniqueConstraint( name = "SomeEntityNameUnique",
                                columnNames = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * Tests the functionality of moving the `@Unique` annotation attributes into the `uniqueConstraints` attribute of
     * the `@Table` annotation while preserving the original structure and ensuring adherence to proper Java syntax.
     *
     * Functionality verified:
     * - Identifies the `@Unique` annotation on a class and retrieves its relevant information (e.g., name, members, table).
     * - Moves the `@Unique` annotation attributes to the `uniqueConstraints` attribute of the `@Table` annotation.
     * - Ensures proper formatting and structure of the resulting `@Table` annotation.
     * - Validates that the `@Unique` annotation is removed after migration.
     * - Confirms appropriate handling of imports for the modified annotations.
     *
     * Preconditions:
     * - The source `@Unique` annotation is present on the class and correctly defined.
     * - The target `@Table` annotation is already present and may or may not define a `uniqueConstraints` attribute.
     *
     * Postconditions:
     * - The `@Unique` annotation is removed from its original location.
     * - The `uniqueConstraints` attribute of the `@Table` annotation is created or updated with the migrated data
     *   from the `@Unique` annotation.
     * - The resulting annotations include a correctly formatted and consistent `@Table` annotation with the embedded
     *   constraints from `@Unique`.
     *
     * Expected outcome:
     * - The test starts with an input Java class containing a `@Unique` annotation along with a `@Table` annotation.
     * - The recipe modifies the input such that the `@Unique` annotation's data is migrated as a part of the
     *   `uniqueConstraints` attribute within the `@Table` annotation.
     * - The modified class reflects the changes accurately and maintains syntactic correctness and proper formatting.
     */
    @DocumentExample
    @Test
    void moveUniqueAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.jdo.annotations.Unique;
                                
                                @Unique(name = "SomeEntityNameUnique", members = {"name"}, table = "table", 
                                deferred = "def", columns = {"col1", "col2"}, extensions = "")
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", uniqueConstraints = {@javax.persistence.UniqueConstraint( name = "SomeEntityNameUnique",
                                columnNames = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    /**
     * This test method validates the functionality of the recipe responsible for migrating nested `@Unique` annotations
     * from the `@Uniques` annotation into the `uniqueConstraints` attribute of the `@Table` annotation. It ensures the
     * transformation is executed properly while maintaining correct formatting and syntax.
     *
     * Functionality tested:
     * - Converts nested `@Unique` annotations within `@Uniques` into individual entries in the `uniqueConstraints`
     *   attribute of the `@Table` annotation.
     * - Removes the `@Uniques` annotation from the class after migration.
     * - Ensures proper structure, formatting, and correctness of the resulting `@Table` annotation.
     * - Verifies proper handling of imports for the modified annotations.
     *
     * Preconditions:
     * - The source annotation (`@Uniques`) containing nested `@Unique` annotations is present on the class.
     * - The target annotation (`@Table`) is present and may or may not already have a `uniqueConstraints` attribute.
     *
     * Postconditions:
     * - The nested `@Unique` annotations within the `@Uniques` annotation are migrated to the `uniqueConstraints` attribute
     *   of the `@Table` annotation.
     * - The `@Uniques` annotation is removed from the class.
     * - The resulting class contains a correctly formatted and complete `@Table` annotation with all constraints embedded.
     *
     * Expected behavior:
     * - The recipe processes an input Java class containing the `@Uniques` annotation with nested `@Unique` annotations.
     * - After executing the recipe, the nested `@Unique` data is moved to the `uniqueConstraints` attribute within the
     *   `@Table` annotation, and the `@Uniques` annotation is removed.
     * - The final output reflects the migration accurately and adheres to proper Java syntax and formatting standards.
     */
    @DocumentExample
    @Test
    void moveUniqueAnnotationFromUniques() {
        rewriteRun(
                spec -> spec.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.Uniques;
                                import javax.jdo.annotations.Unique;
                                
                                @PersistenceCapable(schema = "schemaName")
                                @Uniques({
                                        @Unique(name = "Person__name__UNQ", members = {"firstName", "lastName"}),
                                        @Unique(name = "Person__email__UNQ", members = {"email"})
                                })
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """,
                        """
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.Table;
                                import javax.persistence.UniqueConstraint;
                                
                                @Entity
                                @Table( schema = "schemaName", uniqueConstraints = {@UniqueConstraint( name = "Person__name__UNQ",
                                columnNames = {"firstName", "lastName"}), @UniqueConstraint( name = "Person__email__UNQ",
                                columnNames = {"email"})})
                                public class SomeEntity extends EntityAbstract {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """
                )
        );
    }
}
