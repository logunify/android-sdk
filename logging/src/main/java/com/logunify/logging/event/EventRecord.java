package com.logunify.logging.event;

import org.json.JSONException;
import org.json.JSONObject;

public class EventRecord {
    private static final String JSON_KEY_SERIALIZED_EVENT = "serializedEvent";
    private static final String JSON_KEY_SCHEMA_NAME = "schemaName";
    private static final String JSON_KEY_PROJECT_NAME = "projectName";

    private final String serializedEvent;
    private final String schemaName;
    private final String projectName;

    public EventRecord(String serializedEvent, String schemaName, String projectName) {
        this.serializedEvent = serializedEvent;
        this.schemaName = schemaName;
        this.projectName = projectName;
    }

    public String getSerializedEvent() {
        return serializedEvent;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getProjectName() {
        return projectName;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put(JSON_KEY_SERIALIZED_EVENT, serializedEvent);
        jsonObj.put(JSON_KEY_SCHEMA_NAME, schemaName);
        jsonObj.put(JSON_KEY_PROJECT_NAME, projectName);

        return jsonObj;
    }
}
