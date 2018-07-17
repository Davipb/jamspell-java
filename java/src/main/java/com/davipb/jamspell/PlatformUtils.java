package com.davipb.jamspell;

import lombok.NonNull;
import lombok.val;
import org.apache.commons.lang3.ArchUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.arch.Processor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** Internal class with strictly static helper methods for platform-dependant behavior. */
class PlatformUtils {
    private PlatformUtils() { throw new AssertionError(); }

    /**
     * A '/'-delimited unique path for the current platform, suitable for loading platform-specific resources
     * from the classpath. The generated path does not start nor end with a '/'.
     * <p>
     * The path consists of {@code <os>/<arch>/<bit>}, where {@code <os>} is an identifier for the current
     * Operating System (Windows, Linux, etc), {@code <arch>} is an identifier for the current processor architecture
     * (x86, Itanium, PowerPC, etc), and {@code <bit>} is an identifier for the current processor bitness
     * (32-bit, 64-bit, etc).
     *
     * @see #OS_IDENTIFIER
     * @see #ARCH_IDENTIFIER
     * @see #BITNESS_IDENTIFIER
     */
    final static String PLATFORM_PATH;

    /**
     * The identifier for the current Operational System. The exact identifiers are:
     * <ul>
     * <li>{@code windows} &mdash; For any Microsoft Windows version.</li>
     * <li>{@code linux} &mdash; For any system using the Linux kernel.</li>
     * <li>{@code mac} &mdash; For any Apple Mac version.</li>
     * <li>{@code freebsd} &mdash; For any FreeBSD version.</li>
     * <li>{@code other} &mdash; For any other non-identified Operational System.</li>
     * </ul>
     */
    final static @NotNull String OS_IDENTIFIER;

    /**
     * The identifier for the current processor architecture. The exact identifiers are:
     * <ul>
     * <li>{@code x86} &mdash; For any x86-compatible processor.</li>
     * <li>{@code ia64} &mdash; For Intel Itanium IA-64 processors.</li>
     * <li>{@code ppc} &mdash; For PowerPC processors.</li>
     * <li>{@code other} &mdash; For any other non-identified processor architecture.</li>
     * </ul>
     */
    final static @NotNull String ARCH_IDENTIFIER;

    /**
     * The identifier for the current processor bitness. The exact identifiers are:
     * <ul>
     * <li>{@code bit32} &mdash; For 32-bit processors.</li>
     * <li>{@code bit64} &mdash; For 64-bit processors.</li>
     * <li>{@code other} &mdash; For any other non-identified processor bitness.</li>
     * </ul>
     */
    final static @NotNull String BITNESS_IDENTIFIER;

    static {

        if (SystemUtils.IS_OS_WINDOWS) OS_IDENTIFIER = "windows";
        else if (SystemUtils.IS_OS_LINUX) OS_IDENTIFIER = "linux";
        else if (SystemUtils.IS_OS_MAC) OS_IDENTIFIER = "mac";
        else if (SystemUtils.IS_OS_FREE_BSD) OS_IDENTIFIER = "freebsd";
        else OS_IDENTIFIER = "other";

        val arch = ArchUtils.getProcessor().getType();
        if (arch == Processor.Type.X86) ARCH_IDENTIFIER = "x86";
        else if (arch == Processor.Type.PPC) ARCH_IDENTIFIER = "ppc";
        else if (arch == Processor.Type.IA_64) ARCH_IDENTIFIER = "ia64";
        else ARCH_IDENTIFIER = "other";

        val bitness = ArchUtils.getProcessor().getArch();
        if (bitness == Processor.Arch.BIT_32) BITNESS_IDENTIFIER = "bit32";
        else if (bitness == Processor.Arch.BIT_64) BITNESS_IDENTIFIER = "bit64";
        else BITNESS_IDENTIFIER = "other";

        PLATFORM_PATH = String.join("/", OS_IDENTIFIER, ARCH_IDENTIFIER, BITNESS_IDENTIFIER);
    }

    /**
     * Generates a platform-specific path from a root and a final file name. The generated path is equivalent
     * to appending {@link #OS_IDENTIFIER} to the root path, then appending the name to that, but using Path-specific
     * methods for abstraction and compatibility.
     *
     * @param root The root directory of the path.
     * @param name The name of the file.
     * @return A platform-specific path.
     */
    static @NotNull Path makePlatformSpecific(@NonNull Path root, @NonNull String name) {
        val leaf = root.getFileSystem().getPath(
            PlatformUtils.OS_IDENTIFIER,
            PlatformUtils.ARCH_IDENTIFIER,
            PlatformUtils.BITNESS_IDENTIFIER,
            name
        );

        return root.resolve(leaf);
    }
}
