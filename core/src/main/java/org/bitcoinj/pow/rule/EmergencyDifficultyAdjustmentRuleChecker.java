/*
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
 */

package org.bitcoinj.pow.rule;

import org.bitcoinj.core.*;
import org.bitcoinj.pow.AbstractPowRulesChecker;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import java.math.BigInteger;

public class EmergencyDifficultyAdjustmentRuleChecker extends AbstractPowRulesChecker {

    private static final long TARGET_PRODUCTION_TIME_IN_SECONDS = 12 * 60 * 60; // 12 hours
    private static final int REFERENCE_OF_BLOCKS_PRODUCED_SIZE = 6;

    public EmergencyDifficultyAdjustmentRuleChecker(NetworkParameters networkParameters) {
        super(networkParameters);
    }

    @Override
    public void checkRules(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore,
                           AbstractBlockChain blockChain) throws VerificationException, BlockStoreException {

        try {
            long lastBlocksMPTinSeconds = getMedianProducingTimeInSeconds(REFERENCE_OF_BLOCKS_PRODUCED_SIZE,
                    storedPrev, blockStore);
            checkEDARules(storedPrev, nextBlock, lastBlocksMPTinSeconds);
        } catch (NullPointerException ex) {
            // We don't have enough blocks, yet
        } catch (BlockStoreException ex) {
            // We don't have enough blocks, yet
        }
    }

    private long getMedianProducingTimeInSeconds(int sizeOfBlocks, StoredBlock storedPrev, BlockStore blockStore) throws BlockStoreException {
        StoredBlock cursor = blockStore.get(storedPrev.getHeader().getHash());
        for (int i = 0; i < sizeOfBlocks; i++) {
            if (cursor == null) {
                throw new NullPointerException("Not enough blocks to check difficulty.");
            }
            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
        }
        //Check to see if there are enough blocks before cursor to correctly calculate the median time
        StoredBlock beforeCursor = cursor;
        for (int i = 0; i < 10; i++) {
            beforeCursor = blockStore.get(beforeCursor.getHeader().getPrevBlockHash());
            if(beforeCursor == null)
                throw new NullPointerException("Not enough blocks to check difficulty.");
        }
        return BlockChain.getMedianTimestampOfRecentBlocks(storedPrev, blockStore) -
                BlockChain.getMedianTimestampOfRecentBlocks(cursor, blockStore);
    }

    private void checkEDARules(StoredBlock storedPrev, Block nextBlock, long lastBlocksMPTinSeconds) {
        Block prevBlock = storedPrev.getHeader();
        if (needToReduceTheDifficulty(lastBlocksMPTinSeconds)) {
            BigInteger nPow = calculateReducedDifficulty(prevBlock);
            if (!hasEqualDifficulty(nextBlock.getDifficultyTarget(), nPow)) {
                throwUnexpectedReducedDifficultyException(storedPrev, nextBlock, nPow);
            }
        } else {
            if (!hasEqualDifficulty(prevBlock, nextBlock)) {
                throwUnexpectedDifficultyChangedException(prevBlock, nextBlock, storedPrev);
            }
        }
    }

    private boolean needToReduceTheDifficulty(long lastBlocksMPTinSeconds) {
        return lastBlocksMPTinSeconds >= TARGET_PRODUCTION_TIME_IN_SECONDS;
    }

    private BigInteger calculateReducedDifficulty(Block prevBlock) {
        BigInteger pow = prevBlock.getDifficultyTargetAsInteger();
        // Divide difficulty target by 1/4 (which reduces the difficulty by 20%)
        pow = pow.add(pow.shiftRight(2));

        if (pow.compareTo(networkParameters.getMaxTarget()) > 0) {
            pow = networkParameters.getMaxTarget();
        }
        return pow;
    }

    private void throwUnexpectedReducedDifficultyException(StoredBlock storedPrev, Block nextBlock, BigInteger nPow) {
        throw new VerificationException("Unexpected change in difficulty [6 blocks >12 hours] at height " + storedPrev.getHeight() +
                ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                Utils.encodeCompactBits(nPow));
    }

    private void throwUnexpectedDifficultyChangedException(Block prevBlock, Block nextBlock, StoredBlock storedPrev) {
        throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
                ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                Long.toHexString(prevBlock.getDifficultyTarget()));
    }

}
