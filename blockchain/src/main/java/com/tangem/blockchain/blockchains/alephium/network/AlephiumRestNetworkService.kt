package com.tangem.blockchain.blockchains.alephium.network

import com.tangem.blockchain.blockchains.alephium.models.AlephiumFee
import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.toBlockchainSdkError
import com.tangem.blockchain.extensions.Result
import com.tangem.blockchain.extensions.retryIO
import com.tangem.blockchain.network.createRetrofitInstance
import java.math.BigDecimal

class AlephiumRestNetworkService(override val baseUrl: String) : AlephiumNetworkProvider {

    private val api = createRetrofitInstance(baseUrl = baseUrl).create(AlephiumApi::class.java)

    override suspend fun getInfo(address: String): Result<AlephiumResponse.Utxos> {
        return try {
            val result = retryIO { api.getUtxos(address) }
            Result.Success(result)
        } catch (e: Exception) {
            Result.Failure(e.toBlockchainSdkError())
        }
    }

    override suspend fun getFee(amount: Amount, destination: String, publicKey: String): Result<AlephiumFee> {
        return try {
            val amountString = (amount.value?.movePointRight(amount.decimals) ?: BigDecimal.ZERO).toPlainString()
            val destinations = listOf(AlephiumRequest.BuildTx.Destination(destination, amountString))
            val request = AlephiumRequest.BuildTx(
                destinations = destinations,
                fromPublicKey = publicKey,
            )
            val response = retryIO { api.buildTx(request) }
            val fee = AlephiumFee(
                gasPrice = response.gasPrice.toBigDecimal(),
                gasAmount = response.gasAmount.toBigDecimal(),
                unsignedTx = response.unsignedTx
            )
            Result.Success(fee)
        } catch (e: Exception) {
            Result.Failure(e.toBlockchainSdkError())
        }
    }

    override suspend fun apiBuild(request: ApiBuild): Result<AlephiumResponse.UnsignedTx> {
        return try {
            val amount = request.amount
            val destination = request.destination
            val amountString = (amount.value?.movePointRight(amount.decimals) ?: BigDecimal.ZERO).toPlainString()
            val destinations = listOf(AlephiumRequest.BuildTx.Destination(destination, amountString))

            val requestBuildTx = AlephiumRequest.BuildTx(
                destinations = destinations,
                fromPublicKey = request.publicKey,
            )
            Result.Success(api.buildTx(requestBuildTx))
        } catch (e: Exception) {
            Result.Failure(e.toBlockchainSdkError())
        }
    }

    override suspend fun apiDecode(unsignedTx: String): Result<AlephiumResponse.DecodedTx> {
        return try {
            Result.Success(api.decodeTx(request = AlephiumRequest.DecodeTx(unsignedTx)))
        } catch (e: Exception) {
            Result.Failure(e.toBlockchainSdkError())
        }
    }
}
