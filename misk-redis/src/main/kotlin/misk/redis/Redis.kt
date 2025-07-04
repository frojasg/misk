package misk.redis

import okio.ByteString
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection
import redis.clients.jedis.params.ZAddParams
import redis.clients.jedis.util.JedisClusterCRC16
import java.time.Duration
import java.util.function.Supplier

/**
 * A Redis client.
 *
 * Note: special care must be taken if your Redis is running in cluster mode,
 * as keys **must** belong to the same slot in a single command operation like [lmove].
 * You can control which hash slot a key belongs to a certain degree by making use of
 * `{hashtags}` in the key name.
 *
 * See the [Redis Cluster Spec](https://redis.io/docs/reference/cluster-spec/#hash-tags) for more
 * information.
 */
interface Redis {
  /**
   * Deletes a single key.
   *
   * @param key the key to delete
   * @return false if the key was not deleted, true if the key was deleted
   */
  fun del(key: String): Boolean

  /**
   * Deletes multiple keys.
   *
   * On cluster mode, this might trigger multiple calls to Redis
   *
   * @param keys the keys to delete
   * @return 0 if none of the keys were deleted, otherwise a positive integer
   *         representing the number of keys that were deleted
   */
  fun del(vararg keys: String): Int

  /**
   * Retrieves the values for the given list of keys.
   *
   * On cluster mode, this might trigger multiple calls to Redis
   *
   * @param keys the keys to retrieve
   * @return a list of String in the same order as the specified list of keys.
   * For each key, a value will be returned if a key was found, otherwise null is returned.
   */
  fun mget(vararg keys: String): List<ByteString?>

  /**
   * Sets the key value pairs.
   *
   * On cluster mode, this might trigger multiple calls to Redis
   *
   * @param keyValues the list of keys and values in alternating order.
   */
  // Consider deprecating in favour of a list of pairs?
  fun mset(vararg keyValues: ByteString)

  /**
   * Retrieves the value for the given key as a [ByteString].
   *
   * @param key the key to retrieve
   * @return a [ByteString] if the key was found, null if the key was not found
   */
  operator fun get(key: String): ByteString?

  /**
   * Retrieves the value for the given key as a [ByteString] and deletes the key.
   *
   * @param key the key to retrieve
   * @return a [ByteString] if the key was found, null if the key was not found
   */
  fun getDel(key: String): ByteString?

  /**
   * Delete one or more hash [fields] stored at [key].
   * Specified fields that do not exist are ignored.
   *
   * @return The number of fields that were removed from the hash. If the key does not exist,
   *         it is treated as an empty hash and 0 is returned.
   */
  fun hdel(key: String, vararg fields: String): Long

  /**
   * Retrieves the value for the given key and field as a [ByteString].
   *
   * @param key the key
   * @param field the field
   * @return a [ByteString] if the key/field combination was found, null if not found
   */
  fun hget(key: String, field: String): ByteString?

  /**
   * Retrieves all the fields and associated values for the given key. Returns null if nothing
   * found.
   *
   * @param key the key
   * @return a Map<String, ByteString> of the fields to their associated values
   */
  fun hgetAll(key: String): Map<String, ByteString>?

  /**
   * Returns the number of fields contained in the hash stored at [key].
   */
  fun hlen(key: String): Long

  /**
   * Returns all field names in the hash stored for the given key.
   *
   * @param key the key
   * @return a List<ByteString> of the field names stored for the given key
   */
  fun hkeys(key: String): List<ByteString>

  /**
   * Retrieve the values associated to the specified fields.
   *
   * If some specified fields do not exist, nil values are returned. Non-existing keys are
   * considered like empty hashes.
   *
   * @param key the key
   * @param fields the specific fields to retrieve
   * @return a List<ByteString?> of the values for the specific fields requested,
   * in the same order of the request. Null for missing fields
   */
  fun hmget(key: String, vararg fields: String): List<ByteString?>

