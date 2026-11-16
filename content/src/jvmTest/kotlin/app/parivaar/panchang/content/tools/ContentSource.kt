package app.parivaar.panchang.content.tools

import java.io.File

/** The repo's content-src directory (system property set by the build; falls back to the working dir walk). */
internal object ContentSource {
    val root: File by lazy {
        System.getProperty("pp.contentSrc")?.let(::File)?.takeIf { it.isDirectory }
            ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .map { File(it, "content-src") }.first { it.isDirectory }
    }

    val packs: Map<String, SourcePack> by lazy { SourcePacks.read(root) }
    val banned: Map<String, List<String>> by lazy { SourcePacks.banned(root) }
}
