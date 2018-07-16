# JamSpell Java Binding
A Java binding for [JamSpell](https://github.com/bakwc/JamSpell) (a modern spellchecking library), powered by [Swig](http://www.swig.org/).
This project contains both the JNI C++ implementation and a wrapper Java library for ease of use.

## Portability of models
As of release 0.0.11, JamSpell models [are not cross-platform](https://github.com/bakwc/JamSpell/issues/40).
So while this library will run on any platform for which JamSpell's native library is compiled, models are tied to the native library that generated them.

## Native libraries
Before any of the classes in this library are used, the native JNI + JamSpell libraries are loaded into the JVM.
Native libraries are located by reading the file `/com/davipb/jamspell/<os>/<arch>/<bit>/jamspell-jni-name` at runtime, where `<os>` is the Operational System, `<arch>` the processor architecture, and `<bit>` the processor bitness.
This file should contain the resource path (relative or absolute) of the library to be loaded, which will copied to disk and loaded.

To add support for a new platform with no built-in support by the library, compile the CMake project located at the `cpp` for your target platform, place it in the resource classpath, and add an appropriate `jamspell-jni-name` at the resource path specified above.

To stop jamspell-java from automatically loading native libraries, create a resource at `/com/davipb/jamspell/override`.
If the library detects this file (no matter its contents), all native library loading will be skipped.

For more details on the process, see the javadoc for the `com.davipb.jamspell.JamSpellMemoryManager#initialize()` method.

### Built-in platform support
Currently, the following platforms have built-in support and won't need a custom compiled JNI library:
* Windows x86 32-bit
* Windows x86 64-bit
* Linux x86 64-bit

## Generating & building
To update the Swig-generated files, run Swig at the `jamspell-jni.i` file located at the root of the project.
The native C++ JNI binding is built with CMake. It is linked statically to JamSpell to produce a single shared library to be loaded into the JVM.
The wrapper Java project is built with Gradle, and it contains supported libraries as resources.
