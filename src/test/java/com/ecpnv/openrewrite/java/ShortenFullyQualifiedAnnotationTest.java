package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

class ShortenFullyQualifiedAnnotationTest extends BaseRewriteTest {
    @DocumentExample
    @Test
    void happyPathSingle() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation("lombok.ToString")),
                //language=java
                java(
                        """
                                package a;
                                
                                @lombok.NoArgsConstructor
                                @lombok.ToString(callSuper = true)
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import lombok.ToString;
                                
                                @lombok.NoArgsConstructor
                                @ToString(callSuper = true)
                                public class SomeClass {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPathAll() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                //language=java
                java(
                        """
                                package a;
                                
                                @lombok.NoArgsConstructor
                                @lombok.ToString(callSuper = true)
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import lombok.NoArgsConstructor;
                                import lombok.ToString;
                                
                                @NoArgsConstructor
                                @ToString(callSuper = true)
                                public class SomeClass {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPathDouble() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                java(
                        """
                                package a;
                                
                                public class SomeClass {
                                
                                    @javax.persistence.Version
                                    @javax.jdo.annotations.Version
                                    private String name;
                                
                                }
                                """,
                        """
                                package a;
                                
                                import javax.persistence.Version;
                                
                                public class SomeClass {
                                
                                    @Version
                                    @javax.jdo.annotations.Version
                                    private String name;
                                
                                }
                                """)
        );
    }

    @DocumentExample
    @Test
    void happyPathHalf1() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                java(
                        """
                                package a;
                                
                                import javax.persistence.Version;
                                
                                public class SomeClass {
                                
                                    @Version
                                    @javax.jdo.annotations.Version
                                    private String name;
                                
                                }
                                """)
        );
    }

    @DocumentExample
    @Test
    void happyPathHalf2() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                java(
                        """
                                package a;
                                
                                import javax.persistence.Version;
                                
                                public class SomeClass {
                                
                                    @javax.jdo.annotations.Version
                                    @Version
                                    private String name;
                                
                                }
                                """)
        );
    }
}