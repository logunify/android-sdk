package com.logunify.logging.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.logunify.logging.android.AppMetadata;
import com.logunify.logging.event.EventRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HttpClientTest {
    private MockWebServer mockWebServer;
    private HttpClient httpClient;
    private List<EventRecord> events;
    private AppMetadata appMetadata;

    @Before
    public void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        httpClient = new HttpClient(mockWebServer.url("").toString(), "test_key");

        events = new ArrayList<>();
        events.add(new EventRecord("serialized_event_1", "test_schema", "test_project"));
        events.add(new EventRecord("serialized_event_2", "test_schema", "test_project"));

        appMetadata = new AppMetadata(0, "v", "iid");
    }

    @Test
    public void testSendEventsSuccessfully() throws IOException, InterruptedException, JSONException {
        JSONObject successBody = new JSONObject();
        successBody.put("success", true);
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(successBody.toString()));

        ApiResponse apiResponse = httpClient.sendEvents(events, appMetadata);
        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertTrue(apiResponse.isSuccessful());
        assertFalse(apiResponse.hasError());

        JSONArray jsonArray = new JSONArray();
        for (EventRecord event : events) {
            jsonArray.put(event.toJson());
        }
        JSONObject jsonRequestBody = new JSONObject();
        jsonRequestBody.put(HttpClient.PAYLOAD_KEY_EVENTS, jsonArray);
        jsonRequestBody.put(HttpClient.PAYLOAD_KEY_APP_METADATA, appMetadata.toJSON());

        assertEquals(
                recordedRequest.getBody().readUtf8(),
                jsonRequestBody.toString()
        );
    }

    @Test
    public void testSendEventsUnsuccessfully() throws IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("error"));

        ApiResponse apiResponse = httpClient.sendEvents(events, appMetadata);
        assertTrue(apiResponse.hasError());
        assertFalse(apiResponse.isSuccessful());
    }
}
