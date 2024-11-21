package com.tangem.blockchain.blockchains.alephium.source

// No substypes for the sake of performance
@JvmInline
value class Hint constructor(val value: Int) {


    companion object {

        // todo serde
        // We don't use Serde[Int] here as the value of Hint is random, no need of serde optimization
//        @JvmStatic
//        val serde: Serde<Hint> = Serde
//            .bytesSerde(4)
//            .xmap({ bs -> Hint(Bytes.toIntUnsafe(bs)) }, { hint -> Bytes.from(hint.value) })
//
        fun from(assetOutput: AssetOutput): Hint = ofAsset(assetOutput.lockupScript.scriptHint)
//
//         fun from(contractOutput: ContractOutput): Hint =
//             ofContract(ScriptHint(contractOutput.lockupScript.scriptHint()))
//
        fun ofAsset(scriptHint: ScriptHint): Hint = Hint(scriptHint.value)

        fun ofContract(scriptHint: ScriptHint): Hint = Hint(scriptHint.value xor 1)

        fun unsafe(value: Int): Hint = Hint(value)
    }
}