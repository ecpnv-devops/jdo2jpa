package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

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
}