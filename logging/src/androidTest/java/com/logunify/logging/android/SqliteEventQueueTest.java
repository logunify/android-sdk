package com.logunify.logging.android;

import static org.junit.Assert.assertEquals;

import androidx.test.platform.app.InstrumentationRegistry;

import com.logunify.logging.event.Event;
import com.logunify.logging.event.EventRecord;
import com.test_project.UserActivitySchema;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class SqliteEventQueueTest {
    SqliteEventQueue queue;

    private final Event event1 = UserActivitySchema.UserActivity.newBuilder()
            .setEvent(UserActivitySchema.Event.IMPRESSION)
            .setSessionId(UUID.randomUUID().toString())
            .setUserId("uid")
            .build();
    private final Event event2 = UserActivitySchema.UserActivity.newBuilder()
            .setEvent(UserActivitySchema.Event.CLICK)
            .setSessionId(UUID.randomUUID().toString())
            .setUserId("uid")
            .build();

    @Before
    public void setUp() throws Exception {
        queue = new SqliteEventQueue(InstrumentationRegistry.getInstrumentation().getTargetContext());

        queue.clear();
    }

    @Test
    public void testEnqueueAndPeek() {
        queue.enqueue(event1);
        queue.enqueue(event2);

        List<EventRecord> eventRecords = queue.peek(2);
        assertEquals(eventRecords.size(), 2);

        EventRecord eventRecord1 = eventRecords.get(0);
        assertEquals(eventRecord1.getSerializedEvent(), event1.serialize());
        assertEquals(eventRecord1.getProjectName(), event1.getProjectName());
        assertEquals(eventRecord1.getSchemaName(), event1.getSchemaName());

        EventRecord eventRecord2 = eventRecords.get(1);
        assertEquals(eventRecord2.getSerializedEvent(), event2.serialize());
        assertEquals(eventRecord2.getProjectName(), event2.getProjectName());
        assertEquals(eventRecord2.getSchemaName(), event2.getSchemaName());

        assertEquals(queue.size(), 2);
    }

    @Test
    public void testRemove() {
        queue.enqueue(event1);
        queue.enqueue(event2);
        assertEquals(queue.size(), 2);

        queue.remove(1);
        List<EventRecord> eventRecords = queue.peek(1);
        assertEquals(eventRecords.size(), 1);

        EventRecord remainingEvent = eventRecords.get(0);
        assertEquals(remainingEvent.getSerializedEvent(), event2.serialize());
        assertEquals(remainingEvent.getProjectName(), event2.getProjectName());
        assertEquals(remainingEvent.getSchemaName(), event2.getSchemaName());

        queue.remove(1);
        assertEquals(queue.size(), 0);
    }
}
