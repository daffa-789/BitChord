# The Windows media controls (SMTC), built for the desktop.
#
# WinRT's SystemMediaTransportControls is what puts BitChord's play/pause in the OS's own media
# overlay and binds the keyboard's media keys, so the library is Windows-only by nature and never
# configured on any other host.
#
# MSVC builds it with C++/WinRT; MinGW has no C++/WinRT, so it builds smtc_abi.cpp — the same
# exports written against the raw ABI headers — whenever its headers carry the Media namespace.

cmake_minimum_required(VERSION 3.16)

project(bitchord_smtc LANGUAGES CXX)

set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

foreach(suffix "" _DEBUG _RELEASE _RELWITHDEBINFO _MINSIZEREL)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
endforeach()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# The JNI headers come from the JDK running the build rather than from find_package, which on a JDK
# with no JAVA_HOME set reports a stale system install.
if(DEFINED ENV{JAVA_HOME})
    set(BITCHORD_JNI_INCLUDES $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)
else()
    find_package(JNI REQUIRED)
    set(BITCHORD_JNI_INCLUDES ${JNI_INCLUDE_DIRS})
endif()

if(MSVC)
    add_library(bitchord_smtc SHARED ${CMAKE_CURRENT_SOURCE_DIR}/smtc_jni.cpp)
    # C++20 rather than the 17 above: under 17, C++/WinRT reaches for <experimental/coroutine>, and
    # current MSVC refuses to compile that header at all. C++20 gives it the standard <coroutine>.
    set_target_properties(bitchord_smtc PROPERTIES PREFIX "" CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)
    target_include_directories(bitchord_smtc PRIVATE ${BITCHORD_JNI_INCLUDES})
    # The WinRT umbrella import library.
    target_link_libraries(bitchord_smtc PRIVATE windowsapp gdi32)
else()
    include(CheckIncludeFileCXX)
    check_include_file_cxx(systemmediatransportcontrolsinterop.h BITCHORD_HAVE_SMTC_INTEROP)
    # The same guard smtc_abi.cpp defines: MinGW's windows.foundation.h does not compile without it.
    set(CMAKE_REQUIRED_DEFINITIONS -D____FIReference_1_BYTE_INTERFACE_DEFINED__)
    check_include_file_cxx(windows.media.h BITCHORD_HAVE_WINRT_MEDIA)
    unset(CMAKE_REQUIRED_DEFINITIONS)
    if(NOT BITCHORD_HAVE_SMTC_INTEROP OR NOT BITCHORD_HAVE_WINRT_MEDIA)
        message(FATAL_ERROR "this MinGW has no WinRT Media headers — install an MSVC toolchain")
    endif()
    add_library(bitchord_smtc SHARED ${CMAKE_CURRENT_SOURCE_DIR}/smtc_abi.cpp)
    set_target_properties(bitchord_smtc PROPERTIES PREFIX "")
    target_include_directories(bitchord_smtc PRIVATE ${BITCHORD_JNI_INCLUDES})
    target_compile_options(bitchord_smtc PRIVATE -O2)
    target_link_libraries(bitchord_smtc PRIVATE runtimeobject ole32 user32)
    # The app unpacks this DLL into its private runtime directory and loads it from there, where
    # MinGW's own libstdc++/libgcc/winpthread DLLs are not. Linking them statically is what makes the
    # library self-contained; leaving them dynamic makes it import libwinpthread-1.dll and fail to
    # load on a machine without MinGW.
    target_link_options(bitchord_smtc PRIVATE -static -static-libgcc -static-libstdc++)
endif()
