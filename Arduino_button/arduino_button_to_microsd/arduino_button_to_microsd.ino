#include <SPI.h>
#include <SD.h>

// === USER CONFIG ===
const uint8_t DATA_BTN_PINS[4] = {2, 3, 4, 5}; // data buttons
const uint8_t TRIGGER_PIN      = 6;            // trigger button
const uint8_t SD_CS_PIN        = 10;           // SD chip-select (adjust for your hardware)
const char*   FILE_NAME        = "data.csv";
const unsigned long DEBOUNCE_MS = 20;
// ====================

// Debounce structure
struct Debounce {
  bool lastReading;
  bool stableState;
  unsigned long lastChange;
};

Debounce dataDb[4];
Debounce trigDb;

// Row buffer
uint8_t row[4] = {0, 0, 0, 0};
bool lastTriggerDebounced = HIGH; // INPUT_PULLUP logic

File logFile; // keep file open for session

// ---- Debounce helpers ----
void initButton(uint8_t pin, Debounce &db) {
  pinMode(pin, INPUT_PULLUP);
  db.lastReading = digitalRead(pin);
  db.stableState = db.lastReading;
  db.lastChange  = millis();
}

bool debounceRead(uint8_t pin, Debounce &db) {
  bool reading = digitalRead(pin);
  if (reading != db.lastReading) {
    db.lastChange = millis();
    db.lastReading = reading;
  }
  if (millis() - db.lastChange > DEBOUNCE_MS) {
    db.stableState = reading;
  }
  return db.stableState;
}

// ---- SD setup with fixed header ----
void setupSD() {
  if (!SD.begin(SD_CS_PIN)) {
    Serial.println("SD init failed. Check wiring and CS pin!");
    while (true); // halt
  }

  // Create file and header if it doesn't exist
  if (!SD.exists(FILE_NAME)) {
    logFile = SD.open(FILE_NAME, FILE_WRITE);
    if (logFile) {
      logFile.println("t_ms,b1,b2,b3,b4");
      logFile.flush();
      Serial.println("Created new data.csv with header.");
    } else {
      Serial.println("Error creating data.csv!");
    }
  } else {
    // Open existing file for appending
    logFile = SD.open(FILE_NAME, FILE_WRITE);
    Serial.println("Existing data.csv opened for append.");
  }

  if (!logFile) {
    Serial.println("Failed to open file!");
    while (true);
  }
}

// ---- Write + Reset ----
void writeRow() {
  logFile.print(millis());
  logFile.print(',');
  logFile.print(row[0]); logFile.print(',');
  logFile.print(row[1]); logFile.print(',');
  logFile.print(row[2]); logFile.print(',');
  logFile.println(row[3]);
  logFile.flush(); // ensure write committed

  // Serial echo
  Serial.print("Logged: ");
  Serial.print(row[0]); Serial.print(',');
  Serial.print(row[1]); Serial.print(',');
  Serial.print(row[2]); Serial.print(',');
  Serial.println(row[3]);
}

void resetRow() {
  for (int i = 0; i < 4; i++) row[i] = 0;
}

// ---- SETUP ----
void setup() {
  Serial.begin(115200);
  delay(200);

  // Initialize buttons
  for (int i = 0; i < 4; i++) initButton(DATA_BTN_PINS[i], dataDb[i]);
  initButton(TRIGGER_PIN, trigDb);

  // Initialize SD + file
  setupSD();

  Serial.println("Ready. Press data buttons, then trigger to log a row.");
}

// ---- LOOP ----
void loop() {
  // 1) Read & debounce data buttons
  for (int i = 0; i < 4; i++) {
    bool deb = debounceRead(DATA_BTN_PINS[i], dataDb[i]);
    if (deb == LOW && row[i] == 0) { // pressed once per trial
      row[i] = 1;
    }
  }

  // 2) Detect trigger press (falling edge)
  bool trigDeb = debounceRead(TRIGGER_PIN, trigDb);
  if (lastTriggerDebounced == HIGH && trigDeb == LOW) {
    writeRow();
    resetRow();
  }
  lastTriggerDebounced = trigDeb;
}