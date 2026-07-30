package com.allinweb.ch.facade;

/**
 * Profile marker for the unrestricted React-planned Variables graph authoring surface.
 *
 * <p>This profile deliberately adds no semantic movement policy on top of instruction graph
 * contract v3. The generic transaction still authenticates the Bot Job owner, rejects stale
 * graph facts, requires the complete layout, validates every relationship target, and commits the
 * submitted layout and patches atomically. React remains responsible for the user's exact move or
 * reconnect choice.
 */
public final class VariablesReactAuthoredMutationProfile {

    public static final String PROFILE_ID = "VARIABLES_REACT_AUTHORED_V1";

    private VariablesReactAuthoredMutationProfile() {}
}
