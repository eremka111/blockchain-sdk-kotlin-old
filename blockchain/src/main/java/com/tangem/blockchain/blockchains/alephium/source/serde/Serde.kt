package com.tangem.blockchain.blockchains.alephium.source.serde

import com.tangem.blockchain.blockchains.alephium.source.*

interface Serde<T> : Serializer<T>, Deserializer<T> {

    fun <S> xmap(to: (T) -> S, from: (S) -> T): Serde<S> {
        return object : Serde<S> {
            override fun serialize(input: S): ByteArray {
                return this@Serde.serialize(from(input))
            }

            override fun deserialize(input: ByteArray): Result<S> {
                return this@Serde.deserialize(input).map(to)
            }

            override fun _deserialize(input: ByteArray): Result<Staging<S>> {
                return this@Serde._deserialize(input).map { (t, rest) ->
                    Staging(to(t), rest)
                }
            }
        }
    }

    fun <S> xfmap(to: (T) -> Result<S>, from: (S) -> T): Serde<S> {
        return object : Serde<S> {
            override fun serialize(input: S): ByteArray {
                return this@Serde.serialize(from(input))
            }

            override fun deserialize(input: ByteArray): Result<S> {
                return this@Serde.deserialize(input).map(to).getOrElse { Result.failure(it) }
            }

            override fun _deserialize(input: ByteArray): Result<Staging<S>> {
                return this@Serde._deserialize(input)
                    .map { (t, rest) -> to(t).map { Staging(it, rest) } }
                    .getOrElse { Result.failure(it) }
            }
        }
    }

    fun <S> xomap(to: (T) -> S?, from: (S) -> T): Serde<S> {
        return xfmap(
            { t ->
                to(t)?.let { Result.success(it) } ?: Result.failure(SerdeError.validation("validation error"))
            },
            from
        )
    }

    fun validate(test: (T) -> Result<Unit>): Serde<T> {
        return object : Serde<T> {
            override fun serialize(input: T): ByteArray {
                return this@Serde.serialize(input)
            }

            override fun deserialize(input: ByteArray): Result<T> {
                return this@Serde.deserialize(input).map { t ->
                    val result = test(t)
                    result.fold(
                        onSuccess = { Result.success(t) },
                        onFailure = { Result.failure(SerdeError.validation(it.message.orEmpty())) }
                    )
                }.getOrElse { Result.failure(it) }
            }

            override fun _deserialize(input: ByteArray): Result<Staging<T>> {
                return this@Serde._deserialize(input).map { (t, rest) ->
                    val result = test(t)
                    result.fold(
                        onSuccess = { Result.success(Staging(t, rest)) },
                        onFailure = { Result.failure(SerdeError.validation(it.message.orEmpty())) }
                    )
                }.getOrElse { Result.failure(it) }
            }
        }
    }

