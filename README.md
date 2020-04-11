### Welcome to bitcoinsvj

The bitcoinsvj library is a Java implementation of the Bitcoin (BSV) protocol. This library is a fork of pokkst's bitcoincashj library aimed at supporting the Bitcoin SV eco-system.

- 2GB block support
- Standard BIP44 derivation (m/44'/145'/0' for BSV)
- UTXO management when sending coins using SendRequest.utxos
- BIP47 Reusable Payment Codes support
- Up-to-date hardfork checkpoints

It allows maintaining a wallet and sending/receiving transactions without needing a full blockchain node. It comes with full documentation and some example apps showing how to use it.

### Technologies

* Java 7+ and Gradle 4.4+ for the `core` module
* Java 8+ and Gradle 4.4+ for `tools` and `examples`
* Java 11+ and Gradle 4.10+ for the JavaFX-based `wallettemplate`
* [Gradle](https://gradle.org/) - for building the project
* [Google Protocol Buffers](https://github.com/google/protobuf) - for use with serialization and hardware communications

### Getting started

To get started, it is best to have the latest JDK and Gradle installed. The HEAD of the `master` branch contains the latest development code and various production releases are provided on feature branches.

#### Building from the command line

Official builds are currently using with JDK 8, even though the `core` module is compatible with JDK 7 and later.

To perform a full build (*including* JavaDocs and unit/integration *tests*) use JDK 8+
```
gradle clean build
```
If you are running JDK 11 or later and Gradle 4.10 or later, the build will automatically include the JavaFX-based `wallettemplate` module. The outputs are under the `build` directory.

To perform a full build *without* unit/integration *tests* use:
```
gradle clean assemble
```

#### Building from an IDE

Alternatively, just import the project using your IDE. [IntelliJ](http://www.jetbrains.com/idea/download/) has Gradle integration built-in and has a free Community Edition. Simply use `File | New | Project from Existing Sources` and locate the `build.gradle` in the root of the cloned project source tree.

### Building and Using the Wallet Tool

The **bitcoinj** `tools` subproject includes a command-line Wallet Tool (`wallet-tool`) that can be used to create and manage **bitcoinj**-based wallets (both the HD keychain and SPV blockchain state.) Using `wallet-tool` on Bitcoin's test net is a great way to learn about Bitcoin and **bitcoinj**.

To build an executable shell script that runs the command-line Wallet Tool, use:
```
gradle bitcoinj-tools:installDist
```

You can now run the `wallet-tool` without parameters to get help on its operation:
```
./tools/build/install/wallet-tool/bin/wallet-tool
```

To create a test net wallet file in `~/bitcoinj/bitcoinj-test.wallet`, you would use:
```
mkdir ~/bitcoinj
./tools/build/install/wallet-tool/bin/wallet-tool --net=TEST --wallet=$HOME/bitcoinj/bitcoinj-test.wallet create
```

To sync the newly created wallet in `~/bitcoinj/bitcoinj-test.wallet` with the test net, you would use:
```
./tools/build/install/wallet-tool/bin/wallet-tool --net=TEST --wallet=$HOME/bitcoinj/bitcoinj-test.wallet sync
```

To dump the state of the wallet in `~/bitcoinj/bitcoinj-test.wallet` with the test net, you would use:
```
./tools/build/install/wallet-tool/bin/wallet-tool --net=TEST --wallet=$HOME/bitcoinj/bitcoinj-test.wallet dump
```

Note: These instructions are for macOS/Linux, for Windows use the `tools/build/install/wallet-tool/bin/wallet-tool.bat` batch file with the equivalent Windows command-line commands and options.

### Example applications

These are found in the `examples` module.

### Where next?

Now you are ready to [follow the tutorial](https://bitcoinj.github.io/getting-started).

### Contributing to bitcoinsvj

If you would like to help contribute to bitcoinsvj, feel free to make changes and submit pull requests.

Not a programmer? Not a problem. You can donate Bitcoin to the addresses below:


