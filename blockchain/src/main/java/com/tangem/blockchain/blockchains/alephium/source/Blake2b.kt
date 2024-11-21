package com.tangem.blockchain.blockchains.alephium.source

import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.Blake2bDigest

data class Blake2b(private val _bytes: String) {

    fun length(): Int {
        return _bytes.length
    }

    fun bytes(): ByteArray {
        return _bytes.hexToBytes()
    }

    companion object {

        fun zero(): Blake2b = Blake2b("")

        fun generate(): Blake2b = TODO()

        fun length(): Int = 32

        fun provider(): Digest = Blake2bDigest(length() * 8)
    }
}

object Blake2bUtils {

    fun length(): Int = 32

    fun provider(): Digest = Blake2bDigest(length() * 8)

    fun hash(input: ByteArray): Blake2b {
        val hashser = provider() // For Thread-safety
        hashser.update(input, 0, input.size)
        val res = ByteArray(length())
        hashser.doFinal(res, 0)
        return Blake2b(res.toHexString())
    }
}
