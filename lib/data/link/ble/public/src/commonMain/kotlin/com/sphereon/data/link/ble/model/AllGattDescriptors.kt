/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.data.link.ble.model

import com.sphereon.core.compat.Uuid
import kotlin.uuid.ExperimentalUuidApi

private val knownGattDescriptors: Map<String, String> =
    mapOf(
        // Key-value pairs initialization
        "00002900-0000-1000-8000-00805f9b34fb" to "Characteristic Extended Properties",
        "00002901-0000-1000-8000-00805f9b34fb" to "Characteristic User Description",
        "00002902-0000-1000-8000-00805f9b34fb" to "Client Characteristic Configuration",
        "00002903-0000-1000-8000-00805f9b34fb" to "Server Characteristic Configuration",
        "00002904-0000-1000-8000-00805f9b34fb" to "Characteristic Presentation Format",
        "00002905-0000-1000-8000-00805f9b34fb" to "Characteristic Aggregate Format",
        "00002906-0000-1000-8000-00805f9b34fb" to "Valid Range",
        "00002907-0000-1000-8000-00805f9b34fb" to "External Report Reference",
        "00002908-0000-1000-8000-00805f9b34fb" to "Report Reference",
        "00002909-0000-1000-8000-00805f9b34fb" to "Number of Digitals",
        "0000290a-0000-1000-8000-00805f9b34fb" to "Value Trigger Setting",
        "0000290b-0000-1000-8000-00805f9b34fb" to "Environmental Sensing Configuration",
        "0000290c-0000-1000-8000-00805f9b34fb" to "Environmental Sensing Measurement",
        "0000290d-0000-1000-8000-00805f9b34fb" to "Environmental Sensing Trigger Setting",
        "0000290e-0000-1000-8000-00805f9b34fb" to "Time Trigger Setting",
    )

fun gattDescriptorToName(descriptor: Uuid): String = knownGattDescriptors[descriptor.toString()] ?: descriptor.toString()

