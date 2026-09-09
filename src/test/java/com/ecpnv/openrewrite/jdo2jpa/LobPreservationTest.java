package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

class LobPreservationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Column");
    }

    @Test
    void preservesBlobFieldAndClobPropertyClassification() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;

                                class Payload {
                                    @Column(name = "bytes", allowsNull = "false", jdbcType = "BLOB", sqlType = "BLOB")
                                    private byte[] bytes;

                                    @Column(name = "text", jdbcType = "CLOB", sqlType = "CLOB")
                                    public String getText() {
                                        return null;
                                    }
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Lob;

                                class Payload {
                                    @Column(name = "bytes", nullable = false)
                                    @Lob
                                    private byte[] bytes;

                                    @Column(name = "text")
                                    @Lob
                                    public String getText() {
                                        return null;
                                    }
                                }
                                """));
    }

    @Test
    void preservesExistingLobWithoutDuplication() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                import javax.persistence.Lob;

                                class Payload {
                                    @Column(name = "bytes", jdbcType = "BLOB")
                                    @Lob
                                    private byte[] bytes;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Lob;

                                class Payload {
                                    @Column(name = "bytes")
                                    @Lob
                                    private byte[] bytes;
                                }
                                """));
    }

    @Test
    void doesNotInferLobFromSqlTypeLengthOrJavaType() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;

                                class Payload {
                                    @Column(name = "sql_only", sqlType = "BLOB")
                                    private byte[] sqlOnly;

                                    @Column(name = "large", length = 100000)
                                    private String large;

                                    private byte[] ordinaryBytes;
                                }
                                """,
                        """
                                import javax.persistence.Column;

                                class Payload {
                                    @Column(name = "sql_only")
                                    private byte[] sqlOnly;

                                    @Column(name = "large", length = 100000)
                                    private String large;

                                    private byte[] ordinaryBytes;
                                }
                                """));
    }

    @Test
    void doesNotAddLobToAnnotationDeclarations() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;

                                @Column(name = "meta", jdbcType = "CLOB")
                                public @interface PayloadMetadata {}
                                """,
                        """
                                import javax.persistence.Column;

                                @Column(name = "meta")
                                public @interface PayloadMetadata {}
                                """));
    }
}
