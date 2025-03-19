package com.ecpnv.openrewrite.jdo2jpa.causeway;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
public class AddCausewayEntityListenerTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(PARSER)
                .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway");
    }

    /**
     * A test case for verifying the addition of the {@code @EntityListeners} annotation
     * to a JPA entity. This test ensures that the {@code CausewayEntityListener} class
     * is correctly applied as an entity listener to existing JPA entities.
     * <p>
     * In this test, the input Java source contains a class annotated with the
     * {@code @Entity} annotation. The test validates that the output Java source
     * includes the {@code @EntityListeners} annotation configured with the
     * {@code CausewayEntityListener} class, ensuring compliance with the necessary
     * requirements for Causeway's JPA integration.
     */
    @DocumentExample
    @Test
    void addEntityListener() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.persistence.Entity;
                                @Entity
                                public class SomeEntity {
                                }
                                """,
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity
                                @EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                                public class SomeEntity {
                                }
                                """
                )
        );
    }

    /**
     * A test method verifying that the {@code @EntityListeners} annotation is not added
     * to classes where it is not applicable. This ensures that unnecessary modifications
     * are avoided when working with specific annotations such as {@code @EntityScan},
     * which are not JPA entities or do not require an entity listener.
     * <p>
     * In this test case, the input Java source contains a class annotated with the
     * {@code @EntityScan} annotation. The test validates that no changes are made to
     * the source code, ensuring that the rewrite process correctly distinguishes between
     * JPA entities and other annotated classes.
     */
    @DocumentExample
    @Test
    void dontAddEntityListener() {
        rewriteRun(
                //language=java
                java(
                        """
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                @EntityScan
                                public class SomeModule {
                                }
                                """
                )
        );
    }
}
