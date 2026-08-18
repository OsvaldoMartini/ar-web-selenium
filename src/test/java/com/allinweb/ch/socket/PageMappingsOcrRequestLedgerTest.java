package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import javax.websocket.Session;
import org.junit.jupiter.api.Test;

class PageMappingsOcrRequestLedgerTest {

    private static final String APPLY_RESPONSE =
            PageMappingsOcrRequestLedger.APPLY_RESPONSE_OPERATION;
    private static final String REVIEW_RESPONSE = "pageMappings.ocrReviewResponse";

    @Test
    void identicalApplyRetryAttachesReplacementTransportAndReplaysSuccessfulResponse() {
        PageMappingsOcrRequestLedger ledger = new PageMappingsOcrRequestLedger(4, 4);
        JsonObject request = request("apply-1", "approved_name");
        Session original = mock(Session.class);
        Session replacement = mock(Session.class);

        PageMappingsOcrRequestLedger.Admission accepted = ledger.admit(
                APPLY_RESPONSE, request, subscriber(original, request));
        PageMappingsOcrRequestLedger.Admission attached = ledger.admit(
                APPLY_RESPONSE, request.deepCopy(), subscriber(replacement, request));

        assertEquals(PageMappingsOcrRequestLedger.Disposition.START, accepted.disposition());
        assertNotNull(accepted.ticket());
        assertEquals(PageMappingsOcrRequestLedger.Disposition.ATTACHED, attached.disposition());

        JsonObject success = response(true, "saved");
        List<PageMappingsOcrRequestLedger.Subscriber> recipients =
                ledger.complete(accepted.ticket(), success);
        assertEquals(
                List.of(original, replacement),
                recipients.stream()
                        .map(PageMappingsOcrRequestLedger.Subscriber::transport)
                        .toList());

        Session replayTransport = mock(Session.class);
        PageMappingsOcrRequestLedger.Admission replay = ledger.admit(
                APPLY_RESPONSE, request.deepCopy(), subscriber(replayTransport, request));
        assertEquals(PageMappingsOcrRequestLedger.Disposition.REPLAY, replay.disposition());
        assertEquals("saved", replay.response().get("message").getAsString());
    }

    @Test
    void conflictingPayloadIsDroppedWhileInFlightAndAfterSuccessfulApply() {
        PageMappingsOcrRequestLedger ledger = new PageMappingsOcrRequestLedger(4, 4);
        JsonObject acceptedRequest = request("apply-1", "approved_name");
        JsonObject conflict = request("apply-1", "different_name");
        Session acceptedTransport = mock(Session.class);

        PageMappingsOcrRequestLedger.Admission accepted = ledger.admit(
                APPLY_RESPONSE,
                acceptedRequest,
                subscriber(acceptedTransport, acceptedRequest));
        PageMappingsOcrRequestLedger.Admission inFlightConflict = ledger.admit(
                APPLY_RESPONSE,
                conflict,
                subscriber(mock(Session.class), conflict));

        assertEquals(
                PageMappingsOcrRequestLedger.Disposition.CONFLICT,
                inFlightConflict.disposition());
        assertNull(inFlightConflict.response());
        assertEquals(
                List.of(acceptedTransport),
                ledger.complete(accepted.ticket(), response(true, "saved")).stream()
                        .map(PageMappingsOcrRequestLedger.Subscriber::transport)
                        .toList());

        PageMappingsOcrRequestLedger.Admission completedConflict = ledger.admit(
                APPLY_RESPONSE,
                conflict,
                subscriber(mock(Session.class), conflict));
        assertEquals(
                PageMappingsOcrRequestLedger.Disposition.CONFLICT,
                completedConflict.disposition());
        assertNull(completedConflict.response());
    }

    @Test
    void reviewResponsesAndFailedAppliesAreNotCached() {
        PageMappingsOcrRequestLedger ledger = new PageMappingsOcrRequestLedger(4, 4);
        JsonObject reviewRequest = request("review-1", "candidate");
        PageMappingsOcrRequestLedger.Admission review = ledger.admit(
                REVIEW_RESPONSE,
                reviewRequest,
                subscriber(mock(Session.class), reviewRequest));
        ledger.complete(review.ticket(), response(true, "reviewed"));

        PageMappingsOcrRequestLedger.Admission repeatedReview = ledger.admit(
                REVIEW_RESPONSE,
                reviewRequest.deepCopy(),
                subscriber(mock(Session.class), reviewRequest));
        assertEquals(
                PageMappingsOcrRequestLedger.Disposition.START,
                repeatedReview.disposition());
        ledger.release(repeatedReview.ticket());

        JsonObject applyRequest = request("apply-failed", "candidate");
        PageMappingsOcrRequestLedger.Admission failedApply = ledger.admit(
                APPLY_RESPONSE,
                applyRequest,
                subscriber(mock(Session.class), applyRequest));
        ledger.complete(failedApply.ticket(), response(false, "not saved"));

        PageMappingsOcrRequestLedger.Admission repeatedApply = ledger.admit(
                APPLY_RESPONSE,
                applyRequest.deepCopy(),
                subscriber(mock(Session.class), applyRequest));
        assertEquals(
                PageMappingsOcrRequestLedger.Disposition.START,
                repeatedApply.disposition());
    }

