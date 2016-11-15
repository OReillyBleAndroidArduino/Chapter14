#include "CurieBle.h"

static const char* bluetoothDeviceName = "LedRemote";

static const int   characteristicTransmissionLength = 2; 
static const unsigned int bleDataFooterPosition = 1;
static const unsigned int bleMessageDataPosition = 0;

static const unsigned int bleCommandLedOff = 1; 
static const unsigned int bleCommandLedOn = 2;

static const unsigned int bleResponseError = 0;
static const unsigned int bleResponseConfirmation = 1;
static const unsigned int bleResponseCommand = 2;


static const byte ledPin = 13;
static const unsigned int ledError = 0;
static const unsigned int ledOn = 1;
static const unsigned int ledOff = 2;
int ledState = ledOff;

char bleMessage[characteristicTransmissionLength];
const char* uuid;
bool bleCommandReceived = false;


BLEService service("180C");
BLECharacteristic characteristic(
  "2A56",
  BLEWrite | BLERead | BLENotify,
  characteristicTransmissionLength
);

BLEPeripheral blePeripheral;


void onCharacteristicWritten(BLECentral& central, 
  BLECharacteristic &characteristic) {
    
  bleCommandReceived = true;
  uuid = characteristic.uuid();
  
  strcpy((char*) bleMessage, (const char*) characteristic.value());
}

void sendBleCommandConfirmation(int ledState) {
  byte confirmation[characteristicTransmissionLength] = {0x0};
  if (ledState == HIGH) {
    confirmation[bleMessageDataPosition] = (byte)ledOn;
  } else {
    confirmation[bleMessageDataPosition] = (byte)ledOff;
  }
  
  confirmation[bleDataFooterPosition] = (byte)bleResponseConfirmation;
  characteristic.setValue((const unsigned char*) confirmation, characteristicTransmissionLength);
}


// Central connected.  Print MAC address
void onCentralConnected(BLECentral& central) {
  Serial.print("Central connected: ");
  Serial.println(central.address());
}

// Central disconnected
void onCentralDisconnected(BLECentral& central) {
  Serial.println("Central disconnected");
}


void setup() {
  Serial.begin(9600);
  while (!Serial) {;}

  digitalWrite(ledPin, LOW); // start with LED off
  
  blePeripheral.setLocalName(bluetoothDeviceName);
  
  // attach callback when central connects
  blePeripheral.setEventHandler(
    BLEConnected,
    onCentralConnected
  );
  // attach callback when centlal disconnects
  blePeripheral.setEventHandler(
    BLEDisconnected,
    onCentralDisconnected
  );

  
  blePeripheral.setAdvertisedServiceUuid(service.uuid());
  blePeripheral.addAttribute(service);
  blePeripheral.addAttribute(characteristic);

  characteristic.setEventHandler(
    BLEWritten,
    onCharacteristicWritten
  );

  Serial.print("Starting ");
  Serial.println(bluetoothDeviceName);
  blePeripheral.begin();
}

void loop() {
  if (bleCommandReceived) {
    bleCommandReceived = false; // ensures only executed once
  
    // incoming command is one byte
    unsigned int command = bleMessage[bleMessageDataPosition]; 
    if (command == bleCommandLedOn) {
      Serial.println("Turning LED on");
      ledState = HIGH;
      sendBleCommandConfirmation(ledState);
    } else {
      Serial.println("Turning LED off");
      ledState = LOW;
      sendBleCommandConfirmation(ledState);
    }

    digitalWrite(ledPin, ledState);
  }

}
