#!/bin/bash

set -e
set -o xtrace

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
        REL_DIR=../../../../../../temper.out/js/"$pkg"
        pushd "$REL_DIR"
        rm -f *.tgz
        npm pack
        popd
        npm install "$REL_DIR"/*.tgz --install-links --no-save
        mkdir -p "$pkg"
        tar xfz "$REL_DIR"/*.tgz -C "$pkg" --strip-components=1
    done
fi

cd "$DIR"
exec mvn compile exec:java -Dexec.mainClass="com.example.server.RidiculouslySimpleShoppingCartServer"
