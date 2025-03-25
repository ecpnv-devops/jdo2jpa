package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

class ReplaceLinkInCommentTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyPathSimple() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ReplaceLinkInComment(
                                "org.junit.jupiter.api.Test",
                                "org.openrewrite.DocumentExample")),
                //language=java
                java(
                        """
                                package a;
                                
                                import org.junit.jupiter.api.Test;
                                
                                /**
                                * This is a test for {@link Test}.
                                */
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import org.openrewrite.DocumentExample;
                                
                                /**
                                * This is a test for {@link DocumentExample}.
                                */public class SomeClass {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPathFull() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ReplaceLinkInComment(
                                "org.junit.jupiter.api.Test",
                                "org.openrewrite.DocumentExample")),
                //language=java
                java(
                        """
                                package a;
                                
                                /**
                                * This is a test for {@link org.junit.jupiter.api.Test}.
                                */
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import org.openrewrite.DocumentExample;
                                
                                /**
                                * This is a test for {@link DocumentExample}.
                                */public class SomeClass {
                                }
                                """
                )
        );
    }
}