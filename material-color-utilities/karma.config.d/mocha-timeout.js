// PlatformConformanceTest builds several hundred schemes; mocha's 2 s default is not enough for the
// development wasm binary.
config.set({
    client: {
        mocha: {
            timeout: 60000,
        },
    },
});
