package com.tangem.blockchain.blockchains.alephium.source

object ALPH {
    val CoinInOneALPH: U256 = U256.unsafe(NumberKt.quintillion)
    val CoinInOneNanoAlph: U256 = U256.unsafe(NumberKt.billion)

    val MaxALPHValue: U256 = U256.Billion.mulUnsafe(CoinInOneALPH)

    val LaunchTimestamp: TimeStamp = TimeStamp.unsafe(1636379973000L) // 2021-11-08T11:20:06+00:00

    val MaxTxInputNum: Int = 256
    val MaxTxOutputNum: Int = 256

    fun nanoAlph(amount: Long): U256 {
        require(amount >= 0)
        return U256.unsafe(amount).mulUnsafe(CoinInOneNanoAlph)
    }
}