package com.allinweb.ch.facade;

public final class ScannerSupportSavedFileMessageService {

    public Message pageReview(ScannerSupportFileSaveService.SavedSupportFile savedFile) {
        return new Message(
                "Support file saved",
                savedFile.portalMessage("Drag & drop this file on the Support Portal to create a ticket."));
    }

    public Message elementsReview(ScannerSupportFileSaveService.SavedSupportFile savedFile) {
        return new Message(
                "Elements review saved",
                savedFile.portalMessage("Drag & drop this file on the Support Portal to submit."));
    }

    public record Message(String header, String content) {}
}
