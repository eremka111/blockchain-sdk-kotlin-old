package com.tangem.blockchain.blockchains.alephium.source

data class ScriptHint(val value: Int) {
   /* fun groupIndex(config: GroupConfig): GroupIndex {
        val hash = Bytes.toPosInt(Bytes.xorByte(value))
        return GroupIndex(hash % config.groups)
    }*/

    companion object {
        fun fromHash(hash: Hash): ScriptHint {
            return fromHash(DjbHash.intHash(hash.bytes()))
        }

        fun fromHash(hash: Int): ScriptHint {
            return ScriptHint(hash or 1)
        }
    }
}