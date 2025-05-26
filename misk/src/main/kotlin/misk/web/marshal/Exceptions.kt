/**
 * Exception thrown when the request body cannot be parsed according to the expected schema.
 * This typically indicates a potential breaking change in the API contract between client and server.
 * 
 * Common scenarios that trigger this exception:
 * 1. A required field is missing in the request
 * 2. A field's type has changed (e.g., string to number)
 * 3. An array is provided where a scalar value is expected
 * 4. A scalar value is provided where an array/object is expected
 * 
 * IMPORTANT: If you receive this exception frequently from existing clients, it likely indicates
 * a breaking change was made to the API without proper versioning or client notification.
 * You should investigate recent API changes that might have affected the request schema.
 */
class UnmarshallingDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exception thrown when the request body contains malformed content that cannot be parsed.
 * This typically indicates the client sent an invalid format rather than a schema mismatch.
 * 
 * Common scenarios that trigger this exception:
 * 1. Malformed JSON syntax (missing quotes, unmatched braces, etc.)
 * 2. Invalid escape sequences in strings
 * 3. Unexpected end of input
 * 
 * Unlike [UnmarshallingDataException], this exception usually indicates a client-side error
 * in forming the request rather than an API contract issue.
 */
class UnmarshallingEncodingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
