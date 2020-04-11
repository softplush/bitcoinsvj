/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
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
 */

package org.bitcoinj.params;

import static com.google.common.base.Preconditions.checkState;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

/**
 * Parameters for Bitcoin-like networks.
 */
public abstract class AbstractBitcoinNetParams extends NetworkParameters {

    /**
     * Scheme part for Bitcoin URIs.
     */
    public static final String BITCOIN_SCHEME = "bitcoincash";
    public static final int REWARD_HALVING_INTERVAL = 210000;

    /**
     * The number that is one greater than the largest representable SHA-256
     * hash.
     */
    private static BigInteger LARGEST_HASH = BigInteger.ONE.shiftLeft(256);

    private static final Logger log = LoggerFactory.getLogger(AbstractBitcoinNetParams.class);

    public AbstractBitcoinNetParams() {
        super();
    }

    /**
     * Checks if we are at a reward halving point.
     * @param height The height of the previous stored block
     * @return If this is a reward halving point
     */
    public final boolean isRewardHalvingPoint(final int height) {
        return ((height + 1) % REWARD_HALVING_INTERVAL) == 0;
    }

    /**
     * Checks if we are at a difficulty transition point.
     * @param storedPrev The previous stored block
     * @param parameters The network parameters
     * @return If this is a difficulty transition point
     */
    public static boolean isDifficultyTransitionPoint(StoredBlock storedPrev, NetworkParameters parameters) {
        return ((storedPrev.getHeight() + 1) % parameters.getInterval()) == 0;
    }

    /**
     * determines whether monolith upgrade is activated based on MTP
     * @param storedPrev The previous stored block
     * @param store BlockStore containing at least 11 blocks
     * @param parameters The network parameters
     * @return
     */
    public static boolean isMonolithEnabled(StoredBlock storedPrev, BlockStore store, NetworkParameters parameters) {
        if (storedPrev.getHeight() < 524626) { //current height at time of writing, well below the activation block height
            return false;
        }
        try {
            long mtp = BlockChain.getMedianTimestampOfRecentBlocks(storedPrev, store);
            return isMonolithEnabled(mtp, parameters);
        } catch (BlockStoreException e) {
            throw new RuntimeException("Cannot determine monolith activation without BlockStore");
        }
    }

    /**
     * Compute the a target based on the work done between 2 blocks and the time
     * required to produce that work.
     */
    public static BigInteger ComputeTarget(StoredBlock pindexFirst,
                                           StoredBlock pindexLast) {

        Preconditions.checkState(pindexLast.getHeight() > pindexFirst.getHeight());

        /*
         * From the total work done and the time it took to produce that much work,
         * we can deduce how much work we expect to be produced in the targeted time
         * between blocks.
         */
        BigInteger work = pindexLast.getChainWork().subtract(pindexFirst.getChainWork());
        work = work.multiply(BigInteger.valueOf(TARGET_SPACING));

        // In order to avoid difficulty cliffs, we bound the amplitude of the
        // adjustement we are going to do.
        //assert(pindexLast->nTime > pindexFirst->nTime);
        long nActualTimespan = pindexLast.getHeader().getTimeSeconds() - pindexFirst.getHeader().getTimeSeconds();
        if (nActualTimespan > 288 * TARGET_SPACING) {
            nActualTimespan = 288 * TARGET_SPACING;
        } else if (nActualTimespan < 72 * TARGET_SPACING) {
            nActualTimespan = 72 * TARGET_SPACING;
        }

        work = work.divide(BigInteger.valueOf(nActualTimespan));

        /*
         * We need to compute T = (2^256 / W) - 1 but 2^256 doesn't fit in 256 bits.
         * By expressing 1 as W / W, we get (2^256 - W) / W, and we can compute
         * 2^256 - W as the complement of W.
         */
        return LARGEST_HASH.divide(work).subtract(BigInteger.ONE);//target.add(BigInteger.ONE))
    }

    /**
     * determines whether monolith upgrade is activated based on the given MTP.  Useful for overriding MTP for testing.
     * @param medianTimePast
     * @param parameters The network parameters
     * @return
     */
    public static boolean isMonolithEnabled(long medianTimePast, NetworkParameters parameters) {
        return medianTimePast >= parameters.getMonolithActivationTime();
    }

    @Override
    public Coin getMaxMoney() {
        return MAX_MONEY;
    }

    /** @deprecated use {@link TransactionOutput#getMinNonDustValue()} */
    @Override
    @Deprecated
    public Coin getMinNonDustOutput() {
        return Transaction.MIN_NONDUST_OUTPUT;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return new MonetaryFormat();
    }

    @Override
    public int getProtocolVersionNum(final ProtocolVersion version) {
        return version.getBitcoinProtocolVersion();
    }

    @Override
    public BitcoinSerializer getSerializer(boolean parseRetain) {
        return new BitcoinSerializer(this, parseRetain);
    }

    @Override
    public String getUriScheme() {
        return BITCOIN_SCHEME;
    }

    @Override
    public boolean hasMaxMoney() {
        return true;
    }
}
