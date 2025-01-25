#!/bin/bash

set -e
set -o xtrace

TEMPER_OUT_DIR="$1"
if [ -z "$TEMPER_OUT_DIR" ]; then
    echo 'Pass temper.out directory as first argument'
    exit -1
fi
if [ ! -d "$TEMPER_OUT_DIR/js" ]; then
    echo "No js/ directory inside $TEMPER_OUT_DIR"
    exit -1
fi
shift

export DIR="$(dirname "$0")"
if [ -z "$DIR" ]; then
    export DIR="."
fi
cd "$DIR"
export DIR="$PWD"

if [[ "$*" == *"--nonpm"* ]]
then
    echo "skipping npm install"
else
    cd "$DIR/src/main/resources/assets/"
    for pkg in temper-core std cart-demo; do
        echo packing and installing translated "$pkg"
        PKG_DIR="$TEMPER_OUT_DIR"/js/"$pkg"
        pushd "$PKG_DIR"
        rm -f *.tgz
        npm pack
        popd
        npm install "$PKG_DIR"/*.tgz --install-links --no-save
        mkdir -p "$pkg"
        tar xfz "$PKG_DIR"/*.tgz -C "$pkg" --strip-components=1
    done
fi

cd "$DIR"
exec mvn compile exec:java -Dexec.mainClass="com.example.server.RidiculouslySimpleShoppingCartServer"
