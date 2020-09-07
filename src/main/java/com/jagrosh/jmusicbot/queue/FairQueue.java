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
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @param <T>
 * @author John Grosh (jagrosh)
 */
public class FairQueue<T extends Queueable> {
    // Just hope that no-one actually has this id.
    public static final long REPEAT_SENTINEL = Long.MIN_VALUE;

    private final Map<Long, List<T>> lists = new HashMap<>();
    private final List<Long> listOrder = new ArrayList<>();

    private final List<T> repeatList = new ArrayList<>();

    public FairQueue() {
        lists.put(REPEAT_SENTINEL, repeatList);
    }

    public int add(T item) {
        List<T> list = getOrCreateList(item.getIdentifier());
        list.add(item);
        return globalIndex(item.getIdentifier(), list.size() - 1);
    }

    public int addAt(int index, T item) {
        ListIndex listIndex = localIndex(index);
        List<T> list = getOrCreateList(item.getIdentifier());
        list.add(Math.min(listIndex.index, list.size()), item);
        return globalIndex(item.getIdentifier(), listIndex.index);
    }

    public int addRepeat(T item) {
        repeatList.add(item);
        return repeatList.size() - 1;
    }

    public int size() {
        return lists.values().stream().mapToInt(List::size).sum();
    }

    public T pull() {
        return pullNextList().remove(0);
    }

    public boolean isEmpty() {
        return lists.values().stream().allMatch(List::isEmpty);
    }

    public List<T> getList() {
        List<T> generalList = new ArrayList<>();

        List<List<T>> orderedLists = listOrder.stream().map(lists::get).collect(Collectors.toList());
        for (int individualIndex = 0; !orderedLists.isEmpty(); individualIndex++) {
            ListIterator<List<T>> li = orderedLists.listIterator();
            while (li.hasNext()) {
                List<T> list = li.next();
                if (list.size() <= individualIndex) {
                    li.remove();
                    continue;
                }
                generalList.add(list.get(individualIndex));
            }
        }

        // generalList.addAll(repeatList);

        return generalList;
    }

    public List<T> getList(long identifier) {
        return Collections.unmodifiableList(getOrCreateList(identifier));
    }

    public T get(int index) {
        ListIndex listIndex = localIndex(index);
        return lists.get(listIndex.identifier).get(listIndex.index);
    }

    public T remove(int index) {
        ListIndex listIndex = localIndex(index);
        return lists.get(listIndex.identifier).remove(listIndex.index);
    }

    public T specificQueueRemove(int index, long identifier) {
        return lists.get(identifier).remove(index);
    }

    public List<T> specificQueueRemove(List<Integer> indexes, long identifier) {
        List<T> queue = lists.get(identifier);
        List<T> removed = new ArrayList<>();

        for (int i = queue.size() - 1; i >= 0; i++){
            removed.add(specificQueueRemove(i, identifier));
        }

        return removed;
    }

    public int removeAll(long identifier) {
        List<T> list = getOrCreateList(identifier);
        int size = list.size();
        list.clear();
        return size;
    }

    public void clear() {
        for (List<T> list : lists.values()) {
            list.clear();
        }
    }

    public int shuffle(long identifier) {
        List<T> list = getOrCreateList(identifier);

        for (int i = list.size() - 2; i >= 0; i--) {
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

    /**
     * Move an item to a different position in the list
     *
     * @param from The position of the item
     * @param to   The new position of the item
     * @return the moved item
     */
    public T moveItem(int from, int to) {
        ListIndex listIndexFrom = localIndex(from);
        ListIndex listIndexTo = localIndex(to);
        List<T> list = lists.get(listIndexFrom.identifier);

        T item = list.remove(listIndexFrom.index);
        // Insert it into the same queue that it was taken from, even if it's not quite
        // the right right location.
        list.add(Math.min(listIndexTo.index, list.size() - 1), item);
        return item;
    }

    private List<T> pullNextList() {
        ListIterator<Long> li = listOrder.listIterator();
        while (li.hasNext()) {
            long identifier = li.next();
            List<T> list = lists.get(identifier); // List must already exist if `identifier` is in `listOrder`.
            if (!list.isEmpty()) {
                li.remove();
                listOrder.add(identifier);
                return list;
            }
        }
        return repeatList;
    }

    private int globalIndex(long identifier, int index) {
        if (identifier == REPEAT_SENTINEL) {
            return size() - repeatList.size() + index;
        }

        int orderIndex = listOrder.indexOf(identifier);
        if (orderIndex == -1) {
            return -1;
        }

        int before = 0;
        for (int i = 0; i < listOrder.size(); i++) {
            int otherSize = lists.get(listOrder.get(i)).size();
            before += Math.max(otherSize, index);
            if (i < orderIndex) {
                before++;
            }
        }
        return before;
    }

    private ListIndex localIndex(int index) {
        List<List<T>> orderedLists = listOrder.stream().map(lists::get).collect(Collectors.toList());

        int individualIndex;
        int listIndex;

        boolean needsRepeat;
        do {
            if (orderedLists.isEmpty()) {
                return new ListIndex(REPEAT_SENTINEL, index);
            }

            individualIndex = index / orderedLists.size();
            listIndex = index % orderedLists.size();

            ListIterator<List<T>> li = orderedLists.listIterator();
            needsRepeat = false;
            while (li.hasNext()) {
                List<T> list = li.next();
                if (list.size() <= individualIndex) {
                    li.remove();
                    index -= list.size();
                    needsRepeat = true;
                }
            }
        } while (needsRepeat);

        return new ListIndex(listOrder.get(listIndex), individualIndex);
    }

    private List<T> getOrCreateList(long identifier) {
        return lists.computeIfAbsent(identifier, id -> {
            listOrder.add(0, id);
            return new ArrayList<>();
        });
    }

    private static class ListIndex {
        public long identifier;
        public int index;

        public ListIndex(long identifier, int index) {
            this.identifier = identifier;
            this.index = index;
        }
    }
}
