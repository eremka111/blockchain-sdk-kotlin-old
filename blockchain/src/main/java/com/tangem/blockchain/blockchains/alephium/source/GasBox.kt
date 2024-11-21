package com.tangem.blockchain.blockchains.alephium.source

data class GasBox constructor(val value: Int) : Comparable<GasBox> {
    fun use(amount: GasBox): Result<GasBox> {
        return if (this >= amount) {
            Result.success(GasBox(value - amount.value))
        } else {
            Result.failure(RuntimeException("OutOfGas"))
        }
    }

    fun toU256(): U256 {
        return U256.unsafe(value)
    }

    override fun compareTo(other: GasBox): Int {
        return this.value.compareTo(other.value)
    }

    companion object {
        // todo serde
        /* val serde: Serde<GasBox> = Serde.forProduct1(::GasBox, GasBox::value)
             Serde
             .forProduct1(Function1 { GasBox(it) }, GasBox::value)
 //            .forProduct1(::GasBox, { it.value })
             .validate { box ->
                 if (box.value >= 0) {
                     Serde.Success(Unit)
                 } else {
                     Serde.Failure("Negative gas ${box.value}")
                 }
             }*/

        fun unsafe(initialGas: Int): GasBox {
            require(initialGas >= 0)
            return GasBox(initialGas)
        }
    }
}