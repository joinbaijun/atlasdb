/**
 * Copyright 2017 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.timelock.paxos;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.palantir.paxos.PaxosLearner;
import com.palantir.paxos.PaxosQuorumChecker;
import com.palantir.paxos.PaxosResponse;
import com.palantir.paxos.PaxosValue;

public final class PaxosSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(PaxosTimestampBoundStore.class);
    private static final boolean ONLY_LOG_ON_QUORUM_FAILURE = true;

    private PaxosSynchronizer() {
        // utility
    }

    public static void synchronizeLearner(PaxosLearner learnerToSynchronize,
                                          List<PaxosLearner> paxosLearners) {
        Optional<PaxosValue> mostRecentValue = getMostRecentLearnedValue(paxosLearners);
        if (mostRecentValue.isPresent()) {
            PaxosValue paxosValue = mostRecentValue.get();
            learnerToSynchronize.learn(paxosValue.getRound(), paxosValue);
            if (paxosValue.equals(learnerToSynchronize.getGreatestLearnedValue())) {
                log.info("Started up and found that our value {} is already the most recent.", paxosValue);
            } else {
                log.info("Started up and learned the most recent value: {}.", paxosValue);
            }
        }
    }

    private static Optional<PaxosValue> getMostRecentLearnedValue(List<PaxosLearner> paxosLearners) {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<PaxosValueResponse> responses = PaxosQuorumChecker.collectQuorumResponses(
                ImmutableList.copyOf(paxosLearners),
                learner -> ImmutablePaxosValueResponse.of(learner.getGreatestLearnedValue()),
                paxosLearners.size(),
                executor,
                PaxosQuorumChecker.DEFAULT_REMOTE_REQUESTS_TIMEOUT_IN_SECONDS,
                ONLY_LOG_ON_QUORUM_FAILURE);
        return responses.stream()
                .filter(response -> response.paxosValue() != null)
                .map(PaxosValueResponse::paxosValue)
                .max(Comparator.comparingLong(PaxosValue::getRound));
    }

    @Value.Immutable
    interface PaxosValueResponse extends PaxosResponse {
        default boolean isSuccessful() {
            return true;
        }

        @Value.Parameter
        @Nullable
        PaxosValue paxosValue();
    }
}
