package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.SourceSpec;

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
                                
                                import lombok.*;
                                
                                @lombok.NoArgsConstructor
                                @lombok.ToString(callSuper = true)
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import lombok.*;
                                
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
                                
                                    @javax.persistence.Transient
                                    private Long version;
                                }
                                """,
                        """
                                package a;
                                
                                import javax.persistence.Transient;
                                
                                public class SomeClass {
                                
                                    @Transient
                                    private Long id;
                                
                                    @Transient
                                    private Long version;
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
                                @lombok.EqualsAndHashCode
                                public class SomeClass {
                                }
                                """,
                        """
                                package a;
                                
                                import lombok.EqualsAndHashCode;
                                import lombok.NoArgsConstructor;
                                import lombok.ToString;
                                
                                @NoArgsConstructor
                                @ToString(callSuper = true)
                                @EqualsAndHashCode
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
                                
                                import javax.persistence.*;
                                
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
                java("""
                        package test;
                        
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;
                        
                        @Target({ElementType.TYPE})
                        @Retention(RetentionPolicy.SOURCE)
                        public @interface ToString {
                            boolean includeFieldNames() default true;
                        
                            String[] exclude() default {};
                        
                            String[] of() default {};
                        
                            boolean callSuper() default false;
                        
                            boolean doNotUseGetters() default false;
                        
                            boolean onlyExplicitlyIncluded() default false;
                        
                            @Target({ElementType.FIELD})
                            @Retention(RetentionPolicy.SOURCE)
                            public @interface Exclude {
                            }
                        
                            @Target({ElementType.FIELD, ElementType.METHOD})
                            @Retention(RetentionPolicy.SOURCE)
                            public @interface Include {
                                int rank() default 0;
                        
                                String name() default "";
                            }
                        }
                        """, SourceSpec::skip),
                java("""
                                package a;
                                
                                @lombok.EqualsAndHashCode
                                public class ToString {
                                
                                    @lombok.ToString.Include @lombok.EqualsAndHashCode.Include
                                    private String name;
                                
                                    @test.ToString
                                    private String title;
                                }
                                """
                        ,
                        """
                                package a;
                                
                                import lombok.EqualsAndHashCode;
                                
                                @EqualsAndHashCode
                                public class ToString {
                                
                                    @lombok.ToString.Include @lombok.EqualsAndHashCode.Include
                                    private String name;
                                
                                    @test.ToString
                                    private String title;
                                }
                                """)
        );
    }

    @DocumentExample
    @Test
    void edgeCase2() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                java("""
                        package test;
                        
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Inherited;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;
                        
                        @Inherited
                        @Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface Property {
                            String value() default "";
                        }
                        """, SourceSpec::skip),
                java("""
                        package a;
                        
                        public class Property {
                        }
                        """, SourceSpec::skip),
                java("""
                        package a;
                        
                        public class ToString {
                        
                            @test.Property
                            Property property;
                        
                        }
                        """)
        );
    }

    @DocumentExample
    @Test
    void edgeCase3() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(
                        new ShortenFullyQualifiedAnnotation(null)),
                java("""
                        package org.springframework.stereotype;
                        
                        import java.lang.annotation.Documented;
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;
                        
                        @Target({ElementType.TYPE})
                        @Retention(RetentionPolicy.RUNTIME)
                        @Documented
                        @Component
                        public @interface Repository {
                            String value() default "";
                        }
                        """, SourceSpec::skip),
                java("""
                        package a;
                        
                        public class SomeClass {
                        
                            @org.springframework.stereotype.Repository
                            private static class Repository {
                            }
                        }
                        """)
        );
    }

    @DocumentExample
    @Test
    void edgeCase4() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new ShortenFullyQualifiedAnnotation(null)),
                java("""
                        import javax.persistence.*;
                        
                        @Entity
                        public class Person {}
                        """, SourceSpec::skip),
                java("""
                        import java.util.List;
                        import javax.persistence.*;
                        
                        @Entity
                        public class SomeEntity {
                            private int id;
                            @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                            @JoinTable(name = "some_entity_person",
                                    joinColumns = {@javax.persistence.JoinColumn(name = "some_entity_id")},
                                    inverseJoinColumns = {@javax.persistence.JoinColumn(name = "person_id")})
                            private List<Person> persons;
                        }
                        """
                ));
    }


    @DocumentExample
    @Test
    void edgeCase5() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new ShortenFullyQualifiedAnnotation(null)),
                java("""
                                package a;
                                
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @org.springframework.boot.autoconfigure.domain.EntityScan({"a","b"})
                                public class SomeConfiguration {
                                }
                                """,
                        """
                                package a;
                                
                                import org.springframework.boot.autoconfigure.domain.EntityScan;
                                import org.springframework.context.annotation.Configuration;
                                
                                @Configuration
                                @EntityScan({"a","b"})
                                public class SomeConfiguration {
                                }
                                """
                ));
    }
}