  /**
   * Increments the number stored at [field] in the hash stored at [key] by [increment]. If [key]
   * does not exist, a new key holding a hash is created. If [field] does not exist the value is
   * set to 0 before the operation is performed.
   *
   * @param key the key.
   * @param field the field.
   * @return the value at [field] after the increment operation.
   */
  fun hincrBy(key: String, field: String, increment: Long): Long

  /**
   * Randomly selects [count] fields and values from the hash stored at [key].
   *
   * NB: Implementations using Jedis 4 or seeking to emulate Jedis should use [checkHrandFieldCount]
   * to avoid surprising behaviour like retrieving a result map which is smaller than requested by a
   * completely random factor.
   */
  fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>?

  /**
   * Like [hrandFieldWithValues] but only returns the fields of the hash stored at [key].
   */
  fun hrandField(key: String, count: Long): List<String>

  /**
   * Performs a batched iteration of matching keys.
   * If no pattern is provided, all keys will be scanned through.
   *
   * @param cursor The scan cursor. This should first be "0". Then subsequent cursor values will
   *               be taken from the returned ScanResults.
   * @param matchPattern A glob-like match pattern to filter keys by. If this is not provided,
   *                     then all keys will be scanned.
   * @param count A hinted desired batch size to be returned in each ScanResult. Note that this is
   *              just a hint and there are no guarantees on the actual size of each ScanResult.
   * @return A ScanResult containing the next cursor and the current batch of keys. If the
   *         returned cursor is "0", then there are no more keys left in the iteration.
   */
  fun scan(cursor: String, matchPattern: String? = null, count: Int? = null): ScanResult

  /**
   * Sets the [ByteString] value for the given key.
   *
   * @param key the key to set
   * @param value the value to set
   */
  operator fun set(key: String, value: ByteString)

  /**
   * Sets the [ByteString] value for a key with an expiration date.
   *
   * @param key the key to set
   * @param expiryDuration the amount of time before the key expires
   * @param value the value to set
   */
  operator fun set(key: String, expiryDuration: Duration, value: ByteString)

  /**
   * Sets the [ByteString] value for the given key if it does not already exist.
   *
   * @param key the key to set
   * @param value the value to set
   */
  fun setnx(key: String, value: ByteString): Boolean

  /**
   * Sets the [ByteString] value for the given key if it does not already exist.
   *
   * @param key the key to set
   * @param expiryDuration the amount of time before the key expires
   * @param value the value to set
   */
  fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean

  /**
   * Sets the [ByteString] value for the given key and field
   *
   * @param key the key
   * @param field the field
   * @param value the value to set
   * @return The number of fields that were added.
   *         Returns 0 if all fields had their values overwritten.
   */
  fun hset(key: String, field: String, value: ByteString): Long

  /**
   * Sets the [ByteString] values for the given key and fields
   *
   * @param key the key
   * @param hash the map of fields to [ByteString] value
   * @return The number of fields that were added.
   *         Returns 0 if all fields had their values overwritten.
   */
  fun hset(key: String, hash: Map<String, ByteString>): Long

  /**
   * Increments the number stored at key by one. If the key does not exist, it is set to 0 before
   * performing the operation. An error is returned if the key contains a value of the wrong type or
   * contains a string that can not be represented as integer.
   *
   * Note: this is a string operation because Redis does not have a dedicated integer type. The
   * string stored at the key is interpreted as a base-10 64 bit signed integer to execute the
   * operation.
   *
   * Redis stores integers in their integer representation, so for string values that actually hold
   * an integer, there is no overhead for storing the string representation of the integer.
   */
  fun incr(key: String): Long

  /**
   * Increments the number stored at key by increment. If the key does not exist, it is set to 0
   * before performing the operation. An error is returned if the key contains a value of the wrong
   * type or contains a string that can not be represented as integer.
   *
   * See [incr] for extra information.
   */
  fun incrBy(key: String, increment: Long): Long

