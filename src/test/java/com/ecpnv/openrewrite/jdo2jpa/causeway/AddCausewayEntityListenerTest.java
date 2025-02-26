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

    @DocumentExample
    @Test
    void removeTrailingCommaFromAnnotation() {
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
                                @EntityListeners(org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener.class)
                                public class SomeEntity {
                                }
                                """
                )
        );
    }

}
