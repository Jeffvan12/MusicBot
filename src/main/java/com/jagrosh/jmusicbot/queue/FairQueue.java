/*
 * Copyright 2016 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.jagrosh.jmusicbot.selectors.Selector;

/**
 * @param <T>
 * @author John Grosh (jagrosh)
 */
public class FairQueue<T extends Queueable> {
    // Just hope that no-one actually has these ids.
    public static final long REPEAT_SENTINEL = Long.MIN_VALUE;
    // public static final long NO_USER_SENTINEL = REPEAT_SENTINEL + 1;

    private final QueueIndex<T> QUEUE_INDEX_NOT_FOUND = new QueueIndex<>(null, -1);

    private final Map<Long, UserQueue<T>> userQueues = new HashMap<>();

    private final List<T> repeatList;

    public FairQueue() {
        UserQueue<T> repeatQueue = new UserQueue<>(REPEAT_SENTINEL, Long.MAX_VALUE);
        repeatList = repeatQueue.list;
        userQueues.put(REPEAT_SENTINEL, repeatQueue);
    }

    public int add(T item) {
        List<T> list = getOrCreateQueue(item.getUserIdentifier()).list;
        list.add(item);
        return globalIndex(item.getUserIdentifier(), list.size() - 1);
    }

    public int addAt(int index, T item) {
        QueueIndex<T> listIndex = localIndex(index);
        List<T> list = getOrCreateQueue(item.getUserIdentifier()).list;
        list.add(Math.min(listIndex.index, list.size()), item);
        return globalIndex(item.getUserIdentifier(), listIndex.index);
    }

    public int addRepeat(T item) {
        repeatList.add(item);
        return repeatList.size() - 1;
    }

    public int size() {
        return userQueues.values().stream().mapToInt(q -> q.list.size()).sum();
    }

    public TrackFrom<T> pull() {
        UserQueue<T> queue = pullNextQueue();
        T track = queue.list.remove(0);

        List<Long> identifiers = new ArrayList<>();
        identifiers.add(queue.identifier);
        for (UserQueue<T> otherQueue : userQueues.values()) {
            if (otherQueue == queue || otherQueue.identifier == REPEAT_SENTINEL) {
                continue;
            }
            if (otherQueue.list.remove(track)) {
                identifiers.add(otherQueue.identifier);
            }
        }

        return new TrackFrom<>(track, identifiers);
    }

    public boolean isEmpty() {
        return userQueues.values().stream().allMatch(q -> q.list.isEmpty());
    }

    public List<T> getList() {
        return foldList(FoldResult.next(new ArrayList<T>()), (accumulator, queueIndex) -> {
            accumulator.add(queueIndex.queue.list.get(queueIndex.index));
            return FoldResult.next(accumulator);
        });
    }

    private <A, R> R foldList(FoldResult<A, R> result, BiFunction<A, QueueIndex<T>, FoldResult<A, R>> function) {
        if (result.done) {
            return result.result;
        }

        List<UserQueue<T>> queues = userQueues.values().stream().collect(Collectors.toList());
        long[] queueTimes = new long[queues.size()];
        int[] queueIndices = new int[queues.size()];
        @SuppressWarnings("unchecked")
        HashMap<String, Integer>[] queueShares = new HashMap[queues.size()];

        int repeatIndex = 0;
        for (int i = 0; i < queues.size(); i++) {
            if (queues.get(i).identifier == REPEAT_SENTINEL) {
                repeatIndex = i;
                break;
            }
        }

        for (int i = 0; i < queues.size(); i++) {
            queueTimes[i] = queues.get(i).effectiveElapsedTime;
        }

        while (true) {
            int minIndex = -1;
            long minTime = 0;
            for (int i = 0; i < queues.size(); i++) {
                if (queueIndices[i] < queues.get(i).list.size() && (minIndex == -1 || queueTimes[i] < minTime)) {
                    minIndex = i;
                    minTime = queueTimes[i];
                }
            }

            if (minIndex == -1) {
                break;
            }

            T track = queues.get(minIndex).list.get(queueIndices[minIndex]);
            if (queueShares[minIndex].getOrDefault(track.getTrackIdentifier(), 0) == 0) {
                result = function.apply(result.accumulator,
                        new QueueIndex<>(queues.get(minIndex), queueIndices[minIndex]));
                if (result.done) {
                    break;
                }
                queueTimes[minIndex] += track.getDuration();
                queueIndices[minIndex]++;
                for (int i = 0; i < queues.size(); i++) {
                    if (i != minIndex && i != repeatIndex) {
                        queueShares[i].compute(track.getTrackIdentifier(),
                                (id, value) -> value == null ? 1 : value + 1);
                    }
                }
            } else {
                queueShares[minIndex].compute(track.getTrackIdentifier(), (id, count) -> count - 1);
            }
        }

        return result.result;
    }