  /**
   * [blmove] is the blocking variant of [lmove]. When source contains elements, this command
   * behaves exactly like [lmove]. When used inside a MULTI/EXEC block, this command behaves exactly
   * like [lmove]. When source is empty, Redis will block the connection until another client pushes
   * to it or until timeout (a double value specifying the maximum number of seconds to block) is
   * reached. A timeout of zero can be used to block indefinitely.
   *
   * This command comes in place of the now deprecated [brpoplpush]. Doing BLMOVE RIGHT LEFT is
   * equivalent.
   *
   * Throws an error if using Redis Cluster and source and destination are not in the same hash slot
   *
   * See [lmove] for more information.
   */
  fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString?

  /**
   * [brpoplpush] is the blocking variant of [rpoplpush]. When source contains elements, this
   * command behaves exactly like [rpoplpush]. When used inside a MULTI/EXEC block, this command
   * behaves exactly like [rpoplpush]. When source is empty, Redis will block the connection until
   * another client pushes to it or until timeout is reached. A timeout of zero can be used to block
   * indefinitely.
   *
   * Throws an error if using Redis Cluster and source and destination are not in the same hash slot
   *
   * See [rpoplpush] for more information.
   *
   * As of Redis version 6.2.0, this command is regarded as deprecated.
   *
   * It can be replaced by [blmove] with the RIGHT and LEFT arguments when migrating or writing new
   * code.
   */
  fun brpoplpush(sourceKey: String, destinationKey: String, timeoutSeconds: Int): ByteString?

  /**
   * Atomically returns and removes the first/last element (head/tail depending on the [from]
   * argument) of the list stored at source, and pushes the element at the first/last element
   * (head/tail depending on the [to] argument) of the list stored at destination.
   *
   * For example: consider source holding the list a,b,c, and destination holding the list x,y,z.
   * Executing LMOVE source destination RIGHT LEFT results in source holding a,b and destination
   * holding c,x,y,z.
   *
   * If source does not exist, the value nil is returned and no operation is performed. If source
   * and destination are the same, the operation is equivalent to removing the first/last element
   * from the list and pushing it as first/last element of the list, so it can be considered as a
   * list rotation command (or a no-op if [from] is the same as [to]).
   *
   * Throws an error if using Redis Cluster and source and destination are not in the same hash slot
   *
   * This command comes in place of the now deprecated RPOPLPUSH. Doing LMOVE RIGHT LEFT is
   * equivalent.
   */
  fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString?

  /**
   * Insert all the specified [elements] at the head of the list stored at [key].
   * If [key] does not exist, it is created as empty list before performing the push operations.
   * When [key] holds a value that is not a list, an error is returned.
   *
   * It is possible to push multiple elements using a single command call just specifying multiple
   * arguments at the end of the command. Elements are inserted one after the other to the head of
   * the list, from the leftmost element to the rightmost element.
   * So for instance the command `LPUSH mylist a b c` will result into a list containing `c` as
   * first element, `b` as second element and `a` as third element.
   */
  fun lpush(key: String, vararg elements: ByteString): Long

  /**
   * Insert all the specified [elements] at the tail of the list stored at [key].
   * If [key] does not exist, it is created as empty list before performing the push operations.
   * When [key] holds a value that is not a list, an error is returned.
   *
   * It is possible to push multiple elements using a single command call just specifying multiple
   * arguments at the end of the command. Elements are inserted one after the other to the tail of
   * the list, from the leftmost element to the rightmost element.
   * So for instance the command `RPUSH mylist a b c` will result into a list containing `a` as
   * first element, `b` as second element and `c` as third element.
   */
  fun rpush(key: String, vararg elements: ByteString): Long

  /**
   * Removes and returns the first [count] elements of the list stored at [key].
   *
   * Only available on Redis 6.2.0 and higher.
   * Throws if Redis is too low of a version.
   */
  fun lpop(key: String, count: Int): List<ByteString?>

  /**
   * Removes and returns the first element of the list stored at [key].
   */
  fun lpop(key: String): ByteString?

  /**
   * Removes and returns the last [count] elements of the list stored at [key].
   *
   * Only available on Redis 6.2.0 and higher.
   * Throws if Redis is too low of a version.
   */
  fun rpop(key: String, count: Int): List<ByteString?>

  /**
   * Removes and returns the last element of the list stored at [key].
   */
  fun rpop(key: String): ByteString?

