package com.rasamadev.varsign

import com.itextpdf.text.pdf.security.DigestAlgorithms
import com.itextpdf.text.pdf.security.ExternalSignature
import java.security.PrivateKey
import java.security.Signature

class CustomPrivateKeySignature(
    private val pk: PrivateKey,
    hashAlgorithm: String,
    private val provider: String
): ExternalSignature {
    private val hashAlgorithm: String
    private val encryptionAlgorithm: String

    init {
        this.hashAlgorithm = DigestAlgorithms.getDigest(DigestAlgorithms.getAllowedDigests(hashAlgorithm))
        var encryptionAlgorithmTemp = pk.algorithm
        if (encryptionAlgorithmTemp.startsWith("EC")) {
            encryptionAlgorithmTemp = "ECDSA"
        }
        this.encryptionAlgorithm = encryptionAlgorithmTemp
    }


    override fun getHashAlgorithm(): String {
        return this.hashAlgorithm
    }

    override fun getEncryptionAlgorithm(): String {
        return this.encryptionAlgorithm
    }

    override fun sign(message: ByteArray?): ByteArray {
        val signMode = hashAlgorithm + "with" + encryptionAlgorithm
        val sig: Signature
        sig = Signature.getInstance("SHA256withRSA")

        sig.initSign(pk)
        sig.update(message)
        return sig.sign()
    }
}