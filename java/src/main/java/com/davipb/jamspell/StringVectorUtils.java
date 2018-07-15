package com.davipb.jamspell;

import com.davipb.jamspell.jni.StringVector;
import lombok.NonNull;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * Internal utility class that contains static methods to transform native {@link StringVector StringVectors}
 * into managed Java lists and vice-versa.
 * <p>
 * Although it would be possible to wrap the native StringVector in a Java-conforming list,
 * every operation in that list would have to call back to native code, which would slow it
 * down more than a single-time conversion does.
 */
final class StringVectorUtils {
    private StringVectorUtils() { throw new AssertionError(); }

    /**
     * Transforms a managed java {@link Iterable} into a native {@link StringVector}.
     * The resulting StringVector is <strong>not memory-managed</strong> by {@link JamSpellMemoryManager},
     * which means it must be {@link StringVector#delete() deleted manually}.
     *
     * @param items The strings to be added to the StringVector.
     * @return A non-memory-managed StringVector with all strings in the specified parameter.
     */
    static @NotNull StringVector toNative(@NonNull Iterable<@NotNull String> items) {
        val result = new StringVector();
        StreamSupport.stream(items.spliterator(), false)
            .map(Objects::requireNonNull)
            .forEach(result::add);

        return result;
    }

    /**
     * Transforms a native {@link StringVector} into a managed java {@link List}.
     * This method does not delete the StringVector.
     *
     * @param vector The StringVector to be transformed.
     * @return A List containing all the strings in the StringVector.
     */
    static @NotNull List<@NotNull String> fromNative(@NonNull StringVector vector) {
        val result = new ArrayList<String>();

        for (int i = 0; i < vector.size(); i++) {
            result.add(Objects.requireNonNull(vector.get(i)));
        }

        return result;
    }
}
