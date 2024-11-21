package com.tangem.blockchain.blockchains.alephium.source

sealed interface UnlockScript {

    @JvmInline
    value class P2PKH(
        val publicKey: ByteArray,
    ) : UnlockScript
}