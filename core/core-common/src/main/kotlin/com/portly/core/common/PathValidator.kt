package com.portly.core.common

import com.portly.core.model.ProtocolError
import java.io.File

object PathValidator {

    /**
     * Resolves [requestedPath] strictly inside [rootDir]. Rejects null bytes, backslashes,
     * parent-directory escapes, and symlink escapes outside [rootDir].
     */
    fun canonicalizeUnderRoot(rootDir: File, requestedPath: String): Result<File> {
        if (requestedPath.contains('\u0000')) {
            return Result.failure(
                ProtocolException(
                    ProtocolError.PathTraversalRejected(
                        technicalDetail = "Null byte rejected in path",
                    ),
                ),
            )
        }

        val rootCanonical = runCatching { rootDir.canonicalFile }.getOrElse {
            return Result.failure(
                ProtocolException(
                    ProtocolError.PathNotFound(technicalDetail = it.message),
                    it,
                ),
            )
        }

        val cleanedRelative = requestedPath
            .replace('\\', '/')
            .trim()
            .trimStart('/')

        if (cleanedRelative.isEmpty() || cleanedRelative == ".") {
            return Result.success(rootCanonical)
        }

        val segments = cleanedRelative.split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            if (segment == ".." || segment == ".") {
                // Reject explicit traversal tokens in server-requested paths immediately.
                if (segment == "..") {
                    return Result.failure(
                        ProtocolException(
                            ProtocolError.PathTraversalRejected(
                                technicalDetail = "Parent traversal segment rejected: $requestedPath",
                            ),
                        ),
                    )
                }
            }
        }

        val candidate = File(rootCanonical, segments.filter { it != "." }.joinToString(File.separator))
        val candidateCanonical = runCatching { candidate.canonicalFile }.getOrElse {
            return Result.failure(
                ProtocolException(
                    ProtocolError.PathTraversalRejected(technicalDetail = it.message),
                    it,
                ),
            )
        }

        val rootPathWithSep = if (rootCanonical.path.endsWith(File.separator)) {
            rootCanonical.path
        } else {
            rootCanonical.path + File.separator
        }

        val isInside = candidateCanonical.path == rootCanonical.path ||
            candidateCanonical.path.startsWith(rootPathWithSep)

        if (!isInside) {
            return Result.failure(
                ProtocolException(
                    ProtocolError.PathTraversalRejected(
                        technicalDetail = "Canonical path escaped configured root",
                    ),
                ),
            )
        }

        return Result.success(candidateCanonical)
    }

    fun normalizeRemotePath(path: String): String {
        val unified = path.replace('\\', '/').trim()
        if (unified.isEmpty() || unified == "/") return "/"
        val stack = ArrayDeque<String>()
        for (part in unified.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return if (stack.isEmpty()) "/" else "/" + stack.joinToString("/")
    }

    fun joinRemotePath(parent: String, childName: String): String {
        val base = normalizeRemotePath(parent)
        val cleanChild = sanitizeFileName(childName)
        return if (base == "/") "/$cleanChild" else "$base/$cleanChild"
    }

    fun parentRemotePath(path: String): String {
        val norm = normalizeRemotePath(path)
        if (norm == "/") return "/"
        val idx = norm.lastIndexOf('/')
        return if (idx <= 0) "/" else norm.substring(0, idx)
    }

    fun sanitizeFileName(name: String): String {
        return name
            .replace('\u0000', '_')
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .ifEmpty { "unnamed" }
    }

    fun resolveUniqueName(existingNames: Set<String>, desiredName: String): String {
        if (desiredName !in existingNames) return desiredName
        val dotIndex = desiredName.lastIndexOf('.')
        val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
        var counter = 1
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (candidate !in existingNames) return candidate
            counter++
        }
    }
}
