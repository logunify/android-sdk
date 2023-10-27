package com.logunify.logging.http;

import android.util.Log;

import com.logunify.logging.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Response;

/**
 * Api response wrapper.
 */
public class ApiResponse {
    private Response httpResponse;
    private JSONObject json;
    private String body;

    private ApiResponse(Response httpResponse) {
        this.httpResponse = httpResponse;

        if (httpResponse.body() == null) {
            Log.e(Constants.LOGGING_TAG, "Unable to parse response");
        }
        try {
            this.body = httpResponse.body().string();
            this.json = new JSONObject(body);
        } catch (JSONException e) {
            Log.e(Constants.LOGGING_TAG, "Unable to deserialize json response", e);
        } catch (IOException e) {
            Log.e(Constants.LOGGING_TAG, "IO exception while reading body", e);
        }
    }

    /**
     * Parses the response body as json and returns the wrapped response.
     *
     * @param httpResponse original http response
     * @return wrapped response
     */
    public static ApiResponse fromHttpResponse(Response httpResponse) {
        return new ApiResponse(httpResponse);
    }

    public boolean isSuccessful() {
        return httpResponse.isSuccessful();
    }

    public Map<String, String> getErrors() {
        return new HashMap<>();
    }

    public String getBody() {
        return body;
    }

    public boolean hasError() {
        return !isSuccessful();
    }

    public int getCode() {
        return httpResponse.code();
    }
}