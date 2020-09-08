package com.jagrosh.jmusicbot.selectors;

import java.util.LinkedList;
import java.util.List;

class Tokenizer {
    public List<Token> tokenize(String expr) {
        List<Token> tokens = new LinkedList<>();

        int wordStart = -1;
        int numberStart = -1;
        int lastNonWhitespace = -1;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            switch (c) {
                case '(':
                case ')':
                case '~':
                case '!':
                case '&':
                case '|':
                case ',':
                case '-':
                    if (wordStart != -1) {
                        tokens.add(new Token(false, expr.substring(wordStart, lastNonWhitespace + 1)));
                        wordStart = -1;
                    } else if (numberStart != -1) {
                        tokens.add(new Token(Integer.parseInt(expr.substring(numberStart, i))));
                        numberStart = -1;
                    }
                    tokens.add(new Token(true, Character.toString(c)));
                    break;

                default:
                    if (Character.isDigit(c)) {
                        if (wordStart != -1) {
                            tokens.add(new Token(false, expr.substring(wordStart, lastNonWhitespace + 1)));
                            wordStart = -1;
                        }
                        if (numberStart == -1) {
                            numberStart = i;
                        }
                    } else {
                        if (numberStart != -1) {
                            tokens.add(new Token(Integer.parseInt(expr.substring(numberStart, i))));
                            numberStart = -1;
                        }
                        if (!Character.isWhitespace(c)) {
                            if (wordStart == -1) {
                                wordStart = i;
                            }
                            lastNonWhitespace = i;
                        }
                    }
            }
        }

        if (wordStart != -1) {
            tokens.add(new Token(false, expr.substring(wordStart, lastNonWhitespace + 1)));
        }

        return tokens;
    }
}
