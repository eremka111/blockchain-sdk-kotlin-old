package com.tangem.blockchain.common

sealed interface FeeSelectionState {
    object Allows : FeeSelectionState
    object Forbids : FeeSelectionState
    // TODO AND-3312 delete this one
    object Unspecified : FeeSelectionState
}
