package com.logunify.logging.android;

import static com.logunify.logging.android.Logger.CONFIG_KEY_API_KEY;
import static com.logunify.logging.android.Logger.CONFIG_KEY_RECEIVER_URL;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.logunify.logging.Constants;
import com.logunify.logging.event.EventRecord;
import com.logunify.logging.http.ApiResponse;
import com.logunify.logging.http.HttpClient;

import java.io.IOException;
import java.util.List;

public class LogWorker extends Worker {
    /**
     * Maximum number of messages to send in one bulk request.
     */
    private static final int MAX_BULK_SIZE = 50;

    /**
     * Number of times to attempt to send batch request.
     */
    private static final int MAX_ATTEMPTS = 3;

    private HttpClient client;
    private final Context context;

    private SqliteEventQueue preflightQueue;

    public LogWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        this.client = new HttpClient(
                getInputData().getString(CONFIG_KEY_RECEIVER_URL),
                getInputData().getString(CONFIG_KEY_API_KEY)
        );
        this.preflightQueue = new SqliteEventQueue(context);

        long size = preflightQueue.size();
        Log.d(Constants.LOGGING_TAG, String.format("Worker (%s) started, message queue size: %d", getId(), size));
        if (size <= 0) {
            return Result.success();
        }

        // sendInBatches() only returns false if nothing was sent
        boolean success = sendInBatches();

        if (!success) {
            Log.e(Constants.LOGGING_TAG, String.format("Worker (%s) failed to send logs", getId()));
            return Result.failure();
        } else {
            Log.d(Constants.LOGGING_TAG, String.format("Worker (%s) succeeded in sending logs, message queue size: %d ", getId(), preflightQueue.size()));
            return Result.success();
        }
    }

    private boolean sendInBatches() {
        boolean success = false;
        List<EventRecord> batch = preflightQueue.peek(MAX_BULK_SIZE);
        do {
            if (sendEvents(batch)) {
                success = true;
                preflightQueue.remove(batch.size());
                batch = preflightQueue.peek(MAX_BULK_SIZE);
            } else {
                return success;
            }
        } while (batch.size() > 0);

        return true;
    }

    private boolean sendEvents(List<EventRecord> eventRecords) {
        return attemptSendEvents(eventRecords, MAX_ATTEMPTS);
    }

    private boolean attemptSendEvents(List<EventRecord> events, int leftAttempts) {
        if (leftAttempts == 0) {
            return false;
        }
        leftAttempts -= 1;
        AppMetadata appMetadata = new AppMetadata(
                getInputData().getInt(Logger.APP_METADATA_KEY_VERSION_CODE, 0),
                getInputData().getString(Logger.APP_METADATA_KEY_VERSION_NAME),
                getInputData().getString(Logger.APP_METADATA_KEY_INSTALLATION_ID)
        );
        try {
            Log.d(Constants.LOGGING_TAG, String.format("Attempting to send bulk request with %d events.", events.size()));
            ApiResponse response = client.sendEvents(events, appMetadata);

            if (!response.isSuccessful()) {
                Log.e(Constants.LOGGING_TAG, String.format("Worked (%s), received bad status code (%d) returned from api. Response: %s",
                        getId(), response.getCode(), response.getBody()));
                return false;
            } else if (response.hasError()) {
                // we just log the error, as we most likely cannot resolve the issue by retrying these documents
                Log.e(Constants.LOGGING_TAG, String.format("Worker (%s), unable to send all documents. Response: %s",
                        getId(),
                        response.getBody()));
                return false;
            }

            Log.d(Constants.LOGGING_TAG, String.format("Worker (%s), successfully sent all %d events. Response: %s",
                    getId(),
                    events.size(),
                    response.getBody()));
            return true;
        } catch (IOException e) {
            Log.e(Constants.LOGGING_TAG, String.format("Worked (%s), error while sending logs: %s", getId(), e.getMessage()), e);
            return attemptSendEvents(events, leftAttempts);
        }
    }
}
