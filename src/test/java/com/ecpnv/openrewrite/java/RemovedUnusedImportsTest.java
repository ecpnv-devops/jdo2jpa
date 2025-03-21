package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

import static com.ecpnv.openrewrite.java.RemovedUnusedImports.RemoveUnusedImportsVisitor.stripClass;

class RemovedUnusedImportsTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void removeUnusedImportsHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import java.util.List;
                                import java.util.Optional;
                                
                                public class SomeClass {
                                    private List<String> listOfStrings;
                                
                                }
                                """,
                        """
                                import java.util.List;
                                
                                public class SomeClass {
                                    private List<String> listOfStrings;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsSomeRandomImport() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.IdentityType;
                                
                                public class SomeClass {
                                    private List<String> listOfStrings;
                                
                                }
                                """,
                        """
                                import java.util.List;
                                
                                public class SomeClass {
                                    private List<String> listOfStrings;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportUsageFull() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import java.util.List;
                                import org.springframework.context.annotation.Import;
                                import com.ecpnv.openrewrite.jdo2jpa.com.ecpnv.openrewrite.modules.SomeModule;
                                
                                @Import({
                                
                                //some comment here
                                com.ecpnv.openrewrite.jdo2jpa.com.ecpnv.openrewrite.modules.SomeModule.class,
                                
                                //some comment here
                                })
                                public class SomeClass {
                                    private List<String> listOfStrings;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportUsageAbstract() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import java.util.List;
                                import org.springframework.context.annotation.Import;
                                import com.ecpnv.openrewrite.jdo2jpa.com.ecpnv.openrewrite.modules.SomeModule;
                                
                                public abstract class SomeClass {
                                
                                    @Import({com.ecpnv.openrewrite.jdo2jpa.com.ecpnv.openrewrite.modules.SomeModule.class})
                                
                                    private List<String> listOfStrings;
                                
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportUsageEnum() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import com.ecpnv.openrewrite.jdo2jpa.com.ecpnv.openrewrite.modules.SomeModule;
                                
                                public enum SomeEnum {
                                    private com.ecpnv.openrewrite.jdo2jpa.com.ecpnv.openrewrite.modules.SomeModule someModule;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportUsageLambda() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import lombok.val;
                                import java.util.List;
                                import java.util.stream.Collectors;
                                
                                public class SomeClass {
                                    private List<String> names;
                                
                                    public String doStuff() {
                                        return names.stream().map(name -> {
                                            val prefix = "GK";
                                            return prefix + name;
                                        }).collect(Collectors.joining(","));
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportUsageMethod() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import lombok.val;
                                import java.util.List;
                                import java.util.stream.Collectors;
                                
                                public class SomeClass {
                                    private List<String> names;
                                
                                    public String doStuff() {
                                        val prefix = "GK";
                                        return prefix + names.stream().collect(Collectors.joining(","));
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportUsageTryCatch() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import lombok.val;
                                import java.util.List;
                                import java.util.stream.Collectors;
                                
                                public class SomeClass {
                                    private List<String> names;
                                
                                    public String doStuff() {
                                        try {
                                            val prefix = "GK";
                                            return prefix + names.stream().collect(Collectors.joining(","));
                                        } catch (Exception e) {
                                            return null;
                                        }
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsImportEnum() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import lombok.val;
                                import java.util.List;
                                import java.util.stream.Collectors;
                                
                                public enum TestEnum {
                                    TEST1, TEST2
                                    private List<String> names;
                                
                                    public String doStuff() {
                                        try {
                                            val prefix = "GK";
                                            return prefix + names.stream().collect(Collectors.joining(","));
                                        } catch (Exception e) {
                                            return null;
                                        }
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedImportsAnnotations() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImports()),
                java(
                        """
                                import javax.persistence.Table;
                                import com.ecpnv.openrewrite.java.dom.SomeOtherClass;
                                import javax.persistence.UniqueConstraint;
                                
                                @Table(schema = "dbo", name = "UnitLink", uniqueConstraints = {
                                        @UniqueConstraint(name = "UnitLink_sourceUnit_destinationUnit_UNQ", columnNames = {"sourceUnit", "destinationUnit"})
                                })
                                public class SomeClass {
                                   private static final String NAME = SomeOtherClass.NAME + "123";
                                }
                                """
                )
        );
    }

    @Test
    void testStripClass() {
        assertEquals("FakeSchedulerV2", stripClass("""
                
                // services
                FakeSchedulerV2.class,
                """));
        assertEquals("FakeSchedulerV2", stripClass("""
                /*
                * some comment here 
                */
                FakeSchedulerV2.class,
                
                """));
        assertEquals("FakeSchedulerV2", stripClass("""
                /* test */
                FakeSchedulerV2.class
                """));
        assertEquals("FakeSchedulerV2", stripClass("""
                // dont use SomeOtherClass.class
                FakeSchedulerV2.class,
                
                """));
        assertEquals("FakeSchedulerV2", stripClass("""
                FakeSchedulerV2.class, //some comment here
                
                """));
    }
}
