package com.tangem.blockchain.blockchains.alephium.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

object AlephiumResponse {
    @JsonClass(generateAdapter = true)
    data class Utxos(
        @Json(name = "utxos") val utxos: List<Utxo>,
    )

    @JsonClass(generateAdapter = true)
    data class Utxo(
        @Json(name = "additionalData") val additionalData: String?,
        @Json(name = "amount") val amount: Double,
        @Json(name = "lockTime") val lockTime: Long?,
        @Json(name = "ref") val ref: Ref,
        @Json(name = "tokens") val tokens: List<Token>?,
    ) {
        @JsonClass(generateAdapter = true)
        data class Ref(
            @Json(name = "hint") val hint: Int,
            @Json(name = "key") val key: String,
        )

        @JsonClass(generateAdapter = true)
        data class Token(
            @Json(name = "amount") val amount: Double,
            @Json(name = "id") val id: String,
        )
    }

    @JsonClass(generateAdapter = true)
    data class UnsignedTx(
        @Json(name = "fromGroup") val fromGroup: Int,
        @Json(name = "gasAmount") val gasAmount: Int,
        @Json(name = "gasPrice") val gasPrice: String,
        @Json(name = "toGroup") val toGroup: Int,
        @Json(name = "txId") val txId: String,
        @Json(name = "unsignedTx") val unsignedTx: String,
    )

    @JsonClass(generateAdapter = true)
    data class DecodedTx(
        @Json(name = "fromGroup")
        val fromGroup: Int,
        @Json(name = "toGroup")
        val toGroup: Int,
        @Json(name = "unsignedTx")
        val unsignedTx: UnsignedTx,
    ) {
        @JsonClass(generateAdapter = true)
        data class UnsignedTx(
            @Json(name = "fixedOutputs")
            val fixedOutputs: List<FixedOutput>,
            @Json(name = "gasAmount")
            val gasAmount: Int,
            @Json(name = "gasPrice")
            val gasPrice: String,
            @Json(name = "inputs")
            val inputs: List<Input>,
            @Json(name = "networkId")
            val networkId: Int,
            @Json(name = "scriptOpt")
            val scriptOpt: String?,
            @Json(name = "txId")
            val txId: String,
            @Json(name = "version")
            val version: Int,
        ) {
            @JsonClass(generateAdapter = true)
            data class FixedOutput(
                @Json(name = "address")
                val address: String,
                @Json(name = "attoAlphAmount")
                val attoAlphAmount: String,
                @Json(name = "hint")
                val hint: Int,
                @Json(name = "key")
                val key: String,
                @Json(name = "lockTime")
                val lockTime: Long,
                @Json(name = "message")
                val message: String,
                @Json(name = "tokens")
                val tokens: List<Token>,
            ) {
                @JsonClass(generateAdapter = true)
                data class Token(
                    @Json(name = "amount")
                    val amount: String,
                    @Json(name = "id")
                    val id: String,
                )
            }

            @JsonClass(generateAdapter = true)
            data class Input(
                @Json(name = "outputRef")
                val outputRef: OutputRef,
                @Json(name = "unlockScript")
                val unlockScript: String,
            ) {
                @JsonClass(generateAdapter = true)
                data class OutputRef(
                    @Json(name = "hint")
                    val hint: Int,
                    @Json(name = "key")
                    val key: String,
                )
            }
        }
    }
}
