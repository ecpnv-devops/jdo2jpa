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
    void happyPathAttribute1() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation("javax.persistence.Id")),
                //language=java
                java(
                        """
                                package a;
                                
                                public class SomeClass {
                                
                                    @javax.persistence.Id
                                    private Long id;
                                }
                                """,
                        """
                                package a;
                                
                                import javax.persistence.Id;
                                
                                public class SomeClass {
                                
                                    @Id
                                    private Long id;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPathAttribute2() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation("javax.persistence.Transient")),
                //language=java
                java(
                        """
                                package a;
                                
                                public class SomeClass {
                                
                                    @javax.persistence.Transient
                                    private Long id;
                                }
                                """,
                        """
                                package a;
                                
                                import javax.persistence.Transient;
                                
                                public class SomeClass {
                                
                                    @Transient
                                    private Long id;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void happyPathAll() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation("lombok.ToString")),
                //language=java
                java(
                        """
                                package a;
                                
                                @lombok.NoArgsConstructor
                                @lombok.ToString(callSuper = true)
                                @lombok.EqualsAndHashCode
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import lombok.ToString;
                                
                                @lombok.NoArgsConstructor
                                @ToString(callSuper = true)
                                @lombok.EqualsAndHashCode
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
                        new ShortenFullyQualifiedAnnotation("javax.persistence.Version")),
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
                        new ShortenFullyQualifiedAnnotation("javax.persistence.Version")),
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
                        new ShortenFullyQualifiedAnnotation("javax.persistence.Version")),
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


    @DocumentExample
    @Test
    void edgeCase1() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                java(
                        """
                                package a;
                                
                                @lombok.ToString
                                @lombok.EqualsAndHashCode
                                public class SomeClass {
                                
                                    @lombok.ToString.Include @lombok.EqualsAndHashCode.Include
                                    private String name;
                                
                                }
                                """
                ,
                        """
                                package a;
                                
                                import lombok.EqualsAndHashCode;
                                import lombok.ToString;
                                
                                @ToString
                                @EqualsAndHashCode
                                public class SomeClass {
                                
                                    @lombok.ToString.Include @lombok.EqualsAndHashCode.Include
                                    private String name;
                                
                                }
                                """)
        );
    }
}