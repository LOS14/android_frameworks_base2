/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.companion.virtual.sensor;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class VirtualSensorConfigTest {

    private static final String SENSOR_NAME = "VirtualSensorName";
    private static final String SENSOR_VENDOR = "VirtualSensorVendor";

    @Test
    public void parcelAndUnparcel_matches() {
        final VirtualSensorConfig originalConfig =
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                        .setVendor(SENSOR_VENDOR)
                        .build();
        final Parcel parcel = Parcel.obtain();
        originalConfig.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualSensorConfig recreatedConfig =
                VirtualSensorConfig.CREATOR.createFromParcel(parcel);
        assertThat(recreatedConfig.getType()).isEqualTo(originalConfig.getType());
        assertThat(recreatedConfig.getName()).isEqualTo(originalConfig.getName());
        assertThat(recreatedConfig.getVendor()).isEqualTo(originalConfig.getVendor());
    }

    @Test
    public void sensorConfig_onlyRequiredFields() {
        final VirtualSensorConfig config =
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME).build();
        assertThat(config.getVendor()).isNull();
    }
}
