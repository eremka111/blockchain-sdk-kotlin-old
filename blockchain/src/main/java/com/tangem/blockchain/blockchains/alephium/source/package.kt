package com.tangem.blockchain.blockchains.alephium.source

typealias Hash = Blake2b

val minimalGas: GasBox = GasBox.unsafe(20000)
val dustUtxoAmount: U256 = ALPH.nanoAlph(1000000)