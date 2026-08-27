package com.titanium.policy.query.scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.junit.jupiter.api.Test;

class DeadLetterQueueServiceTest {

    @Test
    void retriesProjectionDeadLetters() {
        Map<String, AtomicInteger> calls = new HashMap<>();
        DeadLetterQueueService deadLetterQueueService = serviceWithProcessors(calls, null);

        deadLetterQueueService.retryDeadLetterEvents();

        assertEquals(1, calls.get("policy-query-group").get());
    }

    @Test
    void containsRetryFailure() {
        Map<String, AtomicInteger> calls = new HashMap<>();
        DeadLetterQueueService deadLetterQueueService = serviceWithProcessors(calls, "policy-query-group");

        deadLetterQueueService.retryDeadLetterEvents();

        assertEquals(1, calls.get("policy-query-group").get());
    }

    private DeadLetterQueueService serviceWithProcessors(Map<String, AtomicInteger> calls, String failingGroup) {
        String[] groups = { "policy-query-group" };
        Map<String, SequencedDeadLetterProcessor<EventMessage<?>>> processors = new HashMap<>();
        for (String group : groups) {
            calls.put(group, new AtomicInteger());
            processors.put(group, processor(group, calls.get(group), failingGroup));
        }

        EventProcessingConfiguration configuration = (EventProcessingConfiguration) Proxy.newProxyInstance(
                EventProcessingConfiguration.class.getClassLoader(),
                new Class<?>[] { EventProcessingConfiguration.class },
                (proxy, method, args) -> {
                    if ("sequencedDeadLetterProcessor".equals(method.getName())) {
                        return Optional.ofNullable(processors.get(args[0]));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new DeadLetterQueueService(configuration);
    }

    @SuppressWarnings("unchecked")
    private SequencedDeadLetterProcessor<EventMessage<?>> processor(
            String group, AtomicInteger calls, String failingGroup) {
        return (SequencedDeadLetterProcessor<EventMessage<?>>) Proxy.newProxyInstance(
                SequencedDeadLetterProcessor.class.getClassLoader(),
                new Class<?>[] { SequencedDeadLetterProcessor.class },
                (proxy, method, args) -> {
                    if ("processAny".equals(method.getName()) || "process".equals(method.getName())) {
                        calls.incrementAndGet();
                        if (group.equals(failingGroup)) {
                            throw new IllegalStateException("retry failed");
                        }
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
