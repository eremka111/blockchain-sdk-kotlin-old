package com.tangem.blockchain.blockchains.alephium.network

import com.tangem.blockchain.blockchains.alephium.models.AlephiumFee
import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.NetworkProvider
import com.tangem.blockchain.extensions.Result
import java.math.BigDecimal

internal interface AlephiumNetworkProvider : NetworkProvider {

    suspend fun getInfo(address: String): Result<AlephiumResponse.Utxos>

    suspend fun getFee(amount: Amount, destination: String, publicKey: String): Result<AlephiumFee>

    suspend fun apiBuild(request: ApiBuild): Result<AlephiumResponse.UnsignedTx>

    suspend fun apiDecode(unsignedTx: String): Result<AlephiumResponse.DecodedTx>
}

data class ApiBuild(
    val publicKey: String,
    val amount: Amount,
    val destination: String,
    val gasPrice: BigDecimal,
    val gasAmount: BigDecimal,
)
