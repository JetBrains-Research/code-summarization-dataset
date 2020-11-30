package reposanalyzer.zipper

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

interface Zipper {
    private companion object {
        const val DOT_GZ = ".gz"
    }

    fun compressFolder(dir: File, removeAfterZip: Boolean = false) {
        if (!dir.exists() || !dir.isDirectory) {
            return
        }
        dir.walkTopDown().maxDepth(1).forEach { file ->
            compressFile(file, removeAfterZip)
        }
    }

    fun compressFile(file: File, removeAfterZip: Boolean = false) {
        if (!file.exists() || file.isDirectory) {
            return
        }
        val gzipFile = File(file.absolutePath + DOT_GZ)
        gzipFile.createNewFile()
        val fileOutputStream = FileOutputStream(gzipFile)
        val bufferedOutputStream = BufferedOutputStream(fileOutputStream)
        val gzipOutputStream = GzipCompressorOutputStream(bufferedOutputStream)
        gzipOutputStream.bufferedWriter().use { out ->
            FileInputStream(file).bufferedReader().use { input ->
                input.lines().forEach { line ->
                    out.appendLine(line)
                }
            }
        }
        fileOutputStream.close()
        bufferedOutputStream.close()
        gzipOutputStream.close()
        if (removeAfterZip) {
            file.delete()
        }
    }
}
