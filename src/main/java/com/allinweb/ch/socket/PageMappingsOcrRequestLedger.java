package com.allinweb.ch.socket;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import javax.websocket.Session;

/**
 * Bounded in-flight subscriber and successful-Apply replay ledger for Page Mappings OCR requests.
 *
 * <p>The ledger deliberately owns no OCR or persistence work. It only linearizes request admission
 * and terminal recipients so reconnecting transports can attach to an already accepted request.
 */
final class PageMappingsOcrRequestLedger {

    static final String APPLY_RESPONSE_OPERATION = "pageMappings.ocrReviewApplyResponse";

    enum Disposition {
        START,
        ATTACHED,
        REPLAY,
        BUSY,
        CONFLICT
    }

    record Subscriber(
            int fallbackHomeBankingId,
            String sessionId,
            Session transport,
            PageMappingsWorkspaceService.OcrAuthority authority,
            JsonObject request) {
        Subscriber {
            sessionId = Objects.requireNonNull(sessionId, "OCR subscriber session is required");
            transport = Objects.requireNonNull(transport, "OCR subscriber transport is required");
            authority = Objects.requireNonNull(authority, "OCR subscriber authority is required");
            request = request == null ? null : request.deepCopy();
        }

        @Override
        public JsonObject request() {
            return request == null ? null : request.deepCopy();
        }
    }

    record Ticket(String key, String payload, boolean replaySuccessfulResponse) {
        Ticket {
            key = Objects.requireNonNull(key, "OCR request key is required");
            payload = Objects.requireNonNull(payload, "OCR request payload is required");
        }
    }

    record Admission(Disposition disposition, Ticket ticket, JsonObject response) {
        Admission {
            disposition = Objects.requireNonNull(disposition, "OCR admission disposition is required");
            response = response == null ? null : response.deepCopy();
        }

        @Override
        public JsonObject response() {
            return response == null ? null : response.deepCopy();
        }
    }

    private final int maxCompletedResponses;
    private final int maxSubscribers;
    private final LinkedHashMap<String, CompletedResponse> completed = new LinkedHashMap<>();
    private ActiveRequest active;

    PageMappingsOcrRequestLedger(int maxCompletedResponses, int maxSubscribers) {
        if (maxCompletedResponses <= 0) {
            throw new IllegalArgumentException("maxCompletedResponses must be positive");
        }
        if (maxSubscribers <= 0) {
            throw new IllegalArgumentException("maxSubscribers must be positive");
        }
        this.maxCompletedResponses = maxCompletedResponses;
        this.maxSubscribers = maxSubscribers;
    }

    synchronized Admission admit(
            String responseOperation, JsonObject request, Subscriber subscriber) {
        String operation = requireNonBlank(
                responseOperation, "Page Mappings OCR response operation is required");
        Objects.requireNonNull(subscriber, "Page Mappings OCR subscriber is required");
        String key = subscriber.authority().ledgerKey(operation);
        String payload = request == null ? "" : request.toString();
        Ticket ticket = new Ticket(
                key, payload, APPLY_RESPONSE_OPERATION.equals(operation));

        CompletedResponse prior = completed.get(key);
        if (prior != null) {
            return prior.payload().equals(payload)
                    ? new Admission(Disposition.REPLAY, null, prior.response())
                    : new Admission(Disposition.CONFLICT, null, null);
        }

        if (active != null && active.ticket().key().equals(key)) {
            if (!active.ticket().payload().equals(payload)) {
                return new Admission(Disposition.CONFLICT, null, null);
            }
            active.addSubscriber(subscriber, maxSubscribers);
            return new Admission(Disposition.ATTACHED, null, null);
        }

        if (active != null) {
            return new Admission(Disposition.BUSY, null, null);
        }

        active = new ActiveRequest(ticket, subscriber);
        return new Admission(Disposition.START, ticket, null);
    }

    synchronized List<Subscriber> complete(Ticket ticket, JsonObject response) {
        if (!matchesActive(ticket)) return List.of();

        // Detach first: allocation or defensive-copy failure must never leave the global OCR lane
        // occupied after the worker has reached a terminal path.
        ActiveRequest completedRequest = active;
        active = null;
        List<Subscriber> recipients = List.copyOf(completedRequest.subscribers());
        if (ticket.replaySuccessfulResponse() && isSuccessful(response)) {
            completed.put(
                    ticket.key(),
                    new CompletedResponse(ticket.payload(), response.deepCopy()));
            trimCompleted();
        }
        return recipients;
    }

    synchronized List<Subscriber> release(Ticket ticket) {
        if (!matchesActive(ticket)) return List.of();
        ActiveRequest releasedRequest = active;
        active = null;
        return List.copyOf(releasedRequest.subscribers());
    }

    private boolean matchesActive(Ticket ticket) {
        return ticket != null
                && active != null
                && active.ticket().key().equals(ticket.key())
                && active.ticket().payload().equals(ticket.payload());
    }

    private void trimCompleted() {
        while (completed.size() > maxCompletedResponses) {
            Iterator<String> oldest = completed.keySet().iterator();
            if (!oldest.hasNext()) return;
            oldest.next();
            oldest.remove();
        }
    }

    private static boolean isSuccessful(JsonObject response) {
        return response != null
                && response.has("ok")
                && response.get("ok").isJsonPrimitive()
                && response.get("ok").getAsBoolean();
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private record CompletedResponse(String payload, JsonObject response) {}

    private static final class ActiveRequest {
        private final Ticket ticket;
        private final List<Subscriber> subscribers = new ArrayList<>();

        private ActiveRequest(Ticket ticket, Subscriber subscriber) {
            this.ticket = Objects.requireNonNull(ticket);
            subscribers.add(Objects.requireNonNull(subscriber));
        }

        private Ticket ticket() {
            return ticket;
        }

        private List<Subscriber> subscribers() {
            return subscribers;
        }

        private void addSubscriber(Subscriber subscriber, int maxSubscribers) {
            subscribers.removeIf(existing -> existing.transport() == subscriber.transport()
                    && Objects.equals(existing.sessionId(), subscriber.sessionId()));
            if (subscribers.size() >= maxSubscribers) {
                subscribers.remove(0);
            }
            subscribers.add(subscriber);
        }
    }
}
