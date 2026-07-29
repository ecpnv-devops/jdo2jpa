package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * Tests for {@link UnwrapAnnotationToClass}.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class UnwrapAnnotationToClassTest extends BaseRewriteTest {

    /**
     * Happy path: the wrapped {@code @Index} annotations are moved out of the {@code @Indices} wrapper onto
     * the class and the wrapper is removed.
     */
    @DocumentExample
    @Test
    void unwrapAndRemoveParent() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new UnwrapAnnotationToClass("javax.jdo.annotations.Index", true)),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;

                                @Indices({
                                  @Index(name = "Person__name__IDX", members = {"name"}),
                                  @Index(name = "Person__email__IDX", members = {"email"}),
                                })
                                public class SomeEntity {
                                        private int id;
                                        private String name, email;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Index;


                                @Index(name = "Person__email__IDX", members = {"email"})
                                @Index(name = "Person__name__IDX", members = {"name"})
                                public class SomeEntity {
                                        private int id;
                                        private String name, email;
                                }
                                """
                )
        );
    }

    /**
     * The {@code removeParentAnnotation} option is optional. When it is omitted (i.e. {@code null}) the recipe
     * must not throw a {@link NullPointerException} while unboxing the {@link Boolean}; it should still unwrap
     * the annotations but leave the parent wrapper in place.
     */
    @Test
    void unwrapWithoutRemoveParentOptionDoesNotThrow() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new UnwrapAnnotationToClass("javax.jdo.annotations.Index", null)),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;

                                @Indices({
                                  @Index(name = "Person__name__IDX", members = {"name"}),
                                })
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;

                                @Indices({
                                  @Index(name = "Person__name__IDX", members = {"name"}),
                                })
                                @Index(name = "Person__name__IDX", members = {"name"})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }
}
