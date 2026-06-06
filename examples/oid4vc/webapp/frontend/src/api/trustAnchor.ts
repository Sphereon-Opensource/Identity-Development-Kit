// IDK E2E CA (CN=IDK E2E CA, O=Sphereon, C=NL) — the example trust anchor a wallet must pin to accept x5c-signed credentials/requests in HAIP mode.
export const IDK_E2E_CA_PEM = `-----BEGIN CERTIFICATE-----
MIIBtTCCAVygAwIBAgIUYHPHPaIhLbZjEG+87CQGaZRT1r0wCgYIKoZIzj0EAwIw
JzElMCMGA1UEAwwcSURLIEUyRSBDQSwgTz1TcGhlcmVvbiwgQz1OTDAeFw0yNjA0
MjkxNjI0NDRaFw0zNjA0MjYxNjI0NDRaMCcxJTAjBgNVBAMMHElESyBFMkUgQ0Es
IE89U3BoZXJlb24sIEM9TkwwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ/QuTk
dimVLElzTHWWRizMyXFP5cxzM+yh4cev69MUXxgdbKxKOrj+MiyibEY2KofqAN3K
hD9MtijUxE80AZeho2YwZDAdBgNVHQ4EFgQU4yGaoD/fqNjRbtD+oLYLU25xeq4w
HwYDVR0jBBgwFoAU4yGaoD/fqNjRbtD+oLYLU25xeq4wEgYDVR0TAQH/BAgwBgEB
/wIBADAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwIDRwAwRAIgRnEcATheGu7k
S9202u8Pw72876+HollpN2soD/kvd9ACICUz0HMgd7/K1/reEK0D4wxQdLvG2pBM
QbJEgC/RKNkP
-----END CERTIFICATE-----`;

// Stable, human-readable facts about the demo CA above. The SHA-256 fingerprint is
// computed live from the PEM on the page (so it can never drift from the cert shown).
export const IDK_E2E_CA_INFO = {
  subject: 'CN=IDK E2E CA, O=Sphereon, C=NL',
  type: 'Self-signed root CA · EC P-256 (ES256)',
};

/** SHA-256 fingerprint of the DER form of [pem], formatted as upper-case colon-separated hex. */
export async function sha256Fingerprint(pem: string): Promise<string> {
  const b64 = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const der = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  const hash = await crypto.subtle.digest('SHA-256', der);
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0').toUpperCase())
    .join(':');
}
