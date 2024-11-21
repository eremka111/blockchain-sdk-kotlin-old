package com.tangem.blockchain.blockchains.alephium.source

/*import org.alephium.flow.gasestimation.AssetScriptGasEstimator
import com.tangem.blockchain.blockchains.alephium.source.GasEstimationMultiplier
import org.alephium.flow.gasestimation.TxScriptEmulator
import org.alephium.protocol.config.NetworkConfig
import com.tangem.blockchain.blockchains.alephium.source.TokenId
import com.tangem.blockchain.blockchains.alephium.source.GasBox
import com.tangem.blockchain.blockchains.alephium.source.GasPrice
import com.tangem.blockchain.blockchains.alephium.source.AVector
import com.tangem.blockchain.blockchains.alephium.source.TxInput
import org.alephium.protocol.vm.StatefulScript
import org.alephium.protocol.vm.UnlockScript
import org.alephium.util.*
import scala.util.Either
import org.alephium.flow.gasestimation.*
import org.alephium.protocol.model.**/

typealias Asset = AssetOutputInfo

/*
 * We sort the Utxos based on the amount and type
 *   - the Utxos with higher persisted level are selected first (confirmed Utxos are of high priority)
 *   - the Utxos with smaller amounts are selected first
 *   - alph selection non-token Utxos first
 *   - the above logic applies to both ALPH and tokens.
 */
// scalastyle:off parameter.number
object UtxoSelectionAlgo {
    interface AssetOrder {
        val byAlph: Comparator<Asset>
        fun byToken(id: TokenId): Comparator<Asset>
    }

    object AssetAscendingOrder : AssetOrder {
        override val byAlph: Comparator<Asset> = Comparator { x, y ->
            val compare1 = x.outputType.cachedLevel.compareTo(y.outputType.cachedLevel)
            if (compare1 != 0) {
                compare1
            } else {
                x.output.amount.compareTo(y.output.amount)
            }
        }

        override fun byToken(id: TokenId): Comparator<Asset> = Comparator { x, y ->
            val compare1 = x.outputType.cachedLevel.compareTo(y.outputType.cachedLevel)

            val tokenX = x.output.tokens.find { it.first.value == id.value }
            val tokenY = y.output.tokens.find { it.first.value == id.value }

            when {
                tokenX != null && tokenY != null -> {
                    if (compare1 != 0) {
                        compare1
                    } else {
                        tokenX.second.compareTo(tokenY.second).let {
                            if (it == 0) byAlph.compare(x, y) else it
                        }
                    }
                }

                tokenX != null -> -1
                tokenY != null -> 1
                else -> byAlph.compare(x, y)
            }
        }
    }

    object AssetDescendingOrder : AssetOrder {
        override val byAlph: Comparator<Asset> = AssetAscendingOrder.byAlph.reversed()
        override fun byToken(id: TokenId): Comparator<Asset> = AssetAscendingOrder.byToken(id).reversed()
    }

    data class Selected(val assets: AVector<Asset>, val gas: GasBox)

    data class SelectedSoFar(val alph: U256, val selected: AVector<Asset>, val rest: AVector<Asset>)

    data class ProvidedGas(
        val gasOpt: GasBox?,
        val gasPrice: GasPrice,
        val gasEstimationMultiplier: GasEstimationMultiplier?,
    )

    data class AssetAmounts(val alph: U256, val tokens: AVector<Pair<TokenId, U256>>)

    data class TxInputWithAsset(val input: TxInput, val asset: Asset) {
        companion object {
            fun from(asset: Asset, unlockScript: UnlockScript): TxInputWithAsset {
                return TxInputWithAsset(TxInput(asset.ref, unlockScript), asset)
            }
        }
    }

    data class Build(val providedGas: ProvidedGas) {
        val ascendingOrderSelector: BuildWithOrder = BuildWithOrder(providedGas, AssetAscendingOrder)

        fun select(
            amounts: AssetAmounts,
            utxos: AVector<Asset>,
        ): Result<Selected> {
            val ascendingResult = ascendingOrderSelector.select(
                amounts,
                utxos,
            )
            return ascendingResult.onFailure {
                val descendingOrderSelector = BuildWithOrder(providedGas, AssetDescendingOrder)
                descendingOrderSelector.select(
                    amounts,
                    utxos,
                )
            }
        }
    }

    data class BuildWithOrder(
        val providedGas: ProvidedGas,
        val assetOrder: AssetOrder,
    ) {
        fun select(
            amounts: AssetAmounts,
            utxos: AVector<Asset>,
        ): Result<Selected> {
            val gasPrice = providedGas.gasPrice
            return when (val gas = providedGas.gasOpt) {
                is GasBox -> {
                    val amountsWithGas = amounts.copy(alph = amounts.alph.addUnsafe(gasPrice * gas))
                    SelectionWithoutGasEstimation(assetOrder)
                        .select(amountsWithGas, utxos)
                        .map { selectedSoFar ->
                            Selected(selectedSoFar.selected, gas)
                        }
                }

                else -> TODO("not null gas")
            }
        }
    }

