/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.footer.ui.viewmodel

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationListRepository
import com.android.systemui.statusbar.notification.domain.interactor.SeenNotificationsInteractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidTestingRunner::class)
@SmallTest
class FooterViewModelTest : SysuiTestCase() {
    private val repository = ActiveNotificationListRepository()
    private val interactor = SeenNotificationsInteractor(repository)
    private val underTest = FooterViewModel(interactor)

    @Test
    fun testMessageVisible_whenFilteredNotifications() = runTest {
        val message by collectLastValue(underTest.message)

        repository.hasFilteredOutSeenNotifications.value = true

        assertThat(message?.visible).isTrue()
    }

    @Test
    fun testMessageVisible_whenNoFilteredNotifications() = runTest {
        val message by collectLastValue(underTest.message)

        repository.hasFilteredOutSeenNotifications.value = false

        assertThat(message?.visible).isFalse()
    }
}
