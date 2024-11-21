package com.tangem.blockchain.blockchains.alephium.source

data class TransactionId constructor(val value: Hash) {

    fun length(): Int {
        return TransactionId.length()
    }

    fun bytes(): ByteArray {
        return value.bytes()
    }

    companion object {

        fun zero(): TransactionId = TransactionId(Blake2b.zero())

        fun length(): Int {
            return Blake2b.length()
        }

        fun hash(bytes: Sequence<Any>?): TransactionId {
            TODO("Not yet implemented")
        }

        fun generate(): TransactionId {
            TODO("Not yet implemented")
        }

        fun hash(string: String?): TransactionId {
            TODO("Not yet implemented")
        }
    }
}