    data class SelectionWithoutGasEstimation(val assetOrder: AssetOrder) {
        fun select(
            amounts: AssetAmounts,
            allUtxos: AVector<Asset>,
        ): Result<SelectedSoFar> {
            val tokensFoundResult: Pair<AVector<Asset>?, AVector<Asset>?> =
                selectForTokens(amounts.tokens, emptyList(), allUtxos)
                    .getOrElse { Pair(null, null) }
            val (utxosForTokens, remainingUtxos) = tokensFoundResult
            utxosForTokens ?: return Result.failure(TODO())
            remainingUtxos ?: return Result.failure(TODO())

            val alphSelected = utxosForTokens.fold(U256.Zero) { acc, asset -> acc.addUnsafe(asset.output.amount) }
            val alphToSelect = amounts.alph.sub(alphSelected) ?: U256.Zero

            val alphFoundResult: Pair<AVector<Asset>?, AVector<Asset>?> =
                selectForAmount(alphToSelect, sortAlph(remainingUtxos)) { asset ->
                    asset.output.amount
                }.getOrElse { Pair(null, null) }

            val (utxosForAlph, restOfUtxos) = alphFoundResult
            utxosForAlph ?: return Result.failure(TODO())
            restOfUtxos ?: return Result.failure(TODO())
            val foundUtxos = utxosForTokens + utxosForAlph
            val attoAlphAmountWithoutGas =
                foundUtxos.fold(U256.Zero) { acc, asset -> acc.addUnsafe(asset.output.amount) }
            return Result.success(SelectedSoFar(attoAlphAmountWithoutGas, foundUtxos, restOfUtxos))
        }

        fun sortAlph(assets: AVector<Asset>): AVector<Asset> {
            val assetsWithoutTokens = assets.filter { it.output.tokens.isEmpty() }
            val assetsWithTokens = assets.filter { it.output.tokens.isNotEmpty() }
            return assetsWithoutTokens.sortedWith(assetOrder.byAlph) + assetsWithTokens.sortedWith(assetOrder.byAlph)
        }

        private inline fun selectForAmount(
            amount: U256,
            sortedUtxos: AVector<Asset>,
            crossinline getAmount: (Asset) -> U256,
        ): Result<Pair<AVector<Asset>, AVector<Asset>>> {
            if (amount == U256.Zero) return Result.success(Pair(emptyList(), sortedUtxos))

            val (sum, index) = sortedUtxos.foldIndexed(Pair(U256.Zero, -1)) { idx, acc, asset ->
                if (acc.first >= amount) acc
                else Pair(acc.first.addUnsafe(getAmount(asset)), idx)
            }

            return if (sum < amount) {
                Result.failure(RuntimeException("Not enough balance: got $sum, expected $amount"))
            } else {
                Result.success(Pair(sortedUtxos.take(index + 1), sortedUtxos.drop(index + 1)))
            }
        }

        tailrec fun selectForTokens(
            totalAmountPerToken: AVector<Pair<TokenId, U256>>,
            currentUtxos: AVector<Asset>,
            restOfUtxos: AVector<Asset>,
        ): Result<Pair<AVector<Asset>, AVector<Asset>>> {
            if (totalAmountPerToken.isEmpty()) return Result.success(Pair(currentUtxos, restOfUtxos))

            val (tokenId, amount) = totalAmountPerToken.first()
            val sortedUtxos = restOfUtxos.sortedWith(assetOrder.byToken(tokenId))
            val remainingTokenAmount = calculateRemainingTokensAmount(currentUtxos, tokenId, amount)

            val foundResult = selectForAmount(remainingTokenAmount, sortedUtxos) { asset ->
                asset.output.tokens
                    .find { it.first.bytes() == tokenId.value.bytes() }
                    ?.second
                    ?: U256.Zero
            }

            return foundResult.map { (first, second) ->
                selectForTokens(
                    totalAmountPerToken.drop(1),
                    currentUtxos + first,
                    second
                )
            }.getOrElse { Result.failure(it) }
        }

        private fun calculateRemainingTokensAmount(
            utxos: AVector<Asset>,
            tokenId: TokenId,
            amount: U256,
        ): U256 {
            val amountInUtxo = utxos.fold(U256.Zero) { acc, utxo ->
                utxo.output.tokens.fold(acc) { innerAcc, token ->
                    if (token.first.bytes() == tokenId.value.bytes()) innerAcc.addUnsafe(token.second) else innerAcc
                }
            }
            return amount.sub(amountInUtxo) ?: U256.Zero
        }
    }
}