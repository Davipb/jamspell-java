package com.davipb.jamspell;

/** Represents an error that has occurred during a JamSpell operation. */
public class JamSpellException extends RuntimeException {
    public JamSpellException() { }
    public JamSpellException(String message) { super(message); }
    public JamSpellException(String message, Throwable cause) { super(message, cause); }
    public JamSpellException(Throwable cause) { super(cause); }
}
