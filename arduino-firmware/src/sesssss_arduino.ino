#include <Wire.h>

// L298N MOTOR PINS

#define IN1 4
#define IN2 5

// HC-SR04 PINS
#define TRIG_PIN 7
#define ECHO_PIN 6


#define I2C_ADDRESS 0x08


volatile float lastDistance = -1.0;
String motorState = "STOP";

unsigned long lastMeasureTime = 0;
const unsigned long MEASURE_INTERVAL = 60; // ms (HC-SR04 için güvenli)


// MOTOR

void motorStop() {
    digitalWrite(IN1, HIGH);
    digitalWrite(IN2, HIGH);
    motorState = "STOP";
}

void motorOpen() {
    digitalWrite(IN1, HIGH);
    digitalWrite(IN2, LOW);
    motorState = "OPEN";
}

void motorClose() {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, HIGH);
    motorState = "CLOSE";
}

// HC-SR04

float measureDistance() {
    digitalWrite(TRIG_PIN, LOW);
    delayMicroseconds(2);

    digitalWrite(TRIG_PIN, HIGH);
    delayMicroseconds(10);
    digitalWrite(TRIG_PIN, LOW);

    long duration = pulseIn(ECHO_PIN, HIGH, 30000); // 30ms timeout
    if (duration == 0) return -1.0;

    return duration * 0.0343f / 2.0f;
}




// ESP32 -> Arduino
void onReceive(int howMany) {
    String cmd = "";

    while (Wire.available()) {
        cmd += (char)Wire.read();
    }
    cmd.trim();

    Serial.print("[I2C CMD] ");
    Serial.println(cmd);

    if (cmd == "OPEN")      motorOpen();
    else if (cmd == "CLOSE") motorClose();
    else if (cmd == "STOP")  motorStop();
}

// ESP32 <- Arduino (4 byte float)
void onRequest() {
    Wire.write((uint8_t*)&lastDistance, sizeof(float));
}


void setup() {
    Serial.begin(9600);

    pinMode(IN1, OUTPUT);
    pinMode(IN2, OUTPUT);

    pinMode(TRIG_PIN, OUTPUT);
    pinMode(ECHO_PIN, INPUT);

    motorStop();

    Wire.begin(I2C_ADDRESS);
    Wire.onReceive(onReceive);
    Wire.onRequest(onRequest);

    Serial.println(" Arduino UNO I2C + Motor + HC-SR04 HAZIR");
}

void loop() {

    // --- HC-SR04 (NON-BLOCKING)
    if (millis() - lastMeasureTime >= MEASURE_INTERVAL) {
        lastMeasureTime = millis();
        lastDistance = measureDistance();

        Serial.print("Mesafe: ");
        Serial.print(lastDistance);
        Serial.print(" cm | Motor: ");
        Serial.println(motorState);
    }

    //delay(1000);
}