    companion object {

        object BoolSerde : FixedSizeSerde<Boolean> {
            override val serdeSize: Int = Byte.SIZE_BYTES

            override fun serialize(input: Boolean): ByteArray {
                return byteArrayOf(if (input) 1 else 0)
            }

            override fun deserialize(input: ByteArray): Result<Boolean> {
                return ByteSerde.deserialize(input).map {
                    when (it) {
                        0.toByte() -> Result.success(false)
                        1.toByte() -> Result.success(true)
                        else -> Result.failure(SerdeError.validation("Invalid bool from byte $it"))
                    }
                }.getOrElse { Result.failure(it) }
            }
        }

        object ByteSerde : FixedSizeSerde<Byte> {
            override val serdeSize: Int = Byte.SIZE_BYTES

            override fun serialize(input: Byte): ByteArray {
                return byteArrayOf(input)
            }

            override fun deserialize(input: ByteArray): Result<Byte> {
                return deserialize0(input) { it[0] }
            }
        }

        object IntSerde : Serde<Int> {
            override fun serialize(input: Int): ByteArray {
                return CompactInteger.Signed.encode(input)
            }

            override fun _deserialize(input: ByteArray): Result<Staging<Int>> {
                return CompactInteger.Signed.decodeInt(input)
            }
        }

        object LongSerde : Serde<Long> {
            override fun serialize(input: Long): ByteArray {
                return CompactInteger.Signed.encode(input)
            }

            override fun _deserialize(input: ByteArray): Result<Staging<Long>> {
                return CompactInteger.Signed.decodeLong(input)
            }
        }

        /*object I256Serde : Serde<I256> {
            override fun serialize(input: I256): ByteArray {
                return CompactInteger.Signed.encode(input)
            }

            override fun _deserialize(input: ByteArray): Result<Staging<I256>> {
                return CompactInteger.Signed.decodeI256(input)
            }
        }*/

        object U256Serde : Serde<U256> {
            override fun serialize(input: U256): ByteArray {
                return CompactInteger.Unsigned.encode(input)
            }

            override fun _deserialize(input: ByteArray): Result<Staging<U256>> {
                return CompactInteger.Unsigned.decodeU256(input)
            }
        }

        object U32Serde : Serde<U32> {
            override fun serialize(input: U32): ByteArray {
                return CompactInteger.Unsigned.encode(input)
            }

            override fun _deserialize(input: ByteArray): Result<Staging<U32>> {
                return CompactInteger.Unsigned.decodeU32(input)
            }
        }

        object ByteStringSerde : Serde<ByteArray> {
            override fun serialize(input: ByteArray): ByteArray {
                return IntSerde.serialize(input.size) + input
            }

            override fun _deserialize(input: ByteArray): Result<Staging<ByteArray>> {
                return IntSerde._deserialize(input).map { (size, rest) ->
                    when {
                        size < 0 -> Result.failure(SerdeError.validation("Negative byte string length: $size"))
                        rest.size >= size -> {
                            val value = rest.copyOfRange(0, size)
                            val remaining = rest.copyOfRange(size, rest.size)
                            Result.success(Staging(value, remaining))
                        }
                        else -> Result.failure(SerdeError.incompleteData(size, rest.size))
                    }
                }.getOrElse { Result.failure(it) }
            }
        }

        object Flags {
            const val none: Int = 0
            const val some: Int = 1
            const val left: Int = 0
            const val right: Int = 1

            val noneB: Byte = none.toByte()
            val someB: Byte = some.toByte()
            val leftB: Byte = left.toByte()
            val rightB: Byte = right.toByte()
        }

        class OptionSerde<T>(private val serde: Serde<T?>) : Serde<T?> {
            override fun serialize(input: T?): ByteArray {
                return when (input) {
                    null -> ByteSerde.serialize(Flags.noneB)
                    else -> ByteSerde.serialize(Flags.someB) + serde.serialize(input)
                }
            }

            override fun _deserialize(input: ByteArray): Result<Staging<T?>> {
                return ByteSerde._deserialize(input).map { (flag, rest) ->
                    when (flag) {
                        Flags.noneB -> Result.success(Staging(null, rest))
                        Flags.someB -> serde._deserialize(rest).map { (t, r) -> Staging(t, r) }
                        else -> Result.failure(SerdeError.wrongFormat("Expect 0 or 1 for option flag"))
                    }
                }.getOrElse { Result.failure(it) }
            }
        }

        open class BatchDeserializer<T>(private val deserializer: Deserializer<T>) {

            private fun <C : MutableList<T>> __deserializeSeq(
                rest: ByteArray,
                index: Int,
                length: Int,
                builder: () -> C,
            ): Result<Staging<C>> {
                return if (index == length) {
                    Result.success(Staging(builder(), rest))
                } else {
                    deserializer._deserialize(rest).map { (t, tRest) ->
                        builder().add(t)
                        __deserializeSeq(tRest, index + 1, length, builder)
                    }.getOrElse { Result.failure(it) }
                }
            }

            fun <C : MutableList<T>> _deserializeSeq(
                size: Int,
                input: ByteArray,
                builder: () -> C,
            ): Result<Staging<C>> {
                return __deserializeSeq(input, 0, size, builder)
            }

            private fun _deserializeArray(
                rest: ByteArray,
                index: Int,
                output: Array<T>,
            ): Result<Staging<Array<T>>> {
                return if (index == output.size) {
                    Result.success(Staging(output, rest))
                } else {
                    deserializer._deserialize(rest).map { (t, tRest) ->
                        output[index] = t
                        _deserializeArray(tRest, index + 1, output)
                    }.getOrElse { Result.failure(it) }
                }
            }

            fun _deserializeArray(n: Int, input: ByteArray): Result<Staging<Array<T>>> {
                return when {
                    n < 0 -> Result.failure(SerdeError.validation("Negative array size: $n"))
                    n > input.size -> Result.failure(SerdeError.validation("Malicious array size: $n"))
                    else -> _deserializeArray(input, 0, arrayOfNulls<Any>(n) as Array<T>)
                }
            }

            fun _deserializeAVector(n: Int, input: ByteArray): Result<Staging<AVector<T>>> {
                return _deserializeArray(n, input).map { staging ->
                    Staging(staging.value.toList(), staging.rest)
                }
            }
        }

        fun bytesSerde(bytes: Int): Serde<ByteArray> =
            object : FixedSizeSerde<ByteArray> {
                override val serdeSize: Int = bytes

                override fun serialize(input: ByteArray): ByteArray {
                    require(input.size == serdeSize) { "Input size must match fixed size" }
                    return input
                }

                override fun deserialize(input: ByteArray): Result<ByteArray> {
                    return deserialize0(input) { it }
                }
            }

        fun <T> fixedSizeSerde(size: Int, serde: Serde<T>): Serde<List<T>> {
            require(size >= 0)
            return object : Serde<List<T>> {
                override fun serialize(input: List<T>): ByteArray {
                    return input.flatMap { serde.serialize(it).toList() }.toByteArray()
                }

                override fun _deserialize(input: ByteArray): Result<Staging<List<T>>> {
                    return BatchDeserializer(serde)._deserializeArray(size, input).map { (arr, rest) ->
                        Staging(arr.toList(), rest)
                    }
                }
            }
        }

        class AVectorSerializer<T>(private val serializer: Serializer<T>) : Serializer<AVector<T>> {
            override fun serialize(input: AVector<T>): ByteArray {
                val serializedLength = IntSerde.serialize(input.size)
                val serializedElements = input.map { serializer.serialize(it) }.reduce { acc, bytes -> acc + bytes }
                return serializedLength + serializedElements
            }
        }

        class AVectorDeserializer<T>(private val deserializer: Deserializer<T>) : BatchDeserializer<T>(deserializer),
            Deserializer<AVector<T>> {
            override fun _deserialize(input: ByteArray): Result<Staging<AVector<T>>> {
                return IntSerde._deserialize(input).map { staging ->
                    val (size, rest) = staging
                    _deserializeAVector(size, rest)
                }.getOrElse { Result.failure(it) }
            }
        }

        fun <T> avectorSerde(serde: Serde<T>): Serde<AVector<T>> {
            return object : BatchDeserializer<T>(serde), Serde<AVector<T>> {
                override fun serialize(input: AVector<T>): ByteArray {
                    val serializedLength = IntSerde.serialize(input.size)
                    val serializedElements = input.map { serde.serialize(it) }.reduce { acc, bytes -> acc + bytes }
                    return serializedLength + serializedElements
                }

                override fun _deserialize(input: ByteArray): Result<Staging<AVector<T>>> {
                    return IntSerde._deserialize(input).map { staging ->
                        val (size, rest) = staging
                        _deserializeAVector(size, rest)
                    }.getOrElse { Result.failure(it) }
                }
            }
        }

        fun <C : MutableList<T>, T> dynamicSizeSerde(
            serde: Serde<T>,
            newBuilder: () -> MutableList<T>,
        ): Serde<C> {
            return object : BatchDeserializer<T>(serde), Serde<C> {
                override fun serialize(input: C): ByteArray {
                    val serializedLength = IntSerde.serialize(input.size)
                    val serializedElements = input.map { serde.serialize(it) }.reduce { acc, bytes -> acc + bytes }
                    return serializedLength + serializedElements
                }

                override fun _deserialize(input: ByteArray): Result<Staging<C>> {
                    return IntSerde._deserialize(input).map { staging ->
                        val (size, rest) = staging
                        _deserializeSeq(size, rest, newBuilder) as Result<Staging<C>>
                    }.getOrElse { Result.failure(it) }
                }
            }
        }

        object TimeStampSerde : FixedSizeSerde<TimeStamp> {
            override val serdeSize: Int = TimeStamp.byteLength

            override fun serialize(input: TimeStamp): ByteArray {
                return Bytes.from(input.millis)
            }

            override fun deserialize(input: ByteArray): Result<TimeStamp> {
                return deserialize1(input) {
                    TimeStamp.from(Bytes.toLongUnsafe(input))
                        ?.let { Result.success(it) }
                        ?: Result.failure(SerdeError.validation("Negative timestamp"))
                }
            }
        }
    }
}

