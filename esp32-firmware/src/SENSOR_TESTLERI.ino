#include <OneWire.h>
#include <DallasTemperature.h>
#include <NimBLEDevice.h>
#include <Wire.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <WiFiClient.h>

// PINLER
#define ONE_WIRE_BUS 33
#define SOUND_PIN    25
#define LDR_PIN      34
#define I2C_SDA 18
#define I2C_SCL 19
#define ARDUINO_ADDR 0x08

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);

// Firebase
const char* FIREBASE_DB_URL = "https://esp32-sensor-data-50f8c-default-rtdb.firebaseio.com";
const char* WIFI_SSID = "Redmi Note 14";
const char* WIFI_PASS = "1234554321";


unsigned long motorStartTime = 0;
String motorState = "STOP";
bool motorRunning = false;
bool i2cOk = false;
unsigned long lastI2cCheck = 0;

bool pendingInitialMotorNotify = true;
unsigned long motorNotifyTime = 0;

// BLE UUID
#define SERVICE_UUID "12345678-1234-5678-1234-56789abcdef0"
#define TEMP_UUID     "00002A6E-0000-1000-8000-00805f9b34fb"
#define SOUND_UUID    "00002A58-0000-1000-8000-00805f9b34fb"
#define LIGHT_UUID    "00002AFB-0000-1000-8000-00805f9b34fb"
#define DIST_UUID     "00002A5D-0000-1000-8000-00805f9b34fb"
#define MOTOR_STATE_UUID "12345678-1234-5678-1234-56789abcdef8"
#define MOTOR_CMD_UUID "12345678-1234-5678-1234-56789abcdef7"

NimBLECharacteristic *tempChar, *soundChar, *lightChar, *distChar, *motorStateChar, *motorCmdChar;

// I2C
void sendMotorCommand(const char *cmd) {
    if (!i2cOk) {
        Serial.println("[I2C] Hata: Cihaz bagli degil.");
        return;
    }
    Wire.beginTransmission(ARDUINO_ADDR);
    Wire.write((const uint8_t *)cmd, strlen(cmd));
    uint8_t err = Wire.endTransmission();

    Serial.printf("[I2C] SEND '%s' err=%d\n", cmd, err); // <-- BU ÇOK ÖNEMLİ
}

// BLE Callback
class MotorCmdCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic, NimBLEConnInfo& connInfo) override {
        std::string cmd = pCharacteristic->getValue();
        String scmd = String(cmd.c_str());
        scmd.trim();

        Serial.print("[BLE WRITE] cmd=");
        Serial.println(scmd);

        motorState = "STOP";

        if (scmd == "OPEN" || scmd == "CLOSE" || scmd == "STOP") {
            motorState = scmd;

            sendMotorCommand(scmd.c_str());
            motorStateChar->setValue(motorState.c_str());
            motorStateChar->notify();

            motorRunning = (motorState != "STOP");
            if (motorRunning) motorStartTime = millis();
        } else {
            Serial.println("[BLE] Unknown motor command!");
        }
    }
};

class ServerCallbacks : public NimBLEServerCallbacks {

    void onConnect(NimBLEServer* pServer) {
        Serial.println("[BLE] Client connected");
        pendingInitialMotorNotify = true;
        motorNotifyTime = millis();

        delay(300); // CCCD yazılması için zaman ver

        motorStateChar->setValue(motorState.c_str());
        motorStateChar->notify();

        Serial.print("[BLE] Initial motor state sent: ");
        Serial.println(motorState);
    }

    void onDisconnect(NimBLEServer* pServer) {
        Serial.println("[BLE] Client disconnected");
        NimBLEDevice::startAdvertising();
    }
};

bool firebasePatch(const char* path, const char* jsonBody) {
    if (WiFi.status() != WL_CONNECTED) return false;

    WiFiClientSecure client;
    client.setInsecure();   // Sertifika kontrolünü kapatır (ESP32 için OK)

    HTTPClient https;
    String url = String(FIREBASE_DB_URL) + path;

    if (!https.begin(client, url)) {
        Serial.println("[FIREBASE] https.begin FAIL");
        return false;
    }

    https.addHeader("Content-Type", "application/json");
    int code = https.sendRequest("PATCH", jsonBody);
    https.end();

    Serial.print("[FIREBASE] HTTP code=");
    Serial.println(code);

    return (code > 0 && code < 400);
}

bool readDistance(float &dist) {
    Wire.requestFrom((uint8_t)ARDUINO_ADDR, (uint8_t)4);
    if (Wire.available() == 4) {
        uint8_t b[4];
        for (int i = 0; i < 4; i++) b[i] = Wire.read();
        memcpy(&dist, b, sizeof(float));
        return true;
    }
    return false;
}

bool checkI2CConnection() {
    Wire.beginTransmission(ARDUINO_ADDR);
    return (Wire.endTransmission() == 0);
}

