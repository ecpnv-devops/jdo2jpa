package com.ecpnv.openrewrite.jdo2jpa;

public final class Constants {

    private Constants() {
    }

    public static final String REWRITE_ANNOTATION_PREFIX = "/*~~>*/";

    public static class Jdo {

        private Jdo() {
        }

        public static final String CLASS_PATH = "jdo-api";
        public static final String BASE_PACKAGE = "javax.jdo.annotations.";

        public static final String COLUMN_ANNOTATION_NAME = "Column";
        public static final String COLUMN_ANNOTATION_FULL = BASE_PACKAGE + COLUMN_ANNOTATION_NAME;
        public final static String COLUMN_ARGUMENT_ALLOWS_NULL = "allowsNull";
        public static final String ARGUMENT_NAME = "name";
        public static final String DISCRIMINATOR_ANNOTATION_NAME = "Discriminator";
        public static final String DISCRIMINATOR_ANNOTATION_FULL = BASE_PACKAGE + DISCRIMINATOR_ANNOTATION_NAME;
        public static final String DISCRIMINATOR_STRATEGY_ANNOTATION_NAME = "DiscriminatorStrategy";
        public static final String DISCRIMINATOR_STRATEGY_ANNOTATION_FULL = BASE_PACKAGE + DISCRIMINATOR_STRATEGY_ANNOTATION_NAME;
        public static final String ELEMENT_ANNOTATION_NAME = "Element";
        public static final String ELEMENT_ANNOTATION_FULL = BASE_PACKAGE + ELEMENT_ANNOTATION_NAME;
        public static final String INDEX_ANNOTATION_NAME = "Index";
        public static final String INDEX_ANNOTATION_FULL = BASE_PACKAGE + INDEX_ANNOTATION_NAME;
        public static final String INHERITANCE_ANNOTATION_NAME = "Inheritance";
        public static final String INHERITANCE_ANNOTATION_FULL = BASE_PACKAGE + INHERITANCE_ANNOTATION_NAME;
        public static final String INHERITANCE_ARGUMENT_STRATEGY = "strategy";
        public static final String JOIN_ANNOTATION_NAME = "Join";
        public static final String JOIN_ANNOTATION_FULL = BASE_PACKAGE + JOIN_ANNOTATION_NAME;
        public static final String JOIN_ARGUMENT_COLUMN = "column";
        public static final String NON_PERSISTENT_NAME = "NotPersistent";
        public static final String NON_PERSISTENT_FULL = BASE_PACKAGE + NON_PERSISTENT_NAME;
        public static final String PERSISTENCE_CAPABLE_ANNOTATION_NAME = "PersistenceCapable";
        public static final String PERSISTENCE_CAPABLE_ANNOTATION_FULL = BASE_PACKAGE + PERSISTENCE_CAPABLE_ANNOTATION_NAME;
        public static final String PERSISTENT_ANNOTATION_NAME = "Persistent";
        public static final String PERSISTENT_ANNOTATION_FULL = BASE_PACKAGE + PERSISTENT_ANNOTATION_NAME;
        public static final String PERSISTENT_ARGUMENT_DEPENDENT_ELEMENT = "dependentElement";
        public static final String PERSISTENT_ARGUMENT_DEFAULT_FETCH_GROUP = "defaultFetchGroup";
        public static final String PERSISTENT_ARGUMENT_TABLE = "table";
    }

    public static class Jpa {
        private Jpa() {
        }

        public static final String CLASS_PATH = "jakarta.persistence-api";
        public static final String BASE_PACKAGE = "javax.persistence.";

