package com.ecpnv.openrewrite.jdo2jpa;

import com.ecpnv.openrewrite.util.JavaParserFactory;

import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
public class BaseRewriteTest implements RewriteTest {

    static {
        System.setProperty("libraryOfAbstractClassName", "jdo2jpa-abstract");//hack to include test jar in rewrite recipe
    }

    public static final JavaParser.Builder<?, ?> PARSER = JavaParserFactory.create();
}
