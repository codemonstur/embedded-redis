#!/bin/sh

cd /redis/deps || exit
make hiredis jemalloc linenoise lua
cd /redis || exit
make