  /**
   * Returns the specified elements of the list stored at key. The offsets start and stop are
   * zero-based indexes, with 0 being the first element of the list (the head of the list), 1 being
   * the next element and so on.
   *
   * These offsets can also be negative numbers indicating offsets starting at the end of the list.
   * For example, -1 is the last element of the list, -2 the penultimate, and so on.
   */
  fun lrange(key: String, start: Long, stop: Long): List<ByteString?>

  /**
   * Trim the list at [key] to the specified range of elements.
   *
   * The offset [start] and [stop] are zero-based indexes. These offsets can also be
   * negative numbers indicating offsets starting at the end of the list.
   */
  fun ltrim(key: String, start: Long, stop: Long)

  /**
   * Removes the first count occurrences of elements equal to element from the list stored at key.
   * The count argument influences the operation in the following ways:
   *  count > 0: Remove elements equal to element moving from head to tail.
   *  count < 0: Remove elements equal to element moving from tail to head.
   *  count = 0: Remove all elements equal to element.
   * For example, LREM list -2 "hello" will remove the last two occurrences of "hello" in the list
   * stored at list.
   *
   * Note that non-existing keys are treated like empty lists, so when key does not exist, the
   * command will always return 0.
   */
  fun lrem(key: String, count: Long, element: ByteString): Long

  /**
   * Atomically returns and removes the last element (tail) of the list stored at source, and pushes
   * the element at the first element (head) of the list stored at destination.
   *
   * For example: consider source holding the list a,b,c, and destination holding the list x,y,z.
   * Executing [rpoplpush] results in source holding a,b and destination holding c,x,y,z.
   *
   * If source does not exist, the value nil is returned and no operation is performed. If source
   * and destination are the same, the operation is equivalent to removing the last element from the
   * list and pushing it as first element of the list, so it can be considered as a list rotation
   * command.
   *
   * Throws an error if using Redis Cluster and source and destination are not in the same hash slot
   *
   * As of Redis version 6.2.0, this command is regarded as deprecated.
   *
   * It can be replaced by [lmove] with the RIGHT and LEFT arguments when migrating or writing new
   * code.
   */
  fun rpoplpush(sourceKey: String, destinationKey: String): ByteString?

  /**
   * Remove the existing timeout on key, turning the key from volatile (a key with an expire set)
   * to persistent (a key that will never expire as no timeout is associated).
   *
   * @return true if the timeout has been removed. false if the key does not exist or does not have
   * an associated timeout.
   */
  fun persist(key: String): Boolean

  /**
   * Set a timeout on key. After the timeout has expired, the key will automatically be deleted. A
   * key with an associated timeout is often said to be volatile in Redis terminology.
   *
   * The timeout will only be cleared by commands that delete or overwrite the contents of the key,
   * including [del], [set], GETSET and all the *STORE commands. This means that all the operations
   * that conceptually alter the value stored at the key without replacing it with a new one will
   * leave the timeout untouched. For instance, incrementing the value of a key with [incr], pushing
   * a new value into a list with LPUSH, or altering the field value of a hash with [hset] are all
   * operations that will leave the timeout untouched.
   *
   * The timeout can also be cleared, turning the key back into a persistent key, using the PERSIST
   * command.
   *
   * If a key is renamed with RENAME, the associated time to live is transferred to the new key
   * name.
   *
   * If a key is overwritten by RENAME, like in the case of an existing key Key_A that is
   * overwritten by a call like RENAME Key_B Key_A, it does not matter if the original Key_A had a
   * timeout associated or not, the new key Key_A will inherit all the characteristics of Key_B.
   *
   * Note that calling [expire]/[pExpire] with a non-positive timeout or [expireAt]/[pExpireAt] with
   * a time in the past will result in the key being deleted rather than expired (accordingly, the
   * emitted key event will be del, not expired).
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun expire(key: String, seconds: Long): Boolean

  /**
   * [expireAt] has the same effect and semantic as [expire], but instead of specifying the number
   * of seconds representing the TTL (time to live), it takes an absolute Unix timestamp (seconds
   * since January 1, 1970). A timestamp in the past will delete the key immediately.
   *
   * Please for the specific semantics of the command refer to the documentation of [expire].
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun expireAt(key: String, timestampSeconds: Long): Boolean

  /**
   * This command works exactly like [expire] but the time to live of the key is specified in
   * milliseconds instead of seconds.
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun pExpire(key: String, milliseconds: Long): Boolean

  /**
   * [pExpireAt] has the same effect and semantic as [expireAt], but the Unix time at which the key
   * will expire is specified in milliseconds instead of seconds.
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean

  /**
   * Marks the given keys to be watched for conditional execution of a transaction.
   */
  fun watch(vararg keys: String)

