package com.davipb.jamspell;

import lombok.NonNull;
import lombok.val;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

/** Internal class responsible for cleaning up native objects. */
final class JamSpellMemoryManager {

    /** Represents a reference to a native object. */
    private final static class NativeReference extends PhantomReference<Object> {
        /** The native pointer where the object is allocated. */
        private final long pointer;
        /** The function responsible for deleting the native object. */
        private final LongConsumer destructor;

        /**
         * Creates a new Native Reference
         *
         * @param referent   The Java object representing the native object being referenced.
         * @param pointer    The native pointer where the object is allocated.
         * @param destructor The function responsible for deleting the native object.
         */
        NativeReference(@NonNull Object referent, long pointer, @NonNull LongConsumer destructor) {
            super(referent, REFERENCE_QUEUE);
            this.pointer = pointer;
            this.destructor = destructor;
        }

        /** Deletes the native object referenced by this reference object. */
        void delete() { destructor.accept(pointer); }
    }

    /** The queue where all native references end up for cleanup. */
    private static final ReferenceQueue<Object> REFERENCE_QUEUE = new ReferenceQueue<>();
    /** The base resource folder where libraries will be searched */
    private static final String LIBRARY_BASE = "com/davipb/jamspell";
    /** Weather this memory manager has been initialized or not */
    private static boolean initialized = false;

    static {
        initialize();

        val thread = new Thread(JamSpellMemoryManager::clean, "jamspell-cleanup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    /**
     * Method responsible for gathering native references from {@link #REFERENCE_QUEUE the queue} and
     * cleaning them up. This method never returns, and should be called into a new thread.
     */
    private static void clean() {
        try {
            while (true) {
                val reference = (NativeReference) REFERENCE_QUEUE.remove();
                reference.delete();
                reference.clear();
            }
        } catch (InterruptedException ignored) { }
    }

    /**
     * Starts managing a native object, ensuring that it will be deleted when no longer in use.
     *
     * @param referent   The Java object representing the native object being referenced.
     * @param pointer    The native pointer where the object is allocated.
     * @param destructor The function responsible for deleting the native object.
     */
    static void manage(@NonNull Object referent, long pointer, @NonNull LongConsumer destructor) {
        if (pointer == 0) return;
        new NativeReference(referent, pointer, destructor);
    }

    /**
     * Loads the appropriate native library files into the JVM.
     * <p>
     * If a resource is found at the path {@code /com/davipb/jamspell/override}, regardless of its contents,
     * the loading of libraries is aborted immediately.
     * <p>
     * Otherwise, a resource is looked up at {@code /com/davipb/jamspell/<platform>/jamspell-jni-name}, where
     * {@code <platform>} is a platform-specific path specified by {@link PlatformUtils#PLATFORM_PATH}
     * <p>
     * If that resource is found, its contents are read and interpreted as the resource path to the native library
     * that will be loaded. If the path starts with {@code /}, it is interpreted as an absolute path, starting at the
     * classpath root. Otherwise, it is interpreted as a relative path starting at the same path as the
     * {@code jamspell-jni-name} resource.
     * <p>
     * Once located, the native library is copied to a temporary location on disk and loaded into the JVM.
     *
     * @throws JamSpellException If a {@code jamspell-jni-name} resource contains an invalid path or if the system is
     *                           unable to copy the native library to disk.
     * @implNote If the current platform doesn't have a {@code jamspell-jni-name}, the library loading is
     * aborted without an error. This is to allow for external loading of native libraries by users of this class without
     * the need to override existing native libraries with an {@code override} file.
     * @see PlatformUtils#PLATFORM_PATH
     */
    static synchronized void initialize() throws JamSpellException {
        if (initialized) return;
        initialized = true;

        val loader = JamSpellMemoryManager.class.getClassLoader();

        val override = loader.getResource(LIBRARY_BASE + "/override");
        if (override != null) return;

        val pathBase = LIBRARY_BASE + "/" + PlatformUtils.PLATFORM_PATH;
        val descriptorPath = String.join("/", pathBase, "jamspell-jni-name");

        val descriptor = loader.getResourceAsStream(descriptorPath);
        if (descriptor == null) return;

        String libraryPath = new BufferedReader(new InputStreamReader(descriptor, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"))
            .trim();

        if (libraryPath.startsWith("/")) libraryPath = libraryPath.substring(1);
        else libraryPath = pathBase + "/" + libraryPath;

        try (val library = loader.getResourceAsStream(libraryPath)) {
            if (library == null)
                throw new JamSpellException("Library descriptor '" + descriptorPath + "' pointed to a library at '" + libraryPath + "', but it doesn't exist");

            val libraryPathSplit = libraryPath.split("/");
            val libraryName = libraryPathSplit[libraryPathSplit.length - 1];

            val tempFile = File.createTempFile("jamspell-", libraryName);
            tempFile.deleteOnExit();
            Files.copy(library, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.load(tempFile.getAbsolutePath());

        } catch (IOException e) {
            throw new JamSpellException("Unable to extract native library", e);
        }

    }


}
