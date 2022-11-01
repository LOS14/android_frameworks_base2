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
 * limitations under the License.
 */

package android.app;

import static android.app.ActivityManager.StopUserOnSwitch;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.RestrictionLevel;
import android.app.assist.ActivityId;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ActivityPresentationInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PermissionMethod;
import android.content.pm.PermissionName;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerExemptionManager.ReasonCode;
import android.os.PowerExemptionManager.TempAllowListType;
import android.os.TransactionTooLargeException;
import android.os.WorkSource;
import android.util.ArraySet;
import android.util.Pair;

import com.android.internal.os.TimeoutRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Activity manager local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class ActivityManagerInternal {

    public enum ServiceNotificationPolicy {
        /**
         * The Notification is not associated with any foreground service.
         */
        NOT_FOREGROUND_SERVICE,
        /**
         * The Notification is associated with a foreground service, but the
         * notification system should handle it just like non-FGS notifications.
         */
        SHOW_IMMEDIATELY,
        /**
         * The Notification is associated with a foreground service, and the
         * notification system should ignore it unless it has already been shown (in
         * which case it should be used to update the currently displayed UI).
         */
        UPDATE_ONLY
    }

    // Access modes for handleIncomingUser.
    /**
     * Allows access to a caller with {@link android.Manifest.permission#INTERACT_ACROSS_USERS}.
     */
    public static final int ALLOW_NON_FULL = 0;
    /**
     * Allows access to a caller with {@link android.Manifest.permission#INTERACT_ACROSS_USERS}
     * if in the same profile group.
     * Otherwise, {@link android.Manifest.permission#INTERACT_ACROSS_USERS_FULL} is required.
     */
    public static final int ALLOW_NON_FULL_IN_PROFILE = 1;
    /**
     * Allows access to a caller only if it has the full
     * {@link android.Manifest.permission#INTERACT_ACROSS_USERS_FULL}.
     */
    public static final int ALLOW_FULL_ONLY = 2;
    /**
     * Allows access to a caller with {@link android.Manifest.permission#INTERACT_ACROSS_PROFILES}
     * if in the same profile group.
     * Otherwise, {@link android.Manifest.permission#INTERACT_ACROSS_USERS} is required and suffices
     * as in {@link #ALLOW_NON_FULL}.
     */
    public static final int ALLOW_PROFILES_OR_NON_FULL = 3;

    /**
     * Returns profile information in free form string in two separate strings.
     * See AppProfiler for the output format.
     * The output can only be used for human consumption. The format may change
     * in the future.
     * Do not call it frequently.
     * @param time uptime for the cpu state
     * @param lines lines of the cpu state should be returned
     * @return a pair of Strings. The first is the current cpu load, the second is the cpu state.
     */
    public abstract Pair<String, String> getAppProfileStatsForDebugging(long time, int lines);

    /**
     * Verify that calling app has access to the given provider.
     */
    public abstract String checkContentProviderAccess(String authority, @UserIdInt int userId);

    /**
     * Verify that calling UID has access to the given provider.
     */
    public abstract int checkContentProviderUriPermission(Uri uri, @UserIdInt int userId,
            int callingUid, int modeFlags);

    // Called by the power manager.
    public abstract void onWakefulnessChanged(int wakefulness);

    /**
     * @return {@code true} if process start is successful, {@code false} otherwise.
     */
    public abstract boolean startIsolatedProcess(String entryPoint, String[] mainArgs,
            String processName, String abiOverride, int uid, Runnable crashHandler);

    /**
     * Called when a user has been deleted. This can happen during normal device usage
     * or just at startup, when partially removed users are purged. Any state persisted by the
     * ActivityManager should be purged now.
     *
     * @param userId The user being cleaned up.
     */
    public abstract void onUserRemoved(@UserIdInt int userId);

    /**
     * Kill foreground apps from the specified user.
     */
    public abstract void killForegroundAppsForUser(@UserIdInt int userId);

    /**
     * Sets how long a {@link PendingIntent} can be temporarily allowlisted to bypass restrictions
     * such as Power Save mode.
     * @param target
     * @param allowlistToken
     * @param duration temp allowlist duration in milliseconds.
     * @param type temp allowlist type defined at {@link TempAllowListType}
     * @param reasonCode one of {@link ReasonCode}
     * @param reason A human-readable reason for logging purposes.
     */
    public abstract void setPendingIntentAllowlistDuration(IIntentSender target,
            IBinder allowlistToken, long duration, @TempAllowListType int type,
            @ReasonCode int reasonCode, @Nullable String reason);

    /**
     * Returns the flags set for a {@link PendingIntent}.
     */
    public abstract int getPendingIntentFlags(IIntentSender target);

    /**
     * Allows a {@link PendingIntent} to start activities from background.
     */
    public abstract void setPendingIntentAllowBgActivityStarts(
            IIntentSender target, IBinder allowlistToken, int flags);

    /**
     * Voids {@link PendingIntent}'s privilege to start activities from background.
     */
    public abstract void clearPendingIntentAllowBgActivityStarts(IIntentSender target,
            IBinder allowlistToken);

    /**
     * Allow DeviceIdleController to tell us about what apps are allowlisted.
     */
    public abstract void setDeviceIdleAllowlist(int[] allAppids, int[] exceptIdleAppids);

    /**
     * Update information about which app IDs are on the temp allowlist.
     * @param appids the updated list of appIds in temp allowlist.
     *               If null, it is to update only changingUid.
     * @param changingUid uid to add or remove to temp allowlist.
     * @param adding true to add to temp allowlist, false to remove from temp allowlist.
     * @param durationMs when adding is true, the duration to be in temp allowlist.
     * @param type temp allowlist type defined at {@link TempAllowListType}.
     * @param reasonCode one of {@link ReasonCode}
     * @param reason A human-readable reason for logging purposes.
     * @param callingUid the callingUid that setup this temp allowlist, only valid when param adding
     *                   is true.
     */
    public abstract void updateDeviceIdleTempAllowlist(@Nullable int[] appids, int changingUid,
            boolean adding, long durationMs, @TempAllowListType int type,
            @ReasonCode int reasonCode,
            @Nullable String reason, int callingUid);

    /**
     * Get the procstate for the UID.  The return value will be between
     * {@link ActivityManager#MIN_PROCESS_STATE} and {@link ActivityManager#MAX_PROCESS_STATE}.
     * Note if the UID doesn't exist, it'll return {@link ActivityManager#PROCESS_STATE_NONEXISTENT}
     * (-1).
     */
    public abstract int getUidProcessState(int uid);

    /**
     * Get a map of pid and package name that process of that pid Android/data and Android/obb
     * directory is not mounted to lowerfs.
     */
    public abstract Map<Integer, String> getProcessesWithPendingBindMounts(int userId);

    /**
     * @return {@code true} if system is ready, {@code false} otherwise.
     */
    public abstract boolean isSystemReady();

    /**
     * @return {@code true} if system is using the "modern" broadcast queue,
     *         {@code false} otherwise.
     */
    public abstract boolean isModernQueueEnabled();

    /**
     * Enforce capability restrictions on use of the given BroadcastOptions
     */
    public abstract void enforceBroadcastOptionsPermissions(@Nullable Bundle options,
            int callingUid);

    /**
     * Returns package name given pid.
     *
     * @param pid The pid we are searching package name for.
     */
    @Nullable
    public abstract String getPackageNameByPid(int pid);

    /**
     * Sets if the given pid has an overlay UI or not.
     *
     * @param pid The pid we are setting overlay UI for.
     * @param hasOverlayUi True if the process has overlay UI.
     * @see android.view.WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
     */
    public abstract void setHasOverlayUi(int pid, boolean hasOverlayUi);

    /**
     * Called after the network policy rules are updated by
     * {@link com.android.server.net.NetworkPolicyManagerService} for a specific {@param uid} and
     * {@param procStateSeq}.
     */
    public abstract void notifyNetworkPolicyRulesUpdated(int uid, long procStateSeq);

    /**
     * Inform ActivityManagerService about the latest {@code blockedReasons} for an uid, which
     * can be used to understand whether the {@code uid} is allowed to access network or not.
     */
    public abstract void onUidBlockedReasonsChanged(int uid, int blockedReasons);

    /**
     * @return true if runtime was restarted, false if it's normal boot
     */
    public abstract boolean isRuntimeRestarted();

    /**
     * Returns if more users can be started without stopping currently running users.
     */
    public abstract boolean canStartMoreUsers();

    /**
     * Sets the user switcher message for switching from {@link android.os.UserHandle#SYSTEM}.
     */
    public abstract void setSwitchingFromSystemUserMessage(String switchingFromSystemUserMessage);

    /**
     * Sets the user switcher message for switching to {@link android.os.UserHandle#SYSTEM}.
     */
    public abstract void setSwitchingToSystemUserMessage(String switchingToSystemUserMessage);

    /**
     * Returns maximum number of users that can run simultaneously.
     */
    public abstract int getMaxRunningUsers();

    /**
     * Whether an UID is active or idle.
     */
    public abstract boolean isUidActive(int uid);

    /**
     * Returns a list of running processes along with corresponding uids, pids and their oom score.
     *
     * Only processes managed by ActivityManagerService are included.
     */
    public abstract List<ProcessMemoryState> getMemoryStateForProcesses();

    /**
     * Checks to see if the calling pid is allowed to handle the user. Returns adjusted user id as
     * needed.
     */
    public abstract int handleIncomingUser(int callingPid, int callingUid, @UserIdInt int userId,
            boolean allowAll, int allowMode, String name, String callerPackage);

    /** Checks if the calling binder pid/uid has the given permission. */
    @PermissionMethod
    public abstract void enforceCallingPermission(@PermissionName String permission, String func);

    /** Returns the current user id. */
    public abstract int getCurrentUserId();

    /** Returns the currently started user ids. */
    public abstract int[] getStartedUserIds();

    /** Returns true if the user is running. */
    public abstract boolean isUserRunning(@UserIdInt int userId, int flags);

    /** Trims memory usage in the system by removing/stopping unused application processes. */
    public abstract void trimApplications();

    /** Kill the processes in the list due to their tasks been removed. */
    public abstract void killProcessesForRemovedTask(ArrayList<Object> procsToKill);

    /** Kill the process immediately. */
    public abstract void killProcess(String processName, int uid, String reason);

    /**
     * Returns {@code true} if {@code uid} is running an activity from {@code packageName}.
     */
    public abstract boolean hasRunningActivity(int uid, @Nullable String packageName);

    public abstract void updateOomAdj();
    public abstract void updateCpuStats();

    /**
     * Update battery stats on activity usage.
     * @param activity
     * @param uid
     * @param userId
     * @param started
     */
    public abstract void updateBatteryStats(
            ComponentName activity, int uid, @UserIdInt int userId, boolean resumed);

    /**
     * Update UsageStats of the activity.
     * @param activity
     * @param userId
     * @param event
     * @param appToken ActivityRecord's appToken.
     * @param taskRoot Task's root
     */
    public abstract void updateActivityUsageStats(
            ComponentName activity, @UserIdInt int userId, int event, IBinder appToken,
            ComponentName taskRoot, ActivityId activityId);
    public abstract void updateForegroundTimeIfOnBattery(
            String packageName, int uid, long cpuTimeDiff);
    public abstract void sendForegroundProfileChanged(@UserIdInt int userId);

    /**
     * Returns whether the given user requires credential entry at this time. This is used to
     * intercept activity launches for locked work apps due to work challenge being triggered or
     * when the profile user is yet to be unlocked.
     */
    public abstract boolean shouldConfirmCredentials(@UserIdInt int userId);

    /**
     * Used in conjunction with {@link #noteAlarmStart(PendingIntent, WorkSource, int, String)} to
     * note an alarm duration for battery attribution
     */
    public abstract void noteAlarmFinish(PendingIntent ps, WorkSource workSource, int sourceUid,
            String tag);

    /**
     * Used in conjunction with {@link #noteAlarmFinish(PendingIntent, WorkSource, int, String)} to
     * note an alarm duration for battery attribution
     */
    public abstract void noteAlarmStart(PendingIntent ps, WorkSource workSource, int sourceUid,
            String tag);

    /**
     * Used to note a wakeup alarm for battery attribution.
     */
    public abstract void noteWakeupAlarm(PendingIntent ps, WorkSource workSource, int sourceUid,
            String sourcePkg, String tag);

    /**
     * Returns whether this app is disallowed to run in the background.
     *
     * @see ActivityManager#APP_START_MODE_DISABLED
     */
    public abstract boolean isAppStartModeDisabled(int uid, String packageName);

    /**
     * Returns the ids of the current user and all of its profiles (if any), regardless of the
     * running state of the profiles.
     */
    public abstract int[] getCurrentProfileIds();
    public abstract UserInfo getCurrentUser();
    public abstract void ensureNotSpecialUser(@UserIdInt int userId);
    public abstract boolean isCurrentProfile(@UserIdInt int userId);
    public abstract boolean hasStartedUserState(@UserIdInt int userId);
    public abstract void finishUserSwitch(Object uss);

    /** Schedule the execution of all pending app GCs. */
    public abstract void scheduleAppGcs();

    /** Gets the task id for a given activity. */
    public abstract int getTaskIdForActivity(@NonNull IBinder token, boolean onlyRoot);

    /** Gets the basic info for a given activity. */
    public abstract ActivityPresentationInfo getActivityPresentationInfo(@NonNull IBinder token);

    public abstract void setBooting(boolean booting);
    public abstract boolean isBooting();
    public abstract void setBooted(boolean booted);
    public abstract boolean isBooted();
    public abstract void finishBooting();

    /**
     * Temp allowlist a UID for PendingIntent.
     * @param callerPid the PID that sent the PendingIntent.
     * @param callerUid the UID that sent the PendingIntent.
     * @param targetUid the UID that is been temp allowlisted.
     * @param duration temp allowlist duration in milliseconds.
     * @param type temp allowlist type defined at {@link TempAllowListType}
     * @param reasonCode one of {@link ReasonCode}
     * @param reason
     */
    public abstract void tempAllowlistForPendingIntent(int callerPid, int callerUid, int targetUid,
            long duration, int type, @ReasonCode int reasonCode, String reason);

    public abstract int broadcastIntentInPackage(String packageName, @Nullable String featureId,
            int uid, int realCallingUid, int realCallingPid, Intent intent, String resolvedType,
            IApplicationThread resultToThread, IIntentReceiver resultTo, int resultCode,
            String resultData, Bundle resultExtras, String requiredPermission, Bundle bOptions,
            boolean serialized, boolean sticky, @UserIdInt int userId,
            boolean allowBackgroundActivityStarts, @Nullable IBinder backgroundActivityStartsToken,
            @Nullable int[] broadcastAllowList);

    public abstract ComponentName startServiceInPackage(int uid, Intent service,
            String resolvedType, boolean fgRequired, String callingPackage,
            @Nullable String callingFeatureId, @UserIdInt int userId,
            boolean allowBackgroundActivityStarts,
            @Nullable IBinder backgroundActivityStartsToken) throws TransactionTooLargeException;

    public abstract void disconnectActivityFromServices(Object connectionHolder);
    public abstract void cleanUpServices(@UserIdInt int userId, ComponentName component,
            Intent baseIntent);
    public abstract ActivityInfo getActivityInfoForUser(ActivityInfo aInfo, @UserIdInt int userId);
    public abstract void ensureBootCompleted();
    public abstract void updateOomLevelsForDisplay(int displayId);
    public abstract boolean isActivityStartsLoggingEnabled();
    /** Returns true if the background activity starts is enabled. */
    public abstract boolean isBackgroundActivityStartsEnabled();
    public abstract void reportCurKeyguardUsageEvent(boolean keyguardShowing);

    /** @see com.android.server.am.ActivityManagerService#monitor */
    public abstract void monitor();

    /** Input dispatch timeout to a window, start the ANR process. Return the timeout extension,
     * in milliseconds, or 0 to abort dispatch. */
    public abstract long inputDispatchingTimedOut(int pid, boolean aboveSystem,
            TimeoutRecord timeoutRecord);

    public abstract boolean inputDispatchingTimedOut(Object proc, String activityShortComponentName,
            ApplicationInfo aInfo, String parentShortComponentName, Object parentProc,
            boolean aboveSystem, TimeoutRecord timeoutRecord);

    /**
     * App started responding to input events. This signal can be used to abort the ANR process and
     * hide the ANR dialog.
     */
    public abstract void inputDispatchingResumed(int pid);

    /**
     * User tapped "wait" in the ANR dialog - reschedule the dialog to be shown again at a later
     * time.
     * @param data AppNotRespondingDialog.Data object
     */
    public abstract void rescheduleAnrDialog(Object data);

    /**
     * Sends {@link android.content.Intent#ACTION_CONFIGURATION_CHANGED} with all the appropriate
     * flags.
     */
    public abstract void broadcastGlobalConfigurationChanged(int changes, boolean initLocale);

    /**
     * Sends {@link android.content.Intent#ACTION_CLOSE_SYSTEM_DIALOGS} with all the appropriate
     * flags.
     */
    public abstract void broadcastCloseSystemDialogs(String reason);

    /**
     * Kills all background processes, except those matching any of the specified properties.
     *
     * @param minTargetSdk the target SDK version at or above which to preserve processes,
     *                     or {@code -1} to ignore the target SDK
     * @param maxProcState the process state at or below which to preserve processes,
     *                     or {@code -1} to ignore the process state
     */
    public abstract void killAllBackgroundProcessesExcept(int minTargetSdk, int maxProcState);

    /** Starts a given process. */
    public abstract void startProcess(String processName, ApplicationInfo info,
            boolean knownToBeDead, boolean isTop, String hostingType, ComponentName hostingName);

    /** Starts up the starting activity process for debugging if needed.
     * This function needs to be called synchronously from WindowManager context so the caller
     * passes a lock {@code wmLock} and waits to be notified.
     *
     * @param wmLock calls {@code notify} on the object to wake up the caller.
    */
    public abstract void setDebugFlagsForStartingActivity(ActivityInfo aInfo, int startFlags,
            ProfilerInfo profilerInfo, Object wmLock);

    /** Returns mount mode for process running with given pid */
    public abstract int getStorageMountMode(int pid, int uid);

    /** Returns true if the given uid is the app in the foreground. */
    public abstract boolean isAppForeground(int uid);

    /** Returns true if the given process name and uid is currently marked 'bad' */
    public abstract boolean isAppBad(String processName, int uid);

    /** Remove pending backup for the given userId. */
    public abstract void clearPendingBackup(@UserIdInt int userId);

    /**
     * When power button is very long pressed, call this interface to do some pre-shutdown work
     * like persisting database etc.
     */
    public abstract void prepareForPossibleShutdown();

    /**
     * Returns {@code true} if {@code uid} is running a foreground service of a specific
     * {@code foregroundServiceType}.
     */
    public abstract boolean hasRunningForegroundService(int uid, int foregroundServiceType);

    /**
     * Returns {@code true} if the given notification channel currently has a
     * notification associated with a foreground service.  This is an AMS check
     * because that is the source of truth for the FGS state.
     */
    public abstract boolean hasForegroundServiceNotification(String pkg, @UserIdInt int userId,
            String channelId);

    /**
     * Tell the service lifecycle logic that the given Notification content is now
     * canonical for any foreground-service visibility policy purposes.
     *
     * Returns a description of any FGs-related policy around the given Notification:
     * not associated with an FGS; ensure display; or only update if already displayed.
     */
    public abstract ServiceNotificationPolicy applyForegroundServiceNotification(
            Notification notification, String tag, int id, String pkg, @UserIdInt int userId);

    /**
     * Callback from the notification subsystem that the given FGS notification has
     * been evaluated, and either shown or explicitly overlooked.  This can happen
     * after either Service.startForeground() or NotificationManager.notify().
     */
    public abstract void onForegroundServiceNotificationUpdate(boolean shown,
            Notification notification, int id, String pkg, @UserIdInt int userId);

    /**
     * Fully stop the given app's processes without restoring service starts or
     * bindings, but without the other durable effects of the full-scale
     * "force stop" intervention.
     */
    public abstract void stopAppForUser(String pkg, @UserIdInt int userId);

    /**
     * If the given app has any FGSs whose notifications are in the given channel,
     * stop them.
     */
    public abstract void stopForegroundServicesForChannel(String pkg, @UserIdInt int userId,
            String channelId);

    /**
     * Registers the specified {@code processObserver} to be notified of future changes to
     * process state.
     */
    public abstract void registerProcessObserver(IProcessObserver processObserver);

    /**
     * Unregisters the specified {@code processObserver}.
     */
    public abstract void unregisterProcessObserver(IProcessObserver processObserver);

    /**
     * Gets the uid of the instrumentation source if there is an unfinished instrumentation that
     * targets the given uid.
     *
     * @param uid The uid to be checked for
     *
     * @return the uid of the instrumentation source, if there is an instrumentation whose target
     * application uid matches the given uid, and {@link android.os.Process#INVALID_UID} otherwise.
     */
    public abstract int getInstrumentationSourceUid(int uid);

    /** Is this a device owner app? */
    public abstract boolean isDeviceOwner(int uid);

    /**
     * Called by DevicePolicyManagerService to set the uid of the device owner.
     */
    public abstract void setDeviceOwnerUid(int uid);

    /** Is this a profile owner app? */
    public abstract boolean isProfileOwner(int uid);

    /**
     * Called by DevicePolicyManagerService to set the uid of the profile owner.
     * @param profileOwnerUids The profile owner UIDs. The ownership of the array is
     *                         passed to callee.
     */
    public abstract void setProfileOwnerUid(ArraySet<Integer> profileOwnerUids);

    /**
     * Set all associated companion app that belongs to a userId.
     * @param userId
     * @param companionAppUids  ActivityManager will take ownership of this Set, the caller
     *                          shouldn't touch this Set after calling this interface.
     */
    public abstract void setCompanionAppUids(int userId, Set<Integer> companionAppUids);

    /**
     * is the uid an associated companion app of a userId?
     * @param userId
     * @param uid
     * @return
     */
    public abstract boolean isAssociatedCompanionApp(int userId, int uid);

    /**
     * Sends a broadcast, assuming the caller to be the system and allowing the inclusion of an
     * approved allowlist of app Ids >= {@link android.os.Process#FIRST_APPLICATION_UID} that the
     * broadcast my be sent to; any app Ids < {@link android.os.Process#FIRST_APPLICATION_UID} are
     * automatically allowlisted.
     *
     * @param filterExtrasForReceiver A function to filter intent extras for the given receiver by
     * using the rules of package visibility. Returns extras with legitimate package info that the
     * receiver is able to access, or {@code null} if none of the packages is visible to the
     * receiver.
     *
     * @see com.android.server.am.ActivityManagerService#broadcastIntentWithFeature(
     *      IApplicationThread, String, Intent, String, IIntentReceiver, int, String, Bundle,
     *      String[], int, Bundle, boolean, boolean, int)
     */
    public abstract int broadcastIntent(Intent intent,
            IIntentReceiver resultTo,
            String[] requiredPermissions, boolean serialized,
            int userId, int[] appIdAllowList,
            @Nullable BiFunction<Integer, Bundle, Bundle> filterExtrasForReceiver,
            @Nullable Bundle bOptions);

    /**
     * Add uid to the ActivityManagerService PendingStartActivityUids list.
     * @param uid uid
     * @param pid pid of the ProcessRecord that is pending top.
     */
    public abstract void addPendingTopUid(int uid, int pid, @Nullable IApplicationThread thread);

    /**
     * Delete uid from the ActivityManagerService PendingStartActivityUids list.
     * @param uid uid
     * @param nowElapsed starting time of updateOomAdj
     */
    public abstract void deletePendingTopUid(int uid, long nowElapsed);

    /**
     * Is the uid in ActivityManagerService PendingStartActivityUids list?
     * @param uid
     * @return true if exists, false otherwise.
     */
    public abstract boolean isPendingTopUid(int uid);

    /**
     * @return the intent for the given intent sender.
     */
    @Nullable
    public abstract Intent getIntentForIntentSender(IIntentSender sender);

    /**
     * Effectively PendingIntent.getActivityForUser(), but the PendingIntent is
     * owned by the given uid rather than by the caller (i.e. the system).
     */
    public abstract PendingIntent getPendingIntentActivityAsApp(
            int requestCode, @NonNull Intent intent, int flags, Bundle options,
            String ownerPkgName, int ownerUid);

    /**
     * Effectively PendingIntent.getActivityForUser(), but the PendingIntent is
     * owned by the given uid rather than by the caller (i.e. the system).
     */
    public abstract PendingIntent getPendingIntentActivityAsApp(
            int requestCode, @NonNull Intent[] intents, int flags, Bundle options,
            String ownerPkgName, int ownerUid);

    /**
     * @return mBootTimeTempAllowlistDuration of ActivityManagerConstants.
     */
    public abstract long getBootTimeTempAllowListDuration();

    /** Register an {@link AnrController} to control the ANR dialog behavior */
    public abstract void registerAnrController(AnrController controller);

    /** Unregister an {@link AnrController} */
    public abstract void unregisterAnrController(AnrController controller);

    /**
     * Is the FGS started from an uid temporarily allowed to have while-in-use permission?
     */
    public abstract boolean isTempAllowlistedForFgsWhileInUse(int uid);

    /**
     * Return the temp allowlist type when server push messaging is over the quota.
     */
    public abstract @TempAllowListType int getPushMessagingOverQuotaBehavior();

    /**
     * Return the startForeground() grace period after calling startForegroundService().
     */
    public abstract int getServiceStartForegroundTimeout();

    /**
     * Returns the capability of the given uid
     */
    public abstract @ProcessCapability int getUidCapability(int uid);

    /**
     * @return The PID list of the isolated process with packages matching the given uid.
     */
    @Nullable
    public abstract List<Integer> getIsolatedProcesses(int uid);

    /** @see ActivityManagerService#sendIntentSender */
    public abstract int sendIntentSender(IIntentSender target, IBinder allowlistToken, int code,
            Intent intent, String resolvedType,
            IIntentReceiver finishedReceiver, String requiredPermission, Bundle options);

    /**
     * Sets the provider to communicate between voice interaction manager service and
     * ActivityManagerService.
     */
    public abstract void setVoiceInteractionManagerProvider(
            @Nullable VoiceInteractionManagerProvider provider);

    /**
     * Sets whether the current foreground user (and its profiles) should be stopped after switched
     * out.
     */
    public abstract void setStopUserOnSwitch(@StopUserOnSwitch int value);

    /**
     * Provides the interface to communicate between voice interaction manager service and
     * ActivityManagerService.
     */
    public interface VoiceInteractionManagerProvider {
        /**
         * Notifies the service when an activity is destroyed.
         */
        void notifyActivityDestroyed(IBinder activityToken);
    }

    /**
     * Get the restriction level of the given UID, if it hosts multiple packages,
     * return least restricted level.
     */
    public abstract @RestrictionLevel int getRestrictionLevel(int uid);

    /**
     * Get the restriction level of the given package for given user id.
     */
    public abstract @RestrictionLevel int getRestrictionLevel(String pkg, @UserIdInt int userId);

    /**
     * Get whether or not apps would be put into restricted standby bucket automatically
     * when it's background-restricted.
     */
    public abstract boolean isBgAutoRestrictedBucketFeatureFlagEnabled();

    /**
     * A listener interface, which will be notified on background restriction changes.
     */
    public interface AppBackgroundRestrictionListener {
        /**
         * Called when the background restriction level of given uid/package is changed.
         */
        default void onRestrictionLevelChanged(int uid, String packageName,
                @RestrictionLevel int newLevel) {
        }

        /**
         * Called when toggling the feature flag of moving to restricted standby bucket
         * automatically on background-restricted.
         */
        default void onAutoRestrictedBucketFeatureFlagChanged(boolean autoRestrictedBucket) {
        }
    }

    /**
     * Register the background restriction listener callback.
     */
    public abstract void addAppBackgroundRestrictionListener(
            @NonNull AppBackgroundRestrictionListener listener);

    /**
     * A listener interface, which will be notified on foreground service state changes.
     */
    public interface ForegroundServiceStateListener {
        /**
         * Call when the given process's foreground service state changes.
         *
         * @param packageName The package name of the process.
         * @param uid The UID of the process.
         * @param pid The pid of the process.
         * @param started {@code true} if the process transits from non-FGS state to FGS state.
         */
        void onForegroundServiceStateChanged(String packageName, int uid, int pid, boolean started);

        /**
         * Call when the notification of the foreground service is updated.
         *
         * @param packageName The package name of the process.
         * @param uid The UID of the process.
         * @param foregroundId The current foreground service notification ID.
         * @param canceling The given notification is being canceled.
         */
        void onForegroundServiceNotificationUpdated(String packageName, int uid, int foregroundId,
                boolean canceling);
    }

    /**
     * Register the foreground service state change listener callback.
     */
    public abstract void addForegroundServiceStateListener(
            @NonNull ForegroundServiceStateListener listener);

    /**
     * A listener interface, which will be notified on the package sends a broadcast.
     */
    public interface BroadcastEventListener {
        /**
         * Called when the given package/uid is sending a broadcast.
         */
        void onSendingBroadcast(String packageName, int uid);
    }

    /**
     * Register the broadcast event listener callback.
     */
    public abstract void addBroadcastEventListener(@NonNull BroadcastEventListener listener);

    /**
     * A listener interface, which will be notified on the package binding to a service.
     */
    public interface BindServiceEventListener {
        /**
         * Called when the given package/uid is binding to a service
         */
        void onBindingService(String packageName, int uid);
    }

    /**
     * Register the bind service event listener callback.
     */
    public abstract void addBindServiceEventListener(@NonNull BindServiceEventListener listener);

    /**
     * Restart android.
     */
    public abstract void restart();

    /**
     * Returns some summary statistics of the current PendingIntent queue - sizes and counts.
     */
    public abstract List<PendingIntentStats> getPendingIntentStats();

    /**
     * Register the UidObserver for NetworkPolicyManager service.
     *
     * This is equivalent to calling
     * {@link IActivityManager#registerUidObserver(IUidObserver, int, int, String)} but having a
     * separate method for NetworkPolicyManager service so that it's UidObserver can be called
     * separately outside the usual UidObserver flow.
     */
    public abstract void registerNetworkPolicyUidObserver(@NonNull IUidObserver observer,
            int which, int cutpoint, @NonNull String callingPackage);
}
