package com.fooddelivery.catalogservice.repository;

/** An entity's id and version, nothing else (cheap to read for a whole table). */
public interface IdVersion {

    Long getId();

    Long getVersion();
}
