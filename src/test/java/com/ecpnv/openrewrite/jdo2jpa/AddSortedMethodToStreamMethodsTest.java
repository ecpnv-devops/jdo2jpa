package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

class AddSortedMethodToStreamMethodsTest extends BaseRewriteTest {
    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new AddSortedMethodToStreamMethods("com.ecpnv.openrewrite.Programmatic")),
                java("""
                            package com.ecpnv.openrewrite;
                            
                            import java.lang.annotation.ElementType;
                            import java.lang.annotation.Inherited;
                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;
                            import java.lang.annotation.Target;
                        
                            @Inherited
                            @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
                            @Retention(RetentionPolicy.RUNTIME)
                            public @interface Programmatic {}
                        """, SourceSpec::skip),
                //language=java
                java(
                        """
                                package a;

                                import java.util.Set;
                                import java.util.HashSet;
                                import java.util.ArrayList;
                                import java.util.stream.Stream;

                                import com.ecpnv.openrewrite.Programmatic;

                                public class SomeClass {

                                    private final Set<String> items = new HashSet<>();
                                    private final Set<String> history = new HashSet<>();

                                    @Programmatic
                                    @Override
                                    public Stream<String> streamHistory() {
                                        return new ArrayList<>(history).stream();
                                    }

                                    @Programmatic
                                    @Override
                                    public Stream<String> streamItems() {
                                        return new ArrayList<>(items).stream().sorted();
                                    }
                                }
                                """,
                        """
                                package a;

                                import java.util.Set;
                                import java.util.HashSet;
                                import java.util.ArrayList;
                                import java.util.stream.Stream;

                                import com.ecpnv.openrewrite.Programmatic;

                                public class SomeClass {

                                    private final Set<String> items = new HashSet<>();
                                    private final Set<String> history = new HashSet<>();

                                    @Programmatic
                                    @Override
                                    public Stream<String> streamHistory() {
                                        return new ArrayList<>(history).stream().sorted();
                                    }

                                    @Programmatic
                                    @Override
                                    public Stream<String> streamItems() {
                                        return new ArrayList<>(items).stream().sorted();
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void unhappyPath() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new AddSortedMethodToStreamMethods("com.ecpnv.openrewrite.Programmatic")),
                java("""
                            package com.ecpnv.openrewrite;
                            
                            import java.lang.annotation.ElementType;
                            import java.lang.annotation.Inherited;
                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;
                            import java.lang.annotation.Target;
                        
                            @Inherited
                            @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
                            @Retention(RetentionPolicy.RUNTIME)
                            public @interface Programmatic {}
                        """, SourceSpec::skip),
                //language=java
                java(
                        """
                                package a;

                                import java.util.SortedSet;
                                import java.util.TreeSet;
                                import java.util.ArrayList;
                                import java.util.stream.Stream;

                                import com.ecpnv.openrewrite.Programmatic;

                                public class SomeClass {

                                    private final SortedSet<String> items = new TreeSet<>();
                                    
                                    public SortedSet<String> getItems() {
                                        return items;
                                    }

                                    @Programmatic
                                    @Override
                                    public Stream<String> streamItems() {
                                        return getItems().stream().filter(s->!s.isEmpty()).sorted().map(String::toUpperCase);
                                    }
                                }
                                """)
        );
    }
}