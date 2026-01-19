# BLE IoT Sensor Monitoring and Control System

## Overview
This project is an IoT-based monitoring and control system developed using ESP32, Arduino Uno, and an Android application.
The system collects environmental sensor data via BLE and Wi-Fi, stores data locally and remotely, and allows motor control through a mobile interface.
The implementation is based on real hardware and real-time communication.

## System Architecture
The system consists of three main layers:
- Hardware layer (ESP32, Arduino Uno, sensors, motor driver)
- Communication layer (BLE and Wi-Fi)
- Mobile application layer (Android)

## Hardware Components
- ESP32 (BLE and Wi-Fi enabled microcontroller)
- Arduino Uno (motor control)
- L298N motor driver
- Sensors:
    - DS18B20 temperature sensor
    - LDR light sensor
    - HC-SR04 ultrasonic distance sensor
    - Sound sensor module
- DC motor (curtain motor)
- Breadboard and auxiliary components

## Data Flow
- Sensor data is collected by ESP32
- Real-time data is sent to the Android application via BLE notifications
- Periodic sensor updates are sent to Firebase Realtime Database via Wi-Fi
- The Android application stores historical data locally using Room Database

## BLE Communication
- ESP32 operates as a BLE GATT Server
- Android application operates as a BLE GATT Client
- Sensor values are transmitted using READ and NOTIFY characteristics
- Motor control commands are sent using WRITE characteristics
- CCCD descriptor is explicitly written to enable notifications

## Android Application Architecture
- Kotlin-based Android application
- MVVM architecture
- Jetpack Compose for UI
- ViewModels manage BLE, Firebase, and Room interactions
- Room Database is used for local historical data storage
- Firebase Realtime Database is used for real-time cloud synchronization

## Local Data Storage
- Sensor data is stored locally for history tracking
- Time-based filtering is supported (daily, weekly, monthly)
- Aggregated sensor data is visualized using line charts

## Key Challenges
- BLE permission handling on Android 12+
- Reliable BLE notification handling
- Synchronization between BLE, Firebase, and Room data sources
- Stable communication between ESP32 and Arduino via I2C

## Future Improvements
- MQTT-based communication
- Offline-first synchronization
- Multi-node ESP32 support
- Edge computing and anomaly detection

## Technologies Used
- ESP32
- Arduino Uno
- Kotlin (Android)
- Jetpack Compose
- Firebase Realtime Database
- Room Database
- BLE (GATT)
