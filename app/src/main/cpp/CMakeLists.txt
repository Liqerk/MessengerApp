cmake_minimum_required(VERSION 3.22.1)

project(byedpi_native)

# Добавляем все .c файлы из папки byedpi
file(GLOB BYE_DPI_SRC
    ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/*.c
)

# Исключаем win_service.c
list(REMOVE_ITEM BYE_DPI_SRC
    ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/win_service.c
)

# Добавляем остальные .c файлы
set(OTHER_SRC
    ${CMAKE_CURRENT_SOURCE_DIR}/native-lib.c
    ${CMAKE_CURRENT_SOURCE_DIR}/utils.c
)

# Собираем библиотеку
add_library(byedpi SHARED ${BYE_DPI_SRC} ${OTHER_SRC})

# Пути для include
target_include_directories(byedpi PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/byedpi
)

# Флаги
target_compile_options(byedpi PRIVATE
    -std=c99
    -O2
    -Wall
    -Wno-unused
    -Wextra
    -Wno-unused-parameter
    -pedantic
    -D_XOPEN_SOURCE=500
    -D_GNU_SOURCE
)

target_compile_definitions(byedpi PRIVATE ANDROID_APP)

# Линковка
target_link_libraries(byedpi PRIVATE
    android
    log
)