void setup() {

    Serial.begin(115200);
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    Serial.println("ESP32 BOOTED");

    while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
    Serial.println("\nWiFi Baglandi");

    // I2C Bağlantısının sağlanması için
    sensors.begin();
    pinMode(SOUND_PIN, INPUT_PULLUP);
    Wire.begin(I2C_SDA, I2C_SCL);
    i2cOk = checkI2CConnection();

    pinMode(LDR_PIN, INPUT);


    NimBLEDevice::init("ESP32_SENSOR_NODE");
    NimBLEServer *server = NimBLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());
    NimBLEService *service = server->createService(SERVICE_UUID);


    tempChar = service->createCharacteristic(TEMP_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    soundChar = service->createCharacteristic(SOUND_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    lightChar = service->createCharacteristic(LIGHT_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    distChar = service->createCharacteristic(DIST_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    motorStateChar = service->createCharacteristic(MOTOR_STATE_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    motorCmdChar = service->createCharacteristic(
            MOTOR_CMD_UUID,
            NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR
    );

    motorCmdChar->setCallbacks(new MotorCmdCallbacks());  // <-- BURASI ŞART
    motorStateChar->setValue(motorState.c_str());  // "STOP"


    service->start();


    NimBLEAdvertising *adv = NimBLEDevice::getAdvertising();
    adv->stop();
    adv->clearData();

    NimBLEAdvertisementData advData;
    advData.setName("ESP32_SENSOR_NODE");
    advData.addServiceUUID(SERVICE_UUID);
    advData.setFlags(0x06); // LE General Discoverable + BR/EDR not supported

    adv->setAdvertisementData(advData);

    // Scan response
    NimBLEAdvertisementData scanData;
    scanData.setName("ESP32_SENSOR_NODE");
    adv->setScanResponseData(scanData);

    adv->start();

    Serial.println("[BLE] Advertising STARTED");
    Serial.println("=== BLE READY ===");
    Serial.print("Motor CMD UUID: ");
    Serial.println(MOTOR_CMD_UUID);

}

void loop() {
    static unsigned long lastSensorCheck = 0;
    static unsigned long lastFirebasePush = 0;
    static float lastTemp = 0, lastDist = 0;
    static int lastLight = 0, lastSound = 0;

    // I2C
    if (millis() - lastI2cCheck > 5000) {
        i2cOk = checkI2CConnection();
        lastI2cCheck = millis();
    }


    if (millis() - lastSensorCheck > 2000) {
        lastSensorCheck = millis();

        sensors.requestTemperatures();
        lastTemp = sensors.getTempCByIndex(0);
        tempChar->setValue((uint8_t*)&lastTemp, sizeof(float));
        tempChar->notify();

        float debugTemp = lastTemp;

        Serial.print("[PIN 33] Gerçek Zamanlı Veri: ");
        if (debugTemp == DEVICE_DISCONNECTED_C) {
            Serial.println("HATA: Sensör okunamıyor");
        } else {
            Serial.print(debugTemp);
            Serial.println(" °C");
        }


        lastSound = digitalRead(SOUND_PIN);
        uint8_t sVal = (uint8_t)lastSound;
        soundChar->setValue(&sVal, 1);
        soundChar->notify();

        int lightRaw = analogRead(LDR_PIN);
        lastLight = map(lightRaw, 0, 4095, 100, 0); // Ters orantı: Karanlıkta değer artar, map ile düzeltildi
        uint8_t lVal = (uint8_t)constrain(lastLight, 0, 100);
        lightChar->setValue(&lVal, 1);
        lightChar->notify();

        float d;
        if (readDistance(d)) {
            lastDist = d;
            distChar->setValue((uint8_t*)&d, sizeof(float));
            distChar->notify();
        }

    }

    // Firebase
    if (millis() - lastFirebasePush > 10000) {
        lastFirebasePush = millis();
        char body[200];

        time_t now = time(nullptr);
        long ts = millis() / 1000;
        snprintf(body, sizeof(body),
                 "{\"temp\":%.1f,\"light\":%d,\"sound\":%d,\"dist\":%.1f,\"motor\":\"%s\",\"ts\":%ld}",
                 lastTemp, lastLight, lastSound, lastDist, motorState.c_str(), ts);
        bool ok1 = firebasePatch("/sensors/current.json", body);
        //bool ok2 = firebasePost("/sensors/history.json", body);

        Serial.print("[FIREBASE] current=");
        Serial.print(ok1 ? "OK" : "FAIL");

    }


    if (pendingInitialMotorNotify && millis() - motorNotifyTime > 2500) {
        pendingInitialMotorNotify = false;

        motorStateChar->setValue(motorState.c_str());
        motorStateChar->notify();

        Serial.print("[BLE] Initial motor state notify sent: ");
        Serial.println(motorState);
    }
}