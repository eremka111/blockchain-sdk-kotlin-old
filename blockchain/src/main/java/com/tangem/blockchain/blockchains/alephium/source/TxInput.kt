package com.tangem.blockchain.blockchains.alephium.source

data class TxInput(val outputRef: AssetOutputRef, val unlockScript: UnlockScript) {

    companion object {
        // Note that the serialization has to put outputRef in the first 32 bytes for the sake of trie indexing
        // todo serde
//        val serde: Serde<TxInput> = Serde.forProduct2(::TxInput, { Pair(it.outputRef, it.unlockScript) })

    }
}

sealed interface TxOutputRef {
    val hint: Hint
    val key: Key

    val isAssetType: Boolean
    val isContractType: Boolean

    data class Key(val value: Hash)

    companion object {

        // todo serde
//        val keySerde: Serde<Key> = serdeImpl<Hash>().xmap(::Key, { it.value })

        // fun key(txId: TransactionId, outputIndex: Int): Key {
        //     return Key(Blake2b.hash(txId.bytes().concat(Bytes.from(outputIndex))))
        // }

    }
}

data class AssetOutputRef constructor(override val hint: Hint, override val key: TxOutputRef.Key) :
    TxOutputRef {
    override val isAssetType: Boolean = true
    override val isContractType: Boolean = false

    // fun fromGroup(config: GroupConfig): GroupIndex =  TODO() // hint.scriptHint().groupIndex

    override fun hashCode(): Int = key.hashCode()
    override fun equals(other: Any?): Boolean =
        when (other) {
            is AssetOutputRef -> hint == other.hint && key == other.key
            else -> false
        }

    companion object {
        // todo serde
       /* val serde: Serde<AssetOutputRef> = Serde
            .forProduct2(::unsafe, { Pair(it.hint, it.key) })
            .validate { outputRef ->
                if (outputRef.hint.isAssetType) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Expect AssetOutputRef, got ContractOutputRef"))
                }
            }*/

        // Hint might not be from script hint
        /*fun unsafe(hint: Hint, key: TxOutputRef.Key): AssetOutputRef = AssetOutputRef(hint, key)

        fun from(scriptHint: ScriptHint, key: TxOutputRef.Key): AssetOutputRef =
            unsafe(Hint.ofAsset(scriptHint), key)

        fun from(output: AssetOutput, key: TxOutputRef.Key): AssetOutputRef =
            AssetOutputRef(output.hint, key)

        // Only use this to initialize Merkle tree of outputs
        fun forSMT(): AssetOutputRef {
            val hint = Hint.ofAsset(ScriptHint.fromHash(0))
            return unsafe(hint, TxOutputRef.unsafeKey(Hash.Blake2b.zero()))
        }*/
    }
}

data class ContractOutputRef private constructor(override val hint: Hint, override val key: TxOutputRef.Key) :
    TxOutputRef {
    override val isAssetType: Boolean = false
    override val isContractType: Boolean = true

    companion object {
        // todo serde
        /*val serde: Serde<ContractOutputRef> = Serde
            .forProduct2(::unsafe, { Pair(it.hint, it.key) })
            .validate { outputRef ->
                if (outputRef.hint.isContractType) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Expected ContractOutputRef, got AssetOutputRef"))
                }
            }*/

    /*    fun inaccurateFirstOutput(contractId: ContractId): ContractOutputRef {
            val outputHint = Hint.ofContract(LockupScript.p2c(contractId).scriptHint)
            return ContractOutputRef(outputHint, TxOutputRef.firstContractOutputKey(contractId))
        }*/

        // Hint might not be from contract hint
//        fun unsafe(hint: Hint, key: Key): ContractOutputRef = ContractOutputRef(hint, key)
//
//        fun from(txId: TransactionId, contractOutput: ContractOutput, outputIndex: Int): ContractOutputRef {
//            val refKey = TxOutputRef.key(txId, outputIndex)
//            return ContractOutputRef(contractOutput.hint, refKey)
//        }
//
//        // Only use this to initialize Merkle tree of outputs
//        fun forSMT(): ContractOutputRef {
//            val hint = Hint.ofContract(ScriptHint.fromHash(0))
//            return unsafe(hint, TxOutputRef.unsafeKey(Hash.zero))
//        }
    }
}
