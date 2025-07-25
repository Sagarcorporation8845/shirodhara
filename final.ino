#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <Keypad.h>
#include <SoftwareSerial.h>
#include <math.h>
#include <ESP32Servo.h> // Using ESP32Servo library which works with ESP32 core 2.x
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
#define LCD_ADDRESS 0x27  // Default I2C address for most LCD2004 modules
#define LCD_COLUMNS 20
#define LCD_ROWS 4

// Sensor and Control Pins
#define TEMP_SENSOR_PIN 34  // ADC1_CH6
#define HEATER_RELAY_PIN 5  // GPIO5 for heater control
#define PUMP_RELAY_PIN   16 // GPIO16 for pump control
#define BUZZER_PIN 19
#define SERVO_PIN 22        // GPIO22 for servo control (SG-5010)
#define EMERGENCY_STOP_PIN 2 // GPIO21 for emergency stop button (NC contact)

// I2C Pins (ESP32 default)
#define I2C_SDA 23
#define I2C_SCL 15

// Setting limits
#define MAX_DURATION 60    // Maximum duration in minutes
#define MAX_TEMPERATURE 45 // Maximum temperature in degrees Celsius

// Temperature control parameters
#define TEMP_TOLERANCE 3.0 // Temperature tolerance in degrees Celsius

// Thermistor parameters
#define THERMISTOR_NOMINAL 10000   // Resistance at 25 degrees C (10k)
#define TEMPERATURE_NOMINAL 25     // Temperature for nominal resistance (25 C)
#define B_COEFFICIENT 3950         // Beta coefficient of the thermistor
#define SERIES_RESISTOR 4700       // Value of the 4.7k resistor


// Bluetooth Module
SoftwareSerial bluetooth(17, 18); // RX, TX

// LCD object
LiquidCrystal_I2C lcd(LCD_ADDRESS, LCD_COLUMNS, LCD_ROWS);

// Emergency stop variables
bool emergencyStopActive = false;
unsigned long lastEmergencyCheckTime = 0;
const long emergencyCheckInterval = 100; // Check emergency stop every 100ms
unsigned long lastEmergencyBeepTime = 0;
bool emergencyBeepState = false;

// Treatment variables
bool treatmentActive = false;
bool heatingActive = false;
bool temperatureReached = false;
unsigned long treatmentStartTime = 0;
unsigned long treatmentDuration = 0; // in milliseconds
bool heaterState = false;
bool servoActive = false;           // Flag to track if servo is active
unsigned long lastServoUpdateTime = 0; // For servo oscillation timing
int servoPosition = 0;              // Current servo position
int servoDirection = 1;             // Direction of servo movement (1 or -1)
int servoMinAngle = 60;             // Minimum angle for oscillation
int servoMaxAngle = 120;            // Maximum angle for oscillation
int servoSpeed = 5;                 // Speed of servo movement (degrees per update)
unsigned long servoUpdateInterval = 50; // Update servo every 50ms

// Servo parameters for SG-5010
#define MIN_MICROS 800   // Minimum pulse width in microseconds (for 0 degrees)
#define MAX_MICROS 2450  // Maximum pulse width in microseconds (for 180 degrees)

// Servo object
Servo oscillationServo;

// Keypad setup
const byte ROWS = 4;
const byte COLS = 4;
char keys[ROWS][COLS] = {
  {'1','4','7','*'},  // A: Start
  {'2','5','8','0'},  // B: Stop
  {'3','6','9','#'},  // C: Set Time
  {'A','B','C','D'}   // *: Cancel, #: Confirm, D: Set Temp
};
byte rowPins[ROWS] = {25, 26, 27, 14}; // Connect to the row pinouts of the keypad
byte colPins[COLS] = {32, 33, 12, 13}; // Connect to the column pinouts of the keypad

Keypad keypad = Keypad(makeKeymap(keys), rowPins, colPins, ROWS, COLS);

// Variables for settings
int duration = 0;
int temperature = 0;
float currentTemperature = 0.0;
bool settingDuration = true; // true for duration, false for temperature
String durationStr = "";
String temperatureStr = "";
unsigned long lastTempReadTime = 0;
const long tempReadInterval = 1000; // Read temperature every 1 second

// Forward declarations
void displaySettingsScreen();
void startHeating();
void startTreatment();
void stopTreatment();

void setupWiFi() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid, password);
  
  IPAddress IP = IPAddress(192, 168, 0, 1);
  IPAddress gateway(192, 168, 0, 1);
  IPAddress subnet(255, 255, 255, 0);
  
  WiFi.softAPConfig(IP, gateway, subnet);
  
  Serial.println("WiFi AP Started");
  Serial.print("IP Address: ");
  Serial.println(WiFi.softAPIP());
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
    unsigned long remainingTime = (treatmentDuration / 1000) - elapsedTime;
    jsonDoc["remaining_time"] = remainingTime;
  } else {
    jsonDoc["remaining_time"] = duration * 60;
  }
  
  String response;
  serializeJson(jsonDoc, response);
  server.send(200, "application/json", response);
}

