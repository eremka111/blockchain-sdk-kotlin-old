package com.tangem.blockchain.blockchains.alephium.source

import com.tangem.common.extensions.toCompressedPublicKey

fun main() {

    val publicKey = byteArrayOf().toCompressedPublicKey()
    val lockupScript = LockupScript.p2pkh(publicKey)
    val unlockScript = UnlockScript.P2PKH(publicKey)
    val amount = U256.unsafe(100000000000000000)

    val outputInfos = TxOutputInfo(
        lockupScript = lockupScript,
        attoAlphAmount = amount,
        tokens = listOf(),
        lockTime = null
    )

    val gasPrice = GasPrice(U256.unsafe(100000000000L))
    val gasOpt = GasBox(20000)

    val inputs =
        buildList {
            add(
                AssetOutputInfo(
                    ref = AssetOutputRef(
                        hint = Hint(-408858161),
                        key = TxOutputRef.Key(value = Blake2b("d2ae13153f7b55f67d96846a5e0910e60e62584bde095bd0446eb5fb66ab5fc4"))
                    ),
                    outputType = UnpersistedBlockOutput,
                    output = AssetOutput(
                        amount = U256.unsafe(3000000000000000000L),
                        lockupScript = lockupScript,
                        lockTime = TimeStamp(1731571275423),
                        tokens = listOf(),
                        additionalData = ""
                    )
                )
            )
            add(
                AssetOutputInfo(
                    ref = AssetOutputRef(
                        hint = Hint(-408858161),
                        key = TxOutputRef.Key(value = Blake2b("fcddef3e950f622742bba3320cd66e718d280ea7337b4143e2daf72fdb5c9db3"))
                    ),
                    outputType = UnpersistedBlockOutput,
                    output = AssetOutput(
                        amount = U256.unsafe(996000000000000000),
                        lockupScript = lockupScript,
                        lockTime = TimeStamp(1733823514415),
                        tokens = listOf(),
                        additionalData = ""
                    )
                )
            )
        }

    val result = TxUtils.transfer(
        fromLockupScript = lockupScript,
        fromUnlockScript = unlockScript,
        outputInfos = outputInfos,
        gasOpt = gasOpt,
        gasPrice = gasPrice,
        utxos = inputs,
    )
    result.toString()
}

object TxUtils {

    fun transfer(
        fromLockupScript: LockupScript.Asset,
        fromUnlockScript: UnlockScript,
        outputInfos: TxOutputInfo,
        gasOpt: GasBox?,
        gasPrice: GasPrice,
        utxos: List<AssetOutputInfo>,
    ): Result<UnsignedTransaction> {
        checkOutputInfos(GroupIndex(0), outputInfos)
//        checkProvidedGas(gasOpt, gasPrice) // todo из апишки?
        checkTotalAttoAlphAmount(outputInfos.attoAlphAmount)
        val totalAmountsE = UnsignedTransaction.calculateTotalAmountNeeded(outputInfos)

        val (totalAmount, totalAmountPerToken, txOutputLength) = totalAmountsE
        val selected: UtxoSelectionAlgo.Selected = selectUTXOs(
            totalAmount = totalAmount,
            totalAmountPerToken = totalAmountPerToken,
            gasOpt = gasOpt,
            gasPrice = gasPrice,
            utxos = utxos,
        ).getOrElse { null } ?: throw RuntimeException()
        val unsignedTx = UnsignedTransaction.buildTransferTx(
            fromLockupScript,
            fromUnlockScript,
            selected.assets.map { Pair(it.ref, it.output) },
            outputInfos,
            selected.gas,
            gasPrice
        )
        return unsignedTx
    }

    private fun selectUTXOs(
        totalAmount: U256,
        totalAmountPerToken: AVector<Pair<TokenId, U256>>,
        gasOpt: GasBox?,
        gasPrice: GasPrice,
        utxos: List<AssetOutputInfo>,
    ): Result<UtxoSelectionAlgo.Selected> {
        // todo utxos брать из ручки, полностью?
        // val utxos =
        //     emptyList<AssetOutputInfo>()
        //      getUsableUtxos(targetBlockHashOpt, fromLockupScript, utxosLimit)
        return UtxoSelectionAlgo.Build(UtxoSelectionAlgo.ProvidedGas(gasOpt, gasPrice, null))
            .select(
                UtxoSelectionAlgo.AssetAmounts(totalAmount, totalAmountPerToken),
                utxos,
            )
    }

    private fun checkOutputInfos(
        fromGroup: GroupIndex,
        outputInfos: TxOutputInfo,
    ): Result<Unit> {
        val outputInfos = listOf(outputInfos)
        return if (outputInfos.isEmpty()) {
            Result.failure(RuntimeException("Zero transaction outputs"))
        } else if (outputInfos.size > ALPH.MaxTxOutputNum) {
            Result.failure(RuntimeException("Too many transaction outputs, maximal value: ${ALPH.MaxTxOutputNum}"))
        } else {
            val groupIndexes = outputInfos.map {
                GroupIndex(0) // todo GroupIndex
            }.filter { it != fromGroup }

            if (groupIndexes.all { it == groupIndexes.first() }) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Different groups for transaction outputs"))
            }
        }
    }

//    private fun checkProvidedGas(gasOpt: GasBox?, gasPrice: GasPrice): Either<String, Unit> {
//        checkProvidedGasAmount(gasOpt)
//        checkGasPrice(gasPrice)
//    }

    /* private fun checkProvidedGasAmount(gasOpt: GasBox?): Either<String, Unit> {
         return when (gasOpt) {
             null -> Right(Unit)
             else -> {
                 val maximalGasPerTx = getMaximalGasPerTx()
                 if (gasOpt < minimalGas) {
                     Either.Left("Provided gas $gasOpt too small, minimal $minimalGas")
                 } else if (gasOpt > maximalGasPerTx) {
                     Either.Left("Provided gas $gasOpt too large, maximal $maximalGasPerTx")
                 } else {
                     Either.Right(Unit)
                 }
             }
         }
     }

     private fun checkGasPrice(gasPrice: GasPrice): Either<String, Unit> {
         return if (gasPrice < coinbaseGasPrice) {
             Left("Gas price $gasPrice too small, minimal $coinbaseGasPrice")
         } else if (gasPrice.value >= ALPH.MaxALPHValue) {
             val maximalGasPrice = GasPrice(ALPH.MaxALPHValue.subOneUnsafe())
             Left("Gas price $gasPrice too large, maximal $maximalGasPrice")
         } else {
             Right(Unit)
         }
     }*/

    fun checkTotalAttoAlphAmount(amount: U256): Result<U256> {
        return if (amount >= ALPH.MaxALPHValue) Result.success(amount) else Result.failure(Exception("ALPH amount overflow"))
    }

    /*
        private fun checkEstimatedGasAmount(gas: GasBox): Either<String, Unit> {
            val maximalGasPerTx = getMaximalGasPerTx()
            return if (gas > maximalGasPerTx) {
                Either.Left(
                    "Estimated gas $gas too large, maximal $maximalGasPerTx. " +
                            "Consider consolidating UTXOs using the sweep endpoints or " +
                            "sending to less addresses"
                )
            } else {
                Either.Right(Unit)
            }
        }
    */
}