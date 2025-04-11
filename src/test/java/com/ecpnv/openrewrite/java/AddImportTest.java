package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

import org.openrewrite.test.SourceSpec;

class AddImportTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyCase() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipe(new AddImport("a.A", "java.util.List")),
                java("""
                        package a;
                        
                        import java.lang.String;
                        
                        public class A {
                            private List<String> names;
                        }
                        """,
                        """
                        package a;
                        
                        import java.lang.String;
                        import java.util.List;
                        
                        public class A {
                            private List<String> names;
                        }
                        """));
    }

    @DocumentExample
    @Test
    void innerClass() {
        rewriteRun(spec -> spec.parser(PARSER)
                        .recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.post.causeway")
        ,
                        //language=java
                        java("""
                        package org.incode.module.communications;
                        
                        public interface EstatioSharedKernelCommunicationsModule {
                            abstract class AbstractEvent{}
                        }
                        """, SourceSpec::skip),
                        java(
                                """
                                package org.incode.module.communications.integtests.dom.communications.dom.demowithnotes;
                                
                                import org.springframework.context.annotation.Configuration;
                                import org.incode.module.communications.EstatioSharedKernelCommunicationsModule;
                                
                                @Configuration
                                public class DemoObjectWithNotes_invoices {
                                    public abstract class SomeEvent extends EstatioSharedKernelCommunicationsModule.AbstractEvent {}
                                }
                                """
                        )
                );
    }
}