/**
 * @brief Handles API requests to update device state.
 * This function now handles multiple actions based on the JSON payload,
 * aligning with the updated workflow diagram.
 * * Payloads:
 * 1. Set Parameters & Start Heating:
 * { "duration": 30, "temperature": 40 }
 * 2. Start Treatment (after temp is reached):
 * { "action": "start" }
 * 3. Stop/Cancel Treatment or Heating:
 * { "action": "stop" }
 */
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

  // Check for an action command (start/stop)
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

  // Check for setting new parameters (duration/temperature)
  if (doc.containsKey("duration") && doc.containsKey("temperature")) {
    int newDuration = doc["duration"];
    int newTemperature = doc["temperature"];

    if (newDuration <= 0 || newDuration > MAX_DURATION || 
        newTemperature <= 0 || newTemperature > MAX_TEMPERATURE) {
      server.send(400, "application/json", "{\"error\":\"Invalid parameters\"}");
      return;
    }

    // Update global variables
    duration = newDuration;
    temperature = newTemperature;
    durationStr = String(duration);
    temperatureStr = String(temperature);

    // Update the LCD to show the new settings from the API
    displaySettingsScreen();
    delay(1000); // Give user a moment to see the new settings

    // Start the heating process
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
  // Initialize serial communication
  Serial.begin(115200);
  bluetooth.begin(9600);
  
  // Initialize WiFi and API
  setupWiFi();
  setupAPI();
  
  // Initialize I2C
  Wire.begin(I2C_SDA, I2C_SCL);
  
  // Initialize LCD
  lcd.init();
  lcd.backlight();
  
  // Initialize relay pins
  pinMode(HEATER_RELAY_PIN, OUTPUT);
  pinMode(PUMP_RELAY_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  
  // Initialize emergency stop pin with internal pullup resistor
  // For NC (Normally Closed) button - will read LOW when not pressed, HIGH when pressed
  pinMode(EMERGENCY_STOP_PIN, INPUT_PULLUP);
  
  // Initialize servo
  oscillationServo.attach(SERVO_PIN, MIN_MICROS, MAX_MICROS);
  oscillationServo.write(90); // Center position
  Serial.println("Servo setup successful");
  
  // Set initial state for relays
  digitalWrite(HEATER_RELAY_PIN, LOW);
  digitalWrite(PUMP_RELAY_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);
  
  // Boot beep - 2 second continuous beep
  digitalWrite(BUZZER_PIN, HIGH);
  delay(2000);
  digitalWrite(BUZZER_PIN, LOW);
  
  // Configure ADC for temperature sensor
  analogReadResolution(12); // ESP32 has 12-bit ADC
  
  // First screen - ZENEVO INNOVATIONS
  lcd.clear();
  lcd.setCursor(0, 1);
  lcd.print("ZENEVO INNOVATIONS");
  delay(5000);  // Display for 5 seconds
  
  // Second screen - Shirodhara Therapy
  lcd.clear();
  lcd.setCursor(3, 0);
  lcd.print("-: Shirodhara :-");
  lcd.setCursor(5, 1);
  lcd.print("Therapy By");
  lcd.setCursor(4, 2);
  lcd.print("Keshayurved");
  delay(5000);  // Display for 5 seconds
  
  // Read initial temperature
  currentTemperature = readTemperature();
  
  // Third screen - Settings screen
  displaySettingsScreen();
}

float readTemperature() {
  // Take multiple samples to reduce noise
  uint16_t samples[NUM_SAMPLES];
  uint8_t i;
  float average = 0;
  
  // Sample the analog input
  for (i = 0; i < NUM_SAMPLES; i++) {
    samples[i] = analogRead(TEMP_SENSOR_PIN);
    delay(10);
  }
  
  // Average the samples
  for (i = 0; i < NUM_SAMPLES; i++) {
    average += samples[i];
  }
  average /= NUM_SAMPLES;
  
  // Convert the analog value to resistance
  average = 4095.0 / average - 1.0;
  average = SERIES_RESISTOR / average;
  
  // Use the Steinhart-Hart equation to calculate temperature
  float steinhart;
  steinhart = average / THERMISTOR_NOMINAL;          // (R/Ro)
  steinhart = log(steinhart);                        // ln(R/Ro)
  steinhart /= B_COEFFICIENT;                        // 1/B * ln(R/Ro)
  steinhart += 1.0 / (TEMPERATURE_NOMINAL + 273.15); // + (1/To)
  steinhart = 1.0 / steinhart;                       // Invert
  steinhart -= 273.15;                               // Convert to Celsius
  
  return steinhart;
}

void displaySettingsScreen() {
  lcd.clear();
  // First row - Current temperature
  lcd.setCursor(0, 0);
  lcd.print("Current Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print(" C");
  
  // Second row - Duration
  lcd.setCursor(0, 1);
  lcd.print("Duration: ");
  lcd.print(durationStr);
  if (settingDuration) {
    lcd.print("_");  // Cursor for active field
  }
  lcd.print(" min");
  
  // Third row - Temperature
  lcd.setCursor(0, 2);
  lcd.print("Temperature: ");
  lcd.print(temperatureStr);
  if (!settingDuration) {
    lcd.print("_");  // Cursor for active field
  }
  lcd.print(" C");
  
  // Fourth row - Instructions
  lcd.setCursor(0, 3);
  lcd.print("Press D to continue");
}

void displayHeatingScreen() {
  lcd.clear();
  
  // First row - Heating status
  lcd.setCursor(0, 0);
  lcd.print("Heating in progress");
  
  // Second row - Current temperature
  lcd.setCursor(0, 1);
  lcd.print("Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print("/");
  lcd.print(temperature);
  lcd.print(" C");
  
  // Third row - Heater status
  lcd.setCursor(0, 2);
  lcd.print("Heater: ");
  lcd.print(heaterState ? "ON " : "OFF");
  
  // Fourth row - Instructions
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
  
  // Sound notification
  for (int i = 0; i < 2; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(200);
    digitalWrite(BUZZER_PIN, LOW);
    delay(200);
  }
}

void displayTreatmentScreen() {
  lcd.clear();
  
  // First row - Treatment status
  lcd.setCursor(0, 0);
  lcd.print("Treatment Active");
  
  // Second row - Current temperature
  lcd.setCursor(0, 1);
  lcd.print("Temp: ");
  lcd.print(currentTemperature, 1);
  lcd.print("/");
  lcd.print(temperature);
  lcd.print(" C");
  
  // Third row - Heater status
  lcd.setCursor(0, 2);
  lcd.print("Heater: ");
  lcd.print(heaterState ? "ON " : "OFF");
  
  // Fourth row - Time remaining
  unsigned long elapsedTime = (millis() - treatmentStartTime) / 1000; // in seconds
  unsigned long remainingTime = (treatmentDuration / 1000) - elapsedTime; // in seconds
  
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
  
  // Sound error tone
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(100);
    digitalWrite(BUZZER_PIN, LOW);
    delay(100);
  }
  
  delay(2000);
}

void showTemperatureTooHighScreen() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("ERROR: Safety Alert");
  lcd.setCursor(0, 1);
  lcd.print("Temperature too high");
  lcd.setCursor(0, 2);
  lcd.print("Current: ");
  lcd.print(currentTemperature, 1);
  lcd.print(" C");
  lcd.setCursor(0, 3);
  lcd.print("Please cool system");
  
  // Sound error tone - longer alert
  for (int i = 0; i < 5; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(200);
    digitalWrite(BUZZER_PIN, LOW);
    delay(200);
  }
  
  delay(3000);
}

void displayEmergencyStopScreen() {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("!!! EMERGENCY STOP !!!");
  lcd.setCursor(0, 1);
  lcd.print("All systems halted");
  lcd.setCursor(0, 3);
  lcd.print("Reset system to resume");
  
  // Initial emergency beep is handled in the loop for continuous pattern
}

void activateEmergencyStop() {
  // Only activate once
  if (!emergencyStopActive) {
    emergencyStopActive = true;
    
    // Stop all operations
    treatmentActive = false;
    heatingActive = false;
    temperatureReached = false;
    servoActive = false;
    
    // Turn off all outputs
    digitalWrite(HEATER_RELAY_PIN, LOW);
    digitalWrite(PUMP_RELAY_PIN, LOW);
    heaterState = false;
    oscillationServo.write(90);  // Return servo to center position
    
    // Display emergency screen
    displayEmergencyStopScreen();
    
    Serial.println("EMERGENCY STOP ACTIVATED");
    bluetooth.println("EMERGENCY STOP ACTIVATED");
  }
}

void checkEmergencyStop() {
  // Read the emergency stop button state
  // For NC contact: HIGH = button pressed (circuit open), LOW = button not pressed (circuit closed)
  if (digitalRead(EMERGENCY_STOP_PIN) == HIGH) {
    activateEmergencyStop();
  }
}

void controlHeater() {
  // Implement temperature control with hysteresis
  if (heatingActive || treatmentActive) {
    // If temperature is below target minus tolerance, turn heater on
    if (currentTemperature < (temperature - TEMP_TOLERANCE/2) && !heaterState) {
      digitalWrite(HEATER_RELAY_PIN, HIGH);
      heaterState = true;
      Serial.println("Heater turned ON");
      bluetooth.println("Heater turned ON");
    }
    // If temperature is above target, turn heater off
    else if (currentTemperature > (temperature + TEMP_TOLERANCE/2) && heaterState) {
      digitalWrite(HEATER_RELAY_PIN, LOW);
      heaterState = false;
      Serial.println("Heater turned OFF");
      bluetooth.println("Heater turned OFF");
    }
    
    // Check if temperature has reached target for the first time
    if (heatingActive && !temperatureReached && currentTemperature >= temperature) {
      temperatureReached = true;
      displayTemperatureReadyScreen();
    }
  } else {
    // If neither heating nor treatment is active, ensure heater is off
    if (heaterState) {
      digitalWrite(HEATER_RELAY_PIN, LOW);
      heaterState = false;
    }
  }
}

void startHeating() {
  // Safety check - don't start if temperature is already too high
  if (currentTemperature > MAX_TEMPERATURE) {
    showTemperatureTooHighScreen();
    displaySettingsScreen();
    return;
  }
  
  heatingActive = true;
  temperatureReached = false;
  
  // Initial heater control
  controlHeater();
  
  // Show heating screen
  displayHeatingScreen();
  
  Serial.println("Heating started");
  bluetooth.println("Heating started");
}

void updateServo() {
  // Only update if servo is active and servo was successfully initialized
  if (servoActive) {
    // Update servo position
    servoPosition += servoDirection * servoSpeed;
    
    // Check if we need to change direction
    if (servoPosition >= servoMaxAngle) {
      servoPosition = servoMaxAngle;
      servoDirection = -1;
    } else if (servoPosition <= servoMinAngle) {
      servoPosition = servoMinAngle;
      servoDirection = 1;
    }
    
    // Write position to servo
    oscillationServo.write(servoPosition);
    
    // Debug output
    Serial.print("Servo position: ");
    Serial.println(servoPosition);
  }
}

void startTreatment() {
  // Double-check temperature is not too high
  if (currentTemperature > MAX_TEMPERATURE) {
    showTemperatureTooHighScreen();
    heatingActive = false;
    displaySettingsScreen();
    return;
  }
  
  treatmentActive = true;
  heatingActive = false;
  servoActive = true;  // Activate servo when treatment starts
  servoPosition = 90;  // Start from middle position
  oscillationServo.write(servoPosition);
  treatmentStartTime = millis();
  treatmentDuration = (unsigned long)duration * 60 * 1000; // Convert minutes to milliseconds
  
  // Turn on pump
  digitalWrite(PUMP_RELAY_PIN, HIGH);
  
  // Show treatment screen
  displayTreatmentScreen();
  
  // Treatment start beep - 3 short beeps
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH);
    delay(100);
    digitalWrite(BUZZER_PIN, LOW);
    delay(100);
  }
  
  Serial.println("Treatment started");
  bluetooth.println("Treatment started");
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
  
  for (int i = 0; i < 3; i++) {
    digitalWrite(BUZZER_PIN, HIGH); delay(100);
    digitalWrite(BUZZER_PIN, LOW); delay(100);
  }
  
  lcd.clear();
  lcd.setCursor(0, 1);
  lcd.print("Treatment Complete!");
  delay(3000);
  
  displaySettingsScreen();
  Serial.println("Treatment stopped");
  bluetooth.println("Treatment stopped");
}

void loop() {
  unsigned long currentMillis = millis();
  
  checkEmergencyStop();
  if (emergencyStopActive) {
    if (currentMillis - lastEmergencyBeepTime >= 1000) {
      lastEmergencyBeepTime = currentMillis;
      emergencyBeepState = !emergencyBeepState;
      digitalWrite(BUZZER_PIN, emergencyBeepState);
    }
    return;
  }

  if (currentMillis - lastTempReadTime >= tempReadInterval) {
    lastTempReadTime = currentMillis;
    currentTemperature = readTemperature();
    
    if ((treatmentActive || heatingActive) && currentTemperature > MAX_TEMPERATURE + 5) {
      stopTreatment();
      showTemperatureTooHighScreen();
      return;
    }
    
    controlHeater();
    
    if (treatmentActive) displayTreatmentScreen();
    else if (heatingActive) displayHeatingScreen();
    else if (!temperatureReached) {
        // Update temperature on settings screen
        lcd.setCursor(14, 0);
        lcd.print("      "); // Clear previous
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
  
  server.handleClient();
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
      return; // Return to avoid display update at the end
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