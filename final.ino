#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <Keypad.h>
#include <SoftwareSerial.h>
#include <math.h>
#include <ESP32Servo.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>

// WiFi credentials
const char* ssid = "Shirodhara";
const char* password = "Zenevo@123";

// Create WebServer object on port 80
WebServer server(80);

// JSON document for API responses
StaticJsonDocument<256> jsonDoc;

// LCD2004 I2C Display
#define LCD_ADDRESS 0x27
#define LCD_COLUMNS 20
#define LCD_ROWS 4

// Sensor and Control Pins
#define TEMP_SENSOR_PIN 34
#define HEATER_RELAY_PIN 5
#define PUMP_RELAY_PIN 16
#define BUZZER_PIN 19
#define SERVO_PIN 22
#define EMERGENCY_STOP_PIN 2

// I2C Pins
#define I2C_SDA 23
#define I2C_SCL 15

// Setting limits
#define MAX_DURATION 60
#define MAX_TEMPERATURE 45

// Temperature control parameters
#define TEMP_TOLERANCE 3.0
#define TEMP_NUM_SAMPLES 5 // Number of samples to average for stability

// Thermistor parameters
#define THERMISTOR_NOMINAL 10000
#define TEMPERATURE_NOMINAL 25
#define B_COEFFICIENT 3950
#define SERIES_RESISTOR 4700

// Bluetooth Module
SoftwareSerial bluetooth(17, 18);

// LCD object
LiquidCrystal_I2C lcd(LCD_ADDRESS, LCD_COLUMNS, LCD_ROWS);

// Emergency stop variables
bool emergencyStopActive = false;

// Treatment variables
bool treatmentActive = false;
bool heatingActive = false;
bool temperatureReached = false;
unsigned long treatmentStartTime = 0;
unsigned long treatmentDuration = 0; // in milliseconds
bool heaterState = false;
bool servoActive = false;
unsigned long lastServoUpdateTime = 0;
int servoPosition = 0;
int servoDirection = 1;
int servoMinAngle = 60;
int servoMaxAngle = 120;
int servoSpeed = 5;
unsigned long servoUpdateInterval = 50;

// Servo parameters
#define MIN_MICROS 800
#define MAX_MICROS 2450

// Servo object
Servo oscillationServo;

// Keypad setup
const byte ROWS = 4;
const byte COLS = 4;
char keys[ROWS][COLS] = {
  {'1','4','7','*'},
  {'2','5','8','0'},
  {'3','6','9','#'},
  {'A','B','C','D'}
};
byte rowPins[ROWS] = {25, 26, 27, 14};
byte colPins[COLS] = {32, 33, 12, 13};
Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

// Variables for settings
int duration = 0;
int temperature = 0;
float currentTemperature = 0.0;
bool settingDuration = true;
String durationStr = "";
String temperatureStr = "";
unsigned long lastTempReadTime = 0;
const long tempReadInterval = 2000; // Read temperature every 2 seconds

// Forward declarations
void displaySettingsScreen();
void startHeating();
void startTreatment();
void stopTreatment();
void controlHeater();

void setupWiFi() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid, password);
  IPAddress IP = IPAddress(192, 168, 0, 1);
  IPAddress gateway(192, 168, 0, 1);
  IPAddress subnet(255, 255, 255, 0);
  WiFi.softAPConfig(IP, gateway, subnet);
  Serial.println("WiFi AP Started. IP: " + WiFi.softAPIP().toString());
}

void handleHealth() {
  jsonDoc.clear();
  jsonDoc["temperature"] = currentTemperature;
  jsonDoc["heater_state"] = heaterState;
  jsonDoc["treatment_active"] = treatmentActive;
  jsonDoc["heating_active"] = heatingActive;
  jsonDoc["temperature_reached"] = temperatureReached;
  jsonDoc["target_temperature"] = temperature;
  jsonDoc["target_duration"] = duration;

  if (treatmentActive) {
    unsigned long elapsedTime = (millis() - treatmentStartTime) / 1000;
    unsigned long remainingSeconds = (treatmentDuration / 1000) > elapsedTime ? (treatmentDuration / 1000) - elapsedTime : 0;
    jsonDoc["remaining_time"] = remainingSeconds;
  } else {
    jsonDoc["remaining_time"] = duration * 60;
  }

  String response;
  serializeJson(jsonDoc, response);
  server.send(200, "application/json", response);
}

