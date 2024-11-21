package com.tangem.blockchain.blockchains.alephium.source

sealed interface LockupScript {
    sealed interface Asset : LockupScript {
        val scriptHint: ScriptHint
    }

    data class P2PKH(
        val pkHash: Hash,
    ) : Asset {
        override val scriptHint: ScriptHint = ScriptHint.fromHash(pkHash)
    }

    companion object {
        fun p2pkh(publicKey: ByteArray): LockupScript.P2PKH {
            return P2PKH(Blake2bUtils.hash(publicKey))
        }
    }
}