    @Test
    void duplicateSubscriberIsReplacedAndTheNewestTransportsStayWithinTheBound() {
        PageMappingsOcrRequestLedger ledger = new PageMappingsOcrRequestLedger(4, 2);
        JsonObject request = request("apply-1", "approved_name");
        Session first = mock(Session.class);
        Session second = mock(Session.class);
        Session third = mock(Session.class);

        PageMappingsOcrRequestLedger.Admission accepted = ledger.admit(
                APPLY_RESPONSE, request, subscriber(first, request));
        ledger.admit(APPLY_RESPONSE, request.deepCopy(), subscriber(first, request));
        ledger.admit(APPLY_RESPONSE, request.deepCopy(), subscriber(second, request));
        ledger.admit(APPLY_RESPONSE, request.deepCopy(), subscriber(third, request));

        List<Session> recipients = ledger.complete(accepted.ticket(), response(true, "saved"))
                .stream()
                .map(PageMappingsOcrRequestLedger.Subscriber::transport)
                .toList();
        assertEquals(List.of(second, third), recipients);
    }

    @Test
    void replayResponseIsDefensivelyCopied() {
        PageMappingsOcrRequestLedger ledger = new PageMappingsOcrRequestLedger(4, 2);
        JsonObject request = request("apply-1", "approved_name");
        PageMappingsOcrRequestLedger.Admission accepted = ledger.admit(
                APPLY_RESPONSE,
                request,
                subscriber(mock(Session.class), request));
        ledger.complete(accepted.ticket(), response(true, "saved"));

        PageMappingsOcrRequestLedger.Admission firstReplay = ledger.admit(
                APPLY_RESPONSE,
                request.deepCopy(),
                subscriber(mock(Session.class), request));
        JsonObject exposed = firstReplay.response();
        assertNotNull(exposed);
        exposed.addProperty("message", "changed by caller");

        PageMappingsOcrRequestLedger.Admission secondReplay = ledger.admit(
                APPLY_RESPONSE,
                request.deepCopy(),
                subscriber(mock(Session.class), request));
        assertTrue(secondReplay.response().get("ok").getAsBoolean());
        assertEquals("saved", secondReplay.response().get("message").getAsString());
    }

    @Test
    void fatalWorkerReleaseClearsApplyWithoutCachingIt() {
        PageMappingsOcrRequestLedger ledger = new PageMappingsOcrRequestLedger(4, 2);
        JsonObject request = request("apply-abrupt", "approved_name");
        Session original = mock(Session.class);
        PageMappingsOcrRequestLedger.Admission accepted = ledger.admit(
                APPLY_RESPONSE, request, subscriber(original, request));

        assertEquals(
                List.of(original),
                ledger.release(accepted.ticket()).stream()
                        .map(PageMappingsOcrRequestLedger.Subscriber::transport)
                        .toList());

        PageMappingsOcrRequestLedger.Admission retry = ledger.admit(
                APPLY_RESPONSE,
                request.deepCopy(),
                subscriber(mock(Session.class), request));
        assertEquals(PageMappingsOcrRequestLedger.Disposition.START, retry.disposition());
    }

    private static PageMappingsOcrRequestLedger.Subscriber subscriber(
            Session transport, JsonObject request) {
        return new PageMappingsOcrRequestLedger.Subscriber(
                7,
                "pageMappingsManager",
                transport,
                new PageMappingsWorkspaceService.OcrAuthority(
                        request.get("bindingEpoch").getAsString(),
                        request.get("workspaceEpoch").getAsLong(),
                        request.get("homeBankingId").getAsInt(),
                        request.get("botJobId").getAsInt(),
                        request.get("requestId").getAsString()),
                request);
    }

    private static JsonObject request(String requestId, String clientNamed) {
        JsonObject request = new JsonObject();
        request.addProperty("contractVersion", 1);
        request.addProperty("requestId", requestId);
        request.addProperty("bindingEpoch", "binding-1");
        request.addProperty("workspaceEpoch", 21);
        request.addProperty("homeBankingId", 7);
        request.addProperty("botJobId", 42);
        request.addProperty("scanId", "scan-1");
        JsonObject change = new JsonObject();
        change.addProperty("scannedElementId", 11);
        change.addProperty("clientNamed", clientNamed);
        JsonArray changes = new JsonArray();
        changes.add(change);
        request.add("changes", changes);
        return request;
    }

    private static JsonObject response(boolean ok, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", ok);
        response.addProperty("message", message);
        return response;
    }
}
