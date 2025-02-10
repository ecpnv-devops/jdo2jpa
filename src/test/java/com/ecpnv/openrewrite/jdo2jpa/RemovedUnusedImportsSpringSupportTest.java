package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

import static com.ecpnv.openrewrite.jdo2jpa.RemovedUnusedImportsSpringSupport.RemoveUnusedImportsVisitor.stripClass;

public class RemovedUnusedImportsSpringSupportTest extends BaseRewriteTest{

    @DocumentExample
    @Test
    void removeUnusedImportsHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImportsSpringSupport()),
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
                        .recipe(new RemovedUnusedImportsSpringSupport()),
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
    void removeUnusedImportsSpringImportUsageFull() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImportsSpringSupport()),
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
    void removeUnusedImportsSpringImportUsageAbstract() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImportsSpringSupport()),
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
    void removeUnusedImportsSpringImportUsageEnum() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new RemovedUnusedImportsSpringSupport()),
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
