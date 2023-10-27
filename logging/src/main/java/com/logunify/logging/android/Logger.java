package com.logunify.logging.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.logunify.logging.Constants;
import com.logunify.logging.event.Event;

import java.util.concurrent.TimeUnit;

public class Logger {
    static final String CONFIG_KEY_RECEIVER_URL = "LogunifyReceiverUrl";
    static final String CONFIG_KEY_API_KEY = "LogunifyAPIKey";

    static final String APP_METADATA_KEY_VERSION_NAME = "versionName";
    static final String APP_METADATA_KEY_VERSION_CODE = "versionCode";
    static final String APP_METADATA_KEY_INSTALLATION_ID = "installationID";

    static final String FLUSH_WORKER_TAG = "com.logunify.logging.android.LogWorker.unconstrained";
    static final String INTERVAL_WORKER_TAG = "com.logunify.logging.android.LogWorker.interval";
    static final String ON_ENQUEUE_WORKER_TAG = "com.logunify.logging.android.LogWorker.onEnqueue";

    static final String RECEIVER_URL = "https://localhost:3000/api/events/_bulk";

    //Maximum number of messages to cache when offline.
    final static int MAX_OFFLINE_MESSAGES = 5000;
    // Minimum number of messages before sending request.
    final static int MIN_BATCH_SIZE = 10;
    // Minimum time between sending requests.
    final static int MIN_TIME_DELAY = 10 * 1000;
    // Max time between sending requests. Android has the had limitation of 15 mins mix.
    static final int PERIODIC_TIME_INTERVAL = 15;

    WorkRequest lastScheduledOneTimeWorkRequest;
    WorkRequest lastScheduledPeriodicWorkRequest;
    WorkManager workerManager;

    String versionName;
    Integer versionCode;
    String installationID;

    SqliteEventQueue preflightQueue;
    long lastScheduled = -1;

    String apiKey;
    String receiverUrl;

    private static Logger instance;

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void init(Context context) {
        if (!isInitialized()) {
            Utils.requireNonNull(context);

            Logger logger = new Logger();
            logger.installationID = Installation.id(context);
            logger.preflightQueue = new SqliteEventQueue(context, MAX_OFFLINE_MESSAGES);
            logger.lastScheduled = SystemClock.elapsedRealtime();
            logger.workerManager = WorkManager.getInstance(context);

            logger.config(context);
            logger.schedulePeriodicWorker();

            instance = logger;
        }
    }

    public static Logger getInstance() {
        if (Logger.isInitialized()) {
            return instance;
        }
        throw new NullPointerException("logunify logger  is not initialized");
    }

    private void config(Context context) {
        Bundle data = null;
        try {
            data = context.getPackageManager().getApplicationInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA).metaData;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        // required fields
        if (!data.containsKey(CONFIG_KEY_API_KEY)) {
            throw new RuntimeException("Please provide <meta-data name=\"" + CONFIG_KEY_API_KEY + "\" value=\"your_api_key\">");
        }
        apiKey = data.getString(CONFIG_KEY_API_KEY);

        // retrieve version name and version code
        PackageInfo pInfo;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = pInfo.versionName;
            versionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Constants.LOGGING_TAG, e.getMessage(), e);
            versionName = "n/a";
            versionCode = -1;
        }

        receiverUrl = data.getString(CONFIG_KEY_RECEIVER_URL, RECEIVER_URL);

        Log.d(Constants.LOGGING_TAG, String.format("logunify is configured:\n"
                        + "  API Key:                                %s\n"
                        + "  Receiver URL:                           %s\n"
                        + "  App Version Name                        %s\n"
                        + "  App Version Code                        %d\n"
                        + "  Installation Id                         %s\n",
                apiKey, receiverUrl, versionName, versionCode, installationID));
    }

    public void logEvent(Event event) {
        if (preflightQueue == null) {
            Log.e(Constants.LOGGING_TAG, "Message queue has not been initialized, message dropped.");
            return;
        }

        Utils.requireNonNull(event);

        preflightQueue.enqueue(event);
        if (preflightQueue.size() == MAX_OFFLINE_MESSAGES) {
            Log.d(
                    Constants.LOGGING_TAG,
                    "Message queue overflowing (" + preflightQueue.size() + " > " + MAX_OFFLINE_MESSAGES + "), some logs might be lost."
            );
        }

        Log.v(Constants.LOGGING_TAG, String.format("Logged event: %s", event));
        Log.v(Constants.LOGGING_TAG, String.format("Logged, with %d events in the queue", preflightQueue.size()));

        if (preflightQueue.size() >= MIN_BATCH_SIZE) {
            // Schedule immediately exceeding the min batch size
            scheduleConstrainedWorker(false);
            lastScheduled = SystemClock.elapsedRealtime();
            Log.d(Constants.LOGGING_TAG, String.format("Scheduled a one time worker to send event batch with %d events to execute immediately", preflightQueue.size()));
        } else if (lastScheduled == -1 || SystemClock.elapsedRealtime() - lastScheduled > MIN_TIME_DELAY) {
            // Otherwise we schedule the job with a delay so the message can still be sent before we reach the min batch size in the event queue,
            // we do this because periodic job cannot have interval less than 15 mins
            scheduleConstrainedWorker(true);
            lastScheduled = SystemClock.elapsedRealtime();
            Log.d(Constants.LOGGING_TAG, String.format("Scheduled a one time worker to send event batch with %d events to executed in %d seconds", preflightQueue.size(), MIN_TIME_DELAY));
        }
    }

    public String getVersionName() {
        return versionName;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public String getInstallationID() {
        return installationID;
    }

    private void scheduleConstrainedWorker(boolean withDelay) {
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(LogWorker.class)
                .addTag(FLUSH_WORKER_TAG)
                .setInputData(getWorkerData())
                .setConstraints(getWorkerConstraints());
        ExistingWorkPolicy existingWorkPolicy = ExistingWorkPolicy.REPLACE;
        if (withDelay) {
            builder.setInitialDelay(MIN_TIME_DELAY, TimeUnit.MILLISECONDS);
            existingWorkPolicy = ExistingWorkPolicy.KEEP;
        }

        OneTimeWorkRequest workRequest = builder.build();
        workerManager.enqueueUniqueWork(
                ON_ENQUEUE_WORKER_TAG,
                existingWorkPolicy,
                workRequest
        );
        lastScheduledOneTimeWorkRequest = workRequest;
    }

    private void schedulePeriodicWorker() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                LogWorker.class, PERIODIC_TIME_INTERVAL, TimeUnit.MINUTES)
                .addTag(INTERVAL_WORKER_TAG)
                .setInputData(getWorkerData())
                .setConstraints(getWorkerConstraints())
                .build();

        workerManager.enqueueUniquePeriodicWork(INTERVAL_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP, workRequest);

        lastScheduledPeriodicWorkRequest = workRequest;
    }

    private Data getWorkerData() {
        return new Data.Builder()
                .putString(CONFIG_KEY_RECEIVER_URL, receiverUrl)
                .putString(CONFIG_KEY_API_KEY, apiKey)
                .putString(APP_METADATA_KEY_VERSION_NAME, versionName)
                .putInt(APP_METADATA_KEY_VERSION_CODE, versionCode)
                .putString(APP_METADATA_KEY_INSTALLATION_ID, installationID)
                .build();
    }

    private Constraints getWorkerConstraints() {
        // @TODO: Make it customizable
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build();
    }
}
