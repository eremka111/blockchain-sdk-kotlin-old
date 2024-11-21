package com.tangem.blockchain.blockchains.alephium.source

typealias AVector<E> = List<E>

fun <E> AVector<E>.empty() = emptyList<E>()

data class NetworkId(val id: Int = 0)
