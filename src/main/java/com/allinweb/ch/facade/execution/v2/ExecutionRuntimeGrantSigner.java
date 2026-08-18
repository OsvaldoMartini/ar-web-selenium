package com.allinweb.ch.facade.execution.v2;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Deterministic compact HS256 signer matching the Node Execution V2 verifier. */
final class ExecutionRuntimeGrantSigner {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final String keyId;

    ExecutionRuntimeGrantSigner(ExecutionRuntimeGrantConfiguration configuration) {
        this.secret = configuration.secretCopy();
        this.keyId = configuration.keyId();
    }

    String sign(JsonObject claims) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", ExecutionV2Contracts.GRANT_ALGORITHM);
        header.addProperty("typ", ExecutionV2Contracts.GRANT_TYPE);
        header.addProperty("kid", keyId);
        String encodedHeader = encode(header.toString());
        String encodedClaims = encode(claims.toString());
        String signingInput = encodedHeader + "." + encodedClaims;
        return signingInput + "." + BASE64_URL.encodeToString(hmac(signingInput));
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Execution V2 HmacSHA256 is unavailable", unavailable);
        }
    }

    private static String encode(String value) {
        return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
