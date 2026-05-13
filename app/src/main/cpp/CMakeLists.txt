cmake_minimum_required(VERSION 3.22.1)
project(serialport)

add_library(serialport SHARED serial_port.cpp)

find_library(log-lib log)

target_link_libraries(serialport ${log-lib})