void handleUpdate() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No data received\"}");
    return;
  }

  String body = server.arg("plain");
  StaticJsonDocument<256> doc;
  DeserializationError error = deserializeJson(doc, body);

  if (error) {
    server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    return;
  }

  if (doc.containsKey("action")) {
    String action = doc["action"];
    if (action == "start") {
      if (temperatureReached && !treatmentActive) {
        startTreatment();
        server.send(200, "application/json", "{\"status\":\"Treatment started\"}");
      } else if (treatmentActive) {
        server.send(400, "application/json", "{\"error\":\"Treatment already active\"}");
      } else {
        server.send(400, "application/json", "{\"error\":\"Temperature not ready yet\"}");
      }
    } else if (action == "stop") {
      stopTreatment();
      server.send(200, "application/json", "{\"status\":\"Process stopped\"}");
    } else {
      server.send(400, "application/json", "{\"error\":\"Invalid action\"}");
    }
    return;
  }

  if (doc.containsKey("duration") && doc.containsKey("temperature")) {
    int newDuration = doc["duration"];
    int newTemperature = doc["temperature"];

    if (newDuration <= 0 || newDuration > MAX_DURATION || newTemperature <= 0 || newTemperature > MAX_TEMPERATURE) {
      server.send(400, "application/json", "{\"error\":\"Invalid parameters\"}");
      return;
    }

    duration = newDuration;
    temperature = newTemperature;
    durationStr = String(duration);
    temperatureStr = String(temperature);

    displaySettingsScreen();
    delay(1000);

    startHeating();
    server.send(200, "application/json", "{\"status\":\"Heating started\"}");
    return;
  }

  server.send(400, "application/json", "{\"error\":\"Invalid request payload\"}");
}


void setupAPI() {
  server.on("/api/health", HTTP_GET, handleHealth);
  server.on("/api/update", HTTP_POST, handleUpdate);
  server.enableCORS(true);
  server.begin();
  Serial.println("HTTP server started");
}

void setup() {
  Serial.begin(115200);
  bluetooth.begin(9600);

  setupWiFi();
  setupAPI();

  Wire.begin(I2C_SDA, I2C_SCL);
  lcd.init();
  lcd.backlight();

  pinMode(HEATER_RELAY_PIN, OUTPUT);
  pinMode(PUMP_RELAY_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(EMERGENCY_STOP_PIN, INPUT_PULLUP);

  oscillationServo.attach(SERVO_PIN, MIN_MICROS, MAX_MICROS);
  oscillationServo.write(90);

  digitalWrite(HEATER_RELAY_PIN, LOW);
  digitalWrite(PUMP_RELAY_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);

  digitalWrite(BUZZER_PIN, HIGH);
  delay(2000);
  digitalWrite(BUZZER_PIN, LOW);

  analogReadResolution(12);

  lcd.clear();
  lcd.setCursor(0, 1);
  lcd.print("ZENEVO INNOVATIONS");
  delay(5000);

  lcd.clear();
  lcd.setCursor(3, 0);
  lcd.print("-: Shirodhara :-");
  lcd.setCursor(5, 1);
  lcd.print("Therapy By");
  lcd.setCursor(4, 2);
  lcd.print("Keshayurved");
  delay(5000);

  currentTemperature = readTemperature();
  displaySettingsScreen();
}

float readTemperature() {
  long sum = 0;
  int validReadings = 0;

  for (int i = 0; i < TEMP_NUM_SAMPLES; i++) {
    int reading = analogRead(TEMP_SENSOR_PIN);

    if (reading > 1 && reading < 4094) {
      sum += reading;
      validReadings++;
    }
    delay(10); // Small delay between readings
  }

  if (validReadings == 0) {
    return NAN; // Return "Not a Number" to indicate an error
  }

  float avgReading = (float)sum / validReadings;

  if (avgReading >= 4095.0) {
    return NAN;
  }

  float resistance = SERIES_RESISTOR / (4095.0 / avgReading - 1.0);
  
  if (resistance <= 0) {
    return NAN;
  }

  float steinhart = resistance / THERMISTOR_NOMINAL;
  steinhart = log(steinhart);
  steinhart /= B_COEFFICIENT;
  steinhart += 1.0 / (TEMPERATURE_NOMINAL + 273.15);
  steinhart = 1.0 / steinhart;
  steinhart -= 273.15;

  return steinhart;
}


void displaySettingsScreen() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Current Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print(" C");
  lcd.setCursor(0, 1);
  lcd.print("Duration: ");
  lcd.print(durationStr);
  if (settingDuration) lcd.print("_");
  lcd.print(" min");
  lcd.setCursor(0, 2);
  lcd.print("Temperature: ");
  lcd.print(temperatureStr);
  if (!settingDuration) lcd.print("_");
  lcd.print(" C");
  lcd.setCursor(0, 3);
  lcd.print("Press D to continue");
}

