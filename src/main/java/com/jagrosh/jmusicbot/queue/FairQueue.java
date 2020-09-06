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
    private final Map<Long, List<T>> lists = new HashMap<>();
    private final List<Long> listOrder = new ArrayList<>();

    // TODO Implement this queue.
    private final List<T> repeatList = new ArrayList<>();

    public int add(T item) {
        List<T> list = getList(item.getIdentifier());
        list.add(item);
        return globalIndex(item.getIdentifier(), list.size() - 1);
    }

    private int globalIndex(long identifier, int index) {
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

        boolean needsRepeat = true;
        do {
            if (orderedLists.isEmpty()) {
                return new ListIndex(0, -1);
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

    public void addAt(int index, T item) {
        ListIndex listIndex = localIndex(index);
        List<T> list = getList(item.getIdentifier());
        list.add(Math.max(listIndex.index, list.size()), item);
    }

    public int addRepeat(T item) {
        repeatList.add(item);
        return repeatList.size() - 1;
    }

    public int size() {
        return lists.values().stream().mapToInt(List::size).sum();
    }

    public T pull() {
        Long identifier = listOrder.remove(0);
        if (identifier == null) {
            return null;
        }
        listOrder.add(identifier);

        List<T> list = lists.get(identifier);
        return list.remove(0);
    }

    public boolean isEmpty() {
        return lists.values().stream().allMatch(List::isEmpty);
    }

    public List<T> getList() {
        List<T> generalList = new ArrayList<>();
        List<List<T>> orderedLists = listOrder.stream().map(lists::get).collect(Collectors.toList());
        int size = size();
        int i = 0;

        outer:
        for (int individualIndex = 0; ; individualIndex++) {
            for (List<T> list : orderedLists) {
                if (list.size() <= individualIndex) {
                    continue;
                }

                generalList.add(list.get(individualIndex));
                i++;
                if (i == size) {
                    break outer;
                }
            }
        }

        return generalList;
    }

    public List<T> getList(long identifier) {
        return lists.computeIfAbsent(identifier, id -> {
            listOrder.add(id);
            return new ArrayList<>();
        });
    }

    public T get(int index) {
        ListIndex listIndex = localIndex(index);
        return lists.get(listIndex.identifier).get(listIndex.index);
    }

    public T remove(int index) {
        ListIndex listIndex = localIndex(index);
        return lists.get(listIndex.identifier).remove(listIndex.index);
    }

    public int removeAll(long identifier) {
        List<T> list = getList(identifier);

        int count = list.size();
        list.clear();
        return count;
    }

    public void clear() {
        for (List<T> list : lists.values()) {
            list.clear();
        }
    }

    public int shuffle(long identifier) {
        List<T> list = getList(identifier);
        for (int i = list.size() - 1; i >= 0; i--) {
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
        List<T> list = getList(listIndexFrom.identifier);

        T item = list.remove(listIndexFrom.index);
        // Insert it into the same queue as it was taken from, even if it's not quite
        // right.
        list.add(Math.min(listIndexTo.index, list.size() - 1), item);
        return item;
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
