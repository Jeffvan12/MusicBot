package com.jagrosh.jmusicbot.selectors;

import java.util.List;

import com.jagrosh.jmusicbot.audio.QueuedTrack;

public class Parser {
    private Tokenizer tokenizer;

    public Parser() {
        tokenizer = new Tokenizer();
    }

    public Selector<QueuedTrack> parse(String expr) throws ParseException {
        List<Token> tokens = tokenizer.tokenize(expr);
        if (tokens.size() == 1 && "all".equals(tokens.get(0).getContentString())) {
            return new Selector.All<QueuedTrack>();
        }
        Selector<QueuedTrack> selector;
        try {
            selector = parseExpr(tokens);
        } catch (IndexOutOfBoundsException e) {
            throw new ParseException();
        }
        if (!tokens.isEmpty()) {
            throw new ParseException();
        }
        return selector;
    }

    private Selector<QueuedTrack> parseExpr(List<Token> tokens) throws ParseException {
        Selector<QueuedTrack> selector = parseTerm(tokens);

        while (!tokens.isEmpty() && tokens.get(0).isSymbol() && (tokens.get(0).getContentString().equals("&")
                || tokens.get(0).getContentString().equals("|") || tokens.get(0).getContentString().equals(","))) {
            Token token = tokens.remove(0);
            switch (token.getContentString()) {
                case "&":
                    selector = new Selector.And<>(selector, parseTerm(tokens));
                    break;
                case "|":
                case ",":
                    selector = new Selector.Or<>(selector, parseTerm(tokens));
                    break;
            }
        }

        return selector;
    }

    private Selector<QueuedTrack> parseTerm(List<Token> tokens) throws ParseException {
        Token token = tokens.remove(0);
        Selector<QueuedTrack> selector;

        if (token.isSymbol()) {
            switch (token.getContentString()) {
                case "(":
                    selector = parseExpr(tokens);
                    token = tokens.remove(0);
                    if (!token.isSymbol() || !token.getContentString().equals(")")) {
                        throw new ParseException();
                    }
                    break;
                case "!":
                    selector = new Selector.Not<>(parseTerm(tokens));
                    break;
                case "-":
                    token = tokens.remove(0);
                    if (!token.isNumber()) {
                        throw new ParseException();
                    }
                    selector = new Selector.IndexRange<>(Integer.MIN_VALUE, token.getContentInt() - 1);
                    break;
                default:
                    throw new ParseException();
            }
        } else if (token.isNumber()) {
            if (!tokens.isEmpty() && tokens.get(0).isSymbol() && tokens.get(0).getContentString().equals("-")) {
                tokens.remove(0);
                if (tokens.isEmpty() || !tokens.get(0).isNumber()) {
                    selector = new Selector.IndexRange<>(token.getContentInt() - 1, Integer.MAX_VALUE);
                } else {
                    Token otherToken = tokens.remove(0);
                    selector = new Selector.IndexRange<>(token.getContentInt() - 1, otherToken.getContentInt() - 1);
                }
            } else {
                selector = new Selector.IndexRange<>(token.getContentInt() - 1, token.getContentInt() - 1);
            }
        } else {
            selector = new Selector.Search(token.getContentString());
        }

        return selector;
    }
}
