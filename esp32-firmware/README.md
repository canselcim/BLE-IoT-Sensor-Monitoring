# ESP32 Firmware

## Overview
This firmware runs on the ESP32 and is responsible for sensor data acquisition, BLE communication, and cloud data transmission.
The ESP32 acts as the main controller of the system.

## Responsibilities
- Read sensor data (temperature, light, distance, sound)
- Act as a BLE GATT Server
- Transmit real-time sensor data to the Android application via BLE
- Send periodic sensor updates to Firebase Realtime Database over Wi-Fi
- Communicate with Arduino Uno via I2C for motor control

## Communication
- BLE:
    - READ and NOTIFY characteristics for sensor data
    - WRITE characteristic for motor commands
- Wi-Fi:
    - HTTP PATCH requests to Firebase Realtime Database
- I2C:
    - Sends motor control commands to Arduino Uno

## Notes
This firmware is designed and tested on real ESP32 hardware.
