package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

class AddAnnotationToClassVariableTest extends BaseRewriteTest {


    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(r -> r.parser(PARSER).recipe(new AddAnnotationToClassVariable(
                        "a.SomeInterface.Helper.*",
                        "javax.persistence.Transient",
                        "@Transient")),
                java("""
                        package a;
                        
                        public interface SomeInterface {
                            public static class Helper<T extends SomeInterface> {
                                T t;
                                public Helper(T t) {
                                    this.t = t;
                                }
                            }
                        }
                        """, SourceSpec::skip),
                java("""
                        package b;
                        
                        import java.util.List;
                        import java.util.ArrayList;
                        import javax.persistence.Entity;
                        
                        import a.SomeInterface;
                        
                        @Entity
                        public class SomeEntity implements SomeInterface {
                            private List<String> names = new ArrayList<>();
                            private SomeInterface.Helper<SomeEntity> helper = new SomeInterface.Helper<>(this);
                        }
                        """,
                        """
                        package b;
                        
                        import java.util.List;
                        import java.util.ArrayList;
                        import javax.persistence.Entity;
                        import javax.persistence.Transient;
                        
                        import a.SomeInterface;

                        @Entity
                        public class SomeEntity implements SomeInterface {
                            private List<String> names = new ArrayList<>();
                            @Transient
                            private SomeInterface.Helper<SomeEntity> helper = new SomeInterface.Helper<>(this);
                        }
                        """));
    }

    @DocumentExample
    @Test
    void unhappyPath1() {
        rewriteRun(r -> r.parser(PARSER).recipe(new AddAnnotationToClassVariable(
                        "b.SomeInterface.Helper.*",
                        "javax.persistence.Transient",
                        "@Transient")),
                java("""
                        package a;
                        
                        public interface SomeInterface {
                            public static class Helper<T extends SomeInterface> {
                                T t;
                                public Helper(T t) {
                                    this.t = t;
                                }
                            }
                        }
                        """, SourceSpec::skip),
                java("""
                        package b;
                        
                        import java.util.List;
                        import java.util.ArrayList;
                        import javax.persistence.Entity;
                        
                        import a.SomeInterface;
                        
                        @Entity
                        public class SomeEntity implements SomeInterface {
                            private List<String> names = new ArrayList<>();
                            private SomeInterface.Helper<SomeEntity> helper = new SomeInterface.Helper<>(this);
                        }
                        """));
    }

    @DocumentExample
    @Test
    void unhappyPath2() {
        rewriteRun(r -> r.parser(PARSER).recipe(new AddAnnotationToClassVariable(
                        "a.SomeInterface.Helper.*",
                        "javax.persistence.Transient",
                        "@Transient")),
                java("""
                        package a;
                        
                        public interface SomeInterface {
                            public static class Helper<T extends SomeInterface> {
                                T t;
                                public Helper(T t) {
                                    this.t = t;
                                }
                            }
                        }
                        """, SourceSpec::skip),
                java("""
                        package b;
                        
                        import java.util.List;
                        import java.util.ArrayList;
                        import javax.persistence.Entity;
                        
                        import a.SomeInterface;
                        
                        @Entity
                        public class SomeEntity implements SomeInterface {
                            private List<String> names = new ArrayList<>();
                            private SomeInterface.Helper<SomeEntity> helper;
                        
                            public SomeEntity() {
                                this.helper = new SomeInterface.Helper<>(this);
                            }
                        }
                        """));
    }
}