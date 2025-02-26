package com.ecpnv.openrewrite.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
public class RemoveTrailingCommaTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(PARSER)
                .recipe(new RemoveTrailingComma());
    }

    @DocumentExample
    @Test
    void removeTrailingCommaFromAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;
                                
                                @Indices({
                                        @Index(
                                                name = "CommChannelRole_comm_channel_type_IDX",
                                                members = { "communication", "channel", "type", }
                                        ),
                                        @Index(
                                                name = "Communication_channel_comm_type_IDX",
                                                members = { "channel", "communication", "type", }
                                        ),
                                        @Index(
                                                name = "CommChannelRole_comm_type_channel_IDX",
                                                members = { "communication", "type", "channel", }
                                        ),
                                        @Index(
                                                name = "Communication_channel_type_comm_IDX",
                                                members = { "channel", "type", "communication", }
                                        ),
                                })
                                public class SomeEntity {
                                        private String[] names = new String[] {"foo", "bar", };
                                }
                                """,
                        """
                                import javax.jdo.annotations.Indices;
                                import javax.jdo.annotations.Index;
                                
                                @Indices({
                                        @Index(
                                                name = "CommChannelRole_comm_channel_type_IDX",
                                                members = { "communication", "channel", "type" }
                                        ),
                                        @Index(
                                                name = "Communication_channel_comm_type_IDX",
                                                members = { "channel", "communication", "type" }
                                        ),
                                        @Index(
                                                name = "CommChannelRole_comm_type_channel_IDX",
                                                members = { "communication", "type", "channel" }
                                        ),
                                        @Index(
                                                name = "Communication_channel_type_comm_IDX",
                                                members = { "channel", "type", "communication" }
                                        )
                                })
                                public class SomeEntity {
                                        private String[] names = new String[] {"foo", "bar" };
                                }
                                """
                )
        );
    }

}