void displayHeatingScreen() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Heating in progress");
  lcd.setCursor(0, 1);
  lcd.print("Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print("/");
  lcd.print(temperature);
  lcd.print(" C");
  lcd.setCursor(0, 2);
  lcd.print("Heater: ");
  lcd.print(heaterState ? "ON " : "OFF");
  lcd.setCursor(0, 3);
  lcd.print("Please wait...");
}

void displayTemperatureReadyScreen() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Temperature Ready!");
  lcd.setCursor(0, 1);
  lcd.print("Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print("/");
  lcd.print(temperature);
  lcd.print(" C");
  lcd.setCursor(0, 3);
  lcd.print("Press A to start");
  for (int i = 0; i < 2; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(200);
    digitalWrite(BUZZER_PIN, LOW);
    delay(200);
  }
}

void displayTreatmentScreen() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Treatment Active");
  lcd.setCursor(0, 1);
  lcd.print("Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print("/");
  lcd.print(temperature);
  lcd.print(" C");
  lcd.setCursor(0, 2);
  lcd.print("Heater: ");
  lcd.print(heaterState ? "ON " : "OFF");
  unsigned long elapsedTime = (millis() - treatmentStartTime) / 1000;
  unsigned long remainingTime = (treatmentDuration / 1000) - elapsedTime;
  int remainingMinutes = remainingTime / 60;
  int remainingSeconds = remainingTime % 60;
  lcd.setCursor(0, 3);
  lcd.print("Time left: ");
  if (remainingMinutes < 10) lcd.print("0");
  lcd.print(remainingMinutes);
  lcd.print(":");
  if (remainingSeconds < 10) lcd.print("0");
  lcd.print(remainingSeconds);
}

void showLimitExceededScreen(bool isDuration) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("ERROR: Limit Exceeded");
  lcd.setCursor(0, 1);
  if (isDuration) {
    lcd.print("Max duration: ");
    lcd.print(MAX_DURATION);
    lcd.print(" min");
  } else {
    lcd.print("Max temperature: ");
    lcd.print(MAX_TEMPERATURE);
    lcd.print(" C");
  }
  lcd.setCursor(0, 3);
  lcd.print("Please enter again");
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(100);
    digitalWrite(BUZZER_PIN, LOW);
    delay(100);
  }
  delay(2000);
}

void activateEmergencyStop() {
  if (!emergencyStopActive) {
    emergencyStopActive = true;
    stopTreatment();
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("!!! EMERGENCY STOP !!!");
    lcd.setCursor(0, 3);
    lcd.print("Reset system to resume");
    Serial.println("EMERGENCY STOP ACTIVATED");
  }
}

void checkEmergencyStop() {
  if (digitalRead(EMERGENCY_STOP_PIN) == HIGH) {
    activateEmergencyStop();
  }
}

void controlHeater() {
  if (heatingActive || treatmentActive) {
    if (currentTemperature < (temperature - TEMP_TOLERANCE / 2) && !heaterState) {
      digitalWrite(HEATER_RELAY_PIN, HIGH);
      heaterState = true;
    } else if (currentTemperature > (temperature + TEMP_TOLERANCE / 2) && heaterState) {
      digitalWrite(HEATER_RELAY_PIN, LOW);
      heaterState = false;
    }
    if (heatingActive && !temperatureReached && currentTemperature >= temperature) {
      temperatureReached = true;
      displayTemperatureReadyScreen();
    }
  } else {
    if (heaterState) {
      digitalWrite(HEATER_RELAY_PIN, LOW);
      heaterState = false;
    }
  }
}

void startHeating() {
  heatingActive = true;
  temperatureReached = false;
  treatmentActive = false;
  controlHeater();
  displayHeatingScreen();
  Serial.println("Heating started");
}

