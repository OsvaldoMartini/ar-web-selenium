package com.allinweb.ch.component.pane;

/** Immutable, browser-independent mapping from TEST RUN UI selection to executor arguments. */
record TestRunExecutionSelection(int blockOrderNumber, boolean runSingleBlock) {

    static TestRunExecutionSelection resolve(
            Integer selectedBlockOrder, boolean executeAllSelected, boolean oneModeSelected) {
        if (executeAllSelected) {
            if (oneModeSelected) {
                throw new IllegalArgumentException("Execute All cannot be combined with ONE mode");
            }
            return new TestRunExecutionSelection(-1, false);
        }
        if (selectedBlockOrder == null || selectedBlockOrder <= 0) {
            throw new IllegalArgumentException("A numbered block with a positive execution order is required");
        }
        return new TestRunExecutionSelection(selectedBlockOrder, oneModeSelected);
    }
}