private val knownGattAttributeNanes =
    mapOf(
        "00002a00-0000-1000-8000-00805f9b34fb" to "Device Name",
        "00002a01-0000-1000-8000-00805f9b34fb" to "Appearance",
        "00002a02-0000-1000-8000-00805f9b34fb" to "Peripheral Privacy Flag",
        "00002a03-0000-1000-8000-00805f9b34fb" to "Reconnection Address",
        "00002a04-0000-1000-8000-00805f9b34fb" to "Peripheral Preferred Connection Parameters",
        "00002a05-0000-1000-8000-00805f9b34fb" to "Service Changed",
        "00002a06-0000-1000-8000-00805f9b34fb" to "Alert Level",
        "00002a07-0000-1000-8000-00805f9b34fb" to "Tx Power Level",
        "00002a08-0000-1000-8000-00805f9b34fb" to "Date Time",
        "00002a09-0000-1000-8000-00805f9b34fb" to "Day of Week",
        "00002a0a-0000-1000-8000-00805f9b34fb" to "Day Date Time",
        "00002a0b-0000-1000-8000-00805f9b34fb" to "Exact Time 100",
        "00002a0c-0000-1000-8000-00805f9b34fb" to "Exact Time 256",
        "00002a0d-0000-1000-8000-00805f9b34fb" to "DST Offset",
        "00002a0e-0000-1000-8000-00805f9b34fb" to "Time Zone",
        "00002a0f-0000-1000-8000-00805f9b34fb" to "Local Time Information",
        "00002a10-0000-1000-8000-00805f9b34fb" to "Secondary Time Zone",
        "00002a11-0000-1000-8000-00805f9b34fb" to "Time with DST",
        "00002a12-0000-1000-8000-00805f9b34fb" to "Time Accuracy",
        "00002a13-0000-1000-8000-00805f9b34fb" to "Time Source",
        "00002a14-0000-1000-8000-00805f9b34fb" to "Reference Time Information",
        "00002a15-0000-1000-8000-00805f9b34fb" to "Time Broadcast",
        "00002a16-0000-1000-8000-00805f9b34fb" to "Time Update Control Point",
        "00002a17-0000-1000-8000-00805f9b34fb" to "Time Update State",
        "00002a18-0000-1000-8000-00805f9b34fb" to "Glucose Measurement",
        "00002a19-0000-1000-8000-00805f9b34fb" to "Battery Level",
        "00002a1a-0000-1000-8000-00805f9b34fb" to "Battery Power State",
        "00002a1b-0000-1000-8000-00805f9b34fb" to "Battery Level State",
        "00002a1c-0000-1000-8000-00805f9b34fb" to "Temperature Measurement",
        "00002a1d-0000-1000-8000-00805f9b34fb" to "Temperature Type",
        "00002a1e-0000-1000-8000-00805f9b34fb" to "Intermediate Temperature",
        "00002a1f-0000-1000-8000-00805f9b34fb" to "Temperature Celsius",
        "00002a20-0000-1000-8000-00805f9b34fb" to "Temperature Fahrenheit",
        "00002a21-0000-1000-8000-00805f9b34fb" to "Measurement Interval",
        "00002a22-0000-1000-8000-00805f9b34fb" to "Boot Keyboard Input Report",
        "00002a23-0000-1000-8000-00805f9b34fb" to "System ID",
        "00002a24-0000-1000-8000-00805f9b34fb" to "Model Number String",
        "00002a25-0000-1000-8000-00805f9b34fb" to "Serial Number String",
        "00002a26-0000-1000-8000-00805f9b34fb" to "Firmware Revision String",
        "00002a27-0000-1000-8000-00805f9b34fb" to "Hardware Revision String",
        "00002a28-0000-1000-8000-00805f9b34fb" to "Software Revision String",
        "00002a29-0000-1000-8000-00805f9b34fb" to "Manufacturer Name String",
        "00002a2a-0000-1000-8000-00805f9b34fb" to "IEEE 11073-20601 Regulatory Certification Data List",
        "00002a2b-0000-1000-8000-00805f9b34fb" to "Current Time",
        "00002a2c-0000-1000-8000-00805f9b34fb" to "Magnetic Declination",
        "00002a2f-0000-1000-8000-00805f9b34fb" to "Position 2D",
        "00002a30-0000-1000-8000-00805f9b34fb" to "Position 3D",
        "00002a31-0000-1000-8000-00805f9b34fb" to "Scan Refresh",
        "00002a32-0000-1000-8000-00805f9b34fb" to "Boot Keyboard Output Report",
        "00002a33-0000-1000-8000-00805f9b34fb" to "Boot Mouse Input Report",
        "00002a34-0000-1000-8000-00805f9b34fb" to "Glucose Measurement Context",
        "00002a35-0000-1000-8000-00805f9b34fb" to "Blood Pressure Measurement",
        "00002a36-0000-1000-8000-00805f9b34fb" to "Intermediate Cuff Pressure",
        "00002a37-0000-1000-8000-00805f9b34fb" to "Heart Rate Measurement",
        "00002a38-0000-1000-8000-00805f9b34fb" to "Body Sensor Location",
        "00002a39-0000-1000-8000-00805f9b34fb" to "Heart Rate Control Point",
        "00002a3a-0000-1000-8000-00805f9b34fb" to "Removable",
        "00002a3b-0000-1000-8000-00805f9b34fb" to "Service Required",
        "00002a3c-0000-1000-8000-00805f9b34fb" to "Scientific Temperature Celsius",
        "00002a3d-0000-1000-8000-00805f9b34fb" to "String",
        "00002a3e-0000-1000-8000-00805f9b34fb" to "Network Availability",
        "00002a3f-0000-1000-8000-00805f9b34fb" to "Alert Status",
        "00002a40-0000-1000-8000-00805f9b34fb" to "Ringer Control point",
        "00002a41-0000-1000-8000-00805f9b34fb" to "Ringer Setting",
        "00002a42-0000-1000-8000-00805f9b34fb" to "Alert Category ID Bit Mask",
        "00002a43-0000-1000-8000-00805f9b34fb" to "Alert Category ID",
        "00002a44-0000-1000-8000-00805f9b34fb" to "Alert Notification Control Point",
        "00002a45-0000-1000-8000-00805f9b34fb" to "Unread Alert Status",
        "00002a46-0000-1000-8000-00805f9b34fb" to "New Alert",
        "00002a47-0000-1000-8000-00805f9b34fb" to "Supported New Alert Category",
        "00002a48-0000-1000-8000-00805f9b34fb" to "Supported Unread Alert Category",
        "00002a49-0000-1000-8000-00805f9b34fb" to "Blood Pressure Feature",
        "00002a4a-0000-1000-8000-00805f9b34fb" to "HID Information",
        "00002a4b-0000-1000-8000-00805f9b34fb" to "Report Map",
        "00002a4c-0000-1000-8000-00805f9b34fb" to "HID Control Point",
        "00002a4d-0000-1000-8000-00805f9b34fb" to "Report",
        "00002a4e-0000-1000-8000-00805f9b34fb" to "Protocol Mode",
        "00002a4f-0000-1000-8000-00805f9b34fb" to "Scan Interval Window",
        "00002a50-0000-1000-8000-00805f9b34fb" to "PnP ID",
        "00002a51-0000-1000-8000-00805f9b34fb" to "Glucose Feature",
        "00002a52-0000-1000-8000-00805f9b34fb" to "Record Access Control Point",
        "00002a53-0000-1000-8000-00805f9b34fb" to "RSC Measurement",
        "00002a54-0000-1000-8000-00805f9b34fb" to "RSC Feature",
        "00002a55-0000-1000-8000-00805f9b34fb" to "SC Control Point",
        "00002a56-0000-1000-8000-00805f9b34fb" to "Digital",
        "00002a57-0000-1000-8000-00805f9b34fb" to "Digital Output",
        "00002a58-0000-1000-8000-00805f9b34fb" to "Analog",
        "00002a59-0000-1000-8000-00805f9b34fb" to "Analog Output",
        "00002a5a-0000-1000-8000-00805f9b34fb" to "Aggregate",
        "00002a5b-0000-1000-8000-00805f9b34fb" to "CSC Measurement",
        "00002a5c-0000-1000-8000-00805f9b34fb" to "CSC Feature",
        "00002a5d-0000-1000-8000-00805f9b34fb" to "Sensor Location",
        "00002a5e-0000-1000-8000-00805f9b34fb" to "PLX Spot-Check Measurement",
        "00002a5f-0000-1000-8000-00805f9b34fb" to "PLX Continuous Measurement Characteristic",
        "00002a60-0000-1000-8000-00805f9b34fb" to "PLX Features",
        "00002a62-0000-1000-8000-00805f9b34fb" to "Pulse Oximetry Control Point",
        "00002a63-0000-1000-8000-00805f9b34fb" to "Cycling Power Measurement",
        "00002a64-0000-1000-8000-00805f9b34fb" to "Cycling Power Vector",
        "00002a65-0000-1000-8000-00805f9b34fb" to "Cycling Power Feature",
        "00002a66-0000-1000-8000-00805f9b34fb" to "Cycling Power Control Point",
        "00002a67-0000-1000-8000-00805f9b34fb" to "Location and Speed Characteristic",
        "00002a68-0000-1000-8000-00805f9b34fb" to "Navigation",
        "00002a69-0000-1000-8000-00805f9b34fb" to "Position Quality",
        "00002a6a-0000-1000-8000-00805f9b34fb" to "LN Feature",
        "00002a6b-0000-1000-8000-00805f9b34fb" to "LN Control Point",
        "00002a6c-0000-1000-8000-00805f9b34fb" to "Elevation",
        "00002a6d-0000-1000-8000-00805f9b34fb" to "Pressure",
        "00002a6e-0000-1000-8000-00805f9b34fb" to "Temperature",
        "00002a6f-0000-1000-8000-00805f9b34fb" to "Humidity",
        "00002a70-0000-1000-8000-00805f9b34fb" to "True Wind Speed",
        "00002a71-0000-1000-8000-00805f9b34fb" to "True Wind Direction",
        "00002a72-0000-1000-8000-00805f9b34fb" to "Apparent Wind Speed",
        "00002a73-0000-1000-8000-00805f9b34fb" to "Apparent Wind Direction",
        "00002a74-0000-1000-8000-00805f9b34fb" to "Gust Factor",
        "00002a75-0000-1000-8000-00805f9b34fb" to "Pollen Concentration",
        "00002a76-0000-1000-8000-00805f9b34fb" to "UV Index",
        "00002a77-0000-1000-8000-00805f9b34fb" to "Irradiance",
        "00002a78-0000-1000-8000-00805f9b34fb" to "Rainfall",
        "00002a79-0000-1000-8000-00805f9b34fb" to "Wind Chill",
    )

