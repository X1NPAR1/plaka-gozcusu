package com.plakagozcusu.core.storage

import com.google.common.truth.Truth.assertThat
import com.plakagozcusu.core.common.AppResult
import com.plakagozcusu.core.model.EvidenceFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EvidenceFileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val store by lazy { EvidenceFileStore(tmp.root) }

    // 2026-10-28T12:00:00Z
    private val captured = 1_793_188_800_000L

    private fun AppResult<EvidenceFile>.ok() = (this as AppResult.Success).value

    @Test
    fun `write stores bytes under a UTC date folder with a correct sha256`() {
        val bytes = "abc".toByteArray()

        val file = store.write(bytes, captured, "scene.jpg").ok()

        assertThat(file.relativePath).isEqualTo("2026/10/28/scene.jpg")
        // Known SHA-256 test vector for "abc".
        assertThat(file.sha256).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(store.resolve(file.relativePath).readBytes()).isEqualTo(bytes)
        assertThat(file.sizeBytes).isEqualTo(3)
    }

    @Test
    fun `verify detects tampering and missing files`() {
        val file = store.write(byteArrayOf(1, 2, 3), captured, "a.jpg").ok()
        assertThat(store.verify(file)).isTrue()

        store.resolve(file.relativePath).writeBytes(byteArrayOf(9))
        assertThat(store.verify(file)).isFalse()

        store.delete(file)
        assertThat(store.verify(file)).isFalse()
    }

    @Test
    fun `no temp file remains after a successful write`() {
        store.write(byteArrayOf(1), captured, "b.jpg").ok()

        assertThat(tmp.root.walkTopDown().none { it.name.endsWith(EvidenceFileStore.TEMP_SUFFIX) }).isTrue()
    }

    @Test
    fun `leftover temp files from a crash are swept and never listed as evidence`() {
        val dir = File(tmp.root, "2026/10/28").apply { mkdirs() }
        File(dir, "half.jpg.tmp").writeBytes(byteArrayOf(1, 2))
        store.write(byteArrayOf(1), captured, "c.jpg").ok()

        assertThat(store.listRelativePaths()).containsExactly("2026/10/28/c.jpg")
        val now = System.currentTimeMillis()
        // A fresh temp file may be a write in progress: kept.
        assertThat(store.sweepTempFiles(now, olderThanMillis = 60_000)).isEqualTo(0)
        assertThat(store.sweepTempFiles(now + 120_000, olderThanMillis = 60_000)).isEqualTo(1)
    }

    @Test
    fun `an existing evidence file is never overwritten`() {
        val first = store.write(byteArrayOf(1), captured, "same.jpg").ok()

        val second = store.write(byteArrayOf(2), captured, "same.jpg")

        assertThat(second).isInstanceOf(AppResult.Failure::class.java)
        assertThat(store.verify(first)).isTrue()
    }

    @Test
    fun `write failure returns a typed error instead of throwing`() {
        // A regular file where the date directory should be makes mkdirs/rename fail.
        File(tmp.root, "2026").writeText("blocker")

        val result = store.write(byteArrayOf(1), captured, "d.jpg")

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `path traversal is rejected`() {
        store.resolve("../outside.jpg")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `file names cannot contain separators`() {
        store.write(byteArrayOf(1), captured, "x/y.jpg")
    }
}
