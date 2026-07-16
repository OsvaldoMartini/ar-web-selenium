package com.allinweb.ch.facade;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Builds and forwards native-mobile scanner test payloads outside the JavaFX scene. */
public final class ScannerMobileTestForwarder {
    private final ScannerMobileTestRoute route;
    private final DataPort data;
    private final SenderPort sender;
    private final Gson gson;

    public ScannerMobileTestForwarder(ScannerMobileTestRoute route) {
        this(route, new DefaultDataPort(), new DefaultSenderPort(), new Gson());
    }

    ScannerMobileTestForwarder(ScannerMobileTestRoute route, DataPort data, SenderPort sender, Gson gson) {
        this.route = route;
        this.data = data;
        this.sender = sender;
        this.gson = gson;
    }

    public void forward(SplitDTO splitDTO, String operationId) {
        InstructionLoad matchingInstruction = matchingInstruction(splitDTO);
        if (matchingInstruction != null) {
            SplitDTO.applyAttrDataFromReferences(splitDTO, matchingInstruction);
            SplitDTO.applyInstructionToSplit(splitDTO, matchingInstruction);
        }

        splitDTO.setOperationId(operationId);
        if (!ScannerWorkspaceOperations.NEW_ELEMENT_DTO.equals(operationId)
                && !ScannerWorkspaceOperations.SEND_ALL_ELEMENTS_DTO.equals(operationId)) {
            sender.sendMessageJson(
                    splitDTO.getHomeBankingId(),
                    route.returnSessionId(),
                    gson.toJson(splitDTO),
                    operationId);
        }
    }

    private InstructionLoad matchingInstruction(SplitDTO splitDTO) {
        Integer elementId = Optional.ofNullable(splitDTO.getElementDetails())
                .filter(elements -> elements.length > 0)
                .map(elements -> elements[0])
                .map(ElementDTO::getId)
                .orElse(null);

        return Optional.ofNullable(data.instructions())
                .orElse(Collections.emptyList())
                .stream()
                .filter(instruction -> Objects.equals(instruction.getId(), elementId))
                .findFirst()
                .orElse(null);
    }

    interface DataPort {
        List<InstructionLoad> instructions();
    }

    interface SenderPort {
        void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId);
    }

    private static final class DefaultDataPort implements DataPort {
        private final PerformLists lists = PerformLists.getInstance();

        @Override
        public List<InstructionLoad> instructions() {
            return lists.getListInstruction();
        }
    }

    private static final class DefaultSenderPort implements SenderPort {
        private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            sessions.sendMessageJson(homeBankingId, sessionId, json, operationId);
        }
    }
}
