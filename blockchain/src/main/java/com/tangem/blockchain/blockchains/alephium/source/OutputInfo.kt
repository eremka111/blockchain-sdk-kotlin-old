package com.tangem.blockchain.blockchains.alephium.source


sealed interface OutputInfo {
    val ref: TxOutputRef
    val output: TxOutput
}

data class AssetOutputInfo(
        override val ref: AssetOutputRef,
        override val output: AssetOutput,
        val outputType: OutputType
) : OutputInfo

/*data class ContractOutputInfo(
        override val ref: ContractOutputRef,
        override val output: ContractOutput
) : OutputInfo*/

sealed interface OutputType {
    val cachedLevel: Int
}

object PersistedOutput : OutputType {
    override val cachedLevel: Int = 0
}

object UnpersistedBlockOutput : OutputType {
    override val cachedLevel: Int = 1
}

object MemPoolOutput : OutputType {
    override val cachedLevel: Int = 2
}