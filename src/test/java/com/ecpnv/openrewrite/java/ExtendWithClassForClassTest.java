package com.ecpnv.openrewrite.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

class ExtendWithClassForClassTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new ExtendWithClassForClass(
                                "com.ecpnv.openrewrite.java.SomeEntity",
                                "org.estatio.base.prod.dom.EntityAbstract"
                        )),
                // language=java
                java(
                        """
                                package com.ecpnv.openrewrite.java;
                                
                                public class SomeEntity implements Comparable<SomeEntity> {
                                
                                    @Override
                                    public int compareTo(SomeEntity o) {
                                        return 0;
                                    }
                                }
                                """,
                        """
                                package com.ecpnv.openrewrite.java;

                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                public class SomeEntity extends EntityAbstract implements Comparable<SomeEntity> {
                                
                                    @Override
                                    public int compareTo(SomeEntity o) {
                                        return 0;
                                    }
                                }
                                """
                )
        );
    }
}