void updateServo() {
  if (servoActive) {
    servoPosition += servoDirection * servoSpeed;
    if (servoPosition >= servoMaxAngle) {
      servoPosition = servoMaxAngle;
      servoDirection = -1;
    } else if (servoPosition <= servoMinAngle) {
      servoPosition = servoMinAngle;
      servoDirection = 1;
    }
    oscillationServo.write(servoPosition);
  }
}

void startTreatment() {
  treatmentActive = true;
  heatingActive = false;
  servoActive = true;
  servoPosition = 90;
  oscillationServo.write(servoPosition);
  treatmentStartTime = millis();
  treatmentDuration = (unsigned long)duration * 60 * 1000;
  digitalWrite(PUMP_RELAY_PIN, HIGH);
  displayTreatmentScreen();
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(100);
    digitalWrite(BUZZER_PIN, LOW);
    delay(100);
  }
  Serial.println("Treatment started");
}

void stopTreatment() {
  treatmentActive = false;
  heatingActive = false;
  temperatureReached = false;
  servoActive = false;
  digitalWrite(HEATER_RELAY_PIN, LOW);
  digitalWrite(PUMP_RELAY_PIN, LOW);
  heaterState = false;
  oscillationServo.write(90);
  if (!emergencyStopActive) {
    for (int i = 0; i < 3; i++) {
      digitalWrite(BUZZER_PIN, HIGH); delay(100);
      digitalWrite(BUZZER_PIN, LOW); delay(100);
    }
    lcd.clear();
    lcd.setCursor(0, 1);
    lcd.print("Treatment Complete!");
    delay(3000);
    displaySettingsScreen();
  }
  Serial.println("Treatment stopped/completed");
}

void loop() {
  unsigned long currentMillis = millis();
  server.handleClient();
  checkEmergencyStop();
  if (emergencyStopActive) {
    if (currentMillis % 1000 < 500) {
        digitalWrite(BUZZER_PIN, HIGH);
    } else {
        digitalWrite(BUZZER_PIN, LOW);
    }
    return;
  }

  if (treatmentActive && (currentMillis - treatmentStartTime >= treatmentDuration)) {
    stopTreatment();
  }

  if (currentMillis - lastTempReadTime >= tempReadInterval) {
    lastTempReadTime = currentMillis;
    float temp = readTemperature();
    if (!isnan(temp)) {
      currentTemperature = temp;
    }
    controlHeater();
    if (treatmentActive) displayTreatmentScreen();
    else if (heatingActive && !temperatureReached) displayHeatingScreen();
    else if (!heatingActive && !treatmentActive) {
        lcd.setCursor(14, 0);
        lcd.print("      ");
        lcd.setCursor(14, 0);
        lcd.print(currentTemperature, 1);
    }
  }

  if (currentMillis - lastServoUpdateTime >= servoUpdateInterval) {
    lastServoUpdateTime = currentMillis;
    updateServo();
  }

  char key = keypad.getKey();
  if (key) {
    digitalWrite(BUZZER_PIN, HIGH); delay(50);
    digitalWrite(BUZZER_PIN, LOW);
    if (treatmentActive) {
      if (key == 'B') stopTreatment();
    } else if (temperatureReached) {
      if (key == 'A') startTreatment();
      if (key == 'B') stopTreatment();
    } else if (heatingActive) {
      if (key == 'B') stopTreatment();
    } else {
      processSettingsKeyPress(key);
    }
  }
  delay(10);
}

void processSettingsKeyPress(char key) {
  switch (key) {
    case '*':
      settingDuration = !settingDuration;
      break;
    case '#':
      if (settingDuration) durationStr = ""; else temperatureStr = "";
      break;
    case 'D':
      duration = durationStr.toInt();
      temperature = temperatureStr.toInt();
      if (duration > MAX_DURATION) {
        showLimitExceededScreen(true);
        durationStr = "";
      } else if (temperature > MAX_TEMPERATURE) {
        showLimitExceededScreen(false);
        temperatureStr = "";
      } else {
        startHeating();
      }
      return;
    default:
      if (key >= '0' && key <= '9') {
        if (settingDuration) {
          if (durationStr.length() < 2) durationStr += key;
        } else {
          if (temperatureStr.length() < 2) temperatureStr += key;
        }
      }
      break;
  }
  displaySettingsScreen();
}
