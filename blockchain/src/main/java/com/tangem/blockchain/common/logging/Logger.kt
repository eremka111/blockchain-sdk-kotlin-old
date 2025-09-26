package com.tangem.blockchain.common.logging

import com.tangem.blockchain.common.logging.BlockchainSDKLogger.Level

/**
 * Internal logger
 *
 * @author Andrew Khokhlov on 22/02/2024
 */
internal object Logger {

    private val loggers = mutableListOf<BlockchainSDKLogger>()

    fun logNetwork(message: String) {
        logInternal(Level.NETWORK, message)
    }

    fun addLoggers(loggers: List<BlockchainSDKLogger>) {
        this.loggers.addAll(loggers)
    }

    private fun logInternal(level: Level, message: String) {
        if (loggers.isEmpty()) return

        loggers.forEach { it.log(level, message) }
    }
}
