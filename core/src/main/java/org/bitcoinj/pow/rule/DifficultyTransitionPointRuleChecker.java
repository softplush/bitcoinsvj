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

public class DifficultyTransitionPointRuleChecker extends AbstractPowRulesChecker {

    public DifficultyTransitionPointRuleChecker(NetworkParameters networkParameters) {
        super(networkParameters);
    }

    @Override
    public void checkRules(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore,
                           AbstractBlockChain blockChain) throws VerificationException, BlockStoreException {

        Block prevBlock = storedPrev.getHeader();

        Block lastBlockInterval = findLastBlockInterval(prevBlock, blockStore);
        int timeSpan = (int) (prevBlock.getTimeSeconds() - lastBlockInterval.getTimeSeconds());
        BigInteger newTarget = calculateNewTarget(prevBlock, timeSpan);

        networkParameters.verifyDifficulty(newTarget, nextBlock);
    }

    private Block findLastBlockInterval(Block prevBlock, BlockStore blockStore) throws BlockStoreException {
        StoredBlock cursor = blockStore.get(prevBlock.getHash());
        for (int i = 0; i < networkParameters.getInterval() - 1; i++) {
            if (cursor == null) {
                throw new VerificationException("Difficulty transition point but we did " +
                        "not find a way back to the genesis block.");
            }
            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
        }
        return cursor.getHeader();
    }

    private BigInteger calculateNewTarget(Block prevBlock, int timeSpan) {
        int targetTimespan = networkParameters.getTargetTimespan();
        if (timeSpan < targetTimespan / 4) {
            timeSpan = targetTimespan / 4;
        } else if (timeSpan > targetTimespan * 4) {
            timeSpan = targetTimespan * 4;
        }

        return Utils.decodeCompactBits(prevBlock.getDifficultyTarget())
                .multiply(BigInteger.valueOf(timeSpan))
                .divide(BigInteger.valueOf(targetTimespan));
    }

}
