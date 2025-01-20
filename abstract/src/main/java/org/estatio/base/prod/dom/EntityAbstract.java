package org.estatio.base.prod.dom;


import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Version;

import org.apache.isis.applib.annotation.PropertyLayout;
import org.apache.isis.applib.annotation.Where;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Base class for entities
 */
@MappedSuperclass
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Getter
public abstract class EntityAbstract {

    @PropertyLayout(hidden = Where.EVERYWHERE)
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @EqualsAndHashCode.Include
    private Long id;

    @PropertyLayout(fieldSetId = "metadata")
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}