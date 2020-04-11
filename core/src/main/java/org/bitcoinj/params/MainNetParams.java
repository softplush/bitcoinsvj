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

        checkpoints.put(11111, Sha256Hash.wrap("0000000069e244f73d78e8fd29ba2fd2ed618bd6fa2ee92559f542fdb26e7c1d"));
        checkpoints.put(33333, Sha256Hash.wrap("000000002dd5588a74784eaa7ab0507a18ad16a236e7b1ce69f00d7ddfb5d0a6"));
        checkpoints.put(74000, Sha256Hash.wrap("0000000000573993a3c9e41ce34471c079dcf5f52a0e824a81e7f953b8661a20"));
        checkpoints.put(91722, Sha256Hash.wrap("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, Sha256Hash.wrap("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, Sha256Hash.wrap("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, Sha256Hash.wrap("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(105000, Sha256Hash.wrap("00000000000291ce28027faea320c8d2b054b2e0fe44a773f3eefb151d6bdc97"));
        checkpoints.put(134444, Sha256Hash.wrap("00000000000005b12ffd4cd315cd34ffd4a594f430ac814c91184a0d42d2b0fe"));
        checkpoints.put(168000, Sha256Hash.wrap("000000000000099e61ea72015e79632f216fe6cb33d7899acb35b75c8303b763"));
        checkpoints.put(193000, Sha256Hash.wrap("000000000000059f452a5f7340de6682a977387c17010ff6e6c3bd83ca8b1317"));
        checkpoints.put(200000, Sha256Hash.wrap("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"));
        checkpoints.put(210000, Sha256Hash.wrap("000000000000048b95347e83192f69cf0366076336c639f9b7228e9ba171342e"));
        checkpoints.put(216116, Sha256Hash.wrap("00000000000001b4f4b433e81ee46494af945cf96014816a4e2370f11b23df4e"));
        checkpoints.put(225430, Sha256Hash.wrap("00000000000001c108384350f74090433e7fcf79a606b8e797f065b130575932"));
        checkpoints.put(250000, Sha256Hash.wrap("000000000000003887df1f29024b06fc2200b55f8af8f35453d7be294df2d214"));
        checkpoints.put(279000, Sha256Hash.wrap("0000000000000001ae8c72a0b0c301f67e3afca10e819efa9041e458e9bd7e40"));
        checkpoints.put(295000, Sha256Hash.wrap("00000000000000004d9b4ef50f0f9d686fd69db2e03af35a100370c64632a983"));
        checkpoints.put(478559, Sha256Hash.wrap("000000000000000000651ef99cb9fcbe0dadde1d424bd9f15ff20136191a5eec"));
        checkpoints.put(557957, Sha256Hash.wrap("0000000000000000020973011c0bb9db0a05fc1cd6807b5da82515bdf97232a8"));

        dnsSeeds = new String[] {
                "seed.bitcoinsv.io",
                "seed.cascharia.com",
                "seed.satoshivision.network"
        };

        httpSeeds = null;
        addrSeeds = null;

        // Aug, 1 hard fork
        uahfHeight = 478559;
        // Nov, 13 hard fork
        daaUpdateHeight = 504031;
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
