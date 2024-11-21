package com.tangem.blockchain.blockchains.alephium.source

/** Up to one new token might be issued in each transaction exception for the coinbase transaction
 * The id of the new token will be hash of the first input
 *
 * @param version
 *   the version of the tx
 * @param networkId
 *   the id of the chain which can accept the tx
 * @param scriptOpt
 *   optional script for invoking stateful contracts
 * @param gasAmount
 *   the amount of gas can be used for tx execution
 * @param inputs
 *   a vector of TxInput
 * @param fixedOutputs
 *   a vector of TxOutput. ContractOutput are put in front of AssetOutput
 */

data class UnsignedTransaction(
    val version: Byte,
    val networkId: NetworkId,
    val gasAmount: GasBox,
    val gasPrice: GasPrice,
    val inputs: AVector<TxInput>,
    val fixedOutputs: AVector<AssetOutput>,
) {

    companion object {

        fun buildTxOutputs(
            fromLockupScript: LockupScript.Asset,
            inputs: AVector<Pair<AssetOutputRef, AssetOutput>>,
            outputInfos: TxOutputInfo,
            gas: GasBox,
            gasPrice: GasPrice,
        ): Result<Pair<AVector<AssetOutput>, AVector<AssetOutput>>> {
            val gasFee = preCheckBuildTx(inputs, gas, gasPrice)
                .onFailure { return Result.failure(it) }.getOrThrow()
            checkMinimalAlphPerOutput(outputInfos)
            checkTokenValuesNonZero(outputInfos)
            val txOutputs = buildOutputs(outputInfos)
            val changeOutputs = calculateChangeOutputs(fromLockupScript, inputs, txOutputs, gasFee)
                .onFailure { return Result.failure(it) }.getOrThrow()
            return Result.success(txOutputs to changeOutputs)
        }

        private inline fun calculateChangeOutputs(
            fromLockupScript: LockupScript.Asset,
            inputs: AVector<Pair<AssetOutputRef, AssetOutput>>,
            txOutputs: AVector<AssetOutput>,
            gasFee: U256,
        ): Result<AVector<AssetOutput>> {
            val inputUTXOView = inputs.map { it.second }
            val alphRemainder = calculateAlphRemainder(
                inputUTXOView.map { it.amount },
                txOutputs.map { it.amount },
                gasFee
            )
                .onFailure { return Result.failure(it) }.getOrThrow()
            val tokensRemainder = calculateTokensRemainder(
                inputUTXOView.flatMap { it.tokens },
                txOutputs.flatMap { it.tokens }
            )
                .onFailure { return Result.failure(it) }.getOrThrow()
            val changeOutputs = calculateChangeOutputs(alphRemainder, tokensRemainder, fromLockupScript)
            return changeOutputs
        }

        fun calculateChangeOutputs(
            alphRemainder: U256,
            tokensRemainder: AVector<Pair<TokenId, U256>>,
            fromLockupScript: LockupScript.Asset,
        ): Result<AVector<AssetOutput>> {
            return if (alphRemainder == U256.Zero && tokensRemainder.isEmpty()) {
                Result.success(emptyList())
            } else {
                val tokenDustAmount = dustUtxoAmount.mulUnsafe(U256.unsafe(tokensRemainder.size))
                val totalDustAmount = tokenDustAmount.addUnsafe(dustUtxoAmount)

                when {
                    (alphRemainder == tokenDustAmount) || (alphRemainder >= totalDustAmount) -> {
                        Result.success(
                            buildOutputs(TxOutputInfo(fromLockupScript, alphRemainder, tokensRemainder, null, null))
                        )
                    }

                    tokensRemainder.isEmpty() -> {
                        Result.failure(
                            RuntimeException(
                                "Not enough ALPH for ALPH change output, expected $dustUtxoAmount, got $alphRemainder"
                            )
                        )
                    }

                    alphRemainder < tokenDustAmount -> {
                        Result.failure(
                            RuntimeException(
                                "Not enough ALPH for token change output, expected $tokenDustAmount, got $alphRemainder"
                            )
                        )
                    }

                    else -> {
                        Result.failure(
                            RuntimeException(
                                "Not enough ALPH for ALPH and token change output, expected $totalDustAmount, got $alphRemainder"
                            )
                        )
                    }
                }
            }
        }

        fun buildOutputs(outputInfos: AVector<TxOutputInfo>): AVector<AssetOutput> {
            return outputInfos.flatMap { buildOutputs(it) }
        }

        fun buildOutputs(outputInfo: TxOutputInfo): AVector<AssetOutput> {
            val (toLockupScript, attoAlphAmount, tokens, lockTimeOpt, additionalDataOpt) = outputInfo
            val tokenOutputs = tokens.map { token ->
                AssetOutput(
                    dustUtxoAmount,
                    toLockupScript,
                    lockTimeOpt ?: (TimeStamp.zero),
                    listOf(token),
                    additionalDataOpt ?: ""
                )
            }
            val alphRemaining = attoAlphAmount
                .sub(dustUtxoAmount.mulUnsafe(U256.unsafe(tokens.size))) ?: U256.Zero
            return if (alphRemaining == U256.Zero) {
                tokenOutputs
            } else {
                val alphOutput = AssetOutput(
                    maxOf(alphRemaining, dustUtxoAmount),
                    toLockupScript,
                    lockTimeOpt ?: (TimeStamp.zero),
                    listOf(),
                    additionalDataOpt ?: ""
                )
                tokenOutputs + alphOutput
            }
        }

        /*  private def checkMinimalAlphPerOutput(
          outputs: AVector[TxOutputInfo]
          ): Either[String, Unit] = {
              check(
                  failCondition = outputs.exists { output =>
                      output.attoAlphAmount < dustUtxoAmount
                  },
                  "Tx output value is too small, avoid spreading dust"
              )
          }

          private def checkTokenValuesNonZero(
          outputs: AVector[TxOutputInfo]
          ): Either[String, Unit] = {
              check(
                  failCondition = outputs.exists(_.tokens.exists(_._2.isZero)),
                  "Value is Zero for one or many tokens in the transaction output"
              )
          }*/
        private fun checkMinimalAlphPerOutput(
            output: TxOutputInfo,
        ): Result<Unit> {
            return check(
                failCondition = output.attoAlphAmount < dustUtxoAmount,
                "Tx output value is too small, avoid spreading dust"
            )
        }

        private fun checkTokenValuesNonZero(
            output: TxOutputInfo,
        ): Result<Unit> {
            return check(
                failCondition = output.tokens.any { it.second.isZero },
                "Value is Zero for one or many tokens in the transaction output"
            )
        }

        private fun check(failCondition: Boolean, errorMessage: String): Result<Unit> {
            return if (!failCondition) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException(errorMessage))
            }
        }

        /*  @inline private def preCheckBuildTx(
          inputs: AVector[(AssetOutputRef, AssetOutput)],
          gas: GasBox,
          gasPrice: GasPrice
          ): Either[String, U256] = {
              assume(gas >= minimalGas)
              assume(gasPrice.value <= ALPH.MaxALPHValue)
              for {
                  _ <- checkWithMaxTxInputNum(inputs)
                  _ <- checkUniqueInputs(inputs)
              } yield gasPrice * gas
          }*/
        private inline fun preCheckBuildTx(
            inputs: AVector<Pair<AssetOutputRef, AssetOutput>>,
            gas: GasBox,
            gasPrice: GasPrice,
        ): Result<U256> {
            if (gas < minimalGas) throw RuntimeException()// todo
            if (gasPrice.value > ALPH.MaxALPHValue) throw RuntimeException()// todo
            checkWithMaxTxInputNum(inputs)
            checkUniqueInputs(inputs)
            return Result.success(gasPrice * gas)
        }

        /*  def checkWithMaxTxInputNum(
          assets: AVector[(AssetOutputRef, AssetOutput)]
          ): Either[String, Unit] = {
              check(
                  failCondition = assets.length > ALPH.MaxTxInputNum,
                  "Too many inputs for the transfer, consider to reduce the amount to send, or use the `sweep-address` endpoint to consolidate the inputs first"
              )
          }*/
        fun checkWithMaxTxInputNum(
            assets: List<Pair<AssetOutputRef, AssetOutput>>,
        ): Result<Unit> {
            return check(
                failCondition = assets.size > ALPH.MaxTxInputNum,
                "Too many inputs for the transfer, consider to reduce the amount to send, or use the `sweep-address` endpoint to consolidate the inputs first"
            )
        }

        /*  def checkUniqueInputs(
          assets: AVector[(AssetOutputRef, AssetOutput)]
          ): Either[String, Unit] = {
              check(
                  failCondition = assets.length > assets.map(_._1).toSet.size,
                  "Inputs not unique"
              )
          }*/
        fun checkUniqueInputs(
            assets: List<Pair<AssetOutputRef, AssetOutput>>,
        ): Result<Unit> {
            return check(
                failCondition = assets.size > assets.map { it.first }.toSet().size,
                "Inputs not unique"
            )
        }

        /*  private def calculateAlphRemainder(
          inputs: AVector[AssetOutput],
          outputs: AVector[AssetOutput],
          gasFee: U256
          ): Either[String, U256] = {
              calculateAlphRemainder(
                  inputs.map(_.amount),
                  outputs.map(_.amount),
                  gasFee
              )
          }*/

        /*  def calculateAlphRemainder(
          inputs: AVector[U256],
          outputs: AVector[U256],
          gasFee: U256
          ): Either[String, U256] = {
              for {
                  inputSum <- EitherF.foldTry(inputs, U256.Zero)(_ add _ toRight "Input amount overflow")
                  outputAmount <- outputs.foldE(U256.Zero)(
                      _ add _ toRight "Output amount overflow"
                  )
                  remainder0 <- inputSum.sub(outputAmount).toRight("Not enough balance")
                  remainder  <- remainder0.sub(gasFee).toRight("Not enough balance for gas fee")
              } yield remainder
          }*/
        fun calculateAlphRemainder(
            inputs: AVector<U256>,
            outputs: AVector<U256>,
            gasFee: U256,
        ): Result<U256> {
            val inputSum = inputs.fold(U256.Zero) { acc, sum ->
                acc.add(sum) ?: return Result.failure(RuntimeException("Input amount overflow"))
            }
            val outputAmount = outputs.fold(U256.Zero) { acc, sum ->
                acc.add(sum) ?: return Result.failure(RuntimeException("Output amount overflow"))
            }
            val remainder0 = inputSum.sub(outputAmount) ?: return Result.failure(RuntimeException("Not enough balance"))
            val remainder =
                remainder0.sub(gasFee) ?: return Result.failure(RuntimeException("Not enough balance for gas fee"))
            return Result.success(remainder)
        }

        /*  private def calculateTokensRemainder(
          inputs: AVector[AssetOutput],
          outputs: AVector[AssetOutput]
          ): Either[String, AVector[(TokenId, U256)]] = {
              calculateTokensRemainder(
                  inputs.flatMap(_.tokens),
                  outputs.flatMap(_.tokens)
              )
          }*/

        /*  def calculateTokensRemainder(
          inputs: AVector[(TokenId, U256)],
          outputs: AVector[(TokenId, U256)]
          ): Either[String, AVector[(TokenId, U256)]] = {
              for {
                  inputs    <- calculateTotalAmountPerToken(inputs)
                  outputs   <- calculateTotalAmountPerToken(outputs)
                  _         <- checkNoNewTokensInOutputs(inputs, outputs)
                  remainder <- calculateRemainingTokens(inputs, outputs)
              } yield {
                  remainder.filterNot(_._2 == U256.Zero)
              }
          }*/
        fun calculateTokensRemainder(
            inputsIn: AVector<Pair<TokenId, U256>>,
            outputsIn: AVector<Pair<TokenId, U256>>,
        ): Result<AVector<Pair<TokenId, U256>>> {
            val inputs = calculateTotalAmountPerToken(inputsIn).onFailure { return Result.failure(it) }.getOrThrow()
            val outputs = calculateTotalAmountPerToken(outputsIn).onFailure { return Result.failure(it) }.getOrThrow()
            checkNoNewTokensInOutputs(inputs, outputs)
            val remainder = calculateRemainingTokens(inputs, outputs)
                .onFailure { return Result.failure(it) }.getOrThrow()
            return Result.success(remainder.filterNot { it.second == U256.Zero })
        }

        /*  private def calculateRemainingTokens(
          inputTokens: AVector[(TokenId, U256)],
          outputTokens: AVector[(TokenId, U256)]
          ): Either[String, AVector[(TokenId, U256)]] = {
              inputTokens.foldE(AVector.empty[(TokenId, U256)]) { case (acc, (inputId, inputAmount)) =>
                  val outputAmount = outputTokens.find(_._1 == inputId).fold(U256.Zero)(_._2)
                  inputAmount.sub(outputAmount).toRight(s"Not enough balance for token $inputId").map {
                      remainder =>
                      acc :+ (inputId -> remainder)
                  }
              }
          }*/
        private fun calculateRemainingTokens(
            inputTokens: AVector<Pair<TokenId, U256>>,
            outputTokens: AVector<Pair<TokenId, U256>>,
        ): Result<AVector<Pair<TokenId, U256>>> {
            return Result.success(inputTokens.fold(listOf()) { acc, (inputId, inputAmount) ->
                val outputAmount = outputTokens.find { it.first == inputId }?.second ?: U256.Zero
                val remainder: U256 = inputAmount.sub(outputAmount)
                    ?: return Result.failure(RuntimeException("Not enough balance for token $inputId"))
                acc.plus(inputId to remainder)
            })
        }

        /*  def calculateTotalAmountPerToken(
          tokens: AVector[(TokenId, U256)]
          ): Either[String, AVector[(TokenId, U256)]] = {
              tokens.foldE(AVector.empty[(TokenId, U256)]) { case (acc, (id, amount)) =>
                  val index = acc.indexWhere(_._1 == id)
                  if (index == -1) {
                      Right(acc :+ (id -> amount))
                  } else {
                      acc(index)._2.add(amount).toRight(s"Amount overflow for token $id").map { amt =>
                          acc.replace(index, (id, amt))
                      }
                  }
              }
          }*/
        fun calculateTotalAmountPerToken(
            tokens: AVector<Pair<TokenId, U256>>,
        ): Result<AVector<Pair<TokenId, U256>>> {
            return Result.success(tokens.fold(listOf()) { acc, (id, amount) ->
                val index = acc.indexOfFirst { it.first == id }
                if (index == -1) {
                    (acc + Pair(id, amount))
                } else {
                    val amt: U256 = acc[index].second.add(amount)
                        ?: return Result.failure(RuntimeException("Amount overflow for token $id"))
                    val list = acc.toMutableList()
                    list[index] = Pair(id, amt)
                    list
                }
            }
            )
        }

        /*  private def checkNoNewTokensInOutputs(
          inputs: AVector[(TokenId, U256)],
          outputs: AVector[(TokenId, U256)]
          ): Either[String, Unit] = {
              val newTokens = outputs.map(_._1).toSet -- inputs.map(_._1).toSet
              check(
                  failCondition = newTokens.nonEmpty,
                  s"New tokens found in outputs: $newTokens"
              )
          }*/
        private fun checkNoNewTokensInOutputs(
            inputs: AVector<Pair<TokenId, U256>>,
            outputs: AVector<Pair<TokenId, U256>>,
        ): Result<Unit> {
            val newTokens = outputs.map { it.first }.toSet() - inputs.map { it.first }.toSet()
            return check(
                failCondition = newTokens.isNotEmpty(),
                errorMessage = "New tokens found in outputs: $newTokens"
            )
        }

        /*  def buildTransferTx(
          fromLockupScript: LockupScript.Asset,
          fromUnlockScript: UnlockScript,
          inputs: AVector[(AssetOutputRef, AssetOutput)],
          outputInfos: AVector[TxOutputInfo],
          gas: GasBox,
          gasPrice: GasPrice
          )(implicit networkConfig: NetworkConfig): Either[String, UnsignedTransaction] = {
              buildTxOutputs(fromLockupScript, inputs, outputInfos, gas, gasPrice)
                  .map { case (txOutputs, changeOutputs) =>
                      UnsignedTransaction(
                          None,
                          gas,
                          gasPrice,
                          buildInputs(fromUnlockScript, inputs),
                          txOutputs ++ changeOutputs
                      )
                  }
          }*/
        fun buildTransferTx(
            fromLockupScript: LockupScript.Asset,
            fromUnlockScript: UnlockScript,
            inputs: AVector<Pair<AssetOutputRef, AssetOutput>>,
            outputInfos: TxOutputInfo,
            gas: GasBox,
            gasPrice: GasPrice,
        ): Result<UnsignedTransaction> {
            return buildTxOutputs(
                fromLockupScript,
                inputs,
                outputInfos,
                gas,
                gasPrice
            ).map { (txOutputs, changeOutputs) ->
                UnsignedTransaction(
                    version = 0,
                    networkId = NetworkId(0),
                    gasAmount = gas,
                    gasPrice = gasPrice,
                    inputs = buildInputs(fromUnlockScript, inputs),
                    fixedOutputs = txOutputs + changeOutputs
                )
            }
        }

        /*  private def buildInputs(
          fromUnlockScript: UnlockScript,
          inputs: AVector[(AssetOutputRef, AssetOutput)]
          ): AVector[TxInput] = {
              assume(fromUnlockScript != UnlockScript.SameAsPrevious)
              inputs.mapWithIndex { case ((outputRef, _), index) =>
                  if (index == 0) {
                      TxInput(outputRef, fromUnlockScript)
                  } else {
                      TxInput(outputRef, UnlockScript.SameAsPrevious)
                  }
              }
          }*/
        private fun buildInputs(
            fromUnlockScript: UnlockScript,
            inputs: AVector<Pair<AssetOutputRef, AssetOutput>>,
        ): AVector<TxInput> {
//          require(fromUnlockScript != UnlockScript.SameAsPrevious)
            return inputs.mapIndexed { index, (outputRef, _) ->
                if (index == 0) {
                    TxInput(outputRef, fromUnlockScript)
                } else {
                    TxInput(outputRef, fromUnlockScript)
                    // todo ?? TxInput(outputRef, UnlockScript.SameAsPrevious)
                }
            }
        }

        // Note: this would calculate excess dustAmount to cover the complicated cases
        fun calculateTotalAmountNeeded(
            outputInfo: TxOutputInfo,
        ): Triple<U256, AVector<Pair<TokenId, U256>>, Int> {
            var totalAlphAmount = U256.Zero
            var totalTokens = emptyMap<TokenId, U256>()
            var totalOutputLength = 0
            val tokenDustAmount = dustUtxoAmount.mulUnsafe(U256.unsafe(outputInfo.tokens.size))
            val outputLength = outputInfo.tokens.size + // UTXOs for token
                if (outputInfo.attoAlphAmount <= tokenDustAmount) 0 else 1 // UTXO for ALPH
            val alphAmount = maxOf(outputInfo.attoAlphAmount, dustUtxoAmount.mulUnsafe(U256.unsafe(outputLength)))
            val newAlphAmount = totalAlphAmount.add(alphAmount) ?: U256.Zero
            val newTotalTokens = updateTokens(totalTokens, outputInfo.tokens).getOrElse { mapOf<TokenId, U256>() }
            totalAlphAmount = newAlphAmount
            totalTokens = newTotalTokens
            totalOutputLength += outputLength

            val outputLengthSender = totalTokens.size + 1
            val alphAmountSender = dustUtxoAmount.mulUnsafe(U256.unsafe(outputLengthSender))
            val finalAlphAmount = totalAlphAmount.add(alphAmountSender) ?: U256.Zero
            return Triple(
                finalAlphAmount,
                totalTokens.toList(),
                totalOutputLength + outputLengthSender
            )
        }

        /*  private def updateTokens(
          totalTokens: ListMap[TokenId, U256],
          newTokens: AVector[(TokenId, U256)]
          ): Either[String, ListMap[TokenId, U256]] = {
              newTokens.foldE(totalTokens) { case (acc, (tokenId, amount)) =>
                  acc.get(tokenId) match {
                      case Some(totalAmount) =>
                      totalAmount.add(amount) match {
                          case Some(newAmount) => Right(acc + (tokenId -> newAmount))
                          case None            => Left(s"Amount overflow for token $tokenId")
                      }
                      case None => Right(acc + (tokenId -> amount))
                  }
              }
          }*/
        private fun updateTokens(
            totalTokens: Map<TokenId, U256>,
            newTokens: AVector<Pair<TokenId, U256>>,
        ): Result<Map<TokenId, U256>> {
            val result = newTokens.fold(totalTokens) { acc, (tokenId, amount) ->
                val totalAmount = acc[tokenId]
                if (totalAmount == null) {
                    acc + (tokenId to amount)
                } else {
                    val newAmount = totalAmount.add(amount)
                        ?: return Result.failure(RuntimeException("Amount overflow for token $tokenId"))
                    acc + (tokenId to newAmount)
                }
            }
            return Result.success(result)
        }
    }
}

data class TxOutputInfo(
    val lockupScript: LockupScript.Asset,
    val attoAlphAmount: U256,
    val tokens: AVector<Pair<TokenId, U256>>,
    val lockTime: TimeStamp?,
    val additionalDataOpt: String?,
) {
    companion object {
        operator fun invoke(
            lockupScript: LockupScript.Asset,
            attoAlphAmount: U256,
            tokens: AVector<Pair<TokenId, U256>>,
            lockTime: TimeStamp?,
        ): TxOutputInfo {
            return TxOutputInfo(lockupScript, attoAlphAmount, tokens, lockTime, null)
        }
    }
}

data class UnlockScriptWithAssets(
    val fromUnlockScript: UnlockScript,
    val assets: AVector<Pair<AssetOutputRef, AssetOutput>>,
)