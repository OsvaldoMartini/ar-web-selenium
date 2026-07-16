package com.allinweb.ch.facade;

import com.google.gson.Gson;

public final class ScannerGridBootstrapService {

    public String bootstrapScript(Request request, Gson gson) {
        if (request == null || gson == null) {
            throw new IllegalArgumentException("Scanner grid bootstrap request and JSON encoder are required");
        }

        return "setTimeout(function() { window.receiveDataFromJava(JSON.stringify(" + request.jsonData() + "), "
                + request.port() + ", " + gson.toJson(request.sessionId()) + ", " + request.homeBankingId() + ", "
                + request.botJobId() + ", " + gson.toJson(request.botJobName()) + " ) }, 1000)";
    }

    public record Request(
            String jsonData, int port, String sessionId, int homeBankingId, int botJobId, String botJobName) {}
}
