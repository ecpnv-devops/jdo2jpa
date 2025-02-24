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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class InheritanceTest {

    @Nested
    class TestInheritanceRecipe extends BaseRewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(PARSER).
                    recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Inheritance");
        }

        /**
         * Unit test method validating the replacement of `InheritanceStrategy.NEW_TABLE` in JDO with
         * the `InheritanceType.JOINED` annotation in JPA.
         * <p>
         * This test ensures the transformation of inheritance annotations from JDO to JPA for parent and
         * child classes. It verifies that:
         * - The `@Inheritance` annotation is correctly replaced with its JPA equivalent in the source code.
         * - The `strategy` attribute from JDO is accurately translated to its corresponding JPA attribute
         * (`InheritanceType.JOINED`).
         * - Child classes that implicitly inherit annotations from the parent class are updated as expected.
         * <p>
         * The test provides assertions to ensure that all required imports are replaced with their JPA
         * counterparts, and unnecessary imports are removed, maintaining the integrity and functionality
         * of the code.
         */
        @DocumentExample
        @Test
        void replaceJoined() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.persistence.Inheritance;
                                    
                                    @Inheritance(strategy = javax.persistence.InheritanceType.JOINED)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }


        /**
         * Unit test method validating the replacement of `InheritanceStrategy.SUBCLASS_TABLE` in JDO
         * with the JPA `@MappedSuperclass` annotation.
         * <p>
         * This test ensures that the transformation correctly updates class-level annotations for
         * inheritance, replacing JDO's `@Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)`
         * with the appropriate JPA equivalent. The test verifies that:
         * <p>
         * - Parent classes are annotated with `@MappedSuperclass` to reflect the change in inheritance strategy.
         * - Child classes no longer contain unnecessary inheritance annotations and retain proper relationships.
         * - All imported annotations and dependencies are updated correctly to adhere to JPA conventions.
         * - Redundant imports from JDO are removed and replaced with the necessary JPA imports.
         * <p>
         * Assertions are made to confirm that all transformations are performed as expected and that the
         * resulting source code aligns with JPA standards for subclass table inheritance.
         */
        @DocumentExample
        @Test
        void replaceSubclassTable() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.persistence.MappedSuperclass;
                                    
                                    @MappedSuperclass
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * Unit test method to validate the replacement of the JDO `InheritanceStrategy.SUPERCLASS_TABLE`
         * annotation with the JPA `InheritanceType.SINGLE_TABLE` annotation.
         * <p>
         * This test ensures the accurate transformation of inheritance annotations from JDO to JPA for
         * parent and child classes, focusing on the scenario where the superclass table inheritance strategy
         * is used in JDO.
         * <p>
         * Transformation goals validated in this test include:
         * - Replacing the JDO `@Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)` annotation with
         * the JPA `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` annotation.
         * - Ensuring that unnecessary JDO imports are removed and replaced with the required JPA imports.
         * - Verifying that subclasses of the primary class do not include redundant inheritance annotations,
         * while still maintaining their proper relationship with the parent class.
         * <p>
         * Assertions are made to confirm:
         * - Correct application of inheritance mapping changes on the parent class.
         * - Maintenance of integrity in the child class relationships and annotations.
         * - Input and output source code are aligned with best practices for JPA inheritance strategies.
         */
        @DocumentExample
        @Test
        void replaceSuperclassTable() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.persistence.Inheritance;
                                    
                                    @Inheritance(strategy = javax.persistence.InheritanceType.SINGLE_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * Unit test method validating the replacement of the `InheritanceStrategy.COMPLETE_TABLE` annotation
         * in JDO with the JPA `InheritanceType.TABLE_PER_CLASS` annotation.
         * <p>
         * This test ensures that classes using the JDO `@Inheritance(strategy = InheritanceStrategy.COMPLETE_TABLE)`
         * are transformed to use the equivalent JPA inheritance mapping. The primary focus of this test is to verify:
         * <p>
         * - The accurate replacement of `@Inheritance` annotations with their JPA counterparts.
         * - The correct modification of the `strategy` attribute from `InheritanceStrategy.COMPLETE_TABLE`
         * to `InheritanceType.TABLE_PER_CLASS`.
         * - The removal of JDO-specific annotations and imports that are no longer relevant.
         * - The introduction of necessary JPA imports to ensure the transformed code is functional.
         * <p>
         * Assertions within the test confirm that the expected output matches the required JPA standard,
         * including the correct inheritance mapping for related classes without redundant or incorrect annotations.
         */
        @DocumentExample
        @Test
        void replaceCompleteTable() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.COMPLETE_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(customStrategy = "custom")
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.persistence.Inheritance;
                                    
                                    @Inheritance(strategy = javax.persistence.InheritanceType.TABLE_PER_CLASS)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }
    }

    @Nested
    class TestAllRecipes extends BaseRewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(PARSER).
                    recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x");
        }

        /**
         * Unit test method validating the behavior of applying all sub-recipes within the `v2x` migration recipe.
         * <p>
         * This method verifies the comprehensive transformation of Java code from JDO (Java Data Objects)
         * annotations to JPA (Java Persistence API) annotations, ensuring the correct application of the
         * sub-recipes provided by the `jdo2jpa.v2x` recipe. It includes:
         * <p>
         * - Thorough testing of the conversion of `@Inheritance` and related annotations from JDO to JPA.
         * - Validation of the transformation of class-level and field-level annotations in both parent and child classes.
         * - Verification that the output matches the expected result with all required modifications applied.
         * <p>
         * Assertions in this test are used to ensure:
         * - The Inheritance@strategy attribute is not copied from the child, because the child has none
         * - Class-level annotations such as `@Discriminator` are replaced accurately with their JPA counterparts.
         * - Specific annotation attributes (e.g., `strategy`, `column`, `columns`) are translated as expected.
         * - Field annotations (e.g., `@Persistent`) are updated to their appropriate JPA equivalents with proper mappings.
         */
        @DocumentExample
        @Test
        void testInheritanceNoCopyOfStrategy() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Discriminator;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    import javax.jdo.annotations.Persistent;
                                    import javax.jdo.annotations.PersistenceCapable;
                                    
                                    @PersistenceCapable(schema = "schemaname", table = "person")
                                    @Discriminator("Person", strategy = "special", column = "col", columns = {"cols"}, indexed = "true")
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    @PersistenceCapable(schema = "schemaname", table = "manager")
                                    @Inheritance
                                    public class Manager extends Person {
                                            @Persistent( mappedBy = "person")
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    
                                    import org.estatio.base.prod.dom.EntityAbstract;
                                    import javax.persistence.*;
                                    
                                    @Table(schema = "schemaname", name = "person")
                                    @DiscriminatorValue("Person")
                                    @DiscriminatorColumn(name = "discriminator", length = 255)
                                    @Entity
                                    @Inheritance(strategy = javax.persistence.InheritanceType.JOINED)
                                    public class Person extends EntityAbstract {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    @Entity
                                    @Table(schema = "schemaname", name = "manager")
                                    @DiscriminatorValue("Manager")
                                    @DiscriminatorColumn(name = "discriminator", length = 255)
                                    public class Manager extends Person {
                                            @OneToMany(mappedBy = "person", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * Unit test method validating the behavior of applying all sub-recipes within the `v2x` migration recipe.
         * <p>
         * This method verifies the comprehensive transformation of Java code from JDO (Java Data Objects)
         * annotations to JPA (Java Persistence API) annotations, ensuring the correct application of the
         * sub-recipes provided by the `jdo2jpa.v2x` recipe. It includes:
         * <p>
         * - Thorough testing of the conversion of `@Inheritance` and related annotations from JDO to JPA.
         * - Validation of the transformation of class-level and field-level annotations in both parent and child classes.
         * - Verification that the output matches the expected result with all required modifications applied.
         * <p>
         * Assertions in this test are used to ensure:
         * - The Inheritance@strategy attribute is copied from the child, resulting in a different but correct JPA migration
         * - Class-level annotations such as `@Discriminator` are replaced accurately with their JPA counterparts.
         * - Specific annotation attributes (e.g., `strategy`, `column`, `columns`) are translated as expected.
         * - Field annotations (e.g., `@Persistent`) are updated to their appropriate JPA equivalents with proper mappings.
         */
        @DocumentExample
        @Test
        void testInheritanceWithCopyOfStrategy() {
            rewriteRun(
                    //language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Discriminator;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    import javax.jdo.annotations.Persistent;
                                    import javax.jdo.annotations.PersistenceCapable;
                                    
                                    @PersistenceCapable(schema = "schemaname", table = "person")
                                    @Discriminator("Person", strategy = "special", column = "col", columns = {"cols"}, indexed = "true")
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    @PersistenceCapable(schema = "schemaname", table = "manager")
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            @Persistent( mappedBy = "person")
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    
                                    import org.estatio.base.prod.dom.EntityAbstract;
                                    import javax.persistence.*;
                                    
                                    @Table(schema = "schemaname", name = "person")
                                    @DiscriminatorValue("Person")
                                    @DiscriminatorColumn(name = "discriminator", length = 255)
                                    @Entity
                                    @Inheritance(strategy = javax.persistence.InheritanceType.SINGLE_TABLE)
                                    public class Person extends EntityAbstract {
                                            private int id;
                                            private String name;
                                    }
                                    
                                    @Entity
                                    @Table(schema = "schemaname", name = "manager")
                                    @DiscriminatorValue("Manager")
                                    @DiscriminatorColumn(name = "discriminator", length = 255)
                                    public class Manager extends Person {
                                            @OneToMany(mappedBy = "person", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }
    }
}