fun gattAttributeToName(attribute: Uuid): String = knownGattAttributeNanes[attribute.toString()] ?: attribute.toString()

object MdocHolderServiceChars {
    object Command {
        const val END = "0x02"
    }

    object ByUuid {
        val STATE = kotlin.uuid.Uuid.parse("00000001-A123-48CE-896B-4C76973373E6")
        val CLIENT_2_SERVER = kotlin.uuid.Uuid.parse("00000002-A123-48CE-896B-4C76973373E6")
        val SERVER_2_CLIENT = kotlin.uuid.Uuid.parse("00000003-A123-48CE-896B-4C76973373E6")
    }
}

object MdocReaderServiceChars {
    object Command {
        const val LAST_PART: Int = 0x00
        const val START: Int = 0x01
        const val END: Int = 0x02
    }

    object ByUuid {
        val STATE = kotlin.uuid.Uuid.parse("00000005-A123-48CE-896B-4C76973373E6")
        val CLIENT_2_SERVER = kotlin.uuid.Uuid.parse("00000006-A123-48CE-896B-4C76973373E6")
        val SERVER_2_CLIENT = kotlin.uuid.Uuid.parse("00000007-A123-48CE-896B-4C76973373E6")
        val IDENT = kotlin.uuid.Uuid.parse("00000008-A123-48CE-896B-4C76973373E6")
    }
}

