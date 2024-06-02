package no.nav.klage.oppgave.util

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files

/**
 * This function is used to get a resource from the file system and delete it after the client has downloaded it.
 */
fun getResourceThatWillBeDeleted(resource: Resource): Resource {
    if (resource is FileSystemResource) {
        return object : FileSystemResource(resource.path) {
            override fun getInputStream(): InputStream {
                return object : FileInputStream(resource.file) {
                    override fun close() {
                        super.close()
                        //Override to do this after client has downloaded file
                        Files.delete(file.toPath())
                    }
                }
            }
        }
    } else {
        return resource
    }
}