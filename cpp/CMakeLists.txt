cmake_minimum_required(VERSION 2.8)
project(jamspell-jni)

if (MSVC)
    set(CMAKE_CXX_FLAGS "/EHsc /W3")
endif(MSVC)

set(JAVA_INCLUDE "$ENV{JAVA_INCLUDE}")
if (JAVA_INCLUDE STREQUAL "")
    if ("$ENV{JAVA_HOME}" STREQUAL "")
        message(FATAL_ERROR "Either JAVA_INCLUDE or JAVA_HOME must be set as environment variables")
    endif()

    set(JAVA_INCLUDE "$ENV{JAVA_HOME}/include")
endif()

include_directories("${CMAKE_SOURCE_DIR}/../JamSpell/" "${JAVA_INCLUDE}" "${JAVA_INCLUDE}/win32")

add_subdirectory(../JamSpell jamspell)
add_library(jamspell-jni SHARED jamspell-jni.cpp)
target_link_libraries(jamspell-jni jamspell_lib)
