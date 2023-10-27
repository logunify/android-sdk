package com.logunify.logging.android;

import org.json.JSONException;
import org.json.JSONObject;

public class AppMetadata {
    private static final String JSON_KEY_VERSION_CODE = "versionCode";
    private static final String JSON_KEY_VERSION_NAME = "versionName";
    private static final String JSON_KEY_INSTALLATION_ID = "installationId";

    private final int versionCode;
    private final String versionName;
    private final String installationID;

    public AppMetadata(int versionCode, String versionName, String installationID) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.installationID = installationID;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getInstallationID() {
        return installationID;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put(JSON_KEY_VERSION_CODE, versionCode);
        jsonObj.put(JSON_KEY_VERSION_NAME, versionName);
        jsonObj.put(JSON_KEY_INSTALLATION_ID, installationID);

        return jsonObj;
    }
}