        public static final String COLUMN_ANNOTATION_NAME = "Column";
        public static final String COLUMN_ANNOTATION_FULL = BASE_PACKAGE + COLUMN_ANNOTATION_NAME;
        public static final String DISCRIMINATOR_VALUE_ANNOTATION_NAME = "DiscriminatorValue";
        public static final String DISCRIMINATOR_VALUE_ANNOTATION_FULL = BASE_PACKAGE + DISCRIMINATOR_VALUE_ANNOTATION_NAME;
        public static final String ENTITY_ANNOTATION_NAME = "Entity";
        public static final String ENTITY_ANNOTATION_FULL = BASE_PACKAGE + ENTITY_ANNOTATION_NAME;
        public static final String INDEX_ANNOTATION_NAME = "Index";
        public static final String INDEX_ANNOTATION_FULL = BASE_PACKAGE + INDEX_ANNOTATION_NAME;
        public static final String INDEX_ANNOTATION_COLUMN_LIST_ATTRIBUTE = "columnList";
        public static final String INHERITANCE_ANNOTATION_NAME = "Inheritance";
        public static final String INHERITANCE_ANNOTATION_FULL = BASE_PACKAGE + INHERITANCE_ANNOTATION_NAME;
        public static final String JOIN_COLUMN_ANNOTATION_NAME = "JoinColumn";
        public static final String JOIN_COLUMN_ANNOTATION_FULL = BASE_PACKAGE + JOIN_COLUMN_ANNOTATION_NAME;
        public static final String JOIN_TABLE_ANNOTATION_NAME = "JoinTable";
        public static final String JOIN_TABLE_ANNOTATION_FULL = BASE_PACKAGE + JOIN_TABLE_ANNOTATION_NAME;
        public static final String ONE_TO_ONE_ANNOTATION_NAME = "OneToOne";
        public static final String ONE_TO_ONE_ANNOTATION_FULL = BASE_PACKAGE + ONE_TO_ONE_ANNOTATION_NAME;
        public static final String ONE_TO_MANY_ANNOTATION_NAME = "OneToMany";
        public static final String ONE_TO_MANY_ANNOTATION_FULL = BASE_PACKAGE + ONE_TO_MANY_ANNOTATION_NAME;
        public static final String ONE_TO_MANY_ARGUMENT_MAPPED_BY = "mappedBy";
        public static final String MANY_TO_ONE_ANNOTATION_NAME = "ManyToOne";
        public static final String MANY_TO_ONE_ANNOTATION_FULL = BASE_PACKAGE + MANY_TO_ONE_ANNOTATION_NAME;
        public static final String TABLE_ANNOTATION_NAME = "Table";
        public static final String TABLE_ANNOTATION_FULL = BASE_PACKAGE + TABLE_ANNOTATION_NAME;
        public static final String TABLE_ARGUMENT_SCHEMA = "schema";
        public final static String TABLE_ARGUMENT_TABLE = "table";
        public static final String TABLE_ARGUMENT_UNIQUE_CONSTRAINTS = "uniqueConstraints";
        public static final String TABLE_ARGUMENT_INDEXES = "indexes";
        public static final String TRANSIENT_ANNOTATION_NAME = "Transient";
        public static final String TRANSIENT_ANNOTATION_FULL = BASE_PACKAGE + TRANSIENT_ANNOTATION_NAME;
        public static final String UNIQUE_CONSTRAINT_ANNOTATION_NAME = "UniqueConstraint";
        public static final String UNIQUE_CONSTRAINT_ANNOTATION_FULL = BASE_PACKAGE + UNIQUE_CONSTRAINT_ANNOTATION_NAME;

        public static final String CASCADE_TYPE_FULL = BASE_PACKAGE + "CascadeType";
        public static final String FETCH_TYPE_FULL = BASE_PACKAGE + "FetchType";

        public static final String MIGRATION_COMMENT = "TODO: manually migrate to JPA";
    }

    public static final String LOMBOK_CLASS_PATH = "lombok";
    public static final String SPRING_CONTEXT_CLASS_PATH = "spring-context";
    public static final String SPRING_BOOT_AUTOCONFIGURATION_CLASS_PATH = "spring-boot-autoconfigure";
    public static final String JODA_TIME_CLASS_PATH = "joda-time";
    public static final String JAVAX_INJECT = "javax.inject";

}
