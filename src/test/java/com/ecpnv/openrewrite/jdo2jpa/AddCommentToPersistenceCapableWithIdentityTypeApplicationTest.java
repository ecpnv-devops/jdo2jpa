package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

class AddCommentToPersistenceCapableWithIdentityTypeApplicationTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void addCommentHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new AddCommentToPersistenceCapableWithIdentityTypeApplication()),
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.APPLICATION)
                                public class SomeEntity {
                                }
                                """
                        ,
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                /*
                                TODO: manually migrate to JPA
                                */
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.APPLICATION)
                                public class SomeEntity {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addCommentUnHappyPath() {
        rewriteRun(
                spec -> spec.parser(PARSER)
                        .recipe(new AddCommentToPersistenceCapableWithIdentityTypeApplication()),
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.DATASTORE)
                                public class SomeEntity {
                                }
                                """
                )
        );
    }
}