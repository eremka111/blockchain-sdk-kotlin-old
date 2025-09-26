package com.tangem.blockchain.common.logging

/**
 * External logger
 *
 * @author Andrew Khokhlov on 22/02/2024
 */
interface BlockchainSDKLogger {

    fun log(level: Level, message: String)

    enum class Level {
        NETWORK,
    }
}
