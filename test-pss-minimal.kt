import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.BinarySize.Companion.bits

suspend fun main() {
    val provider = CryptographyProvider.Default
    val rsaPss = provider.get(RSA.PSS)

    // Generate key pair
    println("Generating RSA-PSS key pair...")
    val keyPair = rsaPss.keyPairGenerator(keySize = 2048.bits, digest = SHA256).generateKey()

    // Sign data
    val data = "Hello, World!".encodeToByteArray()
    println("Signing data with default salt size...")
    val signature = keyPair.privateKey.signatureGenerator().generateSignature(data)
    println("Signature generated: ${signature.size} bytes")

    // Verify signature
    println("Verifying signature with default salt size...")
    val isValid = keyPair.publicKey.signatureVerifier().tryVerifySignature(data, signature)
    println("Signature valid: $isValid")

    if (!isValid) {
        println("ERROR: Signature verification failed!")
        println("This indicates a problem with the dev.whyoleg.cryptography library")
    } else {
        println("SUCCESS: PS256 works correctly with library defaults")
    }
}