  /**
   * Flushes all the previously watched keys for a transaction.
   * If you call EXEC or DISCARD, there's no need to manually call UNWATCH.
   */
  fun unwatch(vararg keys: String)

  /**
   * Marks the start of a transaction block. Subsequent commands will be queued for atomic execution
   * using EXEC.
   */
  fun multi(): Transaction

  /**
   * Begin a pipeline operation to batch together several updates for optimal performance
   */
  @Deprecated("Use pipelining instead.")
  fun pipelined(): Pipeline

  /**
   * Runs a block of Redis commands in a pipeline, for better performance.
   * Pipelined command responses are not returned until the block completes.
   * If you need to use the results of each command immediately, either save the [Supplier]s and call
   * them later, or use non-pipelined operations.
   *
   * See [Redis pipelining](https://redis.io/docs/manual/pipelining/) for more information.
   */
  fun pipelining(block: DeferredRedis.() -> Unit)

  /**
   * Closes the client, so it may not be used further.
   */
  fun close()

  /**
   * Subscribe to a redis channel via pubsub. This is blocking!
   */
  fun subscribe(jedisPubSub: JedisPubSub, channel: String)

  /**
   * Publish a message to a channel.
   */
  fun publish(channel: String, message: String)

  /**
   * Flushes all keys from all databases.
   */
  fun flushAll()

