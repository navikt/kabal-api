package no.nav.klage.oppgave.util

import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.imageio.ImageIO

object ImageUtils {

    private val logger = getLogger(javaClass)

    fun downToA4(origImageFile: File, format: String): File {
        val A4 = PDRectangle.A4
        return try {
            var image = ImageIO.read(origImageFile)
            image = rotatePortrait(image)
            val pdfPageDim = Dimension(A4.width.toInt(), A4.height.toInt())
            val origDim = Dimension(image.width, image.height)
            val newDim = getScaledDimension(origDim, pdfPageDim)
            if (newDim == origDim) {
                origImageFile
            } else {
                val scaledImg = scaleDown(image, newDim)
                toFile(scaledImg, format)
            }
        } catch (ex: IOException) {
            throw RuntimeException("Converting attachment failed.", ex)
        }
    }

    private fun rotatePortrait(image: BufferedImage): BufferedImage {
        if (image.height >= image.width) {
            return image
        }
        if (image.type == BufferedImage.TYPE_CUSTOM) {
            logger.warn("Cannot not rotate image with unknown type.")
            return image
        }
        var rotatedImage = BufferedImage(image.height, image.width, image.type)
        val transform = AffineTransform()
        transform.rotate(
            Math.toRadians(90.0),
            image.height / 2f.toDouble(),
            image.height / 2f.toDouble()
        )
        val op = AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
        rotatedImage = op.filter(image, rotatedImage)
        return rotatedImage
    }

    private fun getScaledDimension(imgSize: Dimension, a4: Dimension): Dimension {
        val originalWidth = imgSize.width
        val originalHeight = imgSize.height
        val a4Width = a4.width
        val a4Height = a4.height
        var newWidth = originalWidth
        var newHeight = originalHeight
        if (originalWidth > a4Width) {
            newWidth = a4Width
            newHeight = newWidth * originalHeight / originalWidth
        }
        if (newHeight > a4Height) {
            newHeight = a4Height
            newWidth = newHeight * originalWidth / originalHeight
        }
        return Dimension(newWidth, newHeight)
    }

    private fun scaleDown(origImage: BufferedImage, newDim: Dimension): BufferedImage {
        val newWidth = newDim.getWidth().toInt()
        val newHeight = newDim.getHeight().toInt()
        val tempImg = origImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
        val scaledImg = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_3BYTE_BGR)
        val g = scaledImg.graphics as Graphics2D
        g.drawImage(tempImg, 0, 0, null)
        g.dispose()
        return scaledImg
    }

    private fun toFile(img: BufferedImage, format: String): File {
        val tempFile = Files.createTempFile(null, null).toFile()
        ImageIO.write(img, format, tempFile)
        return tempFile
    }
}