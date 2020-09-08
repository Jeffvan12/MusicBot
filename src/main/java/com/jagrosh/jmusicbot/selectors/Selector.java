package com.jagrosh.jmusicbot.selectors;

import com.jagrosh.jmusicbot.audio.QueuedTrack;

@FunctionalInterface
public interface Selector<T> {
    public boolean test(int index, T track);

    public static class Index<T> implements Selector<T> {
        int index;

        public Index(int index) {
            this.index = index;
        }

        @Override
        public boolean test(int index, T track) {
            return index == this.index;
        }
    }

    public static class IndexRange<T> implements Selector<T> {
        int start;
        int end;

        public IndexRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean test(int index, T track) {
            return index >= start && index <= end;
        }
    }

    public static class Search implements Selector<QueuedTrack> {
        String search;

        public Search(String search) {
            this.search = search;
        }

        @Override
        public boolean test(int index, QueuedTrack track) {
            return track.getTrack().getInfo().title.toLowerCase().contains(search);
        }
    }

    public static class All<T> implements Selector<T> {
        @Override
        public boolean test(int index, T track) {
            return true;
        }
    }

    public static class And<T> implements Selector<T> {
        Selector<T> left;
        Selector<T> right;

        public And(Selector<T> left, Selector<T> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean test(int index, T track) {
            return left.test(index, track) && right.test(index, track);
        }
    }

    public static class Or<T> implements Selector<T> {
        Selector<T> left;
        Selector<T> right;

        public Or(Selector<T> left, Selector<T> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean test(int index, T track) {
            return left.test(index, track) || right.test(index, track);
        }
    }

    public static class Not<T> implements Selector<T> {
        Selector<T> expr;

        public Not(Selector<T> expr) {
            this.expr = expr;
        }

        @Override
        public boolean test(int index, T track) {
            return !expr.test(index, track);
        }
    }
}
