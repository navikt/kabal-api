package no.nav.klage.oppgave.util

import no.nav.klage.oppgave.exceptions.AttachmentCouldNotBeConvertedException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.tika.Tika
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.MediaType.*
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit
import java.io.File
import kotlin.math.min


@Component
class Image2PDF {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private var supportedMediaTypes: List<MediaType>? = listOf(IMAGE_JPEG, IMAGE_PNG)

    private val A4: PDRectangle = PDRectangle.A4

    fun convertIfImage(file: File): Resource {
        var start = System.currentTimeMillis()
        val bytesForFiletypeDetection =
            file.inputStream()
                .readNBytes(min(DataSize.of(3, DataUnit.KILOBYTES).toBytes().toInt(), file.length().toInt()))
        logger.debug("Reading file for filetype detection took ${System.currentTimeMillis() - start} ms")

        start = System.currentTimeMillis()
        val mediaType = valueOf(Tika().detect(bytesForFiletypeDetection))
        logger.debug("Detecting filetype took ${System.currentTimeMillis() - start} ms")
        if (APPLICATION_PDF == mediaType) {
            logger.debug("File is already a PDF")
            return FileSystemResource(file)
        }
        if (validImageTypes(mediaType)) {
            embedImageInPDF(mediaType.subtype, file)
            return FileSystemResource(file)
        }

        val exception = AttachmentCouldNotBeConvertedException()
        logger.warn("User tried to upload an unsupported file type: $mediaType", exception)
        throw exception
    }

    private fun embedImageInPDF(imgType: String, image: File) {
        embedImageInPDF(image, imgType)
    }

    private fun embedImageInPDF(image: File, imgType: String) {
        try {
            PDDocument().use { doc ->
                addPDFPageFromImage(
                    doc = doc,
                    origImg = image,
                    imgFormat = imgType,
                )
                doc.save(image)
                doc.close()
            }
        } catch (ex: Exception) {
            throw RuntimeException("Conversion of attachment failed", ex)
        }
    }

    private fun validImageTypes(mediaType: MediaType): Boolean {
        val validImageTypes = supportedMediaTypes!!.contains(mediaType)
        logger.debug("{} convert file, of type {}, to PDF", if (validImageTypes) "Will" else "Won't", mediaType)
        return validImageTypes
    }

    private fun addPDFPageFromImage(doc: PDDocument, origImg: File, imgFormat: String) {
        val page = PDPage(A4)
        doc.addPage(page)
        val scaledImg = ImageUtils.downToA4(origImg, imgFormat)
        try {
            PDPageContentStream(doc, page).use { contentStream ->
                val xImage: PDImageXObject = PDImageXObject.createFromFileByContent(scaledImg, doc)
                contentStream.drawImage(xImage, A4.lowerLeftX, A4.lowerLeftY)
                contentStream.close()
            }
        } catch (ex: Exception) {
            throw RuntimeException("Converting attachment failed", ex)
        } finally {
            scaledImg.delete()
        }
    }
}