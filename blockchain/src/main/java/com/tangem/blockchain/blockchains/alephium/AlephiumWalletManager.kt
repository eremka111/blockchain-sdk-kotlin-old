package com.tangem.blockchain.blockchains.alephium

import com.tangem.blockchain.blockchains.alephium.network.AlephiumNetworkProvider
import com.tangem.blockchain.blockchains.alephium.network.AlephiumResponse.Utxo
import com.tangem.blockchain.blockchains.alephium.source.*
import com.tangem.blockchain.common.*
import com.tangem.blockchain.common.transaction.Fee
import com.tangem.blockchain.common.transaction.TransactionFee
import com.tangem.blockchain.common.transaction.TransactionSendResult
import com.tangem.blockchain.extensions.Result
import com.tangem.blockchain.extensions.successOr
import com.tangem.common.extensions.toCompressedPublicKey
import com.tangem.common.extensions.toHexString

internal class AlephiumWalletManager(
    wallet: Wallet,
    private val networkService: AlephiumNetworkProvider,
) : WalletManager(wallet) {

    override val currentHost: String get() = networkService.baseUrl
    private var utxos = listOf<Utxo>()

    override suspend fun updateInternal() {
        val info = networkService.getInfo(wallet.address).successOr { return }
        val amount = info
            .utxos
            .sumOf { it.amount.toBigDecimal().movePointLeft(wallet.blockchain.decimals()) }
        utxos = info.utxos
        wallet.setCoinValue(amount)
    }

    override suspend fun send(
        transactionData: TransactionData,
        signer: TransactionSigner,
    ): Result<TransactionSendResult> {
        TODO("Not yet implemented")
    }

    override suspend fun getFee(amount: Amount, destination: String): Result<TransactionFee> {
        val feeResponse = networkService.getFee(
            amount = amount,
            destination = destination,
            publicKey = wallet.publicKey.blockchainKey.toHexString(),
        )
            .successOr { return it }
        val fee = (feeResponse.gasAmount * feeResponse.gasPrice).movePointLeft(wallet.blockchain.decimals())

        val publicKey = wallet.publicKey.blockchainKey.toCompressedPublicKey()
        val lockupScript = LockupScript.p2pkh(publicKey)
        val unlockScript = UnlockScript.P2PKH(publicKey)
        val innerAmount = U256.unsafe(amount.longValue)

        val outputInfos = TxOutputInfo(
            lockupScript = lockupScript,
            attoAlphAmount = innerAmount,
            tokens = listOf(),
            lockTime = null
        )

        val gasPrice = GasPrice(U256.unsafe(feeResponse.gasPrice.toLong()))
        val gasOpt = GasBox(feeResponse.gasAmount.toInt())

        val inputs = utxos.map {
            AssetOutputInfo(
                ref = AssetOutputRef(
                    hint = Hint(it.ref.hint),
                    key = TxOutputRef.Key(Blake2b(it.ref.key))
                ),
                outputType = UnpersistedBlockOutput,
                output = AssetOutput(
                    amount = U256.unsafe(it.amount.toLong()),
                    lockupScript = lockupScript,
                    lockTime = TimeStamp(it.lockTime ?: 0),
                    tokens = listOf(),
                    additionalData = it.additionalData ?: ""
                )
            )
        }

        val localTx = TxUtils.transfer(
            fromLockupScript = lockupScript,
            fromUnlockScript = unlockScript,
            outputInfos = outputInfos,
            gasOpt = gasOpt,
            gasPrice = gasPrice,
            utxos = inputs,
        )

        val apiDecode = networkService.apiDecode(feeResponse.unsignedTx)
            .successOr { return it }
            .unsignedTx

        val apiDecodeUnsignedTransaction = UnsignedTransaction(
            version = apiDecode.version.toByte(),
            networkId = NetworkId(apiDecode.networkId),
            gasAmount = GasBox(apiDecode.gasAmount),
            gasPrice = GasPrice(U256.unsafe(apiDecode.gasPrice.toLong())),
            inputs = apiDecode.inputs.map {
                TxInput(
                    outputRef = AssetOutputRef(
                        hint = Hint(it.outputRef.hint),
                        key = TxOutputRef.Key(Hash(it.outputRef.key))
                    ),
                    unlockScript = UnlockScript.P2PKH(it.unlockScript.toByteArray()),
                )

            },
            fixedOutputs = apiDecode.fixedOutputs.map {
                AssetOutput(
                    amount = U256.unsafe(it.attoAlphAmount.toLong()),
                    lockupScript = lockupScript /*it.key*/,
                    lockTime = TimeStamp(it.lockTime),
                    tokens = it.tokens.map { token -> TokenId(Hash(token.id)) to U256.unsafe(token.amount.toLong()) },
                    additionalData = it.message
                )
            }
        )

        /* ScriptBuilder.createInputScript()
         ScriptBuilder.createP2PKOutputScript()

         ScriptBuilder.createInputScript(
             signature,
             ECKey.fromPublicOnly(walletPublicKey),
         )*/

        localTx.toString()
        apiDecode.toString()
        apiDecodeUnsignedTransaction.toString()
        // val localTxJson = moshi.adapter<UnsignedTransaction>().toJson(localTx.getOrThrow())
        // val apiDecodeUnsignedTransactionJson = moshi.adapter<UnsignedTransaction>().toJson(apiDecodeUnsignedTransaction)

        return Result.Success(TransactionFee.Single(Fee.Common(Amount(fee, wallet.blockchain))))
    }
}
