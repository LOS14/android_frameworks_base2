/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.keyguard;

/**
 * The callback used by the keyguard view to tell the {@link KeyguardViewMediator}
 * various things.
 */
public interface ViewMediatorCallback {
    /**
     * Reports user activity and requests that the screen stay on.
     */
    void userActivity();

    /**
     * Report that the keyguard is done.
     *
     * @param targetUserId a user that needs to be the foreground user at the completion.
     */
    void keyguardDone(int targetUserId);

    /**
     * Report that the keyguard is done drawing.
     */
    void keyguardDoneDrawing();

    /**
     * Tell ViewMediator that the current view needs IME input
     * @param needsInput
     */
    void setNeedsInput(boolean needsInput);

    /**
     * Report that the keyguard is dismissible, pending the next keyguardDone call.
     *
     * @param targetUserId a user that needs to be the foreground user at the completion.
     */
    void keyguardDonePending(int targetUserId);

    /**
     * Report when keyguard is actually gone
     */
    void keyguardGone();

    /**
     * Report when the UI is ready for dismissing the whole Keyguard.
     */
    void readyForKeyguardDone();

    /**
     * Reset the keyguard and bouncer.
     */
    void resetKeyguard();

    /**
     * Play the "device trusted" sound.
     */
    void playTrustedSound();

    /**
     * @return true if the screen is on
     */
    boolean isScreenOn();

    /**
     * @return one of the reasons why the bouncer needs to be shown right now and the user can't use
     *         his normal unlock method like fingerprint or trust agents. See
     *         {@link KeyguardSecurityView#PROMPT_REASON_NONE},
     *         {@link KeyguardSecurityView#PROMPT_REASON_RESTART} and
     *         {@link KeyguardSecurityView#PROMPT_REASON_TIMEOUT}.
     */
    int getBouncerPromptReason();

    /**
     * Consumes a message that was enqueued to be displayed on the next time the bouncer shows up.
     * @return Message that should be displayed above the challenge.
     */
    CharSequence consumeCustomMessage();

    /**
     * Sets a message to be consumed the next time the bouncer shows up.
     */
    void setCustomMessage(CharSequence customMessage);

    /**
     * Call when cancel button is pressed in bouncer.
     */
    void onCancelClicked();

    /**
     * Determines if bouncer has swiped down.
     */
    void onBouncerSwipeDown();
}