    public List<T> getList(long identifier) {
        return Collections.unmodifiableList(getOrCreateQueue(identifier).list);
    }

    public T get(int index) {
        QueueIndex<T> listIndex = localIndex(index);
        return userQueues.get(listIndex.queue.identifier).list.get(listIndex.index);
    }

    public T remove(int index) {
        QueueIndex<T> listIndex = localIndex(index);
        return userQueues.get(listIndex.queue.identifier).list.remove(listIndex.index);
    }

    public T specificQueueRemove(int index, long identifier) {
        return userQueues.get(identifier).list.remove(index);
    }

    public List<T> specificQueueRemove(List<Integer> indicies, long identifier) {
        List<T> removed = new ArrayList<>();
        indicies.sort(null);

        for (int i = indicies.size() - 1; i >= 0; i--) {
            removed.add(specificQueueRemove(indicies.get(i), identifier));
        }

        return removed;
    }

    public int removeAll(long identifier) {
        List<T> list = getOrCreateQueue(identifier).list;
        int size = list.size();
        list.clear();
        return size;
    }

    public List<T> removeIf(long identifier, Predicate<T> filter) {
        List<T> list = getOrCreateQueue(identifier).list;
        List<T> removed = new ArrayList<>();

        list.removeIf(e -> {
            if (filter.test(e)) {
                removed.add(e);
                return true;
            } else {
                return false;
            }
        });

        return removed;
    }

    public List<T> removeIf(long identifier, Selector<T> selector) {
        List<T> list = getOrCreateQueue(identifier).list;
        List<T> removed = new ArrayList<>();

        int newEnd = 0;
        for (int i = 0; i < list.size(); i++) {
            T item = list.get(i);
            if (selector.test(i, item)) {
                removed.add(item);
            } else {
                list.set(newEnd, item);
                newEnd++;
            }
        }
        for (int i = list.size() - 1; i >= newEnd; i--) {
            list.remove(i);
        }

        return removed;
    }

    public List<T> moveToFrontIf(long identifier, Selector<T> selector) {
        List<T> list = getOrCreateQueue(identifier).list;
        List<T> moved = new ArrayList<>();

        int front = list.size() - 1;
        for (int i = front; i >= 0; i--) {
            T item = list.get(i);
            if (selector.test(i, item)) {
                moved.add(item);
            } else {
                list.set(front, item);
                front--;
            }
        }
        for (int i = 0; i < moved.size() / 2; i++) {
            T temp = moved.get(i);
            moved.set(i, moved.get(moved.size() - i - 1));
            moved.set(moved.size() - i - 1, temp);
        }
        for (int i = 0; i <= front; i++) {
            list.set(i, moved.get(i));
        }

        return moved;
    }

    public void clear() {
        for (UserQueue<T> queue : userQueues.values()) {
            queue.list.clear();
        }
    }

    public int shuffle(long identifier) {
        List<T> list = getOrCreateQueue(identifier).list;

        for (int i = list.size() - 1; i > 0; i--) {
            int otherIndex = (int) (Math.random() * (i + 1));
            T temp = list.get(i);
            list.set(i, list.get(otherIndex));
            list.set(otherIndex, temp);
        }
        return list.size();
    }

    public void skip(int number) {
        for (int i = 0; i < number; i++) {
            pull();
        }
    }

    public int skipAll(long identifier) {
        List<T> list = getOrCreateQueue(identifier).list;
        repeatList.addAll(list);
        int size = list.size();
        list.clear();
        return size;
    }

