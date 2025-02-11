package com.ecpnv.openrewrite.jdo2jpa;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
public class BaseRewriteTest implements RewriteTest {

    public static final JavaParser.Builder<?, ?> PARSER = JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
                    Constants.Jpa.CLASS_PATH,
                    Constants.Jdo.CLASS_PATH,
                    Constants.SPRING_CLASS_PATH,
                    Constants.LOMBOK_CLASS_PATH);//lombok seems to be a special edge-case
}
