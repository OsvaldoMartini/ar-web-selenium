package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerGridPublisherTest {

    private final Gson gson = new Gson();

    @Test
    void publishesSearchTermsWithoutChangingPayload() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload("one", "two", "three");

        publisher.publishSearchTerms("scannerGrid", 2, payload);

        assertEquals(1, sender.messages.size());
        assertEquals("scannerGrid", sender.messages.get(0).sessionId);
        assertEquals(2, sender.messages.get(0).homeBankingId);
        assertEquals("searchTerms", sender.messages.get(0).operationId);
        assertEquals(3, payload.getElementDetails().length);
    }

    @Test
    void publishesChunksWithoutMutatingOriginalPayload() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload("one", "two", "three", "four", "five");

        publisher.publishSearchTermsChunks("scannerGrid", 2, payload, 2);

        assertEquals(3, sender.messages.size());
        assertEquals(List.of(2, 2, 1), sender.messages.stream()
                .map(message -> gson.fromJson(message.json, SplitDTO.class).getElementDetails().length)
                .toList());
        assertEquals(5, payload.getElementDetails().length);
        assertEquals("one", payload.getElementDetails()[0].getDefinedName());
        assertEquals("five", payload.getElementDetails()[4].getDefinedName());
    }

    @Test
    void rejectsNonPositiveChunkSize() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload("one");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> publisher.publishSearchTermsChunks("scannerGrid", 2, payload, 0));

        assertEquals("Scanner chunk size must be positive", error.getMessage());
        assertEquals(0, sender.messages.size());
        assertEquals(1, payload.getElementDetails().length);
    }

    private SplitDTO payload(String... names) {
        SplitDTO payload = new SplitDTO();
        payload.setSessionId("scannerGrid");
        payload.setOperationId("searchTerms");
        payload.setHomeBankingId(2);
        payload.setBotJobId(42);
        ElementDTO[] elements = new ElementDTO[names.length];
        for (int i = 0; i < names.length; i++) {
            ElementDTO element = new ElementDTO();
            element.setDefinedName(names[i]);
            elements[i] = element;
        }
        payload.setElementDetails(elements);
        return payload;
    }

    private static final class RecordingSender implements ScannerGridPublisher.Sender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            messages.add(new Message(homeBankingId, sessionId, json, operationId));
        }
    }

    private record Message(int homeBankingId, String sessionId, String json, String operationId) {}
}