    /**
     * Move an item to a different position in the list
     *
     * @param from The position of the item
     * @param to   The new position of the item
     * @return the moved item
     */
    public T moveItem(int from, int to) {
        QueueIndex<T> listIndexFrom = localIndex(from);
        QueueIndex<T> listIndexTo = localIndex(to);
        List<T> list = userQueues.get(listIndexFrom.queue.identifier).list;

        T item = list.remove(listIndexFrom.index);
        // TODO Fix this for the timed queue (or just remvoe it entirely, it isn't very
        // useful).
        // Insert it into the same queue that it was taken from, even if it's not quite
        // the right right location.
        list.add(Math.min(listIndexTo.index, list.size() - 1), item);
        return item;
    }

    public void addTime(List<Long> identifiers, long time) {
        time /= identifiers.size();
        for (long identifier : identifiers) {
            if (identifier == REPEAT_SENTINEL) {
                continue;
            }

            UserQueue<T> queue = getOrCreateQueue(identifier);
            queue.elapsedTime += time;
            queue.effectiveElapsedTime += time;
        }
    }

    public long getTime(long identifier) {
        return getOrCreateQueue(identifier).elapsedTime;
    }

    public void setEffectiveDifference(List<Long> identifiers, long timeDifference) {
        timeDifference /= identifiers.size();
        for (long identifier : identifiers) {
            if (identifier == REPEAT_SENTINEL) {
                return;
            }

            UserQueue<T> queue = getOrCreateQueue(identifier);
            queue.effectiveElapsedTime = queue.elapsedTime + timeDifference;
        }
    }

    public List<Long> getUsers() {
        return userQueues.values().stream().sorted(Comparator.comparing(q -> q.elapsedTime))
                .map(q -> (Long) q.identifier).collect(Collectors.toList());
    }

    private UserQueue<T> pullNextQueue() {
        UserQueue<T> minQueue = null;
        for (UserQueue<T> queue : userQueues.values()) {
            if (!queue.list.isEmpty() && (minQueue == null || queue.elapsedTime < minQueue.elapsedTime)) {
                minQueue = queue;
            }
        }
        return minQueue;
    }

    private int globalIndex(long identifier, int index) {
        return foldList(FoldResult.next(0, -1),
                (accumulator, queueIndex) -> queueIndex.queue.identifier == identifier && queueIndex.index == index
                        ? FoldResult.done(accumulator)
                        : FoldResult.next(accumulator + 1, -1));
    }

    private QueueIndex<T> localIndex(int index) {
        return foldList(FoldResult.next(0, QUEUE_INDEX_NOT_FOUND),
                (accumulator, queueIndex) -> accumulator == index ? FoldResult.done(queueIndex)
                        : FoldResult.next(accumulator + 1, QUEUE_INDEX_NOT_FOUND));
    }

    public static class TrackFrom<T> {
        public T track;
        public List<Long> identifiers;

        public TrackFrom(T track, List<Long> identifiers) {
            this.track = track;
            this.identifiers = identifiers;
        }
    }

    private UserQueue<T> getOrCreateQueue(long identifier) {
        return userQueues.computeIfAbsent(identifier, id -> {
            return new UserQueue<>(identifier, userQueues.values().stream().filter(q -> q.identifier != REPEAT_SENTINEL)
                    .mapToLong(q -> q.elapsedTime).min().orElse(0));
        });
    }

    private static class QueueIndex<T> {
        public UserQueue<T> queue;
        public int index;

        public QueueIndex(UserQueue<T> queue, int index) {
            this.queue = queue;
            this.index = index;
        }
    }

    private static class FoldResult<A, R> {
        public final A accumulator;
        public final R result;
        public final boolean done;

        private FoldResult(A accumulator, R result, boolean done) {
            this.accumulator = accumulator;
            this.result = result;
            this.done = done;
        }

        public static <A> FoldResult<A, A> next(A accumulator) {
            return new FoldResult<A, A>(accumulator, accumulator, false);
        }

        public static <A, R> FoldResult<A, R> next(A accumulator, R result) {
            return new FoldResult<A, R>(accumulator, result, false);
        }

        public static <A, R> FoldResult<A, R> done(R result) {
            return new FoldResult<A, R>(null, result, true);
        }
    }

    private static class UserQueue<T> {
        public long identifier;
        public long elapsedTime;
        public long effectiveElapsedTime;
        public List<T> list;

        public UserQueue(long identifier, long elapsedTime) {
            this.identifier = identifier;
            this.elapsedTime = elapsedTime;
            effectiveElapsedTime = elapsedTime;
            list = new ArrayList<>();
        }
    }
}
