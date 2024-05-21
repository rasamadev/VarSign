package com.rasamadev.varsign

import com.itextpdf.text.DocumentException
import com.itextpdf.text.pdf.PdfDate
import com.itextpdf.text.pdf.PdfDeveloperExtension
import com.itextpdf.text.pdf.PdfDictionary
import com.itextpdf.text.pdf.PdfName
import com.itextpdf.text.pdf.PdfSignature
import com.itextpdf.text.pdf.PdfSignatureAppearance
import com.itextpdf.text.pdf.PdfString
import com.itextpdf.text.pdf.security.CrlClient
import com.itextpdf.text.pdf.security.DigestAlgorithms
import com.itextpdf.text.pdf.security.ExternalDigest
import com.itextpdf.text.pdf.security.ExternalSignature
import com.itextpdf.text.pdf.security.MakeSignature
import com.itextpdf.text.pdf.security.OcspClient
import com.itextpdf.text.pdf.security.PdfPKCS7
import com.itextpdf.text.pdf.security.TSAClient
import org.spongycastle.asn1.esf.SignaturePolicyIdentifier
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.PrivateKey
import java.security.cert.X509Certificate

class CustomMakeSignature: MakeSignature() {

//    @Throws(IOException::class, DocumentException::class, GeneralSecurityException::class)
//    fun signDetached(
//        sap: PdfSignatureAppearance?,
//        externalDigest: ExternalDigest?,
//        externalSignature: ExternalSignature?,
//        chain: Array<Certificate?>?,
//        crlList: Collection<CrlClient?>?,
//        ocspClient: OcspClient?,
//        tsaClient: TSAClient?,
//        estimatedSize: Int,
//        sigtype: CryptoStandard?
//    ) {
//        CustomMakeSignature.signDetached(
//            sap,
//            externalDigest,
//            externalSignature,
//            chain,
//            crlList,
//            ocspClient,
//            tsaClient,
//            estimatedSize,
//            sigtype,
//            null as SignaturePolicyIdentifier?
//        )
//    }

    companion object{
        @Throws(IOException::class, DocumentException::class, GeneralSecurityException::class)
        fun signDetached(
            sap: PdfSignatureAppearance,
            externalDigest: ExternalDigest,
            externalSignature: ExternalSignature,
            chain: Array<X509Certificate>,
            crlList: Collection<CrlClient?>?,
            ocspClient: OcspClient?,
            tsaClient: TSAClient?,
            estimatedSize: Int,
            sigtype: CryptoStandard,
            signaturePolicy: SignaturePolicyIdentifier?
        ) {
            var estimatedSize = estimatedSize
            var crlBytes: Collection<*>? = null
            var i = 0
            while (crlBytes == null && i < chain.size) {
                crlBytes = processCrl(chain[i++], crlList)
            }
            if (estimatedSize == 0) {
                estimatedSize = 8192
                var exc: ByteArray
                if (crlBytes != null) {
                    val dic = crlBytes.iterator()
                    while (dic.hasNext()) {
                        exc = dic.next() as ByteArray
                        estimatedSize += exc.size + 10
                    }
                }
                if (ocspClient != null) {
                    estimatedSize += 4192
                }
                if (tsaClient != null) {
                    estimatedSize += 4192
                }
            }
            sap.certificate = chain[0]
            if (sigtype == CryptoStandard.CADES) {
                sap.addDeveloperExtension(PdfDeveloperExtension.ESIC_1_7_EXTENSIONLEVEL2)
            }
            val var24 = PdfSignature(
                PdfName.ADOBE_PPKLITE,
                if (sigtype == CryptoStandard.CADES) PdfName.ETSI_CADES_DETACHED else PdfName.ADBE_PKCS7_DETACHED
            )
            var24.setReason(sap.reason)
            var24.setLocation(sap.location)
            var24.setSignatureCreator(sap.signatureCreator)
            var24.setContact(sap.contact)
            var24.setDate(PdfDate(sap.signDate))
            sap.cryptoDictionary = var24
            val var25: HashMap<PdfName, Int> = HashMap<PdfName, Int>()
            var25[PdfName.CONTENTS] = estimatedSize * 2 + 2
            sap.preClose(var25)
            val hashAlgorithm = externalSignature.hashAlgorithm
            val sgn = PdfPKCS7(
                null as PrivateKey?,
                chain,
                hashAlgorithm,
                null as String?,
                externalDigest,
                false
            )
            if (signaturePolicy != null) {
                sgn.setSignaturePolicy(signaturePolicy)
            }
            val data = sap.rangeStream
            val hash = DigestAlgorithms.digest(data, externalDigest.getMessageDigest(hashAlgorithm))
            var ocsp: ByteArray? = null
            if (chain.size >= 2 && ocspClient != null) {
                ocsp = ocspClient.getEncoded(
                    chain[0] as X509Certificate?,
                    chain[1] as X509Certificate?,
                    null as String?
                )
            }
            val sh = sgn.getAuthenticatedAttributeBytes(hash, ocsp,
                crlBytes as MutableCollection<ByteArray>?, sigtype)
            val extSignature = externalSignature.sign(sh)
            sgn.setExternalDigest(
                extSignature,
                null as ByteArray?,
                externalSignature.encryptionAlgorithm
            )
            val encodedSig = sgn.getEncodedPKCS7(hash, tsaClient, ocsp, crlBytes, sigtype)
            if (estimatedSize < encodedSig.size) {
                throw IOException("Not enough space")
            } else {
                val paddedSig = ByteArray(estimatedSize)
                System.arraycopy(encodedSig, 0, paddedSig, 0, encodedSig.size)
                val dic2 = PdfDictionary()
                dic2.put(PdfName.CONTENTS, PdfString(paddedSig).setHexWriting(true))
                sap.close(dic2)
            }
        }
    }
}