@OptIn(ExperimentalUuidApi::class)
val knownGattServiceNames =
    mapOf<String, String>(
        MdocHolderServiceChars.ByUuid.STATE.toString() to "Mdoc Holder State",
        MdocHolderServiceChars.ByUuid.CLIENT_2_SERVER.toString() to "Mdoc Holder Client to Server",
        MdocHolderServiceChars.ByUuid.SERVER_2_CLIENT.toString() to "Mdoc Holder Server to Client",
        MdocReaderServiceChars.ByUuid.STATE.toString() to "Mdoc Reader State",
        MdocReaderServiceChars.ByUuid.CLIENT_2_SERVER.toString() to "Mdoc Reader Client to Server",
        MdocReaderServiceChars.ByUuid.SERVER_2_CLIENT.toString() to "Mdoc Reader Server to Client",
        MdocReaderServiceChars.ByUuid.IDENT.toString() to "Mdoc Reader Identify",
        "00001800-0000-1000-8000-00805f9b34fb" to "Generic Access",
        "00001801-0000-1000-8000-00805f9b34fb" to "Generic Attribute",
        "00001802-0000-1000-8000-00805f9b34fb" to "Immediate Alert",
        "00001803-0000-1000-8000-00805f9b34fb" to "Link Loss",
        "00001804-0000-1000-8000-00805f9b34fb" to "Tx Power",
        "00001805-0000-1000-8000-00805f9b34fb" to "Current Time Service",
        "00001806-0000-1000-8000-00805f9b34fb" to "Reference Time Update Service",
        "00001807-0000-1000-8000-00805f9b34fb" to "Next DST Change Service",
        "00001808-0000-1000-8000-00805f9b34fb" to "Glucose",
        "00001809-0000-1000-8000-00805f9b34fb" to "Health Thermometer",
        "0000180a-0000-1000-8000-00805f9b34fb" to "Device Information",
        "0000180d-0000-1000-8000-00805f9b34fb" to "Heart Rate",
        "0000180e-0000-1000-8000-00805f9b34fb" to "Phone Alert Status Service",
        "0000180f-0000-1000-8000-00805f9b34fb" to "Battery Service",
        "00001810-0000-1000-8000-00805f9b34fb" to "Blood Pressure",
        "00001811-0000-1000-8000-00805f9b34fb" to "Alert Notification Service",
        "00001812-0000-1000-8000-00805f9b34fb" to "Human Interface Device",
        "00001813-0000-1000-8000-00805f9b34fb" to "Scan Parameters",
        "00001814-0000-1000-8000-00805f9b34fb" to "Running Speed and Cadence",
        "00001815-0000-1000-8000-00805f9b34fb" to "Automation IO",
        "00001816-0000-1000-8000-00805f9b34fb" to "Cycling Speed and Cadence",
        "00001818-0000-1000-8000-00805f9b34fb" to "Cycling Power",
        "00001819-0000-1000-8000-00805f9b34fb" to "Location and Navigation",
        "0000181a-0000-1000-8000-00805f9b34fb" to "Environmental Sensing",
        "0000181b-0000-1000-8000-00805f9b34fb" to "Body Composition",
        "0000181c-0000-1000-8000-00805f9b34fb" to "User Data",
        "0000181d-0000-1000-8000-00805f9b34fb" to "Weight Scale",
        "0000181e-0000-1000-8000-00805f9b34fb" to "Bond Management Service",
        "0000181f-0000-1000-8000-00805f9b34fb" to "Continuous Glucose Monitoring",
        "00001820-0000-1000-8000-00805f9b34fb" to "Internet Protocol Support Service",
        "00001821-0000-1000-8000-00805f9b34fb" to "Indoor Positioning",
        "00001822-0000-1000-8000-00805f9b34fb" to "Pulse Oximeter Service",
        "00001823-0000-1000-8000-00805f9b34fb" to "HTTP Proxy",
        "00001824-0000-1000-8000-00805f9b34fb" to "Transport Discovery",
        "00001825-0000-1000-8000-00805f9b34fb" to "Object Transfer Service",
        "00001826-0000-1000-8000-00805f9b34fb" to "Fitness Machine",
        "00001827-0000-1000-8000-00805f9b34fb" to "Mesh Provisioning Service",
        "00001828-0000-1000-8000-00805f9b34fb" to "Mesh Proxy Service",
        "00001829-0000-1000-8000-00805f9b34fb" to "Reconnection Configuration",
        "00002a37-0000-1000-8000-00805f9b34fb" to "Heart Rate Measurement",
        "00002a29-0000-1000-8000-00805f9b34fb" to "Manufacturer Name String",
        "00002a00-0000-1000-8000-00805f9b34fb" to "Device Name",
        "00002a01-0000-1000-8000-00805f9b34fb" to "Appearance",
        "00002a02-0000-1000-8000-00805f9b34fb" to "Peripheral Privacy Flag",
        "00002a03-0000-1000-8000-00805f9b34fb" to "Reconnection Address",
        "00002a04-0000-1000-8000-00805f9b34fb" to "Manufacturer Name String",
        "00002a05-0000-1000-8000-00805f9b34fb" to "Service Changed",
        "00002A06-0000-1000-8000-00805f9b34fb" to "Alert level",
    )

fun gattServiceToName(attribute: Uuid): String = knownGattServiceNames[attribute.toString()] ?: attribute.toString()
