/*
 * Copyright (C) 2008 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.Utils.RingCharBuffer;

import android.util.Log;

public class TextEntryState {
    private static final String TAG = TextEntryState.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int UNKNOWN = 0;
    private static final int START = 1;
    private static final int IN_WORD = 2;
    private static final int ACCEPTED_DEFAULT = 3;
    private static final int PICKED_SUGGESTION = 4;
    private static final int PUNCTUATION_AFTER_ACCEPTED = 5;
    private static final int SPACE_AFTER_ACCEPTED = 6;
    private static final int SPACE_AFTER_PICKED = 7;
    private static final int UNDO_COMMIT = 8;

    private static int sState = UNKNOWN;
    private static int sPreviousState = UNKNOWN;

    private static void setState(final int newState) {
        sPreviousState = sState;
        sState = newState;
    }

    public static void acceptedDefault(CharSequence typedWord, CharSequence actualWord,
            int separatorCode) {
        if (typedWord == null) return;
        setState(ACCEPTED_DEFAULT);
        LatinImeLogger.logOnAutoCorrection(
                typedWord.toString(), actualWord.toString(), separatorCode);
        if (DEBUG)
            displayState("acceptedDefault", "typedWord", typedWord, "actualWord", actualWord);
    }

    // State.ACCEPTED_DEFAULT will be changed to other sub-states
    // (see "case ACCEPTED_DEFAULT" in typedCharacter() below),
    // and should be restored back to State.ACCEPTED_DEFAULT after processing for each sub-state.
    public static void backToAcceptedDefault(CharSequence typedWord) {
        if (typedWord == null) return;
        switch (sState) {
        case SPACE_AFTER_ACCEPTED:
        case PUNCTUATION_AFTER_ACCEPTED:
        case IN_WORD:
            setState(ACCEPTED_DEFAULT);
            break;
        default:
            break;
        }
        if (DEBUG) displayState("backToAcceptedDefault", "typedWord", typedWord);
    }

    public static void acceptedTyped(CharSequence typedWord) {
        setState(PICKED_SUGGESTION);
        if (DEBUG) displayState("acceptedTyped", "typedWord", typedWord);
    }

    public static void acceptedSuggestion(CharSequence typedWord, CharSequence actualWord) {
        setState(PICKED_SUGGESTION);
        if (DEBUG)
            displayState("acceptedSuggestion", "typedWord", typedWord, "actualWord", actualWord);
    }

    public static void typedCharacter(char c, boolean isSeparator, int x, int y) {
        final boolean isSpace = (c == Keyboard.CODE_SPACE);
        switch (sState) {
        case IN_WORD:
            if (isSpace || isSeparator) {
                setState(START);
            } else {
                // State hasn't changed.
            }
            break;
        case ACCEPTED_DEFAULT:
        case SPACE_AFTER_PICKED:
        case PUNCTUATION_AFTER_ACCEPTED:
            if (isSpace) {
                setState(SPACE_AFTER_ACCEPTED);
            } else if (isSeparator) {
                // Swap
                setState(PUNCTUATION_AFTER_ACCEPTED);
            } else {
                setState(IN_WORD);
            }
            break;
        case PICKED_SUGGESTION:
            if (isSpace) {
                setState(SPACE_AFTER_PICKED);
            } else if (isSeparator) {
                // Swap
                setState(PUNCTUATION_AFTER_ACCEPTED);
            } else {
                setState(IN_WORD);
            }
            break;
        case START:
        case UNKNOWN:
        case SPACE_AFTER_ACCEPTED:
            if (!isSpace && !isSeparator) {
                setState(IN_WORD);
            } else {
                setState(START);
            }
            break;
        case UNDO_COMMIT:
            if (isSpace || isSeparator) {
                setState(START);
            } else {
                setState(IN_WORD);
            }
            break;
        }
        RingCharBuffer.getInstance().push(c, x, y);
        if (isSeparator) {
            LatinImeLogger.logOnInputSeparator();
        } else {
            LatinImeLogger.logOnInputChar();
        }
        if (DEBUG) displayState("typedCharacter", "char", c, "isSeparator", isSeparator);
    }
    
    public static void backspace() {
        if (sState == ACCEPTED_DEFAULT) {
            setState(UNDO_COMMIT);
            LatinImeLogger.logOnAutoCorrectionCancelled();
        } else if (sState == UNDO_COMMIT) {
            setState(IN_WORD);
        }
        // TODO: tidy up this logic. At the moment, for example, writing a word goes to
        // ACCEPTED_DEFAULT, backspace will go to UNDO_COMMIT, another backspace will go to IN_WORD,
        // and subsequent backspaces will leave the status at IN_WORD, even if the user backspaces
        // past the end of the word. We are not in a word any more but the state is still IN_WORD.
        if (DEBUG) displayState("backspace");
    }

    public static void restartSuggestionsOnWordBeforeCursor() {
        if (UNKNOWN == sState || ACCEPTED_DEFAULT == sState) {
            // Here we can come from pretty much any state, except the ones that we can't
            // come from after backspace, so supposedly anything except UNKNOWN and
            // ACCEPTED_DEFAULT. Note : we could be in UNDO_COMMIT if
            // LatinIME#revertLastWord() was calling LatinIME#restartSuggestions...()
            Log.e(TAG, "Strange state change : coming from state " + sState);
        }
        setState(IN_WORD);
    }

    public static void reset() {
        setState(START);
        if (DEBUG) displayState("reset");
    }

    public static boolean isUndoCommit() {
        return sState == UNDO_COMMIT;
    }

    public static String getState() {
        return stateName(sState);
    }

    private static String stateName(int state) {
        switch (state) {
        case START: return "START";
        case IN_WORD: return "IN_WORD";
        case ACCEPTED_DEFAULT: return "ACCEPTED_DEFAULT";
        case PICKED_SUGGESTION: return "PICKED_SUGGESTION";
        case PUNCTUATION_AFTER_ACCEPTED: return "PUNCTUATION_AFTER_ACCEPTED";
        case SPACE_AFTER_ACCEPTED: return "SPACE_AFTER_ACCEPTED";
        case SPACE_AFTER_PICKED: return "SPACE_AFTER_PICKED";
        case UNDO_COMMIT: return "UNDO_COMMIT";
        default: return "UNKNOWN";
        }
    }

    private static void displayState(String title, Object ... args) {
        final StringBuilder sb = new StringBuilder(title);
        sb.append(':');
        for (int i = 0; i < args.length; i += 2) {
            sb.append(' ');
            sb.append(args[i]);
            sb.append('=');
            sb.append(args[i+1].toString());
        }
        sb.append(" state=");
        sb.append(stateName(sState));
        sb.append(" previous=");
        sb.append(stateName(sPreviousState));
        Log.d(TAG, sb.toString());
    }
}
