package com.zzy.blog.support

import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 基于磁盘文件的 MultipartFile 实现，提供流式读取能力
 */
class FileBackedMultipartFile(
    private val file: File,
    private val partName: String = "file",
    private val originalFilename: String = file.name,
    private val contentType: String? = Files.probeContentType(file.toPath())
) : MultipartFile {

    override fun getName(): String = partName

    override fun getOriginalFilename(): String = originalFilename

    override fun getContentType(): String? = contentType ?: "application/octet-stream"

    override fun isEmpty(): Boolean = file.length() == 0L

    override fun getSize(): Long = file.length()

    override fun getBytes(): ByteArray = file.readBytes()

    override fun getInputStream(): InputStream = file.inputStream()

    override fun transferTo(dest: File) {
        file.copyTo(dest, overwrite = true)
    }

    override fun transferTo(dest: java.nio.file.Path) {
        Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING)
    }
}
