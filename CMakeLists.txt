cmake_minimum_required(VERSION 3.16)
project(SpellStorm VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

option(SPELLSTORM_BUILD_TESTS "Build the unit/simulation tests" ON)
option(SPELLSTORM_SANITIZE "Enable AddressSanitizer + UndefinedBehaviorSanitizer" OFF)

find_package(SFML 2.5 COMPONENTS graphics window system REQUIRED)

# Every source except main.cpp goes into a static library so that the
# game executable and the tests share exactly the same code.
file(GLOB_RECURSE SPELLSTORM_SOURCES CONFIGURE_DEPENDS
    ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp)
list(REMOVE_ITEM SPELLSTORM_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/src/main.cpp)

add_library(spellstorm_core STATIC ${SPELLSTORM_SOURCES})
target_include_directories(spellstorm_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(spellstorm_core PUBLIC sfml-graphics sfml-window sfml-system)
target_compile_definitions(spellstorm_core PUBLIC
    SPELLSTORM_ASSET_DIR="${CMAKE_CURRENT_SOURCE_DIR}/assets")

if(MSVC)
    target_compile_options(spellstorm_core PUBLIC /W4)
else()
    target_compile_options(spellstorm_core PUBLIC -Wall -Wextra -Wpedantic)
endif()

if(SPELLSTORM_SANITIZE AND NOT MSVC)
    target_compile_options(spellstorm_core PUBLIC -fsanitize=address,undefined -fno-omit-frame-pointer)
    target_link_options(spellstorm_core PUBLIC -fsanitize=address,undefined)
endif()

add_executable(SpellStorm src/main.cpp)
target_link_libraries(SpellStorm PRIVATE spellstorm_core)

# Copy assets next to the executable so the build folder is self-contained.
add_custom_command(TARGET SpellStorm POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
            ${CMAKE_CURRENT_SOURCE_DIR}/assets $<TARGET_FILE_DIR:SpellStorm>/assets)

if(SPELLSTORM_BUILD_TESTS)
    enable_testing()
    add_executable(spellstorm_tests
        tests/test_main.cpp
        tests/test_combat.cpp
        tests/test_spells.cpp
        tests/test_systems.cpp
        tests/test_simulation.cpp
        tests/test_progression.cpp
        tests/test_game_over.cpp
        tests/test_shooting_mode.cpp
        tests/test_assets.cpp)
    target_link_libraries(spellstorm_tests PRIVATE spellstorm_core)
    add_custom_command(TARGET spellstorm_tests POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_directory
                ${CMAKE_CURRENT_SOURCE_DIR}/assets $<TARGET_FILE_DIR:spellstorm_tests>/assets)
    add_test(NAME spellstorm_tests COMMAND spellstorm_tests)
endif()