interface Serializer<T> {
    fun serialize(input: T): ByteArray
}

interface Deserializer<T> {

    fun _deserialize(input: ByteArray): Result<Staging<T>>

    fun deserialize(input: ByteArray): Result<T> {
        return _deserialize(input).map { (output, rest) ->
            if (rest.isEmpty()) {
                Result.success(output)
            } else {
                Result.failure(SerdeError.redundant(input.size - rest.size, input.size))
            }
        }.getOrElse { Result.failure(it) }
    }

    fun <U> validateGet(
        get: (T) -> U?,
        error: (T) -> String,
    ): Deserializer<U> {
        return object : Deserializer<U> {
            override fun _deserialize(input: ByteArray): Result<Staging<U>> {
                return this@Deserializer._deserialize(input).map { (t, rest) ->
                    val u = get(t)
                    if (u != null) {
                        Result.success(Staging(u, rest))
                    } else {
                        Result.failure(SerdeError.wrongFormat(error(t)))
                    }
                }.getOrElse { Result.failure(it) }
            }
        }
    }
}

interface FixedSizeSerde<T> : Serde<T> {
    val serdeSize: Int

    fun deserialize0(input: ByteArray, f: (ByteArray) -> T): Result<T> {
        return when {
            input.size == serdeSize -> Result.success(f(input))
            input.size > serdeSize -> Result.failure(SerdeError.redundant(serdeSize, input.size))
            else -> Result.failure(SerdeError.incompleteData(serdeSize, input.size))
        }
    }

    fun deserialize1(input: ByteArray, f: (ByteArray) -> Result<T>): Result<T> {
        return when {
            input.size == serdeSize -> f(input)
            input.size > serdeSize -> Result.failure(SerdeError.redundant(serdeSize, input.size))
            else -> Result.failure(SerdeError.incompleteData(serdeSize, input.size))
        }
    }

    override fun _deserialize(input: ByteArray): Result<Staging<T>> {
        return if (input.size >= serdeSize) {
            val init = input.copyOfRange(0, serdeSize)
            val rest = input.copyOfRange(serdeSize, input.size)
            deserialize(init).map { Staging(it, rest) }
        } else {
            Result.failure(SerdeError.incompleteData(serdeSize, input.size))
        }
    }
}