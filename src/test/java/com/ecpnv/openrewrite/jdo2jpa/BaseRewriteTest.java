package com.ecpnv.openrewrite.jdo2jpa;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

public class BaseRewriteTest implements RewriteTest {

    public final static JavaParser.Builder<?, ?> PARSER = JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), Constants.JPA_CLASS_PATH, Constants.JDO_CLASS_PATH);
}
