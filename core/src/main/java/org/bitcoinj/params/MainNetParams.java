/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
 * Copyright 2018 the bitcoinj-cash developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been modified by the bitcoinj-cash developers for the bitcoinj-cash project.
 * The original file was from the bitcoinj project (https://github.com/bitcoinj/bitcoinj).
 */

package org.bitcoinj.params;

        import org.bitcoinj.core.Sha256Hash;
        import org.bitcoinj.core.Utils;

        import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public MainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        segwitAddressHrp = "bc";
        port = 8333;
        packetMagic = 0xe3e1f3e8L;
        bip32HeaderP2PKHpub = 0x0488b21e; // The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderP2PKHpriv = 0x0488ade4; // The 4 byte header that serializes in base58 to "xprv"

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setTime(1231006505L);
        genesisBlock.setNonce(2083236893);
        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"),
                genesisHash);

        checkpoints.put(91722, Sha256Hash.wrap("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, Sha256Hash.wrap("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, Sha256Hash.wrap("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, Sha256Hash.wrap("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(200000, Sha256Hash.wrap("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"));
        checkpoints.put(478559, Sha256Hash.wrap("000000000000000000651ef99cb9fcbe0dadde1d424bd9f15ff20136191a5eec")); //August 1, 2017
        checkpoints.put(504031, Sha256Hash.wrap("0000000000000000011ebf65b60d0a3de80b8175be709d653b4c1a1beeb6ab9c")); //November 13, 2017
        checkpoints.put(530359, Sha256Hash.wrap("0000000000000000011ada8bd08f46074f44a8f155396f43e38acf9501c49103")); //May 15, 2018
        checkpoints.put(556767, Sha256Hash.wrap("0000000000000000004626ff6e3b936941d341c5932ece4357eeccac44e6d56c")); //November 15, 2018
        checkpoints.put(582680, Sha256Hash.wrap("000000000000000001b4b8e36aec7d4f9671a47872cb9a74dc16ca398c7dcc18")); //May 15, 2019
        checkpoints.put(609136, Sha256Hash.wrap("000000000000000000b48bb207faac5ac655c313e41ac909322eaa694f5bc5b1")); //November 15, 2019

        dnsSeeds = new String[] {
                "seed.bitcoinabc.org",
                "seed-abc.bitcoinforks.org",
                "btccash-seeder.bitcoinunlimited.info",
                "seed.bitprim.org",
                "seed.deadalnix.me"
        };
        httpSeeds = null;
        addrSeeds = null;

        // Aug, 1 hard fork
        uahfHeight = 478559;
        // Nov, 13 hard fork
        daaUpdateHeight = 504031;
        cashAddrPrefix = "bitcoincash";
        simpleledgerPrefix = "simpleledger";
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
