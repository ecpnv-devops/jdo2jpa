package com.ecpnv.openrewrite.java;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * Tests for {@link CopyAnnotationToSuper}.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class CopyAnnotationToSuperTest extends BaseRewriteTest {

    /**
     * With {@code move = true} the matched annotation is moved from the subclass to its (persistence capable)
     * super class.
     */
    @DocumentExample
    @Test
    void moveAnnotationToSuper() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new CopyAnnotationToSuper(
                                Set.of("javax.jdo.annotations.Inheritance"),
                                null,
                                true,
                                Set.of("javax.jdo.annotations.PersistenceCapable"))),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable
                                public class Parent {
                                }
                                """,
                        """
                                import javax.jdo.annotations.Inheritance;
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable
                                @Inheritance
                                public class Parent {
                                }
                                """
                ),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Inheritance;
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable
                                @Inheritance
                                public class Child extends Parent {
                                }
                                """,
                        """
                                import javax.jdo.annotations.Inheritance;
                                import javax.jdo.annotations.PersistenceCapable;

                                @PersistenceCapable
                                public class Child extends Parent {
                                }
                                """
                )
        );
    }
}
