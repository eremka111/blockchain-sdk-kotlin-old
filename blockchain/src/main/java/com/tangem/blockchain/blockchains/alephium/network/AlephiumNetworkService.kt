package com.tangem.blockchain.blockchains.alephium.network

import com.tangem.blockchain.blockchains.alephium.models.AlephiumFee
import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.extensions.Result
import com.tangem.blockchain.network.MultiNetworkProvider

internal class AlephiumNetworkService(providers: List<AlephiumNetworkProvider>) : AlephiumNetworkProvider {

    override val baseUrl: String get() = multiJsonRpcProvider.currentProvider.baseUrl

    private val multiJsonRpcProvider = MultiNetworkProvider(providers)

    override suspend fun getInfo(address: String): Result<AlephiumResponse.Utxos> =
        multiJsonRpcProvider.performRequest(AlephiumNetworkProvider::getInfo, address)

    override suspend fun getFee(amount: Amount, destination: String, publicKey: String): Result<AlephiumFee> =
        multiJsonRpcProvider.performRequest { getFee(amount, destination, publicKey) }

    override suspend fun apiBuild(request: ApiBuild): Result<AlephiumResponse.UnsignedTx> {
        return multiJsonRpcProvider.performRequest { apiBuild(request) }
    }

    override suspend fun apiDecode(unsignedTx: String): Result<AlephiumResponse.DecodedTx> {
        return multiJsonRpcProvider.performRequest { apiDecode(unsignedTx) }
    }
}
