package com.logunify.logging.android;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkRequest;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.logunify.logging.event.Event;
import com.logunify.logging.event.EventRecord;
import com.test_project.UserActivitySchema;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class LoggerTest {
    private static MockWebServer mockWebServer;
    private static Logger logger;
    private static TestDriver testDriver;
    private static AppMetadata appMetadata;

    @BeforeClass
    public static void beforeClass() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Bundle metaData = context.getPackageManager().getApplicationInfo(context.getPackageName(),
                PackageManager.GET_META_DATA).metaData;
        metaData.putString(Logger.CONFIG_KEY_RECEIVER_URL, mockWebServer.url("").toString());

        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        testDriver = WorkManagerTestInitHelper.getTestDriver(context);

        Logger.init(context);
        logger = Logger.getInstance();

        appMetadata = new AppMetadata(
                logger.versionCode,
                logger.versionName,
                logger.installationID
        );
    }

    @Before
    public void setUp() {
        logger.lastScheduled = -1;
        logger.preflightQueue.clear();
    }

    @Test
    public void testLogWithImmediateOnetimeWorkJob() throws JSONException, InterruptedException, ExecutionException, TimeoutException {
        JSONObject successBody = new JSONObject();
        successBody.put("success", true);
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(successBody.toString()));

        List<Event> events = generateAndPublishEvents(Logger.MIN_BATCH_SIZE);
        testDriver.setAllConstraintsMet(logger.lastScheduledOneTimeWorkRequest.getId());

        validateRequestToServer(events);
        assertEquals(logger.lastScheduledOneTimeWorkRequest.getWorkSpec().initialDelay, 0);

        waitTilWorkIsFinished(logger.lastScheduledOneTimeWorkRequest);
    }

    @Test
    public void testLogWithDelayedOnetimeWorkJob() throws JSONException, InterruptedException, ExecutionException, TimeoutException {
        JSONObject successBody = new JSONObject();
        successBody.put("success", true);
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(successBody.toString()));

        List<Event> events = generateAndPublishEvents(2);
        testDriver.setAllConstraintsMet(logger.lastScheduledOneTimeWorkRequest.getId());
        testDriver.setInitialDelayMet(logger.lastScheduledOneTimeWorkRequest.getId());

        validateRequestToServer(events);
        assertEquals(logger.lastScheduledOneTimeWorkRequest.getWorkSpec().initialDelay, Logger.MIN_TIME_DELAY);

        waitTilWorkIsFinished(logger.lastScheduledOneTimeWorkRequest);
    }

    @Test
    public void testLogWithPeriodicWorkJob() throws JSONException, InterruptedException, ExecutionException, TimeoutException {
        JSONObject successBody = new JSONObject();
        successBody.put("success", true);
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(successBody.toString()));

        List<Event> events = generateAndPublishEvents(2);
        testDriver.setAllConstraintsMet(logger.lastScheduledPeriodicWorkRequest.getId());
        testDriver.setPeriodDelayMet(logger.lastScheduledPeriodicWorkRequest.getId());

        validateRequestToServer(events);

        waitTilWorkIsFinished(logger.lastScheduledPeriodicWorkRequest);
    }

    private void validateRequestToServer(List<Event> events) throws JSONException, InterruptedException {
        JSONArray jsonArray = new JSONArray();
        for (Event event : events) {
            jsonArray.put(
                    new EventRecord(event.serialize(), event.getSchemaName(), event.getProjectName()).toJson()
            );
        }
        JSONObject jsonRequestBody = new JSONObject();
        jsonRequestBody.put("events", jsonArray);
        jsonRequestBody.put("app_metadata", appMetadata.toJSON());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals(
                recordedRequest.getBody().readUtf8(),
                jsonRequestBody.toString()
        );
    }

    private List<Event> generateAndPublishEvents(int numEvents) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < numEvents; ++i) {
            Event event = createEvent();
            events.add(event);
            logger.logEvent(event);
        }

        return events;
    }

    private void waitTilWorkIsFinished(WorkRequest request) throws TimeoutException {
        int attempts = 0;
        while (
                !logger.workerManager.getWorkInfoById(request.getId()).isDone() &&
                        attempts++ < 10
        ) {
            SystemClock.sleep(100);
        }

        if (logger.workerManager.getWorkInfoById(request.getId()).isDone()) {
            return;
        }
        throw new TimeoutException();
    }

    private Event createEvent() {
        return UserActivitySchema.UserActivity.newBuilder()
                .setEvent(UserActivitySchema.Event.CLICK)
                .setSessionId(UUID.randomUUID().toString())
                .setUserId("uid")
                .build();
    }
}
