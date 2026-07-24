package com.allinweb.ch.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A row in the {@code scanned_element} registry — the source of truth for elements seen by the
 * scanner, scoped by organization ({@code homeBankingId}) + {@code botJobId} + {@code pageKey} and
 * identified by a stable {@code elementHash} (so same-name/different-page/different-xPath elements
 * stay distinct).
 *
 * <p>Populated/updated on every scan (see {@code ScannedElementRepository.upsert}); OCR results
 * correct {@code someText}/{@code definedName} and are also stored raw for auditing. Bot-job
 * execution validates its target against these rows.
 */
@Data
@NoArgsConstructor
public class ScannedElement {

    private Long id;
    private Integer homeBankingId;
    private Integer botJobId;
    private Integer homeUrlId;
    private String pageUrl;
    private String pageKey;

    /** Stable identity hash of the page key plus locator fields. */
    private String elementHash;

    private String tagName;
    private String typeElement;
    private String definedName;
    private String clientNamed;
    private String someText;

    private String xPath;
    private String customXPath;
    private String cssSelector;
    private String attribId;
    private String attribName;
    private String coordinates;
    private String iFrameXPath;
    private String shadowHost;
    private String shadowRoot;

    /** JSON of the element's attribute list (AttributeData[]). */
    private String attributeData;

    private String ocrText;
    private String ocrMatchQuality;
    private Double ocrConfidence;

    private int scanCount;
    private String firstScannedAt;
    private String lastScannedAt;
}
