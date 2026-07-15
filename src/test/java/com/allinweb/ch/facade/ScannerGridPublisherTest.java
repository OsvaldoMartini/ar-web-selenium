package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScannerGridPublisherTest {

    private final Gson gson = new Gson();

    @Test
    void publishesSearchTermsWithoutChangingPayload() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload(publisher, "one", "two", "three");

        publisher.publishSearchTerms(publisher.destinationSessionId(), 2, payload);

        assertEquals(1, sender.messages.size());
        assertEquals(publisher.destinationSessionId(), sender.messages.get(0).sessionId);
        assertEquals(2, sender.messages.get(0).homeBankingId);
        assertEquals(publisher.searchTermsOperationId(), sender.messages.get(0).operationId);
        assertEquals(3, payload.getElementDetails().length);
    }

    @Test
    void publishesScannerGridSearchTermsWithoutCallerSupplyingSessionId() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload(publisher, "one");

        publisher.publishScannerGridSearchTerms(2, payload);

        assertEquals(1, sender.messages.size());
        assertEquals(publisher.destinationSessionId(), sender.messages.get(0).sessionId);
        assertEquals(publisher.searchTermsOperationId(), sender.messages.get(0).operationId);
    }

    @Test
    void publishesScannerGridSearchTermsPayloadWithoutCallerSupplyingOperation() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);

        publisher.publishScannerGridSearchTermsPayload(2, java.util.Map.of("id", 42));

        assertEquals(1, sender.messages.size());
        assertEquals(publisher.destinationSessionId(), sender.messages.get(0).sessionId);
        assertEquals(publisher.searchTermsOperationId(), sender.messages.get(0).operationId);
        assertEquals("{\"id\":42}", sender.messages.get(0).json);
    }

    @Test
    void publishesScannerGridPayloadWithCallerSuppliedOperation() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);

        publisher.publishScannerGrid(2, java.util.Map.of("ok", true), "clonedElement");

        assertEquals(1, sender.messages.size());
        assertEquals(2, sender.messages.get(0).homeBankingId);
        assertEquals(publisher.destinationSessionId(), sender.messages.get(0).sessionId);
        assertEquals("clonedElement", sender.messages.get(0).operationId);
        assertEquals("{\"ok\":true}", sender.messages.get(0).json);
    }

    @Test
    void exposesDestinationSessionId() {
        ScannerGridPublisher publisher = new ScannerGridPublisher(new RecordingSender());

        assertEquals(ScannerSearchRoute.standardPageScanner().destinationSessionId(), publisher.destinationSessionId());
    }

    @Test
    void exposesSearchTermsOperationId() {
        ScannerGridPublisher publisher = new ScannerGridPublisher(new RecordingSender());

        assertEquals(ScannerSearchRoute.standardPageScanner().operationId(), publisher.searchTermsOperationId());
    }

    @Test
    void publishesChunksWithoutMutatingOriginalPayload() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload(publisher, "one", "two", "three", "four", "five");

        publisher.publishSearchTermsChunks(publisher.destinationSessionId(), 2, payload, 2);

        assertEquals(3, sender.messages.size());
        assertEquals(List.of(2, 2, 1), sender.messages.stream()
                .map(message -> gson.fromJson(message.json, SplitDTO.class).getElementDetails().length)
                .toList());
        assertEquals(5, payload.getElementDetails().length);
        assertEquals("one", payload.getElementDetails()[0].getDefinedName());
        assertEquals("five", payload.getElementDetails()[4].getDefinedName());
    }

    @Test
    void publishesScannerGridSearchTermChunksWithoutCallerSupplyingSessionId() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload(publisher, "one", "two", "three");

        publisher.publishScannerGridSearchTermsChunks(2, payload, 2);

        assertEquals(2, sender.messages.size());
        assertEquals(List.of(publisher.destinationSessionId(), publisher.destinationSessionId()),
                sender.messages.stream().map(message -> message.sessionId).toList());
        assertEquals(List.of(2, 1), sender.messages.stream()
                .map(message -> gson.fromJson(message.json, SplitDTO.class).getElementDetails().length)
                .toList());
    }

    @Test
    void rejectsNonPositiveChunkSize() {
        RecordingSender sender = new RecordingSender();
        ScannerGridPublisher publisher = new ScannerGridPublisher(sender);
        SplitDTO payload = payload(publisher, "one");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> publisher.publishSearchTermsChunks(publisher.destinationSessionId(), 2, payload, 0));

        assertEquals("Scanner chunk size must be positive", error.getMessage());
        assertEquals(0, sender.messages.size());
        assertEquals(1, payload.getElementDetails().length);
    }

    @Test
    void createsSearchTermsPayloadWithScannerGridContractFields() {
        ScannerGridPublisher publisher = new ScannerGridPublisher(new RecordingSender());
        ElementDTO element = new ElementDTO();
        element.setDefinedName("Login");

        SplitDTO payload = publisher.searchTermsPayload(
                7,
                42,
                "Payments",
                new ElementDTO[] {element},
                List.of(Map.of("blockId", 91, "blockOrderNumber", 1, "blockName", "Login")));

        assertEquals(7, payload.getHomeBankingId());
        assertEquals(42, payload.getBotJobId());
        assertEquals("Payments", payload.getBotJobName());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TOOL, payload.getType());
        assertEquals(publisher.destinationSessionId(), payload.getSessionId());
        assertEquals(publisher.searchTermsOperationId(), payload.getOperationId());
        assertEquals(1, payload.getElementDetails().length);
        assertEquals("Login", payload.getElementDetails()[0].getDefinedName());
        assertEquals(1, payload.getBlocks().size());
    }

    private SplitDTO payload(ScannerGridPublisher publisher, String... names) {
        SplitDTO payload = new SplitDTO();
        payload.setSessionId(publisher.destinationSessionId());
        payload.setOperationId(publisher.searchTermsOperationId());
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
