import { Buffer } from 'node:buffer';
import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import {
  ExecutionGrantClaims,
  parseExecutionGrantClaims,
  parseExecutionGrantHeader,
} from '../contracts/executionContracts';

export interface ExecutionGrantVerifierOptions {
  readonly keyId: string;
  readonly secret: Buffer;
  readonly maxLifetimeSeconds: number;
  readonly clockSkewSeconds: number;
  readonly nowEpochSeconds?: () => number;
}

export interface VerifiedExecutionGrant {
  readonly claims: ExecutionGrantClaims;
  readonly fingerprint: string;
}

export class ExecutionGrantError extends Error {
  constructor(readonly code: string) {
    super(code);
    this.name = 'ExecutionGrantError';
  }
}

const decodeJsonSegment = (segment: string): unknown => {
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) throw new ExecutionGrantError('GRANT_ENCODING_INVALID');
  const decoded = Buffer.from(segment, 'base64url');
  if (decoded.toString('base64url') !== segment || decoded.length === 0 || decoded.length > 4096) {
    throw new ExecutionGrantError('GRANT_ENCODING_INVALID');
  }
  try {
    return JSON.parse(decoded.toString('utf8')) as unknown;
  } catch {
    throw new ExecutionGrantError('GRANT_JSON_INVALID');
  }
};

export class ExecutionGrantVerifier {
  private readonly nowEpochSeconds: () => number;

  constructor(private readonly options: ExecutionGrantVerifierOptions) {
    if (options.secret.length < 32) throw new Error('GRANT_SECRET_INVALID');
    this.nowEpochSeconds = options.nowEpochSeconds
      ?? (() => Math.floor(Date.now() / 1000));
  }

  verify(compactGrant: string): VerifiedExecutionGrant {
    if (compactGrant.length === 0 || compactGrant.length > 12_000) {
      throw new ExecutionGrantError('GRANT_SIZE_INVALID');
    }
    const segments = compactGrant.split('.');
    if (segments.length !== 3) throw new ExecutionGrantError('GRANT_FORMAT_INVALID');
    const [headerSegment, claimsSegment, signatureSegment] = segments;
    if (!headerSegment || !claimsSegment || !signatureSegment
        || !/^[A-Za-z0-9_-]+$/.test(signatureSegment)) {
      throw new ExecutionGrantError('GRANT_FORMAT_INVALID');
    }

    const suppliedSignature = Buffer.from(signatureSegment, 'base64url');
    if (suppliedSignature.toString('base64url') !== signatureSegment) {
      throw new ExecutionGrantError('GRANT_SIGNATURE_INVALID');
    }
    const expectedSignature = createHmac('sha256', this.options.secret)
      .update(`${headerSegment}.${claimsSegment}`, 'ascii')
      .digest();
    if (suppliedSignature.length !== expectedSignature.length
        || !timingSafeEqual(suppliedSignature, expectedSignature)) {
      throw new ExecutionGrantError('GRANT_SIGNATURE_INVALID');
    }

    let header;
    let claims;
    try {
      header = parseExecutionGrantHeader(decodeJsonSegment(headerSegment));
      claims = parseExecutionGrantClaims(decodeJsonSegment(claimsSegment));
    } catch (error) {
      if (error instanceof ExecutionGrantError) throw error;
      throw new ExecutionGrantError(error instanceof Error ? error.message : 'GRANT_INVALID');
    }
    if (header.kid !== this.options.keyId) throw new ExecutionGrantError('GRANT_KEY_UNKNOWN');

    const now = this.nowEpochSeconds();
    if (claims.exp <= claims.iat || claims.nbf < claims.iat
        || claims.exp - claims.iat > this.options.maxLifetimeSeconds) {
      throw new ExecutionGrantError('GRANT_TIME_INVALID');
    }
    if (claims.iat > now + this.options.clockSkewSeconds
        || claims.nbf > now + this.options.clockSkewSeconds) {
      throw new ExecutionGrantError('GRANT_NOT_ACTIVE');
    }
    if (claims.exp <= now) {
      throw new ExecutionGrantError('GRANT_EXPIRED');
    }

    return {
      claims,
      fingerprint: createHash('sha256').update(compactGrant, 'ascii').digest('hex'),
    };
  }
}
