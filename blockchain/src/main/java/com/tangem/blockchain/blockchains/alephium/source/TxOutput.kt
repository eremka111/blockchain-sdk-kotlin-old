package com.tangem.blockchain.blockchains.alephium.source

sealed interface TxOutput {
    val amount: U256
    val lockupScript: LockupScript
    val tokens: AVector<Pair<TokenId, U256>>

    val hint: Hint

    val isAsset: Boolean

    val isContract: Boolean
        get() = !isAsset

    fun payGasUnsafe(fee: U256): TxOutput


    companion object {
        // todo serde
        /*@JvmStatic
        val serde: Serde<TxOutput> = eitherSerde<AssetOutput, ContractOutput>()
            .xmap(
                { it.fold({ assetOutput -> assetOutput }, { contractOutput -> contractOutput }) },
                { output ->
                    when (output) {
                        is AssetOutput -> Either.left(output)
                        is ContractOutput -> Either.right(output)
                    }
                }
            )*/

        fun from(
                amount: U256,
                tokens: AVector<Pair<TokenId, U256>>,
                lockupScript: LockupScript.Asset
        ): AVector<TxOutput>? =
                from(amount, tokens, lockupScript, TimeStamp.zero)

        fun from(
                amount: U256,
                tokens: AVector<Pair<TokenId, U256>>,
                lockupScript: LockupScript.Asset,
                lockTime: TimeStamp
        ): AVector<TxOutput>? {
            val outputs = Array<TxOutput?>(tokens.size + 1) { null }
            tokens.forEachIndexed { index, token ->
                outputs[index] =
                        AssetOutput(dustUtxoAmount, lockupScript, lockTime, listOf(token), "")
            }
            val totalTokenDustAmount = dustUtxoAmount.mulUnsafe(U256.unsafe(tokens.size))
            return when {
                amount == totalTokenDustAmount -> {
                    outputs.filterNotNull()
                }

                amount >= totalTokenDustAmount.addUnsafe(dustUtxoAmount) -> {
                    val alphRemaining = amount.subUnsafe(totalTokenDustAmount)
                    outputs[tokens.size] =
                            AssetOutput(
                                    alphRemaining,
                                    lockupScript,
                                    lockTime,
                                    listOf(),
                                    ""
                            )
                    outputs.mapNotNull { it }
                }

                else -> null
            }
        }

     /*   fun asset(amount: U256, lockupScript: LockupScript.Asset): AssetOutput =
                asset(amount, emptyList(), lockupScript)

        fun asset(
                amount: U256,
                tokens: AVector<Pair<TokenId, U256>>,
                lockupScript: LockupScript.Asset
        ): AssetOutput =
                asset(amount, lockupScript, tokens, TimeStamp.zero)

        fun asset(
                amount: U256,
                lockupScript: LockupScript.Asset,
                tokens: AVector<Pair<TokenId, U256>>,
                lockTimeOpt: TimeStamp?
        ): AssetOutput =
                asset(amount, lockupScript, tokens, lockTimeOpt ?: TimeStamp.zero)

        fun asset(
                amount: U256,
                lockupScript: LockupScript.Asset,
                tokens: AVector<Pair<TokenId, U256>>,
                lockTime: TimeStamp
        ): AssetOutput =
                AssetOutput(amount, lockupScript, lockTime, tokens, "")*/


    }
}

/** @param amount
 * the number of ALPH in the output
 * @param lockupScript
 * guarding script for unspent output
 * @param lockTime
 * the timestamp until when the tx can be used. it's zero by default, and will be replaced with
 * block timestamp in worldstate if it's zero we could implement relative time lock based on
 * block timestamp
 * @param tokens
 * secondary tokens in the output
 * @param additionalData
 * data payload for additional information
 */
data class AssetOutput(
        override val amount: U256,
        override val lockupScript: LockupScript.Asset,
        val lockTime: TimeStamp,
        override val tokens: AVector<Pair<TokenId, U256>>,
        val additionalData: String
) : TxOutput {
    override val isAsset: Boolean
        get() = true

    override val hint: Hint
        get() = Hint.from(this)

    // fun toGroup(config: GroupConfig): GroupIndex = GroupIndex(lockupScript.groupIndex(config))

    override fun payGasUnsafe(fee: U256): AssetOutput =
            AssetOutput(amount.subUnsafe(fee), lockupScript, lockTime, tokens, additionalData)

    companion object {
        // todo serde
//        private val tokenSerde: Serde<Pair<TokenId, U256>> = Serde.tuple2()
//        val serde: Serde<AssetOutput> = Serde.forProduct5(::AssetOutput, {
//            it.amount to it.lockupScript to it.lockTime to it.tokens to it.additionalData
//        })

    }
}

/*
data class ContractOutput(
        override val amount: U256,
        override val lockupScript: LockupScript.P2C,
        override val tokens: AVector<Pair<TokenId, U256>>
) : TxOutput {
    override val isAsset: Boolean
        get() = false

    override val hint: Hint
        get() = Hint.from(this)

    override fun payGasUnsafe(fee: U256): ContractOutput =
            ContractOutput(amount.subUnsafe(fee), lockupScript, tokens)

    companion object {
        // todo serde
//        import AssetOutput.tokenSerde
//        val serde: Serde<ContractOutput> = Serde.forProduct3(::ContractOutput) {
//            it.amount to it.lockupScript to it.tokens
//        }
    }
}
*/
