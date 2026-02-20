#!/bin/sh
set -e
# В контейнере нужны свои node_modules под Linux (lightningcss и др. нативные модули).
# Иначе с хоста (Windows) подтянутся не те бинарники.
npm ci
exec npm run dev
