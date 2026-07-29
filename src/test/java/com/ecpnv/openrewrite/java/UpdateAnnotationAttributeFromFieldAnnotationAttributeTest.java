package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * Tests for {@link UpdateAnnotationAttributeFromFieldAnnotationAttribute}.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class UpdateAnnotationAttributeFromFieldAnnotationAttributeTest extends BaseRewriteTest {

    /**
     * Basic case: a single {@code @Column(name = ...)} on a field replaces the member reference in the
     * class-level {@code @Index(members = ...)}.
     */
    @DocumentExample
    @Test
    void replaceMemberWithColumnName() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new UpdateAnnotationAttributeFromFieldAnnotationAttribute(
                                "javax.jdo.annotations.Index", "members",
                                "javax..Column", "name")),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Index;
                                import javax.jdo.annotations.Column;

                                @Index(name = "IX", members = {"alpha"})
                                public class SomeEntity {
                                        @Column(name = "col_alpha")
                                        private String alpha;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Index;
                                import javax.jdo.annotations.Column;

                                @Index(name = "IX", members = {"col_alpha"})
                                public class SomeEntity {
                                        @Column(name = "col_alpha")
                                        private String alpha;
                                }
                                """
                )
        );
    }

    /**
     * Regression test for grouped constant declarations. When two constants are declared in a single
     * statement ({@code String COL_A = ..., COL_B = ...}) both must be registered so that a
     * {@code @Column(name = SomeEntity.COL_B)} reference on the second constant can be resolved. Before the
     * fix only the first variable of the declaration was registered, leaving the second unresolved.
     */
    @Test
    void resolveGroupedConstantDeclarations() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new UpdateAnnotationAttributeFromFieldAnnotationAttribute(
                                "javax.jdo.annotations.Index", "members",
                                "javax..Column", "name")),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Index;
                                import javax.jdo.annotations.Column;

                                @Index(name = "IX", members = {"beta"})
                                public class SomeEntity {
                                        public static final String COL_A = "col_a", COL_B = "col_b";
                                        @Column(name = SomeEntity.COL_B)
                                        private String beta;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Index;
                                import javax.jdo.annotations.Column;

                                @Index(name = "IX", members = {"col_b"})
                                public class SomeEntity {
                                        public static final String COL_A = "col_a", COL_B = "col_b";
                                        @Column(name = SomeEntity.COL_B)
                                        private String beta;
                                }
                                """
                )
        );
    }
}
