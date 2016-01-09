/*  Sketch per restituire i valori delle variabili utlizzando un protocollo custom.
 *  Questa versione è in grado di restituire solamente i valori degli ingressi analogici,
 *  è stata realizzata appositamente per il programma ArduinoTrend.
 *  
 *  Programma realizzato da Aniballi Stefano.
 */
 
const int ledRUN = 13;  //led a bordo della schedina di arduino
byte telegramRX[10];    //Array per appoggiare il telegramma ricevuto
int indexTelegram = 0;  //Indice per array telegramRX[]
byte buffer[1024];      //Buffer per appoggiare i byte in ingresso sulla seriale
int indexBuffer = 0;    //Indice per il buffer
int lastBuffer = 0;     //Memoria per indicare l' indice dell' ultimo byte nel buffer

void setup() {
  
  Serial.begin(57600);	// impostazione baudrate seriale
  pinMode (ledRUN, OUTPUT);
  digitalWrite(ledRUN, HIGH);
  
}

void loop() {

  lastBuffer = 0;
  indexBuffer = 0;

  //Finche ci sono byte sulla seriale li copio nel buffer
  while (Serial.available() > 0) {
    buffer[lastBuffer] = Serial.read();
    lastBuffer++;
  }

  //Se non sto analizzando un telegramma avanzo l' indice finche non trovo il byte iniziale
  if (indexTelegram == 0) {
    while (buffer[indexBuffer] != '@') {
      if (indexBuffer < lastBuffer) {
        indexBuffer++;
      } else {
        break;
      }
    }
  }
  
  //Finche ci sono byte nel buffer analizzo i telegrammi
  while (indexBuffer < lastBuffer) {
    if (buffer[indexBuffer] == '@') {
      indexTelegram = 0;
    }
    telegramRX[indexTelegram] = buffer[indexBuffer];
    indexTelegram++;
    indexBuffer++;
    if (telegramRX[indexTelegram - 1] == '*' && indexTelegram == 10) {
      analizeTelegram(telegramRX);
      indexTelegram = 0;
    }  
  }
  
}

void analizeTelegram(byte telegram[10]) {
  byte telegramTX[10];
  byte checksum = 0;
  
  //Calcolo il checksum del telegramma ricevuto
  for (int i = 0; i < 7; i++) {
    checksum = telegram[i] ^ telegram[i + 1];
  }
  
  //Se il chesum è corretto eseguo il comando ricevuto
  if ( checksum == telegram[8]) {
    switch (telegram[1]) {
      //Comando READ
      case 0:
        switch (telegram[2]) {
          //Analog Input
          case 'A':
            int numInput = telegram[3];
            int value = analogRead(numInput);
            telegramTX[4] = 0;
            telegramTX[5] = 0;
            telegramTX[6] = highByte(value);
            telegramTX[7] = lowByte(value);
            break;
        }
        break;

      //Comando WRITE
      case 1:
        break;
    }
    
    //Copio il comando è la variabile del telegramma ricevuto su quello di risposta
    telegramTX[0] = telegram[0];
    telegramTX[1] = telegram[1];
    telegramTX[2] = telegram[2];
    telegramTX[3] = telegram[3];
    //Calcolo il Checksum
    for (int i = 0; i < 7; i++) {
      checksum = telegramTX[i] ^ telegramTX[i + 1];
    }
    //Completo il telegramma di risposta
    telegramTX[8] = checksum;
    telegramTX[9] = telegram[9];
    Serial.write(telegramTX, 10);
  }
  
}

