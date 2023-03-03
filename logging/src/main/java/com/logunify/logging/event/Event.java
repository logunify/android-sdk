package com.logunify.logging.event;

public interface Event {
    String getSchemaName();

    String getProjectName();

    String serialize();
}
