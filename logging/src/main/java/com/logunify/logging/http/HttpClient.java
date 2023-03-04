package com.logunify.logging.http;

import android.util.Log;

import com.logunify.logging.Constants;
import com.logunify.logging.android.AppMetadata;
import com.logunify.logging.android.Utils;
import com.logunify.logging.event.EventRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpClient {
    static final String PAYLOAD_KEY_EVENTS = "events";
    static final String PAYLOAD_KEY_APP_METADATA = "app_metadata";

    private final OkHttpClient client = new OkHttpClient();
    private final String receiverUrl;
    private final String apiKey;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public HttpClient(String receiverUrl, String apiKey) {
        Utils.requireNonNull(receiverUrl);
        Utils.requireNonNull(apiKey);
        receiverUrl = receiverUrl.trim();
        if (receiverUrl.endsWith("/")) {
            receiverUrl = receiverUrl.substring(0, receiverUrl.length() - 1);
        }
        this.receiverUrl = receiverUrl;
        this.apiKey = apiKey;
    }

    RequestBody buildRequestBody(List<EventRecord> events, AppMetadata appMetadata) {
        JSONObject payloadJson = new JSONObject();
        try {
            JSONArray eventsInJson = new JSONArray();
            for (EventRecord event : events) {
                eventsInJson.put(event.toJson());
            }

            payloadJson.put(PAYLOAD_KEY_EVENTS, eventsInJson);
            payloadJson.put(PAYLOAD_KEY_APP_METADATA, appMetadata.toJSON());
        } catch (JSONException e) {
            Log.e(Constants.LOGGING_TAG, "Error when preparing JSON payload", e);
        }

        return RequestBody.create(
                payloadJson.toString(),
                MediaType.parse("application/json; charset=utf-8"));
    }

    public ApiResponse sendEvents(List<EventRecord> events, AppMetadata appMetadata) throws IOException {
        Request request = new Request.Builder()
                .addHeader(
                        "X-Auth-Token", apiKey)
                .addHeader(
                        "Content-Type", "application/json; charset=utf-8"
                )
                .url(String.format("%s", receiverUrl))
                .post(buildRequestBody(events, appMetadata))
                .build();
        Response response = client.newCall(request).execute();

        return ApiResponse.fromHttpResponse(response);
    }
}
