package com.jagrosh.jmusicbot.selectors;

class Token {
    private boolean isSymbol;
    private boolean isNumber;

    private String contentString;
    private int contentInt;

    public Token(boolean isSymbol, String content) {
        this.isSymbol = isSymbol;
        this.contentString = content;
    }

    public Token(int content) {
        this.isNumber = true;
        this.contentInt = content;
    }

    public boolean isSymbol() {
        return isSymbol;
    }

    public boolean isNumber() {
        return isNumber;
    }

    public String getContentString() {
        return contentString;
    }

    public int getContentInt() {
        return contentInt;
    }
}
