package com.allinweb.ch.ai;

import java.util.List;

/**
 * Gson DTOs for the JSON plan the AI must return, plus the schema text embedded into the
 * GEN_FLOW prompt ({@code {{JSON_SCHEMA}}} placeholder). Keep DTOs and schema in one file so
 * they cannot drift apart.
 */
public class GenFlowPlan {

    public List<GenFlowBlock> blocks;

    public static class GenFlowBlock {
        public String name;
        public List<GenFlowStep> steps;
    }

    public static class GenFlowStep {
        public String action; // "CLICK" | "INSERT"
        public String elementName; // exact 'name' from ELEMENTS
        public String xpath; // exact 'xpath' from ELEMENTS
        public String cssSelector; // optional
        public String value; // INSERT only — synthetic data
    }

    public static final String SCHEMA_JSON =
            """
            {
              "blocks": [
                {
                  "name": "string, max 40 chars",
                  "steps": [
                    {
                      "action": "CLICK | INSERT",
                      "elementName": "string - exact 'name' from ELEMENTS",
                      "xpath": "string - exact 'xpath' from ELEMENTS",
                      "cssSelector": "string - exact 'cssSelector' from ELEMENTS (optional)",
                      "value": "string - synthetic input value (INSERT only)"
                    }
                  ]
                }
              ]
            }""";
}
