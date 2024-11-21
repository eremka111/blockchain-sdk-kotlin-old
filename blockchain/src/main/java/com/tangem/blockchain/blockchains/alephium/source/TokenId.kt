package com.tangem.blockchain.blockchains.alephium.source

data class TokenId constructor(val value: Hash) {
    fun length(): Int {
        return value.length()
    }

    fun bytes(): ByteArray {
        return value.bytes()
    }
}

/*
object TokenId : HashUtils<TokenId>() {
    @JvmStatic
    val serde: Serde<TokenId> = Serde.forProduct1(::TokenId) { it.value }

    @JvmStatic
    val tokenIdOrder: Comparator<TokenId> = compareBy { it.bytes }

    @JvmStatic
    val zero: TokenId = TokenId(Hash.zero)

    @JvmStatic
    val alph: TokenId = zero

    @JvmStatic
    val length: Int
        get() = Hash.length

    @JvmStatic
    fun generate(): TokenId = TokenId(Hash.generate)

    @JvmStatic
    fun from(contractId: ContractId): TokenId {
        return TokenId(contractId.value)
    }

    @JvmStatic
    fun from(bytes: ByteString): TokenId? {
        return Hash.from(bytes)?.let { TokenId(it) }
    }

    @JvmStatic
    inline fun hash(bytes: Sequence<Byte>): TokenId = TokenId(Hash.hash(bytes))

    @JvmStatic
    inline fun hash(str: String): TokenId = hash(ByteString.fromString(str))

    @JvmStatic
    inline fun unsafe(hash: Hash): TokenId = TokenId(hash)
}*/