  /**
   * Adds the specified [member] with the specified [score] to the sorted set at the [key].
   * If a specified [member] is already a member of the sorted set, the [score] is updated and the
   * element reinserted at the right position to ensure the correct ordering.
   *
   * If [key] does not exist, a new sorted set with the specified [member] as sole member is
   * created, like if the sorted set was empty. If the [key] exists but does not hold a sorted set,
   * an error is returned.
   *
   * ZADD supports a list of [options], specified after the name of the key and before the first
   * score argument. The complete list of options can be found in [ZAddOptions].
   */
  fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: ZAddOptions
  ): Long

  /**
   * Adds all the specified members with the specified scores in [scoreMembers] to the sorted set
   * at the [key]. If a specified [member] is already a member of the sorted set, the [score] is
   * updated and the element reinserted at the right position to ensure the correct ordering.
   *
   * If [key] does not exist, a new sorted set with the specified [member] as sole member is
   * created, like if the sorted set was empty. If the [key] exists but does not hold a sorted set,
   * an error is returned.
   *
   * ZADD supports a list of [options], specified after the name of the key and before the first
   * score argument. The complete list of options can be found in [ZAddOptions]
   */
  fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: ZAddOptions
  ): Long

  /**
   * Returns the score of [member] in the sorted set at [key].
   *
   * If [member] does not exist in the sorted set, or [key] does not exist, nil is returned.
   */
  fun zscore(
    key: String,
    member: String
  ): Double?

  /**
   * Returns the specified range of elements in the sorted set stored at [key].
   *
   * ZRANGE can perform different [type]s of range queries: by index (rank), by the score, or by
   * lexicographical order. Currently only index and score type range queries are supported.
   * See [ZRangeType] for different types of range queries.
   *
   * You can specify the [start] and [stop] of the range you want to filter by.
   * Depending on the [type] you will have to use the appropriate type of [ZRangeMarker].
   *
   * The order of elements is from the lowest to the highest score.
   * Elements with the same score are ordered lexicographically.
   *
   * Setting [reverse] reverses the ordering, so elements are ordered from highest to lowest score,
   * and score ties are resolved by reverse lexicographical ordering.
   *
   * The [limit] argument can be used to obtain a sub-range from the matching elements.
   * See [ZRangeLimit] for more info.
   */
  fun zrange(
    key: String,
    type: ZRangeType = ZRangeType.INDEX,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean = false,
    limit: ZRangeLimit? = null,
  ): List<ByteString?>

  /**
   * This is similar to [zrange] but returns the scores along with the members.
   */
  fun zrangeWithScores(
    key: String,
    type: ZRangeType = ZRangeType.INDEX,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean = false,
    limit: ZRangeLimit? = null,
  ): List<Pair<ByteString?, Double>>

  /**
   * Removes all elements in the sorted set stored at [key] with rank between [start] and [stop].
   * Both start and stop are 0 -based indexes with 0 being the element with the lowest score.
   * These indexes can be negative numbers, where they indicate offsets starting at the element
   * with the highest score. For example: -1 is the element with the highest score, -2 the element
   * with the second-highest score and so forth.
   */
  fun zremRangeByRank(
    key: String,
    start: ZRangeRankMarker,
    stop: ZRangeRankMarker,
  ): Long

  /**
   * Returns the length of the list stored at [key].
   *
   * @param key the key of the list
   * @return the length of the list
   */
  fun llen(key: String): Long

  /**
   * Returns the sorted set cardinality (number of elements) of the sorted set stored at [key]
   */
  fun zcard(
    key: String
  ): Long

  /**
   * Different types of range queries.
   */
  enum class ZRangeType {
    /**
     * The <start> and <stop> arguments represent zero-based indexes.
     * These arguments specify an inclusive range.
     *
     * The indexes can also be negative numbers indicating offsets from the end of the sorted set,
     * with -1 being the last element of the sorted set and so on.
     *
     * Out of range indexes do not produce an error.
     * If <start> is greater than either the end index of the sorted set or <stop>,
     * an empty list is returned.
     * If <stop> is greater than the end index of the sorted set, Redis will use the last element
     * of the sorted set.
     *
     * Use [ZRangeIndexMarker] to specify the start and stop for this type.
     */
    INDEX,

    /**
     * returns the range of elements from the sorted set having scores equal or between <start>
     * and <stop>
     *
     * <start> and <stop> can be -inf and +inf, denoting the negative and positive infinities,
     * respectively. This means that you are not required to know the highest or lowest score in the
     * sorted set to get all elements from or up to a certain score.
     *
     * By default, the score intervals specified by <start> and <stop> are closed (inclusive).
     * It is possible to specify an open interval.
     *
     * Use [ZRangeScoreMarker] to specify the start and stop for this type.
     */
    SCORE
  }

  abstract class ZRangeMarker(
    val value: Any,
    val included: Boolean
  )

  data class ScanResult(
    val cursor: String,
    val keys: List<String>
  )

  data class ZRangeRankMarker(
    val longValue: Long
  ) : ZRangeMarker(longValue, true)

  /**
   * To be used when [ZRangeType] is [ZRangeType.INDEX].
   * The [intValue] should be an integer specifying the index (start or stop)
   */
  data class ZRangeIndexMarker(
    val intValue: Int
  ) : ZRangeMarker(intValue, true)

  /**
   * To be used when [ZRangeType] is [ZRangeType.SCORE].
   * The [doubleValue] should be a double specifying the score (start or stop)
   * By default the range is included. Set [isIncluded] to false in order to exclude the start or
   * stop.
   */
  data class ZRangeScoreMarker @JvmOverloads constructor(
    val doubleValue: Double,
    val isIncluded: Boolean = true,
  ) : ZRangeMarker(doubleValue, isIncluded) {
    override fun toString(): String {
      var ans = when (this.doubleValue) {
        Double.MAX_VALUE -> "+inf"
        Double.MIN_VALUE -> "-inf"
        else -> this.doubleValue.toString()
      }

      if (!this.isIncluded) ans = "($ans"
      return ans
    }
  }

  /**
   * The limit argument in [zrange] and [zrangeWithScores] can be used to obtain a sub-range from
   * the matching elements similar to SELECT LIMIT offset, count in SQL.
   * A negative [count] returns all elements from the [offset].
   * Keep in mind that if <offset> is large, the sorted set needs to be traversed for
   * <offset> elements before getting to the elements to return, which can add up to O(N) time
   * complexity.
   */
  data class ZRangeLimit(
    val offset: Int,
    val count: Int
  )

  /**
   * Options for ZADD. Not all options are compatible with one another.
   * See the [ZADD command documentation](https://redis.io/commands/zadd/) for more information.
   *
   * Note: misk-redis does not currently support the INCR option.
   */
  enum class ZAddOptions {
    /**
     * Only update elements that already exist. Don't add new elements.
     */
    XX,

    /**
     * Only add new elements. Don't update already existing elements.
     */
    NX,

    /**
     * Only update existing elements if the new score is less than the current score.
     * This flag doesn't prevent adding new elements.
     */
    LT,

    /**
     * Only update existing elements if the new score is greater than the current score.
     * This flag doesn't prevent adding new elements.
     */
    GT,

    /**
     * Modify the return value from the number of new elements added, to the total number of
     * elements changed (CH is an abbreviation of changed). Changed elements are new elements
     * added and elements already existing for which the score was updated. So elements specified
     * in the command line having the same score as they had in the past are not counted.
     * Note: normally the return value of ZADD only counts the number of new elements added.
     */
    CH,
    ;

    companion object {
      fun getZAddParams(options: Array<out ZAddOptions>): ZAddParams {
        val params = ZAddParams()

        options.forEach {
          when (it) {
            XX -> params.xx()
            NX -> params.nx()
            LT -> params.lt()
            GT -> params.gt()
            CH -> params.ch()
          }
        }

        return params
      }

      /**
       * Checks that options will result in a valid ZADD command, conforming to the following:
       *
       * ```
       * zadd key [NX|XX] [GT|LT] [CH] score member [score member ...]
       * ```
       *
       * See the [ZADD command documentation](https://redis.io/commands/zadd) for more information.
       */
      internal fun verify(options: Array<out ZAddOptions>) {
        // NX and XX are mutually exclusive.
        require(!options.contains(NX) || !options.contains(XX)) {
          "ERR XX and NX options at the same time are not compatible"
        }
        // GT, LT, NX are mutually exclusive.
        require(
          !(options.contains(NX) && options.contains(LT))
            && !(options.contains(NX) && options.contains(GT))
            && !(options.contains(GT) && options.contains(LT))
        ) {
          "ERR GT, LT, and/or NX options at the same time are not compatible"
        }
      }
    }
  }
}

