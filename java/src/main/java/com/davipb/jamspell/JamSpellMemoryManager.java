package com.davipb.jamspell;

import lombok.NonNull;
import lombok.val;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.function.LongConsumer;

/** Internal class responsible for cleaning up native objects. */
final class JamSpellMemoryManager {

    /** Represents a reference to a native object. */
    private final static class NativeReference extends PhantomReference<Object> {
        /** The native pointer where the object is allocated. */
        private final long pointer;
        /** The function responsible for deleting the native object. */
        private final LongConsumer destructor;

        /** Creates a new Native Reference
         * @param referent The Java object representing the native object being referenced.
         * @param pointer The native pointer where the object is allocated.
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

    /** The queue where all native references end up for cleanup.  */
    private static final ReferenceQueue<Object> REFERENCE_QUEUE = new ReferenceQueue<>();

    static {
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
            while(true) {
                val reference = (NativeReference)REFERENCE_QUEUE.remove();
                reference.delete();
                reference.clear();
            }
        } catch (InterruptedException ignored) { }
    }

    /** Starts managing a native object, ensuring that it will be deleted when no longer in use.
     * @param referent The Java object representing the native object being referenced.
     * @param pointer The native pointer where the object is allocated.
     * @param destructor The function responsible for deleting the native object.
     */
    public static void manage(@NonNull Object referent, long pointer, @NonNull LongConsumer destructor) {
        if (pointer == 0) return;
        new NativeReference(referent, pointer, destructor);
    }

}
