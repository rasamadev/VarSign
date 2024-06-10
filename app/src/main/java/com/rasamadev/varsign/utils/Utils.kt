package com.rasamadev.varsign.utils

import android.app.AlertDialog
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Environment
import android.security.KeyChainException
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Rectangle
import com.itextpdf.text.exceptions.BadPasswordException
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.security.BouncyCastleDigest
import com.itextpdf.text.pdf.security.DigestAlgorithms
import com.itextpdf.text.pdf.security.ExternalDigest
import com.itextpdf.text.pdf.security.ExternalSignature
import com.itextpdf.text.pdf.security.MakeSignature
import com.itextpdf.text.pdf.security.PrivateKeySignature
import org.spongycastle.asn1.esf.SignaturePolicyIdentifier
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * CLASE QUE CONTIENE METODOS DE AYUDA QUE SE PUEDEN LLAMAR DIRECTAMENTE
 * DESDE EL NOMBRE DE LA CLASE, SIN INSTANCIAR UN OBJETO.
 * SON SIMILARES A LOS METODOS ESTATICOS DE JAVA
 */
class Utils {
    companion object {

        /**
         * METODO QUE COMPRUEBA SI UN DOCUMENTO PDF ESTA PROTEGIDO
         * POR CONTRASEÑA.
         *
         * @return true si tiene contraseña, false si no la tiene.
         */
        fun isPasswordProtected(ins: InputStream?): Boolean {
            return try {
                val p = PdfReader(ins)
                false
            } catch (e: BadPasswordException) {
                true
            }
        }

        /**
         * METODO QUE COMPRUEBA SI LA CONTRASEÑA INTRODUCIDA PARA
         * UN DOCUMENTO ES VALIDA.
         *
         * @return true si es correcta, false si es incorrecta
         */
        fun isPasswordValid(ins: InputStream?, password: ByteArray): Boolean {
            return try {
                val pdfReader = PdfReader(ins, password);
                true
            } catch (e: BadPasswordException) {
                false
            }
        }

        /**
         * METODO QUE COMPRUEBA SI EL DISPOSITIVO MOVIL
         * CUENTA CON UN LECTOR DE NFC
         */
        fun NFCExists(context: Context): Boolean {
            val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
            return nfcAdapter != null
        }

        /**
         * METODO QUE COMPRUEBA SI EL LECTOR NFC ESTA
         * ACTIVADO EN EL DISPOSITIVO
         */
        fun NFCActivated(context: Context): Boolean {
            val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
            return nfcAdapter?.isEnabled ?: false
        }

        /**
         * METODO QUE APLICA LA FIRMA AL DOCUMENTO (O DOCUMENTOS) SELECCIONADO(S)
         */
        fun signDocuments(
            privateKey: PrivateKey?,
            chain: Array<X509Certificate>?,
            docsSelected: Array<String>,
            docPath: String,
            passwordDoc: ByteArray,
            numPageSign: Int,
            signPosition: String
        ){
            try {
                var pdfReader: PdfReader
                var rec: Rectangle = Rectangle(20f, 800f, 130f, 830f)

                /** CREAMOS UNA 'ExternalSignature' CON LA CLAVE PRIVADA Y LA FUNCION HASH SHA-256 */
                val pks: ExternalSignature = PrivateKeySignature(
                    privateKey,
                    DigestAlgorithms.SHA256,
                    null
                )

                for(docName: String in docsSelected){
                    /** ESPECIFICAMOS DONDE IRA GUARDADO EL DOCUMENTO FIRMADO (Carpeta 'VarSign') */
                    val fileout = File(Environment.getExternalStoragePublicDirectory("VarSign"), "firmado_$docName")
                    val fos = FileOutputStream(fileout)

                    /** APLICAMOS LA FIRMA EN LA POSICION Y PAGINA INDICADAS ANTERIORMENTE */
//                    val uri = Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(docPath), docName))
                    val file = File(Environment.getExternalStoragePublicDirectory(docPath), docName)
                    if(!passwordDoc.isEmpty()){
                        pdfReader = PdfReader(FileInputStream(file), passwordDoc)
                    }
                    else{
                        pdfReader = PdfReader(FileInputStream(file))
                    }
                    val stamper = PdfStamper.createSignature(pdfReader, fos, '\u0000')

                    val rectangle: Rectangle = pdfReader.getPageSizeWithRotation(numPageSign)
                    if (rectangle.height >= rectangle.width){
                        when (signPosition) {
                            "arrIzq" -> rec = Rectangle(20f, 800f, 130f, 830f)
                            "arrCen" -> rec = Rectangle(243f, 800f, 353f, 830f)
                            "arrDer" -> rec = Rectangle(466f, 800f, 576f, 830f)
                            "abaIzq" -> rec = Rectangle(20f, 20f, 130f, 50f)
                            "abaCen" -> rec = Rectangle(243f, 20f, 353f, 50f)
                            "abaDer" -> rec = Rectangle(466f, 20f, 576f, 50f)
                        }
                    }
                    else{
                        when (signPosition) {
                            "arrIzq" -> rec = Rectangle(20f, 570f, 130f, 600f)
                            "arrCen" -> rec = Rectangle(360f, 570f, 470f, 600f)
                            "arrDer" -> rec = Rectangle(690f, 570f, 800f, 600f)
                            "abaIzq" -> rec = Rectangle(20f, 20f, 130f, 50f)
                            "abaCen" -> rec = Rectangle(360f, 20f, 470f, 50f)
                            "abaDer" -> rec = Rectangle(690f, 20f, 800f, 50f)
                        }
                    }

                    val appearance = stamper.signatureAppearance
                    appearance.setVisibleSignature(rec, numPageSign, null)
                    appearance.imageScale = -1f

                    /**
                     * UTILIZAMOS UN DIGEST PROPORCIONADO POR BouncyCastle QUE UTILIZA SUS METODOS
                     * PARA CALCULAR EL HASH DEL DOCUMENTO
                     */
                    val digest: ExternalDigest = BouncyCastleDigest()

                    /** FIRMAMOS EL DOCUMENTO EN FORMATO 'CAdES' */
                    MakeSignature.signDetached(
                        appearance,
                        digest,
                        pks,
                        chain as Array<X509Certificate>,
                        null,
                        null,
                        null,
                        0,
                        MakeSignature.CryptoStandard.CADES,
                        null as SignaturePolicyIdentifier?
                    )
                }
            } catch (e: KeyChainException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: GeneralSecurityException) {
                e.printStackTrace()
            } catch (e: DocumentException) {
                e.printStackTrace()
            }
        }
    }
}