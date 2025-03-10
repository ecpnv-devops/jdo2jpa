package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

/**
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
class ReplacePrimaryKeyTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources(
                "com.ecpnv.openrewrite.jdo2jpa.v2x.Identity");
    }

    /**
     * Validates that no changes are applied to the Java class structure when no migration is required.
     * <p>
     * This test ensures that when a Java class annotated with JDO's `@PrimaryKey` does not require
     * transformation or migration to JPA, the input remains unchanged in the output.
     * <p>
     * Key aspects evaluated include:
     * - Verification that the class content remains identical before and after execution of any migration recipes.
     * - Ensures that the migration process does not introduce unintended modifications when no transformation rules apply.
     */
    @DocumentExample
    @Test
    void noChange() {
        rewriteRun(
                // language=java
                java(
                        """
                                import javax.jdo.annotations.PrimaryKey;
                                import org.estatio.base.prod.dom.EntityAbstract;
                                public class SomeEntity extends EntityAbstract implements Comparable<SomeEntity> {
                                    @PrimaryKey String name;
                                }
                                """
                )
        );
    }

    /**
     * Tests the migration of JDO's `@PrimaryKey` annotation to JPA's `@Id` annotation within a class.
     * <p>
     * This method ensures that fields annotated with `@PrimaryKey` in JDO are correctly transformed
     * into fields annotated with `@Id` in JPA, preserving the semantics of identifying fields.
     * <p>
     * Key aspects validated include:
     * - Proper replacement of the JDO `@PrimaryKey` annotation with the JPA `@Id` annotation.
     * - Retention of the structural integrity of the class during the transformation.
     * <p>
     * The test uses an input Java class containing the `@PrimaryKey` annotation and checks its expected
     * transformation to the equivalent `@Id` annotation in the output class.
     */
    @DocumentExample
    @Test
    void replaceWithId() {
        rewriteRun(
                // language=java
                java(
                        """
                                import javax.jdo.annotations.PrimaryKey;
                                public class SomeEntity implements Comparable<SomeEntity> {
                                    @PrimaryKey String name;
                                }
                                """,
                        """
                                import javax.persistence.Id;
                                
                                public class SomeEntity implements Comparable<SomeEntity> {
                                    @Id String name;
                                }
                                """
                )
        );
    }
}