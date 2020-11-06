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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.jagrosh.jmusicbot.selectors.Selector;

/**
 * @param <T>
 * @author John Grosh (jagrosh)
 */
public class FairQueue<T extends Queueable> {
    // Just hope that no-one actually has this id.
    public static final long REPEAT_SENTINEL = Long.MIN_VALUE;

    private final Map<Long, UserQueue<T>> userQueues = new HashMap<>();

    private final List<T> repeatList;

    public FairQueue() {
        UserQueue<T> repeatQueue = new UserQueue<>(REPEAT_SENTINEL, Long.MAX_VALUE);
        repeatList = repeatQueue.list;
        userQueues.put(REPEAT_SENTINEL, repeatQueue);
    }

    public int add(T item, long playingIdentifier, long uncountedTime) {
        List<T> list = getOrCreateList(item.getIdentifier()).list;
        list.add(item);
        return globalIndex(item.getIdentifier(), list.size() - 1, playingIdentifier, uncountedTime);
    }

    public int addAt(int index, T item, long playingIdentifier, long uncountedTime) {
        ListIndex listIndex = localIndex(index, playingIdentifier, uncountedTime);
        List<T> list = getOrCreateList(item.getIdentifier()).list;
        list.add(Math.min(listIndex.index, list.size()), item);
        return globalIndex(item.getIdentifier(), listIndex.index, playingIdentifier, uncountedTime);
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
        return new TrackFrom<>(queue.list.remove(0), queue.identifier);
    }

    public boolean isEmpty() {
        return userQueues.values().stream().allMatch(q -> q.list.isEmpty());
    }

    public List<T> getList(long playingIdentifier, long uncountedTime) {
        List<T> generalList = new ArrayList<>();

        List<UserQueue<T>> queues = userQueues.values().stream().collect(Collectors.toList());
        long[] queueTimes = new long[queues.size()];
        int[] queueIndices = new int[queues.size()];

        for (int i = 0; i < queues.size(); i++) {
            queueTimes[i] = queues.get(i).elapsedTime;
            if (queues.get(i).identifier == playingIdentifier) {
                queueTimes[i] += uncountedTime;
            }
        }

        while (true) {
            int minIndex = -1;
            long minTime = 0;
            for (int i = 0; i < queues.size(); i++) {
                if (queueIndices[i] < queues.get(i).list.size()
                        && (minIndex == -1 || queues.get(i).elapsedTime < minTime)) {
                    minIndex = i;
                    minTime = queueTimes[i];
                }
            }

            if (minIndex == -1) {
                break;
            }

            T track = queues.get(minIndex).list.get(queueIndices[minIndex]);
            generalList.add(queues.get(minIndex).list.get(queueIndices[minIndex]));
            queueTimes[minIndex] += track.getDuration();
            queueIndices[minIndex]++;
        }

        return generalList;
    }

    public List<T> getList(long identifier) {
        return Collections.unmodifiableList(getOrCreateList(identifier).list);
    }

    public T get(int index, long playingIdentifier, long uncountedTime) {
        ListIndex listIndex = localIndex(index, playingIdentifier, uncountedTime);
        return userQueues.get(listIndex.identifier).list.get(listIndex.index);
    }

    public T remove(int index, long playingIdentifier, long uncountedTime) {
        ListIndex listIndex = localIndex(index, playingIdentifier, uncountedTime);
        return userQueues.get(listIndex.identifier).list.remove(listIndex.index);
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
        List<T> list = getOrCreateList(identifier).list;
        int size = list.size();
        list.clear();
        return size;
    }

    public List<T> removeIf(long identifier, Predicate<T> filter) {
        List<T> list = getOrCreateList(identifier).list;
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
        List<T> list = getOrCreateList(identifier).list;
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
        List<T> list = getOrCreateList(identifier).list;
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
        List<T> list = getOrCreateList(identifier).list;

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
        List<T> list = getOrCreateList(identifier).list;
        repeatList.addAll(list);
        int size = list.size();
        list.clear();
        return size;
    }

    /**
     * Move an item to a different position in the list
     *
     * @param from
     *                 The position of the item
     * @param to
     *                 The new position of the item
     * @return the moved item
     */
    public T moveItem(int from, int to, long playingIdentifier, long uncountedTime) {
        ListIndex listIndexFrom = localIndex(from, playingIdentifier, uncountedTime);
        ListIndex listIndexTo = localIndex(to, playingIdentifier, uncountedTime);
        List<T> list = userQueues.get(listIndexFrom.identifier).list;

        T item = list.remove(listIndexFrom.index);
        // TODO Fix this for the timed queue.
        // Insert it into the same queue that it was taken from, even if it's not quite
        // the right right location.
        list.add(Math.min(listIndexTo.index, list.size() - 1), item);
        return item;
    }