/**
 * Validates [count] is positive and non-zero.
 * This is to avoid unexpected behaviour due to limitations in Jedis:
 * https://github.com/redis/jedis/issues/3017
 *
 * This check can be removed when Jedis v5.x is released with full support for the behaviours
 * for negative counts that are specified by Redis.
 *
 * https://redis.io/commands/hrandfield/#specification-of-the-behavior-when-count-is-passed
 */
inline fun checkHrandFieldCount(count: Long) {
  require(count > -1) {
    "This Redis client does not support negative field counts for HRANDFIELD."
  }
  require(count > 0) {
    "You must request at least 1 field."
  }
}

internal fun getSlotErrorOrNull(op: String, keys: List<ByteArray>): Throwable? {
  val slots = keys.map { JedisClusterCRC16.getSlot(it) }.distinct()
  return if (slots.size == 1) {
    null
  } else {
    RuntimeException(
      """
      |When using clustered Redis, keys used by one $op command must always map to the same slot, but mapped to slots $slots.
      |You can use {hashtags} in your key name to control how Redis hashes keys to slots.
      |For example, keys: `{customer9001}.contacts` and `{customer9001}.payments` will  hash to the same slot.
      |
      |See https://redis.io/topics/cluster-spec#hash-tags for more information.
      |
      """.trimMargin()
    )
  }
}
