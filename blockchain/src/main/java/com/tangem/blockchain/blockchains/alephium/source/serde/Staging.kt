package com.tangem.blockchain.blockchains.alephium.source.serde

data class Staging<out T>(val value: T, val rest: ByteArray) {
    fun <B> mapValue(transform: (T) -> B): Staging<B> {
        return Staging(transform(value), rest)
    }
}