    public void addTime(long identifier, long time) {
        if (identifier == REPEAT_SENTINEL) {
            return;
        }

        getOrCreateList(identifier).elapsedTime += time;
    }

    private UserQueue<T> pullNextQueue() {
        UserQueue<T> minQueue = null;
        for (UserQueue<T> queue : userQueues.values()) {
            if (!queue.list.isEmpty()
                    && (minQueue == null || queue.elapsedTime < minQueue.elapsedTime)) {
                minQueue = queue;
            }
        }
        return minQueue;
    }

    private int globalIndex(long identifier, int index, long playingIdentifier, long uncountedTime) {
        List<UserQueue<T>> queues = userQueues.values().stream().collect(Collectors.toList());
        long[] queueTimes = new long[queues.size()];
        int[] queueIndices = new int[queues.size()];

        for (int i = 0; i < queues.size(); i++) {
            queueTimes[i] = queues.get(i).elapsedTime;
            if (queues.get(i).identifier == playingIdentifier) {
                queueTimes[i] += uncountedTime;
            }
        }

        int counter = 0;
        while (true) {
            int minIndex = -1;
            long minTime = 0;
            for (int i = 0; i < queues.size(); i++) {
                if (queueIndices[i] < queues.get(i).list.size()
                        && (minIndex == -1 || queues.get(i).elapsedTime < minTime)) {
                    minIndex = i;
                    minTime = queueTimes[i];
                }
            }

            if (minIndex == -1) {
                break;
            }

            if (queues.get(minIndex).identifier == identifier && queueIndices[minIndex] == index) {
                return counter;
            }

            T track = queues.get(minIndex).list.get(queueIndices[minIndex]);
            queueTimes[minIndex] += track.getDuration();
            queueIndices[minIndex]++;

            counter++;
        }

        return -1;
    }

    private ListIndex localIndex(int index, long playingIdentifier, long uncountedTime) {
        List<UserQueue<T>> queues = userQueues.values().stream().collect(Collectors.toList());
        long[] queueTimes = new long[queues.size()];
        int[] queueIndices = new int[queues.size()];

        for (int i = 0; i < queues.size(); i++) {
            queueTimes[i] = queues.get(i).elapsedTime;
            if (queues.get(i).identifier == playingIdentifier) {
                queueTimes[i] += uncountedTime;
            }
        }

        int counter = 0;
        while (true) {
            int minIndex = -1;
            long minTime = 0;
            for (int i = 0; i < queues.size(); i++) {
                if (queueIndices[i] < queues.get(i).list.size()
                        && (minIndex == -1 || queues.get(i).elapsedTime < minTime)) {
                    minIndex = i;
                    minTime = queueTimes[i];
                }
            }

            if (minIndex == -1) {
                break;
            }

            if (counter == index) {
                return new ListIndex(queues.get(minIndex).identifier, queueIndices[minIndex]);
            }

            T track = queues.get(minIndex).list.get(queueIndices[minIndex]);
            queueTimes[minIndex] += track.getDuration();
            queueIndices[minIndex]++;

            counter++;
        }

        return new ListIndex(0, -1);
    }

    private UserQueue<T> getOrCreateList(long identifier) {
        return userQueues.computeIfAbsent(identifier, id -> {
            return new UserQueue<>(identifier,
                    userQueues.values().stream().filter(q -> q.identifier != REPEAT_SENTINEL)
                            .mapToLong(q -> q.elapsedTime).min().orElse(0));
        });
    }

    public static class TrackFrom<T> {
        public T track;
        public long identifier;

        public TrackFrom(T track, long identifier) {
            this.track = track;
            this.identifier = identifier;
        }
    }

    private static class ListIndex {
        public long identifier;
        public int index;

        public ListIndex(long identifier, int index) {
            this.identifier = identifier;
            this.index = index;
        }
    }

    private static class UserQueue<T> {
        public long identifier;
        public long elapsedTime;
        public List<T> list;

        public UserQueue(long identifier, long elapsedTime) {
            this.identifier = identifier;
            this.elapsedTime = elapsedTime;
            list = new ArrayList<>();
        }
    }
}
