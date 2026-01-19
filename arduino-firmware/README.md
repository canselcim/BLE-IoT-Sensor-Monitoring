# Arduino Motor Control Firmware

## Overview
This firmware runs on Arduino Uno and is responsible for controlling the DC motor via the L298N motor driver.
The Arduino operates as an I2C slave device.

## Responsibilities
- Receive motor control commands from ESP32 via I2C
- Control motor direction and speed using the L298N driver
- Execute open and close operations for the curtain motor

## Communication
- I2C:
    - Arduino acts as a slave device
    - Receives control commands from ESP32

## Notes
The firmware focuses on reliable motor